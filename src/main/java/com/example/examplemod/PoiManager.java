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

    public static boolean processEntity(String typeName, BlockPos pos) {
        String poiType = mapToPoi(typeName);
        if (poiType == null) return false;

        return addOrMerge(poiType, pos, typeName);
    }

    // --- POI MAPPING ---
    private static String mapToPoi(String type) {
        return switch (type) {

            // --- Level 0: village ---
            case "minecraft:bed",
                "minecraft:bell",
                "minecraft:lectern",
                "minecraft:brewing_stand",
                "minecraft:blast_furnace",
                "minecraft:smoker",
                "minecraft:cartography_table",
                "minecraft:composter",
                "entity.minecraft.villager",
                "entity.minecraft.iron_golem" -> "village";

            // --- Level 1: dungeon / temple / shipwreck / fortress / bastion ---
            case "minecraft:mob_spawner",
                "minecraft:spawner",
                "minecraft:chest" -> "dungeon";

            case "minecraft:trial_spawner",
                "minecraft:vault" -> "temple";

            // later split into desert_temple / jungle_temple if desired
            case "minecraft:tnt",
                "minecraft:stone_pressure_plate",
                "minecraft:orange_terracotta",
                "minecraft:blue_terracotta" -> "temple";

            case "minecraft:barrel",
                "entity.minecraft.drowned" -> "shipwreck";

            case "entity.minecraft.blaze",
                "entity.minecraft.wither_skeleton" -> "fortress";

            case "entity.minecraft.piglin",
                "entity.minecraft.piglin_brute" -> "bastion";

            // --- Level 2: progression / navigation ---
            case "minecraft:end_portal_frame",
                "entity.minecraft.silverfish" -> "stronghold";

            case "minecraft:obsidian",
                "minecraft:crying_obsidian" -> "ruined_portal";

            case "entity.minecraft.shulker",
                "minecraft:dragon_head" -> "end_city";

            case "entity.minecraft.witch",
                "entity.minecraft.cat" -> "witch_hut";

            case "minecraft:sculk_shrieker",
                "minecraft:sculk_sensor",
                "minecraft:reinforced_deepslate",
                "entity.minecraft.warden" -> "ancient_city";

            // --- Level 3: resource ---
            case "minecraft:budding_amethyst",
                "minecraft:amethyst_block",
                "minecraft:calcite",
                "minecraft:smooth_basalt" -> "geode";

            case "minecraft:bone_block" -> "fossil";

            case "minecraft:lava" -> "lava_pool";

            // later you may want ore-specific subtypes instead
            case "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:copper_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:gold_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:redstone_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:lapis_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:coal_ore",
                "minecraft:deepslate_coal_ore" -> "ore_vein";

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
        int evidenceCount = poi.evidence.size();
        int posCount = poi.seenPositions.size();

        boolean hasBed = poi.evidence.contains("minecraft:bed");
        boolean hasBell = poi.evidence.contains("minecraft:bell");
        boolean hasLectern = poi.evidence.contains("minecraft:lectern");
        boolean hasBrewingStand = poi.evidence.contains("minecraft:brewing_stand");
        boolean hasBlastFurnace = poi.evidence.contains("minecraft:blast_furnace");
        boolean hasSmoker = poi.evidence.contains("minecraft:smoker");
        boolean hasCartographyTable = poi.evidence.contains("minecraft:cartography_table");
        boolean hasComposter = poi.evidence.contains("minecraft:composter");
        boolean hasBarrel = poi.evidence.contains("minecraft:barrel");
        boolean hasVillager = poi.evidence.contains("entity.minecraft.villager");
        boolean hasIronGolem = poi.evidence.contains("entity.minecraft.iron_golem");

        boolean hasSpawner = poi.evidence.contains("minecraft:mob_spawner")
            || poi.evidence.contains("minecraft:spawner");
        boolean hasChest = poi.evidence.contains("minecraft:chest");

        boolean hasTrialSpawner = poi.evidence.contains("minecraft:trial_spawner");
        boolean hasVault = poi.evidence.contains("minecraft:vault");
        boolean hasTnt = poi.evidence.contains("minecraft:tnt");
        boolean hasStonePressurePlate = poi.evidence.contains("minecraft:stone_pressure_plate");
        boolean hasOrangeTerracotta = poi.evidence.contains("minecraft:orange_terracotta");
        boolean hasBlueTerracotta = poi.evidence.contains("minecraft:blue_terracotta");

        boolean hasBlaze = poi.evidence.contains("entity.minecraft.blaze");
        boolean hasWitherSkeleton = poi.evidence.contains("entity.minecraft.wither_skeleton");

        boolean hasPiglin = poi.evidence.contains("entity.minecraft.piglin");
        boolean hasPiglinBrute = poi.evidence.contains("entity.minecraft.piglin_brute");

        boolean hasEndPortalFrame = poi.evidence.contains("minecraft:end_portal_frame");
        boolean hasSilverfish = poi.evidence.contains("entity.minecraft.silverfish");

        boolean hasObsidian = poi.evidence.contains("minecraft:obsidian");
        boolean hasCryingObsidian = poi.evidence.contains("minecraft:crying_obsidian");

        boolean hasShulker = poi.evidence.contains("entity.minecraft.shulker");
        boolean hasDragonHead = poi.evidence.contains("minecraft:dragon_head");

        boolean hasWitch = poi.evidence.contains("entity.minecraft.witch");
        boolean hasCat = poi.evidence.contains("entity.minecraft.cat");

        boolean hasSculkShrieker = poi.evidence.contains("minecraft:sculk_shrieker");
        boolean hasSculkSensor = poi.evidence.contains("minecraft:sculk_sensor");
        boolean hasReinforcedDeepslate = poi.evidence.contains("minecraft:reinforced_deepslate");
        boolean hasWarden = poi.evidence.contains("entity.minecraft.warden");

        boolean hasBuddingAmethyst = poi.evidence.contains("minecraft:budding_amethyst");
        boolean hasAmethystBlock = poi.evidence.contains("minecraft:amethyst_block");
        boolean hasCalcite = poi.evidence.contains("minecraft:calcite");
        boolean hasSmoothBasalt = poi.evidence.contains("minecraft:smooth_basalt");

        boolean hasBoneBlock = poi.evidence.contains("minecraft:bone_block");
        boolean hasLava = poi.evidence.contains("minecraft:lava");

        boolean hasOre = poi.evidence.contains("minecraft:diamond_ore")
            || poi.evidence.contains("minecraft:deepslate_diamond_ore")
            || poi.evidence.contains("minecraft:iron_ore")
            || poi.evidence.contains("minecraft:deepslate_iron_ore")
            || poi.evidence.contains("minecraft:copper_ore")
            || poi.evidence.contains("minecraft:deepslate_copper_ore")
            || poi.evidence.contains("minecraft:gold_ore")
            || poi.evidence.contains("minecraft:deepslate_gold_ore")
            || poi.evidence.contains("minecraft:redstone_ore")
            || poi.evidence.contains("minecraft:deepslate_redstone_ore")
            || poi.evidence.contains("minecraft:lapis_ore")
            || poi.evidence.contains("minecraft:deepslate_lapis_ore")
            || poi.evidence.contains("minecraft:coal_ore")
            || poi.evidence.contains("minecraft:deepslate_coal_ore");

        switch (poi.type) {
            case "village", "village_candidate" -> {
                int score = 0;

                if (hasBed) score += 3;
                if (hasVillager) score += 3;
                if (hasBell) score += 3;
                if (hasIronGolem) score += 2;
                if (hasLectern) score += 2;
                if (hasBrewingStand) score += 2;
                if (hasBlastFurnace) score += 2;
                if (hasSmoker) score += 1;
                if (hasCartographyTable) score += 2;
                if (hasComposter) score += 1;
                if (hasBarrel) score += 1;

                if (posCount >= 6) score += 3;
                else if (posCount >= 3) score += 2;
                else if (posCount >= 2) score += 1;

                if (evidenceCount >= 5) score += 3;
                else if (evidenceCount >= 3) score += 2;
                else if (evidenceCount >= 2) score += 1;

                if (score >= 10) return "very_high";
                if (score >= 6) return "high";
                if (score >= 3) return "medium";
                return "low";
            }

            case "dungeon" -> {
                if (hasSpawner && hasChest) return "very_high";
                if (hasSpawner) return posCount >= 2 ? "high" : "medium";
                if (hasChest && posCount >= 2) return "medium";
                return "low";
            }

            case "temple" -> {
                int score = 0;

                if (hasTrialSpawner) score += 4;
                if (hasVault) score += 4;

                if (hasTnt) score += 3;
                if (hasStonePressurePlate) score += 3;
                if (hasOrangeTerracotta) score += 1;
                if (hasBlueTerracotta) score += 1;

                if (posCount >= 4) score += 2;
                else if (posCount >= 2) score += 1;

                if (score >= 8) return "very_high";
                if (score >= 5) return "high";
                if (score >= 3) return "medium";
                return "low";
            }

            case "fortress" -> {
                if (hasBlaze && hasWitherSkeleton) return "very_high";
                if (hasBlaze || hasWitherSkeleton) return "high";
                return posCount >= 3 ? "medium" : "low";
            }

            case "bastion" -> {
                if (hasPiglinBrute && hasPiglin) return "very_high";
                if (hasPiglinBrute || hasPiglin) return "high";
                return posCount >= 3 ? "medium" : "low";
            }

            case "shipwreck" -> {
                return posCount >= 3 ? "medium" : "low";
            }

            case "stronghold" -> {
                if (hasEndPortalFrame) return "very_high";
                if (hasSilverfish) return "high";
                return evidenceCount >= 2 ? "medium" : "low";
            }

            case "ruined_portal" -> {
                if (hasObsidian && hasCryingObsidian) return "very_high";
                if (hasObsidian || hasCryingObsidian) return posCount >= 3 ? "high" : "medium";
                return "low";
            }

            case "end_city" -> {
                if (hasShulker && hasDragonHead) return "very_high";
                if (hasShulker || hasDragonHead) return "high";
                return evidenceCount >= 2 ? "medium" : "low";
            }

            case "witch_hut" -> {
                if (hasWitch && hasCat) return "very_high";
                if (hasWitch || hasCat) return "high";
                return "low";
            }

            case "ancient_city" -> {
                int score = 0;

                if (hasSculkShrieker) score += 4;
                if (hasSculkSensor) score += 3;
                if (hasReinforcedDeepslate) score += 4;
                if (hasWarden) score += 5;

                if (posCount >= 3) score += 2;
                if (evidenceCount >= 3) score += 2;

                if (score >= 9) return "very_high";
                if (score >= 5) return "high";
                if (score >= 3) return "medium";
                return "low";
            }

            case "geode" -> {
                int score = 0;

                if (hasBuddingAmethyst) score += 4;
                if (hasAmethystBlock) score += 2;
                if (hasCalcite) score += 2;
                if (hasSmoothBasalt) score += 2;

                if (posCount >= 4) score += 2;

                if (score >= 8) return "very_high";
                if (score >= 5) return "high";
                if (score >= 3) return "medium";
                return "low";
            }

            case "fossil" -> {
                if (hasBoneBlock && posCount >= 4) return "very_high";
                if (hasBoneBlock && posCount >= 2) return "high";
                if (hasBoneBlock) return "medium";
                return "low";
            }

            case "lava_pool" -> {
                if (hasLava && posCount >= 8) return "high";
                if (hasLava && posCount >= 3) return "medium";
                if (hasLava) return "low";
                return "low";
            }

            case "ore_vein" -> {
                if (hasOre && posCount >= 10) return "high";
                if (hasOre && posCount >= 4) return "medium";
                if (hasOre) return "low";
                return "low";
            }

            default -> {
                if (evidenceCount >= 4 || posCount >= 6) return "high";
                if (evidenceCount >= 2 || posCount >= 3) return "medium";
                return "low";
            }
        }
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