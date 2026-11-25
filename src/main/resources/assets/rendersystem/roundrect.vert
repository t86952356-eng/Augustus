#version 120

// Simple passthrough vertex shader for rounded rectangles.
// GL4ES-safe: no gl_TexCoord array, uses a custom varying instead.

varying vec2 vUv;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    vUv = gl_MultiTexCoord0.xy;
    gl_FrontColor = gl_Color;
}
