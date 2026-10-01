package io.wispforest.owo.ui.core;

public record Insets(int all) { public static Insets of(int value) { return new Insets(Math.max(0, value)); } }
