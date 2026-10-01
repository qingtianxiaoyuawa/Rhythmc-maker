package io.wispforest.owo.ui.base;

import cn.frkovo.rhythmcmaker.client.ImGuiRuntimeAccess;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseOwoScreen<T extends FlowLayout> extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("RhythMC Maker UI");
    private static boolean renderDiagnosticsLogged;
    protected OwoUIAdapter<T> uiAdapter;

    protected BaseOwoScreen() {
        super(Text.literal("RhythMC Maker"));
    }

    protected abstract OwoUIAdapter<T> createAdapter();
    protected abstract void build(T root);

    @Override
    protected void init() {
        uiAdapter = createAdapter();
        build(uiAdapter.createRoot());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC0B1018);
        if (!renderDiagnosticsLogged) {
            LOGGER.info("UI base render reached: screen={}, adapter={}, root={}, size={}x{}",
                    getClass().getName(), uiAdapter != null, uiAdapter != null && uiAdapter.rootComponent != null, width, height);
            renderDiagnosticsLogged = true;
        }
        ImGuiRuntimeAccess.begin(delta, context, mouseX, mouseY);
        pushTheme();
        try {
            ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
            ImGui.setNextWindowSize(Math.max(260, width), Math.max(180, height), ImGuiCond.Always);
            int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                    | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoScrollbar
                    | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoSavedSettings;
            if (ImGui.begin("RhythMC Maker##" + System.identityHashCode(this), flags)) {
                renderToolbar();
                ImGui.setCursorPos(10, 42);
                int contentFlags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoSavedSettings;
                if (ImGui.beginChild("##rhythmc_content", 0, 0, false, contentFlags)
                        && uiAdapter != null && uiAdapter.rootComponent != null) {
                    uiAdapter.rootComponent.render();
                }
                ImGui.endChild();
            }
            ImGui.end();
        } finally {
            ImGui.popStyleColor(9);
            ImGui.popStyleVar(6);
            ImGuiRuntimeAccess.end();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(input);
        ImGuiRuntimeAccess.key(input.key(), true, input.modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyReleased(input);
        ImGuiRuntimeAccess.key(input.key(), false, input.modifiers());
        return true;
    }

    private void renderToolbar() {
        ImGui.beginChild("##rhythmc_toolbar", 0, 30, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.textColored(0.42f, 0.75f, 1.0f, 1.0f, "RhythMC Maker");
        ImGui.sameLine();
        ImGui.textDisabled("/  制谱与场景工作台");
        ImGui.sameLine(Math.max(0.0f, ImGui.getWindowWidth() - 150.0f));
        ImGui.textDisabled("ESC 返回游戏");
        ImGui.endChild();
        ImGui.separator();
    }

    private static void pushTheme() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 5.0f, 3.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 2.0f);
        pushColor(ImGuiCol.WindowBg, 0.055f, 0.075f, 0.11f, 0.98f);
        pushColor(ImGuiCol.ChildBg, 0.075f, 0.10f, 0.15f, 0.96f);
        pushColor(ImGuiCol.FrameBg, 0.12f, 0.15f, 0.21f, 1.0f);
        pushColor(ImGuiCol.FrameBgHovered, 0.16f, 0.23f, 0.33f, 1.0f);
        pushColor(ImGuiCol.Button, 0.10f, 0.28f, 0.48f, 1.0f);
        pushColor(ImGuiCol.ButtonHovered, 0.15f, 0.40f, 0.68f, 1.0f);
        pushColor(ImGuiCol.ButtonActive, 0.08f, 0.23f, 0.40f, 1.0f);
        pushColor(ImGuiCol.Header, 0.12f, 0.30f, 0.50f, 1.0f);
        pushColor(ImGuiCol.Border, 0.24f, 0.34f, 0.46f, 0.75f);
    }

    private static void pushColor(int slot, float red, float green, float blue, float alpha) {
        ImGui.pushStyleColor(slot, red, green, blue, alpha);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
