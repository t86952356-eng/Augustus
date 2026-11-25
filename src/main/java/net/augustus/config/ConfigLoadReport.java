package net.augustus.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks exact decryption, JSON parsing, module recovery, and setting-level errors
 * during a config load so both in-game warnings and exact console diagnostics are always reported.
 */
public final class ConfigLoadReport {
   private static final Logger LOGGER = LogManager.getLogger("OpenAugustus-Config");
   private static final ThreadLocal<ConfigLoadReport> ACTIVE = new ThreadLocal<>();

   private final String configName;
   private final File file;
   private long fileSize = -1L;
   private String headerHex = "<none>";
   private String decryptionMode = "unknown";
   private boolean usedFallback = false;
   private int loadedModules = 0;
   private int defaultedModules = 0;
   private int failedModules = 0;
   private int failedSettings = 0;
   private String shortFailureReason = "unknown error";
   private Throwable primaryCause = null;
   private final List<String> exactErrors = new ArrayList<>();
   private final List<String> warnings = new ArrayList<>();

   public ConfigLoadReport(String configName, File file) {
      this.configName = configName != null ? configName : "<unnamed>";
      this.file = file;
   }

   public static ConfigLoadReport current() {
      return ACTIVE.get();
   }

   public static void runWithCurrent(ConfigLoadReport report, Runnable task) {
      ConfigLoadReport prev = ACTIVE.get();
      ACTIVE.set(report);
      try {
         task.run();
      } finally {
         if (prev == null) {
            ACTIVE.remove();
         } else {
            ACTIVE.set(prev);
         }
      }
   }

   public void inspectRawBytes(byte[] bytes) {
      if (bytes == null) {
         this.fileSize = 0L;
         this.headerHex = "<null>";
         return;
      }
      this.fileSize = bytes.length;
      this.headerHex = toHexPreview(bytes, 16);
   }

   public static String toHexPreview(byte[] bytes, int maxBytes) {
      if (bytes == null || bytes.length == 0) {
         return "<empty>";
      }
      int n = Math.min(bytes.length, maxBytes);
      StringBuilder sb = new StringBuilder(n * 3);
      for (int i = 0; i < n; i++) {
         if (i > 0) sb.append(' ');
         sb.append(String.format("%02X", bytes[i] & 0xFF));
      }
      if (bytes.length > n) {
         sb.append(" ...");
      }
      return sb.toString();
   }

   public void setDecryptionMode(String mode) {
      this.decryptionMode = mode;
   }

   public void markFallback(String warning) {
      this.usedFallback = true;
      if (warning != null && !warning.isEmpty()) {
         this.warnings.add(warning);
      }
   }

   public void recordError(String stage, String detail, Throwable cause) {
      String msg = "[" + stage + "] " + detail;
      if (cause != null) {
         msg += " -> " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
         if (this.primaryCause == null) {
            this.primaryCause = cause;
         }
      }
      this.exactErrors.add(msg);
      if (this.shortFailureReason.equals("unknown error")) {
         this.shortFailureReason = cause != null && cause.getMessage() != null
            ? stage + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage()
            : stage + ": " + detail;
      }
   }

   public void setShortFailureReason(String reason) {
      if (reason != null && !reason.isEmpty()) {
         this.shortFailureReason = reason;
      }
   }

   public void recordLoadedModule(String moduleName) {
      this.loadedModules++;
   }

   public void recordDefaultedModule(String moduleName) {
      this.defaultedModules++;
   }

   public void recordFailedModule(String moduleName, String detail, Throwable cause) {
      this.failedModules++;
      this.usedFallback = true;
      this.recordError("Module '" + moduleName + "'", detail, cause);
   }

   public void recordFailedSetting(String path, String detail, Throwable cause) {
      this.failedSettings++;
      this.usedFallback = true;
      this.recordError("Setting '" + path + "'", detail, cause);
   }

   public boolean isUsedFallback() {
      return this.usedFallback;
   }

   public boolean hasAnyIssue() {
      return this.usedFallback || !this.exactErrors.isEmpty() || !this.warnings.isEmpty() || this.failedModules > 0 || this.failedSettings > 0;
   }

   public int getLoadedModules() {
      return this.loadedModules;
   }

   public int getDefaultedModules() {
      return this.defaultedModules;
   }

   public int getFailedModules() {
      return this.failedModules;
   }

   public int getFailedSettings() {
      return this.failedSettings;
   }

   public int getTotalErrors() {
      return this.exactErrors.size();
   }

   public String getShortFailureReason() {
      if (this.shortFailureReason.length() > 140) {
         return this.shortFailureReason.substring(0, 137) + "...";
      }
      return this.shortFailureReason;
   }

   public String formatDiagnosticReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("\n================ [OpenAugustus Config Diagnostic] ================\n");
      sb.append("Config:      ").append(this.configName).append('\n');
      sb.append("File:        ").append(this.file != null ? this.file.getAbsolutePath() : "<in-memory>");
      if (this.fileSize >= 0) {
         sb.append(" (").append(this.fileSize).append(" bytes)");
      }
      sb.append('\n');
      sb.append("HeaderHex:   ").append(this.headerHex).append('\n');
      sb.append("DecryptMode: ").append(this.decryptionMode).append(this.usedFallback ? " [FALLBACK]" : "").append('\n');
      sb.append("Modules:     loaded=").append(this.loadedModules)
         .append(", defaulted=").append(this.defaultedModules)
         .append(", failed=").append(this.failedModules)
         .append(", failedSettings=").append(this.failedSettings).append('\n');
      if (!this.warnings.isEmpty()) {
         sb.append("Warnings (").append(this.warnings.size()).append("):\n");
         for (int i = 0; i < this.warnings.size(); i++) {
            sb.append("  [W").append(i + 1).append("] ").append(this.warnings.get(i)).append('\n');
         }
      }
      if (!this.exactErrors.isEmpty()) {
         sb.append("Exact Errors (").append(this.exactErrors.size()).append("):\n");
         int limit = Math.min(this.exactErrors.size(), 50);
         for (int i = 0; i < limit; i++) {
            sb.append("  [E").append(i + 1).append("] ").append(this.exactErrors.get(i)).append('\n');
         }
         if (this.exactErrors.size() > limit) {
            sb.append("  ... (+").append(this.exactErrors.size() - limit).append(" more errors)\n");
         }
      }
      sb.append("==================================================================");
      return sb.toString();
   }

   public void logToConsole() {
      String text = this.formatDiagnosticReport();
      System.err.println(text);
      try {
         if (this.primaryCause != null) {
            LOGGER.error(text, this.primaryCause);
            this.primaryCause.printStackTrace(System.err);
         } else {
            LOGGER.warn(text);
         }
      } catch (Throwable ignored) {
         if (this.primaryCause != null) {
            this.primaryCause.printStackTrace(System.err);
         }
      }
   }
}
