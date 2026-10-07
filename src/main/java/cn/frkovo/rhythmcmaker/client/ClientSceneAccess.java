package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.util.List;

public final class ClientSceneAccess {
    private ClientSceneAccess() {}
    private static MinecraftServer server() throws IOException {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) throw new IOException("当前版本仅支持单人制谱器世界");
        return server;
    }
    static List<SceneEditStorage.Scene> list() throws IOException { return RhythmcMaker.sceneEditors(server(), chartId()); }
    public static List<String> savedSceneNames() throws IOException {
        return list().stream().filter(scene -> scene.saved && scene.name != null && !scene.name.isBlank())
                .map(scene -> scene.name).distinct().toList();
    }
    static SceneEditStorage.Scene create() throws IOException {
        var player = MinecraftClient.getInstance().player;
        String icon = player == null || player.getMainHandStack().isEmpty() ? null : Registries.ITEM.getId(player.getMainHandStack().getItem()).toString();
        return RhythmcMaker.createSceneEditor(server(), chartId(), icon, player == null ? null : player.getUuid());
    }
    static void save(SceneEditStorage.Scene scene) throws IOException { RhythmcMaker.saveSceneEditor(server(), chartId(), scene); }
    static void reset(int index) { RhythmcMakerClient.sendChartCommand("rhythmc_scene_reset " + index); }
    static void teleport(int index) { RhythmcMakerClient.sendChartCommand("rhythmc_scene_editor " + index); }
    static void delete(int index) { RhythmcMakerClient.sendChartCommand("rhythmc_scene_delete " + index); }
    static void status(String message) { ClientChartAccess.status(message); }
    static boolean operationBusy() {
        var player = MinecraftClient.getInstance().player;
        return player != null && RhythmcMaker.isSceneOperationBusy(player.getUuid());
    }
    static String operationStatus() {
        var player = MinecraftClient.getInstance().player;
        return player == null ? null : RhythmcMaker.sceneOperationStatus(player.getUuid());
    }
    static String consumeOperationNotice() {
        var player = MinecraftClient.getInstance().player;
        return player == null ? null : RhythmcMaker.consumeSceneOperationNotice(player.getUuid());
    }
    private static String chartId() throws IOException {
        var chart = ClientChartAccess.resolveActiveChart();
        if (chart == null || chart.id == null || chart.id.isBlank()) throw new IOException("请先进入谱面维度");
        return chart.id;
    }
}

