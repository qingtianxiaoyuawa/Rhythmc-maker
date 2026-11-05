package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import cn.frkovo.rhythmcmaker.client.render.SettingsImGuiView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

final class SettingsScreen extends Screen {
    private final SettingsImGuiView view = new SettingsImGuiView(this::closeSettings);

    SettingsScreen() {
        super(Text.literal("RhythMC 设置"));
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ImGuiRuntime.beginFrame(client)) return;
        try {
            view.render(client);
        } finally {
            ImGuiRuntime.endFrame();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }

    @Override
    protected void applyBlur(DrawContext drawContext) {
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            closeSettings();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void closeSettings() {
        MinecraftClient.getInstance().setScreen(null);
    }
}