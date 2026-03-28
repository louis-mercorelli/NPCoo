package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public class SteveAiCollectors {

    public static class SeenSummary {
        public final int x;
        public final int y;
        public final int z;
        public int count;

        public SeenSummary(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.count = 1;
        }

        public void increment() {
            this.count++;
        }
    }

    public static Map<String, SeenSummary> collectNearbyEntities(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        double radius,
        Predicate<Entity> filter
    ) {
        var nearbyEntities = serverLevel.getEntities(
            (Entity) null,
            steveAiEntity.getBoundingBox().inflate(radius),
            other -> other != null && other.isAlive() && other != steveAiEntity
        );

        Map<String, SeenSummary> grouped = new LinkedHashMap<>();

        for (Entity nearby : nearbyEntities) {
            if (filter != null && !filter.test(nearby)) {
                continue;
            }

            String typeName = nearby.getType().toString();
            BlockPos pos = nearby.blockPosition();

            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ()));
            } else {
                summary.increment();
            }
        }

        return grouped;
    }

    public static Map<String, SeenSummary> collectNearbyEntities(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        double radius
    ) {
        return collectNearbyEntities(serverLevel, steveAiEntity, radius, null);
    }

    public static Map<String, SeenSummary> collectNearbyBlocks(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int horizontalRadius,
        int verticalRadius,
        Predicate<BlockState> filter
    ) {
        BlockPos center = steveAiEntity.blockPosition();
        Map<String, SeenSummary> grouped = new LinkedHashMap<>();

        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    if (filter != null && !filter.test(state)) {
                        continue;
                    }

                    Block block = state.getBlock();
                    String blockName = block.toString();

                    SeenSummary summary = grouped.get(blockName);
                    if (summary == null) {
                        grouped.put(blockName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ()));
                    } else {
                        summary.increment();
                    }
                }
            }
        }

        return grouped;
    }

    public static Map<String, SeenSummary> collectNearbyBlocks(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int horizontalRadius,
        int verticalRadius
    ) {
        return collectNearbyBlocks(serverLevel, steveAiEntity, horizontalRadius, verticalRadius, null);
    }

    public static Map<String, SeenSummary> collectNearbyBlockEntities(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int radiusBlocks
    ) {
        BlockPos center = steveAiEntity.blockPosition();
        Map<String, SeenSummary> grouped = new LinkedHashMap<>();

        BlockPos min = center.offset(-radiusBlocks, -radiusBlocks, -radiusBlocks);
        BlockPos max = center.offset(radiusBlocks, radiusBlocks, radiusBlocks);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be == null) {
                continue;
            }

            String typeName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();

            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ()));
            } else {
                summary.increment();
            }
        }

        return grouped;
    }
}