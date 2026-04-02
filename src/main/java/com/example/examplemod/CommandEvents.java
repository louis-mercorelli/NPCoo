/**
 * File: CommandEvents.java
 *
 * Main intent:
 * Defines CommandEvents functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code onCommandsRegister(...)}:
 *    Purpose: Registers the full testmod command tree and wires each branch to the appropriate handler.
 *    Input: RegisterCommandsEvent event.
 *    Output: void.
 * 2) {@code onServerTick(...)}:
 *    Purpose: Captures tick-start timing data used by the server-load monitor.
 *    Input: net.minecraftforge.event.TickEvent.ServerTickEvent.Pre event.
 *    Output: void.
 * 3) {@code onServerTick(...)}:
 *    Purpose: Runs per-tick post-processing such as server-load tracking and shared SteveAI orchestration.
 *    Input: net.minecraftforge.event.TickEvent.ServerTickEvent.Post event.
 *    Output: void.
 * 4) {@code onPlayerLoggedIn(...)}:
 *    Purpose: Records the most recent player identity so later SteveAI actions can target that player.
 *    Input: PlayerEvent.PlayerLoggedInEvent event.
 *    Output: void.
 */
package com.example.examplemod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import com.example.examplemod.gui.CEHGuiInventory;
import com.sai.InventoryService;
import com.example.examplemod.chat.CEHChat;
import com.example.examplemod.poi.CEHPoi;
import com.example.examplemod.scan.CEHScan;
import com.example.examplemod.scan.SteveAiCollectors;
import com.example.examplemod.steveAI.CEHExplore;
import com.example.examplemod.steveAI.CEHFollow;
import com.example.examplemod.steveAI.CEHNavigationChunk;
import com.example.examplemod.steveAI.SteveAiLocator;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandEvents {
    public static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    public static UUID lastPlayerUuid = null;
    public static String lastPlayerName = null;
    public static boolean steveAiFollowMode = false;
    public static UUID steveAiFollowPlayerUuid = null;
    public static long lastFollowTick = 0L;
    public static boolean steveAiChunkForceEnabled = true;
    public static net.minecraft.core.BlockPos lastSteveAiKnownPos = null;
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastSteveAiKnownDimension = null;
    public static boolean exploreActive = false;
    public static String explorePoi = "";
    public static String explorePoiType = "";
    public static BlockPos exploreCenter = null;
    public static int exploreRadius = 24;
    public static BlockPos currentExploreTarget = null;
    public static long nextExploreRepathGameTime = 0L;
    public static final Set<BlockPos> exploredTargets = new HashSet<>();
    public static final long PERIODIC_SCAN_INTERVAL_TICKS = 1200L;
    public static final long MAX_PERIODIC_SCAN_MS_PER_TICK = 1000L;
    public static final int PERIODIC_SCAN_CHUNK_RADIUS = 10;
    public static final int PERIODIC_SCAN_BLOCK_RADIUS = PERIODIC_SCAN_CHUNK_RADIUS * 16;
    public static final int PERIODIC_TILE_BLOCK_WIDTH = 3 * 16; // 3 chunks = 48 blocks per tile
    public static final Deque<PeriodicScanJob> periodicScanQueue = new ArrayDeque<>();
    public static boolean periodicScanCycleActive = false;
    public static boolean initialMapBuildStarted = false;
    public static boolean initialMapBuildCompleted = false;
    public static boolean screenDebugEnabled = true;
    public static long periodicScanCycleStartNs = 0L;
    public static String periodicScanCycleStartTs = "";
    public static int periodicScanCycleCount = 0;

    public enum PeriodicScanPhase {
        LOCATION_AND_WORLD,
        ENTITIES,
        BLOCKS,
        BLOCK_ENTITIES,
        SUMMARY
    }

    public static final class PeriodicScanJob {
        public final ServerLevel serverLevel;
        public final Entity steveAi;
        public PeriodicScanPhase phase = PeriodicScanPhase.LOCATION_AND_WORLD;
        // Tile grid for incremental scanning (each entry: [minX, minZ, maxX, maxZ])
        public List<int[]> tiles = new java.util.ArrayList<>();
        public int entityTileIdx = 0;
        public int blockTileIdx = 0;
        public int beTileIdx = 0;
        public int totalTiles = 0;
        public int originY = 0;
        // Accumulated results across all tiles
        public Map<String, SteveAiCollectors.SeenSummary> groupedBlocks = new LinkedHashMap<>();
        public Map<String, SteveAiCollectors.SeenSummary> groupedEntities = new LinkedHashMap<>();
        public Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities = new LinkedHashMap<>();

        public PeriodicScanJob(ServerLevel serverLevel, Entity steveAi) {
            this.serverLevel = serverLevel;
            this.steveAi = steveAi;
        }
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.onCommandsRegister START"));
        try {
            event.getDispatcher().register(
                Commands.literal("testmod")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[testmod] The mod command system is working!"), false);
                        LOGGER.info(com.sai.NpcooLog.tag("ExampleMod command executed with no message"));
                        return 1;
                    })
                    .then(Commands.literal("lookSee").executes(CEHLookSee::handleLookSee))
                    .then(Commands.literal("explore")
                        .then(Commands.argument("poi", StringArgumentType.word()).executes(CEHExplore::handleExplorePoi)))
                    .then(Commands.literal("exploreStop").executes(CEHExplore::handleExploreStop))
                    .then(Commands.literal("exploreStatus").executes(CEHExplore::handleExploreStatus))
                    .then(Commands.literal("debugON").executes(ctx -> CEHDebug.handleDebugScreenToggle(ctx, true)))
                    .then(Commands.literal("debugOFF").executes(ctx -> CEHDebug.handleDebugScreenToggle(ctx, false)))
                    .then(Commands.literal("scanSAI")
                        .executes(ctx -> CEHScan.handleScanSai(ctx, "all", 2, false))
                        .then(Commands.literal("detail")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                            .executes(ctx -> CEHScan.handleDetailSaiAtPos(ctx,
                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                                IntegerArgumentType.getInteger(ctx, "radius"))))))))
                        .then(Commands.literal("detailSAI")
                            .executes(ctx -> CEHScan.handleDetailSaiAtSteve(ctx, 1))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> CEHScan.handleDetailSaiAtSteve(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.argument("scanArgs", StringArgumentType.greedyString()).executes(CEHScan::handleScanSaiArgs)))
                    .then(Commands.literal("scanB")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanB",
                                            new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                            IntegerArgumentType.getInteger(ctx, "chunkRadius"), false))
                                        .then(Commands.literal("ForceLoad")
                                            .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanB",
                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                                IntegerArgumentType.getInteger(ctx, "chunkRadius"), true))))))))
                    .then(Commands.literal("scanE")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanE",
                                            new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                            IntegerArgumentType.getInteger(ctx, "chunkRadius"), false))
                                        .then(Commands.literal("ForceLoad")
                                            .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanE",
                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                                IntegerArgumentType.getInteger(ctx, "chunkRadius"), true))))))))
                    .then(Commands.literal("scanBE")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanBE",
                                            new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                            IntegerArgumentType.getInteger(ctx, "chunkRadius"), false))
                                        .then(Commands.literal("ForceLoad")
                                            .executes(ctx -> CEHScan.handleDirectCenteredScan(ctx, "scanBE",
                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                                IntegerArgumentType.getInteger(ctx, "chunkRadius"), true))))))))
                    .then(Commands.literal("scanStatus").executes(CEHScan::handleScanStatus))
                    .then(Commands.literal("serverLoad")
                        .executes(CEHServerLoad::handleServerLoadStatus)
                        .then(Commands.literal("stream")
                            .executes(CEHServerLoad::handleServerLoadStreamStatus)
                            .then(Commands.literal("on").executes(ctx -> CEHServerLoad.handleServerLoadStreamToggle(ctx, true)))
                            .then(Commands.literal("off").executes(ctx -> CEHServerLoad.handleServerLoadStreamToggle(ctx, false))))
                        .then(Commands.literal("set")
                            .then(Commands.argument("idleMspt", DoubleArgumentType.doubleArg(0.1, ServerLoad.SERVER_TICK_BUDGET_MS))
                                .then(Commands.argument("busyMspt", DoubleArgumentType.doubleArg(0.1, ServerLoad.SERVER_TICK_BUDGET_MS))
                                    .then(Commands.argument("behindDebtMs", DoubleArgumentType.doubleArg(1.0, 5000.0))
                                        .executes(CEHServerLoad::handleServerLoadTune)
                                        .then(Commands.argument("emaAlpha", DoubleArgumentType.doubleArg(0.01, 1.0))
                                            .executes(CEHServerLoad::handleServerLoadTune))))))
                        .then(Commands.literal("reset").executes(CEHServerLoad::handleServerLoadReset)))
                    .then(Commands.literal("poiStage1").executes(CEHPoi::handlePoiUpdate))
                    .then(Commands.literal("poiUpdate").executes(CEHPoi::handlePoiUpdate))
                    .then(Commands.literal("poiStage2")
                        .executes(ctx -> CEHPoi.handlePoiConfirmCandidates(ctx, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                            .executes(ctx -> CEHPoi.handlePoiConfirmCandidates(ctx, IntegerArgumentType.getInteger(ctx, "limit")))))
                    .then(Commands.literal("poiConfirmCandidates")
                        .executes(ctx -> CEHPoi.handlePoiConfirmCandidates(ctx, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                            .executes(ctx -> CEHPoi.handlePoiConfirmCandidates(ctx, IntegerArgumentType.getInteger(ctx, "limit")))))
                    .then(Commands.literal("poiReset").executes(CEHPoi::handlePoiReset))
                    .then(Commands.literal("writeT")
                        .executes(CEHWrite::handleWriteT)
                        .then(Commands.argument("suffix", StringArgumentType.greedyString()).executes(CEHWrite::handleWriteT)))
                    .then(Commands.literal("writeTD")
                        .executes(CEHWrite::handleWriteTD)
                        .then(Commands.argument("suffix", StringArgumentType.greedyString()).executes(CEHWrite::handleWriteTD)))
                    .then(Commands.literal("forceChunkOn").executes(CEHNavigationChunk::handleForceChunkOn))
                    .then(Commands.literal("forceChunkOff").executes(CEHNavigationChunk::handleForceChunkOff))
                    .then(Commands.literal("whereRu").executes(CEHNavigationChunk::handleWhereRu))
                    .then(Commands.literal("tele").executes(CEHNavigationChunk::handleTele))
                    .then(Commands.literal("followMe").executes(CEHFollow::handleFollowMe))
                    .then(Commands.literal("findMe").executes(CEHFollow::handleFindMe))
                    .then(Commands.literal("stopFollow").executes(CEHFollow::handleStopFollow))
                    .then(Commands.literal("opengui").executes(CEHGuiInventory::handleOpenGui))
                    .then(Commands.literal("inv").executes(CEHGuiInventory::handleInv))
                    .then(Commands.literal("invAdd")
                        .then(Commands.argument("itemName", StringArgumentType.word())
                            .executes(ctx -> InventoryService.handleInvAdd(ctx, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> InventoryService.handleInvAdd(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                    .then(Commands.literal("invDrop")
                        .then(Commands.argument("itemName", StringArgumentType.word())
                            .executes(ctx -> InventoryService.handleInvDrop(ctx, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> InventoryService.handleInvDrop(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(Commands.literal("writeNow").executes(CEHWrite::handleWriteNow))
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(CEHChat::handleSteveAiChat))
            );
        } finally {
            LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.onCommandsRegister FINISH"));
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Pre event) {
        ServerLoad.onServerTickPre();
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
        var server = event.server();
        if (server.overworld() == null) {
            return;
        }

        ServerLoad.onServerTickPost(server);

        if (steveAiFollowMode && steveAiFollowPlayerUuid != null) {
            long now = server.overworld().getGameTime();

            if (now - lastFollowTick >= 40) {
                lastFollowTick = now;

                ServerPlayer followPlayer = server.getPlayerList().getPlayer(steveAiFollowPlayerUuid);
                if (followPlayer != null) {
                    Villager steveAi = SteveAiLocator.findSteveAi((ServerLevel) followPlayer.level());
                    if (steveAi != null) {
                        double distSq = steveAi.distanceToSqr(followPlayer);

                        if (distSq > 9.0) {
                            boolean started = steveAi.getNavigation().moveTo(followPlayer, 1.0D);
                            LOGGER.info(com.sai.NpcooLog.tag("steveAI follow tick repath result={} distSq={}"), started, distSq);
                        } else {
                            steveAi.getNavigation().stop();
                        }
                    }
                }
            }
        }

        if (lastPlayerUuid == null) {
            return;
        }

        // Temporarily disable automatic periodic scan loop from server tick.
        // OldPeriodScan.maybeStartPeriodicScan(server);
        // OldPeriodScan.processPeriodicScanQueue();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.onPlayerLoggedIn START "));
        try {
            var player = event.getEntity();
            var level = player.level();
            lastPlayerUuid = player.getUUID();
            lastPlayerName = player.getName().getString();
            LOGGER.info(com.sai.NpcooLog.tag("Tracking steveAI file for player UUID: {}"), lastPlayerUuid);

            LOGGER.info(com.sai.NpcooLog.tag("Player UUID: {}"), player.getUUID());
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            LOGGER.info(com.sai.NpcooLog.tag("Player {} logged in"), player.getName().getString());

            if (SteveAiLocator.findSteveAiAnywhere(serverLevel) != null) {
                LOGGER.info(com.sai.NpcooLog.tag("steveAI already exists somewhere in the world, skipping spawn"));
                // TODO: re-enable once chunks are confirmed loaded at login
                // OldPeriodScan.forceStartPeriodicScanNow(serverLevel.getServer());
                return;
            }

            double angle = Math.random() * 2 * Math.PI;
            double distance = 4.0;
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            double spawnX = player.getX() + offsetX;
            double spawnY = player.getY();
            double spawnZ = player.getZ() + offsetZ;

            var steveAi = EntityType.VILLAGER.create(serverLevel, EntitySpawnReason.EVENT);
            if (steveAi instanceof Villager villager) {
                villager.setPos(spawnX, spawnY, spawnZ);
                villager.setCustomName(Component.literal(SteveAiLocator.STEVE_AI_NAME));
                villager.setCustomNameVisible(true);
                villager.setPersistenceRequired();
                villager.addTag(SteveAiLocator.STEVE_AI_TAG);

                villager.setVillagerData(
                    villager.getVillagerData()
                        .withProfession(serverLevel.registryAccess(), VillagerProfession.FARMER)
                        .withLevel(2)
                );

                serverLevel.addFreshEntity(villager);
                LOGGER.info(com.sai.NpcooLog.tag("Spawned steveAI at {}, {}, {}"), spawnX, spawnY, spawnZ);
                // TODO: re-enable once chunks are confirmed loaded at login
                // OldPeriodScan.forceStartPeriodicScanNow(serverLevel.getServer());
            } else {
                LOGGER.warn(com.sai.NpcooLog.tag("Failed to create villager entity for steveAI"));
            }
        } finally {
            LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.onPlayerLoggedIn FINISH"));
        }
    }
}
