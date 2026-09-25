package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class PlaceholderScreen extends RhythmcScreen {
    private final String title;
    private final String section;

    PlaceholderScreen(String title) {
        this(title, "root");
    }

    PlaceholderScreen(String title, String section) {
        this.title = title;
        this.section = section;
    }

    @Override

    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(420), Sizing.content()), 18, 12, 0xEE101821, 0xFF5B7596);
        page.child(UIComponents.label(Text.literal(title)).shadow(true));
        if (section.equals("awa-menu")) {
            page.child(UIComponents.texture(
                    Identifier.of("rhythmc_maker", "textures/gui/awa_menu.png"),
                    0, 0, 979, 979, 979, 979
                ).horizontalSizing(Sizing.fixed(300)).verticalSizing(Sizing.fixed(300)));
            page.child(UIComponents.label(Text.literal("awa~")));
        } else if (title.equals("设置")) {
            page.child(UIComponents.button(Text.literal("按键设置"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("按键设置", "keys"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("制谱器设置"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("制谱器设置", "editor"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("显示设置"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("显示设置", "display"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("音量设置"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("音量设置", "volume"))).horizontalSizing(Sizing.fill()));
        } else if (section.equals("display")) {
            var config = ClientChartAccess.config();
            page.child(UIComponents.label(Text.literal("制谱器大厅正文内容")));
            TextBoxComponent lobbyContent = UIComponents.textBox(Sizing.fill(), config.lobbySidebarContent);
            page.child(lobbyContent);
            page.child(UIComponents.label(Text.literal("该内容会显示在大厅 Sidebar 的正文区域")));
            page.child(UIComponents.button(Text.literal("保存显示设置"), button -> {
                String value = lobbyContent.getText().trim();
                if (value.isBlank()) value = "可以在设置修改此处显示内容~";
                if (value.length() > 120) value = value.substring(0, 120);
                config.lobbySidebarContent = value;
                try {
                    ClientChartAccess.saveConfig(config);
                    ClientChartAccess.status("显示设置已保存");
                } catch (Exception exception) {
                    ClientChartAccess.status("显示设置保存失败");
                }
            }).horizontalSizing(Sizing.fill()));        } else if (section.equals("volume")) {
            var config = ClientChartAccess.config();
            page.child(UIComponents.label(Text.literal("音乐音量倍率（0.1-2.0）")));
            TextBoxComponent musicVolume = UIComponents.textBox(Sizing.fill(), Double.toString(config.musicVolumeMultiplier));
            page.child(musicVolume);
            page.child(UIComponents.label(Text.literal("音符判定音量倍率（0.1-2.0）")));
            TextBoxComponent noteVolume = UIComponents.textBox(Sizing.fill(), Double.toString(config.noteJudgementVolumeMultiplier));
            page.child(noteVolume);
            page.child(UIComponents.label(Text.literal("1.0 为默认音量，2.0 为增强音量")));
            page.child(UIComponents.button(Text.literal("保存音量设置"), button -> {
                try {
                    double music = Double.parseDouble(musicVolume.getText().trim());
                    double note = Double.parseDouble(noteVolume.getText().trim());
                    if (!Double.isFinite(music) || !Double.isFinite(note) || music < 0.1 || music > 2.0 || note < 0.1 || note > 2.0) throw new NumberFormatException();
                    config.musicVolumeMultiplier = music;
                    config.noteJudgementVolumeMultiplier = note;
                    ClientChartAccess.saveConfig(config);
                    ClientChartAccess.status("音量设置已保存");
                } catch (Exception exception) {
                    ClientChartAccess.status("音量设置保存失败：请输入 0.1 至 2.0 的数字");
                }
            }).horizontalSizing(Sizing.fill()));        } else if (section.equals("keys")) {
            page.child(UIComponents.label(Text.literal("飞行速度快捷键：Ctrl+1（1倍）、Ctrl+2（1.5倍）、Ctrl+3（2倍）、Ctrl+4（4倍）")));
            page.child(UIComponents.label(Text.literal("播放起始 Chunk：按住 Alt + 鼠标滚轮，或按住 Alt 后右键当前 Chunk 任意位置")));
            page.child(UIComponents.button(Text.literal("按键选项"), button -> MinecraftClient.getInstance().setScreen(new ControlsOptionsScreen(this, MinecraftClient.getInstance().options))).horizontalSizing(Sizing.fill()));
        } else if (section.equals("chart-settings")) {
            var chart = ClientChartAccess.resolveActiveChart();
            long currentOffset = chart == null ? 0 : chart.offsetMillis;
            page.child(UIComponents.label(Text.literal("偏移值（毫秒）")));
            TextBoxComponent offset = UIComponents.textBox(Sizing.fill(), Long.toString(currentOffset));
            numericIntegerOnly(offset); page.child(offset);
            page.child(UIComponents.button(Text.literal("保存偏移"), button -> {
                try {
                    int value = Integer.parseInt(offset.getText());
                    if (value < -60000 || value > 60000) throw new NumberFormatException();
                    RhythmcMakerClient.sendChartCommand("rhythmc_offset " + value);
                    if (chart != null) chart.offsetMillis = value;
                    ClientChartAccess.status("谱面偏移已保存：" + value + " ms");
                } catch (Exception exception) {
                    ClientChartAccess.status("偏移保存失败：请输入 -60000 至 60000 的整数");
                }
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("quick-functions")) {
            page.child(UIComponents.button(Text.literal("快捷传送"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("快捷传送", "quick-teleport-select"))).horizontalSizing(Sizing.fill()));
        } else if (section.equals("quick-teleport-select")) {
            page.child(UIComponents.label(Text.literal("选择传送目标")));
            page.child(UIComponents.button(Text.literal("按 Chunk 传送"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("快捷传送", "quick-teleport-chunk"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("按歌曲时间传送"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("快捷传送", "quick-teleport-time"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("传送到播放场景"), button -> {
                RhythmcMakerClient.sendChartCommand("rhythmc_teleport_playback_scene");
                close();
            }).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("传送到制谱器平台"), button -> {
                RhythmcMakerClient.sendChartCommand("rhythmc_teleport_editor_platform");
                close();
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("quick-teleport-chunk")) {
            var chart = ClientChartAccess.resolveActiveChart();
            page.child(UIComponents.label(Text.literal("Chunk 数（1 - " + Math.max(1, chart == null ? 1 : chart.chunkCount) + "）")));
            TextBoxComponent chunk = UIComponents.textBox(Sizing.fill(), "1");
            numericIntegerOnly(chunk); page.child(chunk);
            page.child(UIComponents.button(Text.literal("传送"), button -> {
                try {
                    int value = Integer.parseInt(chunk.getText());
                    if (chart == null || value < 1 || value > Math.max(1, chart.chunkCount)) throw new NumberFormatException();
                    RhythmcMakerClient.sendChartCommand("rhythmc_teleport_chunk " + value);
                    close();
                } catch (Exception exception) {
                    ClientChartAccess.status("谱面没有这么长awa~");
                }
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("quick-teleport-time")) {
            var chart = ClientChartAccess.resolveActiveChart();
            double totalSeconds = chart == null ? 0.0 : chart.durationSeconds > 0.0 ? chart.durationSeconds : chart.totalBeats > 0.0 && chart.bpm > 0.0 ? chart.totalBeats * 60.0 / chart.bpm : Math.max(1, chart.chunkCount) * 60.0 / Math.max(1.0, chart.bpm);
            page.child(UIComponents.label(Text.literal("歌曲时间（秒，0 - " + String.format(java.util.Locale.ROOT, "%.2f", totalSeconds) + "）")));
            TextBoxComponent time = UIComponents.textBox(Sizing.fill(), "0");
            numericOnly(time); page.child(time);
            page.child(UIComponents.button(Text.literal("传送"), button -> {
                try {
                    double value = Double.parseDouble(time.getText());
                    if (!Double.isFinite(value) || chart == null || value < 0.0 || value > totalSeconds) throw new NumberFormatException();
                    RhythmcMakerClient.sendChartCommand("rhythmc_teleport_time " + value);
                    close();
                } catch (Exception exception) {
                    ClientChartAccess.status("谱面没有这么长awa~");
                }
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("chunk-score")) {
            var chart = ClientChartAccess.resolveActiveChart();
            int currentDivisions = chart == null ? ClientChartAccess.defaultDivisionsPerChunk() : Math.max(1, chart.divisionsPerChunk);
            int currentLanes = chart == null || chart.laneCount < 1 || chart.laneCount > 9 || chart.laneCount % 2 == 0 ? 3 : chart.laneCount;
            page.child(UIComponents.label(Text.literal("每Chunk分数（1 - 32）")));
            TextBoxComponent divisions = UIComponents.textBox(Sizing.fill(), Integer.toString(currentDivisions));
            numericIntegerOnly(divisions); page.child(divisions);
            page.child(UIComponents.label(Text.literal("编辑器轨道宽度（1 / 3 / 5 / 7 / 9）")));
            TextBoxComponent lanes = UIComponents.textBox(Sizing.fill(), Integer.toString(currentLanes));
            numericIntegerOnly(lanes); page.child(lanes);
            page.child(UIComponents.label(Text.literal("若调整了制谱器轨道宽度，请修改场景以确保视觉效果")));
            page.child(UIComponents.button(Text.literal("应用轨道布局"), button -> {
                try {
                    int divisionsValue = Integer.parseInt(divisions.getText());
                    int lanesValue = Integer.parseInt(lanes.getText());
                    if (divisionsValue < 1 || divisionsValue > 32 || lanesValue < 1 || lanesValue > 9 || lanesValue % 2 == 0) throw new NumberFormatException();
                    RhythmcMakerClient.sendChartCommand("rhythmc_layout_set " + divisionsValue + " " + lanesValue);
                    ClientChartAccess.status("正在应用制谱器轨道：" + lanesValue + " × " + divisionsValue);
                    close();
                } catch (Exception exception) {
                    ClientChartAccess.status("设置失败：每Chunk分数为 1-32，轨道宽度为 1/3/5/7/9");
                }
            }).horizontalSizing(Sizing.fill()));        } else if (section.equals("editor")) {
            var config = ClientChartAccess.config();
            page.child(UIComponents.label(Text.literal("自动保存间隔（秒）")));
            TextBoxComponent autosave = UIComponents.textBox(Sizing.fill(), Double.toString(config.autosaveIntervalSeconds));
            numericOnly(autosave); page.child(autosave);
            page.child(UIComponents.label(Text.literal("Sidebar刷新间隔（秒）")));
            TextBoxComponent sidebar = UIComponents.textBox(Sizing.fill(), Double.toString(config.sidebarRefreshIntervalSeconds));
            numericOnly(sidebar); page.child(sidebar);
            page.child(UIComponents.label(Text.literal("制谱器预览流速倍率（0.1 - 5.0）")));
            TextBoxComponent playerSpeed = UIComponents.textBox(Sizing.fill(), Double.toString(config.playerSpeed));
            numericOnly(playerSpeed); page.child(playerSpeed);
            page.child(UIComponents.label(Text.literal("默认每Chunk分数")));
            TextBoxComponent divisions = UIComponents.textBox(Sizing.fill(), Integer.toString(config.defaultDivisionsPerChunk));
            numericOnly(divisions); page.child(divisions);
            page.child(UIComponents.label(Text.literal("防每Chunk分数修改位移")));
            page.child(UIComponents.button(Text.literal(config.preventChunkDisplacement ? "已开启" : "已关闭"), button -> {
                config.preventChunkDisplacement = !config.preventChunkDisplacement;
                try {
                    ClientChartAccess.saveConfig(config);
                } catch (Exception ignored) {
                }
                MinecraftClient.getInstance().setScreen(new PlaceholderScreen("制谱器设置", "editor"));
            }).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("保存设置"), button -> {
                try {
                    config.autosaveIntervalSeconds = Double.parseDouble(autosave.getText());
                    config.sidebarRefreshIntervalSeconds = Double.parseDouble(sidebar.getText());
                    config.defaultDivisionsPerChunk = Integer.parseInt(divisions.getText());
                    config.playerSpeed = Double.parseDouble(playerSpeed.getText());
                    ClientChartAccess.saveConfig(config);
                    ClientChartAccess.status("设置已保存");
                } catch (Exception exception) {
                    ClientChartAccess.status("设置保存失败：请输入有效数字");
                }
            }).horizontalSizing(Sizing.fill()));
        } else {
            page.child(UIComponents.label(Text.literal("暂时什么都没有哦")));
        }
        page.child(UIComponents.button(Text.literal("关闭"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        var scroll = UIContainers.verticalScroll(Sizing.fixed(420), Sizing.fixed(Math.max(180, this.height - 30)), page);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(scroll));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
