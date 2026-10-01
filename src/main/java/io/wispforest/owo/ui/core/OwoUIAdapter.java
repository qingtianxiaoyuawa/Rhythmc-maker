package io.wispforest.owo.ui.core;

import io.wispforest.owo.ui.container.FlowLayout;
import java.util.function.Supplier;

public final class OwoUIAdapter<T extends FlowLayout> {
    public T rootComponent;
    private final Supplier<T> rootFactory;
    private OwoUIAdapter(Supplier<T> rootFactory) { this.rootFactory = rootFactory; }
    public static <T extends FlowLayout> OwoUIAdapter<T> create(Object screen, Supplier<T> rootFactory) { return new OwoUIAdapter<>(rootFactory); }
    public T createRoot() { return rootComponent = rootFactory.get(); }
}
