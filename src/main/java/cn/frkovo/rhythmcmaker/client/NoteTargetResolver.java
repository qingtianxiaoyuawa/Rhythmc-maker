package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.NoteEditRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

import java.util.List;

final class NoteTargetResolver {
    private static final String NOTE_TAG_PREFIX = "rhythmc_note:";

    private NoteTargetResolver() {
    }

    static NoteTarget resolve(MinecraftClient client, ChartManifest chart) {
        ChartManifest resolvedChart = ClientChartAccess.refreshActiveChartNow();
        if (client.player == null || resolvedChart == null || resolvedChart.notes == null) return null;
        chart = resolvedChart;
        HitResult target = client.crosshairTarget;
        if (target instanceof EntityHitResult entityHit) {
            ChartManifest.Note note = resolveTaggedEntity(entityHit.getEntity(), chart.notes, chart.id);
            if (note != null) return new NoteTarget(chart, note, entityHit.getEntity(), target);
        }
        NoteTarget displayTarget = resolveDisplayTarget(client, chart, target);
        if (displayTarget != null) return displayTarget;
        if (target instanceof BlockHitResult blockHit) {
            BlockPos hitPosition = blockHit.getBlockPos();
            ChartManifest.Note note = chart.notes.stream()
                    .filter(candidate -> candidate != null
                            && !cn.frkovo.rhythmcmaker.chart.NoteEditRules.requiresDisplayEntity(candidate.x, candidate.y, candidate.z))
                    .filter(candidate -> BlockPos.ofFloored(candidate.x, candidate.y, candidate.z).equals(hitPosition))
                    .findFirst().orElse(null);
            return note == null ? null : new NoteTarget(chart, note, null, target);
        }
        return null;
    }

    private static NoteTarget resolveDisplayTarget(MinecraftClient client, ChartManifest chart, HitResult target) {
        if (client.world == null || client.player == null) return null;
        Vec3d origin = client.gameRenderer.getCamera().getCameraPos();
        Vec3d direction = client.player.getRotationVec(1.0f);
        double maximumDistance = client.player.getBlockInteractionRange();
        double maximumDistanceSquared = 0.9 * 0.9;
        RayCandidate nearest = null;
        for (DisplayEntity display : client.world.getEntitiesByClass(DisplayEntity.class,
                new Box(origin, origin).expand(maximumDistance + 1.0),
                entity -> hasNoteBinding(entity, chart.id))) {
            ChartManifest.Note note = resolveTaggedEntity(display, chart.notes, chart.id);
            if (note == null) continue;
            RayCandidate candidate = rayCandidate(note, display, origin, direction, maximumDistance,
                    maximumDistanceSquared);
            if (isCloser(candidate, nearest)) nearest = candidate;
        }
        return nearest == null ? null : new NoteTarget(chart, nearest.note(), nearest.entity(), target);
    }

    private static RayCandidate rayCandidate(ChartManifest.Note note, DisplayEntity entity, Vec3d origin, Vec3d direction,
                                             double maximumDistance, double maximumDistanceSquared) {
        Vec3d hit = entity.getVisibilityBoundingBox()
                .raycast(origin, origin.add(direction.multiply(maximumDistance))).orElse(null);
        if (hit == null) return null;
        double alongRay = hit.subtract(origin).dotProduct(direction);
        if (alongRay < 0.0 || alongRay > maximumDistance) return null;
        double distanceSquared = hit.squaredDistanceTo(origin.add(direction.multiply(alongRay)));
        return distanceSquared <= maximumDistanceSquared ? new RayCandidate(note, entity, distanceSquared, alongRay) : null;
    }

    private static boolean isCloser(RayCandidate candidate, RayCandidate current) {
        if (candidate == null) return false;
        if (current == null) return true;
        if (Math.abs(candidate.distanceSquared() - current.distanceSquared()) > 1.0e-6) {
            return candidate.distanceSquared() < current.distanceSquared();
        }
        return candidate.alongRay() < current.alongRay();
    }

    private record RayCandidate(ChartManifest.Note note, Entity entity, double distanceSquared, double alongRay) {}

    private static ChartManifest.Note resolveTaggedEntity(Entity entity, List<ChartManifest.Note> notes, String chartId) {
        if (!(entity instanceof DisplayEntity)) return null;
        String noteId = entity.getCommandTags().stream()
                .filter(tag -> tag.startsWith(NOTE_TAG_PREFIX + chartId + ":"))
                .map(tag -> tag.substring((NOTE_TAG_PREFIX + chartId + ":").length()))
                .findFirst().orElse(null);
        if (noteId == null && entity.getCustomName() != null) {
            String reference = entity.getCustomName().getString();
            String prefix = "rhythmc_note_ref:" + chartId + ":";
            if (reference.startsWith(prefix)) noteId = reference.substring(prefix.length());
        }
        if (noteId == null || noteId.isBlank()) return null;
        String resolvedNoteId = noteId;
        return notes.stream().filter(note -> note != null && resolvedNoteId.equals(note.id)).findFirst().orElse(null);
    }

    private static boolean hasNoteBinding(Entity entity, String chartId) {
        String prefix = NOTE_TAG_PREFIX + chartId + ":";
        if (entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(prefix))) return true;
        return entity.getCustomName() != null
                && entity.getCustomName().getString().startsWith("rhythmc_note_ref:" + chartId + ":");
    }

}
