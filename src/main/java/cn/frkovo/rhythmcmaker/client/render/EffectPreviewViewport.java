package cn.frkovo.rhythmcmaker.client.render;

import imgui.ImDrawList;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

public final class EffectPreviewViewport {
    private float left;
    private float top;
    private float width;
    private float height;
    private int framebuffer;
    private int colorTexture;
    private int sourceWidth;
    private int sourceHeight;

    public void setBounds(float left, float top, float width, float height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    public boolean contains(float mouseX, float mouseY) {
        return width > 0 && height > 0 && mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }

    public void drawPreview(ImDrawList drawList) {
        if (colorTexture == 0 || width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;

        float fit = Math.min(width / sourceWidth, height / sourceHeight);
        float imageWidth = sourceWidth * fit;
        float imageHeight = sourceHeight * fit;
        float imageLeft = left + (width - imageWidth) / 2.0f;
        float imageTop = top + (height - imageHeight) / 2.0f;
        drawList.pushClipRect(left, top, left + width, top + height, true);
        drawList.addImage(colorTexture, imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight,
                0.0f, 1.0f, 1.0f, 0.0f);
        drawList.popClipRect();
    }

    public void present(MinecraftClient client) {
        int fullWidth = client.getWindow().getFramebufferWidth();
        int fullHeight = client.getWindow().getFramebufferHeight();
        if (fullWidth <= 0 || fullHeight <= 0 || width <= 0 || height <= 0) return;

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int previousPixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (framebuffer == 0) {
                framebuffer = GL30.glGenFramebuffers();
                colorTexture = GL11.glGenTextures();
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            if (sourceWidth != fullWidth || sourceHeight != fullHeight) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fullWidth, fullHeight, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, colorTexture, 0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Effect preview framebuffer is incomplete");
                }
                sourceWidth = fullWidth;
                sourceHeight = fullHeight;
            }

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBlitFramebuffer(0, 0, fullWidth, fullHeight, 0, 0, fullWidth, fullHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPixelUnpackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    public void close() {
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (colorTexture != 0) GL11.glDeleteTextures(colorTexture);
        framebuffer = 0;
        colorTexture = 0;
        sourceWidth = 0;
        sourceHeight = 0;
    }
}
