package net.augustus.utils.shader;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ShaderProgram {
   private int previousProgramId;
   private final Object2IntMap uniformLocations = new Object2IntOpenHashMap();
   private final IntList savedTextureBindings = new IntArrayList();
   private int programId;

   public boolean isUsable() {
      return this.programId != 0;
   }

   public void setUniform1f(String str, float f) {
      if (!this.isUsable()) {
         return;
      }

      GL20.glUniform1f(this.getUniformLocation(str), f);
   }

   public void drawQuad(float f, float f2, float f3, float f4) {
      if (!this.isUsable()) {
         return;
      }

      GL11.glBegin(7);
      GL11.glVertex2f(f, f4);
      GL11.glVertex2f(f3, f4);
      GL11.glVertex2f(f3, f2);
      GL11.glVertex2f(f, f2);
      GL11.glEnd();
   }

   public void unbind() {
      if (!this.isUsable()) {
         return;
      }

      for (int i = 0; i < this.savedTextureBindings.size(); i++) {
         GlStateManager.setActiveTexture(33984 + i);
         GlStateManager.bindTexture(this.savedTextureBindings.getInt(i));
      }

      this.savedTextureBindings.clear();
      GlStateManager.setActiveTexture(33984);
      GL20.glUseProgram(this.previousProgramId);
   }

   public int getUniformLocation(String str) {
      if (!this.isUsable()) {
         return -1;
      } else {
         return this.uniformLocations.computeIntIfAbsent(str, obj -> GL20.glGetUniformLocation(this.programId, str));
      }
   }

   public ShaderProgram(String str, String str2) {
      this.previousProgramId = 0;
      this.programId = 0;
      int n = 0;

      try {
         int n2 = GL20.glCreateShader(35633);
         int n3 = GL20.glCreateShader(35632);
         GL20.glShaderSource(n2, str);
         GL20.glShaderSource(n3, str2);
         GL20.glCompileShader(n2);
         GL20.glCompileShader(n3);
         if (GL20.glGetShaderi(n2, 35713) != 1) {
            System.err.println("[Augustus] Vertex shader failed to compile, the effect is disabled on this renderer: " + GL20.glGetShaderInfoLog(n2, 2048));
            GL20.glDeleteShader(n2);
            GL20.glDeleteShader(n3);
            return;
         } else if (GL20.glGetShaderi(n3, 35713) != 1) {
            System.err.println("[Augustus] Fragment shader failed to compile, the effect is disabled on this renderer: " + GL20.glGetShaderInfoLog(n3, 2048));
            GL20.glDeleteShader(n2);
            GL20.glDeleteShader(n3);
            return;
         } else {
            n = GL20.glCreateProgram();
            GL20.glAttachShader(n, n2);
            GL20.glAttachShader(n, n3);
            GL20.glLinkProgram(n);
            if (GL20.glGetProgrami(n, 35714) != 1) {
               System.err.println("[Augustus] Shader program failed to link, the effect is disabled on this renderer: " + GL20.glGetProgramInfoLog(n, 2048));
               GL20.glDeleteProgram(n);
               return;
            } else {
               GL20.glDeleteShader(n2);
               GL20.glDeleteShader(n3);
            }
         }
      } catch (Throwable t) {
         System.err.println("[Augustus] Shader creation failed, the effect is disabled on this renderer: " + t);
         if (n != 0) {
            try {
               GL20.glDeleteProgram(n);
            } catch (Throwable t2) {
            }
         }

         return;
      }

      this.programId = n;
   }

   public void setUniform1i(String str, int n) {
      if (!this.isUsable()) {
         return;
      }

      GL20.glUniform1i(this.getUniformLocation(str), n);
   }

   public void bindTextureUniform(String str, Framebuffer framebuffer) {
      if (!this.isUsable()) {
         return;
      }

      this.setUniform1i(str, this.savedTextureBindings.size());
      GlStateManager.setActiveTexture(33984 + this.savedTextureBindings.size());
      this.savedTextureBindings.add(GL11.glGetInteger(32873));
      GlStateManager.bindTexture(framebuffer.framebufferTexture);
   }

   public void setUniform1fv(String str, FloatBuffer floatBuffer) {
      if (!this.isUsable()) {
         return;
      }

      GL20.glUniform1(this.getUniformLocation(str), floatBuffer);
   }

   public void setUniform4f(String str, float f, float f2, float f3, float f4) {
      if (!this.isUsable()) {
         return;
      }

      GL20.glUniform4f(this.getUniformLocation(str), f, f2, f3, f4);
   }

   public void bind() {
      if (!this.isUsable()) {
         return;
      }

      this.previousProgramId = GL11.glGetInteger(35725);
      GL20.glUseProgram(this.programId);
   }

   public void setUniform2f(String str, float f, float f2) {
      if (!this.isUsable()) {
         return;
      }

      GL20.glUniform2f(this.getUniformLocation(str), f, f2);
   }
}
