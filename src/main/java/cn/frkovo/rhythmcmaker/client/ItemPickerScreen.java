package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

final class ItemPickerScreen extends RhythmcScreen {
    private final Screen parent;
    private final Consumer<ItemStack> onSelected;

    ItemPickerScreen(Screen parent, Consumer<ItemStack> onSelected) {
        this.parent = parent;
        this.onSelected = onSelected;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int pageHeight = pageHeight();
        int innerWidth = pageWidth - 38;

        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.fixed(pageHeight)), 10, 6, 0xEE101821, 0xFF6689B3);
        page.child(UIComponents.label(Text.literal("选择曲绘物品")).shadow(true));
        page.child(wrappedLabel("从玩家背包中选择一个非空物品。选择后只保存物品外观，不会移动或消耗物品。", innerWidth));

        FlowLayout entries = spaced(UIContainers.verticalFlow(Sizing.fixed(innerWidth), Sizing.content()), 3);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            for (int index = 0; index < 36; index++) {
                ItemStack stack = client.player.getInventory().getStack(index);
                String prefix = index < 9 ? "快捷栏 " + (index + 1) + " · " : "背包 " + (index - 8) + " · ";
                String label = stack.isEmpty() ? prefix + "空" : prefix + stack.getName().getString();
                entries.child(UIComponents.button(Text.literal(label), button -> {
                    if (stack.isEmpty()) {
                        ClientChartAccess.status("曲绘必须选择非空物品");
                        return;
                    }
                    onSelected.accept(stack.copy());
                }).horizontalSizing(Sizing.fixed(innerWidth)).verticalSizing(Sizing.fixed(24)));
            }
        }

        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), entries));
        page.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(86)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
