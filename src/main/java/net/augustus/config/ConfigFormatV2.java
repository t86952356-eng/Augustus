package net.augustus.config;

import net.augustus.Augustus;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.modules.render.ClickGui;
import net.augustus.settings.property.ConfigProperty;
import net.augustus.settings.property.codec.JsonCodecRegistryHolder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Map.Entry;
import java.util.Set;
import net.augustus.modules.AbstractFeature;
import net.augustus.modules.FeatureBase;
import net.augustus.modules.Module;
import net.augustus.modules.ModuleManager;

public class ConfigFormatV2 implements ModuleConfigCodec {

   @Override
   public String[] readHeaderInfo(JsonObject json) {
      if (json == null || !json.has("Config_Header") || !json.get("Config_Header").isJsonObject()) {
         return null;
      }
      JsonObject header = json.getAsJsonObject("Config_Header");
      String name = header.has("Config_Name") ? header.get("Config_Name").getAsString() : "";
      String date = header.has("Config_Date") ? header.get("Config_Date").getAsString() : "";
      String time = header.has("Config_Time") ? header.get("Config_Time").getAsString() : "";
      String editable = header.has("Config_Editable") ? header.get("Config_Editable").getAsString() : "true";
      String author = header.has("Config_Author") ? header.get("Config_Author").getAsString() : "";
      return new String[]{name, date, time, editable, author};
   }

   private static Boolean shouldLoadProperty(ConfigProperty configProperty) {
      FeatureBase featureBase = configProperty.getFeatureBase();
      if (!(featureBase instanceof Module)) {
         return true;
      } else {
         Module module = (Module)featureBase;
         if (!(configProperty instanceof AbstractFeature)) {
            String propName = configProperty.getName();
            if ("M_T".equals(propName)) {
               return !(module instanceof ClickGui) && module.isLoadFromConfig();
            } else if ("M_A".equals(propName)) {
               return module.isLoadFromConfig();
            } else if ("M_K".equals(propName)) {
               return Augustus.getInstance().getModuleManager().clientSettings.loadKeysFromConfigs.getValue()
                  && module.isLoadFromConfig();
            } else {
               return false;
            }
         } else {
            String name = configProperty.getName();
            ModuleManager moduleManager = Augustus.getInstance().getModuleManager();
            if (name.endsWith("Groupygroupy")) {
               return false;
            } else if (name.contains("Blur")) {
               return moduleManager.blur.isLoadFromConfig();
            } else if (name.contains("Shadow")) {
               return moduleManager.shadow.isLoadFromConfig();
            } else {
               return module.isLoadFromConfig();
            }
         }
      }
   }

   @Override
   public JsonObject encodeConfig(String str, String str2, boolean flag) {
      JsonObject json = new JsonObject();
      JsonObject json2 = new JsonObject();
      GregorianCalendar gregorianCalendar = new GregorianCalendar();
      DateFormat dateInstance = DateFormat.getDateInstance(2);
      DateFormat timeInstance = DateFormat.getTimeInstance(3);
      json2.addProperty("Config_Version", Integer.valueOf(2));
      json2.addProperty("Config_Name", str);
      json2.addProperty("Config_Date", dateInstance.format(gregorianCalendar.getTime()));
      json2.addProperty("Config_Time", timeInstance.format(gregorianCalendar.getTime()));
      json2.addProperty("Config_Editable", String.valueOf(flag));
      json2.addProperty("Config_Author", str2 != null && str2.length() != 0 ? str2 : "No_Author");
      json.add("Config_Header", json2);
      JsonObject json3 = ModuleSettingsConfigSection.abstractFeature.serialize(JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER, new JsonObject(), var -> shouldSaveProperty((ConfigProperty)var));
      json.add("Configuration", json3);
      return json;
   }

   private static Boolean shouldSaveProperty(ConfigProperty configProperty) {
      if (!(configProperty instanceof Module)) {
         return true;
      } else {
         Module module = (Module)configProperty;
         return !module.isHiddenFromConfig() && !module.getCategorys().special;
      }
   }

   @Override
   public void applyConfig(JsonObject json) {
      this.applyConfig(json, ConfigLoadReport.current());
   }

   public void applyConfig(JsonObject json, ConfigLoadReport report) {
      if (json == null) {
         return;
      }
      JsonObject configSection = null;
      JsonElement elem = json.get("Configuration");
      if (elem != null && elem.isJsonObject()) {
         configSection = elem.getAsJsonObject();
      } else if (looksLikeFlatModuleMap(json)) {
         configSection = json;
         if (report != null) {
            report.markFallback("Missing 'Configuration' wrapper -> loaded root JSON directly as module map");
         }
      } else {
         if (report != null) {
            report.recordError("ConfigFormatV2", "Missing or invalid 'Configuration' JSON object in config", null);
         }
         return;
      }

      boolean editable = true;
      JsonElement headerElem = json.get("Config_Header");
      if (headerElem != null && headerElem.isJsonObject()) {
         JsonObject headerObj = headerElem.getAsJsonObject();
         editable = !headerObj.has("Config_Editable") || Boolean.parseBoolean(headerObj.get("Config_Editable").getAsString());
      } else if (report != null) {
         report.markFallback("Missing 'Config_Header' -> defaulted to Config_Version=2, Editable=true");
      }

      Augustus.getInstance().setConfigEditingEnabled(true);
      ClickGuiScreen.configEditable = editable;
      ClickGuiScreen.protectedConfigCreator = false;
      final JsonObject targetSection = configSection;
      ConfigLoadReport.runWithCurrent(report, () -> ModuleSettingsConfigSection.abstractFeature.deserialize(
         JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER,
         targetSection,
         true,
         var -> shouldLoadProperty((ConfigProperty)var)
      ));
   }

   private static boolean looksLikeFlatModuleMap(JsonObject json) {
      if (json == null || json.entrySet().isEmpty()) {
         return false;
      }
      for (Object obj : Augustus.getInstance().getModuleManager().getModules()) {
         if (obj instanceof Module) {
            String name = ((Module)obj).getName();
            if (name != null && json.has(name) && json.get(name).isJsonObject()) {
               return true;
            }
         }
      }
      return false;
   }

}
