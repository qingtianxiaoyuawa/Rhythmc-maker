package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.client.render.font.EditorFontAtlas;
import imgui.ImGui;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.flag.ImGuiConfigFlags;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL33;

import imgui.type.ImInt;

/** Owns the native ImGui context used by the editor overlay. */
public final class ImGuiRuntime {
    private static final String GLSL_VERSION = "#version 150";
    private static final ImGuiImplGlfw PLATFORM = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 RENDERER = new ImGuiImplGl3();
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
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.setConfigDockingAlwaysTabBar(true);
        io.setIniFilename(client.runDirectory.toPath().resolve("config").resolve("rhythmc-maker-imgui.ini").toString());
        ImFont editorFont = EditorFontAtlas.getInstance().load(io.getFonts());
        io.setFontDefault(editorFont);
        validateFontTextureSize(io.getFonts());
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

    private static void validateFontTextureSize(ImFontAtlas fonts) {
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        fonts.getTexDataAsAlpha8(width, height);
        int maximum = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (width.get() > maximum || height.get() > maximum) {
            throw new IllegalStateException("编辑器字体图集尺寸 " + width.get() + "x" + height.get() + " 超过显卡纹理上限 " + maximum);
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
        EditorFontAtlas.getInstance().release();
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
