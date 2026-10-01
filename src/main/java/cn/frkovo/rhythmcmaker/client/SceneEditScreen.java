package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
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
import java.util.List;

final class SceneEditScreen extends RhythmcScreen {
    private final SceneEditStorage.Scene selected;
    private TextBoxComponent nameBox;

    SceneEditScreen() { this(null); }
    SceneEditScreen(SceneEditStorage.Scene selected) { this.selected = selected; }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int viewportHeight = Math.max(180, pageHeight() - 24);
        int contentWidth = pageWidth() - 24;
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.fixed(pageHeight())), 12, 12, 0xEE0E1620, 0xFF5B7596);
        FlowLayout content = UIContainers.verticalFlow(Sizing.fixed(contentWidth), Sizing.content());
        content.gap(10);
        content.child(menuHeader("场景编辑", "管理场景、图标与搭建入口", contentWidth));
        if (selected == null) {
            content.child(wrappedLabel("新建场景后，使用主手物品作为场景图标。", contentWidth));
            content.child(UIComponents.button(Text.literal("新建场景"), button -> create()).horizontalSizing(Sizing.fill()));
            try {
                List<SceneEditStorage.Scene> scenes = ClientSceneAccess.list();
                if (scenes.isEmpty()) content.child(wrappedLabel("暂无场景，请先新建场景。", contentWidth));
                for (SceneEditStorage.Scene scene : scenes) {
                    String status = scene.saved ? "已保存" : "未保存";
                    content.child(UIComponents.button(Text.literal(scene.name + "  [" + scene.icon + "]  " + status), button -> MinecraftClient.getInstance().setScreen(new SceneEditScreen(scene))).horizontalSizing(Sizing.fill()));
                }
            } catch (IOException exception) {
                content.child(wrappedLabel("读取场景失败：" + exception.getMessage(), contentWidth));
            }
        } else {
            content.child(UIComponents.label(Text.literal("场景 " + selected.index + " 设置")).shadow(true));
            content.child(UIComponents.label(Text.literal("场景名称")));
            nameBox = UIComponents.textBox(Sizing.fill(), selected.name);
            content.child(nameBox);
            content.child(UIComponents.label(Text.literal("当前图标物品 ID：" + selected.icon)));
            content.child(UIComponents.button(Text.literal("使用主手物品作为图标"), button -> useHeldItemAsIcon()).horizontalSizing(Sizing.fill()));
            content.child(UIComponents.label(Text.literal("保存状态：" + (selected.saved ? "已保存，可供后续特效调用" : "未保存"))));
            content.child(UIComponents.button(Text.literal("保存设置"), button -> save()).horizontalSizing(Sizing.fill()));
            content.child(UIComponents.button(Text.literal("重置场景"), button -> MinecraftClient.getInstance().setScreen(new SceneResetConfirmScreen(selected))).horizontalSizing(Sizing.fill()));
            if (selected.index > 1) content.child(UIComponents.button(Text.literal("删除场景"), button -> MinecraftClient.getInstance().setScreen(new SceneDeleteConfirmScreen(selected))).horizontalSizing(Sizing.fill()));
            content.child(UIComponents.button(Text.literal("开始搭建"), button -> { ClientSceneAccess.teleport(selected.index); close(); }).horizontalSizing(Sizing.fill()));
        }
        content.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.fixed(viewportHeight), content));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void create() {
        try {
            SceneEditStorage.Scene scene = ClientSceneAccess.create();
            ClientSceneAccess.status("场景正在生成或重置中，期间请勿进行其他操作");
            MinecraftClient.getInstance().setScreen(null);
        } catch (IOException exception) { ClientSceneAccess.status("新建场景失败：" + exception.getMessage()); }
    }

    private void useHeldItemAsIcon() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) { ClientSceneAccess.status("主手没有可用物品"); return; }
        selected.icon = Registries.ITEM.getId(stack.getItem()).toString();
        ClientSceneAccess.status("已选择图标 " + selected.icon + "，请点击保存设置");
        client.setScreen(new SceneEditScreen(selected));
    }

    private void save() {
        String name = nameBox.getText().trim();
        if (name.isBlank()) { ClientSceneAccess.status("场景名称不能为空"); return; }
        if (selected.icon == null || !selected.icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { ClientSceneAccess.status("图标必须是有效物品 ID"); return; }
        selected.name = name;
        try { ClientSceneAccess.save(selected); ClientSceneAccess.status("场景设置已保存"); MinecraftClient.getInstance().setScreen(new SceneEditScreen()); }
        catch (IOException exception) { ClientSceneAccess.status("保存场景失败：" + exception.getMessage()); }
    }
}
