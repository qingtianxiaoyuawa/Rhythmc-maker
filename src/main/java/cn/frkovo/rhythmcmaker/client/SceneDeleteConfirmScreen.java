package cn.frkovo.rhythmcmaker.client;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
final class SceneDeleteConfirmScreen extends RhythmcScreen {
    private final SceneEditStorage.Scene scene;
    SceneDeleteConfirmScreen(SceneEditStorage.Scene scene) { this.scene = scene; }
    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(menuHeader("确认删除场景", "此操作会移除当前场景记录", pageWidth() - 24));
        page.child(wrappedLabel("删除场景后，后续场景会前移补位。此操作需要连续确认两次。", pageWidth() - 24));
        page.child(UIComponents.button(Text.literal("第一次确认"), button -> MinecraftClient.getInstance().setScreen(new SceneDeleteFinalScreen(scene))).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("取消"), button -> MinecraftClient.getInstance().setScreen(new SceneEditScreen(scene))).horizontalSizing(Sizing.fill()));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }
}
