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
        return OwoUIAdapter.create(this, () -> UIContainers.verticalFlow(Sizing.fill(), Sizing.fill()));
    }

    protected int pageWidth() {
        return Math.max(320, Math.min(this.width - 24, 980));
    }

    protected int pageHeight() {
        return Math.max(240, Math.min(this.height - 24, 720));
    }

    protected FlowLayout scrollPage(FlowLayout page) {
        int viewportWidth = Math.max(320, Math.min(pageWidth(), this.width - 24));
        int viewportHeight = Math.max(180, Math.min(pageHeight(), this.height - 24));
        return UIContainers.verticalScroll(Sizing.fixed(viewportWidth), Sizing.fixed(viewportHeight), page);
    }

    protected static FlowLayout layout(FlowLayout layout, int padding, int gap, int background, int outline) {
        layout.padding(Insets.of(Math.max(7, padding - 2)));
        layout.gap(Math.max(5, gap - 1));
        layout.surface(Surface.flat(background).and(Surface.outline(outline)));
        return layout;
    }

    protected static FlowLayout menuHeader(String title, String subtitle, int width) {
        FlowLayout header = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
        header.gap(2);
        header.child(UIComponents.label(Text.literal(title)).shadow(true));
        if (subtitle != null && !subtitle.isBlank()) {
            header.child(UIComponents.label(Text.literal(subtitle)));
        }
        return header;
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
        return layout(UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content()), 8, 6, 0xE0182635, 0xFF3B526D);
    }

    protected static FlowLayout panelFill() {
        return layout(UIContainers.verticalFlow(Sizing.fill(), Sizing.content()), 8, 6, 0xE0182635, 0xFF3B526D);
    }
}
