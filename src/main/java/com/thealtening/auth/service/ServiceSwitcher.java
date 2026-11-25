package com.thealtening.auth.service;

import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;

public final class ServiceSwitcher {

   private static void setStaticField(Class<?> targetClass, String fieldName, Object value) throws Exception {
      Field field = targetClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
      field.set(null, value);
   }

   public ServiceSwitcher() {
      try {
         setStaticField(
            YggdrasilMinecraftSessionService.class,
            "WHITELISTED_DOMAINS",
            new String[]{".minecraft.net", ".mojang.com", ".thealtening.com"}
         );
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public AlteningServiceType switchToService(AlteningServiceType alteningServiceType) {
      try {
         String authServer = alteningServiceType.getAuthServer();
         setStaticField(YggdrasilUserAuthentication.class, "BASE_URL", authServer);
         setStaticField(YggdrasilUserAuthentication.class, "ROUTE_AUTHENTICATE", new URL(authServer + "authenticate"));
         setStaticField(YggdrasilUserAuthentication.class, "ROUTE_INVALIDATE", new URL(authServer + "invalidate"));
         setStaticField(YggdrasilUserAuthentication.class, "ROUTE_REFRESH", new URL(authServer + "refresh"));
         setStaticField(YggdrasilUserAuthentication.class, "ROUTE_VALIDATE", new URL(authServer + "validate"));
         setStaticField(YggdrasilUserAuthentication.class, "ROUTE_SIGNOUT", new URL(authServer + "signout"));
         String sessionServer = alteningServiceType.getSessionServer();
         setStaticField(YggdrasilMinecraftSessionService.class, "BASE_URL", sessionServer + "session/minecraft/");
         setStaticField(YggdrasilMinecraftSessionService.class, "JOIN_URL", new URL(sessionServer + "session/minecraft/join"));
         setStaticField(YggdrasilMinecraftSessionService.class, "CHECK_URL", new URL(sessionServer + "session/minecraft/hasJoined"));
         return alteningServiceType;
      } catch (Exception e) {
         e.printStackTrace();
         return AlteningServiceType.MOJANG;
      }
   }
}
