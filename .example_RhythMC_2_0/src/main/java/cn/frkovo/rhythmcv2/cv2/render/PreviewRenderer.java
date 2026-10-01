package cn.frkovo.rhythmcv2.cv2.render;

import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 双通道渲染（§9.4 MVP）：编辑态按当前游标/播放 beat 以运行时同公式渲染预览姿态。
 * ItemDisplay 对象池；窗口 = beat ± renderWindowBeats。
 * MVP 简化：HOLD 链式光带、时间衰减着色、簇收缩不做（不影响空间语义）。
 */
public final class PreviewRenderer {

    public static Material materialOf(int noteType) {
        return switch (noteType) {
            case EditorNote.LOOK -> Material.OBSERVER;
            case EditorNote.HOLD -> Material.DIAMOND_BLOCK;
            case EditorNote.DODGE -> Material.REDSTONE_BLOCK;
            default -> Material.TNT;
        };
    }

    private final CharterSession session;
    private final List<ItemDisplay> live = new ArrayList<>();
    private ItemDisplay ghost;
    private boolean dirty = true;

    public PreviewRenderer(CharterSession session) {
        this.session = session;
    }

    public void markDirty() {
        dirty = true;
    }

    public void updateIfNeeded() {
        if (dirty || session.transport().isPlaying()) {
            update();
            dirty = false;
        }
    }

    public void update() {
        World world = session.workspaceWorld();
        double beat = session.transport().isPlaying()
                ? session.transport().currentBeat().toDouble()
                : session.state().cursorBeat.toDouble();
        double speed = session.plugin().config().playerSpeed();
        EditorLevel level = session.chart().active();

        // 期望渲染集（窗口 = 游标/播放头 ± N 拍内的音符，按 dis 可见域过滤）
        record Item(EditorNote note, double[] world, Quaternionf rot, Vector3f scale) {
        }
        List<Item> desired = new ArrayList<>();
        double window = session.plugin().config().renderWindowBeats();
        for (EditorTrack track : level.tracks.values()) {
            ChartMath.TrackState ts = ChartMath.evaluateState(track, beat, speed);
            for (EditorNote n : track.notes.values()) {
                double noteBeat = n.beat.toDouble();
                if (Math.abs(noteBeat - beat) > window) {
                    continue;
                }
                double noteDis = ChartMath.noteDistance(track, n.beat, speed);
                double[] w = ChartMath.composeWorld(ts, noteDis, n.pos[0], n.pos[1], n.pos[2]);
                double dis = w[3];
                if (dis < ChartMath.zNear(n.noteType) || dis >= ChartMath.zFar(n.noteType)) {
                    continue;
                }
                Quaternionf trackQuat = new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(ts.xRot()),
                        (float) Math.toRadians(ts.yRot()),
                        (float) Math.toRadians(ts.zRot()));
                Quaternionf noteQuat = new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(n.rotation[0].doubleValue()),
                        (float) Math.toRadians(n.rotation[1].doubleValue()),
                        (float) Math.toRadians(n.rotation[2].doubleValue()));
                float mask = (float) ChartMath.visualScaleMask(n.noteType, dis);
                Vector3f scale = new Vector3f(
                        n.scale[0].floatValue() * mask,
                        n.scale[1].floatValue() * mask,
                        n.scale[2].floatValue() * mask);
                desired.add(new Item(n, w, trackQuat.mul(noteQuat), scale));
            }
        }

        // 复用/补充/回收
        while (live.size() < desired.size()) {
            ItemDisplay d = world.spawn(session.spawnLocation(), ItemDisplay.class, d2 -> {
                d2.setPersistent(false);
                d2.setVisibleByDefault(false);
                d2.setTeleportDuration(1);
            });
            live.add(d);
        }
        for (int i = 0; i < live.size(); i++) {
            ItemDisplay d = live.get(i);
            if (i < desired.size()) {
                Item item = desired.get(i);
                Location loc = new Location(world,
                        ChartMath.gameCenterX(0) + item.world()[0],
                        ChartMath.originY() + item.world()[1],
                        ChartMath.judgeZ() - item.world()[2]);
                d.setItemStack(new ItemStack(materialOf(item.note().noteType)));
                d.setTransformation(new Transformation(
                        new Vector3f(), item.rot(), item.scale(), new Quaternionf()));
                d.teleport(loc);
                d.setGlowing(session.state().selectedIds.contains(item.note().id));
                d.setBrightness(new Display.Brightness(15, 15));
            } else {
                d.setItemStack(null);
                d.teleport(session.spawnLocation());
            }
        }
    }

    /** ghost：准星判定平面交点 × 当前游标 → 放置预览（半透明发光框）。 */
    public void updateGhost(double[] chartPos) {
        removeGhost();
        if (chartPos == null) {
            return;
        }
        World world = session.workspaceWorld();
        int type = noteTypeOf(session.state().tool);
        ghost = world.spawn(session.spawnLocation(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBillboard(Display.Billboard.FIXED);
        });
        ghost.setItemStack(new ItemStack(materialOf(type)));
        float s = type == EditorNote.DODGE ? 1.5f : 1.0f;
        ghost.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(s, s, s), new Quaternionf()));
        ghost.teleport(new Location(world,
                ChartMath.gameCenterX(0) + chartPos[0],
                ChartMath.originY() + chartPos[1],
                ChartMath.judgeZ() - chartPos[2]));
        world.spawnParticle(Particle.DUST,
                new Location(world, ChartMath.gameCenterX(0) + chartPos[0],
                        ChartMath.originY() + chartPos[1], ChartMath.judgeZ()),
                4, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(Color.AQUA, 1.0f));
    }

    public void removeGhost() {
        if (ghost != null) {
            ghost.remove();
            ghost = null;
        }
    }

    public static int noteTypeOf(String tool) {
        return switch (tool) {
            case "LOOK" -> EditorNote.LOOK;
            case "HOLD" -> EditorNote.HOLD;
            case "DODGE" -> EditorNote.DODGE;
            default -> EditorNote.TAP;
        };
    }

    public void destroy() {
        removeGhost();
        for (ItemDisplay d : live) {
            d.remove();
        }
        live.clear();
    }
}
