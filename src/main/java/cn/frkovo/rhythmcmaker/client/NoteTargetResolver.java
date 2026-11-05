package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
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
        if (client.player == null || client.crosshairTarget == null || chart == null || chart.notes == null) return null;
        HitResult target = client.crosshairTarget;
        if (target instanceof EntityHitResult entityHit) {
            ChartManifest.Note note = resolveTaggedEntity(entityHit.getEntity(), chart.notes, chart.id);
            return note == null ? null : new NoteTarget(note, entityHit.getEntity(), target);
        }
        if (target instanceof BlockHitResult blockHit) {
            BlockPos hitPosition = blockHit.getBlockPos();
            ChartManifest.Note note = chart.notes.stream().filter(candidate -> candidate != null && !cn.frkovo.rhythmcmaker.chart.NoteEditRules.requiresDisplayEntity(candidate.x, candidate.y, candidate.z)).filter(candidate -> BlockPos.ofFloored(candidate.x, candidate.y, candidate.z).equals(hitPosition)).findFirst().orElse(null);
            return note == null ? null : new NoteTarget(note, null, target);
        }
        if (target.getType() != HitResult.Type.MISS) return null;
        Vec3d origin = client.gameRenderer.getCamera().getCameraPos();
        Vec3d direction = client.player.getRotationVec(1.0f);
        double maximumDistance = client.player.getBlockInteractionRange();
        double maximumDistanceSquared = 0.9 * 0.9;
        RayCandidate nearest = null;
        java.util.Set<String> displayedNoteIds = new java.util.HashSet<>();
        if (client.world != null) {
            for (DisplayEntity.BlockDisplayEntity display : client.world.getEntitiesByType(EntityType.BLOCK_DISPLAY,
                    new Box(origin, origin).expand(maximumDistance + 1.0),
                    entity -> entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(NOTE_TAG_PREFIX + chart.id + ":")))) {
                ChartManifest.Note note = resolveTaggedEntity(display, chart.notes, chart.id);
                if (note == null) continue;
                displayedNoteIds.add(note.id);
                Vec3d displayCenter = display.getVisibilityBoundingBox().getCenter();
                RayCandidate candidate = rayCandidate(note, display, displayCenter, origin, direction,
                        maximumDistance, maximumDistanceSquared);
                if (isCloser(candidate, nearest)) nearest = candidate;
            }
        }
        return nearest == null ? null : new NoteTarget(nearest.note(), nearest.entity(), target);
    }

    private static RayCandidate rayCandidate(ChartManifest.Note note, Entity entity, Vec3d center, Vec3d origin, Vec3d direction,
                                             double maximumDistance, double maximumDistanceSquared) {
        Vec3d hit = entity.getBoundingBox().raycast(origin, origin.add(direction.multiply(maximumDistance))).orElse(null);
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
        if (!(entity instanceof DisplayEntity.BlockDisplayEntity)) return null;
        String prefix = NOTE_TAG_PREFIX + chartId + ":";
        return entity.getCommandTags().stream()
                .filter(tag -> tag.startsWith(prefix))
                .map(tag -> tag.substring(prefix.length()))
                .map(noteId -> notes.stream().filter(note -> note != null && noteId.equals(note.id)).findFirst().orElse(null))
                .findFirst().orElse(null);
    }

}
