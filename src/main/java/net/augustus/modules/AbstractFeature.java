package net.augustus.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.augustus.config.ConfigLoadReport;
import net.augustus.settings.Setting;
import net.augustus.settings.property.AbstractConfigProperty;
import net.augustus.settings.property.ConfigProperty;
import net.augustus.settings.property.ConfigSerializerProvider;
import net.augustus.utils.UnaryFunction;

public class AbstractFeature extends FeatureBase implements ConfigProperty {
   private FeatureBase featureBase = null;

   @Override
   public JsonObject serialize(ConfigSerializerProvider configSerializerProvider, JsonObject json, UnaryFunction unaryFunction) {
      this.onBeforeSerialize();
      JsonObject json2 = new JsonObject();
      this.getProperties().forEach(obj -> {
         if ((Boolean)unaryFunction.apply(obj)) {
            ((ConfigProperty)obj).serialize(configSerializerProvider, json2, unaryFunction);
         }
      });
      if (this.getName() != null && json != null) {
         json.add(this.getName(), json2);
         return json;
      } else {
         return json2;
      }
   }

   public void onAfterDeserialize() {
   }

   public void onBeforeSerialize() {
   }

   @Override
   public void deserialize(ConfigSerializerProvider configSerializerProvider, JsonObject json, boolean flag, UnaryFunction unaryFunction) {
      ConfigLoadReport report = ConfigLoadReport.current();
      JsonObject json2;
      if (this.getName() != null) {
         JsonElement elem = json != null ? json.get(this.getName()) : null;
         if (elem == null || elem.isJsonNull()) {
            if (flag) {
               json2 = new JsonObject();
               if (report != null && this instanceof Module && ((Module)this).isLoadFromConfig()) {
                  report.recordDefaultedModule(this.getName());
               }
            } else {
               json2 = json != null ? json : new JsonObject();
            }
         } else if (!elem.isJsonObject()) {
            if (report != null) {
               String detail = "Expected JSON object, found " + elem.getClass().getSimpleName() + ": " + elem;
               if (this instanceof Module) {
                  report.recordFailedModule(this.getName(), detail, null);
               } else {
                  report.recordFailedSetting(this.buildFeaturePath(), detail, null);
               }
            }
            if (flag) {
               json2 = new JsonObject();
            } else {
               return;
            }
         } else {
            json2 = elem.getAsJsonObject();
            if (report != null && this instanceof Module && ((Module)this).isLoadFromConfig()) {
               report.recordLoadedModule(this.getName());
            }
         }
      } else {
         json2 = json != null ? json : new JsonObject();
      }

      for (int i = this.getProperties().size() - 1; i >= 0; i--) {
         ConfigProperty configProperty = (ConfigProperty)this.getProperties().get(i);
         try {
            if ((Boolean)unaryFunction.apply(configProperty)) {
               configProperty.deserialize(configSerializerProvider, json2, flag, unaryFunction);
            }
         } catch (Throwable t) {
            String path = this.buildPropertyPath(configProperty);
            JsonElement rawElem = configProperty.getName() != null && json2.has(configProperty.getName())
               ? json2.get(configProperty.getName())
               : null;
            String detail = "Failed to deserialize" + (rawElem != null ? " (json=" + rawElem + ")" : "");
            if (report != null) {
               if (configProperty instanceof Module) {
                  report.recordFailedModule(configProperty.getName(), detail, t);
               } else {
                  report.recordFailedSetting(path, detail, t);
               }
            } else {
               System.err.println("[OpenAugustus-Config] Error deserializing " + path + ": " + t);
               t.printStackTrace();
            }
            if (flag) {
               try {
                  if (configProperty instanceof AbstractConfigProperty) {
                     ((AbstractConfigProperty)configProperty).resetToDefault();
                  } else if (configProperty instanceof Setting) {
                     ((Setting)configProperty).resetToDefault();
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      }

      try {
         this.onAfterDeserialize();
      } catch (Throwable t) {
         if (report != null) {
            report.recordFailedSetting(this.buildFeaturePath(), "onAfterDeserialize failed", t);
         } else {
            t.printStackTrace();
         }
      }
   }

   private String buildFeaturePath() {
      if (this.featureBase != null && this.featureBase.getName() != null) {
         return this.featureBase.getName() + "." + this.getName();
      }
      return this.getName() != null ? this.getName() : "<root>";
   }

   private String buildPropertyPath(ConfigProperty prop) {
      String propName = prop != null && prop.getName() != null ? prop.getName() : "<prop>";
      String parent = this.buildFeaturePath();
      return parent.equals("<root>") ? propName : parent + "." + propName;
   }

   @Override
   public FeatureBase getFeatureBase() {
      return this.featureBase;
   }

   public AbstractFeature(FeatureBase featureBase2, String str) {
      super(str);
      this.featureBase = featureBase2;
      if (featureBase2 != null) {
         featureBase2.addProperty(this);
      }
   }

   @Override
   public void setFeatureBase(FeatureBase featureBase2) {
      this.featureBase = featureBase2;
   }

   public AbstractFeature(String str) {
      super(str);
   }
}
