/**
 * File: AiTools.java
 *
 * Main intent:
 * One static method per testmod command, each calling the same underlying service layer
 * (SteveAiScanManager, PoiManager, InventoryService, etc.) and returning a standard
 * AiToolResult so an AI agent can plan, invoke, and interpret commands uniformly.
 *
 * All methods are safe to call from any server-side context. Methods that need a live
 * ServerLevel accept it as their first parameter. Methods that operate on player-targeted
 * actions (tele, followMe, findMe) also accept a ServerPlayer.
 *
 * Naming mirrors toolCommands.json entries for easy cross-reference.
 */
package com.example.examplemod;

import com.example.examplemod.poi.PoiManager;
import com.example.examplemod.scan.SteveAiBiomeSurvey;
import com.example.examplemod.scan.SteveAiCollectors;
import com.example.examplemod.scan.SteveAiScanManager;
import com.example.examplemod.steveAI.CEHNavigationChunk;
import com.example.examplemod.steveAI.SteveAiChunkForcing;
import com.example.examplemod.steveAI.SteveAiLocator;
import com.sai.InventoryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AiTools {

    private AiTools() {}

    // ─── debug ────────────────────────────────────────────────────────────────

    /** Enables the SteveAI on-screen debug overlay and heartbeat log stream. */
    public static AiToolResult debugOn() {
        CommandEvents.screenDebugEnabled = true;
        ServerLoad.setHeartbeatLoggingEnabled(true);
        return AiToolResult.ok("debugOn",
            "SteveAI debug enabled (screen messages and server heartbeat stream).",
            map("screenDebugEnabled", true, "heartbeatLogging", true));
    }

    /** Disables the SteveAI on-screen debug overlay and heartbeat log stream. Default on world entry. */
    public static AiToolResult debugOff() {
        CommandEvents.screenDebugEnabled = false;
        ServerLoad.setHeartbeatLoggingEnabled(false);
        return AiToolResult.ok("debugOff",
            "SteveAI debug disabled.",
            map("screenDebugEnabled", false, "heartbeatLogging", false));
    }

    // ─── scanStatus ───────────────────────────────────────────────────────────

    /** Reports the current scan manager state from the most recent scan. */
    public static AiToolResult scanStatus() {
        String statusText = SteveAiScanManager.getStatusText();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusText", statusText);
        data.put("lastScanType", SteveAiScanManager.getLastScanType());
        data.put("lastScanChunkRadius", SteveAiScanManager.getLastScanChunkRadius());
        data.put("lastScanGameTime", SteveAiScanManager.getLastScanGameTime());
        BlockPos center = SteveAiScanManager.getLastScanCenter();
        if (center != null) {
            data.put("lastScanCenterX", center.getX());
            data.put("lastScanCenterY", center.getY());
            data.put("lastScanCenterZ", center.getZ());
        }
        return AiToolResult.ok("scanStatus", statusText, data);
    }

    // ─── biomeHere / biomeMap / biomeLocate ─────────────────────────────────

    /** Reports the player's and SteveAI's current biome, plus nearby biomes touching each current biome. */
    public static AiToolResult biomeHere(ServerLevel serverLevel, ServerPlayer player) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        BlockPos playerPos = player.blockPosition();
        BlockPos stevePos = steveAi == null ? null : steveAi.blockPosition();
        SteveAiBiomeSurvey.BiomeSurveyResult result = SteveAiBiomeSurvey.surveyAround(
            serverLevel,
            playerPos,
            playerPos,
            stevePos,
            3,
            8,
            false
        );

        Map<String, Object> data = biomeSurveyData(result);
        return AiToolResult.ok(
            "biomeHere",
            "playerBiome=" + result.playerBiomeId + " steveBiome=" + result.steveBiomeId + " time=" + result.elapsedMs + "ms",
            data
        );
    }

    /** Fast sampled biome map around the player with approximate biome boxes and center locations. */
    public static AiToolResult biomeMap(ServerLevel serverLevel, ServerPlayer player, int chunkRadius, int sampleStep, boolean forceLoad) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        BlockPos playerPos = player.blockPosition();
        BlockPos stevePos = steveAi == null ? null : steveAi.blockPosition();
        SteveAiBiomeSurvey.BiomeSurveyResult result = SteveAiBiomeSurvey.surveyAround(
            serverLevel,
            playerPos,
            playerPos,
            stevePos,
            chunkRadius,
            sampleStep,
            forceLoad
        );

        Map<String, Object> data = biomeSurveyData(result);
        return AiToolResult.ok(
            "biomeMap",
            "biomeMap areas=" + result.areas.size() + " loadedChunks=" + result.loadedChunkCount + " time=" + result.elapsedMs + "ms",
            data
        );
    }

    /** Locates the nearest specific biome using the vanilla biome-source search path. */
    public static AiToolResult biomeLocate(
        ServerLevel serverLevel,
        ServerPlayer player,
        String biome,
        int radius,
        int horizontalInterval,
        int verticalInterval
    ) {
        SteveAiBiomeSurvey.LocateBiomeResult result = SteveAiBiomeSurvey.locateNearestBiome(
            serverLevel,
            player.blockPosition(),
            biome,
            radius,
            horizontalInterval,
            verticalInterval
        );

        if (!result.found) {
            return AiToolResult.fail("biomeLocate", result.message);
        }

        return AiToolResult.ok(
            "biomeLocate",
            result.message,
            map(
                "queryBiomeId", result.queryBiomeId,
                "foundBiomeId", result.foundBiomeId,
                "originX", result.origin.getX(),
                "originY", result.origin.getY(),
                "originZ", result.origin.getZ(),
                "foundX", result.foundPos.getX(),
                "foundY", result.foundPos.getY(),
                "foundZ", result.foundPos.getZ(),
                "elapsedMs", result.elapsedMs,
                "radius", result.radius,
                "horizontalInterval", result.horizontalInterval,
                "verticalInterval", result.verticalInterval,
                "manhattanDistance", result.manhattanDistance
            )
        );
    }

    // ─── lookSee ─────────────────────────────────────────────────────────────

    /**
     * Quick 5-chunk scan around SteveAI reporting grouped block, entity, and block-entity counts.
     * Equivalent to /testmod lookSee.
     */
    public static AiToolResult lookSee(ServerLevel serverLevel) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("lookSee", "SteveAI not found.");

        BlockPos center = steveAi.blockPosition();
        final int chunkRadius = 5;

        Map<String, SteveAiCollectors.SeenSummary> blocks =
            SteveAiScanManager.scanB(serverLevel, center, chunkRadius, false);
        Map<String, SteveAiCollectors.SeenSummary> entities =
            SteveAiScanManager.scanE(serverLevel, center, chunkRadius, false);
        Map<String, SteveAiCollectors.SeenSummary> blockEntities =
            SteveAiScanManager.scanBE(serverLevel, center, chunkRadius, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("steveX", center.getX());
        data.put("steveY", center.getY());
        data.put("steveZ", center.getZ());
        data.put("chunkRadius", chunkRadius);
        data.put("blocks", summarize(blocks));
        data.put("entities", summarize(entities));
        data.put("blockEntities", summarize(blockEntities));

        String raw = "lookSee: blocks=" + sumCount(blocks)
            + " entities=" + sumCount(entities)
            + " blockEntities=" + sumCount(blockEntities);
        return AiToolResult.ok("lookSee", raw, data);
    }

    // ─── scanSAI ─────────────────────────────────────────────────────────────

    /**
     * Runs the primary SteveAI grouped scan around SteveAI's current position.
     *
     * @param rawInput    scan mode: "all", a type name, "[type1,type2]", etc.
     * @param chunkRadius chunk radius (default 2)
     */
    public static AiToolResult scanSAI(ServerLevel serverLevel, String rawInput, int chunkRadius) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("scanSAI", "SteveAI not found.");

        SteveAiScanManager.scanSAI(serverLevel, steveAi, rawInput, chunkRadius);

        Map<String, SteveAiCollectors.SeenSummary> blocks        = SteveAiScanManager.getScannedBlocks();
        Map<String, SteveAiCollectors.SeenSummary> entities      = SteveAiScanManager.getScannedEntities();
        Map<String, SteveAiCollectors.SeenSummary> blockEntities = SteveAiScanManager.getScannedBlockEntities();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rawInput", rawInput);
        data.put("chunkRadius", chunkRadius);
        data.put("blocks", summarize(blocks));
        data.put("entities", summarize(entities));
        data.put("blockEntities", summarize(blockEntities));

        String raw = String.format("scanSAI input=%s radius=%d blocks=%d entities=%d blockEntities=%d",
            rawInput, chunkRadius, sumCount(blocks), sumCount(entities), sumCount(blockEntities));
        return AiToolResult.ok("scanSAI", raw, data);
    }

    // ─── scanB / scanE / scanBE ──────────────────────────────────────────────

    /** Scans blocks only in a chunk-radius box at the given centre position. */
    public static AiToolResult scanB(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        Map<String, SteveAiCollectors.SeenSummary> result =
            SteveAiScanManager.scanB(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("blocks", summarize(result));

        return AiToolResult.ok("scanB", "scanB blocks=" + sumCount(result) + " at " + posStr(center), data);
    }

    /** Scans entities only in a chunk-radius box at the given centre position. */
    public static AiToolResult scanE(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        Map<String, SteveAiCollectors.SeenSummary> result =
            SteveAiScanManager.scanE(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("entities", summarize(result));

        return AiToolResult.ok("scanE", "scanE entities=" + sumCount(result) + " at " + posStr(center), data);
    }

    /** Scans block-entities only in a chunk-radius box at the given centre position. */
    public static AiToolResult scanBE(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        Map<String, SteveAiCollectors.SeenSummary> result =
            SteveAiScanManager.scanBE(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("blockEntities", summarize(result));

        return AiToolResult.ok("scanBE", "scanBE blockEntities=" + sumCount(result) + " at " + posStr(center), data);
    }

    // ─── scanSAI2 ────────────────────────────────────────────────────────────

    /**
     * Stage-2 deep structural scan across a chunk radius at the given centre.
     * Runs SAI2 signature matching; results stored in the scan manager.
     */
    public static AiToolResult scanSAI2(ServerLevel serverLevel, BlockPos center, int chunkRadius,
                                         boolean useCache, boolean forceLoad) {
        SteveAiScanManager.scanSAI2Radius(serverLevel, center, chunkRadius, useCache, forceLoad);

        Map<String, SteveAiCollectors.SeenSummary> blocks        = SteveAiScanManager.getScannedBlocks();
        Map<String, SteveAiCollectors.SeenSummary> entities      = SteveAiScanManager.getScannedEntities();
        Map<String, SteveAiCollectors.SeenSummary> blockEntities = SteveAiScanManager.getScannedBlockEntities();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("useCache", useCache); data.put("forceLoad", forceLoad);
        data.put("blocks", summarize(blocks));
        data.put("entities", summarize(entities));
        data.put("blockEntities", summarize(blockEntities));

        return AiToolResult.ok("scanSAI2",
            "scanSAI2 complete at " + posStr(center) + " radius=" + chunkRadius, data);
    }

    // ─── scanSAI2Likely ──────────────────────────────────────────────────────

    /**
     * Runs SAI2 on a single chunk at the given position.
     * Lighter than scanSAI2 — no radius loop; use this to probe one specific chunk.
     */
    public static AiToolResult scanSAI2Likely(ServerLevel serverLevel, BlockPos center, boolean useCache) {
        SteveAiScanManager.ChunkScanResult result =
            SteveAiScanManager.scanSAI2(serverLevel, center, useCache);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("useCache", useCache);
        data.put("chunkX", result.chunkX); data.put("chunkZ", result.chunkZ);
        data.put("chunkWasLoaded", result.chunkWasLoaded);
        data.put("blocks", summarize(result.blocks));
        data.put("entities", summarize(result.entities));
        data.put("blockEntities", summarize(result.blockEntities));

        return AiToolResult.ok("scanSAI2Likely",
            "scanSAI2Likely at " + posStr(center) + " chunkLoaded=" + result.chunkWasLoaded, data);
    }

    // ─── scanSAI3 ────────────────────────────────────────────────────────────

    /** Stage-3 scan at explicit coordinates. */
    public static AiToolResult scanSAI3(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        SteveAiScanManager.scanSAI3(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("blocks", summarize(SteveAiScanManager.getScannedBlocks()));
        data.put("entities", summarize(SteveAiScanManager.getScannedEntities()));
        data.put("blockEntities", summarize(SteveAiScanManager.getScannedBlockEntities()));

        return AiToolResult.ok("scanSAI3", "scanSAI3 complete at " + posStr(center), data);
    }

    // ─── scanSAI4 ────────────────────────────────────────────────────────────

    /** Stage-4 scan with vertical clipping around the centre Y. */
    public static AiToolResult scanSAI4(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        SteveAiScanManager.scanSAI4(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("blocks", summarize(SteveAiScanManager.getScannedBlocks()));
        data.put("entities", summarize(SteveAiScanManager.getScannedEntities()));
        data.put("blockEntities", summarize(SteveAiScanManager.getScannedBlockEntities()));

        return AiToolResult.ok("scanSAI4", "scanSAI4 complete at " + posStr(center), data);
    }

    // ─── scanSAIBroad ────────────────────────────────────────────────────────

    /**
     * Broad lightweight scan — fast coarse evidence collection.
     * chunkRadius=0 covers the single chunk at the given position.
     */
    public static AiToolResult scanSAIBroad(ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad) {
        SteveAiScanManager.scanSAIBroad(serverLevel, center, chunkRadius, forceLoad);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", center.getX()); data.put("centerY", center.getY()); data.put("centerZ", center.getZ());
        data.put("chunkRadius", chunkRadius); data.put("forceLoad", forceLoad);
        data.put("entities", summarize(SteveAiScanManager.getScannedEntities()));
        data.put("blockEntities", summarize(SteveAiScanManager.getScannedBlockEntities()));

        return AiToolResult.ok("scanSAIBroad",
            "scanSAIBroad complete at " + posStr(center) + " radius=" + chunkRadius, data);
    }

    // ─── POIfind ─────────────────────────────────────────────────────────────

    /**
     * Full POI discovery pipeline — broad sweep then deep-scans candidate chunks.
     * Dispatches via the server command system (same path as the chat auto-trigger).
     *
     * @param chunkRadius broad sweep radius in chunks (0–64)
     * @param scanLimit   max candidate chunks to deep-scan (1–128)
     */
    public static AiToolResult poiFind(ServerLevel serverLevel, ServerPlayer player,
                                        int chunkRadius, int scanLimit,
                                        boolean useCache, boolean forceLoad) {
        String cmd = String.format("testmod POIfind %d %d%s%s",
            chunkRadius, scanLimit,
            useCache  ? " UseCache"  : "",
            forceLoad ? " ForceLoad" : "");
        serverLevel.getServer().getCommands()
            .performPrefixedCommand(player.createCommandSourceStack(), cmd);
        return AiToolResult.ok("poiFind",
            "POIfind dispatched chunkRadius=" + chunkRadius + " scanLimit=" + scanLimit,
            map("chunkRadius", chunkRadius, "scanLimit", scanLimit,
                "useCache", useCache, "forceLoad", forceLoad));
    }

    /** Reports POIfind / POI map status without triggering a new scan. */
    public static AiToolResult poiFindStatus() {
        int poiCount = PoiManager.getPoiCount();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("poiCount", poiCount);
        data.put("statusText", SteveAiScanManager.getStatusText());
        return AiToolResult.ok("poiFindStatus", "POIfind status: pois=" + poiCount, data);
    }

    /** Resets POIfind state (clears the POI map and candidate queue). */
    public static AiToolResult poiFindReset() {
        PoiManager.clear();
        return AiToolResult.ok("poiFindReset", "POIfind state reset.", map("poiCount", 0));
    }

    // ─── POImap ──────────────────────────────────────────────────────────────

    /** Returns all confirmed POI entries from the in-memory POI map. */
    public static AiToolResult poiMap() {
        List<String> lines = PoiManager.buildSummaryLines();
        int count = PoiManager.getPoiCount();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("poiCount", count);
        data.put("summaryLines", lines);
        return AiToolResult.ok("poiMap", "POI map: count=" + count, data);
    }

    // ─── POIfindu ────────────────────────────────────────────────────────────

    /**
     * Focused POI search for a specific type (e.g. "village_candidate", "dungeon").
     * Dispatches via the server command system.
     */
    public static AiToolResult poiFindU(ServerLevel serverLevel, ServerPlayer player,
                                         String poiType, int chunkRadius, boolean forceLoad) {
        String cmd = String.format("testmod POIfindu %s %d%s",
            poiType, chunkRadius, forceLoad ? " ForceLoad" : "");
        serverLevel.getServer().getCommands()
            .performPrefixedCommand(player.createCommandSourceStack(), cmd);
        return AiToolResult.ok("poiFindU",
            "POIfindu dispatched poiType=" + poiType + " radius=" + chunkRadius,
            map("poiType", poiType, "chunkRadius", chunkRadius, "forceLoad", forceLoad));
    }

    // ─── POI stage 1 / 2 / reset ─────────────────────────────────────────────

    /** Stage-1: promotes block/entity evidence from the current scan into the POI map. */
    public static AiToolResult poiStage1() {
        int updates = SteveAiScanManager.updatePoiMapFromCurrentScan();
        int total   = PoiManager.getPoiCount();
        return AiToolResult.ok("poiStage1",
            "POI stage1 update: updates=" + updates + " totalPois=" + total,
            map("updates", updates, "totalPois", total));
    }

    /**
     * Stage-2: deduplicates candidate centres by chunk, runs scanSAI2 on each,
     * and promotes confirmed results into the POI map.
     *
     * @param limit  max candidate chunks to process (mirrors /testmod poiStage2 [limit])
     */
    public static AiToolResult poiStage2(ServerLevel serverLevel, int limit) {
        List<BlockPos> candidates = PoiManager.getCandidateCenters(0);
        if (candidates.isEmpty()) {
            return AiToolResult.ok("poiStage2", "No POI candidates to confirm.", map("candidateCount", 0));
        }

        java.util.LinkedHashMap<Long, BlockPos> candidateChunks = new java.util.LinkedHashMap<>();
        for (BlockPos candidate : candidates) {
            int chunkX = candidate.getX() >> 4;
            int chunkZ = candidate.getZ() >> 4;
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            candidateChunks.putIfAbsent(key, candidate);
            if (candidateChunks.size() >= limit) break;
        }

        int poiUpdates = 0;
        for (BlockPos pos : candidateChunks.values()) {
            SteveAiScanManager.scanSAI2(serverLevel, pos, false);
            poiUpdates += SteveAiScanManager.updatePoiMapFromCurrentScan();
        }

        int total = PoiManager.getPoiCount();
        String raw = "POI stage2: candidates=" + candidates.size()
            + " chunksScanned=" + candidateChunks.size()
            + " updates=" + poiUpdates
            + " totalPois=" + total;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidateCount", candidates.size());
        data.put("uniqueChunks", candidateChunks.size());
        data.put("poiUpdates", poiUpdates);
        data.put("totalPois", total);
        return AiToolResult.ok("poiStage2", raw, data);
    }

    /** Clears the entire POI map — confirmed centres, candidates, and counters. */
    public static AiToolResult poiReset() {
        PoiManager.clear();
        return AiToolResult.ok("poiReset", "POI map cleared.", map("totalPois", 0));
    }

    // ─── serverLoad ───────────────────────────────────────────────────────────

    /** Reports current server MSPT, load tier, and tuning thresholds. */
    public static AiToolResult serverLoad(ServerLevel serverLevel) {
        if (ServerLoad.isWarmingUp()) {
            return AiToolResult.ok("serverLoad",
                "Server load warming up — not enough tick samples yet.",
                map("warmingUp", true));
        }
        String msg = ServerLoad.buildServerLoadMessage(serverLevel, true);
        return AiToolResult.ok("serverLoad", msg, map("warmingUp", false, "message", msg));
    }

    /** Enables the server load heartbeat log stream. */
    public static AiToolResult serverLoadStreamOn() {
        ServerLoad.setHeartbeatLoggingEnabled(true);
        return AiToolResult.ok("serverLoadStreamOn", "Server load heartbeat stream enabled.", map("streamEnabled", true));
    }

    /** Disables the server load heartbeat log stream. */
    public static AiToolResult serverLoadStreamOff() {
        ServerLoad.setHeartbeatLoggingEnabled(false);
        return AiToolResult.ok("serverLoadStreamOff", "Server load heartbeat stream disabled.", map("streamEnabled", false));
    }

    /** Restores default server load tuning thresholds. */
    public static AiToolResult serverLoadReset() {
        ServerLoad.resetTune();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idleMspt",     ServerLoad.getIdleMaxMspt());
        data.put("busyMspt",     ServerLoad.getBusyMaxMspt());
        data.put("behindDebtMs", ServerLoad.getBehindLagDebtMs());
        data.put("emaAlpha",     ServerLoad.getLoadEmaAlpha());
        return AiToolResult.ok("serverLoadReset", "Server load tuning reset to defaults.", data);
    }

    /**
     * Applies custom server load thresholds.
     *
     * @param idleMspt     MSPT below which the server is idle
     * @param busyMspt     MSPT above which the server is busy (must be >= idleMspt)
     * @param behindDebtMs accumulated lag debt (ms) to trigger "behind" tier
     * @param emaAlpha     EMA smoothing factor (0.01–1.0)
     */
    public static AiToolResult serverLoadTune(double idleMspt, double busyMspt,
                                               double behindDebtMs, double emaAlpha) {
        if (idleMspt > busyMspt) {
            return AiToolResult.fail("serverLoadTune", "idleMspt must be <= busyMspt.");
        }
        ServerLoad.tune(idleMspt, busyMspt, behindDebtMs, emaAlpha);
        return AiToolResult.ok("serverLoadTune",
            String.format("Server load tuned: idle<=%.1f busy<=%.1f debt>=%.0f alpha=%.2f",
                idleMspt, busyMspt, behindDebtMs, emaAlpha),
            map("idleMspt", idleMspt, "busyMspt", busyMspt, "behindDebtMs", behindDebtMs, "emaAlpha", emaAlpha));
    }

    // ─── explore ─────────────────────────────────────────────────────────────

    /**
     * Directs SteveAI toward the nearest known POI of the given type.
     *
     * @param poi  keyword: "village", "dungeon", "trial", "trial_chamber",
     *             "archaeology", or "archaeology_site"
     */
    public static AiToolResult explore(ServerLevel serverLevel, String poi) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("explore", "SteveAI not found.");

        String poiNorm = poi.toLowerCase(Locale.ROOT).trim();
        String poiType = mapExplorePoiToPoiType(poiNorm);
        if (poiType == null) return AiToolResult.fail("explore", "Unsupported explore POI: " + poi);

        BlockPos nearest = poiNorm.equals("village") || poiNorm.equals("village_candidate")
            ? PoiManager.findNearestVillageForExplore(steveAi.blockPosition())
            : PoiManager.findNearestPoiCenter(poiType, steveAi.blockPosition());
        if (nearest == null) return AiToolResult.fail("explore", "No known " + poi + " POI found.");

        CommandEvents.exploreActive              = true;
        CommandEvents.explorePoi                 = poiNorm;
        CommandEvents.explorePoiType             = poiType;
        CommandEvents.exploreCenter              = nearest.immutable();
        CommandEvents.exploreRadius              = 24;
        CommandEvents.currentExploreTarget       = null;
        CommandEvents.exploredTargets.clear();
        CommandEvents.nextExploreRepathGameTime  = 0L;

        return AiToolResult.ok("explore",
            "Exploring " + poi + " at " + posStr(nearest),
            map("poi", poiNorm, "poiType", poiType,
                "centerX", nearest.getX(), "centerY", nearest.getY(), "centerZ", nearest.getZ()));
    }

    /** Stops any active SteveAI exploration task and clears exploration state. */
    public static AiToolResult exploreStop() {
        CommandEvents.exploreActive        = false;
        CommandEvents.explorePoi           = "";
        CommandEvents.explorePoiType       = "";
        CommandEvents.exploreCenter        = null;
        CommandEvents.currentExploreTarget = null;
        CommandEvents.exploredTargets.clear();
        return AiToolResult.ok("exploreStop", "SteveAI exploration stopped.", map("active", false));
    }

    /** Reports the current SteveAI exploration goal, POI type, centre, and radius. */
    public static AiToolResult exploreStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active",  CommandEvents.exploreActive);
        data.put("poi",     CommandEvents.explorePoi);
        data.put("poiType", CommandEvents.explorePoiType);
        data.put("radius",  CommandEvents.exploreRadius);
        if (CommandEvents.exploreCenter != null) {
            data.put("centerX", CommandEvents.exploreCenter.getX());
            data.put("centerY", CommandEvents.exploreCenter.getY());
            data.put("centerZ", CommandEvents.exploreCenter.getZ());
        }
        String raw = CommandEvents.exploreActive
            ? "Exploring poi=" + CommandEvents.explorePoi
                + " center=" + CommandEvents.exploreCenter
                + " radius=" + CommandEvents.exploreRadius
            : "No active exploration.";
        return AiToolResult.ok("exploreStatus", raw, data);
    }

    // ─── follow / find / stop ─────────────────────────────────────────────────

    /** Enables persistent follow mode so SteveAI continuously tracks the player. */
    public static AiToolResult followMe(ServerLevel serverLevel, ServerPlayer player) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("followMe", "SteveAI not found.");

        CommandEvents.steveAiFollowMode       = true;
        CommandEvents.steveAiFollowPlayerUuid = player.getUUID();
        boolean started = steveAi.getNavigation().moveTo(player, 1.0D);

        return AiToolResult.ok("followMe",
            "SteveAI following " + player.getName().getString() + " pathStarted=" + started,
            map("followMode", true, "playerUuid", player.getUUID().toString(), "pathStarted", started));
    }

    /** One-time path toward the player without enabling persistent follow mode. */
    public static AiToolResult findMe(ServerLevel serverLevel, ServerPlayer player) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("findMe", "SteveAI not found.");

        boolean started = steveAi.getNavigation().moveTo(player, 1.0D);
        return AiToolResult.ok("findMe",
            "SteveAI navigating toward " + player.getName().getString() + " pathStarted=" + started,
            map("pathStarted", started));
    }

    /** Disables follow mode and stops SteveAI's current navigation path. */
    public static AiToolResult stopFollow(ServerLevel serverLevel) {
        CommandEvents.steveAiFollowMode       = false;
        CommandEvents.steveAiFollowPlayerUuid = null;

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi != null) steveAi.getNavigation().stop();

        return AiToolResult.ok("stopFollow", "SteveAI follow stopped.", map("followMode", false));
    }

    // ─── chunk forcing ────────────────────────────────────────────────────────

    /** Enables chunk force-loading around SteveAI's current position. Default on world entry. */
    public static AiToolResult forceChunkOn(ServerLevel serverLevel) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("forceChunkOn", "SteveAI not found in loaded chunks.");

        CommandEvents.steveAiChunkForceEnabled = true;
        SteveAiChunkForcing.updateForcedChunkForSteveAi(serverLevel, steveAi);

        return AiToolResult.ok("forceChunkOn", "SteveAI chunk forcing enabled.", map("enabled", true));
    }

    /** Disables chunk force-loading and unforces the currently tracked chunk. */
    public static AiToolResult forceChunkOff(ServerLevel serverLevel) {
        CommandEvents.steveAiChunkForceEnabled = false;
        SteveAiChunkForcing.clearForcedSteveAiChunk(serverLevel);

        return AiToolResult.ok("forceChunkOff", "SteveAI chunk forcing disabled.", map("enabled", false));
    }

    // ─── whereRu ─────────────────────────────────────────────────────────────

    /** Reports SteveAI's current or last-known block position and dimension. */
    public static AiToolResult whereRu(ServerLevel serverLevel) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        Map<String, Object> data = new LinkedHashMap<>();

        if (steveAi != null) {
            BlockPos pos = steveAi.blockPosition();
            data.put("loaded",    true);
            data.put("x",         pos.getX());
            data.put("y",         pos.getY());
            data.put("z",         pos.getZ());
            data.put("dimension", steveAi.level().dimension().toString());
            return AiToolResult.ok("whereRu", "SteveAI loaded at " + posStr(pos), data);
        }

        if (CommandEvents.lastSteveAiKnownPos != null) {
            BlockPos pos = CommandEvents.lastSteveAiKnownPos;
            data.put("loaded",    false);
            data.put("x",         pos.getX());
            data.put("y",         pos.getY());
            data.put("z",         pos.getZ());
            data.put("dimension", CommandEvents.lastSteveAiKnownDimension != null
                ? CommandEvents.lastSteveAiKnownDimension.toString() : "unknown");
            return AiToolResult.ok("whereRu", "SteveAI unloaded, last known at " + posStr(pos), data);
        }

        return AiToolResult.fail("whereRu", "SteveAI not found and no last known position.");
    }

    // ─── tele ────────────────────────────────────────────────────────────────

    /** Teleports SteveAI to a safe standing position near the given player. */
    public static AiToolResult tele(ServerLevel serverLevel, ServerPlayer player) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("tele", "SteveAI not found.");

        BlockPos safePos = CEHNavigationChunk.findSafeTeleportPosNearPlayer(serverLevel, player);
        if (safePos == null) return AiToolResult.fail("tele", "No safe teleport position found near player.");

        steveAi.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        steveAi.getNavigation().stop();

        return AiToolResult.ok("tele",
            "SteveAI teleported to " + posStr(safePos),
            map("x", safePos.getX(), "y", safePos.getY(), "z", safePos.getZ()));
    }

    // ─── inventory ────────────────────────────────────────────────────────────

    /** Returns a summary of SteveAI's current inventory slots. */
    public static AiToolResult inv(ServerLevel serverLevel) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("inv", "SteveAI not found.");

        String summary = InventoryService.getInventorySummary(steveAi);
        return AiToolResult.ok("inv", "SteveAI inventory: " + summary, map("summary", summary));
    }

    /**
     * Adds items to SteveAI's inventory.
     *
     * @param itemName  registry name or display name (e.g. "wheat", "diamond")
     * @param count     number of items to add (min 1)
     */
    public static AiToolResult invAdd(ServerLevel serverLevel, String itemName, int count) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("invAdd", "SteveAI not found.");

        Item item = InventoryService.findItemByName(itemName);
        if (item == null) return AiToolResult.fail("invAdd", "Unknown item: " + itemName);

        boolean added = InventoryService.addItemToInventory(steveAi, new ItemStack(item, count));
        if (!added) return AiToolResult.fail("invAdd", "SteveAI inventory full.");

        String summary = InventoryService.getInventorySummary(steveAi);
        return AiToolResult.ok("invAdd",
            "Added " + count + " " + itemName + " to SteveAI. " + summary,
            map("itemName", itemName, "count", count, "summary", summary));
    }

    /**
     * Removes items from SteveAI's inventory and drops them in the world.
     *
     * @param itemName  registry name or display name
     * @param count     number of items to drop (min 1)
     */
    public static AiToolResult invDrop(ServerLevel serverLevel, String itemName, int count) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("invDrop", "SteveAI not found.");

        Item item = InventoryService.findItemByName(itemName);
        if (item == null) return AiToolResult.fail("invDrop", "Unknown item: " + itemName);

        ItemStack removed = InventoryService.removeItemFromInventory(steveAi, item, count);
        if (removed.isEmpty()) return AiToolResult.fail("invDrop", "SteveAI does not have that item.");

        InventoryService.dropItem(serverLevel, steveAi, removed);

        String summary = InventoryService.getInventorySummary(steveAi);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemName",       itemName);
        data.put("requestedCount", count);
        data.put("droppedCount",   removed.getCount());
        data.put("summary",        summary);
        return AiToolResult.ok("invDrop",
            "Dropped " + removed.getCount() + " " + itemName + ". " + summary, data);
    }

    // ─── write ────────────────────────────────────────────────────────────────

    /**
     * Forces an immediate flush of all SteveAI scan and POI summary files to disk.
     * Equivalent to /testmod writeNow.
     */
    public static AiToolResult writeNow(ServerLevel serverLevel) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) return AiToolResult.fail("writeNow", "SteveAI not found.");

        UUID playerUuid = CommandEvents.lastPlayerUuid;
        if (playerUuid == null) return AiToolResult.fail("writeNow", "No tracked player UUID.");

        Map<String, SteveAiCollectors.SeenSummary> groupedBE =
            SteveAiCollectors.collectNearbyBlockEntities(serverLevel, steveAi, 30);
        Map<String, SteveAiCollectors.SeenSummary> groupedE =
            SteveAiCollectors.collectNearbyEntities(serverLevel, steveAi, 30.0);

        PoiManager.ingestScanSummaries(java.util.Map.of(), groupedE, groupedBE);

        BlockPos center = steveAi.blockPosition();
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("");
        summaryLines.add("=== POI Summary ===");
        summaryLines.add(String.format("scanCenter=(%d,%d,%d)", center.getX(), center.getY(), center.getZ()));
        summaryLines.addAll(PoiManager.buildSummaryLines());

        CEHWrite.writeSteveAiSummary(serverLevel, playerUuid, steveAi, summaryLines);

        return AiToolResult.ok("writeNow",
            "SteveAI wrote all scan/POI files.",
            map("playerUuid", playerUuid.toString()));
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /** Converts a SeenSummary map to a plain name→count map for AiToolResult data. */
    private static Map<String, Integer> summarize(Map<String, SteveAiCollectors.SeenSummary> map) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (map != null) map.forEach((k, v) -> { if (v != null) out.put(k, v.count); });
        return out;
    }

    /** Sums total occurrence count across all entries in a SeenSummary map. */
    private static int sumCount(Map<String, SteveAiCollectors.SeenSummary> map) {
        if (map == null) return 0;
        return map.values().stream().filter(v -> v != null).mapToInt(v -> v.count).sum();
    }

    /** Formats a BlockPos as "(x,y,z)". */
    private static String posStr(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static Map<String, Object> biomeSurveyData(SteveAiBiomeSurvey.BiomeSurveyResult result) {
        List<Map<String, Object>> areas = new ArrayList<>();
        for (SteveAiBiomeSurvey.BiomeArea area : result.areas) {
            areas.add(map(
                "biomeId", area.biomeId,
                "minX", area.minX,
                "maxX", area.maxX,
                "minY", area.minY,
                "maxY", area.maxY,
                "minZ", area.minZ,
                "maxZ", area.maxZ,
                "centerX", area.centerX,
                "centerY", area.centerY,
                "centerZ", area.centerZ,
                "sampleCount", area.sampleCount,
                "evidenceScore", area.evidenceScore,
                "evidenceSources", area.evidenceSources
            ));
        }

        List<Map<String, Object>> evidenceOnly = new ArrayList<>();
        for (SteveAiBiomeSurvey.EvidenceOnlyBiome biome : result.evidenceOnlyBiomes) {
            evidenceOnly.add(map(
                "biomeId", biome.biomeId,
                "evidenceScore", biome.evidenceScore,
                "evidenceSources", biome.evidenceSources
            ));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerX", result.center.getX());
        data.put("centerY", result.center.getY());
        data.put("centerZ", result.center.getZ());
        data.put("elapsedMs", result.elapsedMs);
        data.put("chunkRadius", result.chunkRadius);
        data.put("sampleStep", result.sampleStep);
        data.put("forceLoad", result.forceLoad);
        data.put("sampleY", result.sampleY);
        data.put("centerBiomeId", result.centerBiomeId);
        data.put("playerBiomeId", result.playerBiomeId);
        data.put("steveBiomeId", result.steveBiomeId);
        data.put("playerTouchingBiomes", result.playerTouchingBiomes);
        data.put("steveTouchingBiomes", result.steveTouchingBiomes);
        data.put("loadedChunkCount", result.loadedChunkCount);
        data.put("sampledCellCount", result.sampledCellCount);
        data.put("areas", areas);
        data.put("evidenceOnlyBiomes", evidenceOnly);
        return data;
    }

    /** Maps an explore POI keyword onto its internal POI type name. Mirrors CEHExplore logic. */
    private static String mapExplorePoiToPoiType(String poi) {
        return switch (poi) {
            case "village"                         -> "village_candidate";
            case "dungeon"                         -> "dungeon";
            case "trial", "trial_chamber"          -> "trial_chamber";
            case "archaeology", "archaeology_site" -> "archaeology_site";
            default -> null;
        };
    }

    /** Convenience: build a Map<String,Object> from alternating key/value pairs. */
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return m;
    }
}
