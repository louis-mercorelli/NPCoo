package com.example.examplemod;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final Map<String, VillageAssignment> savedPersonalities = new HashMap<>();

    public static class VillageAssignment {
        public final String personality;
        public final String scene;

        public VillageAssignment(String personality, String scene) {
            this.personality = personality;
            this.scene = scene;
        }
    }

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
                VillageAssignment assignment = getOrAssignVillageAssignment(poi);
                // Keep personality locked, but pick a fresh scene each summary rebuild.
                String activeScene = chooseSceneForPersonality(assignment.personality);
                String chars = getPersonalityCharacters(assignment.personality);
                personalityPart = " personality=" + assignment.personality
                    + " scene=\"" + activeScene + "\""
                    + " characters=[" + chars + "]";
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

    /** Returns the locked assignment for this village, or assigns one once and keeps it. */
    private static VillageAssignment getOrAssignVillageAssignment(Poi poi) {
        String key = personalityKey(poi.center);
        return savedPersonalities.computeIfAbsent(key, k -> {
            String personality = choosePersonality();
            String scene = chooseSceneForPersonality(personality);
            return new VillageAssignment(personality, scene);
        });
    }

    private static String choosePersonality() {
        if (PERSONALITY_OPTIONS.length == 0) {
            return "scooby_doo";
        }
        int idx = ThreadLocalRandom.current().nextInt(PERSONALITY_OPTIONS.length);
        String chosen = PERSONALITY_OPTIONS[idx];
        return (chosen == null || chosen.isBlank()) ? "scooby_doo" : chosen;
    }

    private static String chooseSceneForPersonality(String personality) {
        String[] scenes = switch (personality) {
            case "scooby_doo" -> new String[] {
                "Night of Fright Is No Delight: Scooby-Doo may inherit part of Colonel Beauregard Sanders' fortune, so the gang travels to a creepy mansion on Mystery Island. They meet the other heirs, including Cosgood Creeps, Cousin Simple, Nora, and Cousin Slicker. Key scenes include the reading of the will, secret passages, disappearing heirs, and glowing green Phantom Shadows chasing everyone through the house. Main characters: Scooby-Doo, Shaggy Rogers, Fred Jones, Daphne Blake, Velma Dinkley, Colonel Sanders' heirs, and the Phantom Shadows.",
                "Jeepers, It's the Creeper: On the way to a barn dance, the gang finds bank guard Mr. Carswell tied up after a robbery. The case leads them to a spooky old barn and the terrifying green-faced monster known as the Creeper. Key scenes include the blackout at the dance, the Creeper stalking the gang through the barn, and Shaggy and Scooby trying to escape while still getting dragged back into the mystery. Main characters: Scooby-Doo, Shaggy Rogers, Fred Jones, Daphne Blake, Velma Dinkley, Mr. Carswell, and the Creeper.",
                "Spooky Space Kook: After the Mystery Machine runs low on gas, the gang ends up near an abandoned airfield where they hear stories about a ghostly astronaut called the Space Kook. They meet local figures including Henry Bascomb and investigate the deserted airport and eerie hangars. Key scenes include the glowing red Space Kook laugh, flying saucer appearances, and nighttime chases across the airstrip. Main characters: Scooby-Doo, Shaggy Rogers, Fred Jones, Daphne Blake, Velma Dinkley, Henry Bascomb, and the Space Kook.",
                "Foul Play in Funland: The gang visits an amusement park and runs into trouble when a dangerous robot named Charlie begins attacking people. They work with park owner Mr. Jenkins and his daughter Sarah Jenkins to figure out what is going on. Key scenes include Charlie chasing the gang through the empty park at night, creepy rides, and the reveal that this mystery is more mechanical than supernatural. Main characters: Scooby-Doo, Shaggy Rogers, Fred Jones, Daphne Blake, Velma Dinkley, Mr. Jenkins, Sarah Jenkins, and Charlie the Robot."
            };
            case "er_tv_series" -> new String[] {
                "Dr. Greene Treats a Critical Trauma Case in the ER: In ER, Dr. Mark Greene leads the trauma team during a chaotic emergency arrival at County General. Characters involved include Dr. Mark Greene, Nurse Carol Hathaway, Dr. Susan Lewis, and Dr. Peter Benton. Key scenes include paramedics rushing in a badly injured patient, Greene calling out treatment orders, Carol managing airways and meds, and the team trying to stabilize the patient while family members wait anxiously nearby.",
                "Doug Ross Breaks the Rules for a Child Patient: Dr. Doug Ross becomes emotionally involved in a pediatric case and pushes past hospital rules because he believes it is the right thing to do for the child. Characters involved include Dr. Doug Ross, Nurse Carol Hathaway, and the child's parents. Key scenes include Doug arguing with other staff over treatment decisions, Carol warning him about the consequences, and Doug choosing compassion over protocol even though it may cost him professionally.",
                "Dr. Carter's Early Days in the ER: John Carter starts out as a young medical student and is thrown into the fast pace of County General under the supervision of Dr. Peter Benton. Characters involved include John Carter, Dr. Peter Benton, Dr. Mark Greene, and various ER patients. Key scenes include Carter fumbling basic tasks, Benton sharply correcting him, Carter learning how quickly lives can change in the trauma room, and his gradual shift from nervous student to determined doctor.",
                "Carol Hathaway Handles a Mass-Casualty Emergency: A major emergency floods County General with injured patients, and Carol Hathaway becomes one of the key people holding the ER together. Characters involved include Carol Hathaway, Mark Greene, Susan Lewis, Peter Benton, and multiple trauma patients. Key scenes include packed hallways, triage decisions, nurses and doctors splitting resources, and Carol balancing medical care with emotional support for frightened patients and families."
            };
            case "lord_of_the_rings" -> new String[] {
                "The Council of Elrond (Rivendell): In The Lord of the Rings: The Fellowship of the Ring, representatives of Middle-earth gather in Rivendell under Elrond to decide the fate of the One Ring. Present are Frodo Baggins, Gandalf, Aragorn, Legolas, Gimli, and Boromir. Key scenes include Gimli attempting to smash the Ring, Boromir arguing to use it against Sauron, and Frodo stepping forward with 'I will take the Ring.' This leads to the formation of the Fellowship.",
                "The Mines of Moria (Khazad-dum): The Fellowship travels through the ancient dwarf kingdom of Moria, once ruled by Durin. Inside, they discover the tomb of Balin and are attacked by orcs, a cave troll, and eventually the terrifying Balrog. Key scenes include the collapsing chamber battle, Gandalf reading Balin's final record, and the iconic moment on the Bridge of Khazad-dum where Gandalf confronts the Balrog: 'You shall not pass!'.",
                "The Battle of Helm's Deep: In The Lord of the Rings: The Two Towers, the people of Rohan defend Helm's Deep against Saruman's army of Uruk-hai, led by Lurtz's forces (film continuity inspiration). Key characters include King Theoden, Aragorn, Legolas, and Gimli. Key scenes include the explosive breach of the Deeping Wall, the desperate defense in the keep, and the dawn charge led by Theoden and Aragorn as Gandalf arrives with the Rohirrim.",
                "Shelob's Lair (Cirith Ungol): In The Lord of the Rings: The Return of the King, Frodo Baggins and Samwise Gamgee are led by Gollum into the dark tunnels of Cirith Ungol. They encounter Shelob, an ancient monstrous spider. Key scenes include Frodo being trapped and stung by Shelob, Sam returning to fight her using the Phial of Galadriel and Sting, and Sam believing Frodo is dead before the orcs carry him away."
            };
            case "pirates_of_the_caribbean" -> new String[] {
                "Port Royal Arrival & Jack's Entrance: In Pirates of the Caribbean: The Curse of the Black Pearl, Captain Jack Sparrow sails into Port Royal on a sinking boat and steps onto the dock just as it goes under. He encounters Elizabeth Swann, Will Turner, and Commodore James Norrington. Key scenes include Elizabeth falling into the water (triggering the Aztec curse medallion), Jack rescuing her, and Jack's swordfight with Will inside the blacksmith shop.",
                "Attack on Port Royal & Elizabeth's Kidnapping: The cursed crew of the Black Pearl, led by Captain Hector Barbossa, attacks Port Royal at night. The pirates reveal their skeletal forms under moonlight due to the Aztec curse. Key scenes include cannon fire lighting up the harbor, townspeople fleeing, and Barbossa confronting Elizabeth, who invokes 'parley' before being taken aboard the Black Pearl.",
                "Isla de Muerta - The Curse Revealed: Jack Sparrow and Will Turner reach Isla de Muerta, where Barbossa's crew hoards cursed Aztec gold. Characters include Jack Sparrow, Will Turner, Barbossa, and the cursed crew. Key scenes include the reveal that the pirates cannot die, their skeletal transformations in moonlight, Jack confronting Barbossa in the treasure cave, and the explanation of the curse tied to the stolen gold and blood debt.",
                "Maelstrom Battle - Jack vs Davy Jones: In Pirates of the Caribbean: At World's End, a massive sea battle takes place inside a maelstrom between the Black Pearl and the Flying Dutchman, commanded by Davy Jones. Key characters include Jack Sparrow, Will Turner, Elizabeth Swann, Hector Barbossa, and Davy Jones. Key scenes include ships circling the whirlpool, sword fights across decks, Will being mortally wounded, and his heart being placed into the Dead Man's Chest, binding him to the Flying Dutchman."
            };
            default -> new String[] {
                "Village life continues with a mysterious event unfolding."
            };
        };

        int idx = ThreadLocalRandom.current().nextInt(scenes.length);
        return scenes[idx];
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
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    // Backward compatible format support:
                    // old: gridX,gridZ=personality
                    // new: gridX,gridZ=personality|scene text
                    String[] valueParts = value.split("\\|", 2);
                    String personality = valueParts[0].trim();
                    if (personality.isBlank()) {
                        personality = "scooby_doo";
                    }
                    String scene;
                    if (valueParts.length > 1 && !valueParts[1].trim().isBlank()) {
                        scene = valueParts[1].trim();
                    } else {
                        scene = chooseSceneForPersonality(personality);
                    }

                    savedPersonalities.put(key, new VillageAssignment(personality, scene));
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
            sb.append("# Village personalities and scenes (locked after first assignment)\n");
            sb.append("# Format: gridX,gridZ=personality|scene\n");
            for (Map.Entry<String, VillageAssignment> entry : savedPersonalities.entrySet()) {
                VillageAssignment assignment = entry.getValue();
                if (assignment == null) continue;
                String personality = (assignment.personality == null || assignment.personality.isBlank())
                    ? "scooby_doo"
                    : assignment.personality;
                String scene = assignment.scene == null ? "" : assignment.scene;
                sb.append(entry.getKey())
                    .append("=")
                    .append(personality)
                    .append("|")
                    .append(scene)
                    .append("\n");
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