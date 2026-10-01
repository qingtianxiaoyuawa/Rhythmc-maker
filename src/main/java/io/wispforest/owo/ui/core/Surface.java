package io.wispforest.owo.ui.core;

public final class Surface {
    public static final Surface VANILLA_TRANSLUCENT = new Surface(0xC0101824, 0xFF4A607C);

    private final int background;
    private final int outline;

    private Surface(int background, int outline) {
        this.background = background;
        this.outline = outline;
    }

    public static Surface flat(int color) {
        return new Surface(color, 0x00000000);
    }

    public Surface and(Surface other) {
        if (other == null) return this;
        return new Surface(background, other.outline != 0 ? other.outline : outline);
    }

    public static Surface outline(int color) {
        return new Surface(0x00000000, color);
    }

    public int background() { return background; }
    public int outline() { return outline; }
}