package cn.frkovo.rhythmcmaker;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartStorage;
import cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig;
import cn.frkovo.rhythmcmaker.scene.SceneEditStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import com.google.gson.JsonObject;
import net.minecraft.util.ActionResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.rule.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RhythmcMaker implements ModInitializer {
    public static final String MOD_ID = "rhythmc_maker";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final BlockPos CHARTER_SPAWN = new BlockPos(0, 65, 0);
    private static final BlockPos EDITOR_SPAWN = new BlockPos(0, 66, 0);
    public static final RegistryKey<World> CHARTER_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "charter"));
    public static final Item START_CHART_ITEM = registerMenuItem("start_chart", "开始写谱（右键打开）");
    public static final Item UNNAMED_ITEM = registerMenuItem("unnamed", "awa~（右键打开）");
    public static final Item SETTINGS_ITEM = registerMenuItem("settings", "设置（右键打开）");
    public static final Item MORE_SETTINGS_ITEM = registerMenuItem("more_settings", "更多选项（右键打开）");
    public static final Item SCENE_EDIT_ITEM = registerMenuItem("scene_edit", "场景编辑（右键打开）");
    public static final Item QUICK_FUNCTIONS_ITEM = registerMenuItem("quick_functions", "快捷功能（右键打开）");
    public static final Item CHART_SETTINGS_ITEM = registerMenuItem("chart_settings", "谱面设置");
    public static final Item MORE_OPTIONS_RETURN_ITEM = registerMenuItem("more_options_return", "返回（右键打开）");
    public static final Item CHART_INFO_ITEM = registerMenuItem("chart_info", "谱面信息（右键打开）");
    public static final Item EFFECT_TRACK_ITEM = registerMenuItem("effect_track", "特效轨道（右键打开）");
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
    private static final Map<UUID, java.util.List<ItemStack>> SAVED_HOTBARS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, EditorAreaTask> EDITOR_AREA_TASKS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, EditorNoteRefreshTask> EDITOR_NOTE_REFRESH_TASKS = new ConcurrentHashMap<>();
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
    private static final int SCENE_EDIT_BASE_X = 10000;
    private static final int SCENE_EDIT_SPACING = 200;
    private static final int SCENE_EDIT_MIN_Z = -32;
    private static final int SCENE_EDIT_MAX_Z = 31;
    private static final int SCENE_EDIT_MIN_Y = 64;
    private static final int SCENE_EDIT_MAX_Y = 72;
    private static final int PLAYBACK_FRAME_Z = -2;
    private static final double PLAYBACK_APPROACH_DISTANCE = 25.0;
    // The note always disappears at this fixed world-space line, independent of playerSpeed.
    // Remove on the judgment line so the model and hit sound stay synchronized.
    private static final double PLAYBACK_DISAPPEAR_Z = PLAYBACK_FRAME_Z;
    private static final int PLAYBACK_DISPLAY_UPDATE_INTERVAL_TICKS = 1;
    // The maker's base visual flow is five times the previous value: old player speed 5.0 equals new 1.0.
    private static final double PLAYBACK_BASE_SPEED_PER_BEAT = 5.0;
    private static final int PLAYBACK_START_DELAY_TICKS = 0;
    private static final int MIN_EDITOR_LANE_COUNT = 1;
    private static final int MAX_EDITOR_LANE_COUNT = 9;
    private static final int SCENE_SIZE = 128;
    private static final int SCENE_MIN_X = PLAYBACK_PLATFORM_X - SCENE_SIZE / 2;
    private static final int SCENE_MAX_X = SCENE_MIN_X + SCENE_SIZE - 1;
    private static final int SCENE_MIN_Y = PLAYBACK_PLATFORM_Y - SCENE_SIZE / 2;
    private static final int SCENE_MAX_Y = SCENE_MIN_Y + SCENE_SIZE - 1;
    private static final int SCENE_MIN_Z = PLAYBACK_PLATFORM_Z - SCENE_SIZE / 2;
    private static final int SCENE_MAX_Z = SCENE_MIN_Z + SCENE_SIZE - 1;
    private static final int SCENE_BORDER_INTERVAL_TICKS = 4;
    private static final int SCENE_BORDER_STEP = 8;
    private static final DustParticleEffect SCENE_BORDER_GLOW = new DustParticleEffect(0x72E8FF, 1.35f);
    private static final DustParticleEffect SCENE_CENTER_GLOW = new DustParticleEffect(0xFF3030, 2.8f);
    private static final DustParticleEffect PLAYBACK_FRAME_GLOW = new DustParticleEffect(0x397BFF, 1.8f);
    private static volatile RhythmcMakerConfig config = new RhythmcMakerConfig();
    private static long lastAutosaveTick;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            config = RhythmcMakerConfig.load(server);
            Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();
            installHubMap(root);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ChartDimensionManager.preloadCharts(server);
            LOGGER.info("Rhythmc maker dynamic chart dimensions are ready");
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
            processPendingEditorEntries(server);
            processEditorAreaTasks(server);
            processEditorNoteRefreshTasks(server);
            processPlaybackSessions(server);
            long tick = server.getTicks();
            if (tick % SCENE_BORDER_INTERVAL_TICKS == 0) showSceneBoundary(server);
            if (tick % ticksForSeconds(config.sidebarRefreshIntervalSeconds) == 0) refreshEditorSidebar(server);
            if (tick - lastAutosaveTick < ticksForSeconds(config.autosaveIntervalSeconds)) return;
            lastAutosaveTick = server.getTicks();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (saveActiveChart(server, player)) player.sendMessage(Text.literal("保存成功！").formatted(Formatting.GREEN), true);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld) || !isEditorWorld(world) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (PLAYBACK_SESSIONS.containsKey(serverPlayer.getUuid())) return ActionResult.FAIL;
            BlockPos target = hitResult.getBlockPos().offset(hitResult.getSide());
            if (isScenePosition(target)) return ActionResult.PASS;
            ItemStack stack = serverPlayer.getStackInHand(hand);
            if (!isNoteItem(stack)) return ActionResult.FAIL;
            ChartManifest chart = loadActiveChart(serverPlayer, serverWorld.getServer());
            if (!isValidNotePosition(target, chart, serverWorld)) return ActionResult.FAIL;
            int type = noteType(stack);
            if (target.getY() == 65 && type != 2) return ActionResult.FAIL;
            if (target.getY() != 65 && type == 2) return ActionResult.FAIL;
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
            int slot = -target.getZ() - 3; note.beat = slot / (double) divisionsPerChunk(chart);
            note.time = note.beat * 60.0 / chart.bpm;
            chart.notes.add(note);
            try { saveChart(serverWorld.getServer(), chart); } catch (IOException ignored) { }
            return ActionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!isEditorWorld(world) || !(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (isScenePosition(pos)) return !PLAYBACK_SESSIONS.containsKey(player.getUuid());
            ChartManifest chart = loadActiveChart(serverPlayer, serverWorld.getServer());
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
             dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_more_options")
                 .executes(context -> openMoreOptions(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_more_return")
                .executes(context -> closeMoreOptions(context.getSource())));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_offset")
                .then(net.minecraft.server.command.CommandManager.argument("milliseconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-60000, 60000))
                    .executes(context -> adjustOffset(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "milliseconds")))));
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("rhythmc_play")
                .executes(context -> togglePlayback(context.getSource()))
                .then(net.minecraft.server.command.CommandManager.argument("startChunk", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                    .executes(context -> togglePlayback(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startChunk")))));
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
        if (ChartDimensionManager.isChartWorld(world.getRegistryKey()) || ChartDimensionManager.isSlotWorld(world.getRegistryKey()) || world.getRegistryKey().equals(CHARTER_DIMENSION)) return true;
        // Keep recognizing pre-fix dynamic worlds so reconnect logic can return players to the hub.
        return world instanceof CustomLevel
            && world.getRegistryKey().getValue().getNamespace().equals(MOD_ID)
            && world.getRegistryKey().getValue().getPath().startsWith("chart_");
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
        world.getGameRules().setValue(GameRules.TNT_EXPLODES, false, server);
        world.getGameRules().setValue(GameRules.ADVANCE_TIME, false, server);
        world.setTimeOfDay(1000L);
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
        player.getInventory().setStack(4, menuStack(UNNAMED_ITEM));
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
    private static int chartOffset(String id) { return 0; }
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
            chart.trackLength = calculateTrackLength(chart);
            ChartStorage.update(source.getServer(), chart);
            boolean assignedDimension = chart.dimensionId == null || chart.dimensionId.isBlank();
            ChartDimensionManager.key(chart);
            if (assignedDimension) ChartStorage.update(source.getServer(), chart);
            ServerWorld editor = ChartDimensionManager.getOrCreate(source.getServer(), chart);
            String validationError = validateEditorEntry(source.getServer(), player, chart, editor);
            if (validationError != null) {
                player.sendMessage(Text.literal("进入谱面制谱维度已取消：" + validationError), false);
                return 0;
            }
            String previousChartId = ACTIVE_CHARTS.get(player.getUuid());
            if (previousChartId != null && !previousChartId.equals(chartId)) {
                ChartManifest previousChart = ChartStorage.find(source.getServer(), previousChartId);
                if (previousChart != null) clearChartNoteBlocks(editor, previousChart);
            }
            EDITOR_AREA_TASKS.remove(editor.getRegistryKey());
            EDITOR_NOTE_REFRESH_TASKS.remove(editor.getRegistryKey());
            clearAllChartNoteDisplays(editor);
            ACTIVE_CHARTS.put(player.getUuid(), chartId);
            ACTIVE_CHART_LANES.put(player.getUuid(), laneCount(chart));
            SELECTED_START_CHUNKS.put(player.getUuid(), Math.max(1, Math.min(Math.max(1, chart.chunkCount), chart.selectedStartChunk)));
            restoreEditorTrackFoundation(editor, 0, chart.trackLength, laneCount(chart));
            extendEditorArea(editor, 0, chart.trackLength, chartBeats(source.getServer(), chartId), laneCount(chart));
            if (!chart.sceneInitialized) {
                buildPlaybackArea(editor, laneCount(chart));
                chart.sceneInitialized = true;
                ChartStorage.update(source.getServer(), chart);
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
    private static void processPendingEditorEntries(MinecraftServer server) {
        for (var iterator = PENDING_EDITOR_ENTRIES.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !entry.getValue().equals(ACTIVE_CHARTS.get(player.getUuid()))) continue;
            if (!DIMENSION_TRAVEL_READY.contains(player.getUuid())) continue;
            try {
                ChartManifest chart = ChartStorage.find(server, entry.getValue());
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
                ChartManifest chart = ChartStorage.find(source.getServer(), chartId);
                if (chart != null) {
                    chart.lastEdited = LocalDateTime.now().format(LAST_EDITED_FORMAT);
                    ChartStorage.update(source.getServer(), chart);
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
                EDITOR_AREA_TASKS.remove(editor.getRegistryKey());
                EDITOR_NOTE_REFRESH_TASKS.remove(editor.getRegistryKey());
                clearChartNoteDisplays(editor, chart);
                clearNoteBlocks(editor, oldNotePositions);
                clearEditorTrackColumns(editor, 0, previousTrackLength);
            }
            chart.divisionsPerChunk = targetDivisions;
            chart.beatsPerMeasure = 0;
            chart.laneCount = targetLaneCount;
            ACTIVE_CHART_LANES.put(player.getUuid(), targetLaneCount);
            repositionNotes(chart);
            chart.trackLength = calculateTrackLength(chart);
            saveChart(source.getServer(), chart);
            if (editor != null) {
                restoreEditorTrackFoundation(editor, 0, chart.trackLength, laneCount(chart));
                extendEditorArea(editor, 0, chart.trackLength, divisionsPerChunk(chart), laneCount(chart));
                markSelectedStartChunk(editor, chart, selectedStartChunk(player, chart));
                refreshNoteDisplays(editor, chart);
                if (config.preventChunkDisplacement && !PLAYBACK_SESSIONS.containsKey(player.getUuid())) {
                    double targetZ = -3.0 - (previousChunk - 1) * divisionsPerChunk(chart);
                    player.teleport(editor, player.getX(), player.getY(), targetZ, Set.of(), player.getYaw(), player.getPitch(), false);
                }
            }
            player.sendMessage(Text.literal("制谱器轨道已更新为 " + laneCount(chart) + " × " + divisionsPerChunk(chart)), true);
            return 1;
        } catch (IOException exception) {
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

    private static boolean saveActiveChart(MinecraftServer server, ServerPlayerEntity player) { String id = ACTIVE_CHARTS.get(player.getUuid()); if (id == null) return false; try { ChartManifest chart = ChartStorage.find(server, id); if (chart == null) return false; saveChart(server, chart); return true; } catch (IOException ignored) { return false; } }
    private static void saveChart(MinecraftServer server, ChartManifest chart) throws IOException { ChartStorage.update(server, chart); }
    public static void saveCurrentChart(MinecraftServer server, ServerPlayerEntity player) { saveActiveChart(server, player); }
    public static String activeChart(ServerPlayerEntity player) { return ACTIVE_CHARTS.get(player.getUuid()); }
    private static ChartManifest loadActiveChart(ServerPlayerEntity player, MinecraftServer server) {
        String id = ACTIVE_CHARTS.get(player.getUuid());
        if (id == null) return null;
        try { return ChartStorage.find(server, id); } catch (IOException ignored) { return null; }
    }
    private static int trackLength(MinecraftServer server, String id) {
        try { ChartManifest chart = ChartStorage.find(server, id); return chart == null ? 4 : calculateTrackLength(chart); } catch (IOException ignored) { return 4; }
    }
    private static int chartBeats(MinecraftServer server, String id) {
        try { ChartManifest chart = ChartStorage.find(server, id); return chart == null ? 4 : divisionsPerChunk(chart); } catch (IOException ignored) { return 4; }
    }
    private static int calculateTrackLength(ChartManifest chart) {
        double beats = chart.durationSeconds > 0 && chart.bpm > 0 ? chart.durationSeconds * chart.bpm / 60.0 : 8.0;
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
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (PLAYBACK_SESSIONS.containsKey(player.getUuid())) return 0;
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        int previous = selectedStartChunk(player, chart);
        int selected = Math.max(1, Math.min(Math.max(1, chart.chunkCount), previous + delta));
        if (selected == previous) return 1;
        SELECTED_START_CHUNKS.put(player.getUuid(), selected);
        chart.selectedStartChunk = selected;
        try { saveChart(source.getServer(), chart); } catch (IOException ignored) { }
        restoreSelectedStartChunk(world, chart, previous);
        markSelectedStartChunk(world, chart, selected);
        player.sendMessage(Text.literal("播放起始 Chunk：" + selected), true);
        return 1;
    }
    private static int setStartChunk(net.minecraft.server.command.ServerCommandSource source, int requestedChunk) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (PLAYBACK_SESSIONS.containsKey(player.getUuid())) return 0;
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world)) return 0;
        int maximum = Math.max(1, chart.chunkCount);
        if (requestedChunk > maximum) {
            player.sendMessage(Text.literal("谱面没有这么长awa~"), true);
            return 0;
        }
        int previous = selectedStartChunk(player, chart);
        if (requestedChunk == previous) return 1;
        SELECTED_START_CHUNKS.put(player.getUuid(), requestedChunk);
        chart.selectedStartChunk = requestedChunk;
        try { saveChart(source.getServer(), chart); } catch (IOException ignored) { }
        restoreSelectedStartChunk(world, chart, previous);
        markSelectedStartChunk(world, chart, requestedChunk);
        player.sendMessage(Text.literal("播放起始 Chunk：" + requestedChunk), true);
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
        double targetZ = -3.0 - (requestedChunk - 1) * divisionsPerChunk(chart);
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
        double targetZ = -3.0 - requestedSeconds * Math.max(0.0, chart.bpm) / 60.0 * divisionsPerChunk(chart);
        player.teleport(world, player.getX(), player.getY(), targetZ, Set.of(), player.getYaw(), player.getPitch(), false);
        player.sendMessage(Text.literal("已传送至 " + String.format(Locale.ROOT, "%.2f", requestedSeconds) + " 秒"), true);
        return 1;
    }
    private static int teleportToPlaybackScene(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (PLAYBACK_SESSIONS.containsKey(player.getUuid())) {
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
        if (PLAYBACK_SESSIONS.containsKey(player.getUuid())) {
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
        try {
            SceneEditStorage.Scene scene = SceneEditStorage.list(source.getServer()).stream().filter(value -> value.index == index).findFirst().orElse(null);
            if (scene == null) { player.sendMessage(Text.literal("场景不存在"), true); return 0; }
            player.teleport(world, scene.x + 0.5, 66.0, 0.5, Set.of(), 0.0f, 0.0f, false);
            player.sendMessage(Text.literal("已进入 " + scene.name + " 搭建场地"), true);
            return 1;
        } catch (IOException exception) {
            player.sendMessage(Text.literal("读取场景失败：" + exception.getMessage()), true);
            return 0;
        }
    }
    public static java.util.List<SceneEditStorage.Scene> sceneEditors(MinecraftServer server) throws IOException { return SceneEditStorage.list(server); }
    public static SceneEditStorage.Scene createSceneEditor(MinecraftServer server) throws IOException {
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server);
        int index = scenes.stream().mapToInt(scene -> scene.index).max().orElse(0) + 1;
        SceneEditStorage.Scene scene = new SceneEditStorage.Scene(index);
        scenes.add(scene);
        SceneEditStorage.save(server, scenes);
        ServerWorld world = server.getWorld(CHARTER_DIMENSION);
        if (world != null) buildSceneEditorArea(world, scene.x);
        return scene;
    }
    public static void saveSceneEditor(MinecraftServer server, SceneEditStorage.Scene scene) throws IOException {
        java.util.List<SceneEditStorage.Scene> scenes = SceneEditStorage.list(server);
        for (int index = 0; index < scenes.size(); index++) if (scenes.get(index).index == scene.index) scenes.set(index, scene);
        SceneEditStorage.save(server, scenes);
    }
    private static int resetSceneEditorCommand(net.minecraft.server.command.ServerCommandSource source, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        resetSceneEditor(source.getServer(), index);
        source.sendFeedback(() -> Text.literal("场景已重置"), false);
        return 1;
    }
    public static void resetSceneEditor(MinecraftServer server, int index) {
        try {
            SceneEditStorage.Scene scene = SceneEditStorage.list(server).stream().filter(value -> value.index == index).findFirst().orElse(null);
            if (scene == null) return;
            ServerWorld world = server.getWorld(CHARTER_DIMENSION);
            if (world != null) { clearSceneEditorArea(world, scene.x); buildSceneEditorArea(world, scene.x); }
        } catch (IOException exception) { LOGGER.warn("Failed to reset scene editor {}", index, exception); }
    }
    private static double totalChartSeconds(ChartManifest chart) {
        if (chart.durationSeconds > 0.0 && Double.isFinite(chart.durationSeconds)) return chart.durationSeconds;
        if (chart.totalBeats > 0.0 && chart.bpm > 0.0) return chart.totalBeats * 60.0 / chart.bpm;
        return Math.max(1, chart.chunkCount) * 60.0 / Math.max(1.0, chart.bpm);
    }
    private static void restoreSelectedStartChunk(ServerWorld world, ChartManifest chart, int selectedChunk) {
        int z = -3 - (selectedChunk - 1) * divisionsPerChunk(chart);
        for (int x = laneWallLeftX(chart); x <= laneWallRightX(chart); x++) for (int y = 64; y <= 68; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isOf(Blocks.LIME_CONCRETE)) world.setBlockState(pos, Blocks.RED_CONCRETE.getDefaultState(), 3);
        }
    }
    private static void markSelectedStartChunk(ServerWorld world, ChartManifest chart, int selectedChunk) {
        int z = -3 - (selectedChunk - 1) * divisionsPerChunk(chart);
        for (int x = laneWallLeftX(chart); x <= laneWallRightX(chart); x++) for (int y = 64; y <= 68; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isOf(Blocks.RED_CONCRETE)) world.setBlockState(pos, Blocks.LIME_CONCRETE.getDefaultState(), 3);
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
    private static void buildSceneEditorArea(ServerWorld world, int centerX) {
        for (int x = centerX - 32; x <= centerX + 32; x++) for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) world.setBlockState(new BlockPos(x, 65, z), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
        for (int x = centerX - 32; x <= centerX + 32; x++) for (int y = 66; y <= 72; y++) {
            world.setBlockState(new BlockPos(x, y, SCENE_EDIT_MIN_Z), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(x, y, SCENE_EDIT_MAX_Z), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) for (int y = 66; y <= 72; y++) {
            world.setBlockState(new BlockPos(centerX - 32, y, z), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(centerX + 32, y, z), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        world.setBlockState(new BlockPos(centerX, 65, 0), Blocks.VERDANT_FROGLIGHT.getDefaultState(), 3);
    }
    private static void clearSceneEditorArea(ServerWorld world, int centerX) {
        for (int x = centerX - 32; x <= centerX + 32; x++) for (int y = SCENE_EDIT_MIN_Y; y <= SCENE_EDIT_MAX_Y; y++) for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
    }
    private static boolean isScenePosition(BlockPos pos) {
        return pos.getX() >= SCENE_MIN_X && pos.getX() <= SCENE_MAX_X
            && pos.getY() >= SCENE_MIN_Y && pos.getY() <= SCENE_MAX_Y
            && pos.getZ() >= SCENE_MIN_Z && pos.getZ() <= SCENE_MAX_Z;
    }
    private static void showSceneBoundary(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world) || PLAYBACK_SESSIONS.containsKey(player.getUuid())) continue;
            if (player.getX() < SCENE_MIN_X - 48 || player.getX() > SCENE_MAX_X + 48
                    || player.getY() < SCENE_MIN_Y - 48 || player.getY() > SCENE_MAX_Y + 48
                    || player.getZ() < SCENE_MIN_Z - 48 || player.getZ() > SCENE_MAX_Z + 48) continue;

            try {
                for (SceneEditStorage.Scene scene : SceneEditStorage.list(server)) {
                    if (Math.abs(player.getX() - scene.x) > 80 || Math.abs(player.getZ()) > 80 || Math.abs(player.getY() - 68) > 80) continue;
                    for (int x = scene.x - 32; x <= scene.x + 32; x += SCENE_BORDER_STEP) {
                        showParticle(world, player, SCENE_BORDER_GLOW, x, 65, SCENE_EDIT_MIN_Z);
                        showParticle(world, player, SCENE_BORDER_GLOW, x, 72, SCENE_EDIT_MIN_Z);
                        showParticle(world, player, SCENE_BORDER_GLOW, x, 65, SCENE_EDIT_MAX_Z);
                        showParticle(world, player, SCENE_BORDER_GLOW, x, 72, SCENE_EDIT_MAX_Z);
                    }
                    for (int z = SCENE_EDIT_MIN_Z; z <= SCENE_EDIT_MAX_Z; z += SCENE_BORDER_STEP) {
                        showParticle(world, player, SCENE_BORDER_GLOW, scene.x - 32, 65, z);
                        showParticle(world, player, SCENE_BORDER_GLOW, scene.x - 32, 72, z);
                        showParticle(world, player, SCENE_BORDER_GLOW, scene.x + 32, 65, z);
                        showParticle(world, player, SCENE_BORDER_GLOW, scene.x + 32, 72, z);
                    }
                }
            } catch (IOException exception) { LOGGER.warn("Failed to render scene editor boundaries", exception); }

            // A sparse 2D particle grid on every face makes the full editable volume visible.
            for (int x = SCENE_MIN_X; x <= SCENE_MAX_X; x += SCENE_BORDER_STEP) {
                for (int z = SCENE_MIN_Z; z <= SCENE_MAX_Z; z += SCENE_BORDER_STEP) {
                    showParticle(world, player, SCENE_BORDER_GLOW, x, SCENE_MIN_Y, z);
                    showParticle(world, player, SCENE_BORDER_GLOW, x, SCENE_MAX_Y, z);
                }
            }
            for (int x = SCENE_MIN_X; x <= SCENE_MAX_X; x += SCENE_BORDER_STEP) {
                for (int y = SCENE_MIN_Y; y <= SCENE_MAX_Y; y += SCENE_BORDER_STEP) {
                    showParticle(world, player, SCENE_BORDER_GLOW, x, y, SCENE_MIN_Z);
                    showParticle(world, player, SCENE_BORDER_GLOW, x, y, SCENE_MAX_Z);
                }
            }
            for (int y = SCENE_MIN_Y; y <= SCENE_MAX_Y; y += SCENE_BORDER_STEP) {
                for (int z = SCENE_MIN_Z; z <= SCENE_MAX_Z; z += SCENE_BORDER_STEP) {
                    showParticle(world, player, SCENE_BORDER_GLOW, SCENE_MIN_X, y, z);
                    showParticle(world, player, SCENE_BORDER_GLOW, SCENE_MAX_X, y, z);
                }
            }
            // Mark only the scene center block (200, 65, 0).
            showParticle(world, player, SCENE_CENTER_GLOW, PLAYBACK_PLATFORM_X, PLAYBACK_PLATFORM_Y, PLAYBACK_PLATFORM_Z);
            showPlaybackFrame(world, player, ACTIVE_CHART_LANES.getOrDefault(player.getUuid(), 3));
        }
    }
    private static void showPlaybackFrame(ServerWorld world, ServerPlayerEntity player, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int left = PLAYBACK_PLATFORM_X - laneHalfWidth;
        int right = PLAYBACK_PLATFORM_X + laneHalfWidth;
        // Blue frame follows the current editor/playback lane width instead of staying at 3 lanes.
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
    private static void showParticle(ServerWorld world, ServerPlayerEntity player, DustParticleEffect effect, int x, int y, int z) {
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
        double dx = player.getX() - px, dy = player.getY() - py, dz = player.getZ() - pz;
        if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;
        world.spawnParticles(player, effect, false, false, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
    }
    private static int togglePlayback(net.minecraft.server.command.ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return togglePlayback(source, -1); }
    private static int togglePlayback(net.minecraft.server.command.ServerCommandSource source, int requestedStartChunk) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (PLAYBACK_SESSIONS.containsKey(player.getUuid())) {
            stopPlayback(source.getServer(), player, true);
            return 1;
        }
        ChartManifest chart = loadActiveChart(player, source.getServer());
        if (chart == null || !(player.getEntityWorld() instanceof ServerWorld world) || !isEditorWorld(world) || chart.bpm <= 0) return 0;
        try { saveChart(source.getServer(), chart); } catch (IOException ignored) { }
        int startChunk = requestedStartChunk > 0 ? Math.max(1, Math.min(Math.max(1, chart.chunkCount), requestedStartChunk)) : selectedStartChunk(player, chart);
        PlaybackSession session = new PlaybackSession(world, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), chart, (startChunk - 1) * 60.0 / chart.bpm, System.nanoTime() + PLAYBACK_START_DELAY_TICKS * 50_000_000L);
        PLAYBACK_SESSIONS.put(player.getUuid(), session);
        player.teleport(world, PLAYBACK_PLATFORM_X + 0.5, PLAYBACK_PLATFORM_Y + 1.0, PLAYBACK_PLATFORM_Z + 0.5, Set.of(), 180.0f, 0.0f, false);
        player.sendMessage(Text.literal("开始播放：Chunk " + startChunk + "（自动 Perfect）"), true);
        return 1;
    }
    private static void processPlaybackSessions(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlaybackSession session = PLAYBACK_SESSIONS.get(player.getUuid());
            if (session == null) continue;
            // Use a monotonic clock; server ticks may stretch during chunk or entity work.
            if (System.nanoTime() < session.startNanos) continue;
            // Song time controls judgment, hit sounds, removal, and song end.
            // Spatial distance is evaluated independently from that clock.
            double songTime = session.startSeconds + (System.nanoTime() - session.startNanos) / 1_000_000_000.0;
            double currentDistance = session.distanceAt(songTime);
            boolean updateDisplayPositions = session.shouldUpdateDisplayPositions(server.getTicks());
            session.playedTapSoundThisTick = false;
            session.playedLookSoundThisTick = false;
            while (session.nextEffectIndex < session.effects.size()) {
                JsonObject effect = session.effects.get(session.nextEffectIndex);
                if (effectSeconds(effect, session.chart) > songTime) break;
                session.nextEffectIndex++;
                playTrackEffect(session, player, effect);
            }
            for (var iterator = session.displays.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                ChartManifest.Note note = session.notesById.get(entry.getKey());
                DisplayEntity.BlockDisplayEntity display = entry.getValue();
                if (note == null) {
                    session.recycle(display);
                    iterator.remove();
                    continue;
                }
                double noteTime = effectiveNoteTime(note, session.chart);
                if (noteTime <= songTime) {
                    if (session.hitNotes.add(note.id)) playHitSound(session, player, note);
                    session.recycle(display);
                    iterator.remove();
                    continue;
                }
                double noteDistance = session.noteDistances.getOrDefault(note.id, Double.NEGATIVE_INFINITY);
                double relativeDistance = noteDistance - currentDistance;
                double displayZ = Math.min(PLAYBACK_DISAPPEAR_Z, PLAYBACK_FRAME_Z - relativeDistance);
                if (updateDisplayPositions) {
                    // Do not queue stale client interpolation: at high speed a queued teleport can overshoot and rebound.
                    display.setTeleportDuration(0);
                    display.setInterpolationDuration(0);
                    display.setPosition(PLAYBACK_PLATFORM_X + note.x, note.y, displayZ);
                }
            }
            while (session.nextNoteIndex < session.notes.size()) {
                ChartManifest.Note note = session.notes.get(session.nextNoteIndex);
                double noteDistance = session.noteDistances.getOrDefault(note.id, Double.POSITIVE_INFINITY);
                double relativeDistance = noteDistance - currentDistance;
                if (relativeDistance > PLAYBACK_APPROACH_DISTANCE) break;
                session.nextNoteIndex++;
                if (effectiveNoteTime(note, session.chart) <= songTime) {
                    if (session.hitNotes.add(note.id)) playHitSound(session, player, note);
                    continue;
                }
                DisplayEntity.BlockDisplayEntity display = session.acquire();
                if (display == null) continue;
                display.setBlockState(blockStateForType(note.type));
                double initialRemaining = Math.max(0.0, relativeDistance);
                display.setTeleportDuration(0);
                display.setPosition(PLAYBACK_PLATFORM_X + note.x, note.y,
                    PLAYBACK_FRAME_Z - initialRemaining);
                display.setInvisible(false);
                display.setNoGravity(true);
                display.setInvulnerable(true);
                display.addCommandTag("rhythmc_preview:" + player.getUuid());
                session.displays.put(note.id, display);
            }
            if (songTime >= playbackEndTime(session.chart)) stopPlayback(server, player, true);
        }
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
            net.minecraft.sound.SoundCategory.PLAYERS, (float) Math.max(0.1, Math.min(2.0, config.noteJudgementVolumeMultiplier)), pitch);
    }
    private static void playTrackEffect(PlaybackSession session, ServerPlayerEntity player, JsonObject effect) {
        if (effect == null) return;
        String type = effect.has("type") ? effect.get("type").getAsString().toUpperCase(java.util.Locale.ROOT) : "PARTICLE";
        if (type.equals("TITLE") || type.equals("ACTIONBAR")) {
            String text = effect.has("text") ? effect.get("text").getAsString() : "";
            if (!text.isBlank()) player.sendMessage(Text.literal(text), true);
        }
        int color = effect.has("color") ? effect.get("color").getAsInt() : 0xB77BFF;
        float scale = effect.has("scale") ? effect.get("scale").getAsFloat() : 1.8f;
        int count = effect.has("count") ? Math.max(1, Math.min(64, effect.get("count").getAsInt())) : 12;
        double x = effect.has("x") ? effect.get("x").getAsDouble() : PLAYBACK_PLATFORM_X + 0.5;
        double y = effect.has("y") ? effect.get("y").getAsDouble() : PLAYBACK_PLATFORM_Y + 1.5;
        double z = effect.has("z") ? effect.get("z").getAsDouble() : PLAYBACK_PLATFORM_Z + 0.5;
        if (!type.equals("TITLE") && !type.equals("ACTIONBAR") || effect.has("particle") || type.equals("FLASH") || type.equals("PARTICLE")) {
            session.world.spawnParticles(player, new DustParticleEffect(color, scale), false, false, x, y, z, count, 0.35, 0.35, 0.35, 0.02);
        }
    }
    private static double effectSeconds(JsonObject effect, ChartManifest chart) {
        if (effect == null || !effect.has("beat") || chart.bpm <= 0.0) return Double.POSITIVE_INFINITY;
        try { return effect.get("beat").getAsDouble() * 60.0 / chart.bpm + chart.offsetMillis / 1000.0; }
        catch (RuntimeException ignored) { return Double.POSITIVE_INFINITY; }
    }
    private static void stopPlayback(MinecraftServer server, ServerPlayerEntity player, boolean returnPlayer) {
        PlaybackSession session = PLAYBACK_SESSIONS.remove(player.getUuid());
        if (session == null) return;
        for (DisplayEntity.BlockDisplayEntity display : session.displays.values()) display.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        if (returnPlayer && player.getEntityWorld() == session.world) {
            player.teleport(session.world, session.returnX, session.returnY, session.returnZ, Set.of(), session.returnYaw, session.returnPitch, false);
            player.sendMessage(Text.literal("播放结束"), true);
        }
    }
    private static final class PlaybackSession {
        private final ServerWorld world;
        private final double returnX, returnY, returnZ;
        private final float returnYaw, returnPitch;
        private final ChartManifest chart;
        private final java.util.List<ChartManifest.SpeedEvent> speedEvents;
        private final double startSeconds;
        private final long startNanos;
        private final java.util.List<ChartManifest.Note> notes;
        private final java.util.List<JsonObject> effects;
        private final Map<String, ChartManifest.Note> notesById = new java.util.HashMap<>();
        private final Map<String, Double> noteDistances = new java.util.HashMap<>();
        private int nextNoteIndex;
        private final Map<String, DisplayEntity.BlockDisplayEntity> displays = new java.util.HashMap<>();
        private final java.util.ArrayDeque<DisplayEntity.BlockDisplayEntity> displayPool = new java.util.ArrayDeque<>();
        private final java.util.Set<String> hitNotes = new java.util.HashSet<>();
        private int nextEffectIndex;
        private long lastDisplayUpdateTick = Long.MIN_VALUE;
        private boolean playedTapSoundThisTick;
        private boolean playedLookSoundThisTick;
        private PlaybackSession(ServerWorld world, double returnX, double returnY, double returnZ, float returnYaw, float returnPitch, ChartManifest chart, double startSeconds, long startNanos) {
            this.world = world; this.returnX = returnX; this.returnY = returnY; this.returnZ = returnZ; this.returnYaw = returnYaw; this.returnPitch = returnPitch; this.chart = chart; this.speedEvents = cn.frkovo.rhythmcmaker.chart.PlaybackDistance.sortedEvents(chart.speedEvents); this.startSeconds = startSeconds; this.startNanos = startNanos;
            this.notes = new java.util.ArrayList<>(chart.notes == null ? java.util.List.of() : chart.notes);
            this.effects = new java.util.ArrayList<>(chart.effects == null ? java.util.List.of() : chart.effects);
            this.effects.sort(java.util.Comparator.comparingDouble(effect -> effectSeconds(effect, chart)));
            this.notes.removeIf(note -> note == null || note.id == null);
            this.notes.sort(java.util.Comparator.comparingDouble(note -> note.time));
            for (ChartManifest.Note note : this.notes) {
                notesById.put(note.id, note);
                noteDistances.put(note.id, distanceAt(effectiveNoteTime(note, chart)));
            }
            while (nextNoteIndex < notes.size() && effectiveNoteTime(notes.get(nextNoteIndex), chart) < startSeconds) {
                hitNotes.add(notes.get(nextNoteIndex).id);
                nextNoteIndex++;
            }
            while (nextEffectIndex < effects.size() && effectSeconds(effects.get(nextEffectIndex), chart) < startSeconds) nextEffectIndex++;
        }
        private double distanceAt(double chartTime) {
            double beat = chartTime * chart.bpm / 60.0;
            return cn.frkovo.rhythmcmaker.chart.PlaybackDistance.atBeat(speedEvents, beat) * PLAYBACK_BASE_SPEED_PER_BEAT * config.playerSpeed;
        }
        private boolean shouldUpdateDisplayPositions(long serverTick) {
            if (lastDisplayUpdateTick != Long.MIN_VALUE
                    && serverTick < lastDisplayUpdateTick + PLAYBACK_DISPLAY_UPDATE_INTERVAL_TICKS) return false;
            lastDisplayUpdateTick = serverTick;
            return true;
        }
        private DisplayEntity.BlockDisplayEntity acquire() {
            DisplayEntity.BlockDisplayEntity display = displayPool.pollFirst();
            if (display != null && !display.isRemoved()) return display;
            display = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
            if (display == null) return null;
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.setTeleportDuration(0);
            display.setInterpolationDuration(0);
            display.addCommandTag("rhythmc_preview");
            world.spawnEntity(display);
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
        int x = pos.getX() - chartOffset(chart.id);
        int z = -pos.getZ() - 3;
        return x >= laneMinX(chart) && x <= laneMaxX(chart) && pos.getY() >= 65 && pos.getY() <= 68 && z >= 0 && z < calculateTrackLength(chart);
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
        for (ChartManifest.Note note : chart.notes) {
            note.z = -3.0 - (note.time * chart.bpm / 60.0) * divisionsPerChunk(chart);
            note.beat = note.time * chart.bpm / 60.0;
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
        clearChartNoteDisplays(world, chart);
        if (chart.notes == null) {
            EDITOR_NOTE_REFRESH_TASKS.remove(world.getRegistryKey());
            return;
        }
        EDITOR_NOTE_REFRESH_TASKS.put(world.getRegistryKey(), new EditorNoteRefreshTask(chart));
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
                world.spawnEntity(display);
            }
            if (task.cursor >= task.notes.size()) iterator.remove();
        }
    }
    private static final class EditorNoteRefreshTask {
        private final String chartId;
        private final java.util.List<ChartManifest.Note> notes;
        private int cursor;
        private EditorNoteRefreshTask(ChartManifest chart) {
            this.chartId = chart.id;
            this.notes = new java.util.ArrayList<>(chart.notes == null ? java.util.List.of() : chart.notes);
        }
    }
    private static void removeNoteDisplay(ServerWorld world, String tag) {
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
    private static void buildEditorArea(ServerWorld world, int x) {
        extendEditorArea(world, x, 4, 4, 3);
    }
    private static void buildEditorArea(ServerWorld world, int x, int length) {
        extendEditorArea(world, x, length, 4, 3);
    }
    private static void copyEditorTemplate(ServerWorld world, int x) {
        if (x == 0) return;
        for (int dx = -2; dx <= 2; dx++) for (int y = 63; y <= 69; y++) for (int z = -2; z >= -10; z--) {
            BlockPos source = new BlockPos(dx, y, z);
            BlockPos target = new BlockPos(x + dx, y, z);
            world.setBlockState(target, world.getBlockState(source), 3);
        }
    }
    private static void restoreEditorTrackFoundation(ServerWorld world, int x, int length, int laneCount) {
        int laneHalfWidth = laneCount / 2;
        int leftWall = -laneHalfWidth - 1;
        int rightWall = laneHalfWidth + 1;
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int dx = -maximumHalfWidth; dx <= maximumHalfWidth; dx++) for (int y = 64; y <= 69; y++) for (int z = -2; z <= 2; z++) {
            world.setBlockState(new BlockPos(x + dx, y, z), Blocks.AIR.getDefaultState(), 3);
        }
        for (int dx = leftWall; dx <= rightWall; dx++) for (int dz = -2; dz <= 2; dz++) {
            world.setBlockState(new BlockPos(x + dx, 65, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
        }
        world.setBlockState(new BlockPos(x, 65, 0), Blocks.VERDANT_FROGLIGHT.getDefaultState(), 3);
        for (int y = 66; y <= 67; y++) {
            for (int dx = leftWall; dx <= rightWall; dx++) world.setBlockState(new BlockPos(x + dx, y, 2), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + leftWall, y, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
                world.setBlockState(new BlockPos(x + rightWall, y, dz), Blocks.WHITE_CONCRETE.getDefaultState(), 3);
            }
        }
        for (int dx = leftWall; dx <= rightWall; dx++) world.setBlockState(new BlockPos(x + dx, 68, 2), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(x + leftWall, 68, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
            world.setBlockState(new BlockPos(x + rightWall, 68, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int dx = -laneHalfWidth; dx <= laneHalfWidth; dx++) for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(x + dx, 69, dz), Blocks.WHITE_STAINED_GLASS.getDefaultState(), 3);
        }
        for (int dx = leftWall; dx <= rightWall; dx++) {
            world.setBlockState(new BlockPos(x + dx, 69, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(x + dx, 65, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        for (int y = 66; y <= 68; y++) {
            world.setBlockState(new BlockPos(x + leftWall, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(x + rightWall, y, -2), Blocks.CYAN_CONCRETE.getDefaultState(), 3);
        }
        for (int beat = 0; beat < Math.max(8, length); beat++) {
            int z = -3 - beat;
            for (int dx = -laneHalfWidth; dx <= laneHalfWidth; dx++) {
                world.setBlockState(new BlockPos(x + dx, 64, z), Blocks.RED_CONCRETE.getDefaultState(), 3);
            }
            world.setBlockState(new BlockPos(x + leftWall, 64, z), Blocks.RED_CONCRETE.getDefaultState(), 3);
            world.setBlockState(new BlockPos(x + rightWall, 64, z), Blocks.RED_CONCRETE.getDefaultState(), 3);
            BlockState center = beat % 4 == 0 ? Blocks.RED_CONCRETE.getDefaultState()
                : beat % 2 == 0 ? Blocks.YELLOW_CONCRETE.getDefaultState() : Blocks.LIME_CONCRETE.getDefaultState();
            world.setBlockState(new BlockPos(x, 63, z), center, 3);
        }
    }
    private static void extendEditorArea(ServerWorld world, int x, int length) {
        extendEditorArea(world, x, length, 4, 3);
    }
    private static void extendEditorArea(ServerWorld world, int x, int length, int divisions, int laneCount) {
        int totalBeats = Math.max(8, length);
        divisions = Math.max(1, Math.min(32, divisions));
        if (!isSupportedLaneCount(laneCount)) laneCount = 3;
        final int targetDivisions = divisions;
        final int targetLaneCount = laneCount;
        EDITOR_AREA_TASKS.compute(world.getRegistryKey(), (key, existing) -> {
            if (existing == null || existing.x != x || existing.divisions != targetDivisions || existing.laneCount != targetLaneCount) {
                return new EditorAreaTask(x, totalBeats, targetDivisions, targetLaneCount);
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
            int budget = 256;
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

    private static void clearEditorTrackColumns(ServerWorld world, int x, int length) {
        int maximumHalfWidth = MAX_EDITOR_LANE_COUNT / 2 + 1;
        for (int slot = 0; slot <= Math.max(0, length); slot++) {
            int z = -3 - slot;
            for (int dx = -maximumHalfWidth; dx <= maximumHalfWidth; dx++) {
                for (int y = 64; y <= 68; y++) {
                    world.setBlockState(new BlockPos(x + dx, y, z), Blocks.AIR.getDefaultState(), 3);
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
                world.setBlockState(new BlockPos(x + dx, y, z), Blocks.RED_STAINED_GLASS.getDefaultState(), 3);
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
                if (!world.getBlockState(target).equals(desired)) world.setBlockState(target, desired, 3);
            }
        }
        BlockPos lowerTarget = new BlockPos(x, 63, targetZ);
        if (!world.getBlockState(lowerTarget).isAir()) world.setBlockState(lowerTarget, Blocks.AIR.getDefaultState(), 3);
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
    private static boolean isPlayerNoteBlock(net.minecraft.block.Block block) {
        return block == Blocks.OBSERVER || block == Blocks.REDSTONE_BLOCK || block == Blocks.DIAMOND_BLOCK || block == Blocks.TNT;
    }

    private static void legacyBuildEditorArea(ServerWorld world, int x, int length) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) world.setBlockState(new BlockPos(x + dx, 65, dz), Blocks.AIR.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) for (int y = 65; y <= 69; y++) world.setBlockState(new BlockPos(x + dx, y, -2), Blocks.RED_CONCRETE.getDefaultState());
        for (int z = -3; z > -3 - length; z--) {
            world.setBlockState(new BlockPos(x - 2, 64, z), Blocks.RED_CONCRETE.getDefaultState());
            world.setBlockState(new BlockPos(x + 2, 64, z), Blocks.RED_CONCRETE.getDefaultState());
            world.setBlockState(new BlockPos(x, 64, z), Blocks.RED_CONCRETE.getDefaultState());
            world.setBlockState(new BlockPos(x, 63, z), ((-z - 3) % 4 == 0) ? Blocks.RED_CONCRETE.getDefaultState() : ((-z - 3) % 2 == 0 ? Blocks.YELLOW_CONCRETE.getDefaultState() : Blocks.LIME_CONCRETE.getDefaultState()));
        }
    }
    private static Item registerMenuItem(String id, String name) {
        Identifier identifier = Identifier.of(MOD_ID, id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, identifier);
        return Registry.register(Registries.ITEM, key, new MenuItem(new Item.Settings().registryKey(key).maxCount(1), name));
    }

    private static final class MenuItem extends Item {
        private final Text name;

        private MenuItem(Settings settings, String name) {
            super(settings);
            this.name = Text.literal(name).setStyle(Style.EMPTY.withColor(Formatting.WHITE).withItalic(false));
        }

        @Override
        public Text getName(ItemStack stack) {
            return name;
        }
    }
}
