package cn.frkovo.rhythmcmaker.client.render;

import imgui.ImGui;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Owns the native ImGui context used by the editor overlay. */
public final class ImGuiRuntime {
    private static final String GLSL_VERSION = "#version 150";
    private static final Identifier EDITOR_FONT = Identifier.of("rhythmc_maker", "fonts/misans-bold.ttf");
    private static final float EDITOR_FONT_SIZE = 18.0f;
    private static final String EDITOR_GLYPHS = "特效编辑器未加载谱面撤销保存播放关闭库仅注册表中的类型允许新增未知类型保留原JSON并只读显示世界预览当前没有客户端玩家维度相机位置从左侧选择一个特效类型或在下方时间线选择已有事件播放头当前事件Screen继续透视当前游戏世界独立相机viewport需要WorldRenderEvents hook当前不在Screen内伪造第二个世界渲染通道状态实机画面虚拟预览独立相机状态不改变玩家真实视角重置相机参数设置请选择一个事件类型只读新增关键帧删除当前事件时间轴Beat未命名谱面已保存未保存就绪标题显示颜色位置持续时间步长速度透明度强度方向角度距离●·：，。；！？→←↑↓";
    private static final ImGuiImplGlfw PLATFORM = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 RENDERER = new ImGuiImplGl3();
    private static byte[] editorFontData;
    private static boolean initialized;
    private static boolean frameOpen;

    private ImGuiRuntime() {
    }

    public static synchronized void initialize(MinecraftClient client) {
        if (initialized) return;
        ImGui.init();
        ImGui.createContext();
        ImGui.styleColorsDark();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        ImFont editorFont = loadEditorFont(client);
        if (!PLATFORM.init(client.getWindow().getHandle(), true)) {
            ImGui.destroyContext();
            throw new IllegalStateException("Unable to initialize the ImGui GLFW backend");
        }
        initializeRendererWithIsolatedTextureUpload();
        initialized = true;
    }

    private static void initializeRendererWithIsolatedTextureUpload() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        int previousPixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int previousUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousUnpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int previousUnpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int previousUnpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        GL33.glBindSampler(0, 0);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        try {
            RENDERER.init(GLSL_VERSION);
            configureFontTexture();
        } finally {
            GL33.glBindSampler(0, previousSampler);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPixelUnpackBuffer);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousUnpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousUnpackSkipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousUnpackSkipRows);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void configureFontTexture() {
        int texture = ImGui.getIO().getFonts().getTexID();
        if (texture == 0) {
            throw new IllegalStateException("ImGui font atlas did not produce a texture");
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static ImFont loadEditorFont(MinecraftClient client) {
        try {
            Resource resource = client.getResourceManager().getResourceOrThrow(EDITOR_FONT);
            try (InputStream input = resource.getInputStream()) {
                editorFontData = input.readAllBytes();
                if (editorFontData.length == 0) {
                    throw new IllegalStateException("ImGui editor font is empty: " + EDITOR_FONT);
                }
                ImFontAtlas fonts = ImGui.getIO().getFonts();
                ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder();
                addUnsignedRanges(rangesBuilder, fonts.getGlyphRangesDefault());
                addUnsignedRanges(rangesBuilder, fonts.getGlyphRangesChineseFull());
                rangesBuilder.addText(EDITOR_GLYPHS);

                ImFontConfig fontConfig = new ImFontConfig();
                fontConfig.setOversampleH(2);
                fontConfig.setOversampleV(2);
                ImFont editorFont = fonts.addFontFromMemoryTTF(
                        editorFontData,
                        EDITOR_FONT_SIZE,
                        fontConfig,
                        rangesBuilder.buildRanges()
                );
                fontConfig.destroy();
                if (editorFont == null) {
                    throw new IllegalStateException("ImGui editor font was not added: " + EDITOR_FONT);
                }
                if (!fonts.build()) {
                    throw new IllegalStateException("ImGui editor font atlas failed to build: " + EDITOR_FONT);
                }
                ImGui.getIO().setFontDefault(editorFont);
                return editorFont;
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read ImGui editor font: " + EDITOR_FONT, exception);
        }
    }

    private static void addUnsignedRanges(ImFontGlyphRangesBuilder builder, short[] ranges) {
        for (int index = 0; index + 1 < ranges.length && ranges[index] != 0; index += 2) {
            int from = ranges[index] & 0xFFFF;
            int to = ranges[index + 1] & 0xFFFF;
            for (int codepoint = from; codepoint <= to; codepoint++) {
                builder.addChar((char) codepoint);
            }
        }
    }

    public static synchronized boolean beginFrame(MinecraftClient client) {
        initialize(client);
        if (frameOpen) return false;
        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(client.getWindow().getWidth(), client.getWindow().getHeight());
        io.setDisplayFramebufferScale(client.getWindow().getScaleFactor(), client.getWindow().getScaleFactor());
        PLATFORM.newFrame();
        ImGui.newFrame();
        frameOpen = true;
        return true;
    }

    public static synchronized void endFrame() {
        if (!frameOpen) return;
        ImGui.render();
        frameOpen = false;
    }

    /** Submits the prepared draw data after Minecraft's deferred GUI pass. */
    public static synchronized void renderPendingDrawData() {
        if (!initialized || frameOpen) return;
        renderWithIsolatedTextureSampler(ImGui.getDrawData());
    }

    public static synchronized void markRenderCallback() {
        // The mixin calls this at the presentation boundary; no per-frame work is needed here.
    }

    public static synchronized void dispose() {
        if (!initialized) return;
        if (frameOpen) {
            ImGui.endFrame();
            frameOpen = false;
        }
        RENDERER.dispose();
        PLATFORM.dispose();
        ImGui.destroyContext();
        editorFontData = null;
        initialized = false;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Minecraft 1.21.11 uses sampler objects for its modern render pipeline.
     * ImGuiImplGl3 binds its texture but does not clear a sampler object already
     * attached to texture unit zero. Isolate that unit so the ImGui atlas uses
     * its own texture filtering, then restore Minecraft's state.
     */
    private static void renderWithIsolatedTextureSampler(imgui.ImDrawData drawData) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL33.glBindSampler(0, 0);
        try {
            RENDERER.renderDrawData(drawData);
        } finally {
            GL33.glBindSampler(0, previousSampler);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }
}
