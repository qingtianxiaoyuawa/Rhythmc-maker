package io.wispforest.owo.ui.component;

import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.function.Consumer;

public final class UIComponents {
    private UIComponents() {}
    public static LabelComponent label(Text text) { return new LabelComponent(text); }
    public static ButtonComponent button(Text text, Consumer<ButtonComponent> action) { return new ButtonComponent(text, action); }
    public static TextBoxComponent textBox(Sizing sizing, String text) { return new TextBoxComponent(sizing, text); }
    public static ItemComponent item(ItemStack stack) { return new ItemComponent(stack); }
    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }
}
