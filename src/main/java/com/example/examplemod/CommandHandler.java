package com.example.examplemod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import com.sai.InventoryService;

final class CommandHandler {

    private CommandHandler() {}

    // -------------------------------------------------------------------------
    // Server Load
    // -------------------------------------------------------------------------

    static int handleServerLoadStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        if (ServerLoad.isWarmingUp()) {
            source.sendSuccess(() -> Component.literal("§e[serverLoad] warming up: waiting for first tick samples§r"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(ServerLoad.buildServerLoadMessage(level, true)), false);
        return 1;
    }

    static int handleServerLoadTune(CommandContext<CommandSourceStack> context) {
        double newIdle = DoubleArgumentType.getDouble(context, "idleMspt");
        double newBusy = DoubleArgumentType.getDouble(context, "busyMspt");
        double newBehindDebt = DoubleArgumentType.getDouble(context, "behindDebtMs");
        double newAlpha = context.getNodes().stream().anyMatch(n -> "emaAlpha".equals(n.getNode().getName()))
            ? DoubleArgumentType.getDouble(context, "emaAlpha")
            : ServerLoad.getLoadEmaAlpha();

        if (newIdle > newBusy) {
            context.getSource().sendFailure(Component.literal("idleMspt must be <= busyMspt"));
            return 0;
        }

        ServerLoad.tune(newIdle, newBusy, newBehindDebt, newAlpha);

        context.getSource().sendSuccess(
            () -> Component.literal(String.format(
                Locale.ROOT,
                "§b[serverLoad] tuned idle<=%.1f busy<=%.1f debt>=%.0f alpha=%.2f§r",
                ServerLoad.getIdleMaxMspt(),
                ServerLoad.getBusyMaxMspt(),
                ServerLoad.getBehindLagDebtMs(),
                ServerLoad.getLoadEmaAlpha()
            )),
            false
        );
        return 1;
    }

    static int handleServerLoadStreamStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal(
                "§b[serverLoad] heartbeat stream "
                + (ServerLoad.isHeartbeatLoggingEnabled() ? "enabled" : "disabled")
                + "§r"),
            false
        );
        return 1;
    }

    static int handleServerLoadStreamToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        ServerLoad.setHeartbeatLoggingEnabled(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("§b[serverLoad] heartbeat stream " + (enabled ? "enabled" : "disabled") + "§r"),
            false
        );
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("serverLoad heartbeat stream {} by {}"),
            enabled ? "enabled" : "disabled",
            context.getSource().getTextName()
        );
        return 1;
    }

    static int handleServerLoadReset(CommandContext<CommandSourceStack> context) {
        ServerLoad.resetTune();
        context.getSource().sendSuccess(
            () -> Component.literal("§b[serverLoad] tuning reset to defaults§r"),
            false
        );
        return 1;
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    static class ParsedScanSaiArgs {
        final String rawScanInput;
        final int chunkRadius;
        final boolean fastMode;

        ParsedScanSaiArgs(String rawScanInput, int chunkRadius, boolean fastMode) {
            this.rawScanInput = rawScanInput;
            this.chunkRadius = chunkRadius;
            this.fastMode = fastMode;
        }
    }

    static ParsedScanSaiArgs parseScanSaiArgs(String tail, int defaultChunkRadius) {
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

    static int handleScanSaiArgs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String tail = StringArgumentType.getString(context, "scanArgs");

        ParsedScanSaiArgs parsed;
        try {
            parsed = parseScanSaiArgs(tail, 2);
        } catch (Exception e) {
            source.sendFailure(Component.literal("scanSAI parse error: " + e.getMessage()));
            return 0;
        }

        return handleScanSai(context, parsed.rawScanInput, parsed.chunkRadius, parsed.fastMode);
    }

    static int handleScanSai(
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

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
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
            CommandEvents.LOGGER.info(
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

        CommandEvents.LOGGER.info(
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
            "SteveAI scan complete: input=" + normalizedInput
            + " chunkRadius=" + chunkRadius
            + " fastMode=" + fastMode
            + (fastMode
                ? " detailedChunks=" + SteveAiScanManager.getLastFastDetailedChunkCount()
                    + " quickChunks=" + SteveAiScanManager.getLastFastQuickChunkCount()
                : "")
            + " groupedCount=" + finalCount
            + " (run /testmod poiStage1 to ingest into POI map)"
        ), false);

        return 1;
    }

    static int handleScanStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal(SteveAiScanManager.getStatusText()),
            false
        );
        return 1;
    }

    static int handleDirectCenteredScan(
        CommandContext<CommandSourceStack> context,
        String mode,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Map<String, SteveAiCollectors.SeenSummary> blocks = Map.of();
        Map<String, SteveAiCollectors.SeenSummary> entities = Map.of();
        Map<String, SteveAiCollectors.SeenSummary> blockEntities = Map.of();

        long startNs = System.nanoTime();
        try {
            switch (mode) {
                case "scanB" -> blocks = SteveAiScanManager.scanB(serverLevel, center, chunkRadius, forceLoad);
                case "scanE" -> entities = SteveAiScanManager.scanE(serverLevel, center, chunkRadius, forceLoad);
                case "scanBE" -> blockEntities = SteveAiScanManager.scanBE(serverLevel, center, chunkRadius, forceLoad);
                default -> {
                    source.sendFailure(Component.literal("Unknown direct scan mode: " + mode));
                    return 0;
                }
            }
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        SteveAiScanManager.replaceScanResults(
            mode.toLowerCase(Locale.ROOT),
            chunkRadius,
            center,
            serverLevel.getGameTime(),
            blocks,
            entities,
            blockEntities
        );

        int groupedCount = blocks.size() + entities.size() + blockEntities.size();
        source.sendSuccess(() -> Component.literal(
            mode + " complete: center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " forceLoad=" + forceLoad
                + " groupedCount=" + groupedCount
                + " time=" + elapsedMs + "ms"
        ), false);

        return 1;
    }

    static int handleDetailSaiAtSteve(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
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
            "SteveAI detail scan complete: center=" + steveAi.blockPosition().toShortString()
            + " radius=" + radius
            + " blocks=" + SteveAiScanManager.getDetailedBlocks().size()
            + " entities=" + SteveAiScanManager.getDetailedEntities().size()
            + " blockEntities=" + SteveAiScanManager.getDetailedBlockEntities().size()
        ), false);

        return 1;
    }

    static int handleDetailSaiAtPos(
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
            "SteveAI detail scan complete: center=" + center.toShortString()
            + " radius=" + radius
            + " blocks=" + SteveAiScanManager.getDetailedBlocks().size()
            + " entities=" + SteveAiScanManager.getDetailedEntities().size()
            + " blockEntities=" + SteveAiScanManager.getDetailedBlockEntities().size()
        ), false);

        return 1;
    }

    // -------------------------------------------------------------------------
    // Look See
    // -------------------------------------------------------------------------

    static int handleLookSee(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        BlockPos center = steveAi.blockPosition();
        int chunkRadius = 5;

        long lookSeeStartNs = System.nanoTime();
        long blocksStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> blocks =
            SteveAiScanManager.scanB(serverLevel, center, chunkRadius, false);
        long blocksMs = (System.nanoTime() - blocksStartNs) / 1_000_000L;

        long entitiesStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> entities =
            SteveAiScanManager.scanE(serverLevel, center, chunkRadius, false);
        long entitiesMs = (System.nanoTime() - entitiesStartNs) / 1_000_000L;

        long blockEntitiesStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> blockEntities =
            SteveAiScanManager.scanBE(serverLevel, center, chunkRadius, false);
        long blockEntitiesMs = (System.nanoTime() - blockEntitiesStartNs) / 1_000_000L;

        long totalMs = (System.nanoTime() - lookSeeStartNs) / 1_000_000L;

        int blocksTotal = totalCount(blocks);
        int entitiesTotal = totalCount(entities);
        int blockEntitiesTotal = totalCount(blockEntities);

        int villagers = entryCount(entities, "minecraft:villager");
        int ironGolems = entryCount(entities, "minecraft:iron_golem");
        int bellsBlocks = entryCount(blocks, "minecraft:bell");
        int bellsBlockEntities = entryCount(blockEntities, "minecraft:bell");
        int beds = entryCount(blocks, "minecraft:bed");

        source.sendSuccess(() -> Component.literal(
            "lookSee center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " groups(B/E/BE)=" + blocks.size() + "/" + entities.size() + "/" + blockEntities.size()
                + " totals(B/E/BE)=" + blocksTotal + "/" + entitiesTotal + "/" + blockEntitiesTotal
                + " signals[villager=" + villagers
                + ", iron_golem=" + ironGolems
                + ", bell(B/BE)=" + bellsBlocks + "/" + bellsBlockEntities
                + ", bed=" + beds + "]"
                + " time=" + totalMs + "ms"
                + " per(B/E/BE)=" + blocksMs + "/" + entitiesMs + "/" + blockEntitiesMs + "ms"
        ), false);

        return 1;
    }

    private static int totalCount(Map<String, SteveAiCollectors.SeenSummary> grouped) {
        if (grouped == null || grouped.isEmpty()) return 0;
        int total = 0;
        for (SteveAiCollectors.SeenSummary s : grouped.values()) {
            if (s != null) total += s.count;
        }
        return total;
    }

    private static int entryCount(Map<String, SteveAiCollectors.SeenSummary> grouped, String key) {
        if (grouped == null || key == null) return 0;
        SteveAiCollectors.SeenSummary s = grouped.get(key);
        return s == null ? 0 : s.count;
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
            String line = String.format(" - %s=%s firstLoc=(%d,%d,%d) cnt=%d", kind, name, s.x, s.y, s.z, s.count);
            source.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
    }

    // -------------------------------------------------------------------------
    // Explore
    // -------------------------------------------------------------------------

    static int handleExplorePoi(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
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

        BlockPos nearest = poi.equals("village_candidate") || poi.equals("village")
            ? PoiManager.findNearestVillageForExplore(steveAi.blockPosition())
            : PoiManager.findNearestPoiCenter(poiType, steveAi.blockPosition());

        if (nearest == null) {
            source.sendFailure(Component.literal("No known " + poi + " POI found."));
            return 0;
        }

        CommandEvents.exploreActive = true;
        CommandEvents.explorePoi = poi;
        CommandEvents.explorePoiType = poiType;
        CommandEvents.exploreCenter = nearest.immutable();
        CommandEvents.exploreRadius = 24;
        CommandEvents.currentExploreTarget = null;
        CommandEvents.exploredTargets.clear();
        CommandEvents.nextExploreRepathGameTime = 0L;

        source.sendSuccess(() -> Component.literal(
            "SteveAI exploring nearest " + poi
            + " at " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
        ), false);

        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("Explore started: poi={} poiType={} center={}"),
            CommandEvents.explorePoi, CommandEvents.explorePoiType, CommandEvents.exploreCenter
        );
        return 1;
    }

    static int handleExploreStop(CommandContext<CommandSourceStack> context) {
        OldTickExplore.stopExploreTask();
        context.getSource().sendSuccess(() -> Component.literal("SteveAI exploration stopped."), false);
        return 1;
    }

    static int handleExploreStatus(CommandContext<CommandSourceStack> context) {
        if (!CommandEvents.exploreActive || CommandEvents.exploreCenter == null) {
            context.getSource().sendSuccess(() -> Component.literal("No active exploration task."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "Exploring poi=" + CommandEvents.explorePoi
            + " poiType=" + CommandEvents.explorePoiType
            + " center=" + CommandEvents.exploreCenter.getX()
                + "," + CommandEvents.exploreCenter.getY()
                + "," + CommandEvents.exploreCenter.getZ()
            + " radius=" + CommandEvents.exploreRadius
            + " target=" + (CommandEvents.currentExploreTarget == null
                ? "none"
                : CommandEvents.currentExploreTarget.toShortString())
        ), false);

        return 1;
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

    // -------------------------------------------------------------------------
    // POI
    // -------------------------------------------------------------------------

    static int handlePoiUpdate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int updates = SteveAiScanManager.updatePoiMapFromCurrentScanFast();
        int poiCount = PoiManager.getPoiCount();
        source.sendSuccess(() -> Component.literal(
            "POI map updated from current scan (fast E+BE stage): updates=" + updates + " totalPois=" + poiCount
        ), false);
        return 1;
    }

    static int handlePoiConfirmCandidates(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        java.util.List<BlockPos> candidates = PoiManager.getCandidateCenters(0);
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No POI candidates to confirm."), false);
            return 1;
        }

        Map<Long, BlockPos> candidateChunks = new LinkedHashMap<>();
        for (BlockPos candidate : candidates) {
            int chunkX = candidate.getX() >> 4;
            int chunkZ = candidate.getZ() >> 4;
            long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            candidateChunks.putIfAbsent(chunkKey, candidate);
            if (candidateChunks.size() >= limit) break;
        }

        int scannedChunks = 0;
        int poiUpdates = 0;
        for (BlockPos candidateChunkCenter : candidateChunks.values()) {
            SteveAiScanManager.scanSAI2(serverLevel, candidateChunkCenter, false);
            poiUpdates += SteveAiScanManager.updatePoiMapFromCurrentScan();
            scannedChunks++;
        }

        int poiCount = PoiManager.getPoiCount();
        int candidateCount = candidates.size();
        int uniqueChunkCount = candidateChunks.size();
        int scannedChunksFinal = scannedChunks;
        int poiUpdatesFinal = poiUpdates;
        int poiCountFinal = poiCount;
        source.sendSuccess(() -> Component.literal(
            "POI stage2 confirmation complete: candidates=" + candidateCount
                + " candidateChunks=" + uniqueChunkCount
                + " chunksScanned=" + scannedChunksFinal
                + " updates=" + poiUpdatesFinal
                + " totalPois=" + poiCountFinal
        ), false);
        return 1;
    }

    static int handlePoiReset(CommandContext<CommandSourceStack> context) {
        PoiManager.clear();
        context.getSource().sendSuccess(() -> Component.literal("POI map reset: totalPois=0"), false);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    static int handleDebugScreenToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandEvents.screenDebugEnabled = enabled;
        context.getSource().sendSuccess(
            () -> Component.literal("SteveAI screen debug messages " + (enabled ? "enabled" : "disabled") + "."),
            false
        );
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("SteveAI screen debug messages set to {} by {}"),
            enabled, context.getSource().getTextName()
        );
        return 1;
    }

    // -------------------------------------------------------------------------
    // Navigation / Chunk
    // -------------------------------------------------------------------------

    static int handleForceChunkOn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi((ServerLevel) player.level());
        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI in loaded chunks."));
            return 0;
        }

        CommandEvents.steveAiChunkForceEnabled = true;
        SteveAiChunkForcing.updateForcedChunkForSteveAi((ServerLevel) player.level(), steveAi);
        source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing enabled."), false);
        return 1;
    }

    static int handleForceChunkOff(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        CommandEvents.steveAiChunkForceEnabled = false;
        SteveAiChunkForcing.clearForcedSteveAiChunk((ServerLevel) player.level());
        source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing disabled."), false);
        return 1;
    }

    static int handleWhereRu(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi != null) {
            String msg = String.format(
                "§6[testmod] steveAI is loaded now at dimension=%s x=%.2f y=%.2f z=%.2f",
                steveAi.level().dimension(),
                steveAi.getX(), steveAi.getY(), steveAi.getZ()
            );
            source.sendSuccess(() -> Component.literal(msg), false);
            CommandEvents.LOGGER.info(
                "whereRu loaded -> dimension={} x={} y={} z={}",
                steveAi.level().dimension(), steveAi.getX(), steveAi.getY(), steveAi.getZ()
            );
            return 1;
        }

        if (CommandEvents.lastSteveAiKnownPos != null && CommandEvents.lastSteveAiKnownDimension != null) {
            String msg = String.format(
                "§6[testmod] steveAI is not loaded. Last known position: dimension=%s x=%d y=%d z=%d",
                CommandEvents.lastSteveAiKnownDimension,
                CommandEvents.lastSteveAiKnownPos.getX(),
                CommandEvents.lastSteveAiKnownPos.getY(),
                CommandEvents.lastSteveAiKnownPos.getZ()
            );
            source.sendSuccess(() -> Component.literal(msg), false);
            CommandEvents.LOGGER.info(
                "whereRu unloaded -> last known dimension={} x={} y={} z={}",
                CommandEvents.lastSteveAiKnownDimension,
                CommandEvents.lastSteveAiKnownPos.getX(),
                CommandEvents.lastSteveAiKnownPos.getY(),
                CommandEvents.lastSteveAiKnownPos.getZ()
            );
            return 1;
        }

        source.sendFailure(Component.literal("Could not find steveAI, and no last known position is recorded."));
        return 0;
    }

    static int handleTele(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI."));
            return 0;
        }

        BlockPos safePos = findSafeTeleportPosNearPlayer(serverLevel, player);
        if (safePos == null) {
            source.sendFailure(Component.literal("Could not find a safe place to teleport steveAI."));
            return 0;
        }

        steveAi.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        steveAi.getNavigation().stop();

        source.sendSuccess(() -> Component.literal(String.format(
            "§6[testmod] steveAI teleported to x=%d y=%d z=%d",
            safePos.getX(), safePos.getY(), safePos.getZ()
        )), false);
        CommandEvents.LOGGER.info(
            "steveAI teleported safely to x={}, y={}, z={}",
            safePos.getX(), safePos.getY(), safePos.getZ()
        );
        return 1;
    }

    static BlockPos findSafeTeleportPosNearPlayer(ServerLevel serverLevel, ServerPlayer player) {
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

    static BlockPos findSafeStandingPos(ServerLevel serverLevel, BlockPos nearPos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos feetPos = nearPos.offset(0, dy, 0);
            BlockPos headPos = feetPos.above();
            BlockPos groundPos = feetPos.below();

            var feetState = serverLevel.getBlockState(feetPos);
            var headState = serverLevel.getBlockState(headPos);
            var groundState = serverLevel.getBlockState(groundPos);

            boolean feetClear = feetState.isAir();
            boolean headClear = headState.isAir();
            boolean solidGround = groundState.isCollisionShapeFullBlock(serverLevel, groundPos);

            if (feetClear && headClear && solidGround) {
                return feetPos;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Follow
    // -------------------------------------------------------------------------

    static int handleFollowMe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI."));
            return 0;
        }

        CommandEvents.steveAiFollowMode = true;
        CommandEvents.steveAiFollowPlayerUuid = player.getUUID();
        boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);

        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI is now following you."), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("steveAI followMe enabled for player {}"), player.getName().getString());
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Initial follow path start result: {}"), started);
        return 1;
    }

    static int handleFindMe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI."));
            return 0;
        }

        boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);
        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI is trying to find you."), false);
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("steveAI findMe started for player {} result={}"),
            player.getName().getString(), started
        );
        return 1;
    }

    static boolean moveSteveAiTowardPlayer(ServerLevel serverLevel, ServerPlayer player, double speed) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            return false;
        }

        PathNavigation nav = steveAi.getNavigation();
        return nav.moveTo(player, speed);
    }

    static int handleStopFollow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommandEvents.steveAiFollowMode = false;
        CommandEvents.steveAiFollowPlayerUuid = null;

        if (source.getEntity() instanceof ServerPlayer player) {
            Villager steveAi = SteveAiLocator.findSteveAi((ServerLevel) player.level());
            if (steveAi != null) steveAi.getNavigation().stop();
        }

        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI follow stopped."), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("steveAI follow mode disabled"));
        return 1;
    }

    // -------------------------------------------------------------------------
    // GUI / Inventory
    // -------------------------------------------------------------------------

    static int handleOpenGui(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 1),
                Optional.empty(),
                new ItemStack(Items.APPLE, 1),
                9999, 1, 0.05f
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
                    CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.opengui ### CREATE MENU ### called "));
                    return new SteveAiMenu(containerId, playerInventory);
                }
            });

            if (containerId.isPresent()) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Sending merchant offers to client, count={}"), offers.size());
                player.sendMerchantOffers(containerId.getAsInt(), offers, 2, 0, false, false);
            } else {
                CommandEvents.LOGGER.warn(com.sai.NpcooLog.tag("openMenu returned empty OptionalInt"));
            }
        }

        return 1;
    }

    static int handleInv(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String summary = InventoryService.getInventorySummary(steveAi);
        source.sendSuccess(() -> Component.literal("SteveAI inventory: " + summary), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("SteveAI inventory command -> {}"), summary);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Chat / AI
    // -------------------------------------------------------------------------

    static int handleSteveAiChat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String message = StringArgumentType.getString(context, "message");

        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("TESTMOD asking OpenAI...: {}"), message);
        source.sendSuccess(() -> Component.literal("§6[testmod] Asking OpenAI: " + message), false);

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        UUID playerUuid = player.getUUID();

        String reply = askSteveAi(serverLevel, playerUuid, message);
        source.sendSuccess(() -> Component.literal("§6[testmod] OpenAI reply: " + reply), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("ExampleMod OpenAI response... {}"), reply);
        return 1;
    }

    static void appendSteveAiChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
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
            CommandEvents.LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI chat file"), e);
        }
    }

    static String chatTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    static String askSteveAi(ServerLevel serverLevel, UUID playerUuid, String message) {
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("askSteveAi START playerUuid={} message={}"), playerUuid, message);

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

        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("askSteveAi FINISH playerUuid={} reply={}"), playerUuid, reply);
        return reply;
    }

}
