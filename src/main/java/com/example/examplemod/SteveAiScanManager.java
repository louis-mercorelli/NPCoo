package com.example.examplemod;

import net.minecraft.core.BlockPos;
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

    public static void clear() {
        scannedBlocks.clear();
        scannedEntities.clear();
        scannedBlockEntities.clear();
        lastScanChunkRadius = 0;
        lastScanType = "";
        lastScanCenter = null;
        lastScanGameTime = 0L;
    }

    public static void scanSAI(ServerLevel serverLevel, Entity steveAiEntity, String scanType, int chunkRadius) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (steveAiEntity == null) {
            throw new IllegalArgumentException("steveAiEntity is null");
        }
        if (scanType == null || scanType.isBlank()) {
            throw new IllegalArgumentException("scanType is blank");
        }
        if (chunkRadius < 1) {
            throw new IllegalArgumentException("chunkRadius must be >= 1");
        }

        int blockRadius = chunkRadius * 16;

        lastScanChunkRadius = chunkRadius;
        lastScanType = scanType.toLowerCase();
        lastScanCenter = steveAiEntity.blockPosition().immutable();
        lastScanGameTime = serverLevel.getGameTime();

        switch (lastScanType) {
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
                "Unknown scan type: " + scanType + " (use blocks, entities, blockentities, all)"
            );
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
        Path entitiesFile = baseFolder.resolve(buildFileName("scannedEntities", safeSuffix));
        Path blockEntitiesFile = baseFolder.resolve(buildFileName("scannedBlockEntities", safeSuffix));
        Path poiSummaryFile = baseFolder.resolve(buildFileName("poiSummary", safeSuffix));

        Files.writeString(statusFile, getStatusText() + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(blocksFile, mapToText(scannedBlocks, "SCANNED BLOCKS"), StandardCharsets.UTF_8);
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
