package net.augustus.config;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigManager {
   public static final ArrayList CONFIG_FILES = new ArrayList();
   private final Object ioLock = new Object();
   private volatile boolean shuttingDown = false;
   private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "OpenAugustus-ConfigSaver");
      t.setDaemon(true);
      return t;
   });
   public final ModuleSettingsConfigSection moduleSettingsConfig;
   public final AccountConfigStorage accountConfig;
   public final ProxyConfigStorage proxyConfig;
   public final ClientSettingsConfigFile clientSettingsConfig;
   public final ModuleConfigStorage moduleConfigStorage;

   public ConfigManager() {
      super();
      this.moduleSettingsConfig = new ModuleSettingsConfigSection();
      this.moduleConfigStorage = new ModuleConfigStorage();
      this.proxyConfig = new ProxyConfigStorage();
      this.accountConfig = new AccountConfigStorage();
      this.clientSettingsConfig = new ClientSettingsConfigFile();
   }

   public void loadAll() {
      ((List<JsonConfigFile>)CONFIG_FILES).forEach((JsonConfigFile jsonConfigFile) -> {
         if (jsonConfigFile.isAutoManaged()) {
            jsonConfigFile.load();
         }
      });
   }

   public void saveAll() {
      synchronized (this.ioLock) {
         ((List<JsonConfigFile>)CONFIG_FILES).forEach((JsonConfigFile jsonConfigFile) -> saveConfigFile(jsonConfigFile));
      }
   }

   public void saveAllForShutdown() {
      this.shuttingDown = true;
      this.saveAll();
   }

   public void saveAllAsync() {
      if (this.shuttingDown) {
         return;
      }

      Map<JsonConfigFile, JsonElement> snapshots = new LinkedHashMap<>();
      for (JsonConfigFile jsonConfigFile : (List<JsonConfigFile>)CONFIG_FILES) {
         if (jsonConfigFile.isAutoManaged()) {
            jsonConfigFile.captureSnapshot();
            JsonElement snapshot = jsonConfigFile.getCachedJson();
            if (snapshot != null) {
               snapshots.put(jsonConfigFile, snapshot);
            }
         }
      }

      if (snapshots.isEmpty()) {
         return;
      }

      this.saveExecutor.execute(() -> {
         synchronized (this.ioLock) {
            if (this.shuttingDown) {
               return;
            }
            for (Map.Entry<JsonConfigFile, JsonElement> entry : snapshots.entrySet()) {
               try {
                  entry.getKey().writeSnapshot(entry.getValue());
               } catch (Throwable t) {
                  t.printStackTrace();
               }
            }
         }
      });
   }

   private static void saveConfigFile(JsonConfigFile jsonConfigFile) {
      if (jsonConfigFile.isAutoManaged()) {
         jsonConfigFile.save(true);
      }
   }

   public void snapshotAll() {
      ((List<JsonConfigFile>)CONFIG_FILES).forEach((JsonConfigFile jsonConfigFile) -> snapshotConfigFile(jsonConfigFile));
   }

   private static void snapshotConfigFile(JsonConfigFile jsonConfigFile) {
      if (jsonConfigFile.isAutoManaged()) {
         jsonConfigFile.captureSnapshot();
      }
   }

}
