package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

final class ImportSceneScreen extends RhythmcScreen {
    private final ImportChartScreen.Draft draft;

    ImportSceneScreen(ImportChartScreen.Draft draft) {
        this.draft = draft;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(menuHeader("上传场景", "导入 .schem 场景并绑定到当前谱面", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal(sceneLabel(0)), button -> MinecraftClient.getInstance().setScreen(new ImportChartScreen(draft, 0))).horizontalSizing(Sizing.fill()));
        for (int index = 1; index < draft.scenes.size(); index++) {
            int scene = index;
            page.child(UIComponents.button(Text.literal(sceneLabel(scene)), button -> MinecraftClient.getInstance().setScreen(new ImportChartScreen(draft, scene))).horizontalSizing(Sizing.fill()));
        }
        page.child(UIComponents.button(Text.literal("新建场景"), button -> {
            draft.scenes.add(new ImportChartScreen.Scene());
            MinecraftClient.getInstance().setScreen(new ImportSceneScreen(draft));
        }).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("返回导入"), button -> MinecraftClient.getInstance().setScreen(new ImportChartScreen(draft, -1))).horizontalSizing(Sizing.fixed(120)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(scrollPage(page)));
    }

    private String sceneLabel(int index) {
        ImportChartScreen.Scene scene = draft.scenes.get(index);
        String name = index == 0 ? "初始场景" : "场景" + index;
        return name + "（" + (scene.complete() ? "已完整提交" : "未完整提交") + "）";
    }
}
