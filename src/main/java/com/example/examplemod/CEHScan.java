/**
 * File: CEHScan.java
 *
 * Main intent:
 * Defines CEHScan functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: CEHScan() {}.
 * 2) {@code defaultChunkRadius)(...)}:
 *    Purpose: Implements defaultChunkRadius) logic in this file.
 *    Input: String tail, int defaultChunkRadius.
 *    Output: ParsedScanSaiArgs.
 * 3) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 4) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 5) {@code radius)(...)}:
 *    Purpose: Implements radius) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context, int radius.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.util.Locale;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHScan {

    private CEHScan() {}

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
}
