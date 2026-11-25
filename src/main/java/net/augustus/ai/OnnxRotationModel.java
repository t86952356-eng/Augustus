package net.augustus.ai;

import java.io.File;

/**
 * Android build: ONNX Runtime is not bundled (it has no Android natives and
 * adds ~93 MB of desktop binaries). This stub keeps the exact public API of
 * the original model wrapper (used by AiRotationPredictor / KillAura) so the
 * AI rotation feature degrades gracefully: the model never loads, and the
 * KillAura setting reports "ONNX Runtime is not available in this build"
 * instead of crashing with NoClassDefFoundError.
 */
public final class OnnxRotationModel {
   private String loadError;
   private static final String DEFAULT_MODEL_PATH = null;
   private Object environment;
   private Object session;
   private final String modelPath;

   public String getModelPath() {
      return this.modelPath;
   }

   public boolean isLoaded() {
      return false;
   }

   public void close() {
      this.session = null;
   }

   public String getLoadError() {
      return this.loadError;
   }

   public float[] runInference(float[] floats, long[] longs) {
      return null;
   }

   public OnnxRotationModel(File file) {
      this.modelPath = file == null ? null : file.getAbsolutePath();
      this.loadError = "ONNX Runtime is not available in this build";
   }
}
