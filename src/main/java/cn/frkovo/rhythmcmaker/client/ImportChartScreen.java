package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ImportChartScreen extends RhythmcScreen {
    private final Draft draft;
    private final int sceneIndex;
    private boolean importing;

    ImportChartScreen() { this(new Draft(), -1); }
    ImportChartScreen(Draft draft, int sceneIndex) { this.draft = draft; this.sceneIndex = sceneIndex; }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 12, 8, 0xF00E1620, 0xFF344A63);
        if (!draft.version3 && !draft.version2) versionMenu(page);
        else if (draft.version2 && sceneIndex == -2) legacyInfoMenu(page);
        else if (draft.version2) importLegacyMenu(page);
        else if (sceneIndex >= 0) sceneFiles(page);
        else importMenu(page);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(scrollPage(page)));
    }

    private void versionMenu(FlowLayout page) {
        page.child(menuHeader("导入谱面", "选择来源并将谱面加入当前工作区", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("2.0 谱面导入"), button -> { draft.version2 = true; reopen(-1); }).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("3.0 谱面导入"), button -> { draft.version3 = true; reopen(-1); }).horizontalSizing(Sizing.fill()));
        back(page);
    }

private void importLegacyMenu(FlowLayout page) {
        page.child(menuHeader("导入 2.0 谱面", "读取旧版谱面并补充必要信息", pageWidth() - 24));
        upload(page, "上传 2.0 谱面文件", draft.difficulty, path -> draft.difficulty = path, "2.0 谱面文件|*.json");
        upload(page, "上传歌曲文件", draft.song, path -> draft.song = path, "音频文件|*.mp3;*.flac;*.wav;*.ogg;*.m4a;*.aac");
        page.child(wrappedLabel("2.0 谱面只需上传难度 JSON 和歌曲文件。点击下一步后填写 BPM、难度和定数；旧版时间单位按 20 TPS 处理，定向 ARENA 特效会自动创建默认场景。", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("下一步：填写谱面信息"), button -> openLegacyInfo()).horizontalSizing(Sizing.fill()));
        back(page);
    }

    private void openLegacyInfo() {
        if (draft.difficulty == null || draft.song == null || !Files.isRegularFile(draft.difficulty) || !Files.isRegularFile(draft.song)) {
            ClientChartAccess.status("请先上传 2.0 谱面 JSON 和歌曲文件");
            return;
        }
        reopen(-2);
    }

    private void legacyInfoMenu(FlowLayout page) {
        page.child(menuHeader("填写 2.0 谱面信息", "确认 BPM、难度与定数", pageWidth() - 24));
        TextBoxComponent bpm = UIComponents.textBox(Sizing.fill(), draft.importBpm);
        TextBoxComponent difficulty = UIComponents.textBox(Sizing.fill(), draft.importDifficulty);
        TextBoxComponent level = UIComponents.textBox(Sizing.fill(), draft.importLevel);
        numericOnly(bpm);
        numericOnly(level);
        page.child(UIComponents.label(Text.literal("BPM（必填，可输入小数）")));
        page.child(bpm);
        page.child(UIComponents.label(Text.literal("难度（必填：WD / NR / ED / VO）")));
        page.child(difficulty);
        page.child(UIComponents.label(Text.literal("定数（必填，可输入小数）")));
        page.child(level);
        page.child(wrappedLabel("歌曲名称将使用 JSON 文件名；谱师和曲师在 2.0 文件中没有可靠字段时使用默认值。音频时长会用于计算制谱器轨道长度。", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("开始导入"), button -> {
            draft.importBpm = bpm.getText().trim();
            draft.importDifficulty = difficulty.getText().trim().toUpperCase(java.util.Locale.ROOT);
            draft.importLevel = level.getText().trim();
            importLegacyChart();
        }).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("返回上传文件"), button -> reopen(-1)).horizontalSizing(Sizing.fixed(140)));
    }

    private void importLegacyChart() {
        if (draft.difficulty == null || draft.song == null || !Files.isRegularFile(draft.difficulty) || !Files.isRegularFile(draft.song)) {
            ClientChartAccess.status("请先上传 2.0 谱面 JSON 和歌曲文件");
            return;
        }
        double bpm;
        double level;
        try {
            bpm = Double.parseDouble(draft.importBpm);
            level = Double.parseDouble(draft.importLevel);
        } catch (NumberFormatException exception) {
            ClientChartAccess.status("BPM 和定数必须是有效数字");
            return;
        }
        if (!(bpm > 0) || level < 0 || !java.util.Set.of("WD", "NR", "ED", "VO").contains(draft.importDifficulty)) {
            ClientChartAccess.status("请输入有效的 BPM、定数和难度（WD/NR/ED/VO）");
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null || importing) return;
        importing = true;
        Path difficulty = draft.difficulty;
        Path song = draft.song;
        ClientChartAccess.status("正在导入 2.0 谱面，请稍候...");
        client.getServer().execute(() -> {
            try {
                ClientChartAccess.importRhythmc2(difficulty, ClientChartAccess.stageAudio(song), bpm, draft.importDifficulty, level);
                client.execute(() -> {
                    importing = false;
                    ClientChartAccess.status("2.0 谱面已转换并导入为 3.0");
                    client.setScreen(new ChartListScreen());
                });
            } catch (Exception exception) {
                client.execute(() -> {
                    importing = false;
                    ClientChartAccess.status("2.0 导入失败：" + exception.getMessage());
                });
            }
        });
    }
    private void importMenu(FlowLayout page) {
        page.child(menuHeader("导入 3.0 谱面", "导入现有 RhythMC 3.0 谱面文件", pageWidth() - 24));
        upload(page, "上传难度文件", draft.difficulty, path -> draft.difficulty = path, "难度文件|*.rmcc;*.json");
        upload(page, "上传谱面信息文件", draft.manifest, path -> draft.manifest = path, "谱面信息|manifest.yml;*.yml;*.yaml");
        upload(page, "上传歌曲文件", draft.song, path -> draft.song = path, "音频文件|*.mp3;*.flac;*.wav;*.ogg;*.m4a;*.aac");
        long completeScenes = draft.scenes.stream().filter(Scene::complete).count();
        page.child(UIComponents.button(Text.literal("上传场景（完整 " + completeScenes + "，未完整 " + (draft.scenes.size() - completeScenes) + "）"), button -> {
            if (draft.scenes.isEmpty()) draft.scenes.add(new Scene());
            MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft));
        }).horizontalSizing(Sizing.fill()));
        page.child(wrappedLabel("场景可不提交；未完整场景不会导入，并使用默认制谱器平台和判定框。", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("开始导入"), button -> importChart()).horizontalSizing(Sizing.fill()));
        back(page);
    }

    private void sceneFiles(FlowLayout page) {
        Scene scene = draft.scenes.get(sceneIndex);
        String name = sceneIndex == 0 ? "初始场景" : "场景" + sceneIndex;
        page.child(UIComponents.label(Text.literal(name)).shadow(true));
        upload(page, "上传" + name, scene.schem, path -> scene.schem = path, "场景文件|*.schem");
        upload(page, "上传" + name + "信息", scene.info, path -> scene.info = path, "场景信息|metadata.yml;*.yml;*.yaml");
        page.child(UIComponents.label(Text.literal("提交状态：" + (scene.complete() ? "已完整提交" : "未完整提交"))));
        if (sceneIndex > 0) {
            page.child(UIComponents.button(Text.literal("删除" + name), button -> {
                draft.scenes.remove(sceneIndex);
                MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft));
            }).horizontalSizing(Sizing.fill()));
        }
        page.child(UIComponents.button(Text.literal("返回场景"), button -> MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft))).horizontalSizing(Sizing.fill()));
    }

    private void upload(FlowLayout page, String title, Path value, java.util.function.Consumer<Path> save, String filter) {
        page.child(UIComponents.button(Text.literal(title + "：" + (value == null ? "未选择" : value.getFileName())), button ->
            GenericFileChooser.choose(filter, path -> { save.accept(path); reopen(sceneIndex); })).horizontalSizing(Sizing.fill()));
    }

    private void importChart() {
        if (draft.difficulty == null || draft.manifest == null || draft.song == null
                || !Files.isRegularFile(draft.difficulty) || !Files.isRegularFile(draft.manifest) || !Files.isRegularFile(draft.song)) {
            ClientChartAccess.status("请先完整上传难度文件、谱面信息文件和歌曲文件");
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null || importing) return;
        importing = true;
        Path difficulty = draft.difficulty;
        Path manifest = draft.manifest;
        Path song = draft.song;
        List<ClientChartAccess.ImportedScene> completeScenes = draft.scenes.stream()
                .filter(Scene::complete)
                .map(scene -> new ClientChartAccess.ImportedScene(scene.schem, scene.info))
                .toList();
        ClientChartAccess.status("正在导入 3.0 谱面，请稍候...");
        client.getServer().execute(() -> {
            try {
                ClientChartAccess.importRhythmc3(difficulty, manifest, ClientChartAccess.stageAudio(song), completeScenes);
                client.execute(() -> {
                    importing = false;
                    ClientChartAccess.status("3.0 谱面已导入（完整场景 " + completeScenes.size() + " 个）");
                    client.setScreen(new ChartListScreen());
                });
            } catch (Exception exception) {
                client.execute(() -> {
                    importing = false;
                    ClientChartAccess.status("导入失败：" + exception.getMessage());
                });
            }
        });
    }

    private void back(FlowLayout page) { page.child(UIComponents.button(Text.literal("返回"), button -> MinecraftClient.getInstance().setScreen(new ChartListScreen())).horizontalSizing(Sizing.fixed(100))); }
    private void reopen(int index) { MinecraftClient.getInstance().setScreen(new ImportChartScreen(draft, index)); }

    static final class Draft { boolean version2, version3; Path difficulty, manifest, song; String importBpm = "120"; String importDifficulty = "WD"; String importLevel = "1"; final List<Scene> scenes = new ArrayList<>(); }
    static final class Scene {
        Path schem, info;
        boolean complete() { return schem != null && info != null && Files.isRegularFile(schem) && Files.isRegularFile(info); }
    }
}


