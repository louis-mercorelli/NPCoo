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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final String VANILLA_POIS_JSON_RESOURCE_PATH =
        "data/examplemod/reference/vanilla_structures_pois.json";
    private static volatile List<UniqueSignatureRule> cachedUniqueSignatureRules = null;
    private static volatile Map<String, VanillaPoiEntry> cachedVanillaPoisMap = null;
    private static final Set<String> poiFindCheckedChunkKeys = new HashSet<>();
    private static volatile Set<String> lastDetectedStructureIds = new HashSet<>();

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

    /** Lightweight descriptor loaded from vanilla_structures_pois.json for POIfindu targeting. */
    static class VanillaPoiEntry {
        final String id;       // e.g. "minecraft:village"
        final String name;     // e.g. "Village"
        final Set<String> entities;
        final Set<String> blockEntities;

        VanillaPoiEntry(String id, String name, Set<String> entities, Set<String> blockEntities) {
            this.id = id;
            this.name = name;
            this.entities = entities;
            this.blockEntities = blockEntities;
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

    private static String poiFindChunkMemoryKey(ServerLevel serverLevel, int chunkX, int chunkZ) {
        return String.valueOf(serverLevel.dimension()) + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * Loads and caches vanilla_structures_pois.json into a map keyed by both the full id
     * (e.g. "minecraft:village") and the short name key (e.g. "village").
     * Returns the cached map on subsequent calls.
     */
    private static Map<String, VanillaPoiEntry> loadVanillaPoisMap() {
        if (cachedVanillaPoisMap != null) {
            return cachedVanillaPoisMap;
        }

        Map<String, VanillaPoiEntry> map = new java.util.HashMap<>();

        try (InputStream input = CEHScan.class.getClassLoader().getResourceAsStream(VANILLA_POIS_JSON_RESOURCE_PATH)) {
            java.io.Reader reader;
            if (input != null) {
                reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            } else {
                Path fallback = Path.of("src/main/resources/" + VANILLA_POIS_JSON_RESOURCE_PATH);
                if (!Files.exists(fallback)) {
                    throw new IllegalStateException("vanilla_structures_pois.json not found");
                }
                reader = Files.newBufferedReader(fallback, StandardCharsets.UTF_8);
            }

            try (reader) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                for (String arrayKey : new String[]{"structures", "pois"}) {
                    if (!root.has(arrayKey)) {
                        continue;
                    }
                    JsonArray arr = root.getAsJsonArray(arrayKey);
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.get("id").getAsString();
                        String name = obj.has("name") ? obj.get("name").getAsString() : id;

                        Set<String> entities = new java.util.LinkedHashSet<>();
                        Set<String> blockEntities = new java.util.LinkedHashSet<>();

                        if (obj.has("entities")) {
                            for (JsonElement e : obj.getAsJsonArray("entities")) {
                                entities.add(e.getAsString());
                            }
                        }
                        if (obj.has("block_entities")) {
                            for (JsonElement e : obj.getAsJsonArray("block_entities")) {
                                blockEntities.add(e.getAsString());
                            }
                        }

                        VanillaPoiEntry entry = new VanillaPoiEntry(id, name, entities, blockEntities);
                        map.put(id.toLowerCase(Locale.ROOT), entry);

                        // Also index by short key (text after ":") for convenience
                        int colon = id.lastIndexOf(':');
                        if (colon >= 0 && colon + 1 < id.length()) {
                            map.putIfAbsent(id.substring(colon + 1).toLowerCase(Locale.ROOT), entry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load vanilla_structures_pois.json: " + e.getMessage(), e);
        }

        cachedVanillaPoisMap = java.util.Collections.unmodifiableMap(map);
        return cachedVanillaPoisMap;
    }

    /** Resolves a user-supplied POI arg (e.g. "village", "minecraft:village") to a VanillaPoiEntry, or null. */
    private static VanillaPoiEntry resolvePoiArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        Map<String, VanillaPoiEntry> map = loadVanillaPoisMap();
        String lower = arg.toLowerCase(Locale.ROOT).trim();
        VanillaPoiEntry entry = map.get(lower);
        if (entry == null && !lower.contains(":")) {
            // Try prefixing "minecraft:"
            entry = map.get("minecraft:" + lower);
        }
        return entry;
    }

    public static int handlePoiFindReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int cleared = poiFindCheckedChunkKeys.size();
        poiFindCheckedChunkKeys.clear();
        source.sendSuccess(() -> Component.literal(
            "POIfind memory reset: clearedCheckedChunks=" + cleared
        ), false);
        return 1;
    }

    public static int handlePoiFindStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(
            "POIfind memory: checkedChunks=" + poiFindCheckedChunkKeys.size()
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

        boolean hasVillager = uniqueEntities.contains("minecraft:villager");
        boolean hasBell = uniqueBlocks.contains("minecraft:bell");
        boolean hasBed = false;
        for (String blockId : uniqueBlocks) {
            if (blockId != null && blockId.endsWith("_bed")) {
                hasBed = true;
                break;
            }
        }

        boolean hasVillageWorkstation =
            uniqueBlocks.contains("minecraft:blast_furnace")
            || uniqueBlocks.contains("minecraft:smoker")
            || uniqueBlocks.contains("minecraft:lectern")
            || uniqueBlocks.contains("minecraft:cartography_table")
            || uniqueBlocks.contains("minecraft:composter")
            || uniqueBlocks.contains("minecraft:stonecutter")
            || uniqueBlocks.contains("minecraft:loom")
            || uniqueBlocks.contains("minecraft:grindstone")
            || uniqueBlocks.contains("minecraft:fletching_table")
            || uniqueBlocks.contains("minecraft:smithing_table");

        if (!likelyIds.contains("minecraft:village") && hasVillager && (hasBell || hasBed || hasVillageWorkstation)) {
            likelyIds.add("minecraft:village");
            matchedDetails.add(
                "minecraft:village <- heuristic: entity=minecraft:villager"
                    + (hasBell ? ", block=minecraft:bell" : "")
                    + (hasBed ? ", block=* _bed" : "")
                    + (hasVillageWorkstation ? ", block=village_workstation" : "")
            );
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

    public static int handlePoiFind(
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
            source.sendFailure(Component.literal("POIfind requires a player command source."));
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
                    String memoryKey = poiFindChunkMemoryKey(serverLevel, chunkX, chunkZ);
                    if (poiFindCheckedChunkKeys.contains(memoryKey)) {
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
                        String memoryKey = poiFindChunkMemoryKey(serverLevel, chunkX, chunkZ);
                        if (poiFindCheckedChunkKeys.contains(memoryKey)) {
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
                    String memoryKey = poiFindChunkMemoryKey(serverLevel, chunkX, chunkZ);
                    if (poiFindCheckedChunkKeys.contains(memoryKey)) {
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
                        String memoryKey = poiFindChunkMemoryKey(serverLevel, chunkX, chunkZ);
                        if (poiFindCheckedChunkKeys.contains(memoryKey)) {
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
            final int checkedChunksFinal = poiFindCheckedChunkKeys.size();
            source.sendSuccess(() -> Component.literal(
                "POIfind: no new candidate chunks found from broad scan"
            ), false);
            source.sendSuccess(() -> Component.literal(
                "POIfind memory: skippedKnownChunks=" + skippedKnownChunksFinal
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
            poiFindCheckedChunkKeys.add(poiFindChunkMemoryKey(serverLevel, c.chunkX, c.chunkZ));

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
            outFile = dir.resolve("POIfind_" + serverLevel.getGameTime() + "_" + playerChunkX + "_" + playerChunkZ + ".txt");

            StringBuilder text = new StringBuilder();
            text.append("POIfind report\n");
            text.append("player=").append(playerPos.toShortString()).append(" playerChunk=").append(playerChunkX).append(",").append(playerChunkZ).append("\n");
            text.append("steveAI=").append(stevePos.toShortString()).append(" steveChunk=").append(steveChunkX).append(",").append(steveChunkZ).append("\n");
            text.append("broadCenter=").append(broadCenter.toShortString()).append(" broadChunkRadius=").append(broadChunkRadius).append("\n");
            text.append("forceLoad=").append(forceLoad).append(" useCache=").append(useCache).append("\n");
            text.append("candidateChunks=").append(candidates.size()).append(" scannedChunks=").append(toScan)
                .append(" skippedKnownChunks=").append(skippedKnownChunks)
                .append(" checkedMemorySize=").append(poiFindCheckedChunkKeys.size()).append("\n\n");

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
        final int checkedChunksFinal = poiFindCheckedChunkKeys.size();

        lastDetectedStructureIds = new HashSet<>(globalLikely);

        source.sendSuccess(() -> Component.literal(
            "POIfind complete: broadRadius=" + broadChunkRadius
                + " candidates=" + candidates.size()
                + " scanned=" + toScan
                + " skippedKnown=" + skippedKnownChunksFinal
                + " checkedMemory=" + checkedChunksFinal
                + " globalLikely=" + globalLikely.size()
                + " time=" + totalElapsedMs + "ms"
        ), false);
        source.sendSuccess(() -> Component.literal("POIfind likelyStructures: " + likelyText), false);
        source.sendSuccess(() -> Component.literal("POIfind reportFile: " + fileText), false);
        return 1;
    }

    /**
     * POIfindu — targeted variant of POIfind.
     * Runs a single scanSAIBroad then filters candidate chunks to ONLY those containing
     * entities or block-entities that match the target POI's known signature from
     * vanilla_structures_pois.json.  There is no per-chunk re-scan, making this
     * significantly faster than POIfind for finding a specific known structure.
     */
    public static int handlePoiFindU(
        CommandContext<CommandSourceStack> context,
        String poiArg,
        int broadChunkRadius,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("POIfindu requires a player command source."));
            return 0;
        }

        VanillaPoiEntry entry;
        try {
            entry = resolvePoiArg(poiArg);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("POIfindu: failed to load POI data: " + e.getMessage()));
            return 0;
        }

        if (entry == null) {
            source.sendFailure(Component.literal(
                "POIfindu: unknown POI '" + poiArg + "'. Use the structure id or short name, e.g. village, mineshaft, minecraft:village."
            ));
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

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int steveChunkX = stevePos.getX() >> 4;
        int steveChunkZ = stevePos.getZ() >> 4;

        long totalStartNs = System.nanoTime();
        try {
            SteveAiScanManager.scanSAIBroad(serverLevel, broadCenter, broadChunkRadius, forceLoad);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        Map<String, SteveAiCollectors.SeenSummary> broadEntities = SteveAiScanManager.getScannedEntities();
        Map<String, SteveAiCollectors.SeenSummary> broadBlockEntities = SteveAiScanManager.getScannedBlockEntities();

        // Collect candidate chunks only from the target POI's known entity/block-entity signals
        LinkedHashSet<Long> candidateKeys = new LinkedHashSet<>();
        List<FindPoiCandidate> candidates = new ArrayList<>();

        for (String entityId : entry.entities) {
            SteveAiCollectors.SeenSummary summary = broadEntities.get(entityId);
            if (summary == null) {
                continue;
            }
            List<BlockPos> positions = summary.allLocations.isEmpty()
                ? List.of(new BlockPos(summary.x, summary.y, summary.z))
                : summary.allLocations;
            for (BlockPos pos : positions) {
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                if (candidateKeys.add(key)) {
                    int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                        + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                    candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                }
            }
        }

        for (String beId : entry.blockEntities) {
            SteveAiCollectors.SeenSummary summary = broadBlockEntities.get(beId);
            if (summary == null) {
                continue;
            }
            List<BlockPos> positions = summary.allLocations.isEmpty()
                ? List.of(new BlockPos(summary.x, summary.y, summary.z))
                : summary.allLocations;
            for (BlockPos pos : positions) {
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                if (candidateKeys.add(key)) {
                    int score = Math.abs(chunkX - playerChunkX) + Math.abs(chunkZ - playerChunkZ)
                        + Math.abs(chunkX - steveChunkX) + Math.abs(chunkZ - steveChunkZ);
                    candidates.add(new FindPoiCandidate(chunkX, chunkZ, pos, score));
                }
            }
        }

        candidates.sort(
            Comparator
                .comparingInt((FindPoiCandidate c) -> c.score)
                .thenComparingInt(c -> c.chunkX)
                .thenComparingInt(c -> c.chunkZ)
        );

        long totalElapsedMs = (System.nanoTime() - totalStartNs) / 1_000_000L;

        // Write results file
        Path outFile = null;
        try {
            Path dir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            outFile = dir.resolve("POIfindu_" + serverLevel.getGameTime() + "_" + playerChunkX + "_" + playerChunkZ + ".txt");

            StringBuilder text = new StringBuilder();
            text.append("POIfindu report\n");
            text.append("target=").append(entry.id).append(" (").append(entry.name).append(")\n");
            text.append("player=").append(playerPos.toShortString()).append(" playerChunk=").append(playerChunkX).append(",").append(playerChunkZ).append("\n");
            text.append("steveAI=").append(stevePos.toShortString()).append(" steveChunk=").append(steveChunkX).append(",").append(steveChunkZ).append("\n");
            text.append("broadCenter=").append(broadCenter.toShortString()).append(" broadChunkRadius=").append(broadChunkRadius).append("\n");
            text.append("forceLoad=").append(forceLoad).append("\n");
            text.append("matchedChunks=").append(candidates.size()).append(" elapsedMs=").append(totalElapsedMs).append("\n\n");

            text.append("matchedChunkResults (sorted by proximity)\n");
            for (FindPoiCandidate c : candidates) {
                text.append("chunk=").append(c.chunkX).append(",").append(c.chunkZ)
                    .append(" samplePos=").append(c.samplePos.toShortString())
                    .append(" score=").append(c.score).append("\n");
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
            CommandEvents.LOGGER.warn("POIfindu failed to write output file: {}", e.getMessage());
        }

        lastDetectedStructureIds = new HashSet<>(Set.of(entry.id));

        String fileText2 = outFile == null ? "(file write failed)" : outFile.toAbsolutePath().toString();
        source.sendSuccess(() -> Component.literal(
            "POIfindu complete: target=" + entry.id
                + " broadRadius=" + broadChunkRadius
                + " matchedChunks=" + candidates.size()
                + " time=" + totalElapsedMs + "ms"
        ), false);
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("POIfindu: no chunks matched target '" + entry.id + "' in broad scan"), false);
        } else {
            FindPoiCandidate best = candidates.get(0);
            source.sendSuccess(() -> Component.literal(
                "POIfindu nearest chunk: " + best.chunkX + "," + best.chunkZ
                    + " samplePos=" + best.samplePos.toShortString()
                    + " score=" + best.score
            ), false);
        }
        source.sendSuccess(() -> Component.literal("POIfindu reportFile: " + fileText2), false);
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

    public static int handlePoiMap(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        // Get the detected structure IDs from last POIfind call
        if (lastDetectedStructureIds.isEmpty()) {
            source.sendFailure(Component.literal("No detected structures. Run /testmod POIfind first."));
            return 0;
        }

        // Load signature rules and build output lines
        List<String> outputLines = new ArrayList<>();
        outputLines.add("Structure,MinX,MaxX,MinY,MaxY,MinZ,MaxZ,CenterX,CenterY,CenterZ");

        // Load all unique signature rules
        List<UniqueSignatureRule> rules = loadUniqueSignatureRules();

        for (String structureId : lastDetectedStructureIds) {
            // Find the signature rule for this structure ID
            UniqueSignatureRule rule = null;
            for (UniqueSignatureRule r : rules) {
                if (r.id.equals(structureId)) {
                    rule = r;
                    break;
                }
            }

            if (rule == null) {
                continue;
            }

            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            // Collect all positions that match this structure's signature
            for (UniqueSignatureToken token : rule.tokens) {
                if (token.key.equals("entity")) {
                    Map<String, SteveAiCollectors.SeenSummary> entities = SteveAiScanManager.getScannedEntities();
                    if (entities.containsKey(token.value)) {
                        SteveAiCollectors.SeenSummary summary = entities.get(token.value);
                        for (BlockPos pos : summary.allLocations) {
                            minX = Math.min(minX, pos.getX());
                            maxX = Math.max(maxX, pos.getX());
                            minY = Math.min(minY, pos.getY());
                            maxY = Math.max(maxY, pos.getY());
                            minZ = Math.min(minZ, pos.getZ());
                            maxZ = Math.max(maxZ, pos.getZ());
                        }
                        // Also check the single location if no allLocations
                        if (summary.allLocations.isEmpty()) {
                            minX = Math.min(minX, summary.x);
                            maxX = Math.max(maxX, summary.x);
                            minY = Math.min(minY, summary.y);
                            maxY = Math.max(maxY, summary.y);
                            minZ = Math.min(minZ, summary.z);
                            maxZ = Math.max(maxZ, summary.z);
                        }
                    }
                } else if (token.key.equals("block_entity")) {
                    Map<String, SteveAiCollectors.SeenSummary> blockEntities = SteveAiScanManager.getScannedBlockEntities();
                    if (blockEntities.containsKey(token.value)) {
                        SteveAiCollectors.SeenSummary summary = blockEntities.get(token.value);
                        for (BlockPos pos : summary.allLocations) {
                            minX = Math.min(minX, pos.getX());
                            maxX = Math.max(maxX, pos.getX());
                            minY = Math.min(minY, pos.getY());
                            maxY = Math.max(maxY, pos.getY());
                            minZ = Math.min(minZ, pos.getZ());
                            maxZ = Math.max(maxZ, pos.getZ());
                        }
                        // Also check the single location if no allLocations
                        if (summary.allLocations.isEmpty()) {
                            minX = Math.min(minX, summary.x);
                            maxX = Math.max(maxX, summary.x);
                            minY = Math.min(minY, summary.y);
                            maxY = Math.max(maxY, summary.y);
                            minZ = Math.min(minZ, summary.z);
                            maxZ = Math.max(maxZ, summary.z);
                        }
                    }
                } else if (token.key.equals("block")) {
                    Map<String, SteveAiCollectors.SeenSummary> blocks = SteveAiScanManager.getScannedBlocks();
                    if (blocks.containsKey(token.value)) {
                        SteveAiCollectors.SeenSummary summary = blocks.get(token.value);
                        for (BlockPos pos : summary.allLocations) {
                            minX = Math.min(minX, pos.getX());
                            maxX = Math.max(maxX, pos.getX());
                            minY = Math.min(minY, pos.getY());
                            maxY = Math.max(maxY, pos.getY());
                            minZ = Math.min(minZ, pos.getZ());
                            maxZ = Math.max(maxZ, pos.getZ());
                        }
                        // Also check the single location if no allLocations
                        if (summary.allLocations.isEmpty()) {
                            minX = Math.min(minX, summary.x);
                            maxX = Math.max(maxX, summary.x);
                            minY = Math.min(minY, summary.y);
                            maxY = Math.max(maxY, summary.y);
                            minZ = Math.min(minZ, summary.z);
                            maxZ = Math.max(maxZ, summary.z);
                        }
                    }
                }
            }

            // Only add if we found at least one position
            if (minX != Double.MAX_VALUE) {
                double centerX = (minX + maxX) / 2.0;
                double centerY = (minY + maxY) / 2.0;
                double centerZ = (minZ + maxZ) / 2.0;

                outputLines.add(String.format(
                    "%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                    structureId, minX, maxX, minY, maxY, minZ, maxZ, centerX, centerY, centerZ
                ));
            }
        }

        // Write output file
        try {
            Path outDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path outFile = outDir.resolve("POImap_" + System.currentTimeMillis() + ".csv");
            Files.writeString(outFile, String.join("\n", outputLines));
            source.sendSuccess(() -> Component.literal("POImap complete: " + outputLines.size() + " structures written to " + outFile.toAbsolutePath()), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("POImap failed: " + e.getMessage()));
            return 0;
        }
    }
}
