package com.example.examplemod;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PoiManager {

    public static class Poi {
        public String type;
        public BlockPos center;
        public Set<String> evidence = new HashSet<>();
        public int count = 0;
        public Set<BlockPos> seenPositions = new HashSet<>();

        public Poi(String type, BlockPos pos, String evidenceType) {
            this.type = type;
            this.center = pos.immutable();
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

            if (newPosition) {
                this.count++;
                recomputeCenter();
            }

            return true;
        }

        private void recomputeCenter() {
            long sumX = 0;
            long sumY = 0;
            long sumZ = 0;

            for (BlockPos p : seenPositions) {
                sumX += p.getX();
                sumY += p.getY();
                sumZ += p.getZ();
            }

            int n = Math.max(1, seenPositions.size());
            this.center = new BlockPos(
                (int) Math.round((double) sumX / n),
                (int) Math.round((double) sumY / n),
                (int) Math.round((double) sumZ / n)
            );
        }
    }

    private static final List<Poi> pois = new ArrayList<>();

    // Village personality options — assigned when a village reaches high/very_high confidence.
    // Persists across PoiManager.clear() calls and is saved/loaded from disk.
    private static final String[] PERSONALITY_OPTIONS = {
        "scooby_doo",
        "er_tv_series",
        "lord_of_the_rings",
        "pirates_of_the_caribbean"
    };

    // Keyed by grid-snapped "X,Z" so minor center drift doesn't create duplicates.
    private static final Map<String, String> savedPersonalities = new HashMap<>();

    // MVP tuning constants
    private static final int VILLAGE_MIN_POSITION_COUNT = 2;   // proxy for "villager > 1"
    private static final int VILLAGE_MIN_Y = 10;

    private static final int UNDERGROUND_MAX_Y = 10;
    private static final int UNDERGROUND_BED_CLUSTER_MIN_POSITIONS = 5; // proxy for "beds > 4"

    public static boolean processBlockEntity(String typeName, BlockPos pos) {
        String normalized = normalizeEvidence(typeName);
        String poiType = mapToPoi(normalized);
        if (poiType == null) return false;

        return addOrMerge(poiType, pos, normalized);
    }

    public static boolean processEntity(String typeName, BlockPos pos) {
        String normalized = normalizeEvidence(typeName);
        String poiType = mapToPoi(normalized);
        if (poiType == null) return false;

        return addOrMerge(poiType, pos, normalized);
    }

    private static String normalizeEvidence(String type) {
        if (type == null) return "";

        return switch (type) {
            case "entity.minecraft.villager" -> "minecraft:villager";
            case "entity.minecraft.iron_golem" -> "minecraft:iron_golem";
            case "entity.minecraft.cat" -> "minecraft:cat";
            case "entity.minecraft.blaze" -> "minecraft:blaze";
            case "entity.minecraft.wither_skeleton" -> "minecraft:wither_skeleton";
            case "entity.minecraft.piglin" -> "minecraft:piglin";
            case "entity.minecraft.piglin_brute" -> "minecraft:piglin_brute";
            case "entity.minecraft.silverfish" -> "minecraft:silverfish";
            case "entity.minecraft.shulker" -> "minecraft:shulker";
            case "entity.minecraft.witch" -> "minecraft:witch";
            case "entity.minecraft.warden" -> "minecraft:warden";
            case "entity.minecraft.drowned" -> "minecraft:drowned";
            default -> type;
        };
    }

    private static String mapToPoi(String type) {
        return switch (type) {

            // Village evidence
            case "minecraft:bell",
                 "minecraft:villager",                
                 "minecraft:lectern",
                 "minecraft:bed",
                 "minecraft:brewing_stand",
                 "minecraft:blast_furnace",
                 "minecraft:smoker",
                 "minecraft:cartography_table",
                 "minecraft:composter",
                 "minecraft:iron_golem",
                 "minecraft:cat" -> "village_candidate";

            // Dungeon
            case "minecraft:mob_spawner",
                 "minecraft:spawner",
                 "minecraft:chest" -> "dungeon";

            // Trial chamber
            case "minecraft:trial_spawner",
                 "minecraft:vault",
                 "minecraft:decorated_pot",
                 "minecraft:waxed_copper_grate",
                 "minecraft:chiseled_tuff",
                 "minecraft:chiseled_tuff_bricks",
                 "minecraft:polished_tuff",
                 "minecraft:tuff_bricks",
                 "minecraft:dispenser",
                 "minecraft:barrel",
                 "minecraft:hopper" -> "trial_chamber";

            // Fortress
            case "minecraft:blaze",
                 "minecraft:wither_skeleton" -> "fortress";

            // Bastion
            case "minecraft:piglin",
                 "minecraft:piglin_brute" -> "bastion";

            // Stronghold
            case "minecraft:end_portal_frame",
                 "minecraft:silverfish" -> "stronghold";

            // Ruined portal
            case "minecraft:obsidian",
                 "minecraft:crying_obsidian" -> "ruined_portal";

            // End city
            case "minecraft:shulker",
                 "minecraft:dragon_head" -> "end_city";

            // Witch hut
            case "minecraft:witch" -> "witch_hut";

            // Ancient city
            case "minecraft:sculk_shrieker",
                 "minecraft:sculk_sensor",
                 "minecraft:reinforced_deepslate",
                 "minecraft:warden" -> "ancient_city";

            // Geode
            case "minecraft:budding_amethyst",
                 "minecraft:amethyst_block",
                 "minecraft:calcite",
                 "minecraft:smooth_basalt" -> "geode";

            // Fossil
            case "minecraft:bone_block" -> "fossil";

            // Lava pool
            case "minecraft:lava" -> "lava_pool";

            // Ore vein
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

    private static int getMergeDistance(String poiType, String evidence) {
        return switch (poiType) {
            case "village_candidate", "village" -> switch (evidence) {
                case "minecraft:bell" -> 96;
                case "minecraft:villager", "minecraft:iron_golem", "minecraft:bed" -> 72;
                default -> 56;
            };
            case "trial_chamber" -> 40;
            case "dungeon" -> 16;
            case "geode" -> 24;
            case "shipwreck" -> 24;
            case "fortress", "bastion", "stronghold", "ancient_city" -> 48;
            case "ruined_portal" -> 24;
            case "ore_vein" -> 20;
            case "lava_pool" -> 20;
            case "fossil" -> 20;
            default -> 20;
        };
    }

    private static boolean addOrMerge(String type, BlockPos pos, String evidence) {
        Poi best = null;
        double bestDist = Double.MAX_VALUE;

        for (Poi poi : pois) {
            if (!poi.type.equals(type)) continue;

            int mergeDistance = getMergeDistance(type, evidence);
            double dist = poi.center.distSqr(pos);
            if (dist <= (double) mergeDistance * mergeDistance && dist < bestDist) {
                bestDist = dist;
                best = poi;
            }
        }

        if (best != null) {
            return best.add(pos, evidence);
        }

        pois.add(new Poi(type, pos, evidence));
        return true;
    }

    private static boolean hasAnyVillageWorkstation(Poi poi) {
        return poi.evidence.contains("minecraft:lectern")
            || poi.evidence.contains("minecraft:brewing_stand")
            || poi.evidence.contains("minecraft:blast_furnace")
            || poi.evidence.contains("minecraft:smoker")
            || poi.evidence.contains("minecraft:cartography_table")
            || poi.evidence.contains("minecraft:composter");
    }

    private static boolean hasTrialChamberCore(Poi poi) {
        return poi.evidence.contains("minecraft:trial_spawner")
            || poi.evidence.contains("minecraft:vault");
    }

    private static boolean hasTrialChamberSupport(Poi poi) {
        return poi.evidence.contains("minecraft:decorated_pot")
            || poi.evidence.contains("minecraft:dispenser")
            || poi.evidence.contains("minecraft:hopper")
            || poi.evidence.contains("minecraft:barrel")
            || poi.evidence.contains("minecraft:waxed_copper_grate")
            || poi.evidence.contains("minecraft:chiseled_tuff")
            || poi.evidence.contains("minecraft:chiseled_tuff_bricks")
            || poi.evidence.contains("minecraft:polished_tuff")
            || poi.evidence.contains("minecraft:tuff_bricks");
    }

    private static String classifyPoiType(Poi poi) {
        if (!poi.type.equals("village_candidate")) {
            return poi.type;
        }

        int centerY = poi.center.getY();

        boolean hasBed = poi.evidence.contains("minecraft:bed");
        boolean hasBell = poi.evidence.contains("minecraft:bell");
        boolean hasVillager = poi.evidence.contains("minecraft:villager");
        boolean hasIronGolem = poi.evidence.contains("minecraft:iron_golem");
        boolean hasCat = poi.evidence.contains("minecraft:cat");
        boolean hasWorkstation = hasAnyVillageWorkstation(poi);

        boolean hasTrialCore = hasTrialChamberCore(poi);
        boolean hasTrialSupport = hasTrialChamberSupport(poi);

        int posCount = poi.seenPositions.size();
        int evidenceCount = poi.evidence.size();

        // New tuning:
        // - underground bed-heavy clusters should not become villages
        // - underground chamber-ish support should push away from village
        boolean undergroundBedClusterLike =
            hasBed
            && centerY < UNDERGROUND_MAX_Y
            && posCount >= UNDERGROUND_BED_CLUSTER_MIN_POSITIONS;

        if (undergroundBedClusterLike) {
            return "village_candidate";
        }

        int score = 0;
        if (hasBed) score += 3;
        if (hasVillager) score += 3;
        if (hasBell) score += 4;
        if (hasIronGolem) score += 2;
        if (hasWorkstation) score += 2;
        if (hasCat) score += 1;

        if (posCount >= 12) score += 3;
        else if (posCount >= 6) score += 2;
        else if (posCount >= 3) score += 1;

        if (evidenceCount >= 5) score += 2;
        else if (evidenceCount >= 3) score += 1;

        if (centerY < UNDERGROUND_MAX_Y && (hasTrialCore || hasTrialSupport)) {
            score -= 4;
        }

        boolean canBeVillage =
            hasVillager
            && posCount >= VILLAGE_MIN_POSITION_COUNT
            && centerY > VILLAGE_MIN_Y;

        if (canBeVillage && score >= 7) {
            return "village";
        }

        return "village_candidate";
    }

    public static List<String> buildSummaryLines() {
        List<String> lines = new ArrayList<>();

        for (Poi poi : pois) {
            String finalType = classifyPoiType(poi);
            String confidence = getConfidence(poi, finalType);

            String personalityPart = "";
            if ("village".equals(finalType)
                    && ("high".equals(confidence) || "very_high".equals(confidence))) {
                String personality = getOrAssignPersonality(poi);
                String chars = getPersonalityCharacters(personality);
                personalityPart = " personality=" + personality + " characters=[" + chars + "]";
            }

            lines.add(String.format(
                "%s loc=(%d,%d,%d) evidence=%s count=%d confidence=%s%s",
                finalType,
                poi.center.getX(),
                poi.center.getY(),
                poi.center.getZ(),
                String.join(",", new java.util.TreeSet<>(poi.evidence)),
                poi.count,
                confidence,
                personalityPart
            ));
        }

        return lines;
    }

    // -------------------------------------------------------------------------
    // Village personality helpers
    // -------------------------------------------------------------------------

    /** Grid-snapped key so minor center drift doesn't create duplicate entries. */
    private static String personalityKey(BlockPos center) {
        int gx = Math.round((float) center.getX() / 64) * 64;
        int gz = Math.round((float) center.getZ() / 64) * 64;
        return gx + "," + gz;
    }

    /** Returns the existing personality for this village, or randomly assigns one. */
    private static String getOrAssignPersonality(Poi poi) {
        String key = personalityKey(poi.center);
        return savedPersonalities.computeIfAbsent(key, k -> {
            // Default to scooby_doo (index 0); use random to pick across options.
            int idx = (int) (Math.random() * PERSONALITY_OPTIONS.length);
            return PERSONALITY_OPTIONS[idx];
        });
    }

    /** Human-readable character list for a personality theme. */
    public static String getPersonalityCharacters(String personality) {
        if (personality == null) return "";
        return switch (personality) {
            case "scooby_doo"             -> "Scooby-Doo, Shaggy, Velma, Daphne, Fred";
            case "er_tv_series"           -> "Dr. Mark Greene, Dr. Doug Ross, Dr. John Carter, Nurse Carol Hathaway, Dr. Peter Benton";
            case "lord_of_the_rings"      -> "Gandalf, Frodo, Aragorn, Legolas, Gimli, Samwise Gamgee, Boromir";
            case "pirates_of_the_caribbean" -> "Jack Sparrow, Will Turner, Elizabeth Swann, Hector Barbossa, Davy Jones";
            default -> "";
        };
    }

    /** Loads saved personalities from a flat properties file (survives server restarts). */
    public static void loadPersonalitiesFromFile(Path file) {
        if (file == null || !Files.exists(file)) return;
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split("=", 2);
                if (parts.length == 2) {
                    savedPersonalities.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            // start fresh — personalities will be re-assigned on next scan
        }
    }

    /** Persists all assignments to a flat properties file. */
    public static void savePersonalitiesToFile(Path file) {
        if (savedPersonalities.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# Village personalities — do not edit manually\n");
            sb.append("# Format: gridX,gridZ=personality\n");
            for (Map.Entry<String, String> entry : savedPersonalities.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            Files.writeString(
                file, sb.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            // non-fatal — data will be re-derived on next scan
        }
    }

    private static String getConfidence(Poi poi, String finalType) {
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
        boolean hasVillager = poi.evidence.contains("minecraft:villager");
        boolean hasIronGolem = poi.evidence.contains("minecraft:iron_golem");
        boolean hasCat = poi.evidence.contains("minecraft:cat");

        boolean hasSpawner = poi.evidence.contains("minecraft:mob_spawner")
            || poi.evidence.contains("minecraft:spawner");
        boolean hasChest = poi.evidence.contains("minecraft:chest");

        boolean hasTrialSpawner = poi.evidence.contains("minecraft:trial_spawner");
        boolean hasVault = poi.evidence.contains("minecraft:vault");
        boolean hasDecoratedPot = poi.evidence.contains("minecraft:decorated_pot");
        boolean hasTuff = poi.evidence.contains("minecraft:tuff_bricks")
            || poi.evidence.contains("minecraft:polished_tuff")
            || poi.evidence.contains("minecraft:chiseled_tuff")
            || poi.evidence.contains("minecraft:chiseled_tuff_bricks");
        boolean hasCopper = poi.evidence.contains("minecraft:waxed_copper_grate");

        boolean hasBlaze = poi.evidence.contains("minecraft:blaze");
        boolean hasWitherSkeleton = poi.evidence.contains("minecraft:wither_skeleton");

        boolean hasPiglin = poi.evidence.contains("minecraft:piglin");
        boolean hasPiglinBrute = poi.evidence.contains("minecraft:piglin_brute");

        boolean hasEndPortalFrame = poi.evidence.contains("minecraft:end_portal_frame");
        boolean hasSilverfish = poi.evidence.contains("minecraft:silverfish");

        boolean hasObsidian = poi.evidence.contains("minecraft:obsidian");
        boolean hasCryingObsidian = poi.evidence.contains("minecraft:crying_obsidian");

        boolean hasShulker = poi.evidence.contains("minecraft:shulker");
        boolean hasDragonHead = poi.evidence.contains("minecraft:dragon_head");

        boolean hasWitch = poi.evidence.contains("minecraft:witch");

        boolean hasSculkShrieker = poi.evidence.contains("minecraft:sculk_shrieker");
        boolean hasSculkSensor = poi.evidence.contains("minecraft:sculk_sensor");
        boolean hasReinforcedDeepslate = poi.evidence.contains("minecraft:reinforced_deepslate");
        boolean hasWarden = poi.evidence.contains("minecraft:warden");

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

        switch (finalType) {
            case "village" -> {
                int score = 0;

                if (hasBed) score += 3;
                if (hasVillager) score += 3;
                if (hasBell) score += 4;
                if (hasIronGolem) score += 2;
                if (hasLectern) score += 2;
                if (hasBrewingStand) score += 2;
                if (hasBlastFurnace) score += 2;
                if (hasSmoker) score += 1;
                if (hasCartographyTable) score += 2;
                if (hasComposter) score += 1;
                if (hasCat) score += 1;

                if (posCount >= 12) score += 3;
                else if (posCount >= 6) score += 2;
                else if (posCount >= 3) score += 1;

                if (evidenceCount >= 5) score += 2;
                else if (evidenceCount >= 3) score += 1;

                if (score >= 12) return "very_high";
                if (score >= 8) return "high";
                if (score >= 5) return "medium";
                return "low";
            }

            case "village_candidate" -> {
                int centerY = poi.center.getY();

                boolean undergroundBedClusterLike =
                    hasBed
                    && centerY < UNDERGROUND_MAX_Y
                    && posCount >= UNDERGROUND_BED_CLUSTER_MIN_POSITIONS;

                if (undergroundBedClusterLike) return "low";

                if (centerY > VILLAGE_MIN_Y && hasVillager && (hasBed || hasBell)) return "high";
                if (centerY > VILLAGE_MIN_Y && (hasBed || hasVillager || hasBell || hasLectern || hasBlastFurnace || hasSmoker)) return "medium";
                return "low";
            }

            case "dungeon" -> {
                if (hasSpawner && hasChest) return "very_high";
                if (hasSpawner) return posCount >= 2 ? "high" : "medium";
                if (hasChest && posCount >= 2) return "medium";
                return "low";
            }

            case "trial_chamber" -> {
                int score = 0;
                if (hasTrialSpawner) score += 4;
                if (hasVault) score += 4;
                if (hasDecoratedPot) score += 2;
                if (hasTuff) score += 2;
                if (hasCopper) score += 2;
                if (posCount >= 6) score += 2;
                else if (posCount >= 3) score += 1;

                if (score >= 10) return "very_high";
                if (score >= 7) return "high";
                if (score >= 4) return "medium";
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

        for (Poi poi : pois) {
            String finalType = classifyPoiType(poi);
            if (!finalType.equals(type)) continue;

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
            String finalType = classifyPoiType(poi);
            if (!finalType.equals("village")) continue;

            String confidence = getConfidence(poi, finalType);
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