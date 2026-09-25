package cn.frkovo.rhythmcmaker.client;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.text.Text;

abstract class RhythmcScreen extends BaseOwoScreen<FlowLayout> {
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    protected int pageWidth() {
        return Math.max(280, Math.min(this.width - 18, 760));
    }

    protected int pageHeight() {
        return Math.max(220, Math.min(this.height - 18, 430));
    }

    protected static FlowLayout layout(FlowLayout layout, int padding, int gap, int background, int outline) {
        layout.padding(Insets.of(padding));
        layout.gap(gap);
        layout.surface(Surface.flat(background).and(Surface.outline(outline)));
        return layout;
    }

    protected static FlowLayout spaced(FlowLayout layout, int gap) {
        layout.gap(gap);
        return layout;
    }

    protected static FlowLayout centered(FlowLayout layout) {
        layout.horizontalAlignment(HorizontalAlignment.CENTER);
        layout.verticalAlignment(VerticalAlignment.CENTER);
        return layout;
    }

    protected static LabelComponent wrappedLabel(String text, int maxWidth) {
        return UIComponents.label(Text.literal(text)).maxWidth(Math.max(120, maxWidth));
    }

    protected static LabelComponent wrappedLabel(Text text, int maxWidth) {
        return UIComponents.label(text).maxWidth(Math.max(120, maxWidth));
    }

    protected static void numericIntegerOnly(TextBoxComponent textBox) {
        textBox.onChanged().subscribe(value -> {
            StringBuilder filtered = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isDigit(character) || (character == '-' && index == 0)) filtered.append(character);
            }
            if (!filtered.toString().equals(value)) textBox.text(filtered.toString());
        });
    }

    protected static void numericOnly(TextBoxComponent textBox) {
        textBox.onChanged().subscribe(value -> {
            StringBuilder filtered = new StringBuilder();
            boolean decimalUsed = false;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isDigit(character)) {
                    filtered.append(character);
                } else if (character == '.' && !decimalUsed) {
                    filtered.append(character);
                    decimalUsed = true;
                }
            }
            if (!filtered.toString().equals(value)) textBox.text(filtered.toString());
        });
    }

    protected static FlowLayout panel(int width) {
        return layout(UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content()), 9, 6, 0xDD182332, 0xFF4A607C);
    }

    protected static FlowLayout panelFill() {
        return layout(UIContainers.verticalFlow(Sizing.fill(), Sizing.content()), 9, 6, 0xDD182332, 0xFF4A607C);
    }
}
