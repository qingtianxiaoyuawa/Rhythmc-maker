package cn.frkovo.rhythmcmaker;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartStorage;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates;
import cn.frkovo.rhythmcmaker.chart.TrackPlayback;
import cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import cn.frkovo.rhythmcmaker.worldedit.WorldEditIntegration;
import cn.frkovo.rhythmcmaker.chart.ChartStorage.ImportedScene;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.rule.GameRules;
import net.casual.arcade.dimensions.level.CustomLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RhythmcMaker implements ModInitializer {
    public static final String MOD_ID = "rhythmc_maker";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final BlockPos CHARTER_SPAWN = new BlockPos(0, 65, 0);
    private static final BlockPos EDITOR_SPAWN = new BlockPos(0, 66, 0);
    public static final RegistryKey<World> CHARTER_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "charter"));
    public static final Item START_CHART_ITEM = registerMenuItem("start_chart", "开始写谱（右键打开）");
    public static final Item AWA_ITEM = registerMenuItem("awa", "awa~（右键打开）");
    public static final Item SETTINGS_ITEM = registerMenuItem("settings", "设置（右键打开）");
    public static final Item MORE_SETTINGS_ITEM = registerMenuItem("more_settings", "更多选项（右键打开）");
    public static final Item SCENE_EDIT_ITEM = registerMenuItem("scene_edit", "场景编辑（右键打开）");
    public static final Item EFFECT_TRACK_ITEM = registerMenuItem("effect_track", "特效编辑器（右键打开）");
    public static final Item SAVE_SCENE_ITEM = registerMenuItem("save_scene", "保存场景（右键保存并返回）");
    public static final Item QUICK_FUNCTIONS_ITEM = registerMenuItem("quick_functions", "快捷功能（右键打开）");
    public static final Item CHART_SETTINGS_ITEM = registerMenuItem("chart_settings", "谱面设置");
    public static final Item MORE_OPTIONS_RETURN_ITEM = registerMenuItem("more_options_return", "返回（右键打开）");
    public static final Item CHART_INFO_ITEM = registerMenuItem("chart_info", "谱面信息（右键打开）");
    public static final Item PLAY_ITEM = registerMenuItem("play", "播放");
    public static final Item INCREASE_BEATS_ITEM = registerMenuItem("increase_beats", "增加小节拍数");
    public static final Item DECREASE_BEATS_ITEM = registerMenuItem("decrease_beats", "减少小节拍数");
    public static final Item CHUNK_SCORE_ITEM = registerMenuItem("chunk_score", "修改制谱器轨道");
    public static final Item EXPORT_ITEM = registerMenuItem("export", "导出（右键打开）");
    private static final Map<UUID, String> ACTIVE_CHARTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_CHART_LANES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING_PLAYER_SETUP = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PENDING_EDITOR_ENTRIES = new ConcurrentHashMap<>();
    private static final Set<UUID> DIMENSION_TRAVEL_READY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> SELECTED_START_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlaybackSession> PLAYBACK_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ScrollJudgementSession> SCROLL_JUDGEMENT_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> EDITOR_TRACK_ADJUSTMENTS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> EDITOR_TRACK_ADJUSTMENT_NOTICES = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.List<ItemStack>> SAVED_HOTBARS = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.List<ItemStack>> SAVED_SCENE_INVENTORIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_SCENE_EDITORS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SCENE_OPERATION_STATUS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SCENE_OPERATION_NOTICES = new ConcurrentHashMap<>();
    private static final Set<UUID> SCENE_CAPTURE_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> ACTIVE_SCENE_CHARTS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<SceneCaptureTask> SCENE_CAPTURE_TASKS = new ConcurrentLinkedQueue<>();
    private static final Map<RegistryKey<World>, EditorAreaTask> EDITOR_AREA_TASKS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, EditorTrackRebuildTask> EDITOR_TRACK_REBUILD_TASKS = new ConcurrentHashMap<>();
    private static final Map<SceneEditorAreaKey, SceneEditorAreaTask> SCENE_EDITOR_AREA_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING_SCENE_TELEPORTS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, EditorNoteRefreshTask> EDITOR_NOTE_REFRESH_TASKS = new ConcurrentHashMap<>();
    private static final Set<NoteDisplayChunkCleanup> NOTE_DISPLAY_CHUNK_CLEANUP_TASKS = ConcurrentHashMap.newKeySet();
    private static final Map<RegistryKey<World>, EditorNoteDisplaySweepTask> EDITOR_NOTE_DISPLAY_SWEEP_TASKS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, ChartManifest> CHART_CACHE_BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<String, CachedChartState> CHART_CACHE_BY_ID = new ConcurrentHashMap<>();
    private static final ExecutorService CHART_PERSISTENCE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rhythmc-maker-chart-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentLinkedQueue<SceneImportTask> SCENE_IMPORT_TASKS = new ConcurrentLinkedQueue<>();
    private static final Set<String> IMPORTING_CHARTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Map<Integer, BlockState>> DECORATION_TEMPLATES = Map.of(
        "start", loadDecorationTemplate("start"),
        "yellow", loadDecorationTemplate("yellow"),
        "green", loadDecorationTemplate("green")
    );
    private static final String HUB_MAP_MARKER = ".rhythmc_maker_hub_map_v1_installed";
    private static final String EDITOR_MAP_MARKER = ".rhythmc_maker_editor_map_v4_installed";
    private static final String CHARTER_WORLD_MARKER = ".rhythmc_maker_charter_world_v1";

    private static final String[] HUB_MAP_REGIONS = {"r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca", "r.0.0.mca"};
    private static final String[] EDITOR_MAP_REGIONS = {"r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca", "r.0.0.mca"};
    private static final String EDITOR_STATUS_OBJECTIVE = "rhythmc_maker_status";
    private static final DateTimeFormatter LAST_EDITED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PLAYBACK_PLATFORM_X = 200;
    private static final int PLAYBACK_PLATFORM_Y = 65;
    private static final int PLAYBACK_PLATFORM_Z = 0;
    private static final int SCENE_EDIT_BASE_X = 600;
    private static final int SCENE_SIZE = 128;
    private static final int SCENE_EDIT_SPACING = 200;
    private static final int SCENE_EDIT_MIN_Z = -64;
    private static final int SCENE_EDIT_MAX_Z = 63;
    private static final int SCENE_EDIT_MIN_Y = PLAYBACK_PLATFORM_Y - SCENE_SIZE / 2;
    private static final int SCENE_EDIT_MAX_Y = SCENE_EDIT_MIN_Y + SCENE_SIZE - 1;
    private static final int PLAYBACK_FRAME_Z = -2;
    private static final double PLAYBACK_APPROACH_DISTANCE = 25.0;
    private static final int PLAYBACK_MAX_PRELOADS_PER_TICK = 64;
    private static final int PLAYBACK_MAX_ACTIVE_DISPLAYS = 256;
    private static final double PLAYBACK_MAX_PRELOAD_SECONDS = 15.0;
    // The note always disappears at this fixed world-space line, independent of playerSpeed.
    // Remove on the judgment line so the model and hit sound stay synchronized.
    private static final double PLAYBACK_DISAPPEAR_Z = PLAYBACK_FRAME_Z;
    private static final int PLAYBACK_DISPLAY_UPDATE_INTERVAL_TICKS = 1;
    // Playback distance uses a 5x visual flow multiplier.

    private static final int PLAYBACK_START_DELAY_TICKS = 0;
    private static final int SCENE_IMPORT_BLOCKS_PER_TICK = 1024;
    private static final int SCENE_CAPTURE_BLOCKS_PER_TICK = 8192;
    private static final int MIN_EDITOR_LANE_COUNT = 1;
    private static final int MAX_EDITOR_LANE_COUNT = 9;
    private static final int SCENE_MIN_X = PLAYBACK_PLATFORM_X - SCENE_SIZE / 2;
    private static final int SCENE_MAX_X = SCENE_MIN_X + SCENE_SIZE - 1;
    private static final int SCENE_MIN_Y = PLAYBACK_PLATFORM_Y - SCENE_SIZE / 2;
    private static final int SCENE_MAX_Y = SCENE_MIN_Y + SCENE_SIZE - 1;
    private static final int SCENE_MIN_Z = PLAYBACK_PLATFORM_Z - SCENE_SIZE / 2;
    private static final int SCENE_MAX_Z = SCENE_MIN_Z + SCENE_SIZE - 1;
    private static final int SCENE_BORDER_INTERVAL_TICKS = 4;
    private static final int SCENE_BORDER_STEP = 8;
    private static final DustParticleEffect SCENE_BORDER_GLOW = new DustParticleEffect(0x72E8FF, 1.35f);
    private static final DustParticleEffect SCENE_BORDER_WHITE_GLOW = new DustParticleEffect(0xF3F8FF, 1.2f);
    private static final DustParticleEffect SCENE_CENTER_GLOW = new DustParticleEffect(0xFF3030, 2.8f);
    private static final DustParticleEffect PLAYBACK_FRAME_GLOW = new DustParticleEffect(0x397BFF, 1.8f);
    private static volatile RhythmcMakerConfig config = new RhythmcMakerConfig();
    private static volatile MinecraftServer activeServer;
    private static volatile MinecraftServer chartCacheServer;
    private static long lastAutosaveTick;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            activeServer = server;
            chartCacheServer = server;
            CHART_CACHE_BY_ID.clear();
            CHART_CACHE_BY_WORLD.clear();
            config = RhythmcMakerConfig.load(server);
            Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();
            installHubMap(root);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Rhythmc maker dynamic chart dimensions are loaded on demand");
        });
        registerOptionalWorldEditIntegration();
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isEditorWorld(world)) return;
            cleanupStaleNoteDisplays(world, chunk.getPos());
            scheduleNoteDisplayChunkCleanup(world, chunk.getPos(), false);
        });
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD && isCharterWorld(server, world)) {
                configureCharterWorld(server, world);
            }
            if (isEditorWorld(world)) configureEditorWorld(server, world);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Integer remaining = PENDING_PLAYER_SETUP.get(player.getUuid());
                if (remaining == null) continue;
                if (remaining > 1) {
                    PENDING_PLAYER_SETUP.put(player.getUuid(), remaining - 1);
                    continue;
                }
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                if (world.getRegistryKey() == World.OVERWORLD && isCharterWorld(server, world)) {
                    prepareHubPlayer(server, player, world);
                    PENDING_PLAYER_SETUP.remove(player.getUuid());
                    DIMENSION_TRAVEL_READY.add(player.getUuid());
                } else if (isEditorWorld(world)) {
                    ServerWorld hub = server.getOverworld();
                    if (isCharterWorld(server, hub)) prepareHubPlayer(server, player, hub);
                    else configureEditorWorld(server, world);
                    PENDING_PLAYER_SETUP.remove(player.getUuid());
                    DIMENSION_TRAVEL_READY.add(player.getUuid());
                } else {
                    PENDING_PLAYER_SETUP.remove(player.getUuid());
                    DIMENSION_TRAVEL_READY.add(player.getUuid());
                }
            }
            processSceneImportTasks(server);
            processPendingEditorEntries(server);
            processEditorAreaTasks(server);
            processEditorTrackRebuildTasks(server);
            processSceneEditorAreaTasks(server);
            processSceneCaptureTasks(server);
            processEditorNoteRefreshTasks(server);
            processNoteDisplayChunkCleanupTasks(server);
            processEditorNoteDisplaySweepTasks(server);
            processScrollJudgementSessions(server);
            processPlaybackSessions(server);
            long tick = server.getTicks();
            if (tick % SCENE_BORDER_INTERVAL_TICKS == 0) showSceneBoundary(server);
            if (tick % ticksForSeconds(config.sidebarRefreshIntervalSeconds) == 0) refreshEditorSidebar(server);
            if (tick - lastAutosaveTick < ticksForSeconds(config.autosaveIntervalSeconds)) return;
            lastAutosaveTick = server.getTicks();
            for (CachedChartState state : CHART_CACHE_BY_ID.values()) if (state.dirty) queueChartSave(server, state);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld) || !isEditorWorld(world) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (isSceneOperationBusy(serverPlayer.getUuid())) return ActionResult.FAIL;
            if (rejectEditorTrackAdjustment(serverPlayer)) return ActionResult.FAIL;
            if (hasPlaybackSession(serverPlayer.getUuid())) return ActionResult.FAIL;
            BlockPos target = hitResult.getBlockPos().offset(hitResult.getSide());
            ChartManifest chart = loadActiveChart(serverPlayer, serverWorld.getServer());
            SceneEditStorage.Scene sceneArea = chart == null ? null : sceneAtPosition(serverWorld, target, chart);
            Integer activeScene = ACTIVE_SCENE_EDITORS.get(serverPlayer.getUuid());
            if (SCENE_CAPTURE_PENDING.contains(serverPlayer.getUuid())) return ActionResult.FAIL;
            if (isWorldEditSelectionAxe(serverPlayer.getStackInHand(hand))) return ActionResult.PASS;
            if (activeScene != null) return chart != null && chart.id.equals(ACTIVE_SCENE_CHARTS.get(serverPlayer.getUuid()))
                    && sceneArea != null && sceneArea.index == activeScene ? ActionResult.PASS : ActionResult.FAIL;
            if (sceneArea != null) return ActionResult.FAIL;
            ItemStack stack = serverPlayer.getStackInHand(hand);
            if (!isNoteItem(stack)) return ActionResult.FAIL;
            if (!isValidNotePosition(target, chart, serverWorld)) return ActionResult.FAIL;
            int type = noteType(stack);
            if (!isAllowedNoteTypeAtHeight(target.getY(), type)) return ActionResult.FAIL;
            if (containsNote(chart, serverWorld, target)) return ActionResult.FAIL;
            serverWorld.setBlockState(target, stack.getItem() == Blocks.OBSERVER.asItem() ? Blocks.OBSERVER.getDefaultState()
                : stack.getItem() == Blocks.REDSTONE_BLOCK.asItem() ? Blocks.REDSTONE_BLOCK.getDefaultState()
                : stack.getItem() == Blocks.DIAMOND_BLOCK.asItem() ? Blocks.DIAMOND_BLOCK.getDefaultState()
                : Blocks.TNT.getDefaultState());
            ChartManifest.Note note = new ChartManifest.Note();
            note.id = UUID.randomUUID().toString();
            note.type = type;
            note.x = target.getX();
            note.y = target.getY();
            note.z = target.getZ();
            note.beat = PlaybackCoordinates.editorBeatAtWorldZ(chart, target.getZ());
            note.time = ChartTiming.beatToSeconds(chart, note.beat);
            chart.notes.add(note);
            try { saveChart(serverWorld.getServer(), chart); } catch (IOException ignored) { }
            return ActionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(world instanceof ServerWorld) || !isEditorWorld(world) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (isSceneOperationBusy(serverPlayer.getUuid())) return ActionResult.FAIL;
            return rejectEditorTrackAdjustment(serverPlayer)
                    ? ActionResult.FAIL
                    : ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!isEditorWorld(world) || !(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (isSceneOperationBusy(serverPlayer.getUuid())) return false;
            if (rejectEditorTrackAdjustment(serverPlayer)) return false;
            ChartManifest chart = loadActiveChart(serverPlayer, serverWorld.getServer());
            SceneEditStorage.Scene sceneArea = chart == null ? null : sceneAtPosition(serverWorld, pos, chart);
            Integer activeScene = ACTIVE_SCENE_EDITORS.get(serverPlayer.getUuid());
            if (activeScene != null && isWorldEditSelectionAxe(serverPlayer.getMainHandStack())) return true;
            if (SCENE_CAPTURE_PENDING.contains(serverPlayer.getUuid()) || hasPlaybackSession(player.getUuid())) return false;
            if (activeScene != null) return chart != null && chart.id.equals(ACTIVE_SCENE_CHARTS.get(serverPlayer.getUuid()))
                    && sceneArea != null && sceneArea.index == activeScene;
            if (sceneArea != null) return false;
            if (chart == null) return false;
            if (!isNoteBlock(state.getBlock())) return false;
            // A stale note block left by a previous layout must remain breakable.
            if (!containsNote(chart, serverWorld, pos)) return true;
            removeNote(chart, serverWorld, pos);
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING_PLAYER_SETUP.remove(handler.getPlayer().getUuid());
            PENDING_EDITOR_ENTRIES.remove(handler.getPlayer().getUuid());
            restoreSceneHotbar(handler.getPlayer());
            queueSceneCaptureOnDisconnect(server, handler.getPlayer());
            ACTIVE_SCENE_EDITORS.remove(handler.getPlayer().getUuid());
            ACTIVE_SCENE_CHARTS.remove(handler.getPlayer().getUuid());
            DIMENSION_TRAVEL_READY.remove(handler.getPlayer().getUuid());
            stopPlayback(server, handler.getPlayer(), false);
            saveActiveChart(server, handler.getPlayer());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // The player is not yet registered in ChunkLevelManager during JOIN. Moving here
            // leaves its source chunk tracker absent and makes the next cross-dimension move fail.
            DIMENSION_TRAVEL_READY.remove(player.getUuid());
            PENDING_PLAYER_SETUP.put(player.getUuid(), 2);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_enter")
                .then(net.minecraft.server.command.CommandManager.argument("chartId", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context -> enterChartDimension(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "chartId")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("hub")
                .executes(context -> returnHub(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_beats")
                .then(net.minecraft.server.command.CommandManager.argument("delta", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 1))
                .executes(context -> adjustBeats(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "delta")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_beats_set")
                .then(net.minecraft.server.command.CommandManager.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
                .executes(context -> setBeats(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "value")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_layout_set")
                .then(net.minecraft.server.command.CommandManager.argument("divisions", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
                    .then(net.minecraft.server.command.CommandManager.argument("lanes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(MIN_EDITOR_LANE_COUNT, MAX_EDITOR_LANE_COUNT))
                        .executes(context -> setEditorLayout(context.getSource(),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "divisions"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "lanes"))))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_start_chunk")
                .then(net.minecraft.server.command.CommandManager.argument("delta", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-16, 16))
                .executes(context -> adjustStartChunk(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "delta")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_start_chunk_set")
                .then(net.minecraft.server.command.CommandManager.argument("chunk", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                    .executes(context -> setStartChunk(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunk")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_teleport_chunk")
                .then(net.minecraft.server.command.CommandManager.argument("chunk", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                    .executes(context -> teleportToChunk(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunk")))));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_teleport_time")
                 .then(net.minecraft.server.command.CommandManager.argument("seconds", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0))
                     .executes(context -> teleportToSongTime(context.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "seconds")))));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_teleport_playback_scene")
                 .executes(context -> teleportToPlaybackScene(context.getSource())));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_teleport_editor_platform")
                  .executes(context -> teleportToEditorPlatform(context.getSource())));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_scene_editor")
                 .then(net.minecraft.server.command.CommandManager.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                     .executes(context -> teleportToSceneEditor(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index")))));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_scene_reset")
                 .then(net.minecraft.server.command.CommandManager.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                     .executes(context -> resetSceneEditorCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index")))));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_scene_delete")
                 .then(net.minecraft.server.command.CommandManager.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 4096))
                     .executes(context -> deleteSceneEditorCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index")))));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_scene_save")
                 .executes(context -> saveSceneEditorCommand(context.getSource())));
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_more_options")
                 .executes(context -> openMoreOptions(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_more_return")
                .executes(context -> closeMoreOptions(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_offset")
                .then(net.minecraft.server.command.CommandManager.argument("milliseconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-60000, 60000))
                    .executes(context -> adjustOffset(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "milliseconds")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_stop_playback")
                .executes(context -> stopPlaybackCommand(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_play")
                .executes(context -> togglePlayback(context.getSource()))
                .then(net.minecraft.server.command.CommandManager.argument("startChunk", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                    .executes(context -> togglePlayback(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startChunk")))
                    .then(net.minecraft.server.command.CommandManager.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(net.minecraft.server.command.CommandManager.argument("rate", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.25, 2.0))
                            .executes(context -> togglePlayback(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startChunk"), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mode"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "rate")))))));
        });
    }

    private static boolean isCharterSave(MinecraftServer server) {
        Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();
        return Files.exists(root.resolve(HUB_MAP_MARKER))
            || Files.exists(root.resolve(CHARTER_WORLD_MARKER))
            || Files.exists(root.resolve(".rhythmc_maker_charter_map_installed"));
    }
    private static boolean isCharterWorld(MinecraftServer server, ServerWorld world) {
        return world.getRegistryKey() == World.OVERWORLD && isCharterSave(server);
    }
    public static boolean isEditorWorld(World world) {
        if (ChartDimensionManager.isChartWorld(world.getRegistryKey()) || world.getRegistryKey().equals(CHARTER_DIMENSION)) return true;
        // Keep recognizing pre-fix dynamic worlds so reconnect logic can return players to the hub.
        return world instanceof CustomLevel
            && world.getRegistryKey().getValue().getNamespace().equals(MOD_ID)
            && ChartDimensionManager.isChartWorld(world.getRegistryKey());
    }
    private static void configureCharterWorld(MinecraftServer server, ServerWorld world) {
        ((ServerWorldProperties) world.getLevelProperties()).setGameMode(GameMode.CREATIVE);
        server.setDefaultGameMode(GameMode.CREATIVE);
        server.getPlayerManager().setCheatsAllowed(true);
        server.setDifficulty(Difficulty.PEACEFUL, true);
        world.getGameRules().setValue(GameRules.KEEP_INVENTORY, true, server);
        world.getGameRules().setValue(GameRules.RESPAWN_RADIUS, 0, server);
        world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), CHARTER_SPAWN, 0.0f, 0.0f));
        LOGGER.info("Configured Rhythmc maker hub at spawn {}", CHARTER_SPAWN);
    }
    private static void configureEditorWorld(MinecraftServer server, ServerWorld world) {
        server.setDifficulty(Difficulty.PEACEFUL, true);
        server.getPlayerManager().setCheatsAllowed(true);
        world.getGameRules().setValue(GameRules.KEEP_INVENTORY, true, server);
        world.getGameRules().setValue(GameRules.RESPAWN_RADIUS, 0, server);
        cleanupOrphanedPlaybackDisplays(world);
        world.getGameRules().setValue(GameRules.TNT_EXPLODES, false, server);
        world.getGameRules().setValue(GameRules.ADVANCE_TIME, true, server);
        ((ServerWorldProperties) world.getLevelProperties()).setGameMode(GameMode.CREATIVE);
        world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), EDITOR_SPAWN, 0.0f, 0.0f));
    }
    private static void prepareHubPlayer(MinecraftServer server, ServerPlayerEntity player, ServerWorld hub) {
        configureCharterWorld(server, hub);
        player.getInventory().clear();
        player.changeGameMode(GameMode.CREATIVE);
        if (player.getEntityWorld() == hub) {
            player.requestTeleport(0.0, 65.0, 0.0);
        } else {
            player.teleport(hub, 0.0, 65.0, 0.0, Set.of(), 0.0f, 0.0f, false);
        }
        setPlayerSpawn(player, hub, CHARTER_SPAWN);
        giveCharterItems(player);
        LOGGER.info("Prepared hub player {} at {}", player.getName().getString(), player.getBlockPos());
    }
    private static void prepareEditorPlayer(MinecraftServer server, ServerPlayerEntity player, ServerWorld editor) {
        configureEditorWorld(server, editor);
        setEditorPlayerSpawn(player, editor);
        player.getInventory().clear();
        player.changeGameMode(GameMode.CREATIVE);
        if (player.getEntityWorld() == editor) {
            player.requestTeleport(EDITOR_SPAWN.getX(), EDITOR_SPAWN.getY(), EDITOR_SPAWN.getZ());
        } else {
            player.teleport(editor, EDITOR_SPAWN.getX(), EDITOR_SPAWN.getY(), EDITOR_SPAWN.getZ(), Set.of(), 0.0f, 0.0f, false);
        }
        giveEditorItems(player);
    }
    private static void setPlayerSpawn(ServerPlayerEntity player, ServerWorld world, BlockPos position) {
        WorldProperties.SpawnPoint spawn = WorldProperties.SpawnPoint.create(world.getRegistryKey(), position, 0.0f, 0.0f);
        player.setSpawnPoint(new ServerPlayerEntity.Respawn(spawn, true), false);
    }
    private static void setEditorPlayerSpawn(ServerPlayerEntity player, ServerWorld editor) {
        WorldProperties.SpawnPoint spawn = WorldProperties.SpawnPoint.create(editor.getRegistryKey(), EDITOR_SPAWN, 0.0f, 0.0f);
        player.setSpawnPoint(new ServerPlayerEntity.Respawn(spawn, true), false);
    }
    private static void giveCharterItems(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getInventory().setStack(0, menuStack(START_CHART_ITEM));
        player.getInventory().setStack(4, menuStack(AWA_ITEM));
        player.getInventory().setStack(8, menuStack(SETTINGS_ITEM));
        player.currentScreenHandler.sendContentUpdates();
    }
    private static void giveEditorItems(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getInventory().setStack(0, new ItemStack(Blocks.OBSERVER.asItem()));
        player.getInventory().setStack(1, new ItemStack(Blocks.REDSTONE_BLOCK.asItem()));
        player.getInventory().setStack(2, new ItemStack(Blocks.DIAMOND_BLOCK.asItem()));
        player.getInventory().setStack(3, new ItemStack(Blocks.TNT.asItem()));
        player.getInventory().setStack(4, menuStack(PLAY_ITEM));
        player.getInventory().setStack(5, menuStack(CHUNK_SCORE_ITEM));
        player.getInventory().setStack(6, menuStack(CHART_SETTINGS_ITEM));
        player.getInventory().setStack(7, menuStack(MORE_SETTINGS_ITEM));
        player.getInventory().setStack(8, menuStack(QUICK_FUNCTIONS_ITEM));
        player.currentScreenHandler.sendContentUpdates();
    }
    private static int openMoreOptions(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        java.util.List<ItemStack> hotbar = new java.util.ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) hotbar.add(player.getInventory().getStack(slot).copy());
        SAVED_HOTBARS.put(player.getUuid(), hotbar);
        player.getInventory().clear();
        player.getInventory().setStack(0, menuStack(CHART_INFO_ITEM));
        player.getInventory().setStack(1, menuStack(SCENE_EDIT_ITEM));
        player.getInventory().setStack(2, menuStack(EFFECT_TRACK_ITEM));
        player.getInventory().setStack(4, menuStack(EXPORT_ITEM));
        player.getInventory().setStack(7, menuStack(SETTINGS_ITEM));
        player.getInventory().setStack(8, menuStack(MORE_OPTIONS_RETURN_ITEM));
        player.currentScreenHandler.sendContentUpdates();
        return 1;
    }
    private static int closeMoreOptions(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        java.util.List<ItemStack> hotbar = SAVED_HOTBARS.remove(player.getUuid());
        player.getInventory().clear();
        if (hotbar != null) for (int slot = 0; slot < Math.min(9, hotbar.size()); slot++) player.getInventory().setStack(slot, hotbar.get(slot));
        player.currentScreenHandler.sendContentUpdates();
        return 1;
    }
    private static int enterChartDimension(net.minecraft.server.command.ServerCommandSource source, String chartId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!DIMENSION_TRAVEL_READY.contains(player.getUuid())) {
            player.sendMessage(Text.literal("制谱器正在完成玩家初始化，请稍后重试"), false);
            return 0;
        }
        ChartManifest chart;
        try {
            chart = ChartStorage.find(source.getServer(), chartId);
            if (chart == null) { player.sendMessage(Text.literal("找不到谱面：" + chartId), false); return 0; }
            cacheChart(source.getServer(), chart);
            if (IMPORTING_CHARTS.contains(chart.id)) { player.sendMessage(Text.literal("该谱面的场景仍在导入，请稍后重试"), true); return 0; }
            chart.trackLength = calculateTrackLength(chart);
            saveChart(source.getServer(), chart);
            String previousDimensionId = chart.dimensionId;
            ChartDimensionManager.key(chart);
            if (!chart.dimensionId.equals(previousDimensionId)) saveChart(source.getServer(), chart);
            ServerWorld editor = ChartDimensionManager.getOrCreate(source.getServer(), chart);
            String validationError = validateEditorEntry(source.getServer(), player, chart, editor);
            if (validationError != null) {
                player.sendMessage(Text.literal("进入谱面制谱维度已取消：" + validationError), false);
                return 0;
            }
            String previousChartId = ACTIVE_CHARTS.get(player.getUuid());
            if (previousChartId != null && !previousChartId.equals(chartId)) {
                ChartManifest previousChart = cachedChart(source.getServer(), previousChartId);
                if (previousChart != null) clearChartNoteBlocks(editor, previousChart);
            }
            EDITOR_AREA_TASKS.remove(editor.getRegistryKey());
            EDITOR_NOTE_REFRESH_TASKS.remove(editor.getRegistryKey());
            clearAllChartNoteDisplays(editor);
            ACTIVE_CHARTS.put(player.getUuid(), chartId);
            CHART_CACHE_BY_WORLD.put(editor.getRegistryKey(), chart);
            ACTIVE_CHART_LANES.put(player.getUuid(), laneCount(chart));
            SELECTED_START_CHUNKS.put(player.getUuid(), Math.max(1, Math.min(Math.max(1, chart.chunkCount), chart.selectedStartChunk)));
            if (!hasEditorTrack(editor, chart)) {
                restoreEditorTrackFoundation(editor, 0, chart.trackLength, laneCount(chart));
                extendEditorArea(editor, 0, chart.trackLength, chartBeats(source.getServer(), chartId), laneCount(chart));
            }
            if (!chart.sceneInitialized) {
                buildPlaybackArea(editor, laneCount(chart));
                chart.sceneInitialized = true;
                saveChart(source.getServer(), chart);
            }
            markSelectedStartChunk(editor, chart, selectedStartChunk(player, chart));
            // Queue note updates so the dimension-change packet is not delayed by a full-chart refresh.
            refreshNoteDisplays(editor, chart);
            PENDING_EDITOR_ENTRIES.put(player.getUuid(), chartId);
        } catch (IOException | RuntimeException exception) {
            ACTIVE_CHARTS.remove(player.getUuid());
            ACTIVE_CHART_LANES.remove(player.getUuid());
            SELECTED_START_CHUNKS.remove(player.getUuid());
            LOGGER.error("Failed to enter Rhythmc maker chart dimension {}", chartId, exception);
            player.sendMessage(Text.literal("进入谱面制谱维度失败，请查看 latest.log"), false);
            return 0;
        }
        player.sendMessage(Text.literal("正在进入谱面制谱维度：" + chartId), true);
        return 1;
    }
    private static void processSceneImportTasks(MinecraftServer server) {
        SceneImportTask task = SCENE_IMPORT_TASKS.peek();
        if (task == null) return;
        try {
            if (!task.process(server, SCENE_IMPORT_BLOCKS_PER_TICK)) return;
            SCENE_IMPORT_TASKS.poll();
            boolean remaining = SCENE_IMPORT_TASKS.stream().anyMatch(pending -> pending.chart.id.equals(task.chart.id));
            if (!remaining) {
                IMPORTING_CHARTS.remove(task.chart.id);
                LOGGER.info("Finished importing Rhythmc scenes for chart {}", task.chart.id);
            }
        } catch (IOException | RuntimeException exception) {
            SCENE_IMPORT_TASKS.poll();
            SCENE_IMPORT_TASKS.removeIf(pending -> pending.chart.id.equals(task.chart.id));
            IMPORTING_CHARTS.remove(task.chart.id);
            LOGGER.error("Failed to import Rhythmc scenes for chart {}", task.chart.id, exception);
        }
    }
    private static void processPendingEditorEntries(MinecraftServer server) {
        for (var iterator = PENDING_EDITOR_ENTRIES.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !entry.getValue().equals(ACTIVE_CHARTS.get(player.getUuid()))) continue;
            if (!DIMENSION_TRAVEL_READY.contains(player.getUuid())) continue;
            try {
                ChartManifest chart = cachedChart(server, entry.getValue());
                if (chart == null) continue;
                ServerWorld editor = ChartDimensionManager.getOrCreate(server, chart);
                String validationError = validateEditorEntry(server, player, chart, editor);
                if (validationError != null) {
                    ACTIVE_CHARTS.remove(player.getUuid());
                    SELECTED_START_CHUNKS.remove(player.getUuid());
                    player.sendMessage(Text.literal("进入谱面制谱维度已取消：" + validationError), false);
                    continue;
                }
                prepareEditorPlayer(server, player, editor);
                player.sendMessage(Text.literal("已进入谱面制谱维度：" + entry.getValue()), true);
            } catch (IOException | RuntimeException exception) {
                ACTIVE_CHARTS.remove(player.getUuid());
                SELECTED_START_CHUNKS.remove(player.getUuid());
                LOGGER.error("Failed to complete Rhythmc maker chart entry {}", entry.getValue(), exception);
                player.sendMessage(Text.literal("进入谱面制谱维度失败，请查看 latest.log"), false);
            }
        }
    }
    private static void verifyStaticEditorPool(MinecraftServer server) {
        ChartDimensionManager.preloadCharts(server);
    }
    private static String validateEditorEntry(MinecraftServer server, ServerPlayerEntity player, ChartManifest chart, ServerWorld editor) {
        if (!DIMENSION_TRAVEL_READY.contains(player.getUuid())) return "玩家区块追踪尚未就绪";
        if (editor == null || !ChartDimensionManager.isChartWorld(editor.getRegistryKey())) return "目标不是已注册的谱面动态维度";
        try {
            if (!editor.getRegistryKey().equals(ChartDimensionManager.key(chart))) return "谱面与目标动态维度不一致";
        } catch (IOException exception) {
            return "谱面没有有效的动态维度绑定";
        }
        try {
            editor.getChunk(EDITOR_SPAWN.getX() >> 4, EDITOR_SPAWN.getZ() >> 4);
            return null;
        } catch (RuntimeException exception) {
            LOGGER.error("Rhythmc maker chart dimension {} failed chunk preflight", chart.dimensionId, exception);
            return "目标槽位出生区块无法加载";
        }
    }
    private static int returnHub(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld hub = source.getServer().getOverworld();
        if (!isCharterWorld(source.getServer(), hub)) { player.sendMessage(Text.literal("当前存档没有制谱器大厅"), false); return 0; }
        stopPlayback(source.getServer(), player, false);
        String chartId = ACTIVE_CHARTS.get(player.getUuid());
        if (chartId != null) {
            try {
                ChartManifest chart = cachedChart(source.getServer(), chartId);
                if (chart != null) {
                    chart.lastEdited = LocalDateTime.now().format(LAST_EDITED_FORMAT);
                    saveChart(source.getServer(), chart);
                }
            } catch (IOException exception) {
                LOGGER.warn("Failed to record chart return time for {}", chartId, exception);
            }
        }
        ACTIVE_CHARTS.remove(player.getUuid());
        ACTIVE_CHART_LANES.remove(player.getUuid());
        SELECTED_START_CHUNKS.remove(player.getUuid());
        prepareHubPlayer(source.getServer(), player, hub);
        refreshEditorSidebar(source.getServer());
        return 1;
    }
    private static int adjustOffset(net.minecraft.server.command.ServerCommandSource source, int milliseconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        chart.offsetMillis = Math.max(-60000L, Math.min(60000L, milliseconds));
        try {
            saveChart(source.getServer(), chart);
            refreshNoteDisplays(world, chart);
        } catch (IOException ignored) {
            return 0;
        }
        player.sendMessage(Text.literal("谱面偏移值：" + chart.offsetMillis + " ms"), true);
        return 1;
    }
    private static int adjustBeats(net.minecraft.server.command.ServerCommandSource source, int delta) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) return 0;
        return updateDivisionsPerChunk(source, chart, divisionsPerChunk(chart) + delta);
    }
    private static int setBeats(net.minecraft.server.command.ServerCommandSource source, int target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) return 0;
        return updateDivisionsPerChunk(source, chart, target);
    }
    private static int updateDivisionsPerChunk(net.minecraft.server.command.ServerCommandSource source, ChartManifest chart, int target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return updateEditorLayout(source, chart, target, laneCount(chart));
    }

    private static int setEditorLayout(net.minecraft.server.command.ServerCommandSource source, int divisions, int lanes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) return 0;
        return updateEditorLayout(source, chart, divisions, lanes);
    }

    private static int updateEditorLayout(net.minecraft.server.command.ServerCommandSource source, ChartManifest chart, int targetDivisions, int targetLaneCount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (rejectEditorTrackAdjustment(player)) return 0;
        targetDivisions = Math.max(1, Math.min(32, targetDivisions));
        if (!isSupportedLaneCount(targetLaneCount)) {
            player.sendMessage(Text.literal("轨道宽度必须是 1 至 " + MAX_EDITOR_LANE_COUNT + " 的奇数"), true);
            return 0;
        }

        int previousDivisions = divisionsPerChunk(chart);
        int previousLaneCount = laneCount(chart);
        int previousTrackLength = Math.max(chart.trackLength, calculateTrackLength(chart));
        if (targetDivisions == previousDivisions && targetLaneCount == previousLaneCount) {
            player.sendMessage(Text.literal("制谱器轨道已是 " + targetLaneCount + " × " + targetDivisions), true);
            return 1;
        }
        if (targetLaneCount < previousLaneCount && hasNotesOutsideLaneRange(chart, targetLaneCount)) {
            player.sendMessage(Text.literal("无法缩窄轨道：请先移动或删除外侧轨道上的音符"), true);
            return 0;
        }

        int previousChunk = currentChunk(player, chart, previousDivisions);
        Map<String, BlockPos> oldNotePositions = captureNotePositions(chart);
        ServerWorld editor = null;
        try { editor = source.getServer().getWorld(ChartDimensionManager.key(chart)); } catch (IOException ignored) { }
        try {
            if (editor != null) {
                EDITOR_TRACK_ADJUSTMENTS.add(player.getUuid());
                EDITOR_AREA_TASKS.remove(editor.getRegistryKey());
                EDITOR_NOTE_REFRESH_TASKS.remove(editor.getRegistryKey());
                EDITOR_NOTE_DISPLAY_SWEEP_TASKS.remove(editor.getRegistryKey());
                clearNoteBlocks(editor, oldNotePositions);
                EDITOR_TRACK_REBUILD_TASKS.remove(editor.getRegistryKey());
            }
            chart.divisionsPerChunk = targetDivisions;
            chart.beatsPerMeasure = 0;
            chart.laneCount = targetLaneCount;
            ACTIVE_CHART_LANES.put(player.getUuid(), targetLaneCount);
            repositionNotes(chart);
            chart.trackLength = calculateTrackLength(chart);
            saveChart(source.getServer(), chart);
            if (editor != null) CHART_CACHE_BY_WORLD.put(editor.getRegistryKey(), chart);
            if (editor != null) {
                EDITOR_TRACK_REBUILD_TASKS.put(editor.getRegistryKey(), new EditorTrackRebuildTask(
                        editor.getRegistryKey(), 0, previousTrackLength, chart.trackLength,
                        targetDivisions, targetLaneCount, previousLaneCount, player.getUuid(), previousChunk));
                player.sendMessage(Text.literal("正在调整制谱器轨道中，请等待旧展示实体清理完成"), true);
            }
            if (editor == null) {
                player.sendMessage(Text.literal("制谱器轨道已更新为 " + laneCount(chart) + " × " + divisionsPerChunk(chart)), true);
            }
            return 1;
        } catch (IOException exception) {
            EDITOR_TRACK_ADJUSTMENTS.remove(player.getUuid());
            EDITOR_TRACK_ADJUSTMENT_NOTICES.remove(player.getUuid());
            chart.divisionsPerChunk = previousDivisions;
            chart.laneCount = previousLaneCount;
            ACTIVE_CHART_LANES.put(player.getUuid(), previousLaneCount);
            chart.beatsPerMeasure = 0;
            repositionNotes(chart);
            chart.trackLength = calculateTrackLength(chart);
            if (editor != null) {
                restoreEditorTrackFoundation(editor, 0, chart.trackLength, previousLaneCount);
                extendEditorArea(editor, 0, chart.trackLength, previousDivisions, previousLaneCount);
                refreshNoteDisplays(editor, chart);
            }
            player.sendMessage(Text.literal("制谱器轨道更新失败，已恢复原布局"), true);
            return 0;
        }
    }

    private static boolean saveActiveChart(MinecraftServer server, ServerPlayerEntity player) {
        String id = ACTIVE_CHARTS.get(player.getUuid());
        if (id == null) return false;
        CachedChartState state = CHART_CACHE_BY_ID.get(id);
        if (state == null) {
            try { state = cacheChart(server, ChartStorage.find(server, id)); } catch (IOException ignored) { return false; }
        }
        return state != null && queueChartSave(server, state);
    }
    private static void saveChart(MinecraftServer server, ChartManifest chart) throws IOException {
        CachedChartState state = cacheChart(server, chart);
        if (state == null) throw new IOException("谱面缓存无效");
        synchronized (state) {
            state.revision++;
            state.dirty = true;
        }
    }
    public static void saveCurrentChart(MinecraftServer server, ServerPlayerEntity player) { saveActiveChart(server, player); }
    public static void updateChart(MinecraftServer server, ChartManifest chart) throws IOException {
        CachedChartState state = cacheChart(server, chart);
        synchronized (state) { state.chart = chart; }
        saveChart(server, chart);
        if (state != null) queueChartSave(server, state);
    }
    public static String activeChart(ServerPlayerEntity player) { return ACTIVE_CHARTS.get(player.getUuid()); }
    private static boolean rejectEditorTrackAdjustment(ServerPlayerEntity player) {
        if (!EDITOR_TRACK_ADJUSTMENTS.contains(player.getUuid())) return false;
        long tick = activeServer == null ? Long.MAX_VALUE : activeServer.getTicks();
        long previousNotice = EDITOR_TRACK_ADJUSTMENT_NOTICES.getOrDefault(player.getUuid(), Long.MIN_VALUE);
        if (tick - previousNotice >= 20L) {
            player.sendMessage(Text.literal("正在调整制谱器轨道中，请等待调整完成"), true);
            EDITOR_TRACK_ADJUSTMENT_NOTICES.put(player.getUuid(), tick);
        }
        return true;
    }
    private static ChartManifest loadActiveChart(ServerPlayerEntity player, MinecraftServer server) {
        String id = ACTIVE_CHARTS.get(player.getUuid());
        if (id == null) return null;
        CachedChartState cached = CHART_CACHE_BY_ID.get(id);
        if (cached != null) return cached.chart;
        try { return cachedChart(server, id); } catch (IOException ignored) { return null; }
    }
    private static int trackLength(MinecraftServer server, String id) {
        ChartManifest chart = CHART_CACHE_BY_ID.containsKey(id) ? CHART_CACHE_BY_ID.get(id).chart : null;
        if (chart == null) try { chart = cachedChart(server, id); } catch (IOException ignored) { }
        return chart == null ? 4 : calculateTrackLength(chart);
    }
    private static int chartBeats(MinecraftServer server, String id) {
        ChartManifest chart = CHART_CACHE_BY_ID.containsKey(id) ? CHART_CACHE_BY_ID.get(id).chart : null;
        if (chart == null) try { chart = cachedChart(server, id); } catch (IOException ignored) { }
        return chart == null ? 4 : divisionsPerChunk(chart);
    }

    private static CachedChartState cacheChart(MinecraftServer server, ChartManifest chart) {
        if (chart == null || chart.id == null || chart.id.isBlank()) return null;
        if (chartCacheServer != server) {
            chartCacheServer = server;
            CHART_CACHE_BY_ID.clear();
            CHART_CACHE_BY_WORLD.clear();
        }
        return CHART_CACHE_BY_ID.computeIfAbsent(chart.id, ignored -> new CachedChartState(chart));
    }

    private static ChartManifest cachedChart(MinecraftServer server, String chartId) throws IOException {
        if (chartId == null || chartId.isBlank()) return null;
        CachedChartState cached = CHART_CACHE_BY_ID.get(chartId);
        if (cached != null) return cached.chart;
        ChartManifest chart = ChartStorage.find(server, chartId);
        return chart == null ? null : cacheChart(server, chart).chart;
    }

    private static boolean queueChartSave(MinecraftServer server, CachedChartState state) {
        long revision;
        synchronized (state) {
            if (!state.dirty || state.saving) return false;
            state.saving = true;
            revision = state.revision;
        }
        CHART_PERSISTENCE_EXECUTOR.submit(() -> {
            try {
                ChartStorage.update(server, state.chart);
                server.execute(() -> {
                    synchronized (state) {
                        if (state.revision == revision) state.dirty = false;
                        state.saving = false;
                    }
                });
            } catch (IOException exception) {
                server.execute(() -> {
                    synchronized (state) { state.saving = false; }
                    LOGGER.warn("Failed to persist chart {}", state.chart.id, exception);
                });
            }
        });
        return true;
    }

    private static final class CachedChartState {
        private ChartManifest chart;
        private long revision;
        private boolean dirty;
        private boolean saving;
        private CachedChartState(ChartManifest chart) { this.chart = chart; }
    }
    private static int calculateTrackLength(ChartManifest chart) {
        double beats = chart.durationSeconds > 0 && chart.bpm > 0 ? ChartTiming.secondsToBeat(chart, chart.durationSeconds) : 8.0;
        chart.totalBeats = beats;
        chart.chunkCount = Math.max(1, (int) Math.ceil(beats));
        return Math.max(8, chart.chunkCount * divisionsPerChunk(chart));
    }

    private static int divisionsPerChunk(ChartManifest chart) {
        int value = chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure;
        return Math.max(1, Math.min(32, value > 0 ? value : config.defaultDivisionsPerChunk));
    }
    private static int laneCount(ChartManifest chart) {
        return chart != null && isSupportedLaneCount(chart.laneCount) ? chart.laneCount : 3;
    }
    private static boolean isSupportedLaneCount(int value) {
        return value >= MIN_EDITOR_LANE_COUNT && value <= MAX_EDITOR_LANE_COUNT && (value & 1) == 1;
    }
    private static int laneMinX(ChartManifest chart) { return -(laneCount(chart) / 2); }
    private static int laneMaxX(ChartManifest chart) { return laneCount(chart) / 2; }
    private static int laneWallLeftX(ChartManifest chart) { return laneMinX(chart) - 1; }
    private static int laneWallRightX(ChartManifest chart) { return laneMaxX(chart) + 1; }
    private static boolean hasNotesOutsideLaneRange(ChartManifest chart, int targetLaneCount) {
        int minX = -(targetLaneCount / 2);
        int maxX = targetLaneCount / 2;
        return chart.notes != null && chart.notes.stream().anyMatch(note -> note != null && (note.x < minX || note.x > maxX));
    }
    private static int currentChunk(ServerPlayerEntity player, ChartManifest chart, int divisions) {
        double progress = Math.max(0.0, -player.getZ() - 3.0);
        return Math.max(1, Math.min(Math.max(1, chart.chunkCount), (int) Math.floor(progress / Math.max(1, divisions)) + 1));
    }
    public static RhythmcMakerConfig currentConfig() { return config; }
    public static void updateConfig(MinecraftServer server, RhythmcMakerConfig updated) throws IOException { updated.save(server); config = updated; }
    public static int defaultDivisionsPerChunk() {
        return config.defaultDivisionsPerChunk;
    }
    private static long ticksForSeconds(double seconds) {
        return Math.max(1L, Math.min(1728000L, Math.round(seconds * 20.0)));
    }
    private static int selectedStartChunk(ServerPlayerEntity player, ChartManifest chart) {
        int maximum = Math.max(1, chart.chunkCount);
        int selected = SELECTED_START_CHUNKS.getOrDefault(player.getUuid(), chart.selectedStartChunk);
        selected = Math.max(1, Math.min(maximum, selected));
        SELECTED_START_CHUNKS.put(player.getUuid(), selected);
        return selected;
    }
    private static int adjustStartChunk(net.minecraft.server.command.ServerCommandSource source, int delta) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return adjustStartChunk(source.getServer(), source.getPlayerOrThrow(), delta);
    }
    public static int adjustStartChunkFromClient(MinecraftServer server, UUID playerId, int delta) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return 0;
        return adjustStartChunk(server, player, delta);
    }
    private static int adjustStartChunk(MinecraftServer server, ServerPlayerEntity player, int delta) {
        if (hasPlaybackSession(player.getUuid())) return 0;
        ChartManifest chart = loadActiveChart(player, server);
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        int previous = selectedStartChunk(player, chart);
        int selected = Math.max(1, Math.min(Math.max(1, chart.chunkCount), previous + delta));
        if (selected == previous) return 1;
        SELECTED_START_CHUNKS.put(player.getUuid(), selected);
        chart.selectedStartChunk = selected;
        try { saveChart(server, chart); } catch (IOException ignored) { return 0; }
        restoreSelectedStartChunk(world, chart, previous);
        markSelectedStartChunk(world, chart, selected);
        player.sendMessage(Text.literal("播放起始 Chunk：" + selected), true);
        return 1;
    }
    private static int setStartChunk(net.minecraft.server.command.ServerCommandSource source, int requestedChunk) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (hasPlaybackSession(player.getUuid())) return 0;
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        int maximum = Math.max(1, chart.chunkCount);
        if (requestedChunk > maximum) {
            player.sendMessage(Text.literal("谱面没有这么长awa~"), true);
            return 0;
        }
        int previous = selectedStartChunk(player, chart);
        if (requestedChunk == previous) return 1;
        int selectedChunk = Math.max(1, Math.min(maximum, requestedChunk));
        SELECTED_START_CHUNKS.put(player.getUuid(), selectedChunk);
        chart.selectedStartChunk = selectedChunk;
        try { saveChart(source.getServer(), chart); } catch (IOException ignored) { return 0; }
        restoreSelectedStartChunk(world, chart, previous);
        markSelectedStartChunk(world, chart, selectedChunk);
        player.sendMessage(Text.literal("播放起始 Chunk：" + selectedChunk), true);
        return 1;
    }
    private static int teleportToChunk(net.minecraft.server.command.ServerCommandSource source, int requestedChunk) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        if (requestedChunk > Math.max(1, chart.chunkCount)) {
            player.sendMessage(Text.literal("谱面没有这么长awa~"), true);
            return 0;
        }
        double targetZ = PlaybackCoordinates.editorWorldZAtBeat(chart, requestedChunk - 1.0);
        player.teleport(world, player.getX(), player.getY(), targetZ, Set.of(), player.getYaw(), player.getPitch(), false);
        player.sendMessage(Text.literal("已传送至 Chunk " + requestedChunk), true);
        return 1;
    }
    private static int teleportToSongTime(net.minecraft.server.command.ServerCommandSource source, double requestedSeconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        double totalSeconds = totalChartSeconds(chart);
        if (!Double.isFinite(requestedSeconds) || requestedSeconds > totalSeconds) {
            player.sendMessage(Text.literal("谱面没有这么长awa~"), true);
            return 0;
        }
        double targetZ = PlaybackCoordinates.editorWorldZAtBeat(chart, ChartTiming.secondsToBeat(chart, requestedSeconds));
        player.teleport(world, player.getX(), player.getY(), targetZ, Set.of(), player.getYaw(), player.getPitch(), false);
        player.sendMessage(Text.literal("已传送至 " + String.format(Locale.ROOT, "%.2f", requestedSeconds) + " 秒"), true);
        return 1;
    }
    private static int teleportToPlaybackScene(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (hasPlaybackSession(player.getUuid())) {
            player.sendMessage(Text.literal("播放中不能使用快捷传送"), true);
            return 0;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world) || loadActiveChart(player, source.getServer()) == null) {
            player.sendMessage(Text.literal("当前不在制谱器谱面世界"), true);
            return 0;
        }
        player.teleport(world, PLAYBACK_PLATFORM_X + 0.5, PLAYBACK_PLATFORM_Y + 1.0, PLAYBACK_PLATFORM_Z + 0.5, Set.of(), 180.0f, 0.0f, false);
        player.sendMessage(Text.literal("已传送到播放场景"), true);
        return 1;
    }
    private static int teleportToEditorPlatform(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (hasPlaybackSession(player.getUuid())) {
            player.sendMessage(Text.literal("播放中不能使用快捷传送"), true);
            return 0;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world) || loadActiveChart(player, source.getServer()) == null) {
            player.sendMessage(Text.literal("当前不在制谱器谱面世界"), true);
            return 0;
        }
        player.teleport(world, EDITOR_SPAWN.getX(), EDITOR_SPAWN.getY(), EDITOR_SPAWN.getZ(), Set.of(), 0.0f, 0.0f, false);
        player.sendMessage(Text.literal("已传送到制谱器平台"), true);
        return 1;
    }
    private static int teleportToSceneEditor(net.minecraft.server.command.ServerCommandSource source, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) { player.sendMessage(Text.literal("当前没有活动谱面"), true); return 0; }
        try {
            SceneEditStorage.Scene scene = SceneEditStorage.list(source.getServer(), chart.id).stream().filter(value -> value.index == index).findFirst().orElse(null);
            if (scene == null) { player.sendMessage(Text.literal("场景不存在"), true); return 0; }
            SceneEditorAreaTask pendingTask = SCENE_EDITOR_AREA_TASKS.get(new SceneEditorAreaKey(world.getRegistryKey(), scene.x));
            if (pendingTask != null) {
                PENDING_SCENE_TELEPORTS.put(player.getUuid(), scene.index);
                player.sendMessage(Text.literal("场景平台正在生成，请稍候…"), true);
                return 1;
            }
            saveSceneHotbar(player);
            ACTIVE_SCENE_EDITORS.put(player.getUuid(), scene.index);
            ACTIVE_SCENE_CHARTS.put(player.getUuid(), chart.id);
            player.teleport(world, scene.x + 0.5, 66.0, 0.5, Set.of(), 0.0f, 0.0f, false);
            player.getInventory().setStack(0, menuStack(SAVE_SCENE_ITEM));
            player.currentScreenHandler.sendContentUpdates();
            player.sendMessage(Text.literal("已进入 " + scene.name + " 搭建场地，右键保存场景后返回"), true);
            return 1;
        } catch (IOException exception) {
            player.sendMessage(Text.literal("读取场景失败：" + exception.getMessage()), true);
            return 0;
        }
    }
    public static java.util.List<SceneEditStorage.Scene> sceneEditors(MinecraftServer server, String chartId) throws IOException {
        for (var task : SCENE_EDITOR_AREA_TASKS.values()) {
            if (chartId.equals(task.chartId)) throw new IOException("场景正在重置或生成，请完成后再打开场景编辑菜单");
        }
        return SceneEditStorage.list(server, chartId);
    }
    public static SceneEditStorage.Scene createSceneEditor(MinecraftServer server, String chartId) throws IOException {
        return createSceneEditor(server, chartId, null);
    }
    public static SceneEditStorage.Scene createSceneEditor(MinecraftServer server, String chartId, String iconId) throws IOException {
        return createSceneEditor(server, chartId, iconId, null);
    }
    public static SceneEditStorage.Scene createSceneEditor(MinecraftServer server, String chartId, String iconId, UUID operationPlayerId) throws IOException {
        ChartManifest chart = cachedChart(server, chartId);
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, chartId);
        int index = scenes.stream().mapToInt(scene -> scene.index).max().orElse(0) + 1;
        SceneEditStorage.Scene scene = new SceneEditStorage.Scene(index);
        if (iconId != null && iconId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) scene.icon = iconId;
        scenes.add(scene);
        SceneEditStorage.save(server, chartId, scenes);
        if (chart != null) {
            ServerWorld world = ChartDimensionManager.getOrCreate(server, chart);
            if (operationPlayerId != null) beginSceneOperation(operationPlayerId, "场景正在生成或重置中，期间请勿进行其他操作");
            queueSceneEditorArea(world, scene.x, laneCount(chart), chart.id, scene.index, operationPlayerId, "生成成功");
        }
        return scene;
    }
    public static void saveSceneEditor(MinecraftServer server, String chartId, SceneEditStorage.Scene scene) throws IOException {
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, chartId);
        for (int index = 0; index < scenes.size(); index++) if (scenes.get(index).index == scene.index) scenes.set(index, scene);
        SceneEditStorage.save(server, chartId, scenes);
    }
    public static void installImportedScenes(MinecraftServer server, ChartManifest chart, java.util.List<ImportedScene> imported) throws IOException {
        if (imported == null || imported.isEmpty()) return;
        ChartDimensionManager.key(chart);
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, chart.id);
        java.util.List<SceneImportTask> pending = new java.util.ArrayList<>();
        int nextIndex = scenes.stream().mapToInt(scene -> scene.index).max().orElse(0) + 1;
        ImportedScene initial = imported.get(0);
        if (isCompleteImportedScene(initial)) {
            SceneEditStorage.copySchematic(server, chart.id, 1, initial.schem());
            chart.sceneInitialized = true;
            pending.add(new SceneImportTask(chart, initial.schem(), PLAYBACK_PLATFORM_X, PLAYBACK_PLATFORM_Y, PLAYBACK_PLATFORM_Z));
        }
        for (int sourceIndex = 1; sourceIndex < imported.size(); sourceIndex++) {
            ImportedScene importedScene = imported.get(sourceIndex);
            if (!isCompleteImportedScene(importedScene)) continue;
            int sceneIndex = nextIndex++;
            SceneEditStorage.Scene scene = new SceneEditStorage.Scene(sceneIndex);
            scene.name = "场景" + sceneIndex;
            scene.icon = readSceneIcon(importedScene.info());
            scene.saved = true;
            scenes.add(scene);
            SceneEditStorage.copySchematic(server, chart.id, sceneIndex, importedScene.schem());
            pending.add(new SceneImportTask(chart, importedScene.schem(), scene.x, PLAYBACK_PLATFORM_Y, PLAYBACK_PLATFORM_Z));
        }
        SceneEditStorage.save(server, chart.id, scenes);
        for (SceneImportTask task : pending) SCENE_IMPORT_TASKS.add(task);
        if (!pending.isEmpty()) {
            IMPORTING_CHARTS.add(chart.id);
            LOGGER.info("Queued {} Rhythmc scene imports for chart {}", pending.size(), chart.id);
        }
    }
    public static void installDefaultArenaScenes(MinecraftServer server, ChartManifest chart, java.util.List<String> arenaNames) throws IOException {
        if (chart == null || arenaNames == null || arenaNames.isEmpty()) return;
        ServerWorld world = ChartDimensionManager.getOrCreate(server, chart);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String name : arenaNames) if (name != null && !name.isBlank()) names.add(name.trim());
        if (names.isEmpty()) return;
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, chart.id);
        int nextIndex = scenes.stream().mapToInt(scene -> scene.index).max().orElse(0) + 1;
        for (String arenaName : names) {
            SceneEditStorage.Scene existing = scenes.stream().filter(scene -> arenaName.equals(scene.name)).findFirst().orElse(null);
            if (existing != null || chart.arenaBindings.containsKey(arenaName)) {
                if (existing != null) chart.arenaBindings.put(arenaName, "scene-editor:" + existing.index);
                continue;
            }
            int index = nextIndex++;
            SceneEditStorage.Scene scene = new SceneEditStorage.Scene(index);
            scene.name = arenaName;
            scene.saved = false;
            scenes.add(scene);
            chart.arenaBindings.put(arenaName, "scene-editor:" + index);
            queueSceneEditorArea(world, scene.x, laneCount(chart), chart.id, scene.index);
        }
        SceneEditStorage.save(server, chart.id, scenes);
        saveChart(server, chart);
    }
    private static boolean isCompleteImportedScene(ImportedScene scene) {
        return scene != null && scene.schem() != null && scene.info() != null
                && Files.isRegularFile(scene.schem()) && Files.isRegularFile(scene.info());
    }
    private static String readSceneIcon(Path info) {
        if (info == null || !Files.isRegularFile(info)) return "minecraft:grass_block";
        try {
            for (String line : Files.readAllLines(info)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("icon:")) {
                    String value = trimmed.substring(5).trim().replace("\"", "");
                    if (value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return value;
                }
            }
        } catch (IOException ignored) { }
        return "minecraft:grass_block";
    }
    private static BlockState parseImportedState(String id) {
        try {
            int bracket = id.indexOf('[');
            String blockId = bracket < 0 ? id : id.substring(0, bracket);
            BlockState state = Registries.BLOCK.get(Identifier.of(blockId)).getDefaultState();
            if (bracket < 0 || !id.endsWith("]")) return state;
            String properties = id.substring(bracket + 1, id.length() - 1);
            for (String entry : properties.split(",")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) continue;
                net.minecraft.state.property.Property<?> property = state.getBlock().getStateManager().getProperty(pair[0]);
                if (property != null) state = setImportedProperty(state, property, pair[1]);
            }
            return state;
        } catch (RuntimeException exception) { return Blocks.AIR.getDefaultState(); }
    }
    private static <T extends Comparable<T>> BlockState setImportedProperty(BlockState state, net.minecraft.state.property.Property<T> property, String value) {
        return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
    }
    private static int readSchematicVarInt(byte[] data, int[] cursor) throws IOException {
        int value = 0, shift = 0;
        while (cursor[0] < data.length && shift < 35) {
            int current = data[cursor[0]++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("场景文件 BlockData 损坏");
    }
    private static final class SceneImportTask {
        private final ChartManifest chart;
        private final Path file;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private ServerWorld world;
        private int width;
        private int height;
        private int length;
        private int baseX;
        private int baseY;
        private int baseZ;
        private int clearMinX;
        private int clearMinY;
        private int clearMinZ;
        private int clearWidth;
        private int clearHeight;
        private int clearLength;
        private int clearCursor;
        private int writeCursor;
        private int nonAirCount;
        private int[] positions;
        private BlockState[] states;
        private boolean loaded;
        private SceneImportTask(ChartManifest chart, Path file, int centerX, int centerY, int centerZ) {
            this.chart = chart;
            this.file = file;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
        }
        private boolean process(MinecraftServer server, int budget) throws IOException {
            if (!loaded) load();
            if (world == null) world = ChartDimensionManager.getOrCreate(server, chart);
            while (budget > 0 && clearCursor < clearWidth * clearHeight * clearLength) {
                int local = clearCursor++;
                int localX = clearMinX + local % clearWidth;
                int localZ = clearMinZ + (local / clearWidth) % clearLength;
                int localY = clearMinY + local / (clearWidth * clearLength);
                BlockPos target = new BlockPos(baseX + localX, baseY + localY, baseZ + localZ);
                if (!world.getBlockState(target).isAir()) world.setBlockState(target, Blocks.AIR.getDefaultState(), 2);
                budget--;
            }
            while (budget > 0 && writeCursor < nonAirCount) {
                int local = positions[writeCursor];
                int localX = local % width;
                int localZ = (local / width) % length;
                int localY = local / (width * length);
                BlockPos target = new BlockPos(baseX + localX, baseY + localY, baseZ + localZ);
                BlockState desired = states[writeCursor++];
                if (!world.getBlockState(target).equals(desired)) world.setBlockState(target, desired, 2);
                budget--;
            }
            return clearCursor >= clearWidth * clearHeight * clearLength && writeCursor >= nonAirCount;
        }
        private void load() throws IOException {
            if (file == null || !Files.isRegularFile(file)) throw new IOException("场景文件不存在：" + file);
            NbtCompound root;
            try (InputStream input = Files.newInputStream(file)) {
                root = NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes());
            }
            width = root.getShort("Width", (short) 0);
            height = root.getShort("Height", (short) 0);
            length = root.getShort("Length", (short) 0);
            long totalLong = (long) width * height * length;
            if (width <= 0 || height <= 0 || length <= 0 || width > SCENE_SIZE || height > SCENE_SIZE || length > SCENE_SIZE || totalLong > Integer.MAX_VALUE) {
                throw new IOException("场景文件尺寸无效");
            }
            NbtCompound palette = root.getCompoundOrEmpty("Palette");
            java.util.Map<Integer, BlockState> paletteStates = new java.util.HashMap<>();
            for (String key : palette.getKeys()) {
                int paletteIndex = palette.getInt(key, -1);
                if (paletteIndex < 0) throw new IOException("场景文件 Palette 索引无效");
                paletteStates.put(paletteIndex, parseImportedState(key));
            }
            if (paletteStates.isEmpty()) throw new IOException("场景文件缺少 Palette");
            byte[] data = root.getByteArray("BlockData").orElseThrow(() -> new IOException("场景文件缺少 BlockData"));
            int total = (int) totalLong;
            int[] decodedPositions = new int[total];
            BlockState[] decodedStates = new BlockState[total];
            int[] cursor = {0};
            int minLocalX = width;
            int minLocalY = height;
            int minLocalZ = length;
            int maxLocalX = -1;
            int maxLocalY = -1;
            int maxLocalZ = -1;
            for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
                int paletteIndex = readSchematicVarInt(data, cursor);
                BlockState state = paletteStates.get(paletteIndex);
                if (state == null) throw new IOException("场景文件包含无效 Palette 索引：" + paletteIndex);
                int flat = (y * length + z) * width + x;
                if (state.isAir()) continue;
                decodedPositions[nonAirCount] = flat;
                decodedStates[nonAirCount++] = state;
                minLocalX = Math.min(minLocalX, x);
                minLocalY = Math.min(minLocalY, y);
                minLocalZ = Math.min(minLocalZ, z);
                maxLocalX = Math.max(maxLocalX, x);
                maxLocalY = Math.max(maxLocalY, y);
                maxLocalZ = Math.max(maxLocalZ, z);
            }
            if (cursor[0] != data.length) throw new IOException("场景文件 BlockData 长度不匹配");
            positions = java.util.Arrays.copyOf(decodedPositions, nonAirCount);
            states = java.util.Arrays.copyOf(decodedStates, nonAirCount);
            baseX = centerX - width / 2;
            baseY = centerY - height / 2;
            baseZ = centerZ - length / 2;
            if (nonAirCount == 0) {
                clearMinX = clearMinY = clearMinZ = clearWidth = clearHeight = clearLength = 0;
            } else {
                clearMinX = minLocalX;
                clearMinY = minLocalY;
                clearMinZ = minLocalZ;
                clearWidth = maxLocalX - minLocalX + 1;
                clearHeight = maxLocalY - minLocalY + 1;
                clearLength = maxLocalZ - minLocalZ + 1;
            }
            loaded = true;
            LOGGER.info("Prepared Rhythmc scene import {}: {}x{}x{}, non-air {}", file.getFileName(), width, height, length, nonAirCount);
        }
    }
    private static int resetSceneEditorCommand(net.minecraft.server.command.ServerCommandSource source, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) { player.sendMessage(Text.literal("当前没有活动谱面"), true); return 0; }
        resetSceneEditor(source.getServer(), chart, index, player.getUuid());
        source.sendFeedback(() -> Text.literal("场景重置任务已开始，完成后才能继续编辑"), false);
        return 1;
    }
    private static int deleteSceneEditorCommand(net.minecraft.server.command.ServerCommandSource source, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null) { player.sendMessage(Text.literal("当前没有活动谱面"), true); return 0; }
        if (index <= 1) { player.sendMessage(Text.literal("初始场景不能删除"), true); return 0; }
        deleteSceneEditor(source.getServer(), chart, index);
        source.sendFeedback(() -> Text.literal("场景已删除，后续场景已前移补位"), false);
        return 1;
    }
    private static int saveSceneEditorCommand(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Integer index = ACTIVE_SCENE_EDITORS.get(player.getUuid());
        if (index == null) { player.sendMessage(Text.literal("当前不在场景搭建模式"), true); return 0; }
        if (SCENE_CAPTURE_PENDING.contains(player.getUuid())) { player.sendMessage(Text.literal("场景正在保存，请稍候"), true); return 0; }
        String chartId = ACTIVE_SCENE_CHARTS.get(player.getUuid());
        ChartManifest chart;
        try { chart = chartId == null ? null : cachedChart(source.getServer(), chartId); }
        catch (IOException exception) { chart = null; }
        if (chart == null) {
            ACTIVE_SCENE_EDITORS.remove(player.getUuid());
            ACTIVE_SCENE_CHARTS.remove(player.getUuid());
            restoreSceneHotbar(player);
            player.sendMessage(Text.literal("当前没有活动谱面"), true);
            return 0;
        }
        try {
            java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(source.getServer(), chart.id);
            SceneEditStorage.Scene scene = scenes.stream().filter(value -> value.index == index).findFirst().orElse(null);
            if (scene == null) {
                ACTIVE_SCENE_EDITORS.remove(player.getUuid());
                ACTIVE_SCENE_CHARTS.remove(player.getUuid());
                restoreSceneHotbar(player);
                player.sendMessage(Text.literal("场景不存在"), true);
                return 0;
            }
            if (!(player.getEntityWorld() instanceof ServerWorld world)) return 0;
            queueSceneCapture(world, chart.id, scene.index, scene.x, player.getUuid(), true);
            player.sendMessage(Text.literal("正在保存场景结构…"), true);
            return 1;
        } catch (IOException exception) {
            ACTIVE_SCENE_EDITORS.remove(player.getUuid());
            ACTIVE_SCENE_CHARTS.remove(player.getUuid());
            player.sendMessage(Text.literal("保存场景失败：" + exception.getMessage()), true);
            return 0;
        }
    }
    private static void saveSceneHotbar(ServerPlayerEntity player) {
        SAVED_SCENE_INVENTORIES.computeIfAbsent(player.getUuid(), ignored -> {
            java.util.List<ItemStack> hotbar = new java.util.ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) hotbar.add(player.getInventory().getStack(slot).copy());
            return hotbar;
        });
        for (int slot = 0; slot < 9; slot++) player.getInventory().setStack(slot, ItemStack.EMPTY);
    }
    private static void restoreSceneHotbar(ServerPlayerEntity player) {
        java.util.List<ItemStack> hotbar = SAVED_SCENE_INVENTORIES.remove(player.getUuid());
        if (hotbar == null) return;
        for (int slot = 0; slot < Math.min(9, hotbar.size()); slot++) player.getInventory().setStack(slot, hotbar.get(slot).copy());
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void queueSceneCapture(ServerWorld world, String chartId, int sceneIndex, int centerX, UUID playerId, boolean returnToEditor) {
        if (playerId != null && !SCENE_CAPTURE_PENDING.add(playerId)) return;
        SCENE_CAPTURE_TASKS.offer(new SceneCaptureTask(world, chartId, sceneIndex, centerX, playerId, returnToEditor));
    }
    public static void resetSceneEditor(MinecraftServer server, ChartManifest chart, int index) {
        resetSceneEditor(server, chart, index, null);
    }
    public static void resetSceneEditor(MinecraftServer server, ChartManifest chart, int index, UUID operationPlayerId) {
        try {
            SceneEditStorage.Scene scene = SceneEditStorage.list(server, chart.id).stream().filter(value -> value.index == index).findFirst().orElse(null);
            if (scene == null) {
                if (operationPlayerId != null) completeSceneOperation(operationPlayerId, "重置场景失败：场景不存在");
                return;
            }
            ServerWorld world = ChartDimensionManager.getOrCreate(server, chart);
            SceneEditStorage.deleteSchematic(server, chart.id, index);
            if (operationPlayerId != null) beginSceneOperation(operationPlayerId, "场景正在生成或重置中，期间请勿进行其他操作");
            queueSceneEditorArea(world, scene.x, laneCount(chart), chart.id, index, operationPlayerId, "重置成功");
        } catch (IOException exception) { LOGGER.warn("Failed to reset scene editor {}", index, exception); }
    }

    public static boolean isSceneOperationBusy(UUID playerId) {
        return playerId != null && SCENE_OPERATION_STATUS.containsKey(playerId);
    }

    public static String sceneOperationStatus(UUID playerId) {
        return playerId == null ? null : SCENE_OPERATION_STATUS.get(playerId);
    }

    public static String consumeSceneOperationNotice(UUID playerId) {
        return playerId == null ? null : SCENE_OPERATION_NOTICES.remove(playerId);
    }

    private static void beginSceneOperation(UUID playerId, String status) {
        SCENE_OPERATION_NOTICES.remove(playerId);
        SCENE_OPERATION_STATUS.put(playerId, status);
    }

    private static void completeSceneOperation(UUID playerId, String message) {
        if (playerId == null) return;
        SCENE_OPERATION_STATUS.remove(playerId);
        SCENE_OPERATION_NOTICES.put(playerId, message);
    }
    private static void deleteSceneEditor(MinecraftServer server, ChartManifest chart, int index) {
        try {
            java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, chart.id);
            if (index <= 1 || index > scenes.size()) return;
            ServerWorld world = ChartDimensionManager.getOrCreate(server, chart);
            int removedPosition = index - 1;
            SceneEditStorage.deleteSchematic(server, chart.id, index);
            for (int sourceIndex = index + 1; sourceIndex <= scenes.size(); sourceIndex++) {
                SceneEditStorage.moveSchematic(server, chart.id, sourceIndex, sourceIndex - 1);
            }
            for (int sourcePosition = removedPosition + 1; sourcePosition < scenes.size(); sourcePosition++) {
                moveSceneArea(world, scenes.get(sourcePosition).x, scenes.get(sourcePosition - 1).x);
            }
            clearSceneEditorArea(world, scenes.get(scenes.size() - 1).x);
            scenes.remove(removedPosition);
            for (int position = 0; position < scenes.size(); position++) {
                SceneEditStorage.Scene scene = scenes.get(position);
                scene.index = position + 1;
                if (scene.initial) scene.x = PLAYBACK_PLATFORM_X;
                else scene.x = SCENE_EDIT_BASE_X + (position - 1) * SCENE_EDIT_SPACING;
            }
            SceneEditStorage.save(server, chart.id, scenes);
        } catch (IOException exception) { LOGGER.warn("Failed to delete scene editor {}", index, exception); }
    }
    private static void moveSceneArea(ServerWorld world, int fromCenterX, int toCenterX) {
        BlockState[] states = new BlockState[SCENE_SIZE * SCENE_SIZE * SCENE_SIZE];
        int cursor = 0;
        for (int localX = -SCENE_SIZE / 2; localX < SCENE_SIZE / 2; localX++) {
            for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y++) {
                for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) {
                    states[cursor++] = world.getBlockState(new BlockPos(fromCenterX + localX, y, z));
                }
            }
        }
        clearSceneEditorArea(world, toCenterX);
        cursor = 0;
        for (int localX = -SCENE_SIZE / 2; localX < SCENE_SIZE / 2; localX++) {
            for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y++) {
                for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) {
                    BlockState state = states[cursor++];
                    if (!state.isAir()) world.setBlockState(new BlockPos(toCenterX + localX, y, z), state, 3);
                }
            }
        }
        clearSceneEditorArea(world, fromCenterX);
    }    private static double totalChartSeconds(ChartManifest chart) {
        if (chart.durationSeconds > 0.0 && Double.isFinite(chart.durationSeconds)) return chart.durationSeconds;
        if (chart.totalBeats > 0.0 && chart.bpm > 0.0) return cn.frkovo.rhythmcmaker.chart.ChartTiming.beatToSeconds(chart, chart.totalBeats);
        return Math.max(1, chart.chunkCount) * 60.0 / Math.max(1.0, chart.bpm);
    }
    private static void restoreSelectedStartChunk(ServerWorld world, ChartManifest chart, int selectedChunk) {
        clearSelectedStartChunkColumn(world, chart, selectedChunk);
    }
    private static void markSelectedStartChunk(ServerWorld world, ChartManifest chart, int selectedChunk) {
        clearSelectedStartChunkColumn(world, chart, selectedChunk);
        int z = (int) Math.round(PlaybackCoordinates.editorWorldZAtBeat(chart, Math.max(0, selectedChunk - 1.0)));
        int left = laneWallLeftX(chart);
        int right = laneWallRightX(chart);
        for (int x = left; x <= right; x++) for (int y = 64; y <= 68; y++) {
            if (x != left && x != right && y != 64) continue;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!isNoteBlock(state.getBlock())) world.setBlockState(pos, Blocks.LIME_CONCRETE.getDefaultState(), 3);
        }
    }
    private static void clearSelectedStartChunkColumn(ServerWorld world, ChartManifest chart, int selectedChunk) {
        int z = (int) Math.round(PlaybackCoordinates.editorWorldZAtBeat(chart, Math.max(0, selectedChunk - 1.0)));
        for (int x = laneWallLeftX(chart); x <= laneWallRightX(chart); x++) for (int y = 64; y <= 68; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isOf(Blocks.LIME_CONCRETE)) world.setBlockState(pos, Blocks.RED_CONCRETE.getDefaultState(), 3);
        }
    }
    private static void clearSelectedStartChunkMarkers(ServerWorld world, ChartManifest chart) {
        int length = Math.max(Math.max(1, chart.trackLength), calculateTrackLength(chart));
        for (int slot = 0; slot <= length; slot++) {
            int z = (int) Math.round(PlaybackCoordinates.editorWorldZAtBeat(chart, slot));
            for (int x = laneWallLeftX(chart); x <= laneWallRightX(chart); x++) for (int y = 64; y <= 68; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (world.getBlockState(pos).isOf(Blocks.LIME_CONCRETE)) world.setBlockState(pos, Blocks.RED_CONCRETE.getDefaultState(), 3);
            }
        }
    }
        private static void buildPlaybackArea(ServerWorld world, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int wallHalfWidth = laneHalfWidth + 1;
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int x = -maximumHalfWidth; x <= maximumHalfWidth; x++) for (int y = 65; y <= 69; y++) for (int z = -2; z <= 2; z++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, y, PLAYBACK_PLATFORM_Z + z), Blocks.AIR.getDefaultState(), 3);
        }
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) for (int z = -2; z <= 2; z++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, 65, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
        }
        world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X, 65, PLAYBACK_PLATFORM_Z), Blocks.VERDANT_FROGLIGHT.getDefaultState(), 3);
        for (int y = 66; y <= 67; y++) {
            for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, y, PLAYBACK_PLATFORM_Z + 2), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X - wallHalfWidth, y, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
                world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + wallHalfWidth, y, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            }
        }
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, 68, PLAYBACK_PLATFORM_Z + 2), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        for (int z = -1; z <= 1; z++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X - wallHalfWidth, 68, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + wallHalfWidth, 68, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int x = -laneHalfWidth; x <= laneHalfWidth; x++) for (int z = -1; z <= 1; z++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, 69, PLAYBACK_PLATFORM_Z + z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, 65, PLAYBACK_PLATFORM_Z - 2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + x, 69, PLAYBACK_PLATFORM_Z - 2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        for (int y = 66; y <= 68; y++) {
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X - wallHalfWidth, y, PLAYBACK_PLATFORM_Z - 2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(PLAYBACK_PLATFORM_X + wallHalfWidth, y, PLAYBACK_PLATFORM_Z - 2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
    }
    private static void buildSceneEditorStructure(ServerWorld world, int centerX, int laneCount) {
        int laneHalfWidth = Math.max(0, laneCount / 2);
        int wallHalfWidth = laneHalfWidth + 1;
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) for (int z = -2; z <= 2; z++) {
            world.setBlockState(new BlockPos(centerX + x, 65, z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
        }
        world.setBlockState(new BlockPos(centerX, 65, 0), Blocks.VERDANT_FROGLIGHT.getDefaultState(), 3);
        for (int y = 66; y <= 67; y++) {
            for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) world.setBlockState(new BlockPos(centerX + x, y, 2), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(new BlockPos(centerX - wallHalfWidth, y, z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
                world.setBlockState(new BlockPos(centerX + wallHalfWidth, y, z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            }
        }
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) world.setBlockState(new BlockPos(centerX + x, 68, 2), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        for (int z = -1; z <= 1; z++) {
            world.setBlockState(new BlockPos(centerX - wallHalfWidth, 68, z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
            world.setBlockState(new BlockPos(centerX + wallHalfWidth, 68, z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int x = -laneHalfWidth; x <= laneHalfWidth; x++) for (int z = -1; z <= 1; z++) {
            world.setBlockState(new BlockPos(centerX + x, 69, z), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int x = -wallHalfWidth; x <= wallHalfWidth; x++) {
            world.setBlockState(new BlockPos(centerX + x, 65, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(centerX + x, 69, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        for (int y = 66; y <= 68; y++) {
            world.setBlockState(new BlockPos(centerX - wallHalfWidth, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(centerX + wallHalfWidth, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
    }
    private static void queueSceneEditorArea(ServerWorld world, int centerX, int laneCount) {
        SCENE_EDITOR_AREA_TASKS.put(new SceneEditorAreaKey(world.getRegistryKey(), centerX), new SceneEditorAreaTask(centerX, laneCount, null, -1, null, null));
    }
    private static void queueSceneEditorArea(ServerWorld world, int centerX, int laneCount, String chartId, int sceneIndex) {
        queueSceneEditorArea(world, centerX, laneCount, chartId, sceneIndex, null, null);
    }
    private static void queueSceneEditorArea(ServerWorld world, int centerX, int laneCount, String chartId, int sceneIndex, UUID operationPlayerId, String operationSuccessMessage) {
        SCENE_EDITOR_AREA_TASKS.put(new SceneEditorAreaKey(world.getRegistryKey(), centerX), new SceneEditorAreaTask(centerX, laneCount, chartId, sceneIndex, operationPlayerId, operationSuccessMessage));
    }
    private static void clearSceneEditorArea(ServerWorld world, int centerX) {
        for (int x = centerX - SCENE_SIZE / 2; x < centerX + SCENE_SIZE / 2; x++) {
            for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y++) {
                for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }
    private static void processSceneEditorAreaTasks(MinecraftServer server) {
        for (var iterator = SCENE_EDITOR_AREA_TASKS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerWorld world = server.getWorld(entry.getKey().worldKey());
            if (world == null) { iterator.remove(); continue; }
            SceneEditorAreaTask task = entry.getValue();
            int budget = 4096;
            int total = SCENE_SIZE * SCENE_SIZE * SCENE_SIZE;
            while (budget-- > 0 && task.clearCursor < total) {
                int local = task.clearCursor++;
                int localX = -SCENE_SIZE / 2 + local % SCENE_SIZE;
                int localZ = SCENE_EDIT_MIN_Z + (local / SCENE_SIZE) % SCENE_SIZE;
                int localY = SCENE_EDIT_MIN_Y + local / (SCENE_SIZE * SCENE_SIZE);
                BlockPos target = new BlockPos(task.centerX + localX, localY, localZ);
                if (!world.getBlockState(target).isAir()) world.setBlockState(target, Blocks.AIR.getDefaultState(), 2);
            }
            if (task.clearCursor < total) continue;
            if (!task.structureBuilt) {
                if (task.centerX == PLAYBACK_PLATFORM_X) buildPlaybackArea(world, task.laneCount);
                else buildSceneEditorStructure(world, task.centerX, task.laneCount);
                task.structureBuilt = true;
            }
            iterator.remove();
            completeSceneOperation(task.operationPlayerId, task.operationSuccessMessage);
            if (task.chartId != null && task.sceneIndex > 0) SCENE_CAPTURE_TASKS.offer(new SceneCaptureTask(world, task.chartId, task.sceneIndex, task.centerX, null, false));
            for (var pending : PENDING_SCENE_TELEPORTS.entrySet()) {
                if (pending.getValue() != task.sceneIndex) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.getKey());
                if (player == null || task.chartId == null) continue;
                try {
                    ChartManifest chart = cachedChart(server, task.chartId);
                    SceneEditStorage.Scene scene = chart == null ? null : SceneEditStorage.list(server, chart.id).stream().filter(value -> value.index == task.sceneIndex).findFirst().orElse(null);
                    if (chart != null && scene != null) {
                        saveSceneHotbar(player);
                        ACTIVE_SCENE_EDITORS.put(player.getUuid(), scene.index);
                        ACTIVE_SCENE_CHARTS.put(player.getUuid(), chart.id);
                        player.teleport(world, scene.x + 0.5, 66.0, 0.5, Set.of(), 0.0f, 0.0f, false);
                        player.getInventory().setStack(0, menuStack(SAVE_SCENE_ITEM));
                        player.currentScreenHandler.sendContentUpdates();
                        player.sendMessage(Text.literal("已进入 " + scene.name + " 搭建场地，右键保存场景后返回"), true);
                    }
                } catch (IOException exception) {
                    player.sendMessage(Text.literal("进入场景失败：" + exception.getMessage()), true);
                }
                PENDING_SCENE_TELEPORTS.remove(pending.getKey(), pending.getValue());
            }
        }
    }

    private static int sceneMinX(int centerX) { return centerX - SCENE_SIZE / 2; }
    private static int sceneMaxX(int centerX) { return centerX + SCENE_SIZE / 2 - 1; }
    private static boolean isInsideSceneArea(ServerPlayerEntity player, int centerX) {
        return player.getX() >= sceneMinX(centerX) && player.getX() < centerX + SCENE_SIZE / 2
                && player.getY() >= SCENE_EDIT_MIN_Y && player.getY() < SCENE_EDIT_MAX_Y + 1.0
                && player.getZ() >= SCENE_EDIT_MIN_Z && player.getZ() < SCENE_EDIT_MAX_Z + 1.0;
    }
    private static void processSceneCaptureTasks(MinecraftServer server) {
        int pendingTasks = SCENE_CAPTURE_TASKS.size();
        for (int index = 0; index < pendingTasks; index++) {
            SceneCaptureTask task = SCENE_CAPTURE_TASKS.poll();
            if (task == null) break;
            try {
                if (!task.process()) {
                    SCENE_CAPTURE_TASKS.offer(task);
                    continue;
                }
                java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server, task.chartId);
                SceneEditStorage.Scene scene = scenes.stream().filter(value -> value.index == task.sceneIndex).findFirst().orElse(null);
                if (scene == null) throw new IOException("场景已不存在");
                scene.saved = true;
                SceneEditStorage.save(server, task.chartId, scenes);
                finishSceneCapture(server, task, null);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Failed to capture scene {} for chart {}", task.sceneIndex, task.chartId, exception);
                finishSceneCapture(server, task, exception);
            }
        }
    }
    private static void finishSceneCapture(MinecraftServer server, SceneCaptureTask task, Exception failure) {
        if (task.playerId == null) return;
        SCENE_CAPTURE_PENDING.remove(task.playerId);
        ACTIVE_SCENE_EDITORS.remove(task.playerId);
        ACTIVE_SCENE_CHARTS.remove(task.playerId);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.playerId);
        if (player == null) return;
        restoreSceneHotbar(player);
        if (failure == null) {
            if (task.returnToEditor) {
                player.teleport(task.world, EDITOR_SPAWN.getX(), EDITOR_SPAWN.getY(), EDITOR_SPAWN.getZ(), Set.of(), 0.0f, 0.0f, false);
            }
            player.sendMessage(Text.literal(task.returnToEditor ? "场景结构已保存，已返回制谱器轨道" : "场景结构已保存"), true);
        } else {
            player.sendMessage(Text.literal("场景结构保存失败：" + failure.getMessage()), true);
        }
    }
    private static void queueSceneCaptureOnDisconnect(MinecraftServer server, ServerPlayerEntity player) {
        Integer sceneIndex = ACTIVE_SCENE_EDITORS.get(player.getUuid());
        String chartId = ACTIVE_SCENE_CHARTS.get(player.getUuid());
        if (sceneIndex == null || sceneIndex == 1 || chartId == null || SCENE_CAPTURE_PENDING.contains(player.getUuid()) || !(player.getEntityWorld() instanceof ServerWorld)) return;
        try {
            ChartManifest chart = cachedChart(server, chartId);
            if (chart == null) return;
            SceneEditStorage.Scene scene = SceneEditStorage.list(server, chart.id).stream().filter(value -> value.index == sceneIndex).findFirst().orElse(null);
            if (scene != null) queueSceneCapture(ChartDimensionManager.getOrCreate(server, chart), chart.id, scene.index, scene.x, null, false);
        } catch (IOException exception) {
            LOGGER.warn("Failed to queue scene save on disconnect", exception);
        }
    }
    private static String blockStateId(BlockState state) {
        StringBuilder value = new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
        java.util.Collection<Property<?>> properties = state.getProperties();
        if (!properties.isEmpty()) {
            value.append('[');
            int index = 0;
            for (Property<?> property : properties) {
                if (index++ > 0) value.append(',');
                appendBlockStateProperty(value, state, property);
            }
            value.append(']');
        }
        return value.toString();
    }
    private static <T extends Comparable<T>> void appendBlockStateProperty(StringBuilder target, BlockState state, Property<T> property) {
        target.append(property.getName()).append('=').append(property.name(state.get(property)));
    }
    private static void writeSchematicVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }
    private static boolean isWorldEditSelectionAxe(ItemStack stack) {
        return stack != null && stack.isOf(Items.WOODEN_AXE);
    }

    private static void registerOptionalWorldEditIntegration() {
        try {
            WorldEditIntegration.register();
        } catch (LinkageError exception) {
            LOGGER.warn("WorldEdit is unavailable; optional integration is disabled", exception);
        }
    }

    public enum WorldEditWriteTarget {
        DENIED,
        TRACK_NOTE,
        SCENE
    }

    public static WorldEditWriteTarget worldEditWriteTarget(UUID playerId, int x, int y, int z, String blockId) {
        MinecraftServer server = activeServer;
        if (server == null || playerId == null || blockId == null) return WorldEditWriteTarget.DENIED;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return WorldEditWriteTarget.DENIED;
        if (rejectEditorTrackAdjustment(player)) return WorldEditWriteTarget.DENIED;
        ChartManifest chart = loadActiveChart(player, server);
        if (chart == null) return WorldEditWriteTarget.DENIED;
        Integer activeScene = ACTIVE_SCENE_EDITORS.get(playerId);
        String activeChart = ACTIVE_SCENE_CHARTS.get(playerId);
        if (activeScene != null) {
            if (!chart.id.equals(activeChart)) return WorldEditWriteTarget.DENIED;
            try {
                SceneEditStorage.Scene scene = SceneEditStorage.list(server, chart.id).stream()
                        .filter(value -> value.index == activeScene)
                        .findFirst().orElse(null);
                return scene != null
                        && x >= sceneMinX(scene.x) && x <= sceneMaxX(scene.x)
                        && y >= SCENE_EDIT_MIN_Y && y <= SCENE_EDIT_MAX_Y
                        && z >= SCENE_EDIT_MIN_Z && z <= SCENE_EDIT_MAX_Z
                        ? WorldEditWriteTarget.SCENE : WorldEditWriteTarget.DENIED;
            } catch (IOException exception) {
                LOGGER.warn("Failed to validate WorldEdit scene edit", exception);
                return WorldEditWriteTarget.DENIED;
            }
        }
        if (!isWorldEditTrackBlock(blockId) || !isValidNotePosition(new BlockPos(x, y, z), chart, world)) return WorldEditWriteTarget.DENIED;
        int type = worldEditNoteType(blockId);
        return type < 0 || isAllowedNoteTypeAtHeight(y, type)
                ? WorldEditWriteTarget.TRACK_NOTE : WorldEditWriteTarget.DENIED;
    }

    public static void syncWorldEditTrackNote(UUID playerId, int x, int y, int z, String blockId) {
        MinecraftServer server = activeServer;
        if (server == null || playerId == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return;
        ChartManifest chart = loadActiveChart(player, server);
        if (chart == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (chart.notes == null) chart.notes = new java.util.ArrayList<>();
        chart.notes.removeIf(note -> isBlockNoteAt(note, pos));
        int type = worldEditNoteType(blockId);
        if (type >= 0) {
            ChartManifest.Note note = new ChartManifest.Note();
            note.id = UUID.randomUUID().toString();
            note.type = type;
            note.x = x;
            note.y = y;
            note.z = z;
            note.beat = PlaybackCoordinates.editorBeatAtWorldZ(chart, z);
            note.time = ChartTiming.beatToSeconds(chart, note.beat);
            chart.notes.add(note);
        }
        chart.totalBeats = chart.notes.stream().filter(java.util.Objects::nonNull)
                .mapToDouble(note -> note.beat).max().orElse(0.0);
        try { saveChart(server, chart); } catch (IOException exception) { LOGGER.warn("Failed to sync WorldEdit note", exception); }
    }

    private static boolean isWorldEditTrackBlock(String blockId) {
        return "minecraft:air".equals(blockId) || "minecraft:cave_air".equals(blockId)
                || "minecraft:void_air".equals(blockId) || worldEditNoteType(blockId) >= 0;
    }

    private static int worldEditNoteType(String blockId) {
        return switch (blockId) {
            case "minecraft:tnt" -> 0;
            case "minecraft:observer" -> 1;
            case "minecraft:diamond_block" -> 2;
            case "minecraft:redstone_block" -> 3;
            default -> -1;
        };
    }
    private static SceneEditStorage.Scene sceneAtPosition(ServerWorld world, BlockPos pos, ChartManifest chart) {
        try {
            for (SceneEditStorage.Scene scene : SceneEditStorage.list(world.getServer(), chart.id)) {
                if (pos.getX() >= sceneMinX(scene.x) && pos.getX() <= sceneMaxX(scene.x)
                        && pos.getY() >= SCENE_EDIT_MIN_Y && pos.getY() <= SCENE_EDIT_MAX_Y
                        && pos.getZ() >= SCENE_EDIT_MIN_Z && pos.getZ() <= SCENE_EDIT_MAX_Z) return scene;
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to inspect scene editor areas", exception);
        }
        return null;
    }
private static void showSceneBoundary(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) continue;
            Integer activeIndex = ACTIVE_SCENE_EDITORS.get(player.getUuid());
            String chartId = ACTIVE_SCENE_CHARTS.get(player.getUuid());
            if (activeIndex == null || chartId == null) continue;
            int centerX;
            int laneCount;
            try {
                ChartManifest chart = cachedChart(server, chartId);
                if (chart == null) continue;
                SceneEditStorage.Scene scene = SceneEditStorage.list(server, chart.id).stream()
                    .filter(value -> value.index == activeIndex).findFirst().orElse(null);
                if (scene == null) continue;
                centerX = scene.x;
                laneCount = Math.max(1, Math.min(9, chart.laneCount));
            } catch (IOException exception) {
                continue;
            }
            int minX = sceneMinX(centerX);
            int maxX = sceneMaxX(centerX);
            int particleStep = Math.max(1, SCENE_BORDER_STEP);
            for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y += particleStep) {
                for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z += particleStep) {
                    showAlternatingSceneParticle(world, player, minX, y, z, y / particleStep + z / particleStep);
                    showAlternatingSceneParticle(world, player, maxX, y, z, y / particleStep + z / particleStep);
                }
            }
            for (int x = minX; x <= maxX; x += particleStep) {
                for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z += particleStep) {
                    showAlternatingSceneParticle(world, player, x, SCENE_EDIT_MIN_Y, z, x / particleStep + z / particleStep);
                    showAlternatingSceneParticle(world, player, x, SCENE_EDIT_MAX_Y, z, x / particleStep + z / particleStep);
                }
            }
            for (int x = minX; x <= maxX; x += particleStep) {
                for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y += particleStep) {
                    showAlternatingSceneParticle(world, player, x, y, SCENE_EDIT_MIN_Z, x / particleStep + y / particleStep);
                    showAlternatingSceneParticle(world, player, x, y, SCENE_EDIT_MAX_Z, x / particleStep + y / particleStep);
                }
            }
            showParticle(world, player, SCENE_CENTER_GLOW, centerX, PLAYBACK_PLATFORM_Y, PLAYBACK_PLATFORM_Z);
            showPlaybackFrame(world, player, laneCount, centerX);
        }
    }
    private static void showAlternatingSceneParticle(ServerWorld world, ServerPlayerEntity player, int x, int y, int z, int pattern) {
        showSceneParticle(world, player, (pattern & 1) == 0 ? SCENE_BORDER_GLOW : SCENE_BORDER_WHITE_GLOW, x, y, z);
    }    private static void showPlaybackFrame(ServerWorld world, ServerPlayerEntity player, int laneCount) {
        showPlaybackFrame(world, player, laneCount, PLAYBACK_PLATFORM_X);
    }
    private static void showPlaybackFrame(ServerWorld world, ServerPlayerEntity player, int laneCount, int centerX) {
        int laneHalfWidth = Math.max(0, laneCount / 2);
        int left = centerX - laneHalfWidth;
        int right = centerX + laneHalfWidth;
        for (int x = left; x <= right; x++) for (int y = 66; y <= 68; y++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, y, -2);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, y, 1);
        }
        for (int y = 66; y <= 68; y++) for (int z = -2; z <= 1; z++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, left, y, z);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, right, y, z);
        }
        for (int x = left; x <= right; x++) for (int z = -2; z <= 1; z++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, 66, z);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, 68, z);
        }
        for (int x = left; x <= right; x++) for (int y = 65; y <= 68; y++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, y, -3);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, y, -30);
        }
        for (int y = 65; y <= 68; y++) for (int z = -30; z <= -3; z++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, left, y, z);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, right, y, z);
        }
        for (int x = left; x <= right; x++) for (int z = -30; z <= -3; z++) {
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, 65, z);
            showParticle(world, player, PLAYBACK_FRAME_GLOW, x, 68, z);
        }
    }
    private static void showSceneParticle(ServerWorld world, ServerPlayerEntity player, DustParticleEffect effect, int x, int y, int z) {
        showParticle(world, player, effect, x, y, z, 128.0);
    }
    private static void showParticle(ServerWorld world, ServerPlayerEntity player, DustParticleEffect effect, int x, int y, int z) {
        showParticle(world, player, effect, x, y, z, 64.0);
    }
    private static void showParticle(ServerWorld world, ServerPlayerEntity player, DustParticleEffect effect, int x, int y, int z, double maximumDistance) {
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
        double dx = player.getX() - px, dy = player.getY() - py, dz = player.getZ() - pz;
        if (dx * dx + dy * dy + dz * dz > maximumDistance * maximumDistance) return;
        world.spawnParticles(player, effect, false, false, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
    }
    private static int stopPlaybackCommand(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!hasPlaybackSession(player.getUuid())) return 0;
        stopPlayback(source.getServer(), player, true);
        return 1;
    }
    private static int togglePlayback(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return togglePlayback(source, -1, "formal", 1.0); }
    private static int togglePlayback(net.minecraft.server.command.ServerCommandSource source, int requestedStartChunk) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return togglePlayback(source, requestedStartChunk, "formal", 1.0); }
    private static int togglePlayback(net.minecraft.server.command.ServerCommandSource source, int requestedStartChunk, String requestedMode, double requestedRate) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (ACTIVE_SCENE_EDITORS.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("场景搭建期间不能播放谱面"), true);
            return 0;
        }
        if (hasPlaybackSession(player.getUuid())) {
            stopPlayback(source.getServer(), player, true);
            return 1;
        }
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world) || chart.bpm <= 0) return 0;
        int startChunk = requestedStartChunk > 0 ? Math.max(1, Math.min(Math.max(1, chart.chunkCount), requestedStartChunk)) : selectedStartChunk(player, chart);
        double divisionsPerChunk = Math.max(1.0, Math.min(32.0, chart.divisionsPerChunk));
        double startBeat = (startChunk - 1.0) * divisionsPerChunk;
        String mode = "scroll".equalsIgnoreCase(requestedMode) ? "scroll" : "formal";
        double rate = normalizePlaybackRate(requestedRate);
        double startSeconds = ChartTiming.beatToSeconds(chart, startBeat);
        long startNanos = System.nanoTime() + PLAYBACK_START_DELAY_TICKS * 50_000_000L;
        if ("scroll".equals(mode)) {
            SCROLL_JUDGEMENT_SESSIONS.put(player.getUuid(), new ScrollJudgementSession(world, chart, startSeconds, startNanos, rate));
        } else {
            PlaybackSession session = new PlaybackSession(world, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), chart, startSeconds, startNanos, mode, rate);
            PLAYBACK_SESSIONS.put(player.getUuid(), session);
            player.teleport(world, PLAYBACK_PLATFORM_X + 0.5, PLAYBACK_PLATFORM_Y + 1.0, PLAYBACK_PLATFORM_Z + 0.5, Set.of(), 180.0f, 0.0f, false);
        }
        player.sendMessage(Text.literal("开始" + ("scroll".equals(mode) ? "滚动" : "正式") + "播放：Chunk " + startChunk + "，" + rate + "x"), true);
        return 1;
    }
    private static void processPlaybackSessions(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlaybackSession session = PLAYBACK_SESSIONS.get(player.getUuid());
            if (session == null) continue;
            try {
            if (System.nanoTime() < session.startNanos) continue;
            double songTime = session.startSeconds + (System.nanoTime() - session.startNanos) / 1_000_000_000.0 * session.rate;
            boolean updateDisplayPositions = session.shouldUpdateDisplayPositions(server.getTicks());
            session.playedTapSoundThisTick = false;
            session.playedLookSoundThisTick = false;
            session.updateClientTime(player);
            if (session.formal) {
                while (session.nextEffectIndex < session.effects.size()) {
                    JsonObject effect = session.effects.get(session.nextEffectIndex);
                    if (session.effectSeconds(effect) > songTime) break;
                    session.nextEffectIndex++;
                    playTrackEffect(session, player, effect);
                }
            }
            if (session.formal) {
                int preloadedThisTick = 0;
                for (var iterator = session.displays.entrySet().iterator(); iterator.hasNext();) {
                    var entry = iterator.next();
                    ChartManifest.Note note = session.notesById.get(entry.getKey());
                    DisplayEntity.BlockDisplayEntity display = entry.getValue();
                    if (note == null || session.shouldHide(note)) { session.recycle(display); iterator.remove(); continue; }
                    display.setGlowing(true);
                    double noteTime = session.noteTimes.getOrDefault(note.id, Double.POSITIVE_INFINITY);
                    if (noteTime <= songTime) {
                        if (session.hitNotes.add(note.id)) playHitSound(session, player, note);
                        session.recycle(display); iterator.remove(); continue;
                    }
                    double relativeDistance = session.relativeDistance(note, songTime);
                    double displayZ = Math.min(PLAYBACK_DISAPPEAR_Z, PLAYBACK_FRAME_Z - relativeDistance);
                    if (updateDisplayPositions) {
                        display.setTeleportDuration(1);
                        display.setInterpolationDuration(0);
                        double[] position = session.displayPosition(note, songTime, displayZ);
                        display.setPosition(position[0], position[1], position[2]);
                    }
                }
                while (session.nextNoteIndex < session.notes.size()) {
                    if (preloadedThisTick >= PLAYBACK_MAX_PRELOADS_PER_TICK
                            || session.displays.size() >= PLAYBACK_MAX_ACTIVE_DISPLAYS) break;
                    ChartManifest.Note note = session.notes.get(session.nextNoteIndex);
                    double noteTime = session.noteTimes.getOrDefault(note.id, Double.POSITIVE_INFINITY);
                    if (!Double.isFinite(noteTime)) {
                        session.nextNoteIndex++;
                        continue;
                    }
                    if (noteTime - songTime > PLAYBACK_MAX_PRELOAD_SECONDS) break;
                    double relativeDistance = session.relativeDistance(note, songTime);
                    if (!Double.isFinite(relativeDistance) || relativeDistance < 0.0) {
                        session.nextNoteIndex++;
                        continue;
                    }
                    if (relativeDistance > PLAYBACK_APPROACH_DISTANCE) break;
                    session.nextNoteIndex++;
                    if (noteTime <= songTime) {
                        if (session.hitNotes.add(note.id)) playHitSound(session, player, note);
                        continue;
                    }
                    double[] position = session.displayPosition(note, songTime, PLAYBACK_FRAME_Z - Math.max(0.0, relativeDistance));
                    DisplayEntity.BlockDisplayEntity display = session.acquire(blockStateForType(note.type), position[0], position[1], position[2]);
                    if (display == null) continue;
                    display.addCommandTag("rhythmc_preview:" + player.getUuid());
                    display.setGlowing(true);
                    session.displays.put(note.id, display);
                    session.updateGlowTeam();
                    preloadedThisTick++;
                }
            }
            if (songTime >= session.endSeconds) stopPlayback(server, player, session.formal);
                    } catch (RuntimeException exception) {
                LOGGER.error("Playback session failed for {}", player.getUuid(), exception);
                stopPlayback(server, player, session.formal);
            }
        }
    }
    private static void processScrollJudgementSessions(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ScrollJudgementSession session = SCROLL_JUDGEMENT_SESSIONS.get(player.getUuid());
            if (session == null || System.nanoTime() < session.startNanos) continue;
            double songTime = session.startSeconds + (System.nanoTime() - session.startNanos) / 1_000_000_000.0 * session.rate;
            session.playedTapSoundThisTick = false;
            session.playedLookSoundThisTick = false;
            int processed = 0;
            while (session.nextNoteIndex < session.notes.size()
                    && session.noteTimes.get(session.nextNoteIndex) <= songTime
                    && processed++ < PLAYBACK_MAX_PRELOADS_PER_TICK) {
                ChartManifest.Note note = session.notes.get(session.nextNoteIndex++);
                playHitSound(session, player, note);
            }
            if (songTime >= session.endSeconds) stopPlayback(server, player, false);
        }
    }
    private static double normalizePlaybackRate(double value) {
        double[] choices = {0.25, 0.5, 0.75, 1.0, 1.5, 2.0};
        double nearest = choices[0];
        for (double choice : choices) if (Math.abs(choice - value) < Math.abs(nearest - value)) nearest = choice;
        return nearest;
    }
    private static void playHitSound(PlaybackSession session, ServerPlayerEntity player, ChartManifest.Note note) {
        if (note.type == 3) return;
        net.minecraft.sound.SoundEvent sound;
        float pitch;
        if (note.type == 2) {
            if (session.playedLookSoundThisTick) return;
            session.playedLookSoundThisTick = true;
            sound = net.minecraft.sound.SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON;
            pitch = 1.0f;
        } else {
            if (session.playedTapSoundThisTick) return;
            session.playedTapSoundThisTick = true;
            // RhythMC 3.0 uses the configurable note-box sound for TAP and LOOK.
            sound = net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value();
            pitch = 0.5f;
        }
        session.world.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
            net.minecraft.sound.SoundCategory.PLAYERS, (float) Math.max(0.1, Math.min(5.0, config.noteJudgementVolumeMultiplier)), pitch);
    }
    private static void playHitSound(ScrollJudgementSession session, ServerPlayerEntity player, ChartManifest.Note note) {
        if (note.type == 3) return;
        net.minecraft.sound.SoundEvent sound;
        float pitch;
        if (note.type == 2) {
            if (session.playedLookSoundThisTick) return;
            session.playedLookSoundThisTick = true;
            sound = net.minecraft.sound.SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON;
            pitch = 1.0f;
        } else {
            if (session.playedTapSoundThisTick) return;
            session.playedTapSoundThisTick = true;
            sound = net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value();
            pitch = 0.5f;
        }
        session.world.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
            net.minecraft.sound.SoundCategory.PLAYERS, (float) Math.max(0.1, Math.min(5.0, config.noteJudgementVolumeMultiplier)), pitch);
    }
    private static void playTrackEffect(PlaybackSession session, ServerPlayerEntity player, JsonObject effect) {
        if (effect == null) return;
        String type = effectType(effect);
        String text = effectString(effect, "text", effectString(effect, "content", ""));
        int color = effectColor(effect);
        float scale = effectFloat(effect, "scale", 1.8f);
        int count = Math.max(1, Math.min(64, effectInt(effect, "count", 12)));
        double x = effectDouble(effect, "x", PLAYBACK_PLATFORM_X + 0.5);
        double y = effectDouble(effect, "y", PLAYBACK_PLATFORM_Y + 1.5);
        double z = effectDouble(effect, "z", PLAYBACK_PLATFORM_Z + 0.5);
        switch (type) {
            case "GLOW_COLOR" -> session.applyGlowColor(effect);
            case "HIDE_NOTES" -> session.updateHiddenNotes(effect);
            case "TITLE", "ACTIONBAR", "MESSAGE" -> { if (!text.isBlank()) player.sendMessage(Text.literal(text), "CHAT".equalsIgnoreCase(effectString(effect, "type", "ACTIONBAR")) ? false : true); }
            case "TEXT_DISPLAY", "TEXT_DISPLAY_EFFECT", "TEXT_DISPLAY_SYNC_TRACK", "TEXT_DISPLAY_DESYNC_TRACK", "TEXT_DISPLAY_REMOVE", "HOLOGRAM", "REMOVE_HOLOGRAM" -> session.applyTextEffect(effect);
            case "EFFECT" -> session.applyPotionProxy(player, effect);
            case "CLEAR_EFFECT" -> player.clearStatusEffects();
            case "TIME" -> session.applyTime(player, effect);
            case "WEATHER" -> session.applyWeather(effect);
            case "ARENA", "CHANGE_ARENA" -> session.applyArena(effect);
            case "FIREWORK" -> session.launchFirework(x, y, z);
            default -> LOGGER.warn("Ignoring unsupported RhythMC effect '{}' instead of substituting a particle placeholder", type);
        }
    }
    private static String effectType(JsonObject effect) {
        return effectString(effect, "eventType", effectString(effect, "type", "PARTICLE")).trim().toUpperCase(java.util.Locale.ROOT);
    }
    private static String effectString(JsonObject effect, String key, String fallback) {
        try { JsonObject properties = effectProperties(effect); return effect != null && effect.has(key) ? effect.get(key).getAsString() : properties.has(key) ? properties.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double effectDouble(JsonObject effect, String key, double fallback) {
        try { JsonObject properties = effectProperties(effect); return effect != null && effect.has(key) ? effect.get(key).getAsDouble() : properties.has(key) ? properties.get(key).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static Formatting effectFormatting(JsonObject effect, String key, Formatting fallback) {
        Formatting formatting = Formatting.byName(effectString(effect, key, "white").toLowerCase(java.util.Locale.ROOT));
        return formatting == null || !formatting.isColor() ? fallback : formatting;
    }
    private static int effectColor(JsonObject effect) {
        if (effect == null || !effect.has("color")) return 0xB77BFF;
        try {
            if (effect.get("color").isJsonPrimitive() && effect.get("color").getAsJsonPrimitive().isNumber()) return effect.get("color").getAsInt();
            String value = effect.get("color").getAsString().trim();
            if (value.isBlank()) return 0xB77BFF;
            return switch (value.toUpperCase(java.util.Locale.ROOT)) {
                case "RED" -> 0xFF0000;
                case "GREEN" -> 0x00FF00;
                case "BLUE" -> 0x0000FF;
                case "YELLOW" -> 0xFFFF00;
                case "WHITE" -> 0xFFFFFF;
                case "BLACK" -> 0x000000;
                case "PURPLE" -> 0x800080;
                case "CYAN" -> 0x00FFFF;
                case "ORANGE" -> 0xFFA500;
                default -> Integer.decode(value);
            };
        } catch (RuntimeException ignored) {
            return 0xB77BFF;
        }
    }
    private static int effectInt(JsonObject effect, String key, int fallback) {
        try { JsonObject properties = effectProperties(effect); return effect != null && effect.has(key) ? effect.get(key).getAsInt() : properties.has(key) ? properties.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static float effectFloat(JsonObject effect, String key, float fallback) {
        try { JsonObject properties = effectProperties(effect); return effect != null && effect.has(key) ? effect.get(key).getAsFloat() : properties.has(key) ? properties.get(key).getAsFloat() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean effectBoolean(JsonObject effect, String key, boolean fallback) {
        try { JsonObject properties = effectProperties(effect); return effect != null && effect.has(key) ? effect.get(key).getAsBoolean() : properties.has(key) ? properties.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static JsonObject effectProperties(JsonObject effect) { return effect != null && effect.has("properties") && effect.get("properties").isJsonObject() ? effect.getAsJsonObject("properties") : new JsonObject(); }
    private static void stopPlayback(MinecraftServer server, ServerPlayerEntity player, boolean returnPlayer) {
        ScrollJudgementSession scrollSession = SCROLL_JUDGEMENT_SESSIONS.remove(player.getUuid());
        player.clearStatusEffects();
        if (scrollSession != null) return;
        PlaybackSession session = PLAYBACK_SESSIONS.remove(player.getUuid());
        if (session == null) return;
        try {
            session.resetClientTime(player);
            session.clearGlowTeam();
            for (DisplayEntity.BlockDisplayEntity display : session.displays.values()) display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            for (DisplayEntity.BlockDisplayEntity display : session.displayPool) display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            session.displays.clear();
            session.displayPool.clear();
            String tag = "rhythmc_preview:" + player.getUuid();
            for (DisplayEntity.BlockDisplayEntity display : session.world.getEntitiesByType(EntityType.BLOCK_DISPLAY,
                    entity -> entity.getCommandTags().contains(tag))) {
                display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to clean playback displays for {}", player.getUuid(), exception);
        } finally {
            if (returnPlayer && session.formal) {
                try {
                    player.teleport(session.world, session.returnX, session.returnY, session.returnZ, Set.of(), session.returnYaw, session.returnPitch, false);
                    player.sendMessage(Text.literal("播放结束"), true);
                } catch (RuntimeException exception) {
                    LOGGER.error("Failed to return player {} after playback", player.getUuid(), exception);
                }
            }
        }
    }
    private static boolean hasPlaybackSession(UUID playerId) {
        return PLAYBACK_SESSIONS.containsKey(playerId) || SCROLL_JUDGEMENT_SESSIONS.containsKey(playerId);
    }
    private static final class ScrollJudgementSession {
        private final ServerWorld world;
        private final double startSeconds;
        private final long startNanos;
        private final double rate;
        private final java.util.List<ChartManifest.Note> notes;
        private final java.util.List<Double> noteTimes;
        private final double endSeconds;
        private int nextNoteIndex;
        private boolean playedTapSoundThisTick;
        private boolean playedLookSoundThisTick;

        private ScrollJudgementSession(ServerWorld world, ChartManifest chart, double startSeconds, long startNanos, double rate) {
            this.world = world;
            this.startSeconds = startSeconds;
            this.startNanos = startNanos;
            this.rate = rate;
            this.notes = new java.util.ArrayList<>(chart.notes == null ? java.util.List.of() : chart.notes);
            this.notes.removeIf(note -> note == null || note.id == null);
            this.notes.sort(java.util.Comparator.comparingDouble(note -> effectiveNoteTime(note, chart)));
            this.noteTimes = new java.util.ArrayList<>(notes.size());
            for (ChartManifest.Note note : notes) noteTimes.add(effectiveNoteTime(note, chart));
            this.endSeconds = playbackEndTime(chart);
            while (nextNoteIndex < noteTimes.size() && noteTimes.get(nextNoteIndex) < startSeconds) nextNoteIndex++;
        }
    }
    private static void cleanupOrphanedPlaybackDisplays(ServerWorld world) {
        for (DisplayEntity.BlockDisplayEntity display : world.getEntitiesByType(EntityType.BLOCK_DISPLAY,
                entity -> entity.getCommandTags().contains("rhythmc_preview"))) {
            boolean active = PLAYBACK_SESSIONS.values().stream().anyMatch(session -> session.world == world
                    && (session.displays.containsValue(display) || session.displayPool.contains(display)));
            if (!active) display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }
    private static final class PlaybackSession {
        private final ServerWorld world;
        private final double returnX, returnY, returnZ;
        private final float returnYaw, returnPitch;
        private final ChartManifest chart;
        private final ChartTiming.Prepared timing;

        private final Map<Integer, TrackPlayback.Prepared> trackProfiles = new java.util.HashMap<>();
        private final double startSeconds;
        private final long startNanos;
        private final boolean formal;
        private final double rate;
        private final java.util.List<ChartManifest.Note> notes;
        private final java.util.List<JsonObject> effects;
        private final Set<Integer> hiddenNoteTypes = new java.util.HashSet<>();
        private final Set<Integer> hiddenTracks = new java.util.HashSet<>();
        private final Map<Integer, Team> glowTeams = new java.util.HashMap<>();
        private Formatting observerGlowColor = Formatting.WHITE;
        private Long clientTime;
        private long clientTimeExpiresAtNanos;
        private final Map<String, ChartManifest.Note> notesById = new java.util.HashMap<>();
        private final Map<String, Double> noteTimes = new java.util.HashMap<>();

        private final double endSeconds;
        private int nextNoteIndex;
        private final Map<String, DisplayEntity.BlockDisplayEntity> displays = new java.util.HashMap<>();
        private final java.util.ArrayDeque<DisplayEntity.BlockDisplayEntity> displayPool = new java.util.ArrayDeque<>();
        private final java.util.Set<String> hitNotes = new java.util.HashSet<>();
        private int nextEffectIndex;
        private long lastDisplayUpdateTick = Long.MIN_VALUE;

        private boolean playedTapSoundThisTick;
        private boolean playedLookSoundThisTick;
        private PlaybackSession(ServerWorld world, double returnX, double returnY, double returnZ, float returnYaw, float returnPitch, ChartManifest chart, double startSeconds, long startNanos, String mode, double rate) {
            this.world = world;
            this.returnX = returnX;
            this.returnY = returnY;
            this.returnZ = returnZ;
            this.returnYaw = returnYaw;
            this.returnPitch = returnPitch;
            this.chart = chart;
            this.timing = ChartTiming.prepare(chart);

            if (chart.tracks != null) for (ChartManifest.Track track : chart.tracks) if (track != null) trackProfiles.put(track.id, TrackPlayback.prepare(track, chart.speedEvents));
            trackProfiles.computeIfAbsent(0, ignored -> TrackPlayback.prepare(null, chart.speedEvents));
            this.startSeconds = startSeconds;
            this.startNanos = startNanos;
            this.formal = !"scroll".equalsIgnoreCase(mode);
            this.rate = rate;
            this.notes = new java.util.ArrayList<>(chart.notes == null ? java.util.List.of() : chart.notes);
            this.effects = new java.util.ArrayList<>(chart.effects == null ? java.util.List.of() : chart.effects);
            this.effects.sort(java.util.Comparator.comparingDouble(this::effectSeconds));
            this.notes.removeIf(note -> note == null || note.id == null);
            this.notes.sort(java.util.Comparator.comparingDouble(note -> note.time));
            for (ChartManifest.Note note : this.notes) {
                notesById.put(note.id, note);
                double noteTime = effectiveNoteTime(note, chart);
                noteTimes.put(note.id, noteTime);

            }
            this.endSeconds = calculateEndSeconds();
            while (nextNoteIndex < notes.size() && noteTimes.getOrDefault(notes.get(nextNoteIndex).id, Double.POSITIVE_INFINITY) < startSeconds) {
                hitNotes.add(notes.get(nextNoteIndex).id);
                nextNoteIndex++;
            }
            while (nextEffectIndex < effects.size() && effectSeconds(effects.get(nextEffectIndex)) < startSeconds) nextEffectIndex++;
            double firstDistance = nextNoteIndex < notes.size() ? relativeDistance(notes.get(nextNoteIndex), startSeconds) : Double.NaN;
            LOGGER.info("Playback preload initialized: chart={}, notes={}, nextNoteIndex={}, firstDistance={}, activeDisplays={}",
                    chart.id, notes.size(), nextNoteIndex, firstDistance, displays.size());
        }
        private void applyVisualState() {
            for (var iterator = displays.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                ChartManifest.Note note = notesById.get(entry.getKey());
                if (note != null && shouldHide(note)) {
                    recycle(entry.getValue());
                    iterator.remove();
                }
            }
            updateGlowTeam();
        }        private boolean shouldHide(ChartManifest.Note note) {
            return hiddenNoteTypes.contains(note.type) || hiddenTracks.contains(note.trackId);
        }
        private void updateHiddenNotes(JsonObject effect) {
            hiddenNoteTypes.clear();
            hiddenTracks.clear();
            if (effect == null) return;
            if (effect.has("noteTypes") && effect.get("noteTypes").isJsonArray()) {
                for (var value : effect.getAsJsonArray("noteTypes")) {
                    try {
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                            hiddenNoteTypes.add(value.getAsInt());
                        } else {
                            String type = value.getAsString().toUpperCase(java.util.Locale.ROOT);
                            hiddenNoteTypes.add(switch (type) {
                                case "LOOK" -> 1;
                                case "HOLD" -> 2;
                                case "DODGE" -> 3;
                                default -> 0;
                            });
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            if (effect.has("tracks") && effect.get("tracks").isJsonArray()) {
                for (var value : effect.getAsJsonArray("tracks")) {
                    try { hiddenTracks.add(value.getAsInt()); } catch (RuntimeException ignored) { }
                }
            }
        }
        private void updateGlowTeam() {
            for (var entry : displays.entrySet()) {
                DisplayEntity.BlockDisplayEntity display = entry.getValue();
                ChartManifest.Note note = notesById.get(entry.getKey());
                Team team = glowTeams.computeIfAbsent(note == null ? 0 : note.type, this::createGlowTeam);
                display.setGlowing(true);
                if (world.getScoreboard().getScoreHolderTeam(display.getNameForScoreboard()) != team) {
                    world.getScoreboard().addScoreHolderToTeam(display.getNameForScoreboard(), team);
                }
            }
        }
        private Team createGlowTeam(int type) {
            String suffix = type == 3 ? "red" : type == 2 ? "diamond" : type == 1 ? "observer" : "white";
            String name = "rhythmc_preview_glow_" + suffix;
            Team team = world.getScoreboard().getTeam(name);
            if (team == null) team = world.getScoreboard().addTeam(name);
            team.setColor(type == 3 ? Formatting.RED : type == 2 ? Formatting.AQUA : type == 1 ? observerGlowColor : Formatting.WHITE);
            return team;
        }
        private void applyGlowColor(JsonObject effect) {
            observerGlowColor = effectFormatting(effect, "color", Formatting.WHITE);
            Team observerTeam = glowTeams.get(1);
            if (observerTeam != null) observerTeam.setColor(observerGlowColor);
            updateGlowTeam();
        }
        private void clearGlowTeam() {
            for (DisplayEntity.BlockDisplayEntity display : displays.values()) {
                world.getScoreboard().clearTeam(display.getNameForScoreboard());
                display.setGlowing(false);
            }
            for (Team team : glowTeams.values()) if (world.getScoreboard().getTeam(team.getName()) == team) world.getScoreboard().removeTeam(team);
            glowTeams.clear();
        }
        private void applyPotionProxy(ServerPlayerEntity player, JsonObject effect) {
            String type = effectString(effect, "type", effectString(effect, "effect", effectString(effect, "potion", "UNKNOWN")));
            if (type.isBlank() || type.equalsIgnoreCase("UNKNOWN")) {
                LOGGER.warn("Skipping RhythMC potion effect without a type: {}", effect);
                return;
            }
            try {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(type.contains(":") ? type : "minecraft:" + type.toLowerCase(java.util.Locale.ROOT));
                var entry = net.minecraft.registry.Registries.STATUS_EFFECT.getEntry(id).orElse(null);
                if (entry == null) {
                    LOGGER.warn("Unknown RhythMC potion effect '{}': {}", type, effect);
                    return;
                }
                int amplifier = Math.max(0, Math.min(255, effectInt(effect, "amplifier", effectInt(effect, "level", 0))));
                int duration = Math.max(1, effectInt(effect, "duration", 100));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(entry, duration, amplifier,
                        effectBoolean(effect, "ambient", false), effectBoolean(effect, "particles", true), true));
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to apply RhythMC potion effect '{}'", type, exception);
            }
        }
        private void applyTime(ServerPlayerEntity player, JsonObject effect) {
            long time = Math.max(0L, (long) effectDouble(effect, "time", effectDouble(effect, "timeOfDay", effectDouble(effect, "worldTime", effectDouble(effect, "value", world.getTimeOfDay())))));
            boolean clientOnly = effectBoolean(effect, "client", true);
            if (!clientOnly) {
                resetClientTime(player);
                world.setTimeOfDay(time);
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket(
                        world.getTime(), time, world.getGameRules().getValue(GameRules.ADVANCE_TIME)));
                return;
            }
            clientTime = time;
            double durationMillis = effectDouble(effect, "duration", Double.POSITIVE_INFINITY);
            double elapsedMillis = Math.max(0.0, (System.nanoTime() - startNanos) / 1_000_000.0 * rate
                    + (startSeconds - effectSeconds(effect)) * 1000.0);
            double remainingNanos = Math.max(0.0, durationMillis - elapsedMillis) * 1_000_000.0 / rate;
            long now = System.nanoTime();
            clientTimeExpiresAtNanos = remainingNanos >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + (long) remainingNanos;
            updateClientTime(player);
        }
        private void updateClientTime(ServerPlayerEntity player) {
            if (clientTime == null) return;
            if (System.nanoTime() >= clientTimeExpiresAtNanos) {
                resetClientTime(player);
                return;
            }
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket(
                    world.getTime(), clientTime, false));
        }
        private void resetClientTime(ServerPlayerEntity player) {
            if (clientTime == null) return;
            clientTime = null;
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket(
                    world.getTime(), world.getTimeOfDay(), world.getGameRules().getValue(GameRules.ADVANCE_TIME)));
        }
        private void applyWeather(JsonObject effect) {
            String weather = effectString(effect, "weather", effectString(effect, "type", "clear")).toLowerCase(java.util.Locale.ROOT);
            boolean raining = weather.contains("rain") || weather.contains("storm");
            world.setWeather(0, raining ? 6000 : 0, raining, weather.contains("storm"));
        }
        private double calculateEndSeconds() {
            double end = chart.durationSeconds + chart.offsetMillis / 1000.0;
            for (double noteTime : noteTimes.values()) if (Double.isFinite(noteTime)) end = Math.max(end, noteTime);
            for (JsonObject effect : effects) end = Math.max(end, effectSeconds(effect));
            return Math.max(0.0, end) + 0.1;
        }
        private double effectSeconds(JsonObject effect) {
            if (effect == null || !effect.has("beat")) return Double.POSITIVE_INFINITY;
            try {
                return timing.beatToSeconds(effect.get("beat").getAsDouble()) + chart.offsetMillis / 1000.0;
            } catch (RuntimeException ignored) {
                return Double.POSITIVE_INFINITY;
            }
        }

        private double relativeDistance(ChartManifest.Note note, double songTime) {
            double beat = PlaybackCoordinates.beatAtSongTime(chart, timing, songTime);
            TrackPlayback.Prepared track = trackProfiles.getOrDefault(note.trackId, trackProfiles.get(0));
            return PlaybackCoordinates.distanceBetweenBeats(chart, track, beat, note.beat, config.playerSpeed);
        }
        private double[] displayPosition(ChartManifest.Note note, double songTime, double displayZ) {
            double beat = timing.secondsToBeat(songTime - chart.offsetMillis / 1000.0);
            TrackPlayback.Pose pose = trackProfiles.getOrDefault(note.trackId, trackProfiles.get(0)).poseAt(beat);
            double localX = (note.sourceX == null ? note.x : note.sourceX) * pose.scaleX();
            double localY = (note.sourceY == null ? note.y - 66.0 : note.sourceY) * pose.scaleY();
            double radians = Math.toRadians(pose.rotationZ());
            double rotatedX = localX * Math.cos(radians) - localY * Math.sin(radians);
            double rotatedY = localX * Math.sin(radians) + localY * Math.cos(radians);
            return new double[]{PLAYBACK_PLATFORM_X + rotatedX + pose.x(), PLAYBACK_PLATFORM_Y + 1.0 + rotatedY + pose.y(), displayZ - pose.z()};
        }
        private void applyTextEffect(JsonObject effect) { String value = effectString(effect, "text", effectString(effect, "content", "")); if (!value.isBlank()) LOGGER.info("RhythMC text effect: {}", value); }
        private void applyArena(JsonObject effect) { String arena = effectString(effect, "arena", ""); if (!arena.isBlank()) LOGGER.info("RhythMC arena effect requested: {}", arena); }
        private void launchFirework(double x, double y, double z) { world.playSound(null, x, y, z, net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f); }

        private boolean shouldUpdateDisplayPositions(long serverTick) {
            if (lastDisplayUpdateTick != Long.MIN_VALUE
                    && serverTick < lastDisplayUpdateTick + PLAYBACK_DISPLAY_UPDATE_INTERVAL_TICKS) return false;
            lastDisplayUpdateTick = serverTick;
            return true;
        }
        private DisplayEntity.BlockDisplayEntity acquire(BlockState state, double x, double y, double z) {
            DisplayEntity.BlockDisplayEntity display = displayPool.pollFirst();
            boolean fresh = display == null || display.isRemoved();
            if (fresh) {
                display = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
                if (display == null) return null;
            }
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.setInvisible(true);
            display.setBlockState(state);
            display.setTeleportDuration(0);
            display.setInterpolationDuration(0);
            display.setPosition(x, y, z);
            if (fresh) {
                display.addCommandTag("rhythmc_preview");
                world.spawnEntity(display);
            }
            // Spawn at the final position before revealing the entity to prevent origin flashes.
            display.setInvisible(false);
            display.setTeleportDuration(1);
            return display;
        }
        private void recycle(DisplayEntity.BlockDisplayEntity display) {
            if (display.isRemoved()) return;
            display.setInvisible(true);
            display.setTeleportDuration(0);
            display.setInterpolationDuration(0);
            display.setPosition(PLAYBACK_PLATFORM_X, -64.0, PLAYBACK_PLATFORM_Z);
            displayPool.addLast(display);
        }
    }
    private static double playbackEndTime(ChartManifest chart) {
        double end = chart.durationSeconds + chart.offsetMillis / 1000.0;
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) {
            if (note != null) end = Math.max(end, effectiveNoteTime(note, chart));
        }
        return Math.max(0.0, end) + 0.1;
    }
    private static double effectiveNoteTime(ChartManifest.Note note, ChartManifest chart) { return note.time + chart.offsetMillis / 1000.0; }
    private static ItemStack menuStack(Item item) {
        return new ItemStack(item);
    }
    private static boolean isNoteItem(ItemStack stack) {
        return stack.isOf(Blocks.OBSERVER.asItem()) || stack.isOf(Blocks.REDSTONE_BLOCK.asItem()) || stack.isOf(Blocks.DIAMOND_BLOCK.asItem()) || stack.isOf(Blocks.TNT.asItem());
    }
    private static boolean isNoteBlock(net.minecraft.block.Block block) {
        return block == Blocks.OBSERVER || block == Blocks.REDSTONE_BLOCK || block == Blocks.DIAMOND_BLOCK || block == Blocks.TNT;
    }
    private static boolean isValidNotePosition(BlockPos pos, ChartManifest chart, ServerWorld world) {
        if (chart == null) return false;
        int x = pos.getX();
        int z = -pos.getZ() - 3;
        return x >= laneMinX(chart) && x <= laneMaxX(chart) && pos.getY() >= 65 && pos.getY() <= 68 && z >= 0 && z < calculateTrackLength(chart);
    }
    private static boolean isAllowedNoteTypeAtHeight(int y, int type) {
        return type >= 0 && (y == 65 ? type == 2 : y >= 66 && y <= 68 && type != 2);
    }
    private static int noteType(ItemStack stack) {
        if (stack.isOf(Blocks.OBSERVER.asItem())) return 1;
        if (stack.isOf(Blocks.DIAMOND_BLOCK.asItem())) return 2;
        if (stack.isOf(Blocks.REDSTONE_BLOCK.asItem())) return 3;
        return 0;
    }
    private static boolean containsNote(ChartManifest chart, ServerWorld world, BlockPos pos) {
        return chart.notes != null && chart.notes.stream().anyMatch(note -> isBlockNoteAt(note, pos));
    }

    private static boolean isBlockNoteAt(ChartManifest.Note note, BlockPos pos) {
        return !hasFraction(note.x) && !hasFraction(note.y) && !hasFraction(note.z)
            && Math.round(note.x) == pos.getX()
            && Math.round(note.y) == pos.getY()
            && Math.round(note.z) == pos.getZ();
    }
    private static void removeNote(ChartManifest chart, ServerWorld world, BlockPos pos) {
        chart.notes.removeIf(note -> {
            // Fractional notes are represented by BlockDisplay entities and can visually overlap
            // a block grid position. Breaking a newly placed block must not delete that note.
            boolean matches = isBlockNoteAt(note, pos);
            if (matches) removeNoteDisplay(world, noteTag(chart.id, note.id));
            return matches;
        });
        try { saveChart(world.getServer(), chart); } catch (IOException ignored) { }
    }
    private static void repositionNotes(ChartManifest chart) {
        if (chart.notes == null) return;
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        for (ChartManifest.Note note : chart.notes) {
            double beat = Double.isFinite(note.beat) ? Math.max(0.0, note.beat) : timing.secondsToBeat(note.time);
            note.beat = beat;
            note.time = timing.beatToSeconds(beat);
            note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, beat);
        }
    }
    private static Map<String, BlockPos> captureNotePositions(ChartManifest chart) {
        Map<String, BlockPos> positions = new java.util.HashMap<>();
        if (chart.notes == null) return positions;
        for (ChartManifest.Note note : chart.notes) {
            if (note.id == null || hasFraction(note.x) || hasFraction(note.y) || hasFraction(note.z)) continue;
            positions.put(note.id, BlockPos.ofFloored(note.x, note.y, note.z));
        }
        return positions;
    }
    private static void clearNoteBlocks(ServerWorld world, Map<String, BlockPos> positions) {
        for (BlockPos pos : positions.values()) {
            if (isNoteBlock(world.getBlockState(pos).getBlock())) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }
    private static void clearChartNoteBlocks(ServerWorld world, ChartManifest chart) {
        clearNoteBlocks(world, captureNotePositions(chart));
    }
    private static void cleanupStaleNoteDisplays(ServerWorld world, ChunkPos chunkPos) {
        ChartManifest chart = chartForWorld(world);
        if (chart == null) return;
        List<? extends DisplayEntity.BlockDisplayEntity> displays = world.getEntitiesByType(EntityType.BLOCK_DISPLAY,
                entity -> entity.getChunkPos().equals(chunkPos));
        if (displays.isEmpty()) return;
        String notePrefix = "rhythmc_note:" + chart.id + ":";
        Set<String> validTags = new java.util.HashSet<>();
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) {
            if (note != null && note.id != null && !note.id.isBlank()) validTags.add(noteTag(chart.id, note.id));
        }
        String generationTag = noteDisplayGenerationTag(chart.id, noteDisplayGeneration(chart));
        for (DisplayEntity.BlockDisplayEntity display : displays) {
            boolean taggedNote = display.getCommandTags().stream().anyMatch(tag -> tag.startsWith("rhythmc_note:"));
            boolean currentChartNote = display.getCommandTags().stream().anyMatch(tag -> tag.startsWith(notePrefix));
            boolean stale = currentChartNote && display.getCommandTags().stream()
                    .anyMatch(tag -> tag.startsWith(notePrefix) && !validTags.contains(tag));
            boolean oldGeneration = currentChartNote && !display.getCommandTags().contains(generationTag);
            boolean foreignChartNote = taggedNote && !currentChartNote && isWithinEditorTrackCleanupArea(display, chart);
            boolean legacyTrackDisplay = !taggedNote && isWithinEditorTrackCleanupArea(display, chart);
            if (stale || oldGeneration || foreignChartNote || legacyTrackDisplay) {
                display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            }
        }
    }
    private static boolean isWithinEditorTrackCleanupArea(DisplayEntity.BlockDisplayEntity display, ChartManifest chart) {
        int maximumHalfWidth = Math.max(MAX_EDITOR_LANE_COUNT / 2 + 1, chart.noteDisplayCleanupLaneCount / 2 + 1);
        int cleanupLength = Math.max(chart.trackLength, chart.noteDisplayCleanupTrackLength);
        return display.getX() >= -maximumHalfWidth && display.getX() <= maximumHalfWidth
                && display.getY() >= 65.0 && display.getY() <= 68.0
                && display.getZ() >= -3.0 - Math.max(0, cleanupLength) && display.getZ() <= -3.0;
    }
    private static void scheduleNoteDisplayChunkCleanup(ServerWorld world, ChunkPos chunkPos, boolean releaseForcedChunk) {
        NOTE_DISPLAY_CHUNK_CLEANUP_TASKS.add(new NoteDisplayChunkCleanup(
                world.getRegistryKey(), chunkPos, 2, releaseForcedChunk));
    }
    private static void processNoteDisplayChunkCleanupTasks(MinecraftServer server) {
        int budget = 32;
        for (var iterator = NOTE_DISPLAY_CHUNK_CLEANUP_TASKS.iterator(); iterator.hasNext() && budget-- > 0;) {
            NoteDisplayChunkCleanup task = iterator.next();
            ServerWorld world = server.getWorld(task.worldKey());
            if (world != null) {
                cleanupStaleNoteDisplays(world, task.chunkPos());
                if (task.retriesRemaining() > 0) {
                    NOTE_DISPLAY_CHUNK_CLEANUP_TASKS.add(new NoteDisplayChunkCleanup(
                            task.worldKey(), task.chunkPos(), task.retriesRemaining() - 1, task.releaseForcedChunk()));
                } else if (task.releaseForcedChunk()) {
                    world.setChunkForced(task.chunkPos().x, task.chunkPos().z, false);
                }
            }
            iterator.remove();
        }
    }
    private static void scheduleNoteDisplaySweep(ServerWorld world, ChartManifest chart) {
        int maximumHalfWidth = Math.max(MAX_EDITOR_LANE_COUNT / 2 + 1, chart.noteDisplayCleanupLaneCount / 2 + 1);
        int minimumX = -maximumHalfWidth;
        int maximumX = maximumHalfWidth;
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) {
            if (note == null || !Double.isFinite(note.x)) continue;
            int x = (int) Math.floor(note.x);
            minimumX = Math.min(minimumX, x);
            maximumX = Math.max(maximumX, x);
        }
        int cleanupLength = Math.max(chart.trackLength, chart.noteDisplayCleanupTrackLength);
        int minimumChunkX = Math.floorDiv(minimumX, 16);
        int maximumChunkX = Math.floorDiv(maximumX, 16);
        int minimumChunkZ = Math.floorDiv(-3 - Math.max(0, cleanupLength), 16);
        int maximumChunkZ = Math.floorDiv(-3, 16);
        EDITOR_NOTE_DISPLAY_SWEEP_TASKS.put(world.getRegistryKey(), new EditorNoteDisplaySweepTask(
                world.getRegistryKey(), chart.id, minimumChunkX, maximumChunkX, minimumChunkZ, maximumChunkZ));
    }
    private static void processEditorNoteDisplaySweepTasks(MinecraftServer server) {
        final int chunkBudget = 2;
        for (var iterator = EDITOR_NOTE_DISPLAY_SWEEP_TASKS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            EditorNoteDisplaySweepTask task = entry.getValue();
            ServerWorld world = server.getWorld(task.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }
            int remaining = chunkBudget;
            while (remaining-- > 0 && task.hasNext()) {
                ChunkPos chunkPos = task.nextChunk();
                world.setChunkForced(chunkPos.x, chunkPos.z, true);
                world.getChunk(chunkPos.x, chunkPos.z);
                scheduleNoteDisplayChunkCleanup(world, chunkPos, true);
            }
            if (task.hasNext()) continue;
            iterator.remove();
        }
    }
    private static ChartManifest chartForWorld(ServerWorld world) {
        if (!isEditorWorld(world)) return null;
        ChartManifest cached = CHART_CACHE_BY_WORLD.get(world.getRegistryKey());
        if (cached != null) return cached;
        try {
            ChartManifest chart = ChartStorage.findByDimensionId(world.getServer(), world.getRegistryKey().getValue().getPath());
            if (chart != null) {
                CHART_CACHE_BY_WORLD.put(world.getRegistryKey(), chart);
                return chart;
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to resolve chart for display cleanup", exception);
        }
        return null;
    }
    private static void clearChartNoteDisplays(ServerWorld world, ChartManifest chart) {
        String prefix = "rhythmc_note:" + chart.id + ":";
        for (DisplayEntity.BlockDisplayEntity display : world.getEntitiesByType(EntityType.BLOCK_DISPLAY, entity -> entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(prefix)))) {
            display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }
    private static void clearAllChartNoteDisplays(ServerWorld world) {
        String prefix = "rhythmc_note:";
        for (DisplayEntity.BlockDisplayEntity display : world.getEntitiesByType(EntityType.BLOCK_DISPLAY, entity -> entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(prefix)))) {
            display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }
    private static void refreshNoteDisplays(ServerWorld world, ChartManifest chart) {
        refreshNoteDisplays(world, chart, chart.trackLength, laneCount(chart));
    }
    private static void refreshNoteDisplays(ServerWorld world, ChartManifest chart, int previousTrackLength, int previousLaneCount) {
        long generation = prepareNoteDisplayGeneration(world, chart, previousTrackLength, previousLaneCount);
        if (generation < 1) return;
        CHART_CACHE_BY_WORLD.put(world.getRegistryKey(), chart);
        scheduleNoteDisplaySweep(world, chart);
        if (chart.notes == null) {
            EDITOR_NOTE_REFRESH_TASKS.remove(world.getRegistryKey());
            removeStaleNoteDisplays(world, chart.id, generation);
            return;
        }
        EDITOR_NOTE_REFRESH_TASKS.put(world.getRegistryKey(), new EditorNoteRefreshTask(chart, generation));
    }
    private static void processEditorNoteRefreshTasks(MinecraftServer server) {
        for (var iterator = EDITOR_NOTE_REFRESH_TASKS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null) {
                iterator.remove();
                continue;
            }
            EditorNoteRefreshTask task = entry.getValue();
            int budget = 64;
            while (budget-- > 0 && task.cursor < task.notes.size()) {
                ChartManifest.Note note = task.notes.get(task.cursor++);
                if (note == null) continue;
                if (!hasFraction(note.x) && !hasFraction(note.y) && !hasFraction(note.z)) {
                    world.setBlockState(BlockPos.ofFloored(note.x, note.y, note.z), blockStateForType(note.type), 3);
                    continue;
                }
                DisplayEntity.BlockDisplayEntity display = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
                if (display == null) continue;
                display.setBlockState(blockStateForType(note.type));
                display.setPosition(note.x, note.y, note.z);
                display.setNoGravity(true);
                display.setInvulnerable(true);
                display.addCommandTag(noteTag(task.chartId, note.id));
                display.addCommandTag(noteDisplayGenerationTag(task.chartId, task.generation));
                world.spawnEntity(display);
            }
            if (task.cursor >= task.notes.size()) {
                removeStaleNoteDisplays(world, task.chartId, task.generation);
                iterator.remove();
            }
        }
    }
    private static final class EditorNoteRefreshTask {
        private final String chartId;
        private final long generation;
        private final java.util.List<ChartManifest.Note> notes;
        private int cursor;
        private EditorNoteRefreshTask(ChartManifest chart, long generation) {
            this.chartId = chart.id;
            this.generation = generation;
            this.notes = new java.util.ArrayList<>(chart.notes == null ? java.util.List.of() : chart.notes);
        }
    }
    private static void removeStaleNoteDisplays(ServerWorld world, String chartId, long generation) {
        String prefix = "rhythmc_note:" + chartId + ":";
        String generationTag = noteDisplayGenerationTag(chartId, generation);
        for (DisplayEntity.BlockDisplayEntity display : world.getEntitiesByType(EntityType.BLOCK_DISPLAY,
                entity -> entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(prefix)))) {
            if (!display.getCommandTags().contains(generationTag)) display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }    private static void removeNoteDisplay(ServerWorld world, String tag) {
        for (DisplayEntity.BlockDisplayEntity display : world.getEntitiesByType(EntityType.BLOCK_DISPLAY, entity -> entity.getCommandTags().contains(tag))) {
            display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }
    private static void refreshEditorSidebar(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(EDITOR_STATUS_OBJECTIVE);
        if (objective != null && scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) == objective) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        }
        if (objective != null) scoreboard.removeObjective(objective);
    }
    private static void setSidebarLine(ServerScoreboard scoreboard, ScoreboardObjective objective, String holderName, String text, int score) {
        var entry = scoreboard.getOrCreateScore(ScoreHolder.fromName(holderName), objective);
        entry.setScore(score);
        entry.setDisplayText(Text.literal(text));
    }
    private static String formatSongTime(double seconds) {
        long centiseconds = Math.max(0L, (long) Math.floor(seconds * 100.0));
        long minutes = centiseconds / 6000;
        return String.format(Locale.ROOT, "%02d:%05.2f", minutes, (centiseconds % 6000) / 100.0);
    }
    private static boolean hasFraction(double value) { return Math.abs(value - Math.rint(value)) > 0.000001; }
    private static String noteTag(String chartId, String noteId) { return "rhythmc_note:" + chartId + ":" + noteId; }
    private static long noteDisplayGeneration(ChartManifest chart) { return Math.max(1L, chart.noteDisplayRevision); }
    private static long prepareNoteDisplayGeneration(ServerWorld world, ChartManifest chart, int previousTrackLength, int previousLaneCount) {
        long previousRevision = chart.noteDisplayRevision;
        int previousCleanupTrackLength = chart.noteDisplayCleanupTrackLength;
        int previousCleanupLaneCount = chart.noteDisplayCleanupLaneCount;
        long nextRevision = Math.max(Math.max(1L, previousRevision + 1L), System.currentTimeMillis());
        chart.noteDisplayRevision = nextRevision;
        chart.noteDisplayCleanupTrackLength = Math.max(Math.max(0, previousTrackLength), Math.max(chart.trackLength, previousCleanupTrackLength));
        chart.noteDisplayCleanupLaneCount = Math.max(Math.max(MIN_EDITOR_LANE_COUNT, previousLaneCount), Math.max(laneCount(chart), previousCleanupLaneCount));
        try {
            saveChart(world.getServer(), chart);
            return nextRevision;
        } catch (IOException exception) {
            chart.noteDisplayRevision = previousRevision;
            chart.noteDisplayCleanupTrackLength = previousCleanupTrackLength;
            chart.noteDisplayCleanupLaneCount = previousCleanupLaneCount;
            LOGGER.error("Failed to persist note display revision for chart {}", chart.id, exception);
            return 0L;
        }
    }
    private static String noteDisplayGenerationTag(String chartId, long generation) { return "rhythmc_note_generation:" + chartId + ":" + generation; }
    private static net.minecraft.block.BlockState blockStateForType(int type) {
        return switch (type) {
            case 1 -> Blocks.OBSERVER.getDefaultState();
            case 2 -> Blocks.DIAMOND_BLOCK.getDefaultState();
            case 3 -> Blocks.REDSTONE_BLOCK.getDefaultState();
            default -> Blocks.TNT.getDefaultState();
        };
    }
    private static void installHubMap(Path worldRoot) {
        Path marker = worldRoot.resolve(HUB_MAP_MARKER);
        if (Files.exists(marker)) return;
        Path regionDirectory = worldRoot.resolve("region");
        Path stagingDirectory = worldRoot.resolve(".rhythmc_maker_hub_map_staging");
        try {
            Files.createDirectories(regionDirectory);
            Files.createDirectories(stagingDirectory);
            for (String region : HUB_MAP_REGIONS) {
                String resourcePath = "data/" + MOD_ID + "/charter_map/region/" + region;
                try (InputStream input = RhythmcMaker.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (input == null) throw new IOException("Missing embedded hub map resource: " + resourcePath);
                    Files.copy(input, stagingDirectory.resolve(region), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            for (String region : HUB_MAP_REGIONS) {
                Files.move(stagingDirectory.resolve(region), regionDirectory.resolve(region), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.deleteIfExists(stagingDirectory);
            Files.createFile(marker);
            LOGGER.info("Installed Rhythmc maker hub map after server shutdown");
        } catch (IOException exception) {
            LOGGER.error("Failed to install Rhythmc maker hub map after server shutdown", exception);
        }
    }
    private static void installEditorMap(Path worldRoot) {
        Path editorRoot = worldRoot.resolve("dimensions").resolve(MOD_ID).resolve("charter");
        Path marker = editorRoot.resolve(EDITOR_MAP_MARKER);
        if (Files.exists(marker)) return;
        Path regionDirectory = editorRoot.resolve("region");
        Path stagingDirectory = editorRoot.resolve(".rhythmc_maker_editor_map_staging");
        try {
            Files.createDirectories(regionDirectory);
            Files.createDirectories(stagingDirectory);
            for (String region : EDITOR_MAP_REGIONS) {
                String resourcePath = "data/" + MOD_ID + "/editor_map/region/" + region;
                try (InputStream input = RhythmcMaker.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (input == null) throw new IOException("Missing embedded editor map resource: " + resourcePath);
                    Files.copy(input, stagingDirectory.resolve(region), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            for (String region : EDITOR_MAP_REGIONS) {
                Files.move(stagingDirectory.resolve(region), regionDirectory.resolve(region), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.deleteIfExists(stagingDirectory);
            Files.createFile(marker);
            LOGGER.info("Installed Rhythmc maker editor map at {}", editorRoot);
        } catch (IOException exception) {
            LOGGER.error("Failed to install Rhythmc maker editor map", exception);
        }
    }
    private static void restoreEditorTrackFoundation(ServerWorld world, int x, int length, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int leftWall = -laneHalfWidth - 1;
        int rightWall = laneHalfWidth + 1;
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int dx = -maximumHalfWidth; dx <= maximumHalfWidth; dx++) for (int y = 64; y <= 69; y++) for (int z = -2; z <= 2; z++) {
            world.setBlockState(new BlockPos(x + dx, y, z), Blocks.AIR.getDefaultState(), 2);
        }
        for (int dx = leftWall; dx <= rightWall; dx++) for (int dz = -2; dz <= 2; dz++) {
            world.setBlockState(new BlockPos(x + dx, 65, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 2);
        }
        world.setBlockState(new BlockPos(x, 65, 0), Blocks.VERDANT_FROGLIGHT.getDefaultState(), 2);
        for (int y = 66; y <= 67; y++) {
            for (int dx = leftWall; dx <= rightWall; dx++) world.setBlockState(new BlockPos(x + dx, y, 2), Blocks.WHITE_CONCRETE.getDefaultState(), 2);
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + leftWall, y, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 2);
                world.setBlockState(new BlockPos(x + rightWall, y, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 2);
            }
        }
        for (int dx = leftWall; dx <= rightWall; dx++) world.setBlockState(new BlockPos(x + dx, 68, 2), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 2);
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(x + leftWall, 68, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 2);
            world.setBlockState(new BlockPos(x + rightWall, 68, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 2);
        }
        for (int dx = -laneHalfWidth; dx <= laneHalfWidth; dx++) for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(x + dx, 69, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 2);
        }
        for (int dx = leftWall; dx <= rightWall; dx++) {
            world.setBlockState(new BlockPos(x + dx, 69, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 2);
            world.setBlockState(new BlockPos(x + dx, 65, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 2);
        }
        for (int y = 66; y <= 68; y++) {
            world.setBlockState(new BlockPos(x + leftWall, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 2);
            world.setBlockState(new BlockPos(x + rightWall, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 2);
        }
    }
    private static void extendEditorArea(ServerWorld world, int x, int length) {
        extendEditorArea(world, x, length, 4, 3);
    }

    private static boolean hasEditorTrack(ServerWorld world, ChartManifest chart) {
        int z = -3;
        for (int x = laneWallLeftX(chart); x <= laneWallRightX(chart); x++) {
            for (int y = 64; y <= 68; y++) {
                if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) return true;
            }
        }
        return false;
    }

    private static void extendEditorArea(ServerWorld world, int x, int length, int divisions, int laneCount) {
        int totalBeats = Math.max(8, length);
        divisions = Math.max(1, Math.min(32, divisions));
        if (!isSupportedLaneCount(laneCount)) laneCount = 3;
        final int targetDivisions = divisions;
        final int targetLaneCount = laneCount;
        EDITOR_AREA_TASKS.compute(world.getRegistryKey(), (key, existing) -> {
            if (existing == null || existing.x != x || existing.divisions != targetDivisions || existing.laneCount != targetLaneCount) {
                EditorAreaTask task = new EditorAreaTask(x, totalBeats, targetDivisions, targetLaneCount);
                copyDecorationColumn(world, x, -3, DECORATION_TEMPLATES.get("start"), targetLaneCount);
                task.cursor = 1;
                return task;
            }
            existing.totalBeats = Math.max(existing.totalBeats, totalBeats);
            return existing;
        });
    }
    private static void processEditorAreaTasks(MinecraftServer server) {
        for (var iterator = EDITOR_AREA_TASKS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null) {
                EDITOR_AREA_TASKS.remove(entry.getKey(), entry.getValue());
                continue;
            }
            EditorAreaTask task = entry.getValue();
            int budget = 8;
            while (budget-- > 0 && task.cursor < task.totalBeats) {
                int targetZ = -3 - task.cursor;
                String template = task.cursor % task.divisions == 0 ? "start" : ((task.cursor - 1) % 2 == 0 ? "yellow" : "green");
                copyDecorationColumn(world, task.x, targetZ, DECORATION_TEMPLATES.get(template), task.laneCount);
                task.cursor++;
            }
            if (task.cursor >= task.totalBeats) {
                buildEditorTrackEndBarrier(world, task.x, task.totalBeats, task.laneCount);
                markPendingStartChunk(server, world);
                EDITOR_AREA_TASKS.remove(entry.getKey(), task);
            }
        }
    }

    private static void processEditorTrackRebuildTasks(MinecraftServer server) {
        for (var iterator = EDITOR_TRACK_REBUILD_TASKS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            EditorTrackRebuildTask task = entry.getValue();
            ServerWorld world = server.getWorld(task.worldKey);
            if (world == null) { iterator.remove(); continue; }
            int budget = Integer.MAX_VALUE;
            if (!task.foundationReady) {
                restoreEditorTrackFoundation(world, task.x, task.newLength, task.laneCount);
                task.foundationReady = true;
            }
            while (budget-- > 0 && task.cursor < task.newLength) {
                int slot = task.cursor++;
                int z = -3 - slot;
                String template = slot % task.divisions == 0 ? "start" : ((slot - 1) % 2 == 0 ? "yellow" : "green");
                copyDecorationColumn(world, task.x, z, DECORATION_TEMPLATES.get(template), task.laneCount);
            }
            if (task.cursor < task.newLength) continue;
            while (budget-- > 0 && task.clearCursor < task.oldLength) {
                int slot = task.clearCursor++;
                if (slot < task.newLength) continue;
                int z = -3 - slot;
                for (int dx = -(MAX_EDITOR_LANE_COUNT / 2 + 1); dx <= MAX_EDITOR_LANE_COUNT / 2 + 1; dx++) {
                    for (int y = 64; y <= 68; y++) {
                        BlockPos pos = new BlockPos(task.x + dx, y, z);
                        if (!isNoteBlock(world.getBlockState(pos).getBlock())) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
            if (task.clearCursor < task.oldLength) continue;
            if (!task.displaysScheduled) {
                buildEditorTrackEndBarrier(world, task.x, task.newLength, task.laneCount);
                ChartManifest chart = null;
                try { chart = cachedChart(server, ACTIVE_CHARTS.get(task.playerId)); } catch (IOException ignored) { }
                if (chart != null) {
                    CHART_CACHE_BY_WORLD.put(world.getRegistryKey(), chart);
                    markSelectedStartChunk(world, chart, chart.selectedStartChunk);
                    refreshNoteDisplays(world, chart, task.oldLength, task.previousLaneCount);
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.playerId);
                    if (player != null && config.preventChunkDisplacement && !hasPlaybackSession(task.playerId)) {
                        double targetZ = PlaybackCoordinates.editorWorldZAtBeat(chart, task.previousChunk - 1.0);
                        player.teleport(world, player.getX(), player.getY(), targetZ, Set.of(), player.getYaw(), player.getPitch(), false);
                    }
                }
                task.displaysScheduled = true;
                continue;
            }
            if (hasPendingEditorTrackDisplayWork(world.getRegistryKey())) continue;
            ServerPlayerEntity completedPlayer = server.getPlayerManager().getPlayer(task.playerId);
            if (completedPlayer != null) {
                EDITOR_TRACK_ADJUSTMENTS.remove(completedPlayer.getUuid());
                EDITOR_TRACK_ADJUSTMENT_NOTICES.remove(completedPlayer.getUuid());
                completedPlayer.sendMessage(Text.literal("制谱器轨道调整完成：" + task.laneCount + " × " + task.divisions), true);
            }
            iterator.remove();
        }
    }
    private static boolean hasPendingEditorTrackDisplayWork(RegistryKey<World> worldKey) {
        return EDITOR_NOTE_REFRESH_TASKS.containsKey(worldKey)
                || EDITOR_NOTE_DISPLAY_SWEEP_TASKS.containsKey(worldKey)
                || NOTE_DISPLAY_CHUNK_CLEANUP_TASKS.stream()
                .anyMatch(task -> task.worldKey().equals(worldKey) && task.releaseForcedChunk());
    }
    private static void clearEditorTrackColumns(ServerWorld world, int x, int length) {
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int slot = 0; slot <= Math.max(0, length); slot++) {
            int z = -3 - slot;
            for (int dx = -maximumHalfWidth; dx <= maximumHalfWidth; dx++) {
                for (int y = 64; y <= 68; y++) {
                    world.setBlockState(new BlockPos(x + dx, y, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    private static void buildEditorTrackEndBarrier(ServerWorld world, int x, int trackLength, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int leftWall = -laneHalfWidth - 1;
        int rightWall = laneHalfWidth + 1;
        int z = -3 - Math.max(0, trackLength);
        for (int dx = leftWall; dx <= rightWall; dx++) {
            for (int y = 64; y <= 68; y++) {
                world.setBlockState(new BlockPos(x + dx, y, z), Blocks.RED_STAINED_GLASS.getDefaultState(), 2);
            }
        }
    }

    private static void markPendingStartChunk(MinecraftServer server, ServerWorld world) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != world) continue;
            ChartManifest chart = loadActiveChart(player, server);
            if (chart != null) markSelectedStartChunk(world, chart, selectedStartChunk(player, chart));
        }
    }

    private record SceneEditorAreaKey(RegistryKey<World> worldKey, int centerX) {}

    private static final class SceneEditorAreaTask {
        private final int centerX;
        private final int laneCount;
        private final String chartId;
        private final int sceneIndex;
        private final UUID operationPlayerId;
        private final String operationSuccessMessage;
        private int clearCursor;
        private boolean structureBuilt;
        private SceneEditorAreaTask(int centerX, int laneCount, String chartId, int sceneIndex, UUID operationPlayerId, String operationSuccessMessage) {
            this.centerX = centerX;
            this.laneCount = laneCount;
            this.chartId = chartId;
            this.sceneIndex = sceneIndex;
            this.operationPlayerId = operationPlayerId;
            this.operationSuccessMessage = operationSuccessMessage;
        }
    }

    private static final class SceneCaptureTask {
        private final ServerWorld world;
        private final String chartId;
        private final int sceneIndex;
        private final int centerX;
        private final UUID playerId;
        private final boolean returnToEditor;
        private final Map<String, Integer> palette = new java.util.LinkedHashMap<>();
        private final ByteArrayOutputStream blockData = new ByteArrayOutputStream(1 << 20);
        private int cursor;

        private SceneCaptureTask(ServerWorld world, String chartId, int sceneIndex, int centerX, UUID playerId, boolean returnToEditor) {
            this.world = world;
            this.chartId = chartId;
            this.sceneIndex = sceneIndex;
            this.centerX = centerX;
            this.playerId = playerId;
            this.returnToEditor = returnToEditor;
        }

        private boolean process() throws IOException {
            int total = SCENE_SIZE * SCENE_SIZE * SCENE_SIZE;
            int budget = SCENE_CAPTURE_BLOCKS_PER_TICK;
            while (budget-- > 0 && cursor < total) {
                int localX = cursor % SCENE_SIZE;
                int localZ = (cursor / SCENE_SIZE) % SCENE_SIZE;
                int localY = cursor / (SCENE_SIZE * SCENE_SIZE);
                BlockState state = world.getBlockState(new BlockPos(centerX - SCENE_SIZE / 2 + localX, SCENE_EDIT_MIN_Y + localY, SCENE_EDIT_MIN_Z + localZ));
                String id = blockStateId(state);
                int paletteIndex = palette.computeIfAbsent(id, ignored -> palette.size());
                writeSchematicVarInt(blockData, paletteIndex);
                cursor++;
            }
            if (cursor < total) return false;
            SceneEditStorage.writeSchematic(world.getServer(), chartId, sceneIndex, palette, blockData.toByteArray());
            return true;
        }
    }
    private record NoteDisplayChunkCleanup(RegistryKey<World> worldKey, ChunkPos chunkPos, int retriesRemaining,
                                           boolean releaseForcedChunk) {}
    private static final class EditorNoteDisplaySweepTask {
        private final RegistryKey<World> worldKey;
        private final String chartId;
        private final int maximumChunkX;
        private final int minimumChunkZ;
        private final int maximumChunkZ;
        private int chunkX;
        private int chunkZ;
        private EditorNoteDisplaySweepTask(RegistryKey<World> worldKey, String chartId, int minimumChunkX, int maximumChunkX,
                                           int minimumChunkZ, int maximumChunkZ) {
            this.worldKey = worldKey;
            this.chartId = chartId;
            this.chunkX = minimumChunkX;
            this.maximumChunkX = maximumChunkX;
            this.minimumChunkZ = minimumChunkZ;
            this.maximumChunkZ = maximumChunkZ;
            this.chunkZ = minimumChunkZ;
        }
        private boolean hasNext() { return chunkX <= maximumChunkX; }
        private ChunkPos nextChunk() {
            ChunkPos result = new ChunkPos(chunkX, chunkZ);
            if (++chunkZ > maximumChunkZ) {
                chunkZ = minimumChunkZ;
                chunkX++;
            }
            return result;
        }
    }
    private static final class EditorTrackRebuildTask {
        private final RegistryKey<World> worldKey;
        private final int x;
        private final int oldLength;
        private final int newLength;
        private final int divisions;
        private final int laneCount;
        private final int previousLaneCount;
        private final UUID playerId;
        private final int previousChunk;
        private int cursor;
        private int clearCursor;
        private boolean foundationReady;
        private boolean displaysScheduled;
        private EditorTrackRebuildTask(RegistryKey<World> worldKey, int x, int oldLength, int newLength,
                                       int divisions, int laneCount, int previousLaneCount, UUID playerId, int previousChunk) {
            this.worldKey = worldKey;
            this.x = x;
            this.oldLength = Math.max(0, oldLength);
            this.newLength = Math.max(8, newLength);
            this.divisions = Math.max(1, Math.min(32, divisions));
            this.laneCount = isSupportedLaneCount(laneCount) ? laneCount : 3;
            this.previousLaneCount = isSupportedLaneCount(previousLaneCount) ? previousLaneCount : this.laneCount;
            this.playerId = playerId;
            this.previousChunk = Math.max(1, previousChunk);
        }
    }
    private static final class EditorAreaTask {
        private final int x;
        private final int divisions;
        private final int laneCount;
        private int totalBeats;
        private int cursor;
        private EditorAreaTask(int x, int totalBeats, int divisions, int laneCount) {
            this.x = x;
            this.totalBeats = totalBeats;
            this.divisions = divisions;
            this.laneCount = laneCount;
        }
    }
    private static Map<Integer, BlockState> loadDecorationTemplate(String name) {
        Map<Integer, BlockState> result = new java.util.HashMap<>();
        String resource = "data/" + MOD_ID + "/structures/" + name + ".nbt";
        try (InputStream input = RhythmcMaker.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing decoration structure: " + resource);
            NbtCompound root = NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes());
            NbtList palette = root.getListOrEmpty("palette");
            NbtList blocks = root.getListOrEmpty("blocks");
            for (int i = 0; i < blocks.size(); i++) {
                NbtCompound block = blocks.getCompoundOrEmpty(i);
                NbtList pos = block.getListOrEmpty("pos");
                int key = (pos.getInt(1, 0) << 4) | pos.getInt(0, 0);
                int stateIndex = block.getInt("state", 0);
                NbtCompound state = palette.getCompoundOrEmpty(stateIndex);
                String blockName = state.getString("Name", "minecraft:air");
                BlockState blockState = Registries.BLOCK.get(Identifier.of(blockName)).getDefaultState();
                result.put(key, blockState);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load decoration structure {}", name, exception);
        }
        return result;
    }

    private static void copyDecorationColumn(ServerWorld world, int x, int targetZ, Map<Integer, BlockState> template, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int leftWall = -laneHalfWidth - 1;
        int rightWall = laneHalfWidth + 1;
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int dx = -maximumHalfWidth; dx <= maximumHalfWidth; dx++) {
            for (int y = 64; y <= 68; y++) {
                BlockPos target = new BlockPos(x + dx, y, targetZ);
                if (isNoteBlock(world.getBlockState(target).getBlock())) continue;
                BlockState desired = decorationState(template, dx, y, laneHalfWidth, leftWall, rightWall);
                if (!world.getBlockState(target).equals(desired)) world.setBlockState(target, desired, 2);
            }
        }
        BlockPos lowerTarget = new BlockPos(x, 63, targetZ);
        if (!world.getBlockState(lowerTarget).isAir()) world.setBlockState(lowerTarget, Blocks.AIR.getDefaultState(), 2);
    }

    private static BlockState decorationState(Map<Integer, BlockState> template, int dx, int y, int laneHalfWidth, int leftWall, int rightWall) {
        if (dx == leftWall) return template.getOrDefault(((y - 64) << 4), Blocks.AIR.getDefaultState());
        if (dx == rightWall) return template.getOrDefault(((y - 64) << 4) | 4, Blocks.AIR.getDefaultState());
        if (y == 64 && dx >= -laneHalfWidth && dx <= laneHalfWidth) {
            int templateX = dx < 0 ? 1 : dx > 0 ? 3 : 2;
            return template.getOrDefault(((y - 64) << 4) | templateX, Blocks.AIR.getDefaultState());
        }
        if (dx >= -1 && dx <= 1) return template.getOrDefault(((y - 64) << 4) | (dx + 2), Blocks.AIR.getDefaultState());
        return Blocks.AIR.getDefaultState();
    }
    private static Item registerMenuItem(String id, String name) {
        Identifier identifier = Identifier.of(MOD_ID, id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, identifier);
        return Registry.register(Registries.ITEM, key, new MenuItem(new Item.Settings().registryKey(key).maxCount(1), name));
    }

    private static final class MenuItem extends Item {
        private final Text name;

        private MenuItem(net.minecraft.item.Item.Settings settings, String name) {
            super(settings);
            this.name = Text.literal(name).setStyle(Style.EMPTY.withColor(Formatting.WHITE).withItalic(false));
        }

        @Override
        public Text getName(ItemStack stack) {
            return name;
        }
    }
}










