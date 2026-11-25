package net.augustus.config;

import net.augustus.Augustus;
import net.augustus.settings.options.NotificationCategoryOption;
import net.augustus.ui.notifications.NotificationLevel;
import net.augustus.ui.notifications.NotificationManager;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.audio.ClientSounds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketThreadUtil;

public class ModuleConfigStorage extends JsonConfigFile {
   private static final byte[] ENCRYPTED_MAGIC_HEADER = new byte[]{-82, 83, 1, 0};
   private static final byte[] AES_KEY = new byte[]{
      114, -27, 95, -64, 18, -101, 73, -38, 126, -9, 44, -87, 61, -122, 7, -55,
      89, -16, 35, -78, 103, -41, 52, -94, 67, -128, 21, -63, 98, -11, 76, -49
   };
   private static final Aes256Ecb CONFIG_CIPHER = new Aes256Ecb(AES_KEY);
   private final HashMap<Integer, ModuleConfigCodec> formatCodecs;

   public ModuleConfigStorage() {
      super(
         text -> ((String)text).getBytes(StandardCharsets.UTF_8),
         bytes -> decryptConfigBytes((byte[])bytes)
      );
      this.formatCodecs = new HashMap<>();
      this.formatCodecs.put(2, new ConfigFormatV2());
   }

   public static boolean hasEncryptedHeader(byte[] bytes) {
      if (bytes == null || bytes.length < ENCRYPTED_MAGIC_HEADER.length) {
         return false;
      }
      for (int i = 0; i < ENCRYPTED_MAGIC_HEADER.length; i++) {
         if (bytes[i] != ENCRYPTED_MAGIC_HEADER[i]) {
            return false;
         }
      }
      return true;
   }

   private static boolean hasAesMagicPrefix(byte[] bytes) {
      return bytes != null && bytes.length >= 4 && bytes[0] == (byte)0xAE && bytes[1] == (byte)0x53;
   }

   public static String decryptConfigBytes(byte[] bytes) {
      ConfigLoadReport silent = new ConfigLoadReport("<bytes>", null);
      String text = decryptWithDiagnostics(bytes, silent);
      return text != null ? text : "";
   }

   /**
    * Multi-stage decoder with exact error reporting and automatic recovery fallbacks:
    * 1. Strict AE 53 01 00 -> AES-256-ECB (PKCS#7) or plain UTF-8 JSON.
    * 2. Fallback: lenient AES-256-ECB raw block decryption (corrupted padding / truncated tail / alternate AE 53 xx xx header).
    * 3. Fallback: headerless AES-256-ECB decryption.
    * 4. Fallback: Base64 / clipboard Name_<x>_<b64> wrapper saved into a file.
    * 5. Fallback: UTF-16LE / UTF-16BE BOM or embedded JSON salvage.
    */
   public static String decryptWithDiagnostics(byte[] bytes, ConfigLoadReport report) {
      if (report != null) {
         report.inspectRawBytes(bytes);
      }
      if (bytes == null || bytes.length == 0) {
         if (report != null) {
            report.setDecryptionMode("empty");
            report.recordError("Read", "Config file is empty (0 bytes)", null);
         }
         return null;
      }

      // 1. Standard Augustus encrypted header (AE 53 01 00)
      if (hasEncryptedHeader(bytes)) {
         int payloadLen = bytes.length - ENCRYPTED_MAGIC_HEADER.length;
         try {
            byte[] decrypted = CONFIG_CIPHER.decryptPkcs7(bytes, ENCRYPTED_MAGIC_HEADER.length, payloadLen);
            String text = stripBom(new String(decrypted, StandardCharsets.UTF_8));
            if (looksLikeJsonStart(text)) {
               if (report != null) {
                  report.setDecryptionMode("aes256-ecb-pkcs7");
               }
               return text;
            }
            if (report != null) {
               report.recordError(
                  "Decrypt (aes256-ecb-pkcs7)",
                  "Decrypted payload does not start with JSON '{' (first decrypted bytes: "
                     + ConfigLoadReport.toHexPreview(decrypted, 16) + "; likely different AES key or unsupported cipher)",
                  null
               );
            }
         } catch (Exception e) {
            if (report != null) {
               report.recordError(
                  "Decrypt (aes256-ecb-pkcs7)",
                  "Strict AES-256-ECB PKCS#7 decryption failed (total=" + bytes.length + " bytes, payload=" + payloadLen + " bytes)",
                  e
               );
            }
         }

         // 2a. Lenient AES-256-ECB fallback (handles bad padding or truncated final block)
         try {
            byte[] raw = CONFIG_CIPHER.decryptRawBlocks(bytes, ENCRYPTED_MAGIC_HEADER.length, payloadLen);
            String text = stripBom(new String(raw, StandardCharsets.UTF_8));
            if (containsJsonMarkers(text)) {
               if (report != null) {
                  report.setDecryptionMode("aes256-ecb-raw-fallback");
                  report.markFallback("Recovered plaintext via lenient AES-256-ECB block decryption (ignored invalid padding / truncated tail)");
               }
               return text;
            }
            if (report != null) {
               report.recordError(
                  "Decrypt Fallback (aes256-ecb-raw)",
                  "Lenient AES-256-ECB output is not valid JSON text (first decrypted bytes: "
                     + ConfigLoadReport.toHexPreview(raw, 16) + "; file is likely encrypted with a different key, custom obfuscator, or severely corrupted)",
                  null
               );
            }
         } catch (Exception e2) {
            if (report != null) {
               report.recordError("Decrypt Fallback (aes256-ecb-raw)", "Lenient block decryption failed", e2);
            }
         }
         return null;
      }

      // 1b. Alternate AE 53 xx xx header (non-standard version bytes)
      if (hasAesMagicPrefix(bytes)) {
         int payloadLen = bytes.length - 4;
         if (report != null) {
            report.recordError(
               "Header",
               "Unexpected encrypted magic header " + ConfigLoadReport.toHexPreview(bytes, 4) + " (expected AE 53 01 00)",
               null
            );
         }
         byte[] raw = CONFIG_CIPHER.decryptRawBlocks(bytes, 4, payloadLen);
         String text = stripBom(new String(raw, StandardCharsets.UTF_8));
         if (containsJsonMarkers(text)) {
            if (report != null) {
               report.setDecryptionMode("aes256-ecb-alt-header-fallback");
               report.markFallback("Recovered config with non-standard AE 53 header via lenient AES-256-ECB fallback");
            }
            return text;
         }
         return null;
      }

      // 1c. UTF-16 BOM detection
      if (bytes.length >= 2) {
         int b0 = bytes[0] & 0xFF;
         int b1 = bytes[1] & 0xFF;
         if (b0 == 0xFF && b1 == 0xFE) {
            String utf16 = stripBom(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE));
            if (containsJsonMarkers(utf16)) {
               if (report != null) {
                  report.setDecryptionMode("utf16le-fallback");
                  report.markFallback("Detected UTF-16LE BOM encoding -> converted to UTF-8");
               }
               return utf16;
            }
         } else if (b0 == 0xFE && b1 == 0xFF) {
            String utf16 = stripBom(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE));
            if (containsJsonMarkers(utf16)) {
               if (report != null) {
                  report.setDecryptionMode("utf16be-fallback");
                  report.markFallback("Detected UTF-16BE BOM encoding -> converted to UTF-8");
               }
               return utf16;
            }
         }
      }

      // 1d. Standard plain UTF-8 JSON
      String utf8 = stripBom(new String(bytes, StandardCharsets.UTF_8));
      String trimmed = utf8.trim();
      if (looksLikeJsonStart(trimmed)) {
         if (report != null) {
            report.setDecryptionMode("plain-utf8");
         }
         return utf8;
      }

      // 2b. Clipboard Base64 / raw Base64 saved directly into a .json file
      String b64Decoded = tryDecodeBase64Wrapper(trimmed, report);
      if (b64Decoded != null) {
         return b64Decoded;
      }

      // 2c. Headerless AES-256-ECB fallback (missing 4-byte AE 53 01 00 prefix)
      if (bytes.length >= 16) {
         byte[] rawNoHeader = CONFIG_CIPHER.decryptRawBlocks(bytes, 0, bytes.length);
         String text = stripBom(new String(rawNoHeader, StandardCharsets.UTF_8));
         if (containsJsonMarkers(text)) {
            if (report != null) {
               report.setDecryptionMode("aes256-ecb-noheader-fallback");
               report.markFallback("Recovered headerless AES-256-ECB payload (4-byte AE 53 01 00 header was missing)");
            }
            return text;
         }
      }

      // 2d. Embedded JSON salvage (e.g., corrupted prefix bytes before '{')
      if (containsJsonMarkers(utf8)) {
         int brace = utf8.indexOf('{');
         if (brace > 0) {
            if (report != null) {
               report.setDecryptionMode("embedded-json-fallback");
               report.markFallback("Skipped " + brace + " non-JSON leading bytes before '{'");
            }
            return utf8.substring(brace);
         }
      }

      if (report != null) {
         report.setDecryptionMode("failed");
         report.recordError(
            "Decrypt",
            "Unrecognized config format (not plain JSON, not Base64, and not decryptable with Augustus AES-256-ECB key; header="
               + ConfigLoadReport.toHexPreview(bytes, 16) + ")",
            null
         );
      }
      return null;
   }

   private static String tryDecodeBase64Wrapper(String trimmed, ConfigLoadReport report) {
      if (trimmed == null || trimmed.isEmpty()) {
         return null;
      }
      String candidate = trimmed;
      if (candidate.startsWith("Name_")) {
         int underscores = 0;
         for (int i = 0; i < candidate.length(); i++) {
            if (candidate.charAt(i) == '_' && ++underscores >= 2) {
               candidate = candidate.substring(i + 1).trim();
               break;
            }
         }
      }
      if (candidate.length() < 16 || candidate.contains("{")) {
         return null;
      }
      try {
         byte[] decoded = Base64.getMimeDecoder().decode(candidate);
         if (decoded != null && decoded.length > 0) {
            if (hasEncryptedHeader(decoded)) {
               byte[] raw = CONFIG_CIPHER.decryptRawBlocks(decoded, ENCRYPTED_MAGIC_HEADER.length, decoded.length - ENCRYPTED_MAGIC_HEADER.length);
               String text = stripBom(new String(raw, StandardCharsets.UTF_8));
               if (containsJsonMarkers(text)) {
                  if (report != null) {
                     report.setDecryptionMode("base64-aes256-fallback");
                     report.markFallback("Decoded Base64-wrapped encrypted config payload");
                  }
                  return text;
               }
            } else {
               String text = stripBom(new String(decoded, StandardCharsets.UTF_8));
               if (containsJsonMarkers(text)) {
                  if (report != null) {
                     report.setDecryptionMode("base64-json-fallback");
                     report.markFallback("Decoded Base64/clipboard-wrapped JSON config payload");
                  }
                  return text;
               }
            }
         }
      } catch (IllegalArgumentException ignored) {
      }
      return null;
   }

   private static String stripBom(String text) {
      if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
         return text.substring(1);
      }
      return text;
   }

   private static boolean looksLikeJsonStart(String text) {
      if (text == null) {
         return false;
      }
      String t = text.trim();
      return t.startsWith("{");
   }

   private static boolean containsJsonMarkers(String text) {
      if (text == null || text.indexOf('{') < 0) {
         return false;
      }
      return text.contains("\"Configuration\"")
         || text.contains("\"Config_Header\"")
         || text.contains("\"M_T\"")
         || text.contains("\"KillAura\"")
         || text.contains("\"HUD\"")
         || text.contains("\"ArrayList\"")
         || text.trim().startsWith("{");
   }

   @Override
   public Class getJsonType() {
      return JsonObject.class;
   }

   @Override
   public void deserialize(JsonElement elem) {
      this.deserializeEmpty((JsonObject)elem);
   }

   public void loadConfig(String configName, String subDir) {
      this.loadConfig(configName, subDir, true);
   }

   public void loadConfig(String configName, String subDir, boolean notify) {
      File file = new File(this.getDirectoryPath() + subDir, configName + ".json");
      ConfigLoadReport report = new ConfigLoadReport(configName, file);
      long startTime = System.currentTimeMillis();

      if (!file.exists()) {
         report.recordError("Read", "File does not exist: " + file.getAbsolutePath(), null);
         report.logToConsole();
         if (notify) {
            this.notifyConfigStatus("Config not found: §c" + configName, NotificationLevel.ERROR);
         }
         return;
      }

      byte[] rawBytes;
      try {
         rawBytes = Files.readAllBytes(file.toPath());
      } catch (Exception e) {
         report.recordError("Read", "IO error reading file " + file.getAbsolutePath(), e);
         report.logToConsole();
         if (notify) {
            this.notifyConfigStatus(
               "Error leyendo config: §c" + configName + " §7(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
               NotificationLevel.ERROR
            );
         }
         return;
      }

      String rawText = decryptWithDiagnostics(rawBytes, report);
      if (rawText == null || rawText.trim().isEmpty()) {
         report.logToConsole();
         if (notify) {
            this.notifyConfigStatus(
               "No se pudo desencriptar/cargar config: §c" + configName + " §7(" + report.getShortFailureReason() + " - revisa la consola)",
               NotificationLevel.ERROR
            );
         }
         return;
      }

      JsonObject json = parseConfigJsonWithFallback(rawText, report);
      if (json == null) {
         report.logToConsole();
         if (notify) {
            this.notifyConfigStatus(
               "Config corrupta (JSON ilegible): §c" + configName + " §7(" + report.getShortFailureReason() + " - revisa la consola)",
               NotificationLevel.ERROR
            );
         }
         return;
      }

      this.applyConfigJson(json, configName, notify, report, startTime);
   }

   /**
    * Parses JSON strictly, then leniently, and if truncated/corrupted mid-file,
    * salvages every intact "ModuleName": { ... } block so the rest can be filled with defaults.
    */
   public static JsonObject parseConfigJsonWithFallback(String rawText, ConfigLoadReport report) {
      if (rawText == null || rawText.trim().isEmpty()) {
         if (report != null) {
            report.recordError("JSON Parse", "Decoded config text is empty", null);
         }
         return null;
      }

      // Stage 1: Strict Gson parse
      try {
         JsonElement elem = new JsonParser().parse(rawText);
         if (elem != null && elem.isJsonObject()) {
            return elem.getAsJsonObject();
         }
         if (report != null) {
            report.recordError("JSON Parse", "Root JSON element is not an object: " + (elem == null ? "null" : elem.getClass().getSimpleName()), null);
         }
      } catch (Exception strictEx) {
         if (report != null) {
            report.recordError("JSON Parse (strict)", "Strict JSON parse failed", strictEx);
         }

         // Stage 2: Lenient Gson parse (handles trailing garbage, comments, unquoted keys, NaN/Infinity)
         try {
            JsonReader reader = new JsonReader(new StringReader(rawText));
            reader.setLenient(true);
            JsonElement elem = new JsonParser().parse(reader);
            if (elem != null && elem.isJsonObject()) {
               if (report != null) {
                  report.markFallback("Recovered JSON via lenient parser (fixed minor syntax/trailing garbage)");
               }
               return elem.getAsJsonObject();
            }
         } catch (Exception lenientEx) {
            if (report != null) {
               report.recordError("JSON Parse (lenient)", "Lenient JSON parse also failed", lenientEx);
            }
         }

         // Stage 3: Partial module-by-module salvage for truncated or partially corrupted JSON
         JsonObject salvaged = recoverPartialConfigJson(rawText, report);
         if (salvaged != null) {
            return salvaged;
         }
      }

      return null;
   }

   private static JsonObject recoverPartialConfigJson(String text, ConfigLoadReport report) {
      JsonObject recoveredConfig = new JsonObject();
      JsonObject recoveredHeader = null;
      int salvagedCount = 0;
      int corruptedBlocks = 0;

      // Try to extract Config_Header if intact
      int headerKeyIdx = text.indexOf("\"Config_Header\"");
      if (headerKeyIdx >= 0) {
         int objStart = findValueObjectStart(text, headerKeyIdx + "\"Config_Header\"".length());
         if (objStart >= 0) {
            int objEnd = findMatchingBrace(text, objStart);
            if (objEnd > objStart) {
               String headerSlice = text.substring(objStart, objEnd + 1);
               try {
                  JsonReader r = new JsonReader(new StringReader(headerSlice));
                  r.setLenient(true);
                  JsonElement el = new JsonParser().parse(r);
                  if (el != null && el.isJsonObject()) {
                     recoveredHeader = el.getAsJsonObject();
                  }
               } catch (Exception e) {
                  if (report != null) {
                     report.recordError("JSON Salvage (Config_Header)", "Could not parse Config_Header block", e);
                  }
               }
            }
         }
      }

      // Locate "Configuration": { ... } or fall back to root '{'
      int scanStart = -1;
      int cfgKeyIdx = text.indexOf("\"Configuration\"");
      if (cfgKeyIdx >= 0) {
         scanStart = findValueObjectStart(text, cfgKeyIdx + "\"Configuration\"".length());
      }
      if (scanStart < 0) {
         scanStart = text.indexOf('{');
      }
      if (scanStart < 0) {
         return null;
      }

      // Scan top-level "Key": { ... } entries inside Configuration
      int i = scanStart + 1;
      int len = text.length();
      while (i < len) {
         i = skipWhitespaceAndCommas(text, i);
         if (i >= len || text.charAt(i) == '}') {
            break;
         }
         if (text.charAt(i) != '"') {
            i++;
            continue;
         }
         int keyEnd = findStringEnd(text, i);
         if (keyEnd < 0) {
            if (report != null) {
               report.recordError("JSON Salvage", "Unterminated key string at char offset " + i, null);
            }
            break;
         }
         String key = text.substring(i + 1, keyEnd);
         int colon = skipWhitespace(text, keyEnd + 1);
         if (colon >= len || text.charAt(colon) != ':') {
            i = keyEnd + 1;
            continue;
         }
         int valStart = skipWhitespace(text, colon + 1);
         if (valStart >= len) {
            if (report != null) {
               report.recordError("JSON Salvage", "Truncated value for entry '" + key + "' at EOF (char " + valStart + ")", null);
            }
            break;
         }
         if (text.charAt(valStart) == '{') {
            int valEnd = findMatchingBrace(text, valStart);
            if (valEnd < 0) {
               corruptedBlocks++;
               if (report != null) {
                  report.recordError("JSON Salvage", "Truncated/unclosed JSON object for module '" + key + "' starting at char " + valStart + " (filled with defaults)", null);
               }
               break;
            }
            if (!"Config_Header".equals(key)) {
               String slice = text.substring(valStart, valEnd + 1);
               try {
                  JsonReader r = new JsonReader(new StringReader(slice));
                  r.setLenient(true);
                  JsonElement parsed = new JsonParser().parse(r);
                  if (parsed != null && parsed.isJsonObject()) {
                     recoveredConfig.add(key, parsed.getAsJsonObject());
                     salvagedCount++;
                  }
               } catch (Exception e) {
                  corruptedBlocks++;
                  if (report != null) {
                     report.recordError("JSON Salvage (Module '" + key + "')", "Corrupted JSON inside module block (char " + valStart + ".." + valEnd + ")", e);
                  }
               }
            }
            i = valEnd + 1;
         } else {
            // Skip primitive or array value
            int next = skipNonObjectValue(text, valStart);
            if (next <= valStart) {
               break;
            }
            i = next;
         }
      }

      if (salvagedCount == 0) {
         if (report != null) {
            report.recordError("JSON Salvage", "0 intact module blocks could be recovered from corrupted JSON", null);
         }
         return null;
      }

      JsonObject result = new JsonObject();
      if (recoveredHeader == null) {
         recoveredHeader = new JsonObject();
         recoveredHeader.addProperty("Config_Version", Integer.valueOf(2));
         recoveredHeader.addProperty("Config_Editable", "true");
      } else if (!recoveredHeader.has("Config_Version") && !recoveredHeader.has("ConfigVersion")) {
         recoveredHeader.addProperty("Config_Version", Integer.valueOf(2));
      }
      result.add("Config_Header", recoveredHeader);
      result.add("Configuration", recoveredConfig);
      if (report != null) {
         report.markFallback(
            "Partial JSON recovery salvaged " + salvagedCount + " intact module block(s)"
               + (corruptedBlocks > 0 ? " (" + corruptedBlocks + " corrupted/truncated block(s) skipped and filled with defaults)" : "")
         );
      }
      return result;
   }

   private static int skipWhitespace(String s, int idx) {
      int n = s.length();
      while (idx < n && Character.isWhitespace(s.charAt(idx))) {
         idx++;
      }
      return idx;
   }

   private static int skipWhitespaceAndCommas(String s, int idx) {
      int n = s.length();
      while (idx < n) {
         char c = s.charAt(idx);
         if (Character.isWhitespace(c) || c == ',') {
            idx++;
         } else {
            break;
         }
      }
      return idx;
   }

   private static int findValueObjectStart(String s, int afterKeyIdx) {
      int colon = skipWhitespace(s, afterKeyIdx);
      if (colon >= s.length() || s.charAt(colon) != ':') {
         return -1;
      }
      int val = skipWhitespace(s, colon + 1);
      return val < s.length() && s.charAt(val) == '{' ? val : -1;
   }

   private static int findStringEnd(String s, int quoteStart) {
      boolean escaped = false;
      for (int i = quoteStart + 1; i < s.length(); i++) {
         char c = s.charAt(i);
         if (escaped) {
            escaped = false;
         } else if (c == '\\') {
            escaped = true;
         } else if (c == '"') {
            return i;
         }
      }
      return -1;
   }

   private static int findMatchingBrace(String s, int openIdx) {
      int depth = 0;
      boolean inString = false;
      boolean escaped = false;
      for (int i = openIdx; i < s.length(); i++) {
         char c = s.charAt(i);
         if (inString) {
            if (escaped) {
               escaped = false;
            } else if (c == '\\') {
               escaped = true;
            } else if (c == '"') {
               inString = false;
            }
         } else {
            if (c == '"') {
               inString = true;
            } else if (c == '{') {
               depth++;
            } else if (c == '}') {
               depth--;
               if (depth == 0) {
                  return i;
               }
            }
         }
      }
      return -1;
   }

   private static int skipNonObjectValue(String s, int start) {
      int n = s.length();
      char c = s.charAt(start);
      if (c == '"') {
         int end = findStringEnd(s, start);
         return end < 0 ? n : end + 1;
      }
      if (c == '[') {
         int depth = 0;
         boolean inString = false;
         boolean escaped = false;
         for (int i = start; i < n; i++) {
            char ch = s.charAt(i);
            if (inString) {
               if (escaped) escaped = false;
               else if (ch == '\\') escaped = true;
               else if (ch == '"') inString = false;
            } else {
               if (ch == '"') inString = true;
               else if (ch == '[') depth++;
               else if (ch == ']') {
                  depth--;
                  if (depth == 0) return i + 1;
               }
            }
         }
         return n;
      }
      int i = start;
      while (i < n) {
         char ch = s.charAt(i);
         if (ch == ',' || ch == '}' || ch == ']') {
            break;
         }
         i++;
      }
      return i;
   }

   public JsonObject buildConfigJson(String configName, boolean editable) {
      return this.formatCodecs.get(2).encodeConfig(configName, Augustus.getInstance().getUsername(), editable);
   }

   public JsonObject readConfigJson(String subDir, String configName) {
      File file = new File(this.getDirectoryPath() + subDir, configName + ".json");
      if (!file.exists()) {
         return null;
      }
      try {
         byte[] bytes = Files.readAllBytes(file.toPath());
         ConfigLoadReport silent = new ConfigLoadReport(configName, file);
         String text = decryptWithDiagnostics(bytes, silent);
         if (text == null || text.trim().isEmpty()) {
            return null;
         }
         return parseConfigJsonWithFallback(text, silent);
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public boolean isBackupEnabled() {
      return false;
   }

   public void applyConfigJson(JsonObject json, String configName, boolean notify) {
      ConfigLoadReport report = new ConfigLoadReport(configName, null);
      this.applyConfigJson(json, configName, notify, report, System.currentTimeMillis());
   }

   private void applyConfigJson(JsonObject json, String configName, boolean notify, ConfigLoadReport report, long startTime) {
      if (json == null) {
         return;
      }
      Integer version = this.readConfigVersion(json);
      if (version == null) {
         report.markFallback("Config_Version missing in Config_Header -> falling back to ConfigFormatV2 (version 2)");
         version = Integer.valueOf(2);
      }
      ModuleConfigCodec codec = this.formatCodecs.get(version);
      if (codec == null) {
         report.markFallback("Unsupported Config_Version (" + version + ") -> attempting partial load with ConfigFormatV2");
         codec = this.formatCodecs.get(2);
      }
      final ModuleConfigCodec activeCodec = codec;
      try {
         if (activeCodec instanceof ConfigFormatV2) {
            ((ConfigFormatV2)activeCodec).applyConfig(json, report);
         } else {
            ConfigLoadReport.runWithCurrent(report, () -> activeCodec.applyConfig(json));
         }
         long elapsed = System.currentTimeMillis() - startTime;

         if (report.getLoadedModules() == 0 && report.hasAnyIssue()) {
            report.logToConsole();
            if (notify) {
               this.notifyConfigStatus(
                  "No se pudo cargar ningún módulo de: §c" + configName + " §7(" + report.getShortFailureReason() + " - revisa la consola)",
                  NotificationLevel.ERROR
               );
            }
            return;
         }

         if (Augustus.getInstance().getModuleManager() != null) {
            Augustus.getInstance().getModuleManager().sortModules();
         }

         if (Augustus.getInstance().getConfigManager() != null) {
            Augustus.getInstance().getConfigManager().saveAllAsync();
         }

         if (report.hasAnyIssue()) {
            report.logToConsole();
            if (notify) {
               String summary = "Config cargada parcialmente (fallback): §e" + configName
                  + " §7[" + report.getLoadedModules() + " cargados, "
                  + report.getDefaultedModules() + " en default"
                  + (report.getTotalErrors() > 0 ? ", " + report.getTotalErrors() + " errores" : "")
                  + " - revisa la consola]";
               this.notifyConfigStatus(summary, NotificationLevel.WARNING);
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
            }
         } else if (notify) {
            this.notifyConfigStatus("Config loaded: §c" + configName + " (" + elapsed + "ms)", NotificationLevel.INFO);
            ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
         }
      } catch (Exception e) {
         report.recordError("ApplyConfig", "Unhandled exception applying config", e);
         report.logToConsole();
         if (notify) {
            this.notifyConfigStatus(
               "Error aplicando config: §c" + configName + " §7(" + e.getClass().getSimpleName() + ": " + e.getMessage() + " - revisa la consola)",
               NotificationLevel.ERROR
            );
         }
      }
   }

   public void saveConfig(String configName, String subDir, boolean editable) {
      try {
         JsonObject json = this.buildConfigJson(configName, editable);
         if (json == null) {
            System.err.println("[OpenAugustus-Config] Failed to build JSON when saving config: " + configName);
            this.notifyConfigStatus("Failed to save config: §c" + configName + " §7(null JSON)", NotificationLevel.ERROR);
            return;
         }
         File file = new File(this.getDirectoryPath() + subDir, configName + ".json");
         long startTime = System.currentTimeMillis();
         this.writeJsonFile(file, json);
         long elapsed = System.currentTimeMillis() - startTime;
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
         this.notifyConfigStatus("Config saved: §c" + configName + " (" + elapsed + "ms)", NotificationLevel.INFO);
      } catch (Exception e) {
         System.err.println("[OpenAugustus-Config] Failed to save config '" + configName + "': " + e);
         e.printStackTrace();
         this.notifyConfigStatus("Failed to save config: §c" + configName + " §7(" + e.getMessage() + ")", NotificationLevel.ERROR);
      }
   }

   public JsonObject serializeEmpty() {
      return null;
   }

   @Override
   public JsonElement serialize() {
      return this.serializeEmpty();
   }

   private void notifyConfigStatus(String message, NotificationLevel level) {
      ChatUtil.sendChat(message);
      if (!Augustus.getInstance().getModuleManager().notifications.isToggled()) {
         return;
      }
      if (!NotificationCategoryOption.CONFIG_SYSTEM.isSelected()) {
         return;
      }
      if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
         NotificationManager.pushNotification("Configsystem", message, level);
         return;
      }
      PacketThreadUtil.runOnMainThreadImmediate(() -> showNotification(message, level));
   }

   @Override
   public String getName() {
      return "";
   }

   @Override
   public String getSubDirectory() {
      return "";
   }

   public void deserializeEmpty(JsonObject json) {
   }

   @Override
   public boolean isAutoManaged() {
      return false;
   }

   private static void showNotification(String message, NotificationLevel level) {
      NotificationManager.pushNotification("Configsystem", message, level);
   }

   public String[] getConfigHeaderInfo(String configName, String subDir) {
      JsonObject json = this.readConfigJson(subDir, configName);
      if (json == null) {
         return null;
      }
      Integer version = this.readConfigVersion(json);
      if (version == null) {
         version = Integer.valueOf(2);
      }
      ModuleConfigCodec codec = this.formatCodecs.get(version);
      if (codec == null) {
         codec = this.formatCodecs.get(2);
      }
      return codec.readHeaderInfo(json);
   }

   private Integer readConfigVersion(JsonObject json) {
      if (json.has("Config_Header") && json.get("Config_Header").isJsonObject()) {
         JsonObject header = json.getAsJsonObject("Config_Header");
         try {
            if (header.has("Config_Version")) {
               return header.get("Config_Version").getAsInt();
            }
            if (header.has("ConfigVersion")) {
               return header.get("ConfigVersion").getAsInt();
            }
         } catch (Exception ignored) {
         }
      }
      return null;
   }
}
