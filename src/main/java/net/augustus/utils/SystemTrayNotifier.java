package net.augustus.utils;

import net.augustus.Augustus;
import net.augustus.ui.UiIconResources;
import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.imageio.ImageIO;

public class SystemTrayNotifier {
   private static TrayIcon trayIcon;

   public static void displayNotification(String str, String str2) {
      try {
         if (trayIcon != null) {
            trayIcon.displayMessage(str, str2, MessageType.INFO);
         }
      } catch (Throwable t) {
      }
   }

   public static void init() {
      // Android build: java.awt SystemTray does not exist on Android (headless
      // JVM). Guard everything so client initialization never breaks.
      try {
         if (!SystemTray.isSupported()) {
            return;
         }
      } catch (Throwable t) {
         return;
      }

      try {
         trayIcon = new TrayIcon(
            ImageIO.read(
               (InputStream)Objects.requireNonNull(
                  UiIconResources.class.getClassLoader().getResourceAsStream("assets/augustus/pictures/logoWindows.png")
               )
            ),
            "Augustus Message"
         );
      } catch (Throwable e) {
      }

      try {
         SystemTray systemTray = SystemTray.getSystemTray();
         trayIcon.setImageAutoSize(true);
         trayIcon.setToolTip("Message from " + Augustus.getInstance().getName() + " hack client");
         if (SystemTray.isSupported()) {
            systemTray.add(trayIcon);
         }
      } catch (Throwable e2) {
      }
   }
}
