package cn.frkovo.rhythmcv2.cv2;

import cn.frkovo.rhythmcv2.cv2.command.CommandRoot;
import cn.frkovo.rhythmcv2.cv2.gui.GuiMenu;
import cn.frkovo.rhythmcv2.cv2.hud.Hud;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import cn.frkovo.rhythmcv2.cv2.session.HotbarListener;
import cn.frkovo.rhythmcv2.cv2.session.SessionManager;
import cn.frkovo.rhythmcv2.cv2.transport.CharterAudioBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * RhythMC Charter V2 —— 插件主导、Mod 必需的游戏内制谱器（MVP）。
 * Mod-only 启动门：无 charter_audio 握手不进会话（附录 D）。
 */
public final class Main extends JavaPlugin {

    private Config configHolder;
    private SessionManager sessions;
    private CharterAudioBridge bridge;
    private Hud hud;
    private HotbarListener hotbar;
    private GuiMenu gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configHolder = new Config(getConfig());
        bridge = new CharterAudioBridge(this);
        bridge.register();
        sessions = new SessionManager(this);
        hud = new Hud(this);
        hotbar = new HotbarListener(this);
        gui = new GuiMenu(this);
        Bukkit.getPluginManager().registerEvents(hotbar, this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        PluginCommand command = getCommand("charter");
        if (command != null) {
            CommandRoot root = new CommandRoot(this);
            command.setExecutor(root);
            command.setTabCompleter(root);
        }

        // 主循环：transport tick + 渲染 + 自动保存
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (CharterSession session : sessions.all()) {
                session.transport().tick();
                session.renderer().updateIfNeeded();
                if (session.autosaveDue()) {
                    try {
                        session.save();
                        session.player().sendMessage("§7[自动保存] " + session.chart().songFolder);
                    } catch (Exception e) {
                        getLogger().warning("自动保存失败: " + e.getMessage());
                    }
                }
            }
        }, 1L, 1L);

        getLogger().info("RhythMC-Charter-V2 enabled (MVP). Channel: " + CharterAudioBridge.CHANNEL);
    }

    @Override
    public void onDisable() {
        sessions.closeAll();
        bridge.unregister();
    }

    @Override
    public void saveDefaultConfig() {
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
    }

    public Config config() {
        return configHolder;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public CharterAudioBridge bridge() {
        return bridge;
    }

    public Hud hud() {
        return hud;
    }

    public HotbarListener hotbar() {
        return hotbar;
    }

    public GuiMenu gui() {
        return gui;
    }
}
