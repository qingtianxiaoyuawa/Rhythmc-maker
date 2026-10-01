package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.draft.DraftCodec;
import cn.frkovo.rhythmcv2.cv2.draft.RmcdStore;
import cn.frkovo.rhythmcv2.cv2.render.ChartMath;
import cn.frkovo.rhythmcv2.cv2.transport.CharterAudioBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话生命周期 + 工作区（§8）：专用平坦世界按 200m 槽位分区（与 Reborn 槽位数学同构）。
 * MVP：不粘贴 arena schematic（M1+），谱师在判定点原地编辑。
 * 热栏为固定工具布局，进出会话时备份/恢复玩家 9 格快捷栏。
 */
public final class SessionManager {

    private final Main plugin;
    private final Map<UUID, CharterSession> sessions = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedHotbar = new HashMap<>();
    private final Map<String, UUID> folderLocks = new HashMap<>();
    private World workspaceWorld;

    public SessionManager(Main plugin) {
        this.plugin = plugin;
    }

    public CharterSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public java.util.Collection<CharterSession> all() {
        return sessions.values();
    }

    public World workspace() {
        if (workspaceWorld == null) {
            String name = plugin.config().workspaceWorldName();
            workspaceWorld = Bukkit.getWorld(name);
            if (workspaceWorld == null) {
                workspaceWorld = Bukkit.createWorld(new WorldCreator(name)
                        .generatorSettings("{\"layers\":[{\"block\":\"minecraft:barrier\",\"height\":1}],\"biome\":\"minecraft:the_void\"}")
                        .generateStructures(false));
            }
        }
        return workspaceWorld;
    }

    /** /charter new：创建空草稿并进入会话（Mod-only 启动门，附录 D）。 */
    public CharterSession create(Player player, String songFolder) throws IOException {
        int slot = nextFreeSlot(player);
        CharterSession existing = get(player);
        if (existing != null) {
            player.sendMessage("§c你已开启会话，请先 /charter close");
            return null;
        }
        if (!plugin.bridge().isModReady(player)) {
            player.sendMessage("§c未检测到 CharterMod（rhythmc:charter_audio HELLO 未完成）。"
                    + "本插件不支持无 Mod 工况，请安装 mod 后重进。");
            return null;
        }
        if (slot < 0) {
            player.sendMessage("§c会话数已达上限");
            return null;
        }
        if (folderLocks.containsKey(songFolder)) {
            player.sendMessage("§c该谱面文件夹已被其他会话打开");
            return null;
        }
        Path dir = songDir(songFolder);
        Files.createDirectories(dir);
        Path draft = dir.resolve(songFolder + ".rmcd");
        EditorChart chart;
        EditorState state = new EditorState();
        if (Files.isRegularFile(draft)) {
            DraftCodec.DraftData data = RmcdStore.load(draft);
            chart = data.chart();
            state = data.state();
            player.sendMessage("§e已存在同名草稿，继续编辑（revision " + chart.revision + "）");
        } else {
            chart = new EditorChart(java.util.UUID.randomUUID().toString(), songFolder);
        }
        return openSession(player, chart, state, songFolder, draft, slot);
    }

    /** /charter open：导入 Reborn 谱面文件夹（无 .rmcd 时）或打开已有草稿。 */
    public CharterSession open(Player player, String songFolder) throws IOException {
        int slot = nextFreeSlot(player);
        CharterSession existing = get(player);
        if (existing != null) {
            player.sendMessage("§c你已开启会话，请先 /charter close");
            return null;
        }
        if (!plugin.bridge().isModReady(player)) {
            player.sendMessage("§c未检测到 CharterMod（rhythmc:charter_audio HELLO 未完成）。");
            return null;
        }
        if (slot < 0) {
            player.sendMessage("§c会话数已达上限");
            return null;
        }
        if (folderLocks.containsKey(songFolder)) {
            player.sendMessage("§c该谱面文件夹已被其他会话打开");
            return null;
        }
        Path dir = songDir(songFolder);
        Files.createDirectories(dir);
        Path draft = dir.resolve(songFolder + ".rmcd");
        EditorChart chart;
        EditorState state = new EditorState();
        if (Files.isRegularFile(draft)) {
            DraftCodec.DraftData data = RmcdStore.load(draft);
            chart = data.chart();
            state = data.state();
        } else {
            Path source = importRoot().resolve(songFolder);
            if (!Files.isRegularFile(source.resolve("manifest.yml"))) {
                player.sendMessage("§c找不到草稿或可导入谱面（" + source + "）。用 /charter new 从零开始。");
                return null;
            }
            chart = cn.frkovo.rhythmcv2.cv2.compile.RmccImporter.importSong(songFolder, source);
            player.sendMessage("§a已导入外部谱面（JSON number 精度风险已记录）");
        }
        return openSession(player, chart, state, songFolder, draft, slot);
    }

    /** 空闲槽位（0..maxSessions-1）；-1 = 满。 */
    private int nextFreeSlot(Player self) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (CharterSession s : sessions.values()) {
            if (!s.player().equals(self)) {
                used.add(s.slot());
            }
        }
        for (int i = 0; i < plugin.config().maxSessions(); i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private CharterSession openSession(Player player, EditorChart chart, EditorState state,
                                       String songFolder, Path draft, int slot) {
        CharterSession session = new CharterSession(plugin, player, chart, state, workspace(), slot, draft);
        sessions.put(player.getUniqueId(), session);
        folderLocks.put(songFolder, player.getUniqueId());
        ensurePlatform(session);
        saveHotbar(player);
        giveToolbox(player);
        Location spawn = session.spawnLocation();
        player.teleport(spawn);
        player.sendMessage("§a[Charter] 会话已开启（sessionId " + session.sessionId().substring(0, 8) + "…）"
                + " 谱面: " + songFolder + " / " + chart.activeLevel);
        session.begin();
        session.renderer().update();
        return session;
    }

    public void close(Player player) {
        CharterSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        try {
            session.save();
        } catch (IOException e) {
            player.sendMessage("§c自动保存失败: " + e.getMessage() + "（草稿仍在内存，重新 open 可继续）");
        }
        session.close();
        folderLocks.remove(session.chart().songFolder);
        restoreHotbar(player);
        player.sendMessage("§a[Charter] 会话已关闭并保存");
    }

    public void onQuit(Player player) {
        CharterSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            try {
                session.save();
            } catch (IOException e) {
                plugin.getLogger().warning("退出自动保存失败: " + e.getMessage());
            }
            session.close();
            folderLocks.remove(session.chart().songFolder);
        }
        plugin.bridge().onQuit(player);
    }

    /** 全服关闭（onDisable）。 */
    public void closeAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (sessions.containsKey(p.getUniqueId())) {
                close(p);
            }
        }
    }

    public Path songDir(String songFolder) {
        return plugin.getDataFolder().toPath().resolve("charts").resolve(songFolder);
    }

    /** 导入根目录：相对路径按插件数据目录解析。 */
    public Path importRoot() {
        Path root = Path.of(plugin.config().importRoot());
        return root.isAbsolute() ? root : plugin.getDataFolder().toPath().resolve(root);
    }

    /** MVP 无 arena 粘贴：在判定点附近铺一小块平台，避免谱师坠入虚空。 */
    private void ensurePlatform(CharterSession session) {
        World world = session.workspaceWorld();
        int cx = (int) Math.floor(cn.frkovo.rhythmcv2.cv2.render.ChartMath.gameCenterX(session.slot()));
        int cz = (int) Math.floor(cn.frkovo.rhythmcv2.cv2.render.ChartMath.judgeZ());
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 8; z <= cz + 2; z++) {
                if (world.getBlockAt(x, 98, z).isEmpty()) {
                    world.getBlockAt(x, 98, z).setType(Material.LIGHT_GRAY_CONCRETE);
                }
            }
        }
    }

    // ---- 热栏工具箱（§9.2 固定布局） ----

    private static final Material[] TOOLS = {
            Material.TNT,            // 0 音符放置器
            Material.BARRIER,        // 1 删除器
            Material.BLAZE_POWDER,   // 2 选择/移动器；右键打开特效轨道
            Material.CLOCK,          // 3 snap 旋转器
            Material.MUSIC_DISC_CAT, // 4 播放/暂停
            Material.ARROW,          // 5 seek 步进
            Material.REPEATER,       // 6 A-B 循环
            Material.PAPER,          // 7 保存
            Material.WRITABLE_BOOK,  // 8 主菜单
    };

    private void saveHotbar(Player player) {
        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            hotbar[i] = player.getInventory().getItem(i);
        }
        savedHotbar.put(player.getUniqueId(), hotbar);
    }

    private void restoreHotbar(Player player) {
        ItemStack[] hotbar = savedHotbar.remove(player.getUniqueId());
        if (hotbar == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, hotbar[i]);
        }
    }

    private void giveToolbox(Player player) {
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, new ItemStack(TOOLS[i]));
        }
        player.getInventory().setHeldItemSlot(0);
    }

    public boolean isToolSlot(int slot) {
        return slot >= 0 && slot < TOOLS.length;
    }
}
