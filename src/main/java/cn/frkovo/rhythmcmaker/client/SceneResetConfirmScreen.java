package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

final class SceneResetConfirmScreen extends RhythmcScreen {
    private final SceneEditStorage.Scene scene;
    SceneResetConfirmScreen(SceneEditStorage.Scene scene) { this.scene = scene; }
    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(UIComponents.label(Text.literal("确认重置场景")).shadow(true));
        page.child(wrappedLabel("重置将清除场景编辑区域内的建筑，并恢复默认场地。此操作需要连续确认两次。", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("第一次确认"), button -> MinecraftClient.getInstance().setScreen(new SceneResetFinalScreen(scene))).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("取消"), button -> MinecraftClient.getInstance().setScreen(new SceneEditScreen(scene))).horizontalSizing(Sizing.fill()));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }
}
