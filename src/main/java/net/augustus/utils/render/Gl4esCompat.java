package net.augustus.utils.render;

public final class Gl4esCompat {
   private static final boolean GL4ES = detectGl4es();

   private static boolean detectGl4es() {
      try {
         String lib = System.getProperty("org.lwjgl.opengl.libname", "");
         if (lib != null && lib.toLowerCase().contains("gl4es")) {
            System.out.println("[Augustus] GL4ES renderer detected: post-processing shaders (blur / shadow / background) are disabled for stability, fonts load lazily on the render thread");
            return true;
         }
      } catch (Throwable t) {
      }

      return false;
   }

   public static boolean isGl4es() {
      return GL4ES;
   }
}
