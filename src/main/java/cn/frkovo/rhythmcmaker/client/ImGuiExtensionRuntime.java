package cn.frkovo.rhythmcmaker.client;

import imgui.ImGui;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.implot.ImPlot;
import imgui.internal.ImGuiContext;

public final class ImGuiExtensionRuntime {
    private static boolean initialized;
    private static boolean imPlotReady;
    private static boolean imNodesReady;
    private static imgui.extension.implot.ImPlotContext imPlotContext;

    private ImGuiExtensionRuntime() {}

    static void initialize(ImGuiContext context) {
        if (initialized) return;
        try {
            imPlotContext = ImPlot.createContext();
            ImPlot.styleColorsDark();
            imPlotReady = true;
        } catch (RuntimeException | LinkageError exception) {
            if (imPlotContext != null) ImPlot.destroyContext(imPlotContext);
        }
        try {
            ImNodes.createContext();
            ImNodes.styleColorsDark();
            imNodesReady = true;
        } catch (RuntimeException | LinkageError ignored) { }
        initialized = true;
    }

    public static boolean imPlotReady() { return imPlotReady; }
    public static boolean imNodesReady() { return imNodesReady; }

    static void beginDockspace() {
        if (initialized && ImGui.getIO().hasConfigFlags(imgui.flag.ImGuiConfigFlags.DockingEnable)) {
            ImGui.dockSpaceOverViewport();
        }
    }
}