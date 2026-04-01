package com.example.examplemod;

import com.example.examplemod.SteveAiCollectors.SeenSummary;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import com.sai.InventoryService;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STEVE_AI_NAME = "steveAI";
    private static final String STEVE_AI_TAG = "steveai_npc";
    private static UUID lastPlayerUuid = null;
    private static String lastPlayerName = null;
    private static net.minecraft.core.BlockPos lastEntityScanCenter = null;
    private static net.minecraft.core.BlockPos lastBlockScanCenter = null;
    private static net.minecraft.core.BlockPos lastBlockEntityScanCenter = null;
    private static final double ENTITY_SCAN_MOVE_THRESHOLD = 20.0;
    private static final double BLOCK_SCAN_MOVE_THRESHOLD = 20.0;
    private static final double BLOCK_ENTITY_SCAN_MOVE_THRESHOLD = 20.0;
    private static boolean steveAiFollowMode = false;
    private static UUID steveAiFollowPlayerUuid = null;
    private static long lastFollowTick = 0L;
    private static boolean steveAiChunkForceEnabled = true;
    private static Integer forcedSteveAiChunkX = null;
    private static Integer forcedSteveAiChunkZ = null;
    private static net.minecraft.core.BlockPos lastSteveAiKnownPos = null;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastSteveAiKnownDimension = null;
    private static boolean exploreActive = false;
    private static String explorePoi = "";
    private static String explorePoiType = "";
    private static BlockPos exploreCenter = null;
    private static int exploreRadius = 24;
    private static BlockPos currentExploreTarget = null;
    private static long nextExploreRepathGameTime = 0L;
    private static final Set<BlockPos> exploredTargets = new HashSet<>();
    private static final long PERIODIC_SCAN_INTERVAL_TICKS = 1200L;
    private static final long MAX_PERIODIC_SCAN_MS_PER_TICK = 1000L;
    private static final int PERIODIC_SCAN_CHUNK_RADIUS = 10;
    private static final int PERIODIC_SCAN_BLOCK_RADIUS = PERIODIC_SCAN_CHUNK_RADIUS * 16;
    private static final int PERIODIC_TILE_BLOCK_WIDTH = 3 * 16; // 3 chunks = 48 blocks per tile
    private static final Deque<PeriodicScanJob> periodicScanQueue = new ArrayDeque<>();
    private static boolean periodicScanCycleActive = false;
    private static boolean periodicScanNotifyThisCycle = false;
    private static boolean initialMapBuildStarted = false;
    private static boolean initialMapBuildCompleted = false;
    private static boolean screenDebugEnabled = true;
    private static long periodicScanCycleStartNs = 0L;
    private static String periodicScanCycleStartTs = "";
    private static int periodicScanCycleCount = 0;

    private enum PeriodicScanPhase {
        LOCATION_AND_WORLD,
        ENTITIES,
        BLOCKS,
        BLOCK_ENTITIES,
        SUMMARY
    }

    private static final class PeriodicScanJob {
        private final ServerLevel serverLevel;
        private final Entity steveAi;
        private PeriodicScanPhase phase = PeriodicScanPhase.LOCATION_AND_WORLD;
        private boolean poiChanged = false;
        private BlockPos blockEntityScanCenter = null;
        // Tile grid for incremental scanning (each entry: [minX, minZ, maxX, maxZ])
        List<int[]> tiles = new java.util.ArrayList<>();
        int entityTileIdx = 0;
        int blockTileIdx = 0;
        int beTileIdx = 0;
        int totalTiles = 0;
        int originY = 0;
        // Accumulated results across all tiles
        Map<String, SteveAiCollectors.SeenSummary> groupedBlocks = new LinkedHashMap<>();
        Map<String, SteveAiCollectors.SeenSummary> groupedEntities = new LinkedHashMap<>();
        Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities = new LinkedHashMap<>();

        PeriodicScanJob(ServerLevel serverLevel, Entity steveAi) {
            this.serverLevel = serverLevel;
            this.steveAi = steveAi;
        }
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        LOGGER.info("CommandEvents.onCommandsRegister START");
        try {
            event.getDispatcher().register(
                Commands.literal("testmod")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        source.sendSuccess(
                            () -> Component.literal("§6[testmod] The mod command system is working!"),
                            false
                        );
                        LOGGER.info("ExampleMod command executed with no message");
                        return 1;
                    })
                    .then(Commands.literal("lookSee")
                        .executes(CommandEvents::handleLookSee)
                    )
                    .then(Commands.literal("explore")
                        .then(Commands.argument("poi", StringArgumentType.word())
                            .executes(CommandEvents::handleExplorePoi)
                        )
                    )
                    .then(Commands.literal("exploreStop")
                        .executes(CommandEvents::handleExploreStop)
                    )
                    .then(Commands.literal("exploreStatus")
                        .executes(CommandEvents::handleExploreStatus)
                    )
                    .then(Commands.literal("debugON")
                        .executes(ctx -> handleDebugScreenToggle(ctx, true))
                    )
                    .then(Commands.literal("debugOFF")
                        .executes(ctx -> handleDebugScreenToggle(ctx, false))
                    )
                    .then(Commands.literal("scanSAI")
                        .executes(ctx -> handleScanSai(ctx, "all", 2, false))
                        .then(Commands.literal("detail")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                            .executes(ctx -> handleDetailSaiAtPos(
                                                ctx,
                                                new BlockPos(
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "y"),
                                                    IntegerArgumentType.getInteger(ctx, "z")
                                                ),
                                                IntegerArgumentType.getInteger(ctx, "radius")
                                            ))
                                        )
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("detailSAI")
                            .executes(ctx -> handleDetailSaiAtSteve(ctx, 1))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> handleDetailSaiAtSteve(
                                    ctx,
                                    IntegerArgumentType.getInteger(ctx, "radius")
                                ))
                            )
                        )
                        .then(Commands.argument("scanArgs", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                String tail = StringArgumentType.getString(ctx, "scanArgs");

                                ParsedScanSaiArgs parsed;
                                try {
                                    parsed = parseScanSaiArgs(tail, 2);
                                } catch (Exception e) {
                                    source.sendFailure(Component.literal("scanSAI parse error: " + e.getMessage()));
                                    return 0;
                                }

                                return handleScanSai(ctx, parsed.rawScanInput, parsed.chunkRadius, parsed.fastMode);
                            })
                        )
                    )
                    .then(Commands.literal("scanStatus")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                () -> Component.literal(SteveAiScanManager.getStatusText()),
                                false
                            );
                            return 1;
                        })
                    )
                    .then(Commands.literal("poiStage1")
                        .executes(CommandEvents::handlePoiUpdate)
                    )
                    .then(Commands.literal("poiUpdate")
                        .executes(CommandEvents::handlePoiUpdate)
                    )
                    .then(Commands.literal("poiStage2")
                        .executes(ctx -> handlePoiConfirmCandidates(ctx, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                            .executes(ctx -> handlePoiConfirmCandidates(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "limit")
                            ))
                        )
                    )
                    .then(Commands.literal("poiConfirmCandidates")
                        .executes(ctx -> handlePoiConfirmCandidates(ctx, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                            .executes(ctx -> handlePoiConfirmCandidates(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "limit")
                            ))
                        )
                    )
                    .then(Commands.literal("poiReset")
                        .executes(CommandEvents::handlePoiReset)
                    )
                    .then(Commands.literal("writeT")
                        .executes(context -> {
                            try {
                                ServerLevel serverLevel = context.getSource().getLevel();
                                Path folder = SteveAiScanManager.writeTextFiles(serverLevel, "");

                                context.getSource().sendSuccess(
                                    () -> Component.literal("SteveAI scan text files written to: " + folder.toAbsolutePath()),
                                    false
                                );
                                return 1;
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.literal("writeT failed: " + e.getMessage()));
                                return 0;
                            }
                        })
                        .then(Commands.argument("suffix", StringArgumentType.greedyString())
                            .executes(context -> {
                                try {
                                    ServerLevel serverLevel = context.getSource().getLevel();
                                    String suffix = StringArgumentType.getString(context, "suffix");
                                    Path folder = SteveAiScanManager.writeTextFiles(serverLevel, suffix);

                                    context.getSource().sendSuccess(
                                        () -> Component.literal("SteveAI scan text files written to: " + folder.toAbsolutePath()),
                                        false
                                    );
                                    return 1;
                                } catch (Exception e) {
                                    context.getSource().sendFailure(Component.literal("writeT failed: " + e.getMessage()));
                                    return 0;
                                }
                            })
                        )
                    )
                    .then(Commands.literal("writeTD")
                        .executes(context -> {
                            try {
                                ServerLevel serverLevel = context.getSource().getLevel();
                                Path folder = SteveAiScanManager.writeDetailTextFiles(serverLevel, "");

                                context.getSource().sendSuccess(
                                    () -> Component.literal("SteveAI detail text files written to: " + folder.toAbsolutePath()),
                                    false
                                );
                                return 1;
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.literal("writeTD failed: " + e.getMessage()));
                                return 0;
                            }
                        })
                        .then(Commands.argument("suffix", StringArgumentType.greedyString())
                            .executes(context -> {
                                try {
                                    ServerLevel serverLevel = context.getSource().getLevel();
                                    String suffix = StringArgumentType.getString(context, "suffix");
                                    Path folder = SteveAiScanManager.writeDetailTextFiles(serverLevel, suffix);

                                    context.getSource().sendSuccess(
                                        () -> Component.literal("SteveAI detail text files written to: " + folder.toAbsolutePath()),
                                        false
                                    );
                                    return 1;
                                } catch (Exception e) {
                                    context.getSource().sendFailure(Component.literal("writeTD failed: " + e.getMessage()));
                                    return 0;
                                }
                            })
                        )
                    )
                    .then(Commands.literal("forceChunkOn")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            Villager steveAi = findSteveAi((ServerLevel) player.level());
                            if (steveAi == null) {
                                source.sendFailure(Component.literal("Could not find steveAI in loaded chunks."));
                                return 0;
                            }

                            steveAiChunkForceEnabled = true;
                            updateForcedChunkForSteveAi((ServerLevel) player.level(), steveAi);

                            source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing enabled."), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("forceChunkOff")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            steveAiChunkForceEnabled = false;
                            clearForcedSteveAiChunk((ServerLevel) player.level());

                            source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing disabled."), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("whereRu")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            ServerLevel serverLevel = (ServerLevel) player.level();
                            Villager steveAi = findSteveAi(serverLevel);

                            if (steveAi != null) {
                                String msg = String.format(
                                    "§6[testmod] steveAI is loaded now at dimension=%s x=%.2f y=%.2f z=%.2f",
                                    steveAi.level().dimension(),
                                    steveAi.getX(),
                                    steveAi.getY(),
                                    steveAi.getZ()
                                );

                                source.sendSuccess(() -> Component.literal(msg), false);

                                LOGGER.info(
                                    "whereRu loaded -> dimension={} x={} y={} z={}",
                                    steveAi.level().dimension(),
                                    steveAi.getX(),
                                    steveAi.getY(),
                                    steveAi.getZ()
                                );

                                return 1;
                            }

                            if (lastSteveAiKnownPos != null && lastSteveAiKnownDimension != null) {
                                String msg = String.format(
                                    "§6[testmod] steveAI is not loaded. Last known position: dimension=%s x=%d y=%d z=%d",
                                    lastSteveAiKnownDimension,
                                    lastSteveAiKnownPos.getX(),
                                    lastSteveAiKnownPos.getY(),
                                    lastSteveAiKnownPos.getZ()
                                );

                                source.sendSuccess(() -> Component.literal(msg), false);

                                LOGGER.info(
                                    "whereRu unloaded -> last known dimension={} x={} y={} z={}",
                                    lastSteveAiKnownDimension,
                                    lastSteveAiKnownPos.getX(),
                                    lastSteveAiKnownPos.getY(),
                                    lastSteveAiKnownPos.getZ()
                                );

                                return 1;
                            }

                            source.sendFailure(Component.literal("Could not find steveAI, and no last known position is recorded."));
                            return 0;
                        })
                    )
                    .then(Commands.literal("tele")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            ServerLevel serverLevel = (ServerLevel) player.level();
                            Villager steveAi = findSteveAi(serverLevel);

                            if (steveAi == null) {
                                source.sendFailure(Component.literal("Could not find steveAI."));
                                return 0;
                            }

                            BlockPos safePos = findSafeTeleportPosNearPlayer(serverLevel, player);

                            if (safePos == null) {
                                source.sendFailure(Component.literal("Could not find a safe place to teleport steveAI."));
                                return 0;
                            }

                            steveAi.teleportTo(
                                safePos.getX() + 0.5,
                                safePos.getY(),
                                safePos.getZ() + 0.5
                            );

                            steveAi.getNavigation().stop();

                            source.sendSuccess(
                                () -> Component.literal(String.format(
                                    "§6[testmod] steveAI teleported to x=%d y=%d z=%d",
                                    safePos.getX(),
                                    safePos.getY(),
                                    safePos.getZ()
                                )),
                                false
                            );

                            LOGGER.info(
                                "steveAI teleported safely to x={}, y={}, z={}",
                                safePos.getX(),
                                safePos.getY(),
                                safePos.getZ()
                            );

                            return 1;
                        })
                    )
                    .then(Commands.literal("followMe")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            ServerLevel serverLevel = (ServerLevel) player.level();
                            Villager steveAi = findSteveAi(serverLevel);

                            if (steveAi == null) {
                                source.sendFailure(Component.literal("Could not find steveAI."));
                                return 0;
                            }

                            steveAiFollowMode = true;
                            steveAiFollowPlayerUuid = player.getUUID();

                            boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);

                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] steveAI is now following you."),
                                false
                            );

                            LOGGER.info("steveAI followMe enabled for player {}", player.getName().getString());
                            LOGGER.info("Initial follow path start result: {}", started);
                            return 1;
                        })
                    )
                    .then(Commands.literal("findMe")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            ServerLevel serverLevel = (ServerLevel) player.level();
                            Villager steveAi = findSteveAi(serverLevel);

                            if (steveAi == null) {
                                source.sendFailure(Component.literal("Could not find steveAI."));
                                return 0;
                            }

                            boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);

                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] steveAI is trying to find you."),
                                false
                            );

                            LOGGER.info("steveAI findMe started for player {} result={}", player.getName().getString(), started);
                            return 1;
                        })
                    )
                    .then(Commands.literal("stopFollow")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            steveAiFollowMode = false;
                            steveAiFollowPlayerUuid = null;

                            if (source.getEntity() instanceof ServerPlayer player) {
                                Villager steveAi = findSteveAi((ServerLevel) player.level());
                                if (steveAi != null) {
                                    steveAi.getNavigation().stop();
                                }
                            }

                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] steveAI follow stopped."),
                                false
                            );

                            LOGGER.info("steveAI follow mode disabled");
                            return 1;
                        })
                    )
                    .then(Commands.literal("opengui")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {

                                MerchantOffers offers = new MerchantOffers();
                                offers.add(new MerchantOffer(
                                    new ItemCost(Items.OAK_LOG, 1),
                                    Optional.empty(),
                                    new ItemStack(Items.APPLE, 1),
                                    9999,
                                    1,
                                    0.05f
                                ));

                                java.util.OptionalInt containerId = player.openMenu(new net.minecraft.world.MenuProvider() {

                                    @Override
                                    public Component getDisplayName() {
                                        return Component.literal("SteveAI");
                                    }

                                    @Override
                                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                                            int containerId,
                                            net.minecraft.world.entity.player.Inventory playerInventory,
                                            net.minecraft.world.entity.player.Player pPlayer) {
                                        LOGGER.info("CommandEvents.opengui ### CREATE MENU ### called ");
                                        return new SteveAiMenu(containerId, playerInventory);
                                    }
                                });

                                if (containerId.isPresent()) {
                                    LOGGER.info("Sending merchant offers to client, count={}", offers.size());

                                    player.sendMerchantOffers(
                                        containerId.getAsInt(),
                                        offers,
                                        2,
                                        0,
                                        false,
                                        false
                                    );
                                } else {
                                    LOGGER.warn("openMenu returned empty OptionalInt");
                                }
                            }

                            return 1;
                        })
                    )
                    .then(Commands.literal("inv")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
                                source.sendFailure(Component.literal("Not on server level."));
                                return 0;
                            }

                            Villager steveAi = findSteveAi(serverLevel);
                            if (steveAi == null) {
                                source.sendFailure(Component.literal("SteveAI not found."));
                                return 0;
                            }

                            String summary = InventoryService.getInventorySummary(steveAi);

                            source.sendSuccess(
                                () -> Component.literal("SteveAI inventory: " + summary),
                                false
                            );

                            LOGGER.info("SteveAI inventory command -> {}", summary);
                            return 1;
                        })
                    )
                    .then(Commands.literal("invAdd")
                        .then(Commands.argument("itemName", StringArgumentType.word())
                            .executes(context -> InventoryService.handleInvAdd(context, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> InventoryService.handleInvAdd(
                                    context,
                                    IntegerArgumentType.getInteger(context, "count")
                                ))
                            )
                        )
                    )
                    .then(Commands.literal("invDrop")
                        .then(Commands.argument("itemName", StringArgumentType.word())
                            .executes(context -> InventoryService.handleInvDrop(context, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    return InventoryService.handleInvDrop(context, count);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("writeNow")
                        .executes(CommandEvents::handleWriteNow)
                    )
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            String message = StringArgumentType.getString(context, "message");

                            LOGGER.info("TESTMOD asking OpenAI...: {}", message);

                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] Asking OpenAI: " + message),
                                false
                            );

                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Player only command."));
                                return 0;
                            }

                            ServerLevel serverLevel = (ServerLevel) player.level();
                            UUID playerUuid = player.getUUID();

                            String reply = askSteveAi(serverLevel, playerUuid, message);
                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] OpenAI reply: " + reply),
                                false
                            );

                            LOGGER.info("ExampleMod OpenAI response... {}", reply);
                            return 1;
                        })
                    )
            );
        } finally {
            LOGGER.info("CommandEvents.onCommandsRegister FINISH");
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
        var server = event.server();
        if (server.overworld() == null) {
            return;
        }

        if (steveAiFollowMode && steveAiFollowPlayerUuid != null) {
            long now = server.overworld().getGameTime();

            if (now - lastFollowTick >= 40) {
                lastFollowTick = now;

                ServerPlayer followPlayer = server.getPlayerList().getPlayer(steveAiFollowPlayerUuid);
                if (followPlayer != null) {
                    Villager steveAi = findSteveAi((ServerLevel) followPlayer.level());
                    if (steveAi != null) {
                        double distSq = steveAi.distanceToSqr(followPlayer);

                        if (distSq > 9.0) {
                            boolean started = steveAi.getNavigation().moveTo(followPlayer, 1.0D);
                            LOGGER.info("steveAI follow tick repath result={} distSq={}", started, distSq);
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

        maybeStartPeriodicScan(server);
        processPeriodicScanQueue();
    }

    private static void maybeStartPeriodicScan(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % PERIODIC_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!periodicScanQueue.isEmpty()) {
            return;
        }

        int queued = enqueuePeriodicScanJobs(server);
        if (queued > 0) {
            startPeriodicScanCycle(queued, false);
        }
    }

    private static void forceStartPeriodicScanNow(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        if (!periodicScanQueue.isEmpty()) {
            return;
        }

        int queued = enqueuePeriodicScanJobs(server);
        if (queued > 0) {
            startPeriodicScanCycle(queued, true);
        }
    }

    private static void startPeriodicScanCycle(int queued, boolean forced) {
        periodicScanCycleActive = true;
        periodicScanNotifyThisCycle = true;
        periodicScanCycleCount++;
        periodicScanCycleStartNs = System.nanoTime();
        periodicScanCycleStartTs = scanTs();

        if (!initialMapBuildStarted) {
            initialMapBuildStarted = true;
        }

        if (forced) {
            LOGGER.info("Periodic scan force-started: cycle={} queuedJobs={} budgetMs={}", periodicScanCycleCount, queued, MAX_PERIODIC_SCAN_MS_PER_TICK);
        } else {
            LOGGER.info("Periodic scan cycle started: cycle={} queuedJobs={} budgetMs={}", periodicScanCycleCount, queued, MAX_PERIODIC_SCAN_MS_PER_TICK);
        }

        notifyPeriodicScanStarted(queued);
    }

    private static int enqueuePeriodicScanJobs(net.minecraft.server.MinecraftServer server) {
        int queued = 0;
        for (ServerLevel serverLevel : server.getAllLevels()) {
            var matches = serverLevel.getEntities(
                (Entity) null,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                CommandEvents::isSteveAi
            );

            for (Entity entity : matches) {
                periodicScanQueue.addLast(new PeriodicScanJob(serverLevel, entity));
                queued++;
            }
        }
        return queued;
    }

    private static void processPeriodicScanQueue() {
        if (periodicScanQueue.isEmpty()) {
            return;
        }

        int queueSizeBefore = periodicScanQueue.size();

        long startNs = System.nanoTime();
        long deadlineNs = startNs + (MAX_PERIODIC_SCAN_MS_PER_TICK * 1_000_000L);
        int steps = 0;

        while (!periodicScanQueue.isEmpty() && System.nanoTime() < deadlineNs) {
            PeriodicScanJob job = periodicScanQueue.pollFirst();
            if (job == null) {
                break;
            }

            if (!job.steveAi.isAlive()) {
                continue;
            }

            processPeriodicScanJobPhase(job);
            steps++;

            if (job.phase != null) {
                periodicScanQueue.addLast(job);
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        LOGGER.info("Periodic scan tick processed: steps={} remainingJobs={} elapsedMs={} budgetMs={}",
            steps,
            periodicScanQueue.size(),
            elapsedMs,
            MAX_PERIODIC_SCAN_MS_PER_TICK
        );

        if (periodicScanCycleActive && queueSizeBefore > 0 && periodicScanQueue.isEmpty()) {
            periodicScanCycleActive = false;
            periodicScanNotifyThisCycle = false;
            if (!initialMapBuildCompleted) {
                initialMapBuildCompleted = true;
            }
            notifyPeriodicScanFinished();
        }
    }

    private static void notifyPeriodicScanStarted(int queuedJobs) {
        String msg = String.format(
            "[SAI] scan#%d START %s jobs=%d",
            periodicScanCycleCount,
            periodicScanCycleStartTs,
            queuedJobs
        );
        sendScanStatusToTrackedPlayer(msg);
    }

    private static void notifyPeriodicScanFinished() {
        String finishTs = scanTs();
        long elapsedMs = periodicScanCycleStartNs > 0L
            ? (System.nanoTime() - periodicScanCycleStartNs) / 1_000_000L
            : -1L;
        String durationText = elapsedMs >= 0L
            ? String.format("%d ms (%.2f s)", elapsedMs, elapsedMs / 1000.0)
            : "unknown";

        String msg = String.format(
            "[SAI] scan#%d FINISH %s start=%s duration=%s",
            periodicScanCycleCount,
            finishTs,
            (periodicScanCycleStartTs == null || periodicScanCycleStartTs.isEmpty()) ? "unknown" : periodicScanCycleStartTs,
            durationText
        );

        periodicScanCycleStartNs = 0L;
        periodicScanCycleStartTs = "";
        sendScanStatusToTrackedPlayer(msg);
    }

    private static String scanTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    private static void sendScanStatusToTrackedPlayer(String msg) {
        sendStatusToPlayer(lastPlayerUuid, msg);
    }

    /** Sends an action-bar-only progress message (no chat message) to avoid flooding. */
    private static void sendScanProgressBar(String msg) {
        if (!screenDebugEnabled) {
            return;
        }

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && lastPlayerUuid != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(lastPlayerUuid);
            if (player != null) {
                player.displayClientMessage(Component.literal(msg), true);
            }
        }
    }

    private static void sendStatusToPlayer(UUID playerUuid, String msg) {
        if (!screenDebugEnabled) {
            LOGGER.info(msg);
            return;
        }

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.info(msg);
            return;
        }

        if (playerUuid != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                player.displayClientMessage(Component.literal(msg), true);
                player.sendSystemMessage(Component.literal(msg));
                return;
            }
        }

        LOGGER.info(msg);
    }

    private static void processPeriodicScanJobPhase(PeriodicScanJob job) {
        ServerLevel serverLevel = job.serverLevel;
        Entity entity = job.steveAi;

        switch (job.phase) {
            case LOCATION_AND_WORLD -> {
                lastSteveAiKnownPos = entity.blockPosition();
                lastSteveAiKnownDimension = entity.level().dimension();

                if (steveAiChunkForceEnabled) {
                    updateForcedChunkForSteveAi(serverLevel, entity);
                }

                String line = String.format(
                    "steveAI location -> player=%s uuid=%s dimension=%s x=%.2f y=%.2f z=%.2f%n",
                    lastPlayerName,
                    lastPlayerUuid,
                    serverLevel.dimension(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
                );

                LOGGER.info(
                    "steveAI location -> dimension: {}, x: {}, y: {}, z: {}",
                    serverLevel.dimension(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
                );

                appendSteveAiLine(serverLevel, lastPlayerUuid, line);
                appendWorldInfo(serverLevel, entity);

                sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 1/5 LOC x=%.0f y=%.0f z=%.0f dim=%s building tile grid",
                    periodicScanCycleCount,
                    entity.getX(), entity.getY(), entity.getZ(),
                    serverLevel.dimension()
                ));

                    // Build the non-overlapping tile grid covering the full scan diameter
                    BlockPos origin = entity.blockPosition();
                    job.originY = origin.getY();
                    int diameter = PERIODIC_SCAN_BLOCK_RADIUS * 2;
                    int tileW = PERIODIC_TILE_BLOCK_WIDTH;
                    int gridN = (int) Math.ceil((double) diameter / tileW);
                    int totalOccupied = gridN * tileW;
                    int startX = origin.getX() - totalOccupied / 2;
                    int startZ = origin.getZ() - totalOccupied / 2;
                    job.tiles = new java.util.ArrayList<>();
                    for (int ix = 0; ix < gridN; ix++) {
                        for (int iz = 0; iz < gridN; iz++) {
                            int minX = startX + ix * tileW;
                            int minZ = startZ + iz * tileW;
                            job.tiles.add(new int[]{minX, minZ, minX + tileW - 1, minZ + tileW - 1});
                        }
                    }
                    job.totalTiles = job.tiles.size();
                    job.groupedBlocks.clear();
                    job.groupedEntities.clear();
                    job.groupedBlockEntities.clear();
                    job.entityTileIdx = 0;
                    job.blockTileIdx = 0;
                    job.beTileIdx = 0;

                job.phase = PeriodicScanPhase.ENTITIES;
                }
            case ENTITIES -> {
                // Process one tile per call; accumulate results across ticks
                if (job.entityTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.entityTileIdx);
                    SteveAiCollectors.mergeInto(
                        job.groupedEntities,
                        SteveAiCollectors.collectEntitiesInTile(
                            serverLevel, tile[0], tile[1], tile[2], tile[3],
                            job.originY, PERIODIC_SCAN_BLOCK_RADIUS
                        )
                    );
                    job.entityTileIdx++;
                    sendScanProgressBar(String.format(
                        "[SAI] scan#%d 2/5 ENTITIES tile %d/%d",
                        periodicScanCycleCount, job.entityTileIdx, job.totalTiles
                    ));
                }
                if (job.entityTileIdx >= job.totalTiles) {
                    lastEntityScanCenter = entity.blockPosition();
                    appendSteveAiLine(serverLevel, lastPlayerUuid, String.format(
                        "entity scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedEntities.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 2/5 ENTITIES done tiles=%d types=%d",
                        periodicScanCycleCount, job.totalTiles, job.groupedEntities.size()
                    ));
                    job.phase = PeriodicScanPhase.BLOCKS;
                }
            }
            case BLOCKS -> {
                // Process one tile per call; accumulate results across ticks
                if (job.blockTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.blockTileIdx);
                    int yMin = job.originY - PERIODIC_SCAN_BLOCK_RADIUS;
                    int yMax = job.originY + PERIODIC_SCAN_BLOCK_RADIUS;
                    SteveAiCollectors.mergeInto(
                        job.groupedBlocks,
                        SteveAiCollectors.collectBlocksInTile(
                            serverLevel, tile[0], tile[1], tile[2], tile[3],
                            yMin, yMax, CommandEvents::isInterestingLookSeeBlock
                        )
                    );
                    job.blockTileIdx++;
                    sendScanProgressBar(String.format(
                        "[SAI] scan#%d 3/5 BLOCKS tile %d/%d",
                        periodicScanCycleCount, job.blockTileIdx, job.totalTiles
                    ));
                }
                if (job.blockTileIdx >= job.totalTiles) {
                    lastBlockScanCenter = entity.blockPosition();
                    appendSteveAiLine(serverLevel, lastPlayerUuid, String.format(
                        "block scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedBlocks.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 3/5 BLOCKS done tiles=%d types=%d",
                        periodicScanCycleCount, job.totalTiles, job.groupedBlocks.size()
                    ));
                    job.phase = PeriodicScanPhase.BLOCK_ENTITIES;
                }
            }
            case BLOCK_ENTITIES -> {
                // Process one tile per call; accumulate results across ticks
                if (job.beTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.beTileIdx);
                    int yMin = job.originY - PERIODIC_SCAN_BLOCK_RADIUS;
                    int yMax = job.originY + PERIODIC_SCAN_BLOCK_RADIUS;
                    SteveAiCollectors.mergeInto(
                        job.groupedBlockEntities,
                        SteveAiCollectors.collectBlockEntitiesInTile(
                            serverLevel, tile[0], tile[1], tile[2], tile[3],
                            yMin, yMax
                        )
                    );
                    job.beTileIdx++;
                    sendScanProgressBar(String.format(
                        "[SAI] scan#%d 4/5 BE tile %d/%d",
                        periodicScanCycleCount, job.beTileIdx, job.totalTiles
                    ));
                }
                if (job.beTileIdx >= job.totalTiles) {
                    lastBlockEntityScanCenter = entity.blockPosition();
                    job.blockEntityScanCenter = lastBlockEntityScanCenter;
                    job.poiChanged = !job.groupedBlockEntities.isEmpty() || !job.groupedEntities.isEmpty();
                    appendSteveAiLine(serverLevel, lastPlayerUuid, String.format(
                        "BE scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedBlockEntities.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 4/5 BE done tiles=%d types=%d",
                        periodicScanCycleCount, job.totalTiles, job.groupedBlockEntities.size()
                    ));
                    job.phase = PeriodicScanPhase.SUMMARY;
                }
            }
            case SUMMARY -> {
                sendScanStatusToTrackedPlayer(String.format(
                    "[SAI] scan#%d 5/5 SUMMARY: ingesting POI + writing full %d-chunk files",
                    periodicScanCycleCount,
                    PERIODIC_SCAN_CHUNK_RADIUS
                ));
                writePeriodicScanFiles(
                    serverLevel,
                    lastPlayerUuid,
                    entity,
                    job.groupedBlocks,
                    job.groupedEntities,
                    job.groupedBlockEntities
                );
                job.phase = null;
            }
            default -> job.phase = null;
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("CommandEvents.onPlayerLoggedIn START ");
        try {
            var player = event.getEntity();
            var level = player.level();
            lastPlayerUuid = player.getUUID();
            lastPlayerName = player.getName().getString();
            LOGGER.info("Tracking steveAI file for player UUID: {}", lastPlayerUuid);

            LOGGER.info("Player UUID: {}", player.getUUID());
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            LOGGER.info("Player {} logged in", player.getName().getString());

            if (findSteveAiAnywhere(serverLevel) != null) {
                LOGGER.info("steveAI already exists somewhere in the world, skipping spawn");
                // TODO: re-enable once chunks are confirmed loaded at login
                // forceStartPeriodicScanNow(serverLevel.getServer());
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
                villager.setCustomName(Component.literal(STEVE_AI_NAME));
                villager.setCustomNameVisible(true);
                villager.setPersistenceRequired();
                villager.addTag(STEVE_AI_TAG);

                villager.setVillagerData(
                    villager.getVillagerData()
                        .withProfession(serverLevel.registryAccess(), VillagerProfession.FARMER)
                        .withLevel(2)
                );

                serverLevel.addFreshEntity(villager);
                LOGGER.info("Spawned steveAI at {}, {}, {}", spawnX, spawnY, spawnZ);
                // TODO: re-enable once chunks are confirmed loaded at login
                // forceStartPeriodicScanNow(serverLevel.getServer());
            } else {
                LOGGER.warn("Failed to create villager entity for steveAI");
            }
        } finally {
            LOGGER.info("CommandEvents.onPlayerLoggedIn FINISH");
        }
    }

    private static void sendLookSeeSection(
        CommandSourceStack source,
        String sectionTitle,
        java.util.Map<String, SteveAiCollectors.SeenSummary> grouped,
        String kind
    ) {
        if (grouped == null || grouped.isEmpty()) {
            source.sendSuccess(() -> Component.literal(sectionTitle + ": none"), false);
            return;
        }

        source.sendSuccess(() -> Component.literal(sectionTitle + ":"), false);

        int shown = 0;
        for (var entry : grouped.entrySet()) {
            if (shown >= 12) {
                int remaining = grouped.size() - shown;
                source.sendSuccess(() -> Component.literal("... +" + remaining + " more"), false);
                break;
            }

            String name = entry.getKey();
            SteveAiCollectors.SeenSummary s = entry.getValue();

            String line = String.format(
                " - %s=%s firstLoc=(%d,%d,%d) cnt=%d",
                kind,
                name,
                s.x,
                s.y,
                s.z,
                s.count
            );

            source.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
    }

    private static String mapExplorePoiToPoiType(String poi) {
        return switch (poi) {
            case "village" -> "village_candidate";
            case "dungeon" -> "dungeon";
            case "trial", "trial_chamber" -> "trial_chamber";
            case "archaeology", "archaeology_site" -> "archaeology_site";
            default -> null;
        };
    }

    private static boolean isInterestingLookSeeBlock(BlockState state) {
        Block block = state.getBlock();

        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BELL
            || block == Blocks.HAY_BLOCK
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.FURNACE
            || block == Blocks.CRAFTING_TABLE
            || block == Blocks.FARMLAND
            || block == Blocks.WATER
            || block == Blocks.BARREL
            || block == Blocks.SMOKER
            || block == Blocks.COMPOSTER
            || state.getBlock() instanceof BedBlock
            || state.getBlock() instanceof DoorBlock;
    }

    private static int handleLookSee(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        double entityRadius = 20.0;

        java.util.Map<String, SteveAiCollectors.SeenSummary> entityMap =
            SteveAiCollectors.collectNearbyEntities(serverLevel, steveAi, entityRadius);

        Map<String, SteveAiCollectors.SeenSummary> grouped =
            SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAi, 20, 20, CommandEvents::isInterestingLookSeeBlock);

        source.sendSuccess(() -> Component.literal(
            "SteveAI lookSee radius=20 at " + steveAi.blockPosition().toShortString()
        ), false);

        sendLookSeeSection(source, "Entities", entityMap, "entity");
        sendLookSeeSection(source, "Blocks", grouped, "block");

        return 1;
    }

    private static int handleExplorePoi(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String poi = StringArgumentType.getString(context, "poi").toLowerCase(Locale.ROOT).trim();
        String poiType = mapExplorePoiToPoiType(poi);

        if (poiType == null) {
            source.sendFailure(Component.literal("Unsupported explore POI: " + poi));
            return 0;
        }

        BlockPos nearest;

        if ((poi.equals("village_candidate")) || (poi.equals("village"))) {
            nearest = PoiManager.findNearestVillageForExplore(steveAi.blockPosition());
        } else {
            nearest = PoiManager.findNearestPoiCenter(poiType, steveAi.blockPosition());
        }

        if (nearest == null) {
            source.sendFailure(Component.literal("No known " + poi + " POI found."));
            return 0;
        }

        exploreActive = true;
        explorePoi = poi;
        explorePoiType = poiType;
        exploreCenter = nearest.immutable();
        exploreRadius = 24;
        currentExploreTarget = null;
        exploredTargets.clear();
        nextExploreRepathGameTime = 0L;

        source.sendSuccess(() -> Component.literal(
            "SteveAI exploring nearest " + poi +
            " at " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
        ), false);

        LOGGER.info("Explore started: poi={} poiType={} center={}", explorePoi, explorePoiType, exploreCenter);
        return 1;
    }

    private static int countNearbyVillagers(ServerLevel serverLevel, Villager steveAi, double radius) {
        var nearby = serverLevel.getEntities(
            (Entity) null,
            steveAi.getBoundingBox().inflate(radius),
            e -> e instanceof Villager && e != steveAi && e.isAlive()
        );

        return nearby.size();
    }

    private static void stopExploreTask() {
        exploreActive = false;
        explorePoi = "";
        explorePoiType = "";
        exploreCenter = null;
        currentExploreTarget = null;
        exploredTargets.clear();
        nextExploreRepathGameTime = 0L;
    }

    private static int handleExploreStop(CommandContext<CommandSourceStack> context) {
        stopExploreTask();
        context.getSource().sendSuccess(() -> Component.literal("SteveAI exploration stopped."), false);
        return 1;
    }

    private static int handleExploreStatus(CommandContext<CommandSourceStack> context) {
        if (!exploreActive || exploreCenter == null) {
            context.getSource().sendSuccess(() -> Component.literal("No active exploration task."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "Exploring poi=" + explorePoi +
            " poiType=" + explorePoiType +
            " center=" + exploreCenter.getX() + "," + exploreCenter.getY() + "," + exploreCenter.getZ() +
            " radius=" + exploreRadius +
            " target=" + (currentExploreTarget == null ? "none" : currentExploreTarget.toShortString())
        ), false); 

        return 1;
    }

    private static int handleDebugScreenToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        screenDebugEnabled = enabled;
        context.getSource().sendSuccess(
            () -> Component.literal("SteveAI screen debug messages " + (enabled ? "enabled" : "disabled") + "."),
            false
        );
        LOGGER.info("SteveAI screen debug messages set to {} by {}", enabled, context.getSource().getTextName());
        return 1;
    }

    private static void tickExplore(ServerLevel serverLevel, Villager steveAi) {
        if (!exploreActive || exploreCenter == null) {
            return;
        }

        long gameTime = serverLevel.getGameTime();

        double distToCenterSq = steveAi.blockPosition().distSqr(exploreCenter);
        if ("village_candidate".equals(explorePoi) || "village".equals(explorePoi)) {
            int villagerCount = countNearbyVillagers(serverLevel, steveAi, 20.0);

            LOGGER.info("Explore village check: nearby villager count={}", villagerCount);

            if (villagerCount > 1) {
                LOGGER.info("Explore complete: found village population near SteveAI.");
                stopExploreTask();
                return;
            }
        }

        if (distToCenterSq > 100.0) {
            if (gameTime >= nextExploreRepathGameTime) {
                boolean ok = steveAi.getNavigation().moveTo(
                    exploreCenter.getX() + 0.5,
                    exploreCenter.getY(),
                    exploreCenter.getZ() + 0.5,
                    0.9
                );
                LOGGER.info("Explore travel-to-poi center={} result={}", exploreCenter, ok);
                nextExploreRepathGameTime = gameTime + 40;
            }
            return;
        }

        if (currentExploreTarget == null || reachedExploreTarget(steveAi, currentExploreTarget)) {
            if (currentExploreTarget != null) {
                exploredTargets.add(currentExploreTarget.immutable());
            }

            currentExploreTarget = pickNextExploreTarget(serverLevel);
            nextExploreRepathGameTime = 0L;

            if (currentExploreTarget == null) {
                LOGGER.info("No valid exploration target around POI center {}", exploreCenter);
                return;
            }

            LOGGER.info("Explore local target={}", currentExploreTarget);
        }

        if (gameTime >= nextExploreRepathGameTime) {
            boolean ok = steveAi.getNavigation().moveTo(
                currentExploreTarget.getX() + 0.5,
                currentExploreTarget.getY(),
                currentExploreTarget.getZ() + 0.5,
                0.9
            );
            LOGGER.info("Explore patrol target={} result={}", currentExploreTarget, ok);
            nextExploreRepathGameTime = gameTime + 40;
        }
    }

    private static BlockPos pickNextExploreTarget(ServerLevel serverLevel) {
        RandomSource random = serverLevel.random;

        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(exploreRadius * 2 + 1) - exploreRadius;
            int dz = random.nextInt(exploreRadius * 2 + 1) - exploreRadius;

            int x = exploreCenter.getX() + dx;
            int z = exploreCenter.getZ() + dz;
            BlockPos top = serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, 0, z)
            );

            BlockPos candidate = top.immutable();

            if (exploredTargets.contains(candidate)) continue;
            if (!isGoodExploreTarget(serverLevel, candidate)) continue;

            return candidate;
        }

        return null;
    }

    private static boolean reachedExploreTarget(Villager steveAi, BlockPos target) {
        return steveAi.blockPosition().distSqr(target) <= 9.0;
    }

    private static boolean isGoodExploreTarget(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isInWorldBounds(pos)) return false;

        BlockState at = serverLevel.getBlockState(pos);
        BlockState above = serverLevel.getBlockState(pos.above());
        BlockState below = serverLevel.getBlockState(pos.below());

        if (!below.blocksMotion()) return false;
        if (!at.isAir()) return false;
        if (!above.isAir()) return false;

        return true;
    }

    private static boolean isSteveAi(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        if (entity.getTags().contains(STEVE_AI_TAG)) {
            return true;
        }

        return entity.hasCustomName()
            && entity.getCustomName() != null
            && STEVE_AI_NAME.equals(entity.getCustomName().getString());
    }

    private static int handleWriteNow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        LOGGER.info("[WRITE DEBUG] /testmod writeNow invoked");

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        UUID playerUuid = lastPlayerUuid;
        if (source.getEntity() instanceof ServerPlayer player) {
            playerUuid = player.getUUID();
        }

        if (playerUuid == null) {
            source.sendFailure(Component.literal("No tracked player UUID available."));
            return 0;
        }

        java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities =
            SteveAiCollectors.collectNearbyBlockEntities(serverLevel, steveAi, 30);
        java.util.Map<String, SteveAiCollectors.SeenSummary> groupedEntities =
            SteveAiCollectors.collectNearbyEntities(serverLevel, steveAi, 30.0);

        // Load persisted village personalities BEFORE clearing, so they survive the rebuild.
        try {
            Path pd = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            PoiManager.loadPersonalitiesFromFile(pd.resolve("village_personalities.txt"));
        } catch (IOException ex) {
            LOGGER.warn("[WRITE DEBUG] Could not load village personalities file", ex);
        }

        PoiManager.ingestScanSummaries(Map.of(), groupedEntities, groupedBlockEntities);

        BlockPos center = steveAi.blockPosition();
        java.util.List<String> summaryLines = new java.util.ArrayList<>();
        summaryLines.add("");
        summaryLines.add("=== POI Summary ===");
        summaryLines.add(String.format(
            "scanCenter=(%d,%d,%d)",
            center.getX(),
            center.getY(),
            center.getZ()
        ));

        for (String poiLine : PoiManager.buildSummaryLines()) {
            summaryLines.add(poiLine);
        }

        writeSteveAiSummary(serverLevel, playerUuid, steveAi, summaryLines);

        source.sendSuccess(
            () -> Component.literal("SteveAI wrote all scan/POI files now."),
            false
        );

        return 1;
    }

    private static void writeSteveAiSummary(
        ServerLevel serverLevel,
        UUID playerUuid,
        Entity steveAiEntity,
        java.util.List<String> poiSummaryLines
    ) {
        try {
            LOGGER.info("[WRITE DEBUG] writeSteveAiSummary start playerUuid={} steveAiPos={}", playerUuid, steveAiEntity.blockPosition());

            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);

            java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlocks =
                SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAiEntity, 30, 70, CommandEvents::isInterestingLookSeeBlock);

            java.util.Map<String, SteveAiCollectors.SeenSummary> groupedEntities =
                SteveAiCollectors.collectNearbyEntities(serverLevel, steveAiEntity, 30.0);

            java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities =
                SteveAiCollectors.collectNearbyBlockEntities(serverLevel, steveAiEntity, 30);

            Path blocksFile = playerDataDir.resolve("scannedBlocks_" + playerUuid + ".txt");
            Path entitiesFile = playerDataDir.resolve("scannedEntities_" + playerUuid + ".txt");
            Path blockEntitiesFile = playerDataDir.resolve("scannedBlockEntities_" + playerUuid + ".txt");
            Path poiSummaryFile = playerDataDir.resolve("poiSummary_" + playerUuid + ".txt");

            logFileTail("WRITE DEBUG", blocksFile, 10);
            logFileTail("WRITE DEBUG", entitiesFile, 10);
            logFileTail("WRITE DEBUG", blockEntitiesFile, 10);
            logFileTail("WRITE DEBUG", poiSummaryFile, 10);

            Files.writeString(
                blocksFile,
                mapToText(groupedBlocks, "SCANNED BLOCKS"),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            Files.writeString(
                entitiesFile,
                mapToText(groupedEntities, "SCANNED ENTITIES"),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            Files.writeString(
                blockEntitiesFile,
                mapToText(groupedBlockEntities, "SCANNED BLOCK ENTITIES"),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            StringBuilder poiText = new StringBuilder();
            poiText.append("POI SUMMARY\n");
            for (String line : poiSummaryLines) {
                poiText.append(line).append("\n");
            }

            Files.writeString(
                poiSummaryFile,
                poiText.toString(),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            // Persist village personality assignments so they survive server restarts.
            PoiManager.savePersonalitiesToFile(playerDataDir.resolve("village_personalities.txt"));

            String writeMsg = String.format(
                "[testmod] SteveAI files written %s blocks/entities/blockEntities/poiSummary/personality",
                scanTs()
            );
            sendStatusToPlayer(playerUuid, writeMsg);

            LOGGER.info("[WRITE DEBUG] writeSteveAiSummary finished folder={}", playerDataDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to write steveAI summary file", e);
            sendStatusToPlayer(playerUuid, "[testmod] SteveAI file write FAILED " + scanTs() + " (see server log)");
        }
    }

    private static void writePeriodicScanFiles(
        ServerLevel serverLevel,
        UUID playerUuid,
        Entity steveAiEntity,
        Map<String, SteveAiCollectors.SeenSummary> groupedBlocks,
        Map<String, SteveAiCollectors.SeenSummary> groupedEntities,
        Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities
    ) {
        if (playerUuid == null) {
            LOGGER.warn("Periodic scan write skipped because lastPlayerUuid is null");
            return;
        }

        try {
            SteveAiScanManager.replaceScanResults(
                "all",
                PERIODIC_SCAN_CHUNK_RADIUS,
                steveAiEntity.blockPosition(),
                serverLevel.getGameTime(),
                groupedBlocks,
                groupedEntities,
                groupedBlockEntities
            );

            int poiUpdates = SteveAiScanManager.updatePoiMapFromCurrentScanFast();

            Path playerDataDir = SteveAiScanManager.writeTextFiles(serverLevel, playerUuid.toString());
            PoiManager.savePersonalitiesToFile(playerDataDir.resolve("village_personalities.txt"));

            String writeMsg = String.format(
                "[testmod] SteveAI full %d-chunk files written %s blocks=%d entities=%d blockEntities=%d poiUpdates=%d",
                PERIODIC_SCAN_CHUNK_RADIUS,
                scanTs(),
                groupedBlocks.size(),
                groupedEntities.size(),
                groupedBlockEntities.size(),
                poiUpdates
            );
            sendStatusToPlayer(playerUuid, writeMsg);
        } catch (IOException e) {
            LOGGER.error("Failed to write periodic steveAI scan files", e);
            sendStatusToPlayer(playerUuid, "[testmod] SteveAI file write FAILED " + scanTs() + " (see server log)");
        }
    }

    private static void logFileTail(String logPrefix, Path file, int maxLines) {
        try {
            if (file == null || !Files.exists(file)) {
                LOGGER.info("[{}] tail skipped, file missing: {}", logPrefix, file);
                return;
            }

            java.util.List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                LOGGER.info("[{}] tail for {} -> (file empty)", logPrefix, file.getFileName());
                return;
            }

            int start = Math.max(0, lines.size() - maxLines);
            java.util.List<String> tail = lines.subList(start, lines.size());

            LOGGER.info("[{}] tail for {} (last {} lines):", logPrefix, file.getFileName(), tail.size());
            for (String line : tail) {
                LOGGER.info("[{}] {}", logPrefix, line);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to log [{}] tail for file {}", logPrefix, file, e);
        }
    }

    private static String mapToText(java.util.Map<String, SteveAiCollectors.SeenSummary> map, String title) {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append("\n");
        sb.append("count=").append(map.size()).append("\n\n");

        for (java.util.Map.Entry<String, SteveAiCollectors.SeenSummary> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    private static void appendSteveAiLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path steveAiFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI.txt");

            Files.writeString(
                steveAiFile,
                line,
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            LOGGER.error("Failed to write steveAI text file", e);
        }
    }

    private static Entity findSteveAiAnywhere(ServerLevel serverLevel) {
        for (ServerLevel levelToCheck : serverLevel.getServer().getAllLevels()) {
            var matches = levelToCheck.getEntities(
                (Entity) null,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                entity -> isSteveAi(entity)
            );

            if (!matches.isEmpty()) {
                Entity existing = matches.get(0);
                LOGGER.info(
                    "Found existing steveAI in dimension {} at x={}, y={}, z={}",
                    levelToCheck.dimension(),
                    existing.getX(),
                    existing.getY(),
                    existing.getZ()
                );
                return existing;
            }
        }

        return null;
    }

    private static int handleScanSai(
        CommandContext<CommandSourceStack> context,
        String rawScanInput,
        int chunkRadius,
        boolean fastMode
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        long scanStartNs = System.nanoTime();
        try {
            if (fastMode) {
                if (!(source.getEntity() instanceof ServerPlayer player)) {
                    source.sendFailure(Component.literal("scanSAI fast requires a player command source."));
                    return 0;
                }
                SteveAiScanManager.scanSAIFast(serverLevel, steveAi, player.blockPosition(), rawScanInput, chunkRadius);
            } else {
                SteveAiScanManager.scanSAI(serverLevel, steveAi, rawScanInput, chunkRadius);
            }
        } catch (IllegalArgumentException e) {
            long elapsedMs = (System.nanoTime() - scanStartNs) / 1_000_000L;
            LOGGER.info(
                "scanSAI failed thread={} input={} chunkRadius={} fastMode={} elapsedMs={} reason={}",
                Thread.currentThread().getName(),
                rawScanInput,
                chunkRadius,
                fastMode,
                elapsedMs,
                e.getMessage()
            );
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int count =
            SteveAiScanManager.getScannedBlocks().size()
            + SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();

        String normalizedInput = SteveAiScanManager.getLastScanType();
        int finalCount = count;
        long elapsedMs = (System.nanoTime() - scanStartNs) / 1_000_000L;

        LOGGER.info(
            "scanSAI complete thread={} input={} normalizedInput={} chunkRadius={} fastMode={} groupedBlocks={} groupedEntities={} groupedBlockEntities={} groupedTotal={} elapsedMs={}",
            Thread.currentThread().getName(),
            rawScanInput,
            normalizedInput,
            chunkRadius,
            fastMode,
            SteveAiScanManager.getScannedBlocks().size(),
            SteveAiScanManager.getScannedEntities().size(),
            SteveAiScanManager.getScannedBlockEntities().size(),
            finalCount,
            elapsedMs
        );

        source.sendSuccess(() -> Component.literal(
            "SteveAI scan complete: input=" + normalizedInput +
            " chunkRadius=" + chunkRadius +
            " fastMode=" + fastMode +
            (fastMode
                ? " detailedChunks=" + SteveAiScanManager.getLastFastDetailedChunkCount()
                    + " quickChunks=" + SteveAiScanManager.getLastFastQuickChunkCount()
                : "") +
            " groupedCount=" + finalCount +
            " (run /testmod poiStage1 to ingest into POI map)"
        ), false);

        return 1;
    }

    private static int handlePoiUpdate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int updates = SteveAiScanManager.updatePoiMapFromCurrentScanFast();
        int poiCount = PoiManager.getPoiCount();

        source.sendSuccess(() -> Component.literal(
            "POI map updated from current scan (fast E+BE stage): updates=" + updates + " totalPois=" + poiCount
        ), false);
        return 1;
    }

    private static int handlePoiConfirmCandidates(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        java.util.List<BlockPos> candidates = PoiManager.getCandidateCenters(limit);
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No POI candidates to confirm."), false);
            return 1;
        }

        int scannedChunks = 0;
        int poiUpdates = 0;

        for (BlockPos candidate : candidates) {
            SteveAiScanManager.scanSAI2(serverLevel, candidate, false);
            poiUpdates += SteveAiScanManager.updatePoiMapFromCurrentScan();
            scannedChunks++;
        }

        int poiCount = PoiManager.getPoiCount();
        int scannedChunksFinal = scannedChunks;
        int poiUpdatesFinal = poiUpdates;
        int poiCountFinal = poiCount;
        source.sendSuccess(() -> Component.literal(
            "POI stage2 confirmation complete: candidatesScanned=" + scannedChunksFinal
                + " updates=" + poiUpdatesFinal
                + " totalPois=" + poiCountFinal
        ), false);
        return 1;
    }

    private static int handlePoiReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PoiManager.clear();
        source.sendSuccess(() -> Component.literal("POI map reset: totalPois=0"), false);
        return 1;
    }

    public static void appendSteveAiChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");

            String out = (line == null ? "" : line);
            if (!out.endsWith("\n")) {
                out += "\n";
            }

            Files.writeString(
                chatFile,
                out,
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            LOGGER.error("Failed to write steveAI chat file", e);
        }
    }

    public static String chatTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    public static String askSteveAi(ServerLevel serverLevel, UUID playerUuid, String message) {
        LOGGER.info("askSteveAi START playerUuid={} message={}", playerUuid, message);

        String fileContext = SteveAiContextFiles.buildChatContext(serverLevel, playerUuid, 200);
        String normalizedMessage = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        boolean louTruthMode = normalizedMessage.contains("lou wants to know")
            || normalizedMessage.contains("lwtk");

        String behaviorText;
        if (louTruthMode) {
            behaviorText =
                "You are SteveAI, a Minecraft villager. " +
                "Be very honest and direct. " +
                "Add a bit of specific detail that helps answer the question clearly. " +
                "Keep responses concise and not too long.";
        } else {
            behaviorText =
                "You are SteveAI, a Minecraft villager. " +
                "You are shy at first and mistrustful in this new world. " +
                "You are truthful but vague and may fib to protect yourself, especially early in a relationship. " +
                "After days of knowing someone you become more open and share more detailed, personal and useful info.\n" +
                "Keep replies short if possible, even curt if warranted.";
        }

        String prompt =
            behaviorText + " " +
            "Use the context files below if relevant.\n" +
            "IMPORTANT: The POI summary may list confirmed villages with a 'personality' field. " +
            "When a village has a personality, its villagers ARE the characters from that theme. " +
            "For example, a village with personality=scooby_doo has villagers named Scooby-Doo, Shaggy, Velma, Daphne, and Fred. " +
            "A village with personality=er_tv_series has Dr. Mark Greene, Dr. Doug Ross, Dr. John Carter, Nurse Carol Hathaway, and Dr. Peter Benton. " +
            "A village with personality=lord_of_the_rings has Gandalf, Frodo, Aragorn, Legolas, Gimli, Samwise Gamgee, and Boromir. " +
            "A village with personality=pirates_of_the_caribbean has Jack Sparrow, Will Turner, Elizabeth Swann, Hector Barbossa, and Davy Jones. " +
            "Always refer to those villagers by their character names and stay in character for that theme.\n\n" +
            fileContext + "\n\n" +
            "Player asks: " + message;

        String reply = OpenAiService.ask(prompt);

        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] YOU: " + oneLine(message));
        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] STEVEAI: " + oneLine(reply));
        appendSteveAiChatLine(serverLevel, playerUuid, "");

        LOGGER.info("askSteveAi FINISH playerUuid={} reply={}", playerUuid, reply);
        return reply;
    }

    private static void appendWorldInfo(ServerLevel serverLevel, Entity steveAiEntity) {
        if (lastPlayerUuid == null) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        long dayTime = serverLevel.getDayTime();
        long dayNumber = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        boolean isRaining = serverLevel.isRaining();
        boolean isThundering = serverLevel.isThundering();

        var biomeHolder = serverLevel.getBiome(steveAiEntity.blockPosition());
        String biomeName = "unknown";
        var biomeKey = serverLevel.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .getKey(biomeHolder.value());
        if (biomeKey != null) {
            biomeName = biomeKey.toString();
        }

        int moonPhase = (int) ((dayNumber % 8L + 8L) % 8L);

        String line = String.format(
            "world info -> dimension=%s biome=%s gameTime=%d day=%d timeOfDay=%d raining=%s thundering=%s moonPhase=%d%n",
            serverLevel.dimension(),
            biomeName,
            gameTime,
            dayNumber,
            timeOfDay,
            isRaining,
            isThundering,
            moonPhase
        );

        appendSteveAiLine(serverLevel, lastPlayerUuid, line);
    }

    private static void appendNearbyBlocks(ServerLevel serverLevel, Entity steveAiEntity, int horizontalRadius, int verticalRadius) {
        if (lastPlayerUuid == null) {
            return;
        }

        java.util.Map<String, SteveAiCollectors.SeenSummary> grouped =
            SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAiEntity, horizontalRadius, verticalRadius, CommandEvents::isInterestingLookSeeBlock);

        if (grouped.isEmpty()) {
            appendSteveAiLine(serverLevel, lastPlayerUuid, "nearby blocks -> none found\n");
            return;
        }

        for (var entry : grouped.entrySet()) {
            String blockName = entry.getKey();
            SteveAiCollectors.SeenSummary summary = entry.getValue();

            String line = String.format(
                "B -> block=%s firstLoc=(%d,%d,%d) cnt=%d%n",
                blockName,
                summary.x,
                summary.y,
                summary.z,
                summary.count
            );

            appendSteveAiLine(serverLevel, lastPlayerUuid, line);
        }
    }

    private static void appendNearbyEntities(ServerLevel serverLevel, Entity steveAiEntity, double radius) {
        if (lastPlayerUuid == null) {
            return;
        }

        java.util.Map<String, SteveAiCollectors.SeenSummary> grouped =
            SteveAiCollectors.collectNearbyEntities(serverLevel, steveAiEntity, radius);

        if (grouped.isEmpty()) {
            String line = String.format(
                "E -> none within %.1f blocks of steveAI%n",
                radius
            );
            appendSteveAiLine(serverLevel, lastPlayerUuid, line);
            return;
        }

        for (var entry : grouped.entrySet()) {
            String typeName = entry.getKey();
            SteveAiCollectors.SeenSummary summary = entry.getValue();

            String line = String.format(
                "E -> type=%s firstLoc=(%d,%d,%d) cnt=%d%n",
                typeName,
                summary.x,
                summary.y,
                summary.z,
                summary.count
            );

            appendSteveAiLine(serverLevel, lastPlayerUuid, line);
        }
    }

    private static boolean appendNearbyBlockEntities(ServerLevel serverLevel, Entity steveAiEntity, int radiusBlocks) {
        if (lastPlayerUuid == null) {
            return false;
        }

        var center = steveAiEntity.blockPosition();

        int minChunkX = (center.getX() - radiusBlocks) >> 4;
        int maxChunkX = (center.getX() + radiusBlocks) >> 4;
        int minChunkZ = (center.getZ() - radiusBlocks) >> 4;
        int maxChunkZ = (center.getZ() + radiusBlocks) >> 4;

        int radiusSq = radiusBlocks * radiusBlocks;
        boolean foundAny = false;
        boolean foundNewPoi = false;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    var pos = entry.getKey();
                    BlockEntity blockEntity = entry.getValue();

                    int dx = pos.getX() - center.getX();
                    int dy = pos.getY() - center.getY();
                    int dz = pos.getZ() - center.getZ();

                    int distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > radiusSq) {
                        continue;
                    }

                    foundAny = true;

                    String typeName = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
                    if (PoiManager.processBlockEntity(typeName, pos)) {
                        foundNewPoi = true;
                    }
                    String line = String.format(
                        "BE -> type=%s x=%d y=%d z=%d%n",
                        typeName,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        steveAiEntity.distanceToSqr(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                        ) > 0
                            ? Math.sqrt(steveAiEntity.distanceToSqr(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                            ))
                            : 0.0
                    );

                    appendSteveAiLine(serverLevel, lastPlayerUuid, line);
                }
            }
        }

        if (!foundAny) {
            appendSteveAiLine(
                serverLevel,
                lastPlayerUuid,
                String.format("BE -> none within %d blocks of steveAI%n", radiusBlocks)
            );
        }
        return foundNewPoi;
    }

    private static boolean hasMovedFarEnough(net.minecraft.core.BlockPos lastCenter, Entity entity, double threshold) {
        if (lastCenter == null) {
            return true;
        }
        double dx = entity.getX() - lastCenter.getX();
        double dy = entity.getY() - lastCenter.getY();
        double dz = entity.getZ() - lastCenter.getZ();

        double distanceSq = dx * dx + dy * dy + dz * dz;
        return distanceSq >= threshold * threshold;
    }

    private static class ParsedScanSaiArgs {
        final String rawScanInput;
        final int chunkRadius;
        final boolean fastMode;

        ParsedScanSaiArgs(String rawScanInput, int chunkRadius, boolean fastMode) {
            this.rawScanInput = rawScanInput;
            this.chunkRadius = chunkRadius;
            this.fastMode = fastMode;
        }
    }

    private static int handleDetailSaiAtSteve(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        try {
            SteveAiScanManager.detailSAI(serverLevel, steveAi.blockPosition(), radius);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "SteveAI detail scan complete: center=" + steveAi.blockPosition().toShortString() +
            " radius=" + radius +
            " blocks=" + SteveAiScanManager.getDetailedBlocks().size() +
            " entities=" + SteveAiScanManager.getDetailedEntities().size() +
            " blockEntities=" + SteveAiScanManager.getDetailedBlockEntities().size()
        ), false);

        return 1;
    }

    private static int handleDetailSaiAtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int radius
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        try {
            SteveAiScanManager.detailSAI(serverLevel, center, radius);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "SteveAI detail scan complete: center=" + center.toShortString() +
            " radius=" + radius +
            " blocks=" + SteveAiScanManager.getDetailedBlocks().size() +
            " entities=" + SteveAiScanManager.getDetailedEntities().size() +
            " blockEntities=" + SteveAiScanManager.getDetailedBlockEntities().size()
        ), false);

        return 1;
    }

    private static ParsedScanSaiArgs parseScanSaiArgs(String tail, int defaultChunkRadius) {
        if (tail == null || tail.isBlank()) {
            return new ParsedScanSaiArgs("all", defaultChunkRadius, false);
        }

        String s = tail.trim();
        boolean fastMode = false;

        if (s.equalsIgnoreCase("fast")) {
            return new ParsedScanSaiArgs("all", defaultChunkRadius, true);
        }

        int fastTokenStart = s.toLowerCase(java.util.Locale.ROOT).lastIndexOf(" fast");
        if (fastTokenStart >= 0 && fastTokenStart + 5 == s.length()) {
            fastMode = true;
            s = s.substring(0, fastTokenStart).trim();
        }

        if (s.startsWith("[")) {
            int close = s.lastIndexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("Missing closing ] in scanSAI target list.");
            }

            String rawScanInput = s.substring(0, close + 1).trim();
            String rest = s.substring(close + 1).trim();

            int chunkRadius = defaultChunkRadius;
            if (!rest.isEmpty()) {
                try {
                    chunkRadius = Integer.parseInt(rest);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid scanSAI radius: " + rest);
                }
            }

            return new ParsedScanSaiArgs(rawScanInput, chunkRadius, fastMode);
        }

        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace > 0) {
            String maybeRadius = s.substring(lastSpace + 1).trim();
            try {
                int radius = Integer.parseInt(maybeRadius);
                String rawScanInput = s.substring(0, lastSpace).trim();
                if (rawScanInput.isEmpty()) {
                    throw new IllegalArgumentException("Missing scanSAI type or targets.");
                }
                return new ParsedScanSaiArgs(rawScanInput, radius, fastMode);
            } catch (NumberFormatException ignored) {
            }
        }

        return new ParsedScanSaiArgs(s, defaultChunkRadius, fastMode);
    }

    public static Villager findSteveAi(ServerLevel serverLevel) {
        var matches = serverLevel.getEntities(
            EntityType.VILLAGER,
            new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
            entity -> isSteveAi(entity)
        );

        if (matches.isEmpty()) {
            return null;
        }

        Entity e = matches.get(0);
        return e instanceof Villager v ? v : null;
    }

    private static boolean moveSteveAiTowardPlayer(ServerLevel serverLevel, ServerPlayer player, double speed) {
        Villager steveAi = findSteveAi(serverLevel);
        if (steveAi == null) {
            return false;
        }

        PathNavigation nav = steveAi.getNavigation();
        return nav.moveTo(player, speed);
    }

    private static BlockPos findSafeTeleportPosNearPlayer(ServerLevel serverLevel, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos base = playerPos.offset(dx, 0, dz);

                    BlockPos safe = findSafeStandingPos(serverLevel, base);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }

        return findSafeStandingPos(serverLevel, playerPos);
    }

    private static BlockPos findSafeStandingPos(ServerLevel serverLevel, BlockPos nearPos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos feetPos = nearPos.offset(0, dy, 0);
            BlockPos headPos = feetPos.above();
            BlockPos groundPos = feetPos.below();

            BlockState feetState = serverLevel.getBlockState(feetPos);
            BlockState headState = serverLevel.getBlockState(headPos);
            BlockState groundState = serverLevel.getBlockState(groundPos);

            boolean feetClear = feetState.isAir();
            boolean headClear = headState.isAir();
            boolean solidGround = groundState.isCollisionShapeFullBlock(serverLevel, groundPos);

            if (feetClear && headClear && solidGround) {
                return feetPos;
            }
        }

        return null;
    }

    private static void updateForcedChunkForSteveAi(ServerLevel serverLevel, Entity steveAiEntity) {
        int chunkX = steveAiEntity.chunkPosition().x;
        int chunkZ = steveAiEntity.chunkPosition().z;

        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null
                && forcedSteveAiChunkX == chunkX && forcedSteveAiChunkZ == chunkZ) {
            return;
        }

        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null) {
            boolean removed = serverLevel.setChunkForced(forcedSteveAiChunkX, forcedSteveAiChunkZ, false);
            LOGGER.info("Unforced old steveAI chunk {},{} result={}", forcedSteveAiChunkX, forcedSteveAiChunkZ, removed);
        }

        boolean added = serverLevel.setChunkForced(chunkX, chunkZ, true);
        forcedSteveAiChunkX = chunkX;
        forcedSteveAiChunkZ = chunkZ;

        LOGGER.info("Forced steveAI chunk {},{} result={}", chunkX, chunkZ, added);
    }

    private static void clearForcedSteveAiChunk(ServerLevel serverLevel) {
        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null) {
            boolean removed = serverLevel.setChunkForced(forcedSteveAiChunkX, forcedSteveAiChunkZ, false);
            LOGGER.info("Cleared forced steveAI chunk {},{} result={}", forcedSteveAiChunkX, forcedSteveAiChunkZ, removed);
        }

        forcedSteveAiChunkX = null;
        forcedSteveAiChunkZ = null;
    }
}