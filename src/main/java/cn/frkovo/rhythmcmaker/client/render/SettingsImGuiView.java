package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.client.ClientChartAccess;
import cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

public final class SettingsImGuiView {
    private static final int PAGE_EDITOR = 0;
    private static final int PAGE_SOUND = 1;
    private static final int PAGE_DISPLAY = 2;
    private static final int PAGE_HELP = 3;
    private static final double MIN_AUTOSAVE = 0.05;
    private static final double MAX_AUTOSAVE = 86400.0;
    private static final double MIN_REFRESH = 0.05;
    private static final double MAX_REFRESH = 3600.0;
    private static final double MIN_SPEED = 0.1;
    private static final double MAX_SPEED = 5.0;
    private static final double MIN_MUSIC_VOLUME = 0.1;
    private static final double MAX_MUSIC_VOLUME = 2.0;
    private static final double MIN_NOTE_VOLUME = 0.1;
    private static final double MAX_NOTE_VOLUME = 5.0;
    private static final int MIN_BEAT_SCORE = 1;
    private static final int MAX_BEAT_SCORE = 32;

    private final Runnable closeAction;
    private final RhythmcMakerConfig config;
    private final ImDouble autosave = new ImDouble();
    private final ImDouble refresh = new ImDouble();
    private final ImDouble speed = new ImDouble();
    private final ImInt beatScore = new ImInt();
    private final ImDouble musicVolume = new ImDouble();
    private final ImDouble noteVolume = new ImDouble();
    private final ImString lobbyContent;
    private int page = PAGE_EDITOR;

    public SettingsImGuiView(Runnable closeAction) {
        this.closeAction = closeAction;
        this.config = ClientChartAccess.config();
        this.lobbyContent = new ImString(config.lobbySidebarContent, 121);
        syncFields();
    }

    public void render(MinecraftClient client) {
        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float displayHeight = ImGui.getIO().getDisplaySizeY();
        float width = Math.max(1.0f, Math.min(displayWidth - 24.0f, Math.max(420.0f, displayWidth * 0.86f)));
        float height = Math.max(1.0f, Math.min(displayHeight - 24.0f, Math.max(300.0f, displayHeight * 0.86f)));
        ImGui.setNextWindowPos((displayWidth - width) * 0.5f, (displayHeight - height) * 0.5f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(Math.min(360.0f, width), Math.min(260.0f, height),
                Math.max(1.0f, displayWidth - 16.0f), Math.max(1.0f, displayHeight - 16.0f));
        if (ImGui.begin("设置##rhythmc-settings", ImGuiWindowFlags.NoCollapse)) {
            ImGui.text("RhythMC Maker 设置");
            ImGui.separator();
            float availableWidth = ImGui.getContentRegionAvailX();
            float sidebarWidth = Math.min(190.0f, Math.max(140.0f, availableWidth * 0.25f));
            boolean narrow = availableWidth < 430.0f;
            if (narrow) {
                ImInt selected = new ImInt(page);
                ImGui.setNextItemWidth(-1.0f);
                if (ImGui.combo("##settings-page", selected, new String[]{"制谱器设置", "声音设置", "显示设置", "帮助"})) page = selected.get();
            }
            float contentHeight = Math.max(1.0f, ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing() - 8.0f);
            if (!narrow) {
                boolean sidebarVisible = ImGui.beginChild("settings-sidebar", sidebarWidth, contentHeight, true);
                if (sidebarVisible) {
                    sidebarButton("制谱器设置", PAGE_EDITOR);
                    sidebarButton("声音设置", PAGE_SOUND);
                    sidebarButton("显示设置", PAGE_DISPLAY);
                    sidebarButton("帮助", PAGE_HELP);
                }
                ImGui.endChild();
                ImGui.sameLine();
            }
            boolean contentVisible = ImGui.beginChild("settings-content", 0.0f, contentHeight, true);
            if (contentVisible) renderPage();
            ImGui.endChild();
            ImGui.separator();
            if (ImGui.button("关闭##settings-close")) closeAction.run();
        }
        ImGui.end();
    }

    private void sidebarButton(String label, int targetPage) {
        float itemWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        if (ImGui.selectable(label, page == targetPage, 0, itemWidth, 38.0f)) page = targetPage;
    }

    private void renderPage() {
        if (page == PAGE_EDITOR) renderEditorPage();
        else if (page == PAGE_SOUND) renderSoundPage();
        else if (page == PAGE_DISPLAY) renderDisplayPage();
        else renderHelpPage();
    }

    private void renderEditorPage() {
        ImGui.text("制谱器设置");
        ImGui.separator();
        renderDouble("自动保存间隔 (s)", autosave, MIN_AUTOSAVE, MAX_AUTOSAVE, 10.0, "%.2f");
        renderDouble("谱面信息刷新间隔 (s)", refresh, MIN_REFRESH, MAX_REFRESH, 0.05, "%.2f");
        renderDouble("制谱器流速", speed, MIN_SPEED, MAX_SPEED, 0.1, "%.2f");
        renderInteger("默认每 beat 分数", beatScore, MIN_BEAT_SCORE, MAX_BEAT_SCORE, 1);
    }

    private void renderSoundPage() {
        ImGui.text("声音设置");
        ImGui.separator();
        renderDouble("音乐音量", musicVolume, MIN_MUSIC_VOLUME, MAX_MUSIC_VOLUME, 0.1, "%.2f");
        renderDouble("音符判定音量", noteVolume, MIN_NOTE_VOLUME, MAX_NOTE_VOLUME, 0.1, "%.2f");
    }

    private void renderDisplayPage() {
        ImGui.text("显示设置");
        ImGui.separator();
        ImGui.text("制谱器大厅正文内容");
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputTextMultiline("##lobby-content", lobbyContent, -1.0f, 120.0f, ImGuiInputTextFlags.CallbackResize)) {
            String value = lobbyContent.get().replace("\r\n", "\n");
            if (value.length() > 120) value = value.substring(0, 120);
            lobbyContent.set(value);
            config.lobbySidebarContent = value;
            save();
        }
        ImGui.text("输入框支持换行，HUD 会按换行显示；文本不会自动折行。");
    }

    private void renderHelpPage() {
        ImGui.text("帮助");
        ImGui.separator();
        ImGui.textWrapped("右键设置物品或按 H 打开此菜单。输入框会立即过滤非法字符，并在超出范围时钳制到最近合法值。");
        ImGui.textWrapped("数字输入支持旁边的 - / + 按钮；不同设置使用不同步进：自动保存 10，刷新间隔 0.05，流速和音量 0.1，每 beat 分数 1。");
    }

    private void renderDouble(String label, ImDouble field, double minimum, double maximum, double step, String format) {
        ImGui.text(label);
        ImGui.setNextItemWidth(Math.max(1.0f, Math.min(190.0f, ImGui.getContentRegionAvailX() - 76.0f)));
        double previous = field.get();
        boolean changed = ImGui.inputDouble("##" + label, field, 0.0, 0.0, format);
        double value = Double.isNaN(field.get()) ? previous : Math.max(minimum, Math.min(maximum, field.get()));
        if (value != field.get()) {
            field.set(value);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("-##minus-" + label)) {
            field.set(clamp(field.get() - step, minimum, maximum));
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("+##plus-" + label)) {
            field.set(clamp(field.get() + step, minimum, maximum));
            changed = true;
        }
        if (changed) applyDouble(label, field.get());
    }

    private void renderInteger(String label, ImInt field, int minimum, int maximum, int step) {
        ImGui.text(label);
        ImGui.setNextItemWidth(Math.max(1.0f, Math.min(190.0f, ImGui.getContentRegionAvailX() - 76.0f)));
        boolean changed = ImGui.inputInt("##" + label, field, 0, 0);
        int value = Math.max(minimum, Math.min(maximum, field.get()));
        if (value != field.get()) {
            field.set(value);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("-##minus-" + label)) {
            field.set(Math.max(minimum, field.get() - step));
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("+##plus-" + label)) {
            field.set(Math.min(maximum, field.get() + step));
            changed = true;
        }
        if (changed) {
            config.defaultDivisionsPerBeat = field.get();
            save();
        }
    }

    private void applyDouble(String label, double value) {
        if (label.startsWith("自动保存")) config.autosaveIntervalSeconds = value;
        else if (label.startsWith("谱面信息")) config.sidebarRefreshIntervalSeconds = value;
        else if (label.startsWith("制谱器流速")) config.playerSpeed = value;
        else if (label.startsWith("音乐音量")) config.musicVolumeMultiplier = value;
        else if (label.startsWith("音符判定")) config.noteJudgementVolumeMultiplier = value;
        save();
    }

    private void syncFields() {
        autosave.set(config.autosaveIntervalSeconds);
        refresh.set(config.sidebarRefreshIntervalSeconds);
        speed.set(config.playerSpeed);
        beatScore.set(config.defaultDivisionsPerBeat);
        musicVolume.set(config.musicVolumeMultiplier);
        noteVolume.set(config.noteJudgementVolumeMultiplier);
        lobbyContent.set(config.lobbySidebarContent);
    }

    private void save() {
        try {
            ClientChartAccess.saveConfig(config);
            ClientChartAccess.status("设置已保存");
        } catch (Exception exception) {
            ClientChartAccess.status("设置保存失败");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }
}
