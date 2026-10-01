package cn.frkovo.rhythmcmaker.worldedit;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.EventHandler;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class WorldEditIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("rhythmc_maker/worldedit");
    private static boolean registered;

    private WorldEditIntegration() {}

    public static void register() {
        if (registered) return;
        if (!FabricLoader.getInstance().isModLoaded("worldedit")) {
            LOGGER.info("WorldEdit is not installed; optional integration is disabled");
            return;
        }
        try {
            WorldEdit.getInstance().getEventBus().subscribe(EditSessionEvent.class,
            new EventHandler(EventHandler.Priority.VERY_EARLY) {
                @Override public void dispatch(Object event) {
                    EditSessionEvent editEvent = (EditSessionEvent) event;
                    if (editEvent.getStage() != EditSession.Stage.BEFORE_HISTORY) return;
                    Actor actor = editEvent.getActor();
                    if (actor == null || !actor.isPlayer() || actor.getUniqueId() == null) return;
                    UUID playerId = actor.getUniqueId();
                    Extent extent = editEvent.getExtent();
                    editEvent.setExtent(new SceneEditExtent(extent, playerId));
                }

                @Override public int hashCode() {
                    return System.identityHashCode(this);
                }

                @Override public boolean equals(Object other) {
                    return this == other;
                }
            });
            registered = true;
            LOGGER.info("WorldEdit track-note and scene editing integration enabled");
        } catch (LinkageError exception) {
            LOGGER.warn("WorldEdit is present but could not be linked; optional integration is disabled", exception);
        }
    }

    private static final class SceneEditExtent extends AbstractDelegateExtent {
        private final UUID playerId;

        private SceneEditExtent(Extent extent, UUID playerId) {
            super(extent);
            this.playerId = playerId;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
                throws com.sk89q.worldedit.WorldEditException {
            String blockId = block.getBlockType().id();
            RhythmcMaker.WorldEditWriteTarget target = RhythmcMaker.worldEditWriteTarget(
                    playerId, position.x(), position.y(), position.z(), blockId);
            if (target == RhythmcMaker.WorldEditWriteTarget.DENIED) return false;
            boolean changed = super.setBlock(position, block);
            if (changed && target == RhythmcMaker.WorldEditWriteTarget.TRACK_NOTE) {
                RhythmcMaker.syncWorldEditTrackNote(playerId, position.x(), position.y(), position.z(), blockId);
            }
            return changed;
        }
    }
}




