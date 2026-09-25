package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

final class CreateChartScreen extends RhythmcScreen {
    private final ChartManifest draft;
    private final Path stagedAudio;
    private final String errorMessage;
    private final boolean bpmMeasuring;
    private TextBoxComponent titleBox;
    private TextBoxComponent artistBox;
    private TextBoxComponent charterBox;
    private TextBoxComponent bpmBox;
    private TextBoxComponent levelBox;

    CreateChartScreen() {
        this(newDraft(), null, "");
    }

    CreateChartScreen(ChartManifest draft, Path stagedAudio) {
        this(draft, stagedAudio, "", false);
    }

    private CreateChartScreen(ChartManifest draft, Path stagedAudio, String errorMessage) {
        this(draft, stagedAudio, errorMessage, false);
    }

    private CreateChartScreen(ChartManifest draft, Path stagedAudio, String errorMessage, boolean bpmMeasuring) {
        this.draft = draft;
        this.stagedAudio = stagedAudio;
        this.errorMessage = errorMessage;
        this.bpmMeasuring = bpmMeasuring;
    }

    private static ChartManifest newDraft() {
        ChartManifest draft = new ChartManifest();
        draft.divisionsPerChunk = ClientChartAccess.defaultDivisionsPerChunk();
        draft.id = UUID.randomUUID().toString();
        draft.title = "";
        draft.artist = "";
        draft.charter = MinecraftClient.getInstance().player == null ? "" : MinecraftClient.getInstance().player.getName().getString();
        draft.bpm = 120;
        draft.difficulty = "WD";
        draft.level = 1;
        draft.coverItemId = "";
        return draft;
    }

    private static ChartManifest copyDraft(ChartManifest source) {
        ChartManifest copy = new ChartManifest();
        copy.id = source.id;
        copy.title = source.title;
        copy.artist = source.artist;
        copy.charter = source.charter;
        copy.bpm = source.bpm;
        copy.sceneInitialized = source.sceneInitialized;
        copy.difficulty = source.difficulty;
        copy.level = source.level;
        copy.divisionsPerChunk = source.divisionsPerChunk;
        copy.beatsPerMeasure = source.beatsPerMeasure;
        copy.coverItemId = source.coverItemId;
        copy.audioFile = source.audioFile;
        copy.lastEdited = source.lastEdited;
        copy.durationSeconds = source.durationSeconds;
        copy.totalBeats = source.totalBeats;
        copy.chunkCount = source.chunkCount;
        copy.trackLength = source.trackLength;
        copy.editorSlot = source.editorSlot;
        copy.dimensionId = source.dimensionId;
        copy.selectedStartChunk = source.selectedStartChunk;
        copy.notes = new java.util.ArrayList<>(source.notes);
        return copy;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int pageHeight = pageHeight();
        int innerWidth = pageWidth - 38;
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.fixed(pageHeight)), 10, 6, 0xEE0F1721, 0xFF55779E);
        page.child(UIComponents.label(Text.literal("创建谱面")).shadow(true));

        FlowLayout form = spaced(UIContainers.verticalFlow(Sizing.fixed(innerWidth), Sizing.content()), 6);
        addCoverSection(form, innerWidth);
        addFormSection(form, innerWidth);
        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), form));
        page.child(UIComponents.button(Text.literal("返回谱面列表"), button -> {
                    if (!bpmMeasuring) MinecraftClient.getInstance().setScreen(new ChartListScreen());
                })
                .horizontalSizing(Sizing.fixed(120)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void addCoverSection(FlowLayout form, int width) {
        FlowLayout section = panel(width);
        section.child(UIComponents.label(Text.literal("曲绘（必选）")).shadow(true));
        ItemStack cover = ClientChartAccess.coverStack(draft.coverItemId);
        section.child(UIComponents.item(cover).horizontalSizing(Sizing.fixed(54)).verticalSizing(Sizing.fixed(54)));
        section.child(wrappedLabel(draft.coverItemId.isBlank() ? "尚未选择曲绘物品" : "当前曲绘：" + cover.getName().getString(), width - 24));
        section.child(UIComponents.button(Text.literal("选择曲绘"), button -> chooseCover()).horizontalSizing(Sizing.fill()));
        form.child(section);
    }

    private void addFormSection(FlowLayout form, int width) {
        FlowLayout section = panel(width);
        String audioText = stagedAudio == null ? "上传歌曲（必填）" : "已导入：" + stagedAudio.getFileName();
        section.child(UIComponents.button(Text.literal(audioText), button -> chooseAudio()).horizontalSizing(Sizing.fill()));
        section.child(UIComponents.label(Text.literal("歌曲名称")));
        titleBox = UIComponents.textBox(Sizing.fill(), draft.title);
        section.child(titleBox);
        section.child(UIComponents.label(Text.literal("曲师")));
        artistBox = UIComponents.textBox(Sizing.fill(), draft.artist);
        section.child(artistBox);
        section.child(UIComponents.label(Text.literal("谱师")));
        charterBox = UIComponents.textBox(Sizing.fill(), draft.charter);
        section.child(charterBox);
        if (bpmMeasuring) {
            section.child(UIComponents.label(Text.literal("正在测量BPM").styled(style -> style.withColor(0xF05B64))));
        }
        section.child(UIComponents.label(Text.literal("BPM（可输入小数）")));
        bpmBox = UIComponents.textBox(Sizing.fill(), trimNumber(draft.bpm));
        numericOnly(bpmBox);
        section.child(bpmBox);
        section.child(UIComponents.label(Text.literal("难度：" + draft.difficulty).styled(style -> style.withColor(ClientChartAccess.difficultyColor(draft.difficulty)))));
        section.child(UIComponents.button(Text.literal("选择难度：" + draft.difficulty), button -> openDifficultyDropdown(button))
                .horizontalSizing(Sizing.fill()));
        section.child(UIComponents.label(Text.literal("定数（可输入小数）").styled(style -> style.withColor(ClientChartAccess.difficultyColor(draft.difficulty)))));
        levelBox = UIComponents.textBox(Sizing.fill(), trimNumber(draft.level));
        numericOnly(levelBox);
        section.child(levelBox);
        section.child(wrappedLabel("支持音频：mp3、flac、wav、ogg、m4a、aac。", width - 24));
        if (!errorMessage.isBlank()) {
            section.child(wrappedLabel(Text.literal("创建失败：" + errorMessage).styled(style -> style.withColor(0xF05B64)), width - 24));
        }
        section.child(UIComponents.button(Text.literal("创建谱面"), button -> createChart()).horizontalSizing(Sizing.fill()).verticalSizing(Sizing.fixed(34)));
        form.child(section);
    }

    private void openDifficultyDropdown(io.wispforest.owo.ui.component.ButtonComponent button) {
        DropdownComponent.openContextMenu(this, this.uiAdapter.rootComponent, (parent, dropdown) -> parent.child(dropdown),
                button.x(), button.y() + button.height(), dropdown -> {
                    for (String difficulty : new String[]{"WD", "NR", "ED", "VO"}) {
                        dropdown.button(Text.literal(difficulty).styled(style -> style.withColor(ClientChartAccess.difficultyColor(difficulty))), menu -> {
                            this.uiAdapter.rootComponent.removeChild(menu);
                            setDifficulty(difficulty);
                        });
                    }
                });
    }

    private void chooseCover() {
        if (bpmMeasuring || !syncDraft()) return;
        MinecraftClient.getInstance().setScreen(new ItemPickerScreen(this, stack -> {
            draft.coverItemId = Registries.ITEM.getId(stack.getItem()).toString();
            MinecraftClient.getInstance().setScreen(new CreateChartScreen(draft, stagedAudio));
        }));
    }

    private void chooseAudio() {
        if (bpmMeasuring || !syncDraft()) return;
        ChartManifest preservedDraft = copyDraft(draft);
        AudioFileChooser.choose(source -> {
            try {
                Path staged = ClientChartAccess.stageAudio(source);
                ClientChartAccess.status("歌曲已复制到世界目录：" + staged.getFileName());
                CreateChartScreen measuring = new CreateChartScreen(preservedDraft, staged, "", true);
                MinecraftClient.getInstance().setScreen(measuring);
                measuring.measureBpm(staged);
            } catch (IOException exception) {
                ClientChartAccess.status("导入歌曲失败：" + exception.getMessage());
            }
        });
    }

    private void measureBpm(Path audio) {
        Thread.ofVirtual().start(() -> {
            try {
                var analysis = ClientChartAccess.analyzeTiming(audio);
                MinecraftClient.getInstance().execute(() -> {
                    draft.bpm = Math.max(1L, Math.round(analysis.selectedBpm()));
                    MinecraftClient.getInstance().setScreen(new CreateChartScreen(draft, stagedAudio));
                    String confidence = analysis.confidence() >= 0.60 ? "高" : analysis.confidence() >= 0.25 ? "中" : "低";
                    String candidates = analysis.candidates().stream().limit(3).map(candidate -> trimNumber(candidate.bpm())).collect(java.util.stream.Collectors.joining("/"));
                    ClientChartAccess.status("BPM 测量完成：" + draft.bpm + "，置信度 " + confidence + "（候选 " + candidates + "）");
                });
            } catch (IOException exception) {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(new CreateChartScreen(draft, stagedAudio, "BPM 测量失败：" + exception.getMessage()));
                    ClientChartAccess.status("BPM 测量失败，将保留当前数值");
                });
            }
        });
    }

    @Override public boolean shouldCloseOnEsc() {
        return !bpmMeasuring;
    }

    private void setDifficulty(String difficulty) {
        if (!syncDraft()) return;
        draft.difficulty = difficulty;
        MinecraftClient.getInstance().setScreen(new CreateChartScreen(draft, stagedAudio));
    }

    private void createChart() {
        if (bpmMeasuring) return;
        try {
            if (!syncDraft()) return;
            ClientChartAccess.create(draft, stagedAudio);
            ClientChartAccess.status("已创建谱面：" + draft.title);
            MinecraftClient.getInstance().setScreen(null);
        } catch (IOException | NumberFormatException exception) {
            showCreateError(exception.getMessage());
        }
    }

    private boolean syncDraft() {
        if (titleBox == null) return true;
        try {
            draft.title = titleBox.getText().trim();
            draft.artist = artistBox.getText().trim();
            draft.charter = charterBox.getText().trim();
            draft.bpm = Double.parseDouble(bpmBox.getText().trim());
            draft.level = Double.parseDouble(levelBox.getText().trim());
            return true;
        } catch (NumberFormatException exception) {
            showCreateError("BPM 和定数必须为有效数字");
            return false;
        }
    }

    private void showCreateError(String message) {
        MinecraftClient.getInstance().setScreen(new CreateChartScreen(draft, stagedAudio, message == null || message.isBlank() ? "未知错误" : message));
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
