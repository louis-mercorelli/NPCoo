package com.example.examplemod;

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

        clearScannedMapsOnly();

        if (looksLikeTargetList(trimmedInput)) {
            lastScanType = "filtered:" + trimmedInput.toLowerCase(java.util.Locale.ROOT);
            runFilteredScan(serverLevel, steveAiEntity, trimmedInput, blockRadius);
        } else {
            lastScanType = trimmedInput.toLowerCase(java.util.Locale.ROOT);
            runLegacyScan(serverLevel, steveAiEntity, trimmedInput, blockRadius);
        }
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

        chunkScanResults.put(chunkKey(chunkX, chunkZ), result);
        return result;
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

        switch (normalized) {
            case "blocks" -> scannedBlocks = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAiEntity, blockRadius, blockRadius)
            );

            case "entities" -> scannedEntities = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyEntities(serverLevel, steveAiEntity, blockRadius)
            );

            case "blockentities" -> scannedBlockEntities = new LinkedHashMap<>(
                SteveAiCollectors.collectNearbyBlockEntities(serverLevel, steveAiEntity, blockRadius)
            );

            case "all" -> {
                scannedBlocks = new LinkedHashMap<>(
                    SteveAiCollectors.collectNearbyBlocks(serverLevel, steveAiEntity, blockRadius, blockRadius)
                );
                scannedEntities = new LinkedHashMap<>(
                    SteveAiCollectors.collectNearbyEntities(serverLevel, steveAiEntity, blockRadius)
                );
                scannedBlockEntities = new LinkedHashMap<>(
                    SteveAiCollectors.collectNearbyBlockEntities(serverLevel, steveAiEntity, blockRadius)
                );
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