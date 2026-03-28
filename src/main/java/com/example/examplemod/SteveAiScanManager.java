package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
}
