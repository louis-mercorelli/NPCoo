/**
 * File: SteveAiScanManager.java
 *
 * Main intent:
 * Defines SteveAiScanManager functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code ChunkScanResult(...)}:
 *    Purpose: Captures the results and metadata for a single chunk-centered scan request.
 *    Input: BlockPos requestedPos, int chunkX, int chunkZ, boolean chunkWasLoaded, long scanGameTime, Map<String, SteveAiCollectors.SeenSummary> blocks, Map<String, SteveAiCollectors.SeenSummary> entities, Map<String, SteveAiCollectors.SeenSummary> blockEntities.
 *    Output: none (constructor).
 * 2) {@code isEmpty(...)}:
 *    Purpose: Reports whether a filter target set contains no requested block, entity, or block-entity ids.
 *    Input: none.
 *    Output: boolean.
 * 3) {@code clear(...)}:
 *    Purpose: Resets all stored scan results, detail results, metadata, and cached chunk scan entries.
 *    Input: none.
 *    Output: void.
 * 4) {@code clearScannedMapsOnly(...)}:
 *    Purpose: Clears the grouped scan result maps and chunk cache while leaving detail scan state untouched.
 *    Input: none.
 *    Output: void.
 * 5) {@code clearDetailOnly(...)}:
 *    Purpose: Clears detailed scan lists and their associated last-detail metadata.
 *    Input: none.
 *    Output: void.
 * 6) {@code scanSAI(...)}:
 *    Purpose: Runs the main SteveAI grouped scan flow based on the requested scan mode and radius.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, String rawInput, int chunkRadius.
 *    Output: void.
 * 7) {@code detailSAI(...)}:
 *    Purpose: Performs a detailed block/entity/block-entity scan around a center point using block radius.
 *    Input: ServerLevel serverLevel, BlockPos center, int radiusBlocks.
 *    Output: void.
 * 8) {@code scanSAI2(...)}:
 *    Purpose: Scans one chunk region into a reusable chunk-level result object, optionally using cached data.
 *    Input: ServerLevel serverLevel, BlockPos blockPos, boolean useCache.
 *    Output: ChunkScanResult.
 * 9) {@code validateScanRadius(...)}:
 *    Purpose: Enforces configured scan radius limits and force-load constraints before scanning starts.
 *    Input: int chunkRadius, boolean forceLoad.
 *    Output: void.
 * 10) {@code forceLoadChunks(...)}:
 *    Purpose: Ensures all chunks in the requested scan area are loaded before a force-load scan executes.
 *    Input: ServerLevel serverLevel, BlockPos center, int chunkRadius.
 *    Output: void.
 * 11) {@code scanE(...)}:
 *    Purpose: Collects grouped nearby entity summaries within a chunk-radius search area.
 *    Input: ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 12) {@code scanBE(...)}:
 *    Purpose: Collects grouped nearby block-entity summaries within a chunk-radius search area.
 *    Input: ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 13) {@code scanEInTile(...)}:
 *    Purpose: Scans entities inside one rectangular tile used by incremental or fast scanning.
 *    Input: ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ, int yCenter, int yRadius.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 14) {@code scanBEInTile(...)}:
 *    Purpose: Scans block entities inside one rectangular tile using explicit vertical bounds.
 *    Input: ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ, int yMin, int yMax.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 15) {@code scanB(...)}:
 *    Purpose: Collects grouped nearby block summaries within a chunk-radius search area.
 *    Input: ServerLevel serverLevel, BlockPos center, int chunkRadius, boolean forceLoad.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 16) {@code scanSAIFast(...)}:
 *    Purpose: Executes the optimized fast scan path that mixes detailed and quick chunk processing around SteveAI and the player.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, BlockPos playerPos, String rawScanInput, int chunkRadius.
 *    Output: void.
 * 17) {@code looksLikeTargetList(...)}:
 *    Purpose: Detects whether the raw scan input is formatted as an explicit target list.
 *    Input: String rawInput.
 *    Output: boolean.
 * 18) {@code runLegacyScan(...)}:
 *    Purpose: Runs the legacy named scan modes that map to the older broad collection behaviors.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, String scanType, int blockRadius.
 *    Output: void.
 * 19) {@code runFilteredScan(...)}:
 *    Purpose: Runs a filtered scan using parsed explicit target ids and aliases.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, String rawInput, int blockRadius.
 *    Output: void.
 * 20) {@code parseTargetTokens(...)}:
 *    Purpose: Splits a raw target-list input string into normalized token entries.
 *    Input: String rawInput.
 *    Output: java.util.List<String>.
 * 21) {@code resolveFilterTargets(...)}:
 *    Purpose: Converts parsed target tokens into concrete block/entity/block-entity target sets.
 *    Input: java.util.List<String> tokens.
 *    Output: ScanFilterTargets.
 * 22) {@code resolveAlias(...)}:
 *    Purpose: Maps user-friendly aliases to canonical namespaced target ids.
 *    Input: String token.
 *    Output: String.
 * 23) {@code mapToClusteredText(...)}:
 *    Purpose: Serializes grouped seen-summary maps into readable clustered report text.
 *    Input: Map<String, SteveAiCollectors.SeenSummary> map, String title.
 *    Output: String.
 * 24) {@code detailListToText(...)}:
 *    Purpose: Serializes detailed scan entries into readable line-based text.
 *    Input: List<SteveAiCollectors.DetailedEntry> entries, String title.
 *    Output: String.
 * 25) {@code splitIntoTouchingClusters(...)}:
 *    Purpose: Groups positions into connected clusters based on touching-neighbor adjacency.
 *    Input: java.util.List<BlockPos> positions.
 *    Output: java.util.List<java.util.Set<BlockPos>>.
 * 26) {@code getTouchingNeighbors(...)}:
 *    Purpose: Returns the orthogonally touching neighboring block positions for a source position.
 *    Input: BlockPos pos.
 *    Output: java.util.List<BlockPos>.
 * 27) {@code updatePoiMapFromCurrentScan(...)}:
 *    Purpose: Pushes the current grouped scan results into the POI manager using full ingestion.
 *    Input: none.
 *    Output: int.
 * 28) {@code updatePoiMapFromCurrentScanFast(...)}:
 *    Purpose: Pushes the current grouped scan results into the POI manager using fast ingestion.
 *    Input: none.
 *    Output: int.
 * 29) {@code replaceScanResults(...)}:
 *    Purpose: Replaces all stored grouped scan state and metadata with externally supplied scan results.
 *    Input: String scanType, int chunkRadius, BlockPos center, long gameTime, Map<String, SteveAiCollectors.SeenSummary> blocks, Map<String, SteveAiCollectors.SeenSummary> entities, Map<String, SteveAiCollectors.SeenSummary> blockEntities.
 *    Output: void.
 * 30) {@code getScannedBlocks(...)}:
 *    Purpose: Returns the current grouped block scan map.
 *    Input: none.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 31) {@code getChunkScanResult(...)}:
 *    Purpose: Returns the cached chunk scan result for one chunk coordinate pair.
 *    Input: int chunkX, int chunkZ.
 *    Output: ChunkScanResult.
 * 32) {@code getChunkScanResults(...)}:
 *    Purpose: Returns the full map of cached chunk scan results.
 *    Input: none.
 *    Output: Map<Long, ChunkScanResult>.
 * 33) {@code getScannedEntities(...)}:
 *    Purpose: Returns the current grouped entity scan map.
 *    Input: none.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 34) {@code getScannedBlockEntities(...)}:
 *    Purpose: Returns the current grouped block-entity scan map.
 *    Input: none.
 *    Output: Map<String, SteveAiCollectors.SeenSummary>.
 * 35) {@code getDetailedBlocks(...)}:
 *    Purpose: Returns the most recent detailed block scan entries.
 *    Input: none.
 *    Output: List<SteveAiCollectors.DetailedEntry>.
 * 36) {@code getDetailedEntities(...)}:
 *    Purpose: Returns the most recent detailed entity scan entries.
 *    Input: none.
 *    Output: List<SteveAiCollectors.DetailedEntry>.
 * 37) {@code getDetailedBlockEntities(...)}:
 *    Purpose: Returns the most recent detailed block-entity scan entries.
 *    Input: none.
 *    Output: List<SteveAiCollectors.DetailedEntry>.
 * 38) {@code getLastScanChunkRadius(...)}:
 *    Purpose: Returns the chunk radius used by the last grouped scan.
 *    Input: none.
 *    Output: int.
 * 39) {@code getLastScanType(...)}:
 *    Purpose: Returns the normalized scan type recorded for the last grouped scan.
 *    Input: none.
 *    Output: String.
 * 40) {@code getLastScanCenter(...)}:
 *    Purpose: Returns the center position used by the last grouped scan.
 *    Input: none.
 *    Output: BlockPos.
 * 41) {@code getLastScanGameTime(...)}:
 *    Purpose: Returns the game-time tick when the last grouped scan was stored.
 *    Input: none.
 *    Output: long.
 * 42) {@code getLastDetailRadius(...)}:
 *    Purpose: Returns the radius used by the last detailed scan.
 *    Input: none.
 *    Output: int.
 * 43) {@code getLastDetailCenter(...)}:
 *    Purpose: Returns the center position used by the last detailed scan.
 *    Input: none.
 *    Output: BlockPos.
 * 44) {@code getLastDetailGameTime(...)}:
 *    Purpose: Returns the game-time tick when the last detailed scan was stored.
 *    Input: none.
 *    Output: long.
 * 45) {@code getLastFastDetailedChunkCount(...)}:
 *    Purpose: Returns how many chunks were processed in detailed mode by the last fast scan.
 *    Input: none.
 *    Output: int.
 * 46) {@code getLastFastQuickChunkCount(...)}:
 *    Purpose: Returns how many chunks were processed in quick mode by the last fast scan.
 *    Input: none.
 *    Output: int.
 * 47) {@code getStatusText(...)}:
 *    Purpose: Builds a human-readable status summary of the currently stored scan state.
 *    Input: none.
 *    Output: String.
 * 48) {@code writeTextFiles(...)}:
 *    Purpose: Writes the current grouped scan results and POI summary to text files.
 *    Input: ServerLevel serverLevel, String suffix.
 *    Output: Path.
 * 49) {@code writeDetailTextFiles(...)}:
 *    Purpose: Writes the current detailed scan results to text files.
 *    Input: ServerLevel serverLevel, String suffix.
 *    Output: Path.
 * 50) {@code mapToText(...)}:
 *    Purpose: Converts a grouped summary map into a simple titled text block.
 *    Input: Map<String, SteveAiCollectors.SeenSummary> map, String title.
 *    Output: String.
 * 51) {@code buildFileName(...)}:
 *    Purpose: Builds an output filename from a base label and sanitized suffix.
 *    Input: String baseName, String safeSuffix.
 *    Output: String.
 * 52) {@code sanitizeSuffix(...)}:
 *    Purpose: Normalizes an optional suffix into a filesystem-safe filename fragment.
 *    Input: String suffix.
 *    Output: String.
 * 53) {@code blankIfNeeded(...)}:
 *    Purpose: Returns an empty string for null values so text serialization stays stable.
 *    Input: String value.
 *    Output: String.
 * 54) {@code chunkKey(...)}:
 *    Purpose: Encodes chunk X/Z coordinates into a single map key.
 *    Input: int chunkX, int chunkZ.
 *    Output: long.
 */
package com.example.examplemod.scan;

import com.example.examplemod.SteveAiContextFiles;
import com.example.examplemod.poi.PoiManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SteveAiScanManager {

    private static final int MAX_STORED_BLOCK_LOCATIONS_PER_TYPE = 20;
    private static final int FORCE_LOAD_MAX_CHUNK_RADIUS = 4;

    private static Map<String, SteveAiCollectors.SeenSummary> scannedBlocks = new LinkedHashMap<>();
    private static Map<String, SteveAiCollectors.SeenSummary> scannedEntities = new LinkedHashMap<>();
    private static Map<String, SteveAiCollectors.SeenSummary> scannedBlockEntities = new LinkedHashMap<>();

    private static List<SteveAiCollectors.DetailedEntry> detailedBlocks = new java.util.ArrayList<>();
    private static List<SteveAiCollectors.DetailedEntry> detailedEntities = new java.util.ArrayList<>();
    private static List<SteveAiCollectors.DetailedEntry> detailedBlockEntities = new java.util.ArrayList<>();

    private static int lastScanChunkRadius = 0;
    private static String lastScanType = "";
    private static BlockPos lastScanCenter = null;
    private static long lastScanGameTime = 0L;

    private static int lastDetailRadius = 0;
    private static BlockPos lastDetailCenter = null;
    private static long lastDetailGameTime = 0L;
    private static int lastFastDetailedChunkCount = 0;
    private static int lastFastQuickChunkCount = 0;

    private static final Map<Long, ChunkScanResult> chunkScanResults = new LinkedHashMap<>();

    public static final class ChunkScanResult {
        public final BlockPos requestedPos;
        public final int chunkX;
        public final int chunkZ;
        public final boolean chunkWasLoaded;
        public final long scanGameTime;
        public final Map<String, SteveAiCollectors.SeenSummary> blocks;
        public final Map<String, SteveAiCollectors.SeenSummary> entities;
        public final Map<String, SteveAiCollectors.SeenSummary> blockEntities;

        public ChunkScanResult(
            BlockPos requestedPos,
            int chunkX,
            int chunkZ,
            boolean chunkWasLoaded,
            long scanGameTime,
            Map<String, SteveAiCollectors.SeenSummary> blocks,
            Map<String, SteveAiCollectors.SeenSummary> entities,
            Map<String, SteveAiCollectors.SeenSummary> blockEntities
        ) {
            this.requestedPos = requestedPos == null ? null : requestedPos.immutable();
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkWasLoaded = chunkWasLoaded;
            this.scanGameTime = scanGameTime;
            this.blocks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(blocks == null ? Map.of() : blocks));
            this.entities = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entities == null ? Map.of() : entities));
            this.blockEntities = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(blockEntities == null ? Map.of() : blockEntities));
        }
    }

    public static class ScanFilterTargets {
        public final java.util.Set<String> blockIds = new java.util.LinkedHashSet<>();
        public final java.util.Set<String> entityIds = new java.util.LinkedHashSet<>();
        public final java.util.Set<String> blockEntityIds = new java.util.LinkedHashSet<>();
        public final java.util.List<String> unresolved = new java.util.ArrayList<>();

        public boolean isEmpty() {
            return blockIds.isEmpty() && entityIds.isEmpty() && blockEntityIds.isEmpty();
        }
    }

    public static void clear() {
        scannedBlocks.clear();
        scannedEntities.clear();
        scannedBlockEntities.clear();
        detailedBlocks.clear();
        detailedEntities.clear();
        detailedBlockEntities.clear();
        lastScanChunkRadius = 0;
        lastScanType = "";
        lastScanCenter = null;
        lastScanGameTime = 0L;
        lastDetailRadius = 0;
        lastDetailCenter = null;
        lastDetailGameTime = 0L;
        lastFastDetailedChunkCount = 0;
        lastFastQuickChunkCount = 0;
        chunkScanResults.clear();
    }

    private static void clearScannedMapsOnly() {
        scannedBlocks.clear();
        scannedEntities.clear();
        scannedBlockEntities.clear();
    }

    private static void clearDetailOnly() {
        detailedBlocks.clear();
        detailedEntities.clear();
        detailedBlockEntities.clear();
    }

    public static void scanSAI(ServerLevel serverLevel, Entity steveAiEntity, String rawInput, int chunkRadius) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (steveAiEntity == null) {
            throw new IllegalArgumentException("steveAiEntity is null");
        }
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("scan input is blank");
        }
        if (chunkRadius < 1) {
            throw new IllegalArgumentException("chunkRadius must be >= 1");
        }

        int blockRadius = chunkRadius * 16;
        String trimmedInput = rawInput.trim();

        lastScanChunkRadius = chunkRadius;
        lastScanCenter = steveAiEntity.blockPosition().immutable();
        lastScanGameTime = serverLevel.getGameTime();
        lastFastDetailedChunkCount = 0;
        lastFastQuickChunkCount = 0;

        clearScannedMapsOnly();

        if (looksLikeTargetList(trimmedInput)) {
            lastScanType = "filtered:" + trimmedInput.toLowerCase(java.util.Locale.ROOT);
            runFilteredScan(serverLevel, steveAiEntity, trimmedInput, blockRadius);
        } else {
            lastScanType = trimmedInput.toLowerCase(java.util.Locale.ROOT);
            runLegacyScan(serverLevel, steveAiEntity, trimmedInput, blockRadius);
        }

        SteveAiCollectors.annotateDistanceFromCenter(scannedBlocks, lastScanCenter);
        SteveAiCollectors.annotateDistanceFromCenter(scannedEntities, lastScanCenter);
        SteveAiCollectors.annotateDistanceFromCenter(scannedBlockEntities, lastScanCenter);
    }

    public static void detailSAI(ServerLevel serverLevel, BlockPos center, int radiusBlocks) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (center == null) {
            throw new IllegalArgumentException("detail center is null");
        }
        if (radiusBlocks < 1) {
            throw new IllegalArgumentException("detail radius must be >= 1");
        }

        clearDetailOnly();

        detailedBlocks = new java.util.ArrayList<>(
            SteveAiCollectors.collectDetailedBlocksAt(serverLevel, center, radiusBlocks)
        );
        detailedEntities = new java.util.ArrayList<>(
            SteveAiCollectors.collectDetailedEntitiesAt(serverLevel, center, radiusBlocks)
        );
        detailedBlockEntities = new java.util.ArrayList<>(
            SteveAiCollectors.collectDetailedBlockEntitiesAt(serverLevel, center, radiusBlocks)
        );

        lastDetailCenter = center.immutable();
        lastDetailRadius = radiusBlocks;
        lastDetailGameTime = serverLevel.getGameTime();
    }

    public static ChunkScanResult scanSAI2(ServerLevel serverLevel, BlockPos blockPos, boolean useCache) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (blockPos == null) {
            throw new IllegalArgumentException("blockPos is null");
        }

        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;

        if (useCache) {
            ChunkScanResult cached = chunkScanResults.get(chunkKey(chunkX, chunkZ));
            if (cached != null) {
                return cached;
            }
        }

        boolean chunkWasLoaded = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        serverLevel.getChunk(chunkX, chunkZ);

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int yMin = serverLevel.getMinY();
        int yMax = serverLevel.getMaxY();
        int yCenter = (yMin + yMax) / 2;
        int yRadius = Math.max(yCenter - yMin, yMax - yCenter);

        Map<String, SteveAiCollectors.SeenSummary> blocks = SteveAiCollectors.collectBlocksInTile(
            serverLevel,
            minX,
            minZ,
            maxX,
            maxZ,
            yMin,
            yMax,
            null
        );

        Map<String, SteveAiCollectors.SeenSummary> entities = SteveAiCollectors.collectEntitiesInTile(
            serverLevel,
            minX,
            minZ,
            maxX,
            maxZ,
            yCenter,
            yRadius
        );

        Map<String, SteveAiCollectors.SeenSummary> blockEntities = SteveAiCollectors.collectBlockEntitiesInTile(
            serverLevel,
            minX,
            minZ,
            maxX,
            maxZ,
            yMin,
            yMax
        );

        long scanGameTime = serverLevel.getGameTime();
        ChunkScanResult result = new ChunkScanResult(
            blockPos,
            chunkX,
            chunkZ,
            chunkWasLoaded,
            scanGameTime,
            blocks,
            entities,
            blockEntities
        );

        scannedBlocks = new LinkedHashMap<>(blocks);
        scannedEntities = new LinkedHashMap<>(entities);
        scannedBlockEntities = new LinkedHashMap<>(blockEntities);
        lastScanType = "chunk";
        lastScanChunkRadius = 1;
        lastScanCenter = blockPos.immutable();
        lastScanGameTime = scanGameTime;

        SteveAiCollectors.annotateDistanceFromCenter(scannedBlocks, lastScanCenter);
        SteveAiCollectors.annotateDistanceFromCenter(scannedEntities, lastScanCenter);
        SteveAiCollectors.annotateDistanceFromCenter(scannedBlockEntities, lastScanCenter);

        chunkScanResults.put(chunkKey(chunkX, chunkZ), result);
        return result;
    }

    private static void validateScanRadius(int chunkRadius, boolean forceLoad) {
        if (chunkRadius < 1) {
            throw new IllegalArgumentException("chunkRadius must be >= 1");
        }
        if (forceLoad && chunkRadius > FORCE_LOAD_MAX_CHUNK_RADIUS) {
            throw new IllegalArgumentException(
                "ForceLoad supports max chunkRadius=" + FORCE_LOAD_MAX_CHUNK_RADIUS
            );
        }
    }

    private static void forceLoadChunks(ServerLevel serverLevel, BlockPos center, int chunkRadius) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                serverLevel.getChunk(chunkX, chunkZ);
            }
        }
    }

    public static Map<String, SteveAiCollectors.SeenSummary> scanE(
        ServerLevel serverLevel,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (center == null) {
            throw new IllegalArgumentException("center is null");
        }

        validateScanRadius(chunkRadius, forceLoad);
        if (forceLoad) {
            forceLoadChunks(serverLevel, center, chunkRadius);
        }

        int blockRadius = chunkRadius * 16;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            center.getX() - blockRadius,
            center.getY() - blockRadius,
            center.getZ() - blockRadius,
            center.getX() + blockRadius + 1,
            center.getY() + blockRadius + 1,
            center.getZ() + blockRadius + 1
        );

        Map<String, SteveAiCollectors.SeenSummary> grouped = new LinkedHashMap<>();
        var entities = serverLevel.getEntities((Entity) null, box, e -> e != null && e.isAlive());
        for (Entity nearby : entities) {
            String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(nearby.getType()).toString();
            BlockPos pos = nearby.blockPosition();
            SteveAiCollectors.SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SteveAiCollectors.SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
            } else {
                summary.addLocation(pos);
            }
        }

        SteveAiCollectors.annotateDistanceFromCenter(grouped, center);
        return grouped;
    }

    public static Map<String, SteveAiCollectors.SeenSummary> scanBE(
        ServerLevel serverLevel,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (center == null) {
            throw new IllegalArgumentException("center is null");
        }

        validateScanRadius(chunkRadius, forceLoad);
        if (forceLoad) {
            forceLoadChunks(serverLevel, center, chunkRadius);
        }

        int blockRadius = chunkRadius * 16;
        Map<String, SteveAiCollectors.SeenSummary> grouped = new LinkedHashMap<>();
        BlockPos min = center.offset(-blockRadius, -blockRadius, -blockRadius);
        BlockPos max = center.offset(blockRadius, blockRadius, blockRadius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            net.minecraft.world.level.block.entity.BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be == null) {
                continue;
            }

            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (key == null) {
                continue;
            }

            String typeName = key.toString();
            SteveAiCollectors.SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SteveAiCollectors.SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
            } else {
                summary.addLocation(pos.immutable());
            }
        }

        SteveAiCollectors.annotateDistanceFromCenter(grouped, center);
        return grouped;
    }

    private static Map<String, SteveAiCollectors.SeenSummary> scanEInTile(
        ServerLevel serverLevel,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int yCenter,
        int yRadius
    ) {
        return SteveAiCollectors.collectEntitiesInTile(serverLevel, minX, minZ, maxX, maxZ, yCenter, yRadius);
    }

    private static Map<String, SteveAiCollectors.SeenSummary> scanBEInTile(
        ServerLevel serverLevel,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int yMin,
        int yMax
    ) {
        return SteveAiCollectors.collectBlockEntitiesInTile(serverLevel, minX, minZ, maxX, maxZ, yMin, yMax);
    }

    public static Map<String, SteveAiCollectors.SeenSummary> scanB(
        ServerLevel serverLevel,
        BlockPos center,
        int chunkRadius,
        boolean forceLoad
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (center == null) {
            throw new IllegalArgumentException("center is null");
        }

        validateScanRadius(chunkRadius, forceLoad);
        if (forceLoad) {
            forceLoadChunks(serverLevel, center, chunkRadius);
        }

        int horizontalRadius = chunkRadius * 16;
        int verticalRadius = chunkRadius * 16;
    int centerChunkX = center.getX() >> 4;
    int centerChunkZ = center.getZ() >> 4;
        Map<String, SteveAiCollectors.SeenSummary> grouped = new LinkedHashMap<>();

        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    SteveAiCollectors.SeenSummary summary = grouped.get(blockName);
                    if (summary == null) {
                        grouped.put(blockName, new SteveAiCollectors.SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
                    } else {
                        summary.increment();
                        int posChunkX = pos.getX() >> 4;
                        int posChunkZ = pos.getZ() >> 4;
                        boolean isInCenterChunk = posChunkX == centerChunkX && posChunkZ == centerChunkZ;
                        if (isInCenterChunk && summary.allLocations.size() < MAX_STORED_BLOCK_LOCATIONS_PER_TYPE) {
                            summary.allLocations.add(pos.immutable());
                        }
                    }
                }
            }
        }

        SteveAiCollectors.annotateDistanceFromCenter(grouped, center);

        return grouped;
    }

    public static void scanSAIFast(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        BlockPos playerPos,
        String rawScanInput,
        int chunkRadius
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (steveAiEntity == null) {
            throw new IllegalArgumentException("steveAiEntity is null");
        }
        if (playerPos == null) {
            throw new IllegalArgumentException("playerPos is null");
        }
        if (rawScanInput == null || rawScanInput.isBlank()) {
            throw new IllegalArgumentException("scan input is blank");
        }
        if (chunkRadius < 1) {
            throw new IllegalArgumentException("chunkRadius must be >= 1");
        }

        String normalized = rawScanInput.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("all")) {
            throw new IllegalArgumentException("scanSAI fast currently supports only 'all'.");
        }

        BlockPos stevePos = steveAiEntity.blockPosition();
        int centerChunkX = stevePos.getX() >> 4;
        int centerChunkZ = stevePos.getZ() >> 4;
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        java.util.Set<Long> detailedChunks = new java.util.LinkedHashSet<>();
        detailedChunks.add(chunkKey(centerChunkX, centerChunkZ));
        detailedChunks.add(chunkKey(playerChunkX, playerChunkZ));

        Map<String, SteveAiCollectors.SeenSummary> groupedBlocks = new LinkedHashMap<>();
        Map<String, SteveAiCollectors.SeenSummary> groupedEntities = new LinkedHashMap<>();
        Map<String, SteveAiCollectors.SeenSummary> groupedBlockEntities = new LinkedHashMap<>();
        java.util.Set<Long> processedDetailedChunks = new java.util.HashSet<>();

        int yMin = serverLevel.getMinY();
        int yMax = serverLevel.getMaxY();
        int yCenter = (yMin + yMax) / 2;
        int yRadius = Math.max(yCenter - yMin, yMax - yCenter);
        int quickChunkCount = 0;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                long key = chunkKey(chunkX, chunkZ);
                if (detailedChunks.contains(key)) {
                    ChunkScanResult detail = scanSAI2(
                        serverLevel,
                        new BlockPos((chunkX << 4) + 8, stevePos.getY(), (chunkZ << 4) + 8),
                        false
                    );
                    SteveAiCollectors.mergeInto(groupedBlocks, detail.blocks);
                    SteveAiCollectors.mergeInto(groupedEntities, detail.entities);
                    SteveAiCollectors.mergeInto(groupedBlockEntities, detail.blockEntities);
                    processedDetailedChunks.add(key);
                    continue;
                }

                if (serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    continue;
                }

                int minX = chunkX << 4;
                int minZ = chunkZ << 4;
                int maxX = minX + 15;
                int maxZ = minZ + 15;

                SteveAiCollectors.mergeInto(
                    groupedEntities,
                    scanEInTile(serverLevel, minX, minZ, maxX, maxZ, yCenter, yRadius)
                );
                SteveAiCollectors.mergeInto(
                    groupedBlockEntities,
                    scanBEInTile(serverLevel, minX, minZ, maxX, maxZ, yMin, yMax)
                );
                quickChunkCount++;
            }
        }

        for (Long key : detailedChunks) {
            if (processedDetailedChunks.contains(key)) {
                continue;
            }

            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            ChunkScanResult detail = scanSAI2(
                serverLevel,
                new BlockPos((chunkX << 4) + 8, stevePos.getY(), (chunkZ << 4) + 8),
                false
            );
            SteveAiCollectors.mergeInto(groupedBlocks, detail.blocks);
            SteveAiCollectors.mergeInto(groupedEntities, detail.entities);
            SteveAiCollectors.mergeInto(groupedBlockEntities, detail.blockEntities);
        }

        replaceScanResults(
            "all_fast",
            chunkRadius,
            stevePos,
            serverLevel.getGameTime(),
            groupedBlocks,
            groupedEntities,
            groupedBlockEntities
        );
        SteveAiCollectors.annotateDistanceFromCenter(scannedBlocks, stevePos);
        SteveAiCollectors.annotateDistanceFromCenter(scannedEntities, stevePos);
        SteveAiCollectors.annotateDistanceFromCenter(scannedBlockEntities, stevePos);
        lastFastDetailedChunkCount = detailedChunks.size();
        lastFastQuickChunkCount = quickChunkCount;
    }

    private static boolean looksLikeTargetList(String rawInput) {
        if (rawInput == null) {
            return false;
        }

        String s = rawInput.trim();
        if (s.isEmpty()) {
            return false;
        }

        if (s.startsWith("[") && s.endsWith("]")) {
            return true;
        }

        String lowered = s.toLowerCase(java.util.Locale.ROOT);
        return !lowered.equals("blocks")
            && !lowered.equals("entities")
            && !lowered.equals("blockentities")
            && !lowered.equals("all");
    }

    private static void runLegacyScan(ServerLevel serverLevel, Entity steveAiEntity, String scanType, int blockRadius) {
        String normalized = scanType.toLowerCase(java.util.Locale.ROOT).trim();
        BlockPos center = steveAiEntity.blockPosition();
        int chunkRadius = Math.max(1, blockRadius / 16);

        switch (normalized) {
            case "blocks" -> scannedBlocks = scanB(serverLevel, center, chunkRadius, false);

            case "entities" -> scannedEntities = scanE(serverLevel, center, chunkRadius, false);

            case "blockentities" -> scannedBlockEntities = scanBE(serverLevel, center, chunkRadius, false);

            case "all" -> {
                scannedBlocks = scanB(serverLevel, center, chunkRadius, false);
                scannedEntities = scanE(serverLevel, center, chunkRadius, false);
                scannedBlockEntities = scanBE(serverLevel, center, chunkRadius, false);
            }

            default -> throw new IllegalArgumentException(
                "Unknown scan type: " + scanType + " (use blocks, entities, blockentities, all, or [villager bell])"
            );
        }
    }

    private static void runFilteredScan(ServerLevel serverLevel, Entity steveAiEntity, String rawInput, int blockRadius) {
        java.util.List<String> tokens = parseTargetTokens(rawInput);
        ScanFilterTargets targets = resolveFilterTargets(tokens);

        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                "No valid scan targets found in: " + rawInput
                    + (targets.unresolved.isEmpty() ? "" : " unresolved=" + targets.unresolved)
            );
        }

        if (!targets.blockIds.isEmpty()) {
            scannedBlocks = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyBlocksFiltered(
                    serverLevel,
                    steveAiEntity,
                    blockRadius,
                    blockRadius,
                    targets.blockIds
                )
            );
        }

        if (!targets.entityIds.isEmpty()) {
            scannedEntities = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyEntitiesFiltered(
                    serverLevel,
                    steveAiEntity,
                    blockRadius,
                    targets.entityIds
                )
            );
        }

        if (!targets.blockEntityIds.isEmpty()) {
            scannedBlockEntities = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyBlockEntitiesFiltered(
                    serverLevel,
                    steveAiEntity,
                    blockRadius,
                    targets.blockEntityIds
                )
            );
        }
    }

    private static java.util.List<String> parseTargetTokens(String rawInput) {
        if (rawInput == null) {
            return java.util.List.of();
        }

        String s = rawInput.trim();
        if (s.isEmpty()) {
            return java.util.List.of();
        }

        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        if (s.isEmpty()) {
            return java.util.List.of();
        }

        String[] rawTokens = s.split("\\s+");
        java.util.List<String> out = new java.util.ArrayList<>();

        for (String token : rawTokens) {
            if (token == null) {
                continue;
            }

            String cleaned = token.trim();

            while (cleaned.startsWith(",")) {
                cleaned = cleaned.substring(1).trim();
            }
            while (cleaned.endsWith(",")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
            }

            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }

        return out;
    }

    private static ScanFilterTargets resolveFilterTargets(java.util.List<String> tokens) {
        ScanFilterTargets result = new ScanFilterTargets();

        for (String raw : tokens) {
            String token = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }

            String resolved = resolveAlias(token);
            String fullId = resolved.contains(":") ? resolved : "minecraft:" + resolved;
            Identifier rl = Identifier.tryParse(fullId);

            if (rl == null) {
                result.unresolved.add(raw);
                continue;
            }

            if (BuiltInRegistries.BLOCK.containsKey(rl)) {
                result.blockIds.add(rl.toString());
                continue;
            }

            if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                result.entityIds.add(rl.toString());
                continue;
            }

            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(rl)) {
                result.blockEntityIds.add(rl.toString());
                continue;
            }

            result.unresolved.add(raw);
        }

        return result;
    }

    private static String resolveAlias(String token) {
        return switch (token) {
            case "villagers" -> "villager";
            case "bells" -> "bell";
            case "beds" -> "red_bed";
            default -> token;
        };
    }

    private static String mapToClusteredText(Map<String, SteveAiCollectors.SeenSummary> map, String title) {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append("\n");
        sb.append("count=").append(map.size()).append("\n\n");

        for (Map.Entry<String, SteveAiCollectors.SeenSummary> entry : map.entrySet()) {
            String blockName = entry.getKey();
            SteveAiCollectors.SeenSummary summary = entry.getValue();

            if (summary == null) {
                continue;
            }

            if (!summary.storesAllLocations() || summary.allLocations.isEmpty()) {
                sb.append(blockName)
                    .append(" -> ")
                    .append(summary)
                    .append("\n");
                continue;
            }

            java.util.List<java.util.Set<BlockPos>> clusters = splitIntoTouchingClusters(summary.allLocations);

            sb.append(blockName)
                .append(" -> clusterCount=")
                .append(clusters.size())
                .append(", clusters=(");

            for (int i = 0; i < clusters.size(); i++) {
                java.util.Set<BlockPos> cluster = clusters.get(i);
                BlockPos first = cluster.iterator().next();

                if (i > 0) {
                    sb.append("; ");
                }

                sb.append("firstLoc=(")
                    .append(first.getX()).append(",")
                    .append(first.getY()).append(",")
                    .append(first.getZ()).append("), count=")
                    .append(cluster.size());
            }

            sb.append(")\n");
        }

        return sb.toString();
    }

    private static String detailListToText(List<SteveAiCollectors.DetailedEntry> entries, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        sb.append("count=").append(entries.size()).append("\n\n");

        for (SteveAiCollectors.DetailedEntry entry : entries) {
            sb.append(entry.typeName)
                .append(" -> loc=(")
                .append(entry.pos.getX()).append(",")
                .append(entry.pos.getY()).append(",")
                .append(entry.pos.getZ()).append(")")
                .append("\n");
        }

        return sb.toString();
    }

    private static java.util.List<java.util.Set<BlockPos>> splitIntoTouchingClusters(java.util.List<BlockPos> positions) {
        java.util.List<java.util.Set<BlockPos>> clusters = new java.util.ArrayList<>();
        java.util.Set<BlockPos> unvisited = new java.util.HashSet<>();

        for (BlockPos pos : positions) {
            unvisited.add(pos.immutable());
        }

        while (!unvisited.isEmpty()) {
            BlockPos start = unvisited.iterator().next();

            java.util.Set<BlockPos> cluster = new java.util.LinkedHashSet<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

            queue.add(start);
            unvisited.remove(start);

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                cluster.add(current);

                for (BlockPos neighbor : getTouchingNeighbors(current)) {
                    if (unvisited.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private static java.util.List<BlockPos> getTouchingNeighbors(BlockPos pos) {
        return java.util.List.of(
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.above(),
            pos.below()
        );
    }

    public static int updatePoiMapFromCurrentScan() {
        return PoiManager.ingestScanSummaries(scannedBlocks, scannedEntities, scannedBlockEntities);
    }

    public static int updatePoiMapFromCurrentScanFast() {
        return PoiManager.ingestFastScanSummaries(scannedEntities, scannedBlockEntities);
    }

    public static void replaceScanResults(
        String scanType,
        int chunkRadius,
        BlockPos center,
        long gameTime,
        Map<String, SteveAiCollectors.SeenSummary> blocks,
        Map<String, SteveAiCollectors.SeenSummary> entities,
        Map<String, SteveAiCollectors.SeenSummary> blockEntities
    ) {
        lastScanType = scanType == null ? "" : scanType;
        lastScanChunkRadius = Math.max(chunkRadius, 0);
        lastScanCenter = center == null ? null : center.immutable();
        lastScanGameTime = gameTime;
        scannedBlocks = new LinkedHashMap<>(blocks == null ? Map.of() : blocks);
        scannedEntities = new LinkedHashMap<>(entities == null ? Map.of() : entities);
        scannedBlockEntities = new LinkedHashMap<>(blockEntities == null ? Map.of() : blockEntities);
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedBlocks() {
        return scannedBlocks;
    }

    public static ChunkScanResult getChunkScanResult(int chunkX, int chunkZ) {
        return chunkScanResults.get(chunkKey(chunkX, chunkZ));
    }

    public static Map<Long, ChunkScanResult> getChunkScanResults() {
        return java.util.Collections.unmodifiableMap(chunkScanResults);
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedEntities() {
        return scannedEntities;
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedBlockEntities() {
        return scannedBlockEntities;
    }

    public static List<SteveAiCollectors.DetailedEntry> getDetailedBlocks() {
        return detailedBlocks;
    }

    public static List<SteveAiCollectors.DetailedEntry> getDetailedEntities() {
        return detailedEntities;
    }

    public static List<SteveAiCollectors.DetailedEntry> getDetailedBlockEntities() {
        return detailedBlockEntities;
    }

    public static int getLastScanChunkRadius() {
        return lastScanChunkRadius;
    }

    public static String getLastScanType() {
        return lastScanType;
    }

    public static BlockPos getLastScanCenter() {
        return lastScanCenter;
    }

    public static long getLastScanGameTime() {
        return lastScanGameTime;
    }

    public static int getLastDetailRadius() {
        return lastDetailRadius;
    }

    public static BlockPos getLastDetailCenter() {
        return lastDetailCenter;
    }

    public static long getLastDetailGameTime() {
        return lastDetailGameTime;
    }

    public static int getLastFastDetailedChunkCount() {
        return lastFastDetailedChunkCount;
    }

    public static int getLastFastQuickChunkCount() {
        return lastFastQuickChunkCount;
    }

    public static String getStatusText() {
        String centerText = (lastScanCenter == null)
            ? "null"
            : lastScanCenter.getX() + ", " + lastScanCenter.getY() + ", " + lastScanCenter.getZ();

        String detailCenterText = (lastDetailCenter == null)
            ? "null"
            : lastDetailCenter.getX() + ", " + lastDetailCenter.getY() + ", " + lastDetailCenter.getZ();

        return "SteveAI Scan Status\n"
            + "lastScanType=" + blankIfNeeded(lastScanType) + "\n"
            + "lastScanChunkRadius=" + lastScanChunkRadius + "\n"
            + "lastScanCenter=" + centerText + "\n"
            + "lastScanGameTime=" + lastScanGameTime + "\n"
            + "lastFastDetailedChunkCount=" + lastFastDetailedChunkCount + "\n"
            + "lastFastQuickChunkCount=" + lastFastQuickChunkCount + "\n"
            + "scannedBlocks.count=" + scannedBlocks.size() + "\n"
            + "scannedEntities.count=" + scannedEntities.size() + "\n"
            + "scannedBlockEntities.count=" + scannedBlockEntities.size() + "\n"
            + "lastDetailRadius=" + lastDetailRadius + "\n"
            + "lastDetailCenter=" + detailCenterText + "\n"
            + "lastDetailGameTime=" + lastDetailGameTime + "\n"
            + "detailedBlocks.count=" + detailedBlocks.size() + "\n"
            + "detailedEntities.count=" + detailedEntities.size() + "\n"
            + "detailedBlockEntities.count=" + detailedBlockEntities.size();
    }

    public static Path writeTextFiles(ServerLevel serverLevel, String suffix) throws IOException {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }

        String safeSuffix = sanitizeSuffix(suffix);

        Path baseFolder = SteveAiContextFiles.getSteveAiDataDir(serverLevel);

        Path statusFile = baseFolder.resolve(buildFileName("scanStatus", safeSuffix));
        Path blocksFile = baseFolder.resolve(buildFileName("scannedBlocks", safeSuffix));
        Path blocksClusteredFile = baseFolder.resolve(buildFileName("scannedBlocksClustered", safeSuffix));
        Path entitiesFile = baseFolder.resolve(buildFileName("scannedEntities", safeSuffix));
        Path blockEntitiesFile = baseFolder.resolve(buildFileName("scannedBlockEntities", safeSuffix));
        Path poiSummaryFile = baseFolder.resolve(buildFileName("poiSummary", safeSuffix));

        Files.writeString(statusFile, getStatusText() + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(blocksFile, mapToText(scannedBlocks, "SCANNED BLOCKS"), StandardCharsets.UTF_8);
        Files.writeString(blocksClusteredFile, mapToClusteredText(scannedBlocks, "SCANNED BLOCKS CLUSTERED"), StandardCharsets.UTF_8);
        Files.writeString(entitiesFile, mapToText(scannedEntities, "SCANNED ENTITIES"), StandardCharsets.UTF_8);
        Files.writeString(blockEntitiesFile, mapToText(scannedBlockEntities, "SCANNED BLOCK ENTITIES"), StandardCharsets.UTF_8);

        StringBuilder poiText = new StringBuilder();
        poiText.append("POI SUMMARY").append("\n");

        java.util.List<String> poiLines = PoiManager.buildSummaryLines();
        poiText.append("count=").append(poiLines.size()).append("\n\n");

        for (String line : poiLines) {
            poiText.append(line).append("\n");
        }

        Files.writeString(poiSummaryFile, poiText.toString(), StandardCharsets.UTF_8);

        return baseFolder;
    }

    public static Path writeDetailTextFiles(ServerLevel serverLevel, String suffix) throws IOException {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }

        String safeSuffix = sanitizeSuffix(suffix);

        Path baseFolder = SteveAiContextFiles.getSteveAiDataDir(serverLevel);

        Path detailStatusFile = baseFolder.resolve(buildFileName("detailStatus", safeSuffix));
        Path detailBlocksFile = baseFolder.resolve(buildFileName("detailBlocks", safeSuffix));
        Path detailEntitiesFile = baseFolder.resolve(buildFileName("detailEntities", safeSuffix));
        Path detailBlockEntitiesFile = baseFolder.resolve(buildFileName("detailBlockEntities", safeSuffix));

        String detailStatus =
            "STEVEAI DETAIL STATUS\n"
                + "lastDetailRadius=" + lastDetailRadius + "\n"
                + "lastDetailCenter=" + (lastDetailCenter == null
                    ? "null"
                    : lastDetailCenter.getX() + ", " + lastDetailCenter.getY() + ", " + lastDetailCenter.getZ()) + "\n"
                + "lastDetailGameTime=" + lastDetailGameTime + "\n"
                + "detailedBlocks.count=" + detailedBlocks.size() + "\n"
                + "detailedEntities.count=" + detailedEntities.size() + "\n"
                + "detailedBlockEntities.count=" + detailedBlockEntities.size() + "\n";

        Files.writeString(detailStatusFile, detailStatus, StandardCharsets.UTF_8);
        Files.writeString(detailBlocksFile, detailListToText(detailedBlocks, "DETAIL BLOCKS"), StandardCharsets.UTF_8);
        Files.writeString(detailEntitiesFile, detailListToText(detailedEntities, "DETAIL ENTITIES"), StandardCharsets.UTF_8);
        Files.writeString(detailBlockEntitiesFile, detailListToText(detailedBlockEntities, "DETAIL BLOCK ENTITIES"), StandardCharsets.UTF_8);

        return baseFolder;
    }

    private static String mapToText(Map<String, SteveAiCollectors.SeenSummary> map, String title) {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append("\n");
        sb.append("count=").append(map.size()).append("\n\n");

        for (Map.Entry<String, SteveAiCollectors.SeenSummary> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    private static String buildFileName(String baseName, String safeSuffix) {
        if (safeSuffix.isEmpty()) {
            return baseName + ".txt";
        }
        return baseName + "_" + safeSuffix + ".txt";
    }

    private static String sanitizeSuffix(String suffix) {
        if (suffix == null) {
            return "";
        }

        String s = suffix.trim();
        if (s.isEmpty()) {
            return "";
        }

        s = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        s = s.replaceAll("_+", "_");

        if (s.length() > 50) {
            s = s.substring(0, 50);
        }

        return s;
    }

    private static String blankIfNeeded(String value) {
        return (value == null || value.isBlank()) ? "(blank)" : value;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
