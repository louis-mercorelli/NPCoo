/**
 * File: CEHPoi.java
 *
 * Main intent:
 * Defines CEHPoi functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHPoi(...)}:
 *    Purpose: Prevents instantiation of this static POI command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code handlePoiUpdate(...)}:
 *    Purpose: Updates the POI map from the currently stored fast scan results.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code handlePoiConfirmCandidates(...)}:
 *    Purpose: Rescans candidate chunks and promotes POI candidates through the stage-two confirmation flow.
 *    Input: CommandContext<CommandSourceStack> context, int limit.
 *    Output: int.
 * 4) {@code handlePoiReset(...)}:
 *    Purpose: Clears all stored POI data.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 */
package com.example.examplemod.poi;

import com.mojang.brigadier.context.CommandContext;
import com.example.examplemod.scan.SteveAiScanManager;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;


public final class CEHPoi {

    private CEHPoi() {}

    public static int handlePoiUpdate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int updates = SteveAiScanManager.updatePoiMapFromCurrentScan();
        int poiCount = PoiManager.getPoiCount();
        source.sendSuccess(() -> Component.literal(
            "POI map updated from current scan (B+E+BE stage): updates=" + updates + " totalPois=" + poiCount
        ), false);
        return 1;
    }

    public static int handlePoiConfirmCandidates(CommandContext<CommandSourceStack> context, int limit) {
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

    public static int handlePoiReset(CommandContext<CommandSourceStack> context) {
        PoiManager.clear();
        context.getSource().sendSuccess(() -> Component.literal("POI map reset: totalPois=0"), false);
        return 1;
    }
}