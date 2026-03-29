package com.example.examplemod;

import com.example.examplemod.SteveAiCollectors.SeenSummary;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.Registries;
//import com.mojang.brigadier.arguments.StringArgumentType;
//import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
//import net.minecraft.world.item.ItemStack;

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
    private static final double ENTITY_SCAN_MOVE_THRESHOLD = 30.0;
    private static final double BLOCK_SCAN_MOVE_THRESHOLD = 30.0;
    private static final double BLOCK_ENTITY_SCAN_MOVE_THRESHOLD = 30.0;
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

    @SubscribeEvent
        public static void onCommandsRegister(RegisterCommandsEvent event) {
            LOGGER.info("CommandEvents.onCommandsRegister START");
            try {
                event.getDispatcher().register(
                    Commands.literal("testmod")
                        // optional: /testmod with no argument
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
                        .then(Commands.literal("scanSAI")
                            .executes(ctx -> handleScanSai(ctx, "all", 2))

                            // new: detailed scan at explicit coordinates
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

                            // new: detailed scan around SteveAI
                            .then(Commands.literal("detailSAI")
                                .executes(ctx -> handleDetailSaiAtSteve(ctx, 1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                    .executes(ctx -> handleDetailSaiAtSteve(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "radius")
                                    ))
                                )
                            )
                            // existing broad scan modes
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

                                    return handleScanSai(ctx, parsed.rawScanInput, parsed.chunkRadius);
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
                        // testmod opengui will open trading my custom trading window 
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
                                            2,      // villager level
                                            0,      // villager xp
                                            false,  // show progress bar
                                            false   // can restock
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

                                String summary = getSteveAiInventorySummary(steveAi);

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
                                .executes(context -> handleInvAdd(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                    .executes(context -> handleInvAdd(
                                        context,
                                        IntegerArgumentType.getInteger(context, "count")
                                    ))
                                )
                            )
                        )
                        .then(Commands.literal("invDrop")
                            .then(Commands.argument("itemName", StringArgumentType.word())
                                .executes(context -> handleInvDrop(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                    .executes(context -> {
                                        int count = IntegerArgumentType.getInteger(context, "count");
                                        return handleInvDrop(context, count);
                                    })
                                )
                            )
                        )
                        // /testmod hello John
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

        // steveAI should follow player
        if (steveAiFollowMode && steveAiFollowPlayerUuid != null) {
            long now = server.overworld().getGameTime();

            // refresh path every 2 seconds
            if (now - lastFollowTick >= 40) {
                lastFollowTick = now;

                ServerPlayer followPlayer = server.getPlayerList().getPlayer(steveAiFollowPlayerUuid);
                if (followPlayer != null) {
                    Villager steveAi = findSteveAi((ServerLevel) followPlayer.level());
                    if (steveAi != null) {
                        double distSq = steveAi.distanceToSqr(followPlayer);

                        // only repath if not already close
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

        // every 60 seconds
        if (server.overworld().getGameTime() % 1200 != 0) {
            return;
        }

        for (ServerLevel serverLevel : server.getAllLevels()) {
            var matches = serverLevel.getEntities(
                (Entity) null,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                entity -> isSteveAi(entity)
            );
            for (Entity entity : matches) {
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

                if (hasMovedFarEnough(lastEntityScanCenter, entity, ENTITY_SCAN_MOVE_THRESHOLD)) {
                    lastEntityScanCenter = entity.blockPosition();

                    appendSteveAiLine(
                        serverLevel,
                        lastPlayerUuid,
                        String.format(
                            "entity scan centered on -> x=%d y=%d z=%d%n",
                            lastEntityScanCenter.getX(),
                            lastEntityScanCenter.getY(),
                            lastEntityScanCenter.getZ()
                        )
                    );

                    appendNearbyEntities(serverLevel, entity, 30.0);
                }

                if (hasMovedFarEnough(lastBlockScanCenter, entity, BLOCK_SCAN_MOVE_THRESHOLD)) {
                    lastBlockScanCenter = entity.blockPosition();

                    appendSteveAiLine(
                        serverLevel,
                        lastPlayerUuid,
                        String.format(
                            "B scan centered on -> x=%d y=%d z=%d%n",
                            lastBlockScanCenter.getX(),
                            lastBlockScanCenter.getY(),
                            lastBlockScanCenter.getZ()
                        )
                    );

                    appendNearbyBlocks(serverLevel, entity, 30, 70);

                    if (entity instanceof Villager villager) {
                        //addCoalToSteveAiInventory(villager, 4);
                        addItemToSteveAiInventory(villager, new ItemStack(Items.COAL, 4));
                        appendSteveAiLine(
                            serverLevel,
                            lastPlayerUuid,
                            "SteveAI added 4 coal to inventory based on nearby scan.\n"
                        );
                    }
                }

                //boolean didBlockEntityScan = false;
                boolean poiChanged = false;
                if (hasMovedFarEnough(lastBlockEntityScanCenter, entity, BLOCK_ENTITY_SCAN_MOVE_THRESHOLD)) {
                    lastBlockEntityScanCenter = entity.blockPosition();

                    appendSteveAiLine(
                        serverLevel,
                        lastPlayerUuid,
                        String.format(
                            "BE scan centered on -> x=%d y=%d z=%d%n",
                            lastBlockEntityScanCenter.getX(),
                            lastBlockEntityScanCenter.getY(),
                            lastBlockEntityScanCenter.getZ()
                        )
                    );

                    poiChanged = appendNearbyBlockEntities(serverLevel, entity, 30);
                    //didBlockEntityScan = true;
                }

                if (poiChanged) {
                    java.util.List<String> summaryLines = new java.util.ArrayList<>();
                    summaryLines.add("");
                    summaryLines.add("=== POI Summary ===");
                    summaryLines.add(String.format(
                        "scanCenter=(%d,%d,%d)",
                        lastBlockEntityScanCenter.getX(),
                        lastBlockEntityScanCenter.getY(),
                        lastBlockEntityScanCenter.getZ()
                    ));

                    for (String poiLine : PoiManager.buildSummaryLines()) {
                        summaryLines.add(poiLine);
                    }

                    writeSteveAiSummary(serverLevel, lastPlayerUuid, summaryLines);
                    //PoiManager.clear();
                }
            }
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
                return;
            }

            // Spawn steveAI 4 blocks away in a random direction
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
        int blockRadius = 20;

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

        if ((poi.equals("village_candidate")) ||  (poi.equals("village"))){
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

    private static void tickExplore(ServerLevel serverLevel, Villager steveAi) {
        if (!exploreActive || exploreCenter == null) return;

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
        // First, travel to the POI center area
        if (distToCenterSq > 100.0) { // farther than 10 blocks
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

        // Then wander around the POI center
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

    private static String getSteveAiInventorySummary(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty()) {
                sb.append("slot ")
                .append(i)
                .append(": ")
                .append(slot.getHoverName().getString())
                .append(" x")
                .append(slot.getCount())
                .append("; ");
            }
        }

        return sb.length() == 0 ? "Inventory empty." : sb.toString();
    }

    private static Item findItemByName(String itemName) {
        Identifier id;

        if (itemName.contains(":")) {
            id = Identifier.tryParse(itemName);
        } else {
            id = Identifier.tryParse("minecraft:" + itemName);
        }

        if (id == null) {
            return null;
        }

        var itemRef = BuiltInRegistries.ITEM.get(id);
        return itemRef.map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    private static boolean addItemToSteveAiInventory(Villager villager, ItemStack toAdd) {
        try {
            SimpleContainer inv = villager.getInventory();

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);

                if (slot.isEmpty()) {
                    inv.setItem(i, toAdd.copy());
                    LOGGER.info("Added {} x{} to SteveAI inventory slot {}",
                            toAdd.getItem(), toAdd.getCount(), i);
                    return true;
                }

                if (ItemStack.isSameItem(slot, toAdd) && slot.getCount() < slot.getMaxStackSize()) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int move = Math.min(space, toAdd.getCount());

                    slot.grow(move);
                    toAdd.shrink(move);

                    LOGGER.info("Stacked {} of {} into SteveAI inventory slot {}",
                            move, slot.getItem(), i);

                    if (toAdd.isEmpty()) {
                        return true;
                    }
                }
            }

            LOGGER.info("SteveAI inventory full; could not add {}", toAdd.getItem());
            return false;

        } catch (Exception e) {
            LOGGER.error("Failed adding item to SteveAI inventory", e);
            return false;
        }
    }

    private static int handleInvAdd(CommandContext<CommandSourceStack> context, int count) {
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

        String itemName = StringArgumentType.getString(context, "itemName");
        Item item = findItemByName(itemName);

        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + itemName));
            return 0;
        }

        boolean added = addItemToSteveAiInventory(steveAi, new ItemStack(item, count));

        if (added) {
            String summary = getSteveAiInventorySummary(steveAi);
            String displayName = new ItemStack(item).getHoverName().getString();
            source.sendSuccess(
                () -> Component.literal("Added " + count + " " + displayName + " to SteveAI. " + summary),
                false
            );
            LOGGER.info("SteveAI invAdd -> {} x{} ; {}", itemName, count, summary);
            return 1;
        } else {
            source.sendFailure(Component.literal("SteveAI inventory full."));
            return 0;
        }
    }

    private static ItemStack removeItemFromSteveAiInventory(Villager villager, Item item, int count) {
        try {
            SimpleContainer inv = villager.getInventory();
            int remaining = count;
            ItemStack removedTotal = ItemStack.EMPTY;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);

                if (slot.isEmpty()) {
                    continue;
                }

                if (slot.getItem() != item) {
                    continue;
                }

                int take = Math.min(slot.getCount(), remaining);
                ItemStack taken = slot.split(take);

                if (removedTotal.isEmpty()) {
                    removedTotal = taken.copy();
                } else {
                    removedTotal.grow(taken.getCount());
                }

                remaining -= take;

                if (slot.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }

                if (remaining <= 0) {
                    break;
                }
            }

            return removedTotal.isEmpty() ? ItemStack.EMPTY : removedTotal;

        } catch (Exception e) {
            LOGGER.error("Failed removing item from SteveAI inventory", e);
            return ItemStack.EMPTY;
        }
    }

    private static void dropItemFromSteveAi(ServerLevel serverLevel, Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(
            serverLevel,
            villager.getX(),
            villager.getY() + 0.5,
            villager.getZ(),
            stack
        );

        itemEntity.setDefaultPickUpDelay();

        double dx = (serverLevel.random.nextDouble() - 0.5) * 0.2;
        double dy = 0.2;
        double dz = (serverLevel.random.nextDouble() - 0.5) * 0.2;
        itemEntity.setDeltaMovement(dx, dy, dz);

        serverLevel.addFreshEntity(itemEntity);
    }
    private static int handleInvDrop(CommandContext<CommandSourceStack> context, int count) {
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

        String itemName = StringArgumentType.getString(context, "itemName");
        Item item = findItemByName(itemName);

        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + itemName));
            return 0;
        }

        ItemStack removed = removeItemFromSteveAiInventory(steveAi, item, count);

        if (removed.isEmpty()) {
            source.sendFailure(Component.literal("SteveAI does not have that item."));
            return 0;
        }

        dropItemFromSteveAi(serverLevel, steveAi, removed);

        String summary = getSteveAiInventorySummary(steveAi);
        source.sendSuccess(
            () -> Component.literal(
                "SteveAI dropped " + removed.getCount() + " " +
                removed.getHoverName().getString() + ". " + summary
            ),
            false
        );

        LOGGER.info("SteveAI invDrop -> item={} requested={} dropped={} ; {}",
                itemName, count, removed.getCount(), summary);

        return 1;
    }

    private static void writeSteveAiSummary(ServerLevel serverLevel, UUID playerUuid, java.util.List<String> lines) {
        try {
            Path playerDataDir = serverLevel.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(playerDataDir);

            Path summaryFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_summary.txt");

            Files.write(
                summaryFile,
                lines,
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            LOGGER.error("Failed to write steveAI summary file", e);
        }
    }

    private static void appendSteveAiLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            LOGGER.error("CommandEvents - Writing to steveAI text file");
            Path playerDataDir = serverLevel.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            LOGGER.error("CommandEvents - Writing to steveAI text file playerDataDir:" + playerDataDir.toString());
            Files.createDirectories(playerDataDir);

            Path steveAiFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI.txt");
            LOGGER.error("CommandEvents - Writing to steveAI text file line: " + line);
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
        int chunkRadius
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

        try {
            SteveAiScanManager.scanSAI(serverLevel, steveAi, rawScanInput, chunkRadius);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int count =
            SteveAiScanManager.getScannedBlocks().size()
            + SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();

        //String normalizedInput = rawScanInput.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedInput = SteveAiScanManager.getLastScanType();
        int finalCount = count;

        source.sendSuccess(() -> Component.literal(
            "SteveAI scan complete: input=" + normalizedInput +
            " chunkRadius=" + chunkRadius +
            " groupedCount=" + finalCount
        ), false);

        return 1;
    }

    public static void appendSteveAiChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = serverLevel.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(playerDataDir);

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

        String fileContext = SteveAiContextFiles.buildChatContext(playerUuid, 200);

        String prompt =
            "You are SteveAI, a Minecraft villager. " +
            "You are shy at first and mistrustful in this new world. " +
            "You are truthful but vague and may fib to protect yourself, especially early in a relationship. " +
            "After days of knowing someone you become more open and share more detailed, personal and useful info.\n" +
            "Keep replies short if possible, even curt if warranted. " +
            "Use the context files below if relevant.\n\n" +
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
                
                // Only use chunks that are already loaded; do NOT force load.
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

                    //String typeName = blockEntity.getType().toString();
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

        ParsedScanSaiArgs(String rawScanInput, int chunkRadius) {
            this.rawScanInput = rawScanInput;
            this.chunkRadius = chunkRadius;
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
            return new ParsedScanSaiArgs("all", defaultChunkRadius);
        }

        String s = tail.trim();

        // Bracketed form: [villager bell] 3
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

            return new ParsedScanSaiArgs(rawScanInput, chunkRadius);
        }

        // Legacy/simple form: all 3
        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace > 0) {
            String maybeRadius = s.substring(lastSpace + 1).trim();
            try {
                int radius = Integer.parseInt(maybeRadius);
                String rawScanInput = s.substring(0, lastSpace).trim();
                if (rawScanInput.isEmpty()) {
                    throw new IllegalArgumentException("Missing scanSAI type or targets.");
                }
                return new ParsedScanSaiArgs(rawScanInput, radius);
            } catch (NumberFormatException ignored) {
                // whole thing is the raw input
            }
        }

        return new ParsedScanSaiArgs(s, defaultChunkRadius);
    }
    private static Villager findSteveAi(ServerLevel serverLevel) {
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

        // Search nearby spots around the player first
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

        // Fallback: try player's exact area
        return findSafeStandingPos(serverLevel, playerPos);
    }
    private static BlockPos findSafeStandingPos(ServerLevel serverLevel, BlockPos nearPos) {
        // search a little up/down in case terrain is uneven
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

        // already forcing the right chunk
        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null
                && forcedSteveAiChunkX == chunkX && forcedSteveAiChunkZ == chunkZ) {
            return;
        }

        // unforce previous chunk
        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null) {
            boolean removed = serverLevel.setChunkForced(forcedSteveAiChunkX, forcedSteveAiChunkZ, false);
            LOGGER.info("Unforced old steveAI chunk {},{} result={}", forcedSteveAiChunkX, forcedSteveAiChunkZ, removed);
        }

        // force current chunk
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
