/**
 * File: SteveAiCollectors.java
 *
 * Main intent:
 * Defines SteveAiCollectors functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SeenSummary(...)}:
 *    Purpose: Performs seen summary.
 *    Input: int x, int y, int z.
 *    Output: none (constructor).
 * 2) {@code SeenSummary(...)}:
 *    Purpose: Performs seen summary.
 *    Input: int x, int y, int z, boolean storeAllLocations.
 *    Output: none (constructor).
 * 3) {@code increment(...)}:
 *    Purpose: Performs increment.
 *    Input: none.
 *    Output: void.
 * 4) {@code addLocation(...)}:
 *    Purpose: Adds add location.
 *    Input: BlockPos pos.
 *    Output: void.
 * 5) {@code setMinDistanceFromCenter(...)}:
 *    Purpose: Sets set min distance from center.
 *    Input: double distance.
 *    Output: void.
 * 6) {@code storesAllLocations(...)}:
 *    Purpose: Performs stores all locations.
 *    Input: none.
 *    Output: boolean.
 * 7) {@code toString(...)}:
 *    Purpose: Performs to string.
 *    Input: none.
 *    Output: String.
 * 8) {@code DetailedEntry(...)}:
 *    Purpose: Performs detailed entry.
 *    Input: String typeName, BlockPos pos.
 *    Output: none (constructor).
 * 9) {@code toString(...)}:
 *    Purpose: Performs to string.
 *    Input: none.
 *    Output: String.
 * 10) {@code shouldStoreAllLocationsForBlock(...)}:
 *    Purpose: Checks whether should store all locations for block.
 *    Input: String blockName.
 *    Output: boolean.
 * 11) {@code collectNearbyEntities(...)}:
 *    Purpose: Performs collect nearby entities.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, double radius, Predicate<Entity> filter.
 *    Output: Map<String, SeenSummary>.
 * 12) {@code collectNearbyEntities(...)}:
 *    Purpose: Performs collect nearby entities.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, double radius.
 *    Output: Map<String, SeenSummary>.
 * 13) {@code collectNearbyEntitiesFiltered(...)}:
 *    Purpose: Performs collect nearby entities filtered.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, double radius, Set<String> targetEntityIds.
 *    Output: Map<String, SeenSummary>.
 * 14) {@code collectNearbyBlocks(...)}:
 *    Purpose: Performs collect nearby blocks.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int horizontalRadius, int verticalRadius, Predicate<BlockState> filter.
 *    Output: Map<String, SeenSummary>.
 * 15) {@code collectNearbyBlocks(...)}:
 *    Purpose: Performs collect nearby blocks.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int horizontalRadius, int verticalRadius.
 *    Output: Map<String, SeenSummary>.
 * 16) {@code collectNearbyBlocksFiltered(...)}:
 *    Purpose: Performs collect nearby blocks filtered.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int horizontalRadius, int verticalRadius, Set<String> targetBlockIds.
 *    Output: Map<String, SeenSummary>.
 * 17) {@code collectNearbyBlockEntities(...)}:
 *    Purpose: Performs collect nearby block entities.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int radiusBlocks.
 *    Output: Map<String, SeenSummary>.
 * 18) {@code collectNearbyBlockEntities(...)}:
 *    Purpose: Performs collect nearby block entities.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int radiusBlocks, Predicate<BlockEntity> filter.
 *    Output: Map<String, SeenSummary>.
 * 19) {@code collectNearbyBlockEntitiesFiltered(...)}:
 *    Purpose: Performs collect nearby block entities filtered.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity, int radiusBlocks, Set<String> targetBlockEntityIds.
 *    Output: Map<String, SeenSummary>.
 * 20) {@code collectEntitiesInTile(...)}:
 *    Purpose: Performs collect entities in tile.
 *    Input: ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ, int yCenter, int yRadius.
 *    Output: Map<String, SeenSummary>.
 * 21) {@code collectBlocksInTile(...)}:
 *    Purpose: Performs collect blocks in tile.
 *    Input: ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ, int yMin, int yMax, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter.
 *    Output: Map<String, SeenSummary>.
 * 22) {@code collectBlockEntitiesInTile(...)}:
 *    Purpose: Performs collect block entities in tile.
 *    Input: ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ, int yMin, int yMax.
 *    Output: Map<String, SeenSummary>.
 * 23) {@code mergeInto(...)}:
 *    Purpose: Performs merge into.
 *    Input: Map<String, SeenSummary> target, Map<String, SeenSummary> source.
 *    Output: void.
 * 24) {@code annotateDistanceFromCenter(...)}:
 *    Purpose: Performs annotate distance from center.
 *    Input: Map<String, SeenSummary> grouped, BlockPos center.
 *    Output: void.
 * 25) {@code collectDetailedBlocksAt(...)}:
 *    Purpose: Performs collect detailed blocks at.
 *    Input: ServerLevel serverLevel, BlockPos center, int radiusBlocks.
 *    Output: List<DetailedEntry>.
 * 26) {@code collectDetailedBlockEntitiesAt(...)}:
 *    Purpose: Performs collect detailed block entities at.
 *    Input: ServerLevel serverLevel, BlockPos center, int radiusBlocks.
 *    Output: List<DetailedEntry>.
 * 27) {@code collectDetailedEntitiesAt(...)}:
 *    Purpose: Performs collect detailed entities at.
 *    Input: ServerLevel serverLevel, BlockPos center, int radiusBlocks.
 *    Output: List<DetailedEntry>.
 */
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
        public double minDistanceFromCenter = -1.0;

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

        public void setMinDistanceFromCenter(double distance) {
            if (distance < 0) {
                return;
            }
            if (this.minDistanceFromCenter < 0 || distance < this.minDistanceFromCenter) {
                this.minDistanceFromCenter = distance;
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
            if (minDistanceFromCenter >= 0) {
                sb.append(", minDist=")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", minDistanceFromCenter));
            }

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

    // ── Tile-based incremental scan helpers ───────────────────────────────────

    /**
     * Collect entities whose block position falls inside the given horizontal tile
     * (minX..maxX, minZ..maxZ) and within ±yRadius of yCenter.
     */
    public static Map<String, SeenSummary> collectEntitiesInTile(
        ServerLevel serverLevel,
        int minX, int minZ, int maxX, int maxZ,
        int yCenter, int yRadius
    ) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            minX, yCenter - yRadius, minZ,
            maxX + 1, yCenter + yRadius + 1, maxZ + 1
        );
        var entities = serverLevel.getEntities(
            (net.minecraft.world.entity.Entity) null, box,
            e -> e != null && e.isAlive()
        );
        Map<String, SeenSummary> grouped = new LinkedHashMap<>();
        for (net.minecraft.world.entity.Entity nearby : entities) {
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

    /**
     * Collect blocks inside the given non-overlapping tile box, applying the same
     * interesting-block filter as the regular nearby-blocks collector.
     */
    public static Map<String, SeenSummary> collectBlocksInTile(
        ServerLevel serverLevel,
        int minX, int minZ, int maxX, int maxZ,
        int yMin, int yMax,
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter
    ) {
        Map<String, SeenSummary> grouped = new LinkedHashMap<>();
        for (int y = yMin; y <= yMax; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (filter != null && !filter.test(state)) continue;
                    net.minecraft.world.level.block.Block block = state.getBlock();
                    String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();
                    SeenSummary existing = grouped.get(blockName);
                    if (existing == null) {
                        grouped.put(blockName, new SeenSummary(x, y, z, shouldStoreAllLocationsForBlock(blockName)));
                    } else {
                        if (existing.storesAllLocations()) existing.addLocation(new BlockPos(x, y, z));
                        else existing.increment();
                    }
                }
            }
        }
        return grouped;
    }

    /**
     * Collect block entities inside the given non-overlapping tile box.
     */
    public static Map<String, SeenSummary> collectBlockEntitiesInTile(
        ServerLevel serverLevel,
        int minX, int minZ, int maxX, int maxZ,
        int yMin, int yMax
    ) {
        Map<String, SeenSummary> grouped = new LinkedHashMap<>();
        BlockPos min = new BlockPos(minX, yMin, minZ);
        BlockPos max = new BlockPos(maxX, yMax, maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            net.minecraft.world.level.block.entity.BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be == null) continue;
            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (key == null) continue;
            String typeName = key.toString();
            SeenSummary summary = grouped.get(typeName);
            if (summary == null) {
                grouped.put(typeName, new SeenSummary(pos.getX(), pos.getY(), pos.getZ(), true));
            } else {
                summary.addLocation(pos.immutable());
            }
        }
        return grouped;
    }

    /**
     * Merge all entries from {@code source} into {@code target}.
     * Entries that store all locations will have their location lists combined;
     * count-only entries will have their counts summed.
     */
    public static void mergeInto(
        Map<String, SeenSummary> target,
        Map<String, SeenSummary> source
    ) {
        for (Map.Entry<String, SeenSummary> entry : source.entrySet()) {
            String key = entry.getKey();
            SeenSummary src = entry.getValue();
            SeenSummary tgt = target.get(key);
            if (tgt == null) {
                target.put(key, src);
            } else {
                if (src.storesAllLocations() && src.allLocations != null) {
                    for (BlockPos loc : src.allLocations) {
                        tgt.addLocation(loc);
                    }

                    int missingCount = src.count - src.allLocations.size();
                    for (int i = 0; i < missingCount; i++) {
                        tgt.increment();
                    }
                } else {
                    for (int i = 0; i < src.count; i++) {
                        tgt.increment();
                    }
                }

                if (src.minDistanceFromCenter >= 0) {
                    tgt.setMinDistanceFromCenter(src.minDistanceFromCenter);
                }
            }
        }
    }

    public static void annotateDistanceFromCenter(
        Map<String, SeenSummary> grouped,
        BlockPos center
    ) {
        if (grouped == null || center == null) {
            return;
        }

        for (SeenSummary summary : grouped.values()) {
            if (summary == null) {
                continue;
            }

            double bestDistance = Double.MAX_VALUE;
            boolean found = false;

            if (summary.storesAllLocations() && summary.allLocations != null && !summary.allLocations.isEmpty()) {
                for (BlockPos pos : summary.allLocations) {
                    double distance = Math.sqrt(pos.distSqr(center));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                    }
                    found = true;
                }
            } else {
                BlockPos pos = new BlockPos(summary.x, summary.y, summary.z);
                bestDistance = Math.sqrt(pos.distSqr(center));
                found = true;
            }

            if (found) {
                summary.setMinDistanceFromCenter(bestDistance);
            }
        }
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
