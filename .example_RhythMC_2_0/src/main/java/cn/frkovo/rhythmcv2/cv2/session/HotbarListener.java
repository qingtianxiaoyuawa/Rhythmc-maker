package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.render.PreviewRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 热栏工具箱交互（§9.2）：固定 9 槽；左/右键 + sneak 组合。
 * 放置 = 空间（准星 ghost）× 时间（游标 snap 量化）。
 */
public final class HotbarListener implements Listener {

    private static final int[] SNAPS = {1, 2, 3, 4, 6, 8, 12, 16, -1};

    private final Main plugin;
    /** GUI 输入等待：player → 输入回调（聊天流输入，MVP 无 anvil）。 */
    private final Map<UUID, Consumer<String>> chatInputs = new ConcurrentHashMap<>();

    public HotbarListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatInputs.remove(event.getPlayer().getUniqueId());
        plugin.sessions().onQuit(event.getPlayer());
    }

    /** 聊天输入（GUI 数值编辑用）。 */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> input = chatInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> input.accept(message));
    }

    public void awaitChatInput(Player player, String prompt, Consumer<String> callback) {
        player.sendMessage("§e[输入] " + prompt + " §7（聊天栏输入，cancel 取消）");
        chatInputs.put(player.getUniqueId(), s -> {
            if (s.equalsIgnoreCase("cancel")) {
                player.sendMessage("§7已取消");
            } else {
                callback.accept(s);
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        CharterSession session = plugin.sessions().get(player);
        if (session == null) {
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        if (!plugin.sessions().isToolSlot(slot)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean sneak = player.isSneaking();
        switch (slot) {
            case 0 -> toolPlace(session, left, sneak);
            case 1 -> toolDelete(session, left);
            case 2 -> {
                if (left) {
                    toolSelect(session, true);
                } else {
                    plugin.gui().openEffectTrack(session);
                }
            }
            case 3 -> toolSnap(session, left);
            case 4 -> toolTransport(session, left);
            case 5 -> toolSeek(session, left);
            case 6 -> toolLoop(session, left, sneak);
            case 7 -> toolSave(session);
            case 8 -> plugin.gui().openMain(session);
        }
        session.renderer().updateIfNeeded();
    }

    private void toolPlace(CharterSession session, boolean left, boolean sneak) {
        if (!left) {
            // 右键循环切换类型 TAP→LOOK→HOLD→DODGE
            String[] order = {"TAP", "LOOK", "HOLD", "DODGE"};
            String tool = session.state().tool;
            for (int i = 0; i < order.length; i++) {
                if (order[i].equals(tool)) {
                    session.state().tool = order[(i + 1) % order.length];
                    break;
                }
            }
            session.player().sendMessage("§7放置类型: §b" + session.state().tool);
            return;
        }
        double[] pos = session.placement().aimChartPos(session);
        if (pos == null) {
            session.player().sendMessage("§c准星未指向判定平面");
            return;
        }
        String tool = session.state().tool;
        Operation op;
        if (tool.equals("HOLD")) {
            op = sneak ? session.placement().holdFinish(session)
                    : session.placement().holdAppend(session, pos);
            if (op == null) {
                session.player().sendMessage("§7HOLD 组已结束");
                return;
            }
        } else {
            op = session.placement().placeNote(session, pos);
        }
        if (session.apply(op)) {
            session.state().lastPos = pos[0] + "," + pos[1] + "," + pos[2];
            session.renderer().updateGhost(null);
        }
    }

    private void toolDelete(CharterSession session, boolean left) {
        if (!left) {
            return;
        }
        var note = session.placement().targetedNote(session);
        if (note == null) {
            session.player().sendMessage("§c准星未指向音符");
            return;
        }
        String trackId = new EditorChartAccess(session).activeTrackId();
        session.apply(new Operation.NoteDelete(session.chart().activeLevel, trackId,
                cn.frkovo.rhythmcv2.cv2.ops.OperationApplier.noteSnapshot(note)));
    }

    private void toolSelect(CharterSession session, boolean left) {
        var note = session.placement().targetedNote(session);
        if (note == null) {
            session.state().selectedIds.clear();
            session.renderer().markDirty();
            session.player().sendMessage("§7已清空选择");
            return;
        }
        if (left) {
            session.state().selectedIds.add(note.id);
        } else {
            session.state().selectedIds.remove(note.id);
        }
        session.renderer().markDirty();
    }

    private void toolSnap(CharterSession session, boolean left) {
        int current = session.state().snap;
        int idx = 0;
        for (int i = 0; i < SNAPS.length; i++) {
            if (SNAPS[i] == current) {
                idx = i;
                break;
            }
        }
        idx = (idx + (left ? 1 : SNAPS.length - 1)) % SNAPS.length;
        session.state().snap = SNAPS[idx];
        session.player().sendMessage("§7snap: §b" + (SNAPS[idx] > 0 ? "1/" + SNAPS[idx] : "off"));
    }

    private void toolTransport(CharterSession session, boolean left) {
        if (left) {
            if (session.transport().isPlaying()) {
                session.transport().pause();
            } else {
                session.transport().play();
            }
        } else {
            session.transport().stop();
        }
    }

    private void toolSeek(CharterSession session, boolean left) {
        BeatFraction cur = session.state().cursorBeat;
        BeatFraction next = left ? cur.add(BeatFraction.of(4)) : cur.subtract(BeatFraction.of(4));
        if (next.isNegative()) {
            next = BeatFraction.ZERO;
        }
        session.transport().seek(next);
        session.renderer().markDirty();
    }

    private void toolLoop(CharterSession session, boolean left, boolean sneak) {
        var state = session.state();
        if (sneak) {
            state.clearLoop();
            session.transport().clearLoop();
            session.player().sendMessage("§7A-B 循环已清除");
            return;
        }
        BeatFraction cur = session.state().cursorBeat;
        if (left) {
            state.loopA = cur;
            session.player().sendMessage("§7循环 A = " + cur);
        } else {
            state.loopB = cur;
            session.player().sendMessage("§7循环 B = " + cur);
        }
        if (state.loopActive()) {
            session.transport().setLoop(state.loopA, state.loopB);
            session.player().sendMessage("§aA-B 循环生效 [" + state.loopA + ", " + state.loopB + "]");
        }
    }

    private void toolSave(CharterSession session) {
        try {
            session.save();
            session.player().sendMessage("§a已保存 .rmcd（revision " + session.chart().revision + "）");
        } catch (Exception e) {
            session.player().sendMessage("§c保存失败: " + e.getMessage());
        }
    }
}
