package cn.frkovo.rhythmcmaker.client.render;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class EffectPreviewViewport {
    private float left;
    private float top;
    private float width;
    private float height;
    private int framebuffer;
    private int colorBuffer;
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

    public void present(MinecraftClient client) {
        int fullWidth = client.getWindow().getFramebufferWidth();
        int fullHeight = client.getWindow().getFramebufferHeight();
        if (fullWidth <= 0 || fullHeight <= 0 || width <= 0 || height <= 0) return;
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (framebuffer == 0) {
                framebuffer = GL30.glGenFramebuffers();
                colorBuffer = GL30.glGenRenderbuffers();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            if (sourceWidth != fullWidth || sourceHeight != fullHeight) {
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorBuffer);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, fullWidth, fullHeight);
                GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL30.GL_RENDERBUFFER, colorBuffer);
                if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Effect preview framebuffer is incomplete");
                }
                sourceWidth = fullWidth;
                sourceHeight = fullHeight;
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL30.glBlitFramebuffer(0, 0, fullWidth, fullHeight, 0, 0, fullWidth, fullHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glClearColor(0, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            double scaleX = fullWidth / (double) client.getWindow().getWidth();
            double scaleY = fullHeight / (double) client.getWindow().getHeight();
            double fit = Math.min(width * scaleX / fullWidth, height * scaleY / fullHeight);
            int imageWidth = Math.max(1, (int) Math.round(fullWidth * fit));
            int imageHeight = Math.max(1, (int) Math.round(fullHeight * fit));
            int imageLeft = (int) Math.round(left * scaleX + (width * scaleX - imageWidth) / 2);
            int imageBottom = fullHeight - (int) Math.round(top * scaleY + (height * scaleY + imageHeight) / 2);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL30.glBlitFramebuffer(0, 0, fullWidth, fullHeight, imageLeft, imageBottom,
                    imageLeft + imageWidth, imageBottom + imageHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    public void close() {
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (colorBuffer != 0) GL30.glDeleteRenderbuffers(colorBuffer);
        framebuffer = 0;
        colorBuffer = 0;
    }
}
