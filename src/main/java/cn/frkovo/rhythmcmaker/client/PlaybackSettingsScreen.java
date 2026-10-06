package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

final class PlaybackSettingsScreen extends RhythmcScreen {
    private final RhythmcMakerConfig config;

    PlaybackSettingsScreen() {
        this.config = ClientChartAccess.config();
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int viewportWidth = Math.max(1, Math.min(this.width - 24, 440));
        int viewportHeight = Math.max(1, this.height - 24);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(viewportWidth), Sizing.content()), 12, 8, 0xEE0F1721, 0xFF55779E);
        int innerWidth = Math.max(1, viewportWidth - 24);
        page.child(menuHeader("播放设置", "调整播放方式与全局音高 / 速度", innerWidth));
        page.child(UIComponents.label(Text.literal("\u64ad\u653e\u65b9\u5f0f\uff1a" + ("scroll".equals(config.playbackMode) ? "\u6eda\u52a8\u64ad\u653e" : "\u6b63\u5f0f\u64ad\u653e"))));
        page.child(UIComponents.button(Text.literal("\u6b63\u5f0f\u64ad\u653e"), button -> chooseMode("formal")).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("\u6eda\u52a8\u64ad\u653e"), button -> chooseMode("scroll")).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.label(Text.literal("\u5168\u5c40\u97f3\u9ad8 / \u901f\u5ea6\uff081%-1000%\uff09")));
        TextBoxComponent pitchBox = UIComponents.textBox(Sizing.fill(), formatPitch(config.playbackPitchPercent));
        numericOnly(pitchBox);
        page.child(pitchBox);
        int presetGap = 4;
        int presetWidth = Math.max(44, (innerWidth - presetGap * 4) / 5);
        FlowLayout presets = spaced(UIContainers.horizontalFlow(Sizing.fixed(innerWidth), Sizing.content()), presetGap);
        for (double preset : new double[]{50.0, 75.0, 100.0, 125.0, 150.0}) {
            final double selected = preset;
            presets.child(UIComponents.button(Text.literal(formatPitch(selected) + "%"), button -> {
                pitchBox.text(formatPitch(selected));
                applyPitch(pitchBox);
            }).horizontalSizing(Sizing.fixed(presetWidth)));
        }
        page.child(presets);
        page.child(UIComponents.button(Text.literal("\u5e94\u7528\u97f3\u9ad8 / \u901f\u5ea6"), button -> applyPitch(pitchBox)).horizontalSizing(Sizing.fill()));
        page.child(wrappedLabel("\u53f3\u952e\u64ad\u653e\u4f1a\u8bb0\u4f4f\u4e0a\u6b21\u9009\u62e9\uff1b\u8d77\u59cb Chunk \u5bf9\u6b63\u5f0f\u64ad\u653e\u548c\u6eda\u52a8\u64ad\u653e\u5747\u6709\u6548\u3002", viewportWidth - 24));
        page.child(UIComponents.button(Text.literal("\u7acb\u5373\u64ad\u653e"), button -> start()).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("\u5173\u95ed"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        var scroll = UIContainers.verticalScroll(Sizing.fixed(viewportWidth), Sizing.fixed(viewportHeight), page);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill()).child(scroll)));
    }

    private void chooseMode(String mode) {
        config.playbackMode = mode;
        save();
    }

    private void start() {
        RhythmcMakerClient.startConfiguredPlayback();
    }

    private void applyPitch(TextBoxComponent pitchBox) {
        try {
            double pitchPercent = Double.parseDouble(pitchBox.getText().trim());
            if (!Double.isFinite(pitchPercent) || pitchPercent < 1.0 || pitchPercent > 1000.0) {
                throw new NumberFormatException();
            }
            RhythmcMakerClient.setPlaybackPitchPercent(pitchPercent);
            MinecraftClient.getInstance().setScreen(new PlaybackSettingsScreen());
        } catch (NumberFormatException exception) {
            ClientChartAccess.status("播放设置保存失败：音高/速度请输入 1 至 1000");
        }
    }

    private void save() {
        try {
            ClientChartAccess.saveConfig(config);
            ClientChartAccess.status("\u64ad\u653e\u8bbe\u7f6e\u5df2\u4fdd\u5b58");
            MinecraftClient.getInstance().setScreen(new PlaybackSettingsScreen());
        } catch (Exception exception) {
            ClientChartAccess.status("\u64ad\u653e\u8bbe\u7f6e\u4fdd\u5b58\u5931\u8d25");
        }
    }

    private static String formatPitch(double pitchPercent) {
        return pitchPercent == (long) pitchPercent ? Long.toString((long) pitchPercent) : Double.toString(pitchPercent);
    }
}
