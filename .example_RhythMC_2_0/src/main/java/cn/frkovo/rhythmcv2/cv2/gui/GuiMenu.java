package cn.frkovo.rhythmcv2.cv2.gui;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import cn.frkovo.rhythmcv2.cv2.session.HotbarListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主菜单（§12 MVP）：manifest 基本信息、BPM 事件表、保存/编译/发布入口。
 * 数值编辑走聊天输入（HotbarListener.awaitChatInput），完整 GUI 编辑器留 M4。
 */
public final class GuiMenu implements Listener {

    private final Main plugin;
    private final Map<UUID, String> openMenus = new ConcurrentHashMap<>();

    public GuiMenu(Main plugin) {
        this.plugin = plugin;
    }

    public void openMain(CharterSession session) {
        Player p = session.player();
        Inventory inv = Bukkit.createInventory(null, 27, "§bCharter V2 · " + session.chart().songFolder);
        inv.setItem(4, icon(Material.NAME_TAG, "§e" + session.chart().manifestName,
                "§7composer: " + session.chart().composer,
                "§7难度: " + session.chart().activeLevel,
                "§7revision: " + session.chart().revision,
                "§7点击改名（聊天输入）"));
        int slot = 9;
        for (BeatClock.BpmEvent e : session.chart().bpms) {
            if (slot > 16) {
                break;
            }
            inv.setItem(slot++, icon(Material.REPEATER, "§bBPM " + e.bpm().toPlainString(),
                    "§7beat " + e.beat(), "§7shift+左键删除"));
        }
        inv.setItem(21, icon(Material.NOTE_BLOCK, "§a添加 BPM（游标处）", "§7点击后聊天输入 BPM 值"));
        inv.setItem(23, icon(Material.PAPER, "§a保存 .rmcd"));
        inv.setItem(24, icon(Material.FURNACE, "§6编译"));
        inv.setItem(25, icon(Material.EMERALD, "§2发布（published/）"));
        openMenus.put(p.getUniqueId(), "main");
        p.openInventory(inv);
    }

    /** 独立特效轨道框架；特效事件只使用 EditorLevel.effects。 */
    public void openEffectTrack(CharterSession session) {
        Player p = session.player();
        Inventory inv = Bukkit.createInventory(null, 54, "§5特效轨道 · " + session.chart().songFolder);
        inv.setItem(4, icon(Material.BLAZE_POWDER, "§d特效轨道",
                "§7确认音符位置的俯瞰图不在此编辑",
                "§7普通音符仍由制谱器轨道管理",
                "§8当前事件: " + session.chart().active().effects.size()));
        inv.setItem(45, icon(Material.ARROW, "§7返回主菜单"));
        inv.setItem(49, icon(Material.NETHER_STAR, "§d添加特效轨道",
                "§7创建独立的效果编排轨道"));
        inv.setItem(53, icon(Material.LIME_DYE, "§a添加特效事件",
                "§7以当前 Beat 创建 TITLE 占位事件"));
        int row = 18;
        int index = 1;
        for (JsonObject effect : session.chart().active().effects) {
            if (row >= 45) break;
            String type = effect.has("type") ? effect.get("type").getAsString() : "EFFECT";
            String beat = effect.has("beat") ? effect.get("beat").getAsString() : "—";
            inv.setItem(row++, icon(Material.PURPLE_CONCRETE, "§d" + type,
                    "§7Beat " + beat, "§8特效轨道事件 #" + index++,
                    "§7左键选择 · Shift+左键删除"));
        }
        openMenus.put(p.getUniqueId(), "effects");
        p.openInventory(inv);
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String menu = openMenus.get(player.getUniqueId());
        if (menu == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        CharterSession session = plugin.sessions().get(player);
        if (session == null) {
            player.closeInventory();
            return;
        }
        HotbarListener hotbar = plugin.hotbar();
        int slot = event.getSlot();
        if ("effects".equals(menu)) {
            handleEffectTrackClick(event, player, session);
            return;
        }
        switch (slot) {
            case 4 -> {
                player.closeInventory();
                hotbar.awaitChatInput(player, "歌曲名", value -> {
                    session.chart().manifestName = value;
                    player.sendMessage("§aname = " + value);
                });
            }
            case 21 -> {
                player.closeInventory();
                hotbar.awaitChatInput(player, "BPM 值（游标 beat = " + session.state().cursorBeat + "）", value -> {
                    try {
                        new java.math.BigDecimal(value);
                    } catch (RuntimeException e) {
                        player.sendMessage("§cBPM 格式错误");
                        return;
                    }
                    session.apply(new Operation.BpmAdd(session.state().cursorBeat.toString(), value));
                });
            }
            case 23 -> {
                player.closeInventory();
                try {
                    session.save();
                    player.sendMessage("§a已保存");
                } catch (Exception e) {
                    player.sendMessage("§c保存失败: " + e.getMessage());
                }
            }
            case 24 -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "charter compile");
            }
            case 25 -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "charter publish");
            }
            default -> {
                if (slot >= 9 && slot <= 16 && event.isShiftClick() && event.isLeftClick()) {
                    int idx = slot - 9;
                    var bpms = session.chart().bpms;
                    if (idx < bpms.size()) {
                        BeatClock.BpmEvent e = bpms.get(idx);
                        session.apply(new Operation.BpmRemove(e.beat().toString(), e.bpm().toPlainString()));
                        openMain(session);
                    }
                }
            }
        }
    }

    private void handleEffectTrackClick(InventoryClickEvent event, Player player, CharterSession session) {
        int slot = event.getSlot();
        if (slot == 45) { openMain(session); return; }
        if (slot == 49) {
            player.sendMessage("§d[特效轨道] 已预留独立轨道创建入口；当前使用全局特效轨道框架");
            return;
        }
        if (slot == 53) {
            JsonObject effect = new JsonObject();
            effect.addProperty("type", "TITLE");
            effect.addProperty("beat", session.state().cursorBeat.toString());
            effect.addProperty("track", "effects-main");
            effect.addProperty("text", "Ready");
            session.chart().active().effects.add(effect);
            player.sendMessage("§a已在 Beat " + session.state().cursorBeat + " 添加特效事件占位");
            openEffectTrack(session);
            return;
        }
        if (slot >= 18 && slot < 45 && event.isShiftClick() && event.isLeftClick()) {
            int index = slot - 18;
            var effects = session.chart().active().effects;
            if (index < effects.size()) {
                effects.remove(index);
                player.sendMessage("§7已删除特效事件");
                openEffectTrack(session);
            }
        }
    }
}
