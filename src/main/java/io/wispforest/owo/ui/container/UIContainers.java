package io.wispforest.owo.ui.container;

import io.wispforest.owo.ui.core.Sizing;

public final class UIContainers {
    private UIContainers() {}
    public static FlowLayout verticalFlow(Sizing horizontal, Sizing vertical) { FlowLayout layout = new FlowLayout(false); layout.horizontalSizing(horizontal); layout.verticalSizing(vertical); return layout; }
    public static FlowLayout horizontalFlow(Sizing horizontal, Sizing vertical) { FlowLayout layout = new FlowLayout(true); layout.horizontalSizing(horizontal); layout.verticalSizing(vertical); return layout; }
    public static FlowLayout verticalScroll(Sizing horizontal, Sizing vertical, FlowLayout content) { FlowLayout layout = new FlowLayout(false, true); layout.horizontalSizing(horizontal); layout.verticalSizing(vertical); layout.child(content); return layout; }
}
