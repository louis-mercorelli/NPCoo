package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHWrite {

    private CEHWrite() {}

    static int handleWriteNow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[WRITE DEBUG] /testmod writeNow invoked"));

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        UUID playerUuid = CommandEvents.lastPlayerUuid;
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

        try {
            Path pd = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            PoiManager.loadPersonalitiesFromFile(pd.resolve("village_personalities.txt"));
        } catch (IOException ex) {
            CommandEvents.LOGGER.warn(com.sai.NpcooLog.tag("[WRITE DEBUG] Could not load village personalities file"), ex);
        }

        PoiManager.ingestScanSummaries(Map.of(), groupedEntities, groupedBlockEntities);

        BlockPos center = steveAi.blockPosition();
        java.util.List<String> summaryLines = new java.util.ArrayList<>();
        summaryLines.add("");
        summaryLines.add("=== POI Summary ===");
        summaryLines.add(String.format("scanCenter=(%d,%d,%d)", center.getX(), center.getY(), center.getZ()));
        for (String poiLine : PoiManager.buildSummaryLines()) {
            summaryLines.add(poiLine);
        }

        UUID finalPlayerUuid = playerUuid;
        writeSteveAiSummary(serverLevel, finalPlayerUuid, steveAi, summaryLines);
        source.sendSuccess(() -> Component.literal("SteveAI wrote all scan/POI files now."), false);
        return 1;
    }

    static void writeSteveAiSummary(
        ServerLevel serverLevel,
        UUID playerUuid,
        Entity steveAiEntity,
        java.util.List<String> poiSummaryLines
    ) {
        try {
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[WRITE DEBUG] writeSteveAiSummary start playerUuid={} steveAiPos={}"), playerUuid, steveAiEntity.blockPosition());

            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);

            java.util.Map<String, SteveAiCollectors.SeenSummary> groupedBlocks =
                SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAiEntity, 30, 70, SteveAiScanFilters::isInterestingLookSeeBlock);

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

            PoiManager.savePersonalitiesToFile(playerDataDir.resolve("village_personalities.txt"));

            String writeMsg = String.format(
                "[testmod] SteveAI files written %s blocks/entities/blockEntities/poiSummary/personality",
                SteveAiTime.scanTs()
            );
            contextSendStatusToPlayer(playerUuid, writeMsg);

            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[WRITE DEBUG] writeSteveAiSummary finished folder={}"), playerDataDir.toAbsolutePath());
        } catch (IOException e) {
            CommandEvents.LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI summary file"), e);
            contextSendStatusToPlayer(playerUuid, "[testmod] SteveAI file write FAILED " + SteveAiTime.scanTs() + " (see server log)");
        }
    }

    private static void contextSendStatusToPlayer(UUID playerUuid, String msg) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (!CommandEvents.screenDebugEnabled || server == null) {
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

    private static void logFileTail(String logPrefix, Path file, int maxLines) {
        try {
            if (file == null || !Files.exists(file)) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[{}] tail skipped, file missing: {}"), logPrefix, file);
                return;
            }

            java.util.List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[{}] tail for {} -> (file empty)"), logPrefix, file.getFileName());
                return;
            }

            int start = Math.max(0, lines.size() - maxLines);
            java.util.List<String> tail = lines.subList(start, lines.size());

            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[{}] tail for {} (last {} lines):"), logPrefix, file.getFileName(), tail.size());
            for (String line : tail) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("[{}] {}"), logPrefix, line);
            }
        } catch (IOException e) {
            CommandEvents.LOGGER.error(com.sai.NpcooLog.tag("Failed to log [{}] tail for file {}"), logPrefix, file, e);
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

    static int handleWriteT(CommandContext<CommandSourceStack> context) {
        try {
            ServerLevel serverLevel = context.getSource().getLevel();
            String suffix = "";
            try { suffix = StringArgumentType.getString(context, "suffix"); } catch (Exception ignored) {}
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
    }

    static int handleWriteTD(CommandContext<CommandSourceStack> context) {
        try {
            ServerLevel serverLevel = context.getSource().getLevel();
            String suffix = "";
            try { suffix = StringArgumentType.getString(context, "suffix"); } catch (Exception ignored) {}
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
    }
}
