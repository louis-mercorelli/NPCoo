package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.entity.ai.navigation.PathNavigation;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.Registries;


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
    private static final double ENTITY_SCAN_MOVE_THRESHOLD = 75.0;
    private static final double BLOCK_SCAN_MOVE_THRESHOLD = 50.0;
    private static final double BLOCK_ENTITY_SCAN_MOVE_THRESHOLD = 300.0;
    private static boolean steveAiFollowMode = false;
    private static UUID steveAiFollowPlayerUuid = null;
    private static long lastFollowTick = 0L;
    private static boolean steveAiChunkForceEnabled = true;
    private static Integer forcedSteveAiChunkX = null;
    private static Integer forcedSteveAiChunkZ = null;
    private static net.minecraft.core.BlockPos lastSteveAiKnownPos = null;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastSteveAiKnownDimension = null;

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

                                String reply = OpenAiService.ask("you are a Minecraft Villager. Your reply should be short so it doens't fill the screen. The Player asks: " + message);
                                        
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

                    appendNearbyEntities(serverLevel, entity, 100.0);
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

                    appendNearbyBlocks(serverLevel, entity, 50, 100);
                }

                boolean didBlockEntityScan = false;

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

                    appendNearbyBlockEntities(serverLevel, entity, 300);
                    didBlockEntityScan = true;
                }

                if (didBlockEntityScan) {
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
                    PoiManager.clear();
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

    private static void writeSteveAiSummary(ServerLevel serverLevel, UUID playerUuid, java.util.List<String> lines) {
        try {
            Path playerDataDir = serverLevel.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(playerDataDir);

            Path summaryFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_summary.txt");

            Files.write(
                summaryFile,
                lines,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
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

        var center = steveAiEntity.blockPosition();
        java.util.Map<String, SeenSummary> grouped = new java.util.LinkedHashMap<>();

        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    var pos = center.offset(x, y, z);
                    var state = serverLevel.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    String blockName = state.getBlock().toString();

                    SeenSummary summary = grouped.get(blockName);
                    if (summary == null) {
                        grouped.put(
                            blockName,
                            new SeenSummary(pos.getX(), pos.getY(), pos.getZ())
                        );
                    } else {
                        summary.increment();
                    }
                }
            }
        }

        if (grouped.isEmpty()) {
            appendSteveAiLine(serverLevel, lastPlayerUuid, "nearby blocks -> none found\n");
            return;
        }

        for (var entry : grouped.entrySet()) {
            String blockName = entry.getKey();
            SeenSummary summary = entry.getValue();

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

        var nearbyEntities = serverLevel.getEntities(
            (Entity) null,
            steveAiEntity.getBoundingBox().inflate(radius),
            other -> other != null && other.isAlive() && other != steveAiEntity
        );

        if (nearbyEntities.isEmpty()) {
            String line = String.format(
                "E -> none within %.1f blocks of steveAI%n",
                radius
            );
            appendSteveAiLine(serverLevel, lastPlayerUuid, line);
            return;
        }

        java.util.Map<String, SeenSummary> grouped = new java.util.LinkedHashMap<>();

        for (Entity nearby : nearbyEntities) {
            String typeName = nearby.getType().toString();

            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(
                    typeName,
                    new SeenSummary(
                        nearby.blockPosition().getX(),
                        nearby.blockPosition().getY(),
                        nearby.blockPosition().getZ()
                    )
                );
            } else {
                summary.increment();
            }
        }

        for (var entry : grouped.entrySet()) {
            String typeName = entry.getKey();
            SeenSummary summary = entry.getValue();

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

    private static void appendNearbyBlockEntities(ServerLevel serverLevel, Entity steveAiEntity, int radiusBlocks) {
        if (lastPlayerUuid == null) {
            return;
        }

        var center = steveAiEntity.blockPosition();

        int minChunkX = (center.getX() - radiusBlocks) >> 4;
        int maxChunkX = (center.getX() + radiusBlocks) >> 4;
        int minChunkZ = (center.getZ() - radiusBlocks) >> 4;
        int maxChunkZ = (center.getZ() + radiusBlocks) >> 4;

        int radiusSq = radiusBlocks * radiusBlocks;
        boolean foundAny = false;

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
                    PoiManager.processBlockEntity(typeName, pos);
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

    private static class SeenSummary {
        int x;
        int y;
        int z;
        int count;

        SeenSummary(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.count = 1;
        }

        void increment() {
            this.count++;
        }
    }
}
