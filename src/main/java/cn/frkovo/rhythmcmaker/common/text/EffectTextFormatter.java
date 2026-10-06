package cn.frkovo.rhythmcmaker.common.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class EffectTextFormatter {
    private static final EffectTextFormatter INSTANCE = new EffectTextFormatter();

    private EffectTextFormatter() {
    }

    public static EffectTextFormatter getInstance() {
        return INSTANCE;
    }

    public Text text(String value, int fallbackColor) {
        if (value == null || value.isEmpty()) return Text.empty();
        MutableText result = Text.empty();
        Style style = Style.EMPTY.withColor(fallbackColor);
        StringBuilder segment = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character != '&' && character != '\u00A7') || index + 1 >= value.length()) {
                segment.append(character);
                continue;
            }
            Formatting formatting = Formatting.byCode(value.charAt(index + 1));
            if (formatting == null) {
                segment.append(character);
                continue;
            }
            if (!segment.isEmpty()) {
                result.append(Text.literal(segment.toString()).setStyle(style));
                segment.setLength(0);
            }
            if (formatting == Formatting.RESET) style = Style.EMPTY.withColor(fallbackColor);
            else if (formatting.isColor()) style = Style.EMPTY.withColor(formatting);
            else style = style.withFormatting(formatting);
            index++;
        }
        if (!segment.isEmpty()) result.append(Text.literal(segment.toString()).setStyle(style));
        return result;
    }

    public String normalize(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\u00A7' && index + 1 < value.length()
                    && Formatting.byCode(value.charAt(index + 1)) != null) {
                result.append('&');
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    public List<PreviewSegment> preview(String value, int fallbackColor) {
        List<PreviewSegment> segments = new ArrayList<>();
        if (value == null || value.isEmpty()) return segments;
        Style style = Style.EMPTY.withColor(fallbackColor);
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character != '&' && character != '\u00A7') || index + 1 >= value.length()) {
                text.append(character);
                continue;
            }
            Formatting formatting = Formatting.byCode(value.charAt(index + 1));
            if (formatting == null) {
                text.append(character);
                continue;
            }
            if (!text.isEmpty()) {
                segments.add(new PreviewSegment(text.toString(), style.getColor() == null ? fallbackColor : style.getColor().getRgb(),
                        style.isBold(), style.isItalic(), style.isObfuscated()));
                text.setLength(0);
            }
            if (formatting == Formatting.RESET) style = Style.EMPTY.withColor(fallbackColor);
            else if (formatting.isColor()) style = Style.EMPTY.withColor(formatting);
            else style = style.withFormatting(formatting);
            index++;
        }
        if (!text.isEmpty()) {
            segments.add(new PreviewSegment(text.toString(), style.getColor() == null ? fallbackColor : style.getColor().getRgb(),
                    style.isBold(), style.isItalic(), style.isObfuscated()));
        }
        return List.copyOf(segments);
    }

    public record PreviewSegment(String text, int color, boolean bold, boolean italic, boolean obfuscated) {
    }
}