package cn.frkovo.rhythmcmaker.client;

import imgui.ImDrawData;
import imgui.ImGui;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFont;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class ImGuiRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("RhythMC Maker ImGui");
    private static MinecraftImGuiImplGl3 gl3;
    private static boolean initialized;
    private static DrawContext drawContext;
    private static ImDrawData pendingDrawData;
    private static final List<QueuedItem> queuedItems = new ArrayList<>();
    private static boolean beginDiagnosticsLogged;
    private static boolean drawDiagnosticsLogged;
    private static boolean submitHookDiagnosticsLogged;
    private static boolean submitDiagnosticsLogged;
    private static boolean awaitingSubmitDiagnostics;
    private static float pendingScrollHorizontal;
    private static float pendingScrollVertical;
    private static byte[] bundledFontData;

    private ImGuiRuntime() {}

    static void beginFrame(float delta, DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!beginDiagnosticsLogged) {
            LOGGER.info("ImGui beginFrame reached: initialized={}, scaled={}x{}, framebuffer={}x{}",
                    initialized, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                    client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
            beginDiagnosticsLogged = true;
        }
        drawContext = context;
        pendingDrawData = null;
        queuedItems.clear();
        if (!initialized) {
            ImGui.createContext();
            ImGuiExtensionRuntime.initialize(ImGui.getCurrentContext());
            ImGuiIO io = ImGui.getIO();
            configureKeyMap(io);
            io.removeConfigFlags(ImGuiConfigFlags.ViewportsEnable);
            io.setKeyRepeatDelay(0.30f);
            io.setKeyRepeatRate(0.045f);
            io.setConfigInputTextCursorBlink(true);
            io.setConfigDragClickToInputText(true);
            configureClipboard(io);
            io.setIniFilename(null);
            gl3 = new MinecraftImGuiImplGl3();
            gl3.init();
            loadChineseFont(io);
            gl3.updateFontsTexture();
            initialized = true;
        }
        ImGuiIO io = ImGui.getIO();
        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        float framebufferScaleX = scaledWidth > 0
                ? (float) client.getWindow().getFramebufferWidth() / scaledWidth : 1.0f;
        float framebufferScaleY = scaledHeight > 0
                ? (float) client.getWindow().getFramebufferHeight() / scaledHeight : 1.0f;
        io.setDisplaySize(scaledWidth, scaledHeight);
        io.setDisplayFramebufferScale(framebufferScaleX, framebufferScaleY);
        io.setDeltaTime(Math.max(0.001f, delta));
        io.setMousePos(mouseX, mouseY);        long windowHandle = client.getWindow().getHandle();
        for (int button = 0; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            io.setMouseDown(button, GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS);
        }
        io.setMouseWheelH(pendingScrollHorizontal);
        io.setMouseWheel(pendingScrollVertical);
        pendingScrollHorizontal = 0.0f;
        pendingScrollVertical = 0.0f;
        ImGui.newFrame();
    }

    static void endFrame() {
        ImGui.render();
        ImDrawData drawData = ImGui.getDrawData();
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (!drawDiagnosticsLogged) {
            LOGGER.info("ImGui draw data queued: lists={}, vertices={}, display={}x{}, framebuffer={}x{}, framebufferScale={}x{}, fontTexture={}",
                    drawData.getCmdListsCount(), drawData.getTotalVtxCount(), drawData.getDisplaySizeX(), drawData.getDisplaySizeY(),
                    client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(),
                    drawData.getFramebufferScaleX(), drawData.getFramebufferScaleY(), ImGui.getIO().getFonts().getTexID());
            drawDiagnosticsLogged = true;
        }
        pendingDrawData = drawData;
        if (drawContext != null) {
            for (QueuedItem item : queuedItems) drawContext.drawItem(item.stack(), item.x(), item.y());
        }
        queuedItems.clear();
        drawContext = null;
    }

    static void renderPending() {
        ImDrawData drawData = pendingDrawData;
        pendingDrawData = null;
        if (awaitingSubmitDiagnostics && !submitHookDiagnosticsLogged) {
            LOGGER.info("ImGui submit hook reached after final framebuffer blit: initialized={}, pending={}", initialized, drawData != null);
            submitHookDiagnosticsLogged = true;
            awaitingSubmitDiagnostics = false;
        }
        if (!initialized || drawData == null) return;
        gl3.renderDrawData(drawData);
        if (!submitDiagnosticsLogged) {
            LOGGER.info("ImGui draw data submitted after final framebuffer blit: lists={}, vertices={}",
                    drawData.getCmdListsCount(), drawData.getTotalVtxCount());
            submitDiagnosticsLogged = true;
        }
    }

    static void queueItem(ItemStack stack, int x, int y) {
        if (drawContext != null && stack != null && !stack.isEmpty()) queuedItems.add(new QueuedItem(stack.copy(), x, y));
    }

    static void addScroll(double horizontal, double vertical) {
        if (initialized) {
            pendingScrollVertical += (float) vertical;
            pendingScrollHorizontal += (float) horizontal;
        }
    }

    static void addCharacter(int codepoint) {
        if (initialized) ImGui.getIO().addInputCharacter(codepoint);
    }

    static void setKey(int keyCode, boolean pressed, int modifiers) {
        if (!initialized || keyCode < 0 || keyCode >= 512) return;
        ImGuiIO io = ImGui.getIO();
        int imguiKey = switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> ImGuiKey.Tab;
            case GLFW.GLFW_KEY_LEFT -> ImGuiKey.LeftArrow;
            case GLFW.GLFW_KEY_RIGHT -> ImGuiKey.RightArrow;
            case GLFW.GLFW_KEY_UP -> ImGuiKey.UpArrow;
            case GLFW.GLFW_KEY_DOWN -> ImGuiKey.DownArrow;
            case GLFW.GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp;
            case GLFW.GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown;
            case GLFW.GLFW_KEY_HOME -> ImGuiKey.Home;
            case GLFW.GLFW_KEY_END -> ImGuiKey.End;
            case GLFW.GLFW_KEY_INSERT -> ImGuiKey.Insert;
            case GLFW.GLFW_KEY_DELETE -> ImGuiKey.Delete;
            case GLFW.GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace;
            case GLFW.GLFW_KEY_SPACE -> ImGuiKey.Space;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> ImGuiKey.Enter;
            case GLFW.GLFW_KEY_ESCAPE -> ImGuiKey.Escape;
            case GLFW.GLFW_KEY_A -> ImGuiKey.A;
            case GLFW.GLFW_KEY_C -> ImGuiKey.C;
            case GLFW.GLFW_KEY_V -> ImGuiKey.V;
            case GLFW.GLFW_KEY_X -> ImGuiKey.X;
            case GLFW.GLFW_KEY_Y -> ImGuiKey.Y;
            case GLFW.GLFW_KEY_Z -> ImGuiKey.Z;
            default -> -1;
        };
        io.setKeyCtrl((modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
        io.setKeyShift((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        io.setKeyAlt((modifiers & GLFW.GLFW_MOD_ALT) != 0);
        io.setKeySuper((modifiers & GLFW.GLFW_MOD_SUPER) != 0);
    }

    private static void configureKeyMap(ImGuiIO io) {
        io.setKeyMap(ImGuiKey.Tab, GLFW.GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW.GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW.GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW.GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW.GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW.GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW.GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW.GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW.GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW.GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW.GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW.GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW.GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW.GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW.GLFW_KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.A, GLFW.GLFW_KEY_A);
        io.setKeyMap(ImGuiKey.C, GLFW.GLFW_KEY_C);
        io.setKeyMap(ImGuiKey.V, GLFW.GLFW_KEY_V);
        io.setKeyMap(ImGuiKey.X, GLFW.GLFW_KEY_X);
        io.setKeyMap(ImGuiKey.Y, GLFW.GLFW_KEY_Y);
        io.setKeyMap(ImGuiKey.Z, GLFW.GLFW_KEY_Z);
    }
    private static void loadChineseFont(ImGuiIO io) {
        Path font = extractBundledFont();
        ImFontAtlas fonts = io.getFonts();
        if (Files.isRegularFile(font)) {
            try {
                bundledFontData = Files.readAllBytes(font);
                ImFontGlyphRangesBuilder ranges = new ImFontGlyphRangesBuilder();
                ranges.addRanges(fonts.getGlyphRangesDefault());
                ranges.addRanges(fonts.getGlyphRangesChineseSimplifiedCommon());
                ImFontConfig chineseConfig = new ImFontConfig();
                chineseConfig.setOversampleH(2);
                chineseConfig.setOversampleV(2);
                chineseConfig.setPixelSnapH(true);
                ImFont loaded = fonts.addFontFromMemoryTTF(bundledFontData, 8.0f, chineseConfig, ranges.buildRanges());
                if (loaded == null) throw new IOException("ImGui rejected bundled font");
                LOGGER.info("Loaded Chinese UI font from memory: {}", font);
                return;
            } catch (IOException | RuntimeException exception) {
                bundledFontData = null;
                LOGGER.warn("Bundled Chinese UI font was rejected; using ImGui default font", exception);
            }
        } else {
            fonts.addFontDefault();
            LOGGER.warn("Chinese UI font was not found at {}; using default font", font);
            return;
        }
        fonts.addFontDefault();
    }

    private static Path extractBundledFont() {
        Path target = MinecraftClient.getInstance().runDirectory.toPath().resolve("rhythmc-maker").resolve("fonts").resolve("misans-bold.ttf");
        try {
            Files.createDirectories(target.getParent());
            Resource resource = MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of("rhythmc_maker", "fonts/misans-bold.ttf")).orElseThrow();
            if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                    try (var input = resource.getInputStream()) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
            }
            return target;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to extract bundled Misans font", exception);
            return Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "simhei.ttf");
        }
    }

    private static void configureClipboard(ImGuiIO io) {
        io.setGetClipboardTextFn(new imgui.callback.ImStrSupplier() {
            @Override
            public String get() {
                try {
                    return GLFW.glfwGetClipboardString(MinecraftClient.getInstance().getWindow().getHandle());
                } catch (RuntimeException exception) {
                    return "";
                }
            }
        });
        io.setSetClipboardTextFn(new imgui.callback.ImStrConsumer() {
            @Override
            public void accept(String text) {
                GLFW.glfwSetClipboardString(MinecraftClient.getInstance().getWindow().getHandle(), text);
            }
        });
    }

    private record QueuedItem(ItemStack stack, int x, int y) {}
}



