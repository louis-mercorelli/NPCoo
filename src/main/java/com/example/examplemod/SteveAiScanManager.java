package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class SteveAiScanManager {

    private static Map<String, SteveAiCollectors.SeenSummary> scannedBlocks = new LinkedHashMap<>();
    private static Map<String, SteveAiCollectors.SeenSummary> scannedEntities = new LinkedHashMap<>();
    private static Map<String, SteveAiCollectors.SeenSummary> scannedBlockEntities = new LinkedHashMap<>();

    private static int lastScanChunkRadius = 0;
    private static String lastScanType = "";
    private static BlockPos lastScanCenter = null;
    private static long lastScanGameTime = 0L;

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
        lastScanChunkRadius = 0;
        lastScanType = "";
        lastScanCenter = null;
        lastScanGameTime = 0L;
    }

    private static void clearScannedMapsOnly() {
        scannedBlocks.clear();
        scannedEntities.clear();
        scannedBlockEntities.clear();
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

        rebuildPoisFromCurrentScan();
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

    private static void rebuildPoisFromCurrentScan() {
        PoiManager.clear();

        for (Map.Entry<String, SteveAiCollectors.SeenSummary> entry : scannedBlockEntities.entrySet()) {
            String typeName = entry.getKey();
            SteveAiCollectors.SeenSummary summary = entry.getValue();

            if (summary.allLocations != null && !summary.allLocations.isEmpty()) {
                for (BlockPos pos : summary.allLocations) {
                    PoiManager.processBlockEntity(typeName, pos);
                }
            } else {
                PoiManager.processBlockEntity(typeName, new BlockPos(summary.x, summary.y, summary.z));
            }
        }

        for (Map.Entry<String, SteveAiCollectors.SeenSummary> entry : scannedEntities.entrySet()) {
            String typeName = entry.getKey();
            SteveAiCollectors.SeenSummary summary = entry.getValue();

            if (summary.allLocations != null && !summary.allLocations.isEmpty()) {
                for (BlockPos pos : summary.allLocations) {
                    PoiManager.processEntity(typeName, pos);
                }
            } else {
                PoiManager.processEntity(typeName, new BlockPos(summary.x, summary.y, summary.z));
            }
        }
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedBlocks() {
        return scannedBlocks;
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedEntities() {
        return scannedEntities;
    }

    public static Map<String, SteveAiCollectors.SeenSummary> getScannedBlockEntities() {
        return scannedBlockEntities;
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

    public static String getStatusText() {
        String centerText = (lastScanCenter == null)
            ? "null"
            : lastScanCenter.getX() + ", " + lastScanCenter.getY() + ", " + lastScanCenter.getZ();

        return "SteveAI Scan Status\n"
            + "lastScanType=" + blankIfNeeded(lastScanType) + "\n"
            + "lastScanChunkRadius=" + lastScanChunkRadius + "\n"
            + "lastScanCenter=" + centerText + "\n"
            + "lastScanGameTime=" + lastScanGameTime + "\n"
            + "scannedBlocks.count=" + scannedBlocks.size() + "\n"
            + "scannedEntities.count=" + scannedEntities.size() + "\n"
            + "scannedBlockEntities.count=" + scannedBlockEntities.size();
    }

    public static Path writeTextFiles(ServerLevel serverLevel, String suffix) throws IOException {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }

        String safeSuffix = sanitizeSuffix(suffix);

        Path baseFolder = serverLevel.getServer()
            .getWorldPath(LevelResource.ROOT)
            .resolve("testmod")
            .resolve("player");

        Files.createDirectories(baseFolder);

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
}
