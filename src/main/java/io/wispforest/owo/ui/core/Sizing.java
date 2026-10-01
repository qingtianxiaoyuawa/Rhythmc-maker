package io.wispforest.owo.ui.core;

public record Sizing(int value, Kind kind) {
    public enum Kind { FIXED, FILL, CONTENT, EXPAND }
    public static Sizing fixed(int value) { return new Sizing(Math.max(1, value), Kind.FIXED); }
    public static Sizing fill() { return new Sizing(0, Kind.FILL); }
    public static Sizing content() { return new Sizing(0, Kind.CONTENT); }
    public static Sizing expand() { return new Sizing(0, Kind.EXPAND); }
}
