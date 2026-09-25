package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;

final class SceneEditScreen extends RhythmcScreen {
    private final SceneEditStorage.Scene selected;
    private TextBoxComponent nameBox;
    private TextBoxComponent iconBox;

    SceneEditScreen() { this(null); }
    SceneEditScreen(SceneEditStorage.Scene selected) { this.selected = selected; }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.content()), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(UIComponents.label(Text.literal("场景编辑")).shadow(true));
        if (selected == null) {
            page.child(UIComponents.button(Text.literal("新建场景"), button -> create()).horizontalSizing(Sizing.fill()));
            try {
                List<SceneEditStorage.Scene> scenes = ClientSceneAccess.list();
                if (scenes.isEmpty()) page.child(wrappedLabel("暂无场景，请先新建场景。", pageWidth() - 24));
                for (SceneEditStorage.Scene scene : scenes) {
                    page.child(UIComponents.button(Text.literal(scene.name + "  [" + scene.icon + "]"), button -> MinecraftClient.getInstance().setScreen(new SceneEditScreen(scene))).horizontalSizing(Sizing.fill()));
                }
            } catch (IOException exception) {
                page.child(wrappedLabel("读取场景失败：" + exception.getMessage(), pageWidth() - 24));
            }
        } else {
            page.child(UIComponents.label(Text.literal("场景 " + selected.index + " 设置")).shadow(true));
            page.child(UIComponents.label(Text.literal("场景名称")));
            nameBox = UIComponents.textBox(Sizing.fill(), selected.name);
            page.child(nameBox);
            page.child(UIComponents.label(Text.literal("对应图标物品 ID")));
            iconBox = UIComponents.textBox(Sizing.fill(), selected.icon);
            page.child(iconBox);
            page.child(UIComponents.button(Text.literal("保存设置"), button -> save()).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("重置场景"), button -> MinecraftClient.getInstance().setScreen(new SceneResetConfirmScreen(selected))).horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("开始搭建"), button -> { ClientSceneAccess.teleport(selected.index); close(); }).horizontalSizing(Sizing.fill()));
        }
        page.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void create() {
        try {
            SceneEditStorage.Scene scene = ClientSceneAccess.create();
            ClientSceneAccess.status("已新建 " + scene.name);
            MinecraftClient.getInstance().setScreen(new SceneEditScreen());
        } catch (IOException exception) { ClientSceneAccess.status("新建场景失败：" + exception.getMessage()); }
    }

    private void save() {
        String name = nameBox.getText().trim();
        String icon = iconBox.getText().trim();
        if (name.isBlank()) { ClientSceneAccess.status("场景名称不能为空"); return; }
        if (icon.isBlank() || !icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { ClientSceneAccess.status("图标必须是有效物品 ID"); return; }
        selected.name = name; selected.icon = icon;
        try { ClientSceneAccess.save(selected); ClientSceneAccess.status("场景设置已保存"); MinecraftClient.getInstance().setScreen(new SceneEditScreen()); }
        catch (IOException exception) { ClientSceneAccess.status("保存场景失败：" + exception.getMessage()); }
    }
}
