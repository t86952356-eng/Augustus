package net.augustus.utils.audio;

import net.augustus.Augustus;
import net.augustus.utils.file.ClientFileUtil;

/**
 * Android-safe audio player.
 *
 * The desktop build of Augustus plays its custom music (holiday songs,
 * MLG / ShuffleMode tracks) through JavaFX {@code AudioClip}. JavaFX needs
 * its own native toolkit (glass / prism / gstreamer), which does not exist
 * on Android, and initializing it there crashes the client. The Android
 * build therefore ships this safe no-op implementation instead: every
 * method returns immediately, no JavaFX class is ever referenced, and the
 * in-game sounds keep working through the vanilla OpenAL sound system
 * (ClientSounds / SoundHandler).
 */
public class JavaFxAudioPlayer {
   private Object audioClip = null;

   public void playResource(String str, boolean flag) {
      // no-op on Android: JavaFX media is unavailable
   }

   public void stop() {
      // no-op on Android
   }

   public void playResource(String str) {
      this.playResource(str, false);
   }

   public boolean isPlaying() {
      return false;
   }

   public void playFile(String str) {
      // no-op on Android
   }

   public void setCycleCount(int cycleCount) {
      // no-op on Android
   }

   public void setVolume(double volume) {
      // no-op on Android
   }
}
