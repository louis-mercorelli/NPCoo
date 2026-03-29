package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class SteveAiCollectors {

    public static class SeenSummary {
        public final int x;
        public final int y;
        public final int z;
        public int count;

        public final java.util.List<BlockPos> allLocations = new java.util.ArrayList<>();
        private final boolean storeAllLocations;

        public SeenSummary(int x, int y, int z) {
            this(x, y, z, false);
        }

        public SeenSummary(int x, int y, int z, boolean storeAllLocations) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.count = 1;
            this.storeAllLocations = storeAllLocations;

            if (storeAllLocations) {
                this.allLocations.add(new BlockPos(x, y, z));
            }
        }

        public void increment() {
            this.count++;
        }

        public void addLocation(BlockPos pos) {
            this.count++;
            if (storeAllLocations) {
                this.allLocations.add(pos.immutable());
            }
        }

        public boolean storesAllLocations() {
            return storeAllLocations;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("firstLoc=(").append(x).append(",").append(y).append(",").append(z).append(")");
            sb.append(", count=").append(count);

            if (storeAllLocations && !allLocations.isEmpty()) {
                sb.append(", allLocs=(");
                for (int i = 0; i < allLocations.size(); i++) {
                    BlockPos p = allLocations.get(i);
                    if (i > 0) {
                        sb.append("; ");
                    }
                    sb.append("(")
                        .append(p.getX()).append(",")
                        .append(p.getY()).append(",")
                        .append(p.getZ()).append(")");
                }
                sb.append(")");
            }

            return sb.toString();
        }
    }

    public static class DetailedEntry {
        public final String typeName;
        public final BlockPos pos;

        public DetailedEntry(String typeName, BlockPos pos) {
            this.typeName = typeName;
            this.pos = pos.immutable();
        }

        @Override
        public String toString() {
            return typeName + " @ (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
        }
    }

    private static boolean shouldStoreAllLocationsForBlock(String blockName) {
        return blockName.equals("minecraft:cartography_table")
            || blockName.equals("minecraft:bell")
            || blockName.equals("minecraft:lectern")
            || blockName.equals("minecraft:diamond_ore");
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

            String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(nearby.getType()).toString();
            BlockPos pos = nearby.blockPosition();

            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
            } else {
                summary.addLocation(pos);
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

    public static Map<String, SeenSummary> collectNearbyEntitiesFiltered(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        double radius,
        Set<String> targetEntityIds
    ) {
        if (targetEntityIds == null || targetEntityIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return collectNearbyEntities(
            serverLevel,
            steveAiEntity,
            radius,
            nearby -> {
                EntityType<?> type = nearby.getType();
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                return key != null && targetEntityIds.contains(key.toString());
            }
        );
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
                    String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();

                    SeenSummary summary = grouped.get(blockName);
                    if (summary == null) {
                        boolean storeAllLocations = shouldStoreAllLocationsForBlock(blockName);
                        grouped.put(blockName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ(), storeAllLocations));
                    } else {
                        if (summary.storesAllLocations()) {
                            summary.addLocation(pos);
                        } else {
                            summary.increment();
                        }
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

    public static Map<String, SeenSummary> collectNearbyBlocksFiltered(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int horizontalRadius,
        int verticalRadius,
        Set<String> targetBlockIds
    ) {
        if (targetBlockIds == null || targetBlockIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return collectNearbyBlocks(
            serverLevel,
            steveAiEntity,
            horizontalRadius,
            verticalRadius,
            state -> {
                Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                return key != null && targetBlockIds.contains(key.toString());
            }
        );
    }

    public static Map<String, SeenSummary> collectNearbyBlockEntities(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int radiusBlocks
    ) {
        return collectNearbyBlockEntities(serverLevel, steveAiEntity, radiusBlocks, null);
    }

    public static Map<String, SeenSummary> collectNearbyBlockEntities(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int radiusBlocks,
        Predicate<BlockEntity> filter
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

            if (filter != null && !filter.test(be)) {
                continue;
            }

            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (key == null) {
                continue;
            }

            String typeName = key.toString();

            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
            } else {
                summary.addLocation(pos);
            }
        }

        return grouped;
    }

    public static Map<String, SeenSummary> collectNearbyBlockEntitiesFiltered(
        ServerLevel serverLevel,
        Entity steveAiEntity,
        int radiusBlocks,
        Set<String> targetBlockEntityIds
    ) {
        if (targetBlockEntityIds == null || targetBlockEntityIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return collectNearbyBlockEntities(
            serverLevel,
            steveAiEntity,
            radiusBlocks,
            be -> {
                Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                return key != null && targetBlockEntityIds.contains(key.toString());
            }
        );
    }

    public static List<DetailedEntry> collectDetailedBlocksAt(
        ServerLevel serverLevel,
        BlockPos center,
        int radiusBlocks
    ) {
        List<DetailedEntry> out = new ArrayList<>();

        for (int y = -radiusBlocks; y <= radiusBlocks; y++) {
            for (int x = -radiusBlocks; x <= radiusBlocks; x++) {
                for (int z = -radiusBlocks; z <= radiusBlocks; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    Block block = state.getBlock();
                    Identifier key = BuiltInRegistries.BLOCK.getKey(block);
                    if (key == null) {
                        continue;
                    }

                    out.add(new DetailedEntry(key.toString(), pos));
                }
            }
        }

        return out;
    }

    public static List<DetailedEntry> collectDetailedBlockEntitiesAt(
        ServerLevel serverLevel,
        BlockPos center,
        int radiusBlocks
    ) {
        List<DetailedEntry> out = new ArrayList<>();

        BlockPos min = center.offset(-radiusBlocks, -radiusBlocks, -radiusBlocks);
        BlockPos max = center.offset(radiusBlocks, radiusBlocks, radiusBlocks);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be == null) {
                continue;
            }

            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (key == null) {
                continue;
            }

            out.add(new DetailedEntry(key.toString(), pos));
        }

        return out;
    }

    public static List<DetailedEntry> collectDetailedEntitiesAt(
        ServerLevel serverLevel,
        BlockPos center,
        int radiusBlocks
    ) {
        List<DetailedEntry> out = new ArrayList<>();

        var nearbyEntities = serverLevel.getEntities(
            (Entity) null,
            new net.minecraft.world.phys.AABB(
                center.getX() - radiusBlocks, center.getY() - radiusBlocks, center.getZ() - radiusBlocks,
                center.getX() + radiusBlocks + 1, center.getY() + radiusBlocks + 1, center.getZ() + radiusBlocks + 1
            ),
            other -> other != null && other.isAlive()
        );

        for (Entity nearby : nearbyEntities) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(nearby.getType());
            if (key == null) {
                continue;
            }

            out.add(new DetailedEntry(key.toString(), nearby.blockPosition()));
        }

        return out;
    }
}