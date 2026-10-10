package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.EditorTrackLayout;
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

import java.util.HashSet;
import java.util.Set;

final class PlaceholderScreen extends RhythmcScreen {
    private final String title;
    private final String section;
    private static boolean trackSnapUndoAvailable;
    private static String trackSnapChartId;
    private static TrackSnapSummary lastTrackSnapSummary;

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
        int pageWidth = Math.max(320, Math.min(this.width - 24, 520));
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.content()), 12, 7, 0xEE0F1721, 0xFF55779E);
        page.child(menuHeader(title, section.equals("awa-menu") ? null : "RhythMC Maker 设置与工具", pageWidth - 24));
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
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("volume")) {
            var config = ClientChartAccess.config();
            page.child(UIComponents.label(Text.literal("音乐音量倍率（0.1-2.0）")));
            TextBoxComponent musicVolume = UIComponents.textBox(Sizing.fill(), Double.toString(config.musicVolumeMultiplier));
            page.child(musicVolume);
            page.child(UIComponents.label(Text.literal("音符判定音量倍率（0.1-5.0）")));
            TextBoxComponent noteVolume = UIComponents.textBox(Sizing.fill(), Double.toString(config.noteJudgementVolumeMultiplier));
            page.child(noteVolume);
            page.child(UIComponents.label(Text.literal("1.0 为默认音量，最高可调至 5.0")));
            page.child(UIComponents.button(Text.literal("保存音量设置"), button -> {
                try {
                    double music = Double.parseDouble(musicVolume.getText().trim());
                    double note = Double.parseDouble(noteVolume.getText().trim());
                    if (!Double.isFinite(music) || !Double.isFinite(note) || music < 0.1 || music > 2.0 || note < 0.1 || note > 5.0) throw new NumberFormatException();
                    config.musicVolumeMultiplier = music;
                    config.noteJudgementVolumeMultiplier = note;
                    ClientChartAccess.saveConfig(config);
                    ClientChartAccess.status("音量设置已保存");
                } catch (Exception exception) {
                    ClientChartAccess.status("音量设置保存失败：音乐请输入 0.1 至 2.0，判定音效请输入 0.1 至 5.0");
                }
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("keys")) {
            page.child(UIComponents.label(Text.literal("飞行速度快捷键：Ctrl+1（1倍）、Ctrl+2（1.5倍）、Ctrl+3（2倍）、Ctrl+4（4倍）")));
            page.child(UIComponents.label(Text.literal("播放起始 Chunk：按住 Alt + 鼠标滚轮，或按住 Alt 后右键当前 Chunk 任意位置")));
            page.child(UIComponents.label(Text.literal("播放方式：按住 Shift + 右键播放，打开菜单选择正式播放或滚动播放；全局音高和速度在特效编辑器控制（此处仅作说明）")));
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
            page.child(UIComponents.button(Text.literal("轨道吸附"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("轨道吸附", "track-snap"))).horizontalSizing(Sizing.fill()));
        } else if (section.equals("track-snap")) {
            var chart = ClientChartAccess.resolveActiveChart();
            TrackSnapSummary summary = trackSnapSummary(chart);
            if (chart != null && chart.id.equals(trackSnapChartId) && lastTrackSnapSummary != null) {
                summary = lastTrackSnapSummary;
            }
            page.child(UIComponents.label(Text.literal("谱面基础 BPM：" + (chart == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", chart.bpm)))));
            page.child(UIComponents.label(Text.literal("当前每 Chunk 分数：" + (chart == null ? "-" : Math.max(1, chart.divisionsPerChunk)))));
            page.child(UIComponents.label(Text.literal("待吸附音符数量：" + summary.fractionalCount)));
            page.child(UIComponents.label(Text.literal("预计重复删除数量：" + summary.duplicateCount)));
            page.child(UIComponents.label(Text.literal("吸附后剩余音符数量：" + summary.remainingCount)));
            if (summary.fractionalCount == 0) page.child(UIComponents.label(Text.literal("没有需要吸附的音符")));
            page.child(UIComponents.label(Text.literal("这项操作会改变音符时机，可能造成偏差，请确认操作")));
            var confirm = UIComponents.button(Text.literal("继续确认"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("轨道吸附确认", "track-snap-confirm")));
            confirm.active(summary.fractionalCount > 0);
            page.child(confirm.horizontalSizing(Sizing.fill()));
            var undo = UIComponents.button(Text.literal("撤销上次吸附"), button -> {
                RhythmcMakerClient.sendChartCommand("rhythmc_track_snap_undo");
                trackSnapUndoAvailable = false;
                lastTrackSnapSummary = null;
                MinecraftClient.getInstance().setScreen(new PlaceholderScreen("轨道吸附", "track-snap"));
            });
            undo.active(trackSnapUndoAvailable && chart != null && chart.id.equals(trackSnapChartId));
            page.child(undo.horizontalSizing(Sizing.fill()));
        } else if (section.equals("track-snap-confirm")) {
            var chart = ClientChartAccess.resolveActiveChart();
            TrackSnapSummary summary = trackSnapSummary(chart);
            if (chart != null && chart.id.equals(trackSnapChartId) && lastTrackSnapSummary != null) summary = lastTrackSnapSummary;
            page.child(UIComponents.label(Text.literal("谱面基础 BPM：" + (chart == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", chart.bpm)))));
            page.child(UIComponents.label(Text.literal("当前每 Chunk 分数：" + (chart == null ? "-" : Math.max(1, chart.divisionsPerChunk)))));
            page.child(UIComponents.label(Text.literal("待吸附音符数量：" + summary.fractionalCount)));
            page.child(UIComponents.label(Text.literal("预计重复删除数量：" + summary.duplicateCount)));
            page.child(UIComponents.label(Text.literal("吸附后剩余音符数量：" + summary.remainingCount)));
            TrackSnapSummary confirmedSummary = summary;
            page.child(UIComponents.label(Text.literal("这项操作会改变音符时机，可能造成偏差，请确认你真的要吸附")));
            page.child(UIComponents.button(Text.literal("确认吸附"), button -> {
                if (chart == null || confirmedSummary.fractionalCount == 0) return;
                RhythmcMakerClient.sendChartCommand("rhythmc_track_snap");
                MinecraftClient.getInstance().setScreen(null);
            }).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("取消"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("轨道吸附", "track-snap"))).horizontalSizing(Sizing.fill()));
        } else if (section.equals("quick-teleport-select")) {
            page.child(UIComponents.label(Text.literal("选择传送目标")));
            page.child(UIComponents.button(Text.literal("按 Chunk 传送"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("快捷传送", "quick-teleport-chunk"))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("按歌曲时间传送"), button -> MinecraftClient.getInstance().setScreen(new PlaceholderScreen("快捷传送", "quick-teleport-time"))).horizontalSizing(Sizing.fill()));
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
            double totalSeconds = chart == null ? 0.0 : chart.durationSeconds > 0.0 ? chart.durationSeconds : chart.totalBeats > 0.0 && chart.bpm > 0.0 ? ChartTiming.beatToSeconds(chart, chart.totalBeats) : Math.max(1, chart.chunkCount) * 60.0 / Math.max(1.0, chart.bpm);
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
            int currentDivisions = chart == null ? ClientChartAccess.defaultDivisionsPerChunk() : EditorTrackLayout.boundedDivisionsPerChunk(chart.divisionsPerChunk);
            int currentLanes = EditorTrackLayout.supportedLaneCount(chart == null ? EditorTrackLayout.DEFAULT_LANE_COUNT : chart.laneCount);
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
                    if (!EditorTrackLayout.isSupportedDivisionsPerChunk(divisionsValue) || !EditorTrackLayout.isSupportedLaneCount(lanesValue)) throw new NumberFormatException();
                    EditorTrackLayout layout = EditorTrackLayout.of(lanesValue, divisionsValue);
                    ClientChartAccess.applyLocalLayout(layout);
                    RhythmcMakerClient.sendChartCommand("rhythmc_layout_set " + layout.divisionsPerChunk() + " " + layout.laneCount());
                    ClientChartAccess.status("正在应用制谱器轨道：" + layout.laneCount() + " × " + layout.divisionsPerChunk());
                    close();
                } catch (Exception exception) {
                    ClientChartAccess.status("设置失败：每Chunk分数为 1-32，轨道宽度为 1/3/5/7/9");
                }
            }).horizontalSizing(Sizing.fill()));
        } else if (section.equals("editor")) {
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
        var scroll = UIContainers.verticalScroll(Sizing.fixed(pageWidth), Sizing.fixed(Math.max(180, this.height - 30)), page);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(scroll));
    }

    private static TrackSnapSummary trackSnapSummary(cn.frkovo.rhythmcmaker.chart.ChartManifest chart) {
        if (chart == null || chart.notes == null) return new TrackSnapSummary(0, 0, 0);
        int fractional = 0;
        int duplicates = 0;
        Set<TrackSnapKey> retained = new HashSet<>();
        for (var note : chart.notes) {
            if (note == null) continue;
            boolean isFractional = Math.abs(note.z - Math.rint(note.z)) > 0.000001;
            double targetZ = isFractional ? Math.ceil(note.z - 0.5) : note.z;
            TrackSnapKey key = new TrackSnapKey(note.x, note.y, targetZ, note.type);
            if (isFractional) {
                fractional++;
                if (!retained.add(key)) duplicates++;
            } else if (!retained.add(key)) duplicates++;
        }
        return new TrackSnapSummary(fractional, duplicates, chart.notes.size() - duplicates);
    }

    private record TrackSnapKey(double x, double y, double z, int type) {}
    private record TrackSnapSummary(int fractionalCount, int duplicateCount, int remainingCount) {}

    static void setTrackSnapUndoAvailable(java.util.UUID playerId, cn.frkovo.rhythmcmaker.chart.ChartManifest chart) {
        if (playerId == null || chart == null) return;
        trackSnapUndoAvailable = true;
        trackSnapChartId = chart.id;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
