package com.example.examplemod;

import com.example.examplemod.poi.PoiManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Archived periodic-scan logic extracted from CommandEvents.
 * Not currently active — tick-path calls are commented out in CommandEvents.onServerTick.
 *
 * To re-enable, uncomment in CommandEvents.onServerTick:
 *   OldPeriodScan.maybeStartPeriodicScan(server);
 *   OldPeriodScan.processPeriodicScanQueue();
 */
class OldPeriodScan {

    private OldPeriodScan() {}

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
            String line = String.format(" - %s=%s firstLoc=(%d,%d,%d) cnt=%d", kind, name, s.x, s.y, s.z, s.count);
            source.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
    }

    static void forceStartPeriodicScanNow(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        if (!CommandEvents.periodicScanQueue.isEmpty()) {
            return;
        }

        int queued = enqueuePeriodicScanJobs(server);
        if (queued > 0) {
            startPeriodicScanCycle(queued, true);
        }
    }

    static void maybeStartPeriodicScan(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % CommandEvents.PERIODIC_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!CommandEvents.periodicScanQueue.isEmpty()) {
            return;
        }

        int queued = enqueuePeriodicScanJobs(server);
        if (queued > 0) {
            startPeriodicScanCycle(queued, false);
        }
    }

    static int enqueuePeriodicScanJobs(net.minecraft.server.MinecraftServer server) {
        int queued = 0;
        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            var matches = serverLevel.getEntities(
                (net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                SteveAiLocator::isSteveAi
            );

            for (net.minecraft.world.entity.Entity entity : matches) {
                CommandEvents.periodicScanQueue.addLast(new CommandEvents.PeriodicScanJob(serverLevel, entity));
                queued++;
            }
        }
        return queued;
    }

    static void startPeriodicScanCycle(int queued, boolean forced) {
        CommandEvents.periodicScanCycleActive = true;
        CommandEvents.periodicScanCycleCount++;
        CommandEvents.periodicScanCycleStartNs = System.nanoTime();
        CommandEvents.periodicScanCycleStartTs = SteveAiTime.scanTs();

        if (!CommandEvents.initialMapBuildStarted) {
            CommandEvents.initialMapBuildStarted = true;
        }

        if (forced) {
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Periodic scan force-started: cycle={} queuedJobs={} budgetMs={}"), CommandEvents.periodicScanCycleCount, queued, CommandEvents.MAX_PERIODIC_SCAN_MS_PER_TICK);
        } else {
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Periodic scan cycle started: cycle={} queuedJobs={} budgetMs={}"), CommandEvents.periodicScanCycleCount, queued, CommandEvents.MAX_PERIODIC_SCAN_MS_PER_TICK);
        }

        notifyPeriodicScanStarted(queued);
    }

    private static void notifyPeriodicScanStarted(int queuedJobs) {
        String msg = String.format(
            "[SAI] scan#%d START %s jobs=%d",
            CommandEvents.periodicScanCycleCount,
            CommandEvents.periodicScanCycleStartTs,
            queuedJobs
        );
        sendScanStatusToTrackedPlayer(msg);
    }

    static void processPeriodicScanQueue() {
        if (CommandEvents.periodicScanQueue.isEmpty()) {
            return;
        }

        int queueSizeBefore = CommandEvents.periodicScanQueue.size();

        long startNs = System.nanoTime();
        long deadlineNs = startNs + (CommandEvents.MAX_PERIODIC_SCAN_MS_PER_TICK * 1_000_000L);
        int steps = 0;

        while (!CommandEvents.periodicScanQueue.isEmpty() && System.nanoTime() < deadlineNs) {
            CommandEvents.PeriodicScanJob job = CommandEvents.periodicScanQueue.pollFirst();
            if (job == null) {
                break;
            }

            if (!job.steveAi.isAlive()) {
                continue;
            }

            processPeriodicScanJobPhase(job);
            steps++;

            if (job.phase != null) {
                CommandEvents.periodicScanQueue.addLast(job);
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Periodic scan tick processed: steps={} remainingJobs={} elapsedMs={} budgetMs={}"),
            steps,
            CommandEvents.periodicScanQueue.size(),
            elapsedMs,
            CommandEvents.MAX_PERIODIC_SCAN_MS_PER_TICK
        );

        if (CommandEvents.periodicScanCycleActive && queueSizeBefore > 0 && CommandEvents.periodicScanQueue.isEmpty()) {
            CommandEvents.periodicScanCycleActive = false;
            if (!CommandEvents.initialMapBuildCompleted) {
                CommandEvents.initialMapBuildCompleted = true;
            }
            notifyPeriodicScanFinished();
        }
    }

    private static void notifyPeriodicScanFinished() {
        String finishTs = SteveAiTime.scanTs();
        long elapsedMs = CommandEvents.periodicScanCycleStartNs > 0L
            ? (System.nanoTime() - CommandEvents.periodicScanCycleStartNs) / 1_000_000L
            : -1L;
        String durationText = elapsedMs >= 0L
            ? String.format("%d ms (%.2f s)", elapsedMs, elapsedMs / 1000.0)
            : "unknown";

        String msg = String.format(
            "[SAI] scan#%d FINISH %s start=%s duration=%s",
            CommandEvents.periodicScanCycleCount,
            finishTs,
            (CommandEvents.periodicScanCycleStartTs == null || CommandEvents.periodicScanCycleStartTs.isEmpty()) ? "unknown" : CommandEvents.periodicScanCycleStartTs,
            durationText
        );

        CommandEvents.periodicScanCycleStartNs = 0L;
        CommandEvents.periodicScanCycleStartTs = "";
        sendScanStatusToTrackedPlayer(msg);
    }

    /** Sends an action-bar-only progress message (no chat message) to avoid flooding. */
    private static void sendScanProgressBar(String msg) {
        if (!CommandEvents.screenDebugEnabled) {
            return;
        }

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && CommandEvents.lastPlayerUuid != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(CommandEvents.lastPlayerUuid);
            if (player != null) {
                player.displayClientMessage(Component.literal(msg), true);
            }
        }
    }

    private static void sendScanStatusToTrackedPlayer(String msg) {
        sendStatusToPlayer(CommandEvents.lastPlayerUuid, msg);
    }

    private static void sendStatusToPlayer(java.util.UUID playerUuid, String msg) {
        if (!CommandEvents.screenDebugEnabled) {
            CommandEvents.LOGGER.info(msg);
            return;
        }

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            CommandEvents.LOGGER.info(msg);
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

        CommandEvents.LOGGER.info(msg);
    }

    private static void writePeriodicScanFiles(
        ServerLevel serverLevel,
        java.util.UUID playerUuid,
        Entity steveAiEntity,
        java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlocks,
        java.util.Map<String, SteveAiCollectors.SeenSummary> groupedEntities,
        java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities
    ) {
        if (playerUuid == null) {
            CommandEvents.LOGGER.warn(com.sai.NpcooLog.tag("Periodic scan write skipped because lastPlayerUuid is null"));
            return;
        }

        try {
            SteveAiScanManager.replaceScanResults(
                "all",
                CommandEvents.PERIODIC_SCAN_CHUNK_RADIUS,
                steveAiEntity.blockPosition(),
                serverLevel.getGameTime(),
                groupedBlocks,
                groupedEntities,
                groupedBlockEntities
            );

            int poiUpdates = SteveAiScanManager.updatePoiMapFromCurrentScanFast();

            java.nio.file.Path playerDataDir = SteveAiScanManager.writeTextFiles(serverLevel, playerUuid.toString());
            PoiManager.savePersonalitiesToFile(playerDataDir.resolve("village_personalities.txt"));

            String writeMsg = String.format(
                "[testmod] SteveAI full %d-chunk files written %s blocks=%d entities=%d blockEntities=%d poiUpdates=%d",
                CommandEvents.PERIODIC_SCAN_CHUNK_RADIUS,
                SteveAiTime.scanTs(),
                groupedBlocks.size(),
                groupedEntities.size(),
                groupedBlockEntities.size(),
                poiUpdates
            );
            sendStatusToPlayer(playerUuid, writeMsg);
        } catch (java.io.IOException e) {
            CommandEvents.LOGGER.error(com.sai.NpcooLog.tag("Failed to write periodic steveAI scan files"), e);
            sendStatusToPlayer(playerUuid, "[testmod] SteveAI file write FAILED " + SteveAiTime.scanTs() + " (see server log)");
        }
    }

    private static void appendWorldInfo(ServerLevel serverLevel, Entity steveAiEntity) {
        if (CommandEvents.lastPlayerUuid == null) {
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
            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
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

        appendSteveAiLine(serverLevel, CommandEvents.lastPlayerUuid, line);
    }

    private static void appendSteveAiLine(ServerLevel serverLevel, java.util.UUID playerUuid, String line) {
        try {
            java.nio.file.Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            java.nio.file.Path steveAiFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI.txt");

            java.nio.file.Files.writeString(
                steveAiFile,
                line,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (java.io.IOException e) {
            CommandEvents.LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI text file"), e);
        }
    }

    private static void processPeriodicScanJobPhase(CommandEvents.PeriodicScanJob job) {
        ServerLevel serverLevel = job.serverLevel;
        Entity entity = job.steveAi;

        switch (job.phase) {
            case LOCATION_AND_WORLD -> {
                CommandEvents.lastSteveAiKnownPos = entity.blockPosition();
                CommandEvents.lastSteveAiKnownDimension = entity.level().dimension();

                if (CommandEvents.steveAiChunkForceEnabled) {
                    SteveAiChunkForcing.updateForcedChunkForSteveAi(serverLevel, entity);
                }

                String line = String.format(
                    "steveAI location -> player=%s uuid=%s dimension=%s x=%.2f y=%.2f z=%.2f%n",
                    CommandEvents.lastPlayerName,
                    CommandEvents.lastPlayerUuid,
                    serverLevel.dimension(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
                );

                CommandEvents.LOGGER.info(
                    "steveAI location -> dimension: {}, x: {}, y: {}, z: {}",
                    serverLevel.dimension(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
                );

                appendSteveAiLine(serverLevel, CommandEvents.lastPlayerUuid, line);
                appendWorldInfo(serverLevel, entity);

                sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 1/5 LOC x=%.0f y=%.0f z=%.0f dim=%s building tile grid",
                    CommandEvents.periodicScanCycleCount,
                    entity.getX(), entity.getY(), entity.getZ(),
                    serverLevel.dimension()
                ));

                    // Build the non-overlapping tile grid covering the full scan diameter
                    BlockPos origin = entity.blockPosition();
                    job.originY = origin.getY();
                    int diameter = CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS * 2;
                    int tileW = CommandEvents.PERIODIC_TILE_BLOCK_WIDTH;
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

                job.phase = CommandEvents.PeriodicScanPhase.ENTITIES;
                }
            case ENTITIES -> {
                // Process one tile per call; accumulate results across ticks
                if (job.entityTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.entityTileIdx);
                    SteveAiCollectors.mergeInto(
                        job.groupedEntities,
                        SteveAiCollectors.collectEntitiesInTile(
                            serverLevel, tile[0], tile[1], tile[2], tile[3],
                            job.originY, CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS
                        )
                    );
                    job.entityTileIdx++;
                    sendScanProgressBar(String.format(
                        "[SAI] scan#%d 2/5 ENTITIES tile %d/%d",
                        CommandEvents.periodicScanCycleCount, job.entityTileIdx, job.totalTiles
                    ));
                }
                if (job.entityTileIdx >= job.totalTiles) {
                    appendSteveAiLine(serverLevel, CommandEvents.lastPlayerUuid, String.format(
                        "entity scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedEntities.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 2/5 ENTITIES done tiles=%d types=%d",
                        CommandEvents.periodicScanCycleCount, job.totalTiles, job.groupedEntities.size()
                    ));
                    job.phase = CommandEvents.PeriodicScanPhase.BLOCKS;
                }
            }
            case BLOCKS -> {
                // Process one tile per call; accumulate results across ticks
                if (job.blockTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.blockTileIdx);
                    int yMin = job.originY - CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS;
                    int yMax = job.originY + CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS;
                    SteveAiCollectors.mergeInto(
                        job.groupedBlocks,
                        SteveAiCollectors.collectBlocksInTile(
                            serverLevel, tile[0], tile[1], tile[2], tile[3],
                            yMin, yMax, SteveAiScanFilters::isInterestingLookSeeBlock
                        )
                    );
                    job.blockTileIdx++;
                    sendScanProgressBar(String.format(
                        "[SAI] scan#%d 3/5 BLOCKS tile %d/%d",
                        CommandEvents.periodicScanCycleCount, job.blockTileIdx, job.totalTiles
                    ));
                }
                if (job.blockTileIdx >= job.totalTiles) {
                    appendSteveAiLine(serverLevel, CommandEvents.lastPlayerUuid, String.format(
                        "block scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedBlocks.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 3/5 BLOCKS done tiles=%d types=%d",
                        CommandEvents.periodicScanCycleCount, job.totalTiles, job.groupedBlocks.size()
                    ));
                    job.phase = CommandEvents.PeriodicScanPhase.BLOCK_ENTITIES;
                }
            }
            case BLOCK_ENTITIES -> {
                // Process one tile per call; accumulate results across ticks
                if (job.beTileIdx < job.totalTiles) {
                    int[] tile = job.tiles.get(job.beTileIdx);
                    int yMin = job.originY - CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS;
                    int yMax = job.originY + CommandEvents.PERIODIC_SCAN_BLOCK_RADIUS;
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
                        CommandEvents.periodicScanCycleCount, job.beTileIdx, job.totalTiles
                    ));
                }
                if (job.beTileIdx >= job.totalTiles) {
                    appendSteveAiLine(serverLevel, CommandEvents.lastPlayerUuid, String.format(
                        "BE scan done -> tiles=%d types=%d%n",
                        job.totalTiles, job.groupedBlockEntities.size()
                    ));
                    sendScanStatusToTrackedPlayer(String.format(
                        "[SAI] scan#%d 4/5 BE done tiles=%d types=%d",
                        CommandEvents.periodicScanCycleCount, job.totalTiles, job.groupedBlockEntities.size()
                    ));
                    job.phase = CommandEvents.PeriodicScanPhase.SUMMARY;
                }
            }
            case SUMMARY -> {
                sendScanStatusToTrackedPlayer(String.format(
                    "[SAI] scan#%d 5/5 SUMMARY: ingesting POI + writing full %d-chunk files",
                    CommandEvents.periodicScanCycleCount,
                    CommandEvents.PERIODIC_SCAN_CHUNK_RADIUS
                ));
                writePeriodicScanFiles(
                    serverLevel,
                    CommandEvents.lastPlayerUuid,
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
}
