package com.example.examplemod;
import net.minecraft.core.BlockPos;
import java.util.*;

public class PoiManager {
    public static class Poi {
        public String type;
        public BlockPos center;
        public Set<String> evidence = new HashSet<>();
        public int count = 0;
        public Set<BlockPos> seenPositions = new HashSet<>();

        public Poi(String type, BlockPos pos, String evidenceType) {
            this.type = type;
            this.center = pos;
            this.evidence.add(evidenceType);
            this.count = 1;
            this.seenPositions.add(pos.immutable());
        }

        public boolean add(BlockPos pos, String evidenceType) {
            boolean newEvidence = this.evidence.add(evidenceType);
            boolean newPosition = this.seenPositions.add(pos.immutable());

            if (!newEvidence && !newPosition) {
                return false;
            }

            this.center = new BlockPos(
                (this.center.getX() + pos.getX()) / 2,
                (this.center.getY() + pos.getY()) / 2,
                (this.center.getZ() + pos.getZ()) / 2
            );

            if (newPosition) {
                this.count++;
            }
            return true;
        }
    }

    private static final List<Poi> pois = new ArrayList<>();

    private static final int MERGE_DISTANCE = 20;

    // --- PUBLIC ENTRY POINT ---
    public static boolean processBlockEntity(String typeName, BlockPos pos) {
        String poiType = mapToPoi(typeName);
        if (poiType == null) return false;

        return addOrMerge(poiType, pos, typeName);
    }

    // --- POI MAPPING ---
    private static String mapToPoi(String type) {
        return switch (type) {
            case "minecraft:bed", "minecraft:blast_furnace" -> "village_candidate";

            case "minecraft:trial_spawner", "minecraft:vault" -> "trial_chamber";

            case "minecraft:mob_spawner", "minecraft:chest" -> "dungeon";

            case "minecraft:brushable_block" -> "archaeology_site";

            default -> null;
        };
    }

    // --- MERGE LOGIC ---
    private static boolean addOrMerge(String type, BlockPos pos, String evidence) {
        for (Poi poi : pois) {
            if (!poi.type.equals(type)) continue;

            if (poi.center.distSqr(pos) < MERGE_DISTANCE * MERGE_DISTANCE) {
                return poi.add(pos, evidence);
            }
        }

        pois.add(new Poi(type, pos, evidence));
        return true;
    }

    // --- OUTPUT ---
    public static List<String> buildSummaryLines() {
        List<String> lines = new ArrayList<>();

        for (Poi poi : pois) {
            String confidence = getConfidence(poi);

            lines.add(String.format(
                "%s loc=(%d,%d,%d) evidence=%s count=%d confidence=%s",
                poi.type,
                poi.center.getX(),
                poi.center.getY(),
                poi.center.getZ(),
                String.join(",", poi.evidence),
                poi.count,
                confidence
            ));
        }

        return lines;
    }

    private static String getConfidence(Poi poi) {
        if (poi.evidence.contains("minecraft:trial_spawner") && poi.evidence.contains("minecraft:vault")) {
            return "very_high";
        }

        if (poi.evidence.contains("minecraft:mob_spawner") && poi.evidence.contains("minecraft:chest")) {
            return "high";
        }

        if (poi.evidence.contains("minecraft:bed") && poi.evidence.contains("minecraft:blast_furnace")) {
            return "high";
        }

        return poi.count > 3 ? "medium" : "low";
    }

    public static void clear() {
        pois.clear();
    }

    public static BlockPos findNearestPoiCenter(String type, BlockPos fromPos) {
        Poi nearest = null;
        double bestDist = Double.MAX_VALUE;

        for (Poi poi : pois) {   // replace POIS with your actual collection
            if (!poi.type.equals(type)) continue;

            double dist = poi.center.distSqr(fromPos);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = poi;
            }
        }

        return nearest == null ? null : nearest.center.immutable();
    }

    public static BlockPos findNearestVillageForExplore(BlockPos fromPos) {
        Poi nearest = null;
        double bestDistXZ = Double.MAX_VALUE;

        for (Poi poi : pois) {
            if (!poi.type.equals("village_candidate")) continue;
            if (poi.count < 3) continue;

            String confidence = getConfidence(poi);
            if ("low".equals(confidence)) continue;

            double dx = poi.center.getX() - fromPos.getX();
            double dz = poi.center.getZ() - fromPos.getZ();
            double distXZ = dx * dx + dz * dz;

            if (distXZ < bestDistXZ) {
                bestDistXZ = distXZ;
                nearest = poi;
            }
        }

        return nearest == null ? null : nearest.center.immutable();
    }

}