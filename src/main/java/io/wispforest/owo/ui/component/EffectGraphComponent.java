package io.wispforest.owo.ui.component;

import cn.frkovo.rhythmcmaker.client.ImGuiExtensionRuntime;
import com.google.gson.JsonObject;
import imgui.ImGui;
import imgui.extension.imnodes.ImNodes;

import java.util.List;

public final class EffectGraphComponent extends UiComponent {
    private final List<JsonObject> effects;

    public EffectGraphComponent(List<JsonObject> effects) { this.effects = effects; }

    @Override public void render() {
        if (!ImGuiExtensionRuntime.imNodesReady() || effects == null || effects.isEmpty()) return;
        ImNodes.beginNodeEditor();
        int count = Math.min(24, effects.size());
        for (int index = 0; index < count; index++) {
            JsonObject effect = effects.get(index);
            int nodeId = 1000 + index;
            ImNodes.setNodeGridSpacePos(nodeId, 24.0f + (index % 4) * 190.0f, 24.0f + (index / 4) * 86.0f);
            ImNodes.beginNode(nodeId);
            ImNodes.beginNodeTitleBar();
            ImGui.text(effectType(effect));
            ImNodes.endNodeTitleBar();
            ImNodes.beginStaticAttribute(nodeId * 10);
            ImGui.text("Beat " + beat(effect));
            ImNodes.endStaticAttribute();
            ImNodes.endNode();
            if (index > 0) ImNodes.link(2000 + index, (nodeId - 1) * 10, nodeId * 10);
        }
        ImNodes.endNodeEditor();
    }

    private static String effectType(JsonObject effect) {
        if (effect == null) return "UNKNOWN";
        if (effect.has("eventType")) return effect.get("eventType").getAsString();
        if (effect.has("type")) return effect.get("type").getAsString();
        return "UNKNOWN";
    }

    private static String beat(JsonObject effect) {
        try { return String.format(java.util.Locale.ROOT, "%.2f", effect.get("beat").getAsDouble()); }
        catch (RuntimeException ignored) { return "?"; }
    }
}
