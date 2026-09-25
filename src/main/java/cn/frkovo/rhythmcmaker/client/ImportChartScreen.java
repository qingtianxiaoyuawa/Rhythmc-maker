package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ImportChartScreen extends RhythmcScreen {
    private final Draft draft; private final int sceneIndex;
    ImportChartScreen() { this(new Draft(), -1); }
    ImportChartScreen(Draft draft, int sceneIndex) { this.draft = draft; this.sceneIndex = sceneIndex; }
    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        if (!draft.version3) versionMenu(page); else if (sceneIndex >= 0) sceneFiles(page); else importMenu(page);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }
    private void versionMenu(FlowLayout page) {
        page.child(UIComponents.label(Text.literal("导入谱面")).shadow(true));
        page.child(UIComponents.button(Text.literal("2.0 谱面导入"), button -> ClientChartAccess.status("2.0 谱面导入暂未开放")).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("3.0 谱面导入"), button -> { draft.version3 = true; reopen(-1); }).horizontalSizing(Sizing.fill())); back(page);
    }
    private void importMenu(FlowLayout page) {
        page.child(UIComponents.label(Text.literal("导入 3.0 谱面")).shadow(true));
        upload(page, "上传难度文件", draft.difficulty, path -> draft.difficulty = path, "难度文件|*.rmcc;*.json");
        upload(page, "上传谱面信息文件", draft.manifest, path -> draft.manifest = path, "谱面信息|manifest.yml;*.yml;*.yaml");
        upload(page, "上传歌曲文件", draft.song, path -> draft.song = path, "音频文件|*.mp3;*.flac;*.wav;*.ogg;*.m4a;*.aac");
        page.child(UIComponents.button(Text.literal("上传场景（" + draft.scenes.size() + "）"), button -> { if(draft.scenes.isEmpty()) draft.scenes.add(new Scene()); MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft)); }).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("开始导入"), button -> importChart()).horizontalSizing(Sizing.fill())); back(page);
    }
    private void sceneFiles(FlowLayout page) {
        Scene scene = draft.scenes.get(sceneIndex); String name = sceneIndex == 0 ? "初始场景" : "场景" + sceneIndex;
        page.child(UIComponents.label(Text.literal(name)).shadow(true));
        upload(page, "上传" + name, scene.schem, path -> scene.schem = path, "场景文件|*.schem");
        upload(page, "上传" + name + "信息", scene.info, path -> scene.info = path, "场景信息|metadata.yml;*.yml;*.yaml");
        page.child(UIComponents.button(Text.literal("返回场景"), button -> MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft))).horizontalSizing(Sizing.fill()));
    }
    private void upload(FlowLayout page, String title, Path value, java.util.function.Consumer<Path> save, String filter) { page.child(UIComponents.button(Text.literal(title + "：" + (value == null ? "未选择" : value.getFileName())), button -> GenericFileChooser.choose(filter, path -> { save.accept(path); reopen(sceneIndex); })).horizontalSizing(Sizing.fill())); }
    private void importChart() {
        if (draft.difficulty == null || draft.manifest == null || draft.song == null) { ClientChartAccess.status("请先上传难度文件、谱面信息文件和歌曲文件"); return; }
        try { ClientChartAccess.importRhythmc3(draft.difficulty, draft.manifest, ClientChartAccess.stageAudio(draft.song)); ClientChartAccess.status("3.0 谱面已导入"); MinecraftClient.getInstance().setScreen(new ChartListScreen()); } catch (Exception exception) { ClientChartAccess.status("导入失败：" + exception.getMessage()); }
    }    private void back(FlowLayout page) { page.child(UIComponents.button(Text.literal("返回"), button -> MinecraftClient.getInstance().setScreen(new ChartListScreen())).horizontalSizing(Sizing.fixed(100))); }
    private void reopen(int index) { MinecraftClient.getInstance().setScreen(new ImportChartScreen(draft, index)); }
    static final class Draft { boolean version3; Path difficulty, manifest, song; final List<Scene> scenes = new ArrayList<>(); }
    static final class Scene { Path schem, info; }
}
