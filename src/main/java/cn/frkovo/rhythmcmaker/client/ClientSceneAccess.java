package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.List;

final class ClientSceneAccess {
    private ClientSceneAccess() {}
    private static MinecraftServer server() throws IOException {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) throw new IOException("当前版本仅支持单人制谱器世界");
        return server;
    }
    static List<SceneEditStorage.Scene> list() throws IOException { return RhythmcMaker.sceneEditors(server()); }
    static SceneEditStorage.Scene create() throws IOException { return RhythmcMaker.createSceneEditor(server()); }
    static void save(SceneEditStorage.Scene scene) throws IOException { RhythmcMaker.saveSceneEditor(server(), scene); }
    static void reset(int index) { RhythmcMakerClient.sendChartCommand("rhythmc_scene_reset " + index); }
    static void teleport(int index) { RhythmcMakerClient.sendChartCommand("rhythmc_scene_editor " + index); }
    static void status(String message) { ClientChartAccess.status(message); }
}
