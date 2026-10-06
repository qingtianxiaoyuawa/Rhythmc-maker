package cn.frkovo.rhythmcmaker.client.render;

import imgui.internal.ImGui;
import imgui.internal.ImGuiDockNode;

public final class ImGuiDockNodeAccess {
    private static final int DOCK_SPACE_FLAG = 1 << 10;
    private static final ImGuiDockNodeAccess INSTANCE = new ImGuiDockNodeAccess();

    private ImGuiDockNodeAccess() {
    }

    public static ImGuiDockNodeAccess getInstance() {
        return INSTANCE;
    }

    public boolean exists(int nodeId) {
        return exists(ImGui.dockBuilderGetNode(nodeId));
    }

    public void addDockSpaceNode(int nodeId) {
        ImGui.dockBuilderAddNode(nodeId, DOCK_SPACE_FLAG);
    }

    public boolean hasCentralDockspace(int nodeId) {
        ImGuiDockNode root = ImGui.dockBuilderGetNode(nodeId);
        if (!exists(root) || !root.isDockSpace()) return false;
        return exists(centralNode(nodeId));
    }

    public boolean exists(ImGuiDockNode node) {
        return node != null && node.isValidPtr();
    }

    public ImGuiDockNode centralNode(int dockspaceId) {
        return ImGui.dockBuilderGetCentralNode(dockspaceId);
    }
}
