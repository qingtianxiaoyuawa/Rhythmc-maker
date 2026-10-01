package cn.frkovo.rhythmcmaker.client;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
final class SceneDeleteFinalScreen extends RhythmcScreen {
    private final SceneEditStorage.Scene scene;
    SceneDeleteFinalScreen(SceneEditStorage.Scene scene) { this.scene = scene; }
    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(menuHeader("最后确认删除", "请再次确认后继续", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("确认删除"), button -> { ClientSceneAccess.delete(scene.index); close(); }).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("返回"), button -> MinecraftClient.getInstance().setScreen(new SceneEditScreen(scene))).horizontalSizing(Sizing.fill()));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }
}
