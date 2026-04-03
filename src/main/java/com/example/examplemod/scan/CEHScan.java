/**
 * File: CEHScan.java
 *
 * Main intent:
 * Defines CEHScan functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHScan(...)}:
 *    Purpose: Prevents instantiation of this static scan-command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code parseScanSaiArgs(...)}:
 *    Purpose: Parses the freeform scanSAI argument tail into scan input, chunk radius, and fast-mode flags.
 *    Input: String tail, int defaultChunkRadius.
 *    Output: ParsedScanSaiArgs.
 * 3) {@code handleScanSaiArgs(...)}:
 *    Purpose: Handles the greedy scanSAI argument form and reports parse failures back to the command source.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 4) {@code handleScanSai(...)}:
 *    Purpose: Runs the main grouped or fast SteveAI scan flow and reports the resulting grouped counts.
 *    Input: CommandContext<CommandSourceStack> context, String rawScanInput, int chunkRadius, boolean fastMode.
 *    Output: int.
 * 5) {@code handleScanStatus(...)}:
 *    Purpose: Reports the current scan manager status for the most recently stored scan state.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 6) {@code handleDirectCenteredScan(...)}:
 *    Purpose: Runs a direct centered scanB, scanE, or scanBE request at explicit coordinates.
 *    Input: CommandContext<CommandSourceStack> context, String mode, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: int.
 * 7) {@code handleDetailSaiAtSteve(...)}:
 *    Purpose: Runs a detailed scan centered on SteveAI's current position.
 *    Input: CommandContext<CommandSourceStack> context, int radius.
 *    Output: int.
 * 8) {@code handleDetailSaiAtPos(...)}:
 *    Purpose: Runs a detailed scan centered on the requested block position.
 *    Input: CommandContext<CommandSourceStack> context, BlockPos center, int radius.
 *    Output: int.
 * 9) {@code handleScanSai3AtPos(...)}:
 *    Purpose: Runs scanSAI3 at an explicit position with chunk-radius input and optional force-load behavior.
 *    Input: CommandContext<CommandSourceStack> context, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: int.
 * 10) {@code handleScanSai2AtPos(...)}:
 *    Purpose: Runs scanSAI2 radius-mode at an explicit position with optional cache and force-load behavior.
 *    Input: CommandContext<CommandSourceStack> context, BlockPos center, int chunkRadius, boolean useCache, boolean forceLoad.
 *    Output: int.
 * 11) {@code handleScanSai4AtPos(...)}:
 *    Purpose: Runs scanSAI4 at an explicit position using 1-based chunk radius and vertical clipping around Y.
 *    Input: CommandContext<CommandSourceStack> context, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: int.
 */
package com.example.examplemod.scan;

import com.example.examplemod.CommandEvents;
import com.example.examplemod.SteveAiContextFiles;
import com.example.examplemod.steveAI.SteveAiLocator;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

public final class CEHScan {

    private static final String UNIQUE_SIGNATURES_RESOURCE_PATH =
        "data/examplemod/reference/vanilla_structures_pois_unique_signatures.txt";
    private static volatile List<UniqueSignatureRule> cachedUniqueSignatureRules = null;
    private static final Set<String> findPoiCheckedChunkKeys = new HashSet<>();

    private CEHScan() {}

    static class UniqueSignatureToken {
        final String key;
        final String value;

        UniqueSignatureToken(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    static class UniqueSignatureRule {
        final String id;
        final List<UniqueSignatureToken> tokens;

        UniqueSignatureRule(String id, List<UniqueSignatureToken> tokens) {
            this.id = id;
            this.tokens = tokens;
        }
    }

    static class Sai2LikelyAnalysis {
        final BlockPos center;
        final int chunkX;
        final int chunkZ;
        final boolean loadedBefore;
        final long elapsedMs;
        final Set<String> uniqueBlocks;
        final Set<String> uniqueEntities;
        final Set<String> uniqueBlockEntities;
        final List<String> likelyIds;
        final List<String> matchedDetails;

        Sai2LikelyAnalysis(
            BlockPos center,
            int chunkX,
            int chunkZ,
            boolean loadedBefore,
            long elapsedMs,
            Set<String> uniqueBlocks,
            Set<String> uniqueEntities,
            Set<String> uniqueBlockEntities,
            List<String> likelyIds,
            List<String> matchedDetails
        ) {
            this.center = center.immutable();
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.loadedBefore = loadedBefore;
            this.elapsedMs = elapsedMs;
            this.uniqueBlocks = uniqueBlocks;
            this.uniqueEntities = uniqueEntities;
            this.uniqueBlockEntities = uniqueBlockEntities;
            this.likelyIds = likelyIds;
            this.matchedDetails = matchedDetails;
        }
    }

    static class FindPoiCandidate {
        final int chunkX;
        final int chunkZ;
        final BlockPos samplePos;
        final int score;

        FindPoiCandidate(int chunkX, int chunkZ, BlockPos samplePos, int score) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.samplePos = samplePos.immutable();
            this.score = score;
        }
    }

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

    public static int handleScanSaiArgs(CommandContext<CommandSourceStack> context) {
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

    public static int handleScanSai(
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

    public static int handleScanStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal(SteveAiScanManager.getStatusText()),
            false
        );
        return 1;
    }

    public static int handleDirectCenteredScan(
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

    public static int handleScanSai3AtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        long startNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAI3(serverLevel, center, chunkRadius, forceLoad);
        } catch (IllegalArgumentException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            CommandEvents.LOGGER.info(
                "scanSAI3 failed thread={} center={} chunkRadius={} forceLoad={} elapsedMs={} reason={}",
                Thread.currentThread().getName(),
                center.toShortString(),
                chunkRadius,
                forceLoad,
                elapsedMs,
                e.getMessage()
            );
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int groupedCount =
            SteveAiScanManager.getScannedBlocks().size()
            + SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();
        int blockRadius = 8 + ((chunkRadius - 1) * 16);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendSuccess(() -> Component.literal(
            "scanSAI3 complete: center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " blockRadius=" + blockRadius
                + " forceLoad=" + forceLoad
                + " groupedCount=" + groupedCount
                + " time=" + elapsedMs + "ms"
                + " (see server log for block/entity/blockEntity timing breakdown)"
        ), false);

        return 1;
    }

    public static int handleScanSai2AtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int chunkRadius,
        boolean useCache,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        long startNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAI2Radius(serverLevel, center, chunkRadius, useCache, forceLoad);
        } catch (IllegalArgumentException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            CommandEvents.LOGGER.info(
                "scanSAI2Radius failed thread={} center={} chunkRadius={} useCache={} forceLoad={} elapsedMs={} reason={}",
                Thread.currentThread().getName(),
                center.toShortString(),
                chunkRadius,
                useCache,
                forceLoad,
                elapsedMs,
                e.getMessage()
            );
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int groupedCount =
            SteveAiScanManager.getScannedBlocks().size()
            + SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();

        int effectiveChunkRadius = Math.max(0, chunkRadius - 1);
        int scannedChunks = (2 * effectiveChunkRadius + 1) * (2 * effectiveChunkRadius + 1);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendSuccess(() -> Component.literal(
            "scanSAI2Radius complete: center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " effectiveChunkRadius=" + effectiveChunkRadius
                + " useCache=" + useCache
                + " forceLoad=" + forceLoad
                + " scannedChunks=" + scannedChunks
                + " groupedCount=" + groupedCount
                + " time=" + elapsedMs + "ms"
                + " (see server log for timing details)"
        ), false);

        return 1;
    }

    public static int handleScanSai4AtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        long startNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAI4(serverLevel, center, chunkRadius, forceLoad);
        } catch (IllegalArgumentException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            CommandEvents.LOGGER.info(
                "scanSAI4 failed thread={} center={} chunkRadius={} forceLoad={} elapsedMs={} reason={}",
                Thread.currentThread().getName(),
                center.toShortString(),
                chunkRadius,
                forceLoad,
                elapsedMs,
                e.getMessage()
            );
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int groupedCount =
            SteveAiScanManager.getScannedBlocks().size()
            + SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();

        int effectiveChunkRadius = Math.max(0, chunkRadius - 1);
        int scannedChunks = (2 * effectiveChunkRadius + 1) * (2 * effectiveChunkRadius + 1);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendSuccess(() -> Component.literal(
            "scanSAI4 complete: center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " effectiveChunkRadius=" + effectiveChunkRadius
                + " forceLoad=" + forceLoad
                + " scannedChunks=" + scannedChunks
                + " groupedCount=" + groupedCount
                + " time=" + elapsedMs + "ms"
                + " (y-range is centerY-16..centerY+16; see server log for timing details)"
        ), false);

        return 1;
    }

    public static int handleScanSaiBroadAtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        long startNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAIBroad(serverLevel, center, chunkRadius, forceLoad);
        } catch (IllegalArgumentException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            CommandEvents.LOGGER.info(
                "scanSAIBroad failed thread={} center={} chunkRadius={} forceLoad={} elapsedMs={} reason={}",
                Thread.currentThread().getName(),
                center.toShortString(),
                chunkRadius,
                forceLoad,
                elapsedMs,
                e.getMessage()
            );
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int groupedCount =
            SteveAiScanManager.getScannedEntities().size()
            + SteveAiScanManager.getScannedBlockEntities().size();

        int consideredChunks = (2 * chunkRadius + 1) * (2 * chunkRadius + 1);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendSuccess(() -> Component.literal(
            "scanSAIBroad complete: center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " forceLoad=" + forceLoad
                + " consideredChunks=" + consideredChunks
                + " groupedCount=" + groupedCount
                + " time=" + elapsedMs + "ms"
                + " (E+BE only; use scanSAI4 to confirm candidates)"
        ), false);

        return 1;
    }

    private static List<UniqueSignatureRule> loadUniqueSignatureRules() {
        if (cachedUniqueSignatureRules != null) {
            return cachedUniqueSignatureRules;
        }

        List<UniqueSignatureRule> rules = new ArrayList<>();

        try (InputStream input = CEHScan.class.getClassLoader().getResourceAsStream(UNIQUE_SIGNATURES_RESOURCE_PATH)) {
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        parseUniqueSignatureLine(line, rules);
                    }
                }
            } else {
                Path fallback = Path.of("src/main/resources/" + UNIQUE_SIGNATURES_RESOURCE_PATH);
                if (!Files.exists(fallback)) {
                    throw new IllegalStateException("Unique signatures file not found: " + UNIQUE_SIGNATURES_RESOURCE_PATH);
                }

                for (String line : Files.readAllLines(fallback, StandardCharsets.UTF_8)) {
                    parseUniqueSignatureLine(line, rules);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load unique signatures: " + e.getMessage(), e);
        }

        cachedUniqueSignatureRules = java.util.Collections.unmodifiableList(rules);
        return cachedUniqueSignatureRules;
    }

    private static void parseUniqueSignatureLine(String line, List<UniqueSignatureRule> out) {
        if (line == null) {
            return;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }

        int sep = trimmed.indexOf('|');
        if (sep <= 0 || sep + 1 >= trimmed.length()) {
            return;
        }

        String id = trimmed.substring(0, sep).trim();
        String tokenText = trimmed.substring(sep + 1).trim();
        if (id.isEmpty() || tokenText.isEmpty()) {
            return;
        }

        List<UniqueSignatureToken> tokens = new ArrayList<>();
        String[] parts = tokenText.split(",");
        for (String part : parts) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0 || eq + 1 >= p.length()) {
                continue;
            }

            String key = p.substring(0, eq).trim();
            String value = p.substring(eq + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                tokens.add(new UniqueSignatureToken(key, value));
            }
        }

        if (!tokens.isEmpty()) {
            out.add(new UniqueSignatureRule(id, tokens));
        }
    }

    private static boolean ruleMatches(
        UniqueSignatureRule rule,
        Set<String> blocks,
        Set<String> entities,
        Set<String> blockEntities,
        int centerY
    ) {
        for (UniqueSignatureToken token : rule.tokens) {
            switch (token.key) {
                case "entity" -> {
                    if (!entities.contains(token.value)) {
                        return false;
                    }
                }
                case "block_entity" -> {
                    if (!blockEntities.contains(token.value)) {
                        return false;
                    }
                }
                case "block" -> {
                    if (!blocks.contains(token.value)) {
                        return false;
                    }
                }
                case "y" -> {
                    String[] rangeAndCommon = token.value.split("@", 2);
                    String[] minMax = rangeAndCommon[0].split("\\.\\.", 2);
                    if (minMax.length != 2) {
                        return false;
                    }

                    int yMin;
                    int yMax;
                    try {
                        yMin = Integer.parseInt(minMax[0]);
                        yMax = Integer.parseInt(minMax[1]);
                    } catch (NumberFormatException e) {
                        return false;
                    }

                    if (centerY < yMin || centerY > yMax) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }

        return true;
    }

    private static String findPoiChunkMemoryKey(ServerLevel serverLevel, int chunkX, int chunkZ) {
        return String.valueOf(serverLevel.dimension()) + ":" + chunkX + ":" + chunkZ;
    }

    public static int handleFindPoiReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int cleared = findPoiCheckedChunkKeys.size();
        findPoiCheckedChunkKeys.clear();
        source.sendSuccess(() -> Component.literal(
            "findPOI memory reset: clearedCheckedChunks=" + cleared
        ), false);
        return 1;
    }

    public static int handleFindPoiStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(
            "findPOI memory: checkedChunks=" + findPoiCheckedChunkKeys.size()
        ), false);
        return 1;
    }

    private static Sai2LikelyAnalysis runSai2LikelyAnalysis(
        ServerLevel serverLevel,
        BlockPos center,
        boolean useCache,
        List<UniqueSignatureRule> rules
    ) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        boolean wasLoaded = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        if (!wasLoaded) {
            serverLevel.getChunk(chunkX, chunkZ);
        }

        long startNs = System.nanoTime();
        SteveAiScanManager.ChunkScanResult result = SteveAiScanManager.scanSAI2(serverLevel, center, useCache);

        Set<String> uniqueBlocks = new TreeSet<>(result.blocks.keySet());
        Set<String> uniqueEntities = new TreeSet<>(result.entities.keySet());
        Set<String> uniqueBlockEntities = new TreeSet<>(result.blockEntities.keySet());

        List<String> likelyIds = new ArrayList<>();
        List<String> matchedDetails = new ArrayList<>();
        for (UniqueSignatureRule rule : rules) {
            if (ruleMatches(rule, uniqueBlocks, uniqueEntities, uniqueBlockEntities, center.getY())) {
                likelyIds.add(rule.id);
                StringBuilder detail = new StringBuilder(rule.id).append(" <- ");
                for (int i = 0; i < rule.tokens.size(); i++) {
                    UniqueSignatureToken token = rule.tokens.get(i);
                    if (i > 0) {
                        detail.append(", ");
                    }
                    detail.append(token.key).append("=").append(token.value);
                }
                matchedDetails.add(detail.toString());
            }
        }

        java.util.Collections.sort(likelyIds);
        java.util.Collections.sort(matchedDetails);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        return new Sai2LikelyAnalysis(
            center,
            chunkX,
            chunkZ,
            wasLoaded,
            elapsedMs,
            uniqueBlocks,
            uniqueEntities,
            uniqueBlockEntities,
            likelyIds,
            matchedDetails
        );
    }

    public static int handleScanSai2LikelyAtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        boolean useCache
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        List<UniqueSignatureRule> rules;
        try {
            rules = loadUniqueSignatureRules();
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        Sai2LikelyAnalysis analysis = runSai2LikelyAnalysis(serverLevel, center, useCache, rules);
        int chunkX = analysis.chunkX;
        int chunkZ = analysis.chunkZ;
        boolean wasLoaded = analysis.loadedBefore;
        long elapsedMs = analysis.elapsedMs;
        Set<String> uniqueBlocks = analysis.uniqueBlocks;
        Set<String> uniqueEntities = analysis.uniqueEntities;
        Set<String> uniqueBlockEntities = analysis.uniqueBlockEntities;
        List<String> likelyIds = analysis.likelyIds;
        List<String> matchedDetails = analysis.matchedDetails;

        Path outFile = null;
        try {
            Path dir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            outFile = dir.resolve("scanSAI2Likely_" + serverLevel.getGameTime() + "_" + chunkX + "_" + chunkZ + ".txt");

            StringBuilder text = new StringBuilder();
            text.append("scanSAI2 likely structures report\n");
            text.append("center=").append(center.toShortString()).append("\n");
            text.append("chunk=").append(chunkX).append(",").append(chunkZ).append("\n");
            text.append("chunkWasLoaded=").append(wasLoaded).append("\n");
            text.append("useCache=").append(useCache).append("\n");
            text.append("elapsedMs=").append(elapsedMs).append("\n\n");

            text.append("uniqueEntities (count=").append(uniqueEntities.size()).append(")\n");
            for (String value : uniqueEntities) text.append(value).append("\n");
            text.append("\n");

            text.append("uniqueBlockEntities (count=").append(uniqueBlockEntities.size()).append(")\n");
            for (String value : uniqueBlockEntities) text.append(value).append("\n");
            text.append("\n");

            text.append("uniqueBlocks (count=").append(uniqueBlocks.size()).append(")\n");
            for (String value : uniqueBlocks) text.append(value).append("\n");
            text.append("\n");

            text.append("likelyStructures (count=").append(likelyIds.size()).append(")\n");
            for (String id : likelyIds) text.append(id).append("\n");
            text.append("\n");

            text.append("matchedSignatures\n");
            for (String d : matchedDetails) text.append(d).append("\n");

            Files.writeString(
                outFile,
                text.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            CommandEvents.LOGGER.warn("scanSAI2Likely failed to write output file: {}", e.getMessage());
        }

        String likelyText = likelyIds.isEmpty() ? "none" : String.join(", ", likelyIds);
        String fileText = outFile == null ? "(file write failed)" : outFile.toAbsolutePath().toString();

        source.sendSuccess(() -> Component.literal(
            "scanSAI2Likely complete: center=" + center.toShortString()
                + " chunk=" + chunkX + "," + chunkZ
                + " loadedBefore=" + wasLoaded
                + " uniqueB=" + uniqueBlocks.size()
                + " uniqueE=" + uniqueEntities.size()
                + " uniqueBE=" + uniqueBlockEntities.size()
                + " likely=" + likelyIds.size()
                + " time=" + elapsedMs + "ms"
        ), false);

        source.sendSuccess(() -> Component.literal("likelyStructures: " + likelyText), false);
        source.sendSuccess(() -> Component.literal("reportFile: " + fileText), false);

        return 1;
    }

    public static int handleFindPoi(
        CommandContext<CommandSourceStack> context,
        int broadChunkRadius,
        int scanLimit,
        boolean useCache,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("findPOI requires a player command source."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        BlockPos stevePos = steveAi.blockPosition();
        BlockPos broadCenter = new BlockPos(
            (playerPos.getX() + stevePos.getX()) / 2,
            (playerPos.getY() + stevePos.getY()) / 2,
            (playerPos.getZ() + stevePos.getZ()) / 2
        );

        long totalStartNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAIBroad(serverLevel, broadCenter, broadChunkRadius, forceLoad);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int steveChunkX = stevePos.getX() >> 4;
        int steveChunkZ = stevePos.getZ() >> 4;

        Map<String, SteveAiCollectors.SeenSummary> broadEntities = SteveAiScanManager.getScannedEntities();
        Map<String, SteveAiCollectors.SeenSummary> broadBlockEntities = SteveAiScanManager.getScannedBlockEntities();

        LinkedHashSet<Long> candidateKeys = new LinkedHashSet<>();
        List<FindPoiCandidate> candidates = new ArrayList<>();
        int skippedKnownChunks = 0;

        for (SteveAiCollectors.SeenSummary summary : broadEntities.values()) {
            if (summary == null) {
                continue;
            }

            if (summary.allLocations.isEmpty()) {
                BlockPos pos = new BlockPos(summary.x, summary.y, summary.z);
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                if (candidateKeys.add(key)) {
                    String memoryKey = findPoiChunkMemoryKey(serverLevel, chunkX, chunkZ);
                    if (findPoiCheckedChunkKeys.contains(memoryKey)) {
                        skippedKnownChunks++;
                        continue;
                    }
                    int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                        + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                    candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                }
            } else {
                for (BlockPos pos : summary.allLocations) {
                    int chunkX = pos.getX() >> 4;
                    int chunkZ = pos.getZ() >> 4;
                    long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                    if (candidateKeys.add(key)) {
                        String memoryKey = findPoiChunkMemoryKey(serverLevel, chunkX, chunkZ);
                        if (findPoiCheckedChunkKeys.contains(memoryKey)) {
                            skippedKnownChunks++;
                            continue;
                        }
                        int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                            + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                        candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                    }
                }
            }
        }

        for (SteveAiCollectors.SeenSummary summary : broadBlockEntities.values()) {
            if (summary == null) {
                continue;
            }

            if (summary.allLocations.isEmpty()) {
                BlockPos pos = new BlockPos(summary.x, summary.y, summary.z);
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                if (candidateKeys.add(key)) {
                    String memoryKey = findPoiChunkMemoryKey(serverLevel, chunkX, chunkZ);
                    if (findPoiCheckedChunkKeys.contains(memoryKey)) {
                        skippedKnownChunks++;
                        continue;
                    }
                    int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                        + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                    candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                }
            } else {
                for (BlockPos pos : summary.allLocations) {
                    int chunkX = pos.getX() >> 4;
                    int chunkZ = pos.getZ() >> 4;
                    long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                    if (candidateKeys.add(key)) {
                        String memoryKey = findPoiChunkMemoryKey(serverLevel, chunkX, chunkZ);
                        if (findPoiCheckedChunkKeys.contains(memoryKey)) {
                            skippedKnownChunks++;
                            continue;
                        }
                        int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                            + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                        candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                    }
                }
            }
        }

        candidates.sort(
            Comparator
                .comparingInt((FindPoiCandidate c) -> c.score)
                .thenComparingInt(c -> c.chunkX)
                .thenComparingInt(c -> c.chunkZ)
        );

        int toScan = Math.min(scanLimit, candidates.size());
        if (toScan <= 0) {
            final int skippedKnownChunksFinal = skippedKnownChunks;
            final int checkedChunksFinal = findPoiCheckedChunkKeys.size();
            source.sendSuccess(() -> Component.literal(
                "findPOI: no new candidate chunks found from broad scan"
            ), false);
            source.sendSuccess(() -> Component.literal(
                "findPOI memory: skippedKnownChunks=" + skippedKnownChunksFinal
                    + " checkedChunks=" + checkedChunksFinal
            ), false);
            return 1;
        }

        List<UniqueSignatureRule> rules;
        try {
            rules = loadUniqueSignatureRules();
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        List<String> chunkSummaries = new ArrayList<>();
        Set<String> globalLikely = new TreeSet<>();

        for (int i = 0; i < toScan; i++) {
            FindPoiCandidate c = candidates.get(i);
            BlockPos scanCenter = new BlockPos((c.chunkX << 4) + 8, c.samplePos.getY(), (c.chunkZ << 4) + 8);
            Sai2LikelyAnalysis analysis = runSai2LikelyAnalysis(serverLevel, scanCenter, useCache, rules);
            findPoiCheckedChunkKeys.add(findPoiChunkMemoryKey(serverLevel, c.chunkX, c.chunkZ));

            globalLikely.addAll(analysis.likelyIds);
            chunkSummaries.add(
                "chunk=" + c.chunkX + "," + c.chunkZ
                    + " score=" + c.score
                    + " likely=" + analysis.likelyIds.size()
                    + " ids=" + (analysis.likelyIds.isEmpty() ? "none" : String.join(",", analysis.likelyIds))
            );
        }

        Path outFile = null;
        try {
            Path dir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            outFile = dir.resolve("findPOI_" + serverLevel.getGameTime() + "_" + playerChunkX + "_" + playerChunkZ + ".txt");

            StringBuilder text = new StringBuilder();
            text.append("findPOI report\n");
            text.append("player=").append(playerPos.toShortString()).append(" playerChunk=").append(playerChunkX).append(",").append(playerChunkZ).append("\n");
            text.append("steveAI=").append(stevePos.toShortString()).append(" steveChunk=").append(steveChunkX).append(",").append(steveChunkZ).append("\n");
            text.append("broadCenter=").append(broadCenter.toShortString()).append(" broadChunkRadius=").append(broadChunkRadius).append("\n");
            text.append("forceLoad=").append(forceLoad).append(" useCache=").append(useCache).append("\n");
            text.append("candidateChunks=").append(candidates.size()).append(" scannedChunks=").append(toScan)
                .append(" skippedKnownChunks=").append(skippedKnownChunks)
                .append(" checkedMemorySize=").append(findPoiCheckedChunkKeys.size()).append("\n\n");

            text.append("globalLikely (count=").append(globalLikely.size()).append(")\n");
            for (String id : globalLikely) {
                text.append(id).append("\n");
            }
            text.append("\nchunkResults\n");
            for (String line : chunkSummaries) {
                text.append(line).append("\n");
            }

            Files.writeString(
                outFile,
                text.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            CommandEvents.LOGGER.warn("findPOI failed to write output file: {}", e.getMessage());
        }

        long totalElapsedMs = (System.nanoTime() - totalStartNs) / 1_000_000L;
        String likelyText = globalLikely.isEmpty() ? "none" : String.join(", ", globalLikely);
        String fileText = outFile == null ? "(file write failed)" : outFile.toAbsolutePath().toString();
        final int skippedKnownChunksFinal = skippedKnownChunks;
        final int checkedChunksFinal = findPoiCheckedChunkKeys.size();

        source.sendSuccess(() -> Component.literal(
            "findPOI complete: broadRadius=" + broadChunkRadius
                + " candidates=" + candidates.size()
                + " scanned=" + toScan
            + " skippedKnown=" + skippedKnownChunksFinal
            + " checkedMemory=" + checkedChunksFinal
                + " globalLikely=" + globalLikely.size()
                + " time=" + totalElapsedMs + "ms"
        ), false);
        source.sendSuccess(() -> Component.literal("findPOI likelyStructures: " + likelyText), false);
        source.sendSuccess(() -> Component.literal("findPOI reportFile: " + fileText), false);

        return 1;
    }

    public static int handleDetailSaiAtSteve(CommandContext<CommandSourceStack> context, int radius) {
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

    public static int handleDetailSaiAtPos(
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
