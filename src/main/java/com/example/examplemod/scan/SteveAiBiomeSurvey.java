package com.example.examplemod.scan;

import com.example.examplemod.SteveAiContextFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SteveAiBiomeSurvey {

    private static final String VANILLA_POIS_JSON_RESOURCE_PATH =
        "data/examplemod/reference/vanilla_structures_pois.json";

    private static volatile List<PoiBiomeHint> cachedPoiBiomeHints;

    private SteveAiBiomeSurvey() {}

    public static final class BiomeArea {
        public final String biomeId;
        public final int minX;
        public final int maxX;
        public final int minY;
        public final int maxY;
        public final int minZ;
        public final int maxZ;
        public final int centerX;
        public final int centerY;
        public final int centerZ;
        public final int sampleCount;
        public final int evidenceScore;
        public final List<String> evidenceSources;

        private BiomeArea(
            String biomeId,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            int sampleCount,
            int evidenceScore,
            List<String> evidenceSources
        ) {
            this.biomeId = biomeId;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.centerX = (minX + maxX) / 2;
            this.centerY = (minY + maxY) / 2;
            this.centerZ = (minZ + maxZ) / 2;
            this.sampleCount = sampleCount;
            this.evidenceScore = evidenceScore;
            this.evidenceSources = evidenceSources;
        }
    }

    public static final class EvidenceOnlyBiome {
        public final String biomeId;
        public final int evidenceScore;
        public final List<String> evidenceSources;

        private EvidenceOnlyBiome(String biomeId, int evidenceScore, List<String> evidenceSources) {
            this.biomeId = biomeId;
            this.evidenceScore = evidenceScore;
            this.evidenceSources = evidenceSources;
        }
    }

    public static final class BiomeSurveyResult {
        public final BlockPos center;
        public final BlockPos playerPos;
        public final BlockPos stevePos;
        public final long elapsedMs;
        public final int chunkRadius;
        public final int sampleStep;
        public final boolean forceLoad;
        public final int sampleY;
        public final int loadedChunkCount;
        public final int sampledCellCount;
        public final String centerBiomeId;
        public final String playerBiomeId;
        public final String steveBiomeId;
        public final Set<String> playerTouchingBiomes;
        public final Set<String> steveTouchingBiomes;
        public final List<BiomeArea> areas;
        public final List<EvidenceOnlyBiome> evidenceOnlyBiomes;

        private BiomeSurveyResult(
            BlockPos center,
            BlockPos playerPos,
            BlockPos stevePos,
            long elapsedMs,
            int chunkRadius,
            int sampleStep,
            boolean forceLoad,
            int sampleY,
            int loadedChunkCount,
            int sampledCellCount,
            String centerBiomeId,
            String playerBiomeId,
            String steveBiomeId,
            Set<String> playerTouchingBiomes,
            Set<String> steveTouchingBiomes,
            List<BiomeArea> areas,
            List<EvidenceOnlyBiome> evidenceOnlyBiomes
        ) {
            this.center = center;
            this.playerPos = playerPos;
            this.stevePos = stevePos;
            this.elapsedMs = elapsedMs;
            this.chunkRadius = chunkRadius;
            this.sampleStep = sampleStep;
            this.forceLoad = forceLoad;
            this.sampleY = sampleY;
            this.loadedChunkCount = loadedChunkCount;
            this.sampledCellCount = sampledCellCount;
            this.centerBiomeId = centerBiomeId;
            this.playerBiomeId = playerBiomeId;
            this.steveBiomeId = steveBiomeId;
            this.playerTouchingBiomes = playerTouchingBiomes;
            this.steveTouchingBiomes = steveTouchingBiomes;
            this.areas = areas;
            this.evidenceOnlyBiomes = evidenceOnlyBiomes;
        }
    }

    public static final class LocateBiomeResult {
        public final boolean found;
        public final String queryBiomeId;
        public final String foundBiomeId;
        public final BlockPos origin;
        public final BlockPos foundPos;
        public final long elapsedMs;
        public final int radius;
        public final int horizontalInterval;
        public final int verticalInterval;
        public final int manhattanDistance;
        public final String message;

        private LocateBiomeResult(
            boolean found,
            String queryBiomeId,
            String foundBiomeId,
            BlockPos origin,
            BlockPos foundPos,
            long elapsedMs,
            int radius,
            int horizontalInterval,
            int verticalInterval,
            int manhattanDistance,
            String message
        ) {
            this.found = found;
            this.queryBiomeId = queryBiomeId;
            this.foundBiomeId = foundBiomeId;
            this.origin = origin;
            this.foundPos = foundPos;
            this.elapsedMs = elapsedMs;
            this.radius = radius;
            this.horizontalInterval = horizontalInterval;
            this.verticalInterval = verticalInterval;
            this.manhattanDistance = manhattanDistance;
            this.message = message;
        }
    }

    private static final class BiomeAreaAccumulator {
        private final String biomeId;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private int sampleCount = 0;
        private int evidenceScore = 0;
        private final LinkedHashSet<String> evidenceSources = new LinkedHashSet<>();

        private BiomeAreaAccumulator(String biomeId) {
            this.biomeId = biomeId;
        }

        private void includeCell(int x, int y, int z, int sampleStep) {
            int half = Math.max(2, sampleStep / 2);
            minX = Math.min(minX, x - half);
            maxX = Math.max(maxX, x + half);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z - half);
            maxZ = Math.max(maxZ, z + half);
            sampleCount++;
        }

        private void addEvidence(int score, String source) {
            evidenceScore += Math.max(0, score);
            if (source != null && !source.isBlank()) {
                evidenceSources.add(source);
            }
        }

        private BiomeArea toArea() {
            return new BiomeArea(
                biomeId,
                minX,
                maxX,
                minY == Integer.MAX_VALUE ? 0 : minY,
                maxY == Integer.MIN_VALUE ? 0 : maxY,
                minZ,
                maxZ,
                sampleCount,
                evidenceScore,
                new ArrayList<>(evidenceSources)
            );
        }
    }

    private static final class PoiBiomeHint {
        private final String id;
        private final Set<String> biomeTokens;
        private final Set<String> entities;
        private final Set<String> blockEntities;
        private final Set<String> blocks;

        private PoiBiomeHint(
            String id,
            Set<String> biomeTokens,
            Set<String> entities,
            Set<String> blockEntities,
            Set<String> blocks
        ) {
            this.id = id;
            this.biomeTokens = biomeTokens;
            this.entities = entities;
            this.blockEntities = blockEntities;
            this.blocks = blocks;
        }
    }

    public static String getBiomeId(ServerLevel serverLevel, BlockPos pos) {
        if (serverLevel == null || pos == null) {
            return "unknown";
        }

        Holder<Biome> biomeHolder = serverLevel.getBiome(pos);
        Identifier key = serverLevel.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .getKey(biomeHolder.value());
        return key == null ? "unknown" : key.toString();
    }

    public static BiomeSurveyResult surveyAround(
        ServerLevel serverLevel,
        BlockPos center,
        BlockPos playerPos,
        BlockPos stevePos,
        int chunkRadius,
        int sampleStep,
        boolean forceLoad
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (center == null) {
            throw new IllegalArgumentException("center is null");
        }
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("chunkRadius must be >= 0");
        }
        long startedNs = System.nanoTime();

        int effectiveSampleStep = Math.max(4, sampleStep);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int minChunkX = centerChunkX - chunkRadius;
        int maxChunkX = centerChunkX + chunkRadius;
        int minChunkZ = centerChunkZ - chunkRadius;
        int maxChunkZ = centerChunkZ + chunkRadius;
        int minX = minChunkX << 4;
        int maxX = (maxChunkX << 4) + 15;
        int minZ = minChunkZ << 4;
        int maxZ = (maxChunkZ << 4) + 15;
        int sampleY = Math.max(serverLevel.getMinY(), Math.min(serverLevel.getMaxY(), center.getY()));

        Map<String, BiomeAreaAccumulator> areaAccumulators = new LinkedHashMap<>();
        Map<Long, LevelChunk> chunkCache = new HashMap<>();
        Set<Long> loadedChunkKeys = new LinkedHashSet<>();
        Map<Long, String> sampledCells = new LinkedHashMap<>();

        for (int z = minZ; z <= maxZ; z += effectiveSampleStep) {
            for (int x = minX; x <= maxX; x += effectiveSampleStep) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = chunkKey(chunkX, chunkZ);
                LevelChunk chunk = chunkCache.get(chunkKey);
                if (!chunkCache.containsKey(chunkKey)) {
                    chunk = forceLoad
                        ? serverLevel.getChunk(chunkX, chunkZ)
                        : serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                    chunkCache.put(chunkKey, chunk);
                }
                if (chunk == null) {
                    continue;
                }

                loadedChunkKeys.add(chunkKey);
                int sampleX = Math.min(x, maxX);
                int sampleZ = Math.min(z, maxZ);
                String biomeId = getBiomeId(serverLevel, new BlockPos(sampleX, sampleY, sampleZ));
                areaAccumulators
                    .computeIfAbsent(biomeId, BiomeAreaAccumulator::new)
                    .includeCell(sampleX, sampleY, sampleZ, effectiveSampleStep);
                sampledCells.put(gridKey(sampleX, sampleZ), biomeId);
            }
        }

        String centerBiomeId = getBiomeId(serverLevel, center);
        String playerBiomeId = playerPos == null ? centerBiomeId : getBiomeId(serverLevel, playerPos);
        String steveBiomeId = stevePos == null ? "unknown" : getBiomeId(serverLevel, stevePos);

        Map<String, Set<String>> adjacency = buildBiomeAdjacency(sampledCells, effectiveSampleStep);
        applyPoiEvidence(serverLevel, areaAccumulators);

        List<BiomeArea> areas = areaAccumulators.values().stream()
            .map(BiomeAreaAccumulator::toArea)
            .sorted(
                Comparator
                    .comparingInt((BiomeArea area) -> distanceSq(area.centerX, area.centerZ, center.getX(), center.getZ()))
                    .thenComparing(Comparator.comparingInt((BiomeArea area) -> area.sampleCount).reversed())
                    .thenComparing(area -> area.biomeId)
            )
            .toList();

        List<EvidenceOnlyBiome> evidenceOnlyBiomes = buildEvidenceOnlyBiomes(areaAccumulators);
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L;

        return new BiomeSurveyResult(
            center.immutable(),
            playerPos == null ? null : playerPos.immutable(),
            stevePos == null ? null : stevePos.immutable(),
            elapsedMs,
            chunkRadius,
            effectiveSampleStep,
            forceLoad,
            sampleY,
            loadedChunkKeys.size(),
            sampledCells.size(),
            centerBiomeId,
            playerBiomeId,
            steveBiomeId,
            new LinkedHashSet<>(adjacency.getOrDefault(playerBiomeId, Set.of())),
            new LinkedHashSet<>(adjacency.getOrDefault(steveBiomeId, Set.of())),
            areas,
            evidenceOnlyBiomes
        );
    }

    public static LocateBiomeResult locateNearestBiome(
        ServerLevel serverLevel,
        BlockPos origin,
        String biomeQuery,
        int radius,
        int horizontalInterval,
        int verticalInterval
    ) {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin is null");
        }
        long startedNs = System.nanoTime();

        String targetBiomeId = normalizeBiomeToken(biomeQuery);
        if (!isSpecificBiomeId(targetBiomeId)) {
            return new LocateBiomeResult(
                false,
                targetBiomeId,
                "",
                origin.immutable(),
                null,
                (System.nanoTime() - startedNs) / 1_000_000L,
                radius,
                horizontalInterval,
                verticalInterval,
                -1,
                "Biome query must resolve to a specific biome id, e.g. plains or minecraft:plains."
            );
        }

        Climate.Sampler sampler = serverLevel.getChunkSource().randomState().sampler();
        Pair<BlockPos, Holder<Biome>> found = serverLevel.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .findClosestBiome3d(
                origin,
                radius,
                Math.max(4, horizontalInterval),
                Math.max(4, verticalInterval),
                holder -> targetBiomeId.equals(biomeIdFromHolder(serverLevel, holder)),
                sampler,
                serverLevel
            );

        if (found == null) {
            return new LocateBiomeResult(
                false,
                targetBiomeId,
                "",
                origin.immutable(),
                null,
                (System.nanoTime() - startedNs) / 1_000_000L,
                radius,
                horizontalInterval,
                verticalInterval,
                -1,
                "No biome match found within radius=" + radius + "."
            );
        }

        BlockPos foundPos = found.getFirst();
        String foundBiomeId = biomeIdFromHolder(serverLevel, found.getSecond());
        int distance = Math.abs(foundPos.getX() - origin.getX())
            + Math.abs(foundPos.getY() - origin.getY())
            + Math.abs(foundPos.getZ() - origin.getZ());
        return new LocateBiomeResult(
            true,
            targetBiomeId,
            foundBiomeId,
            origin.immutable(),
            foundPos.immutable(),
            (System.nanoTime() - startedNs) / 1_000_000L,
            radius,
            horizontalInterval,
            verticalInterval,
            distance,
            "Found " + foundBiomeId + " at " + foundPos.toShortString() + "."
        );
    }

    public static Path writeSurveyReport(ServerLevel serverLevel, String prefix, BiomeSurveyResult result) throws IOException {
        Path dir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
        Path outFile = dir.resolve(prefix + "_" + serverLevel.getGameTime() + ".txt");

        StringBuilder text = new StringBuilder();
        text.append("BIOME SURVEY\n");
        text.append("center=").append(result.center.toShortString())
            .append(" chunkRadius=").append(result.chunkRadius)
            .append(" sampleStep=").append(result.sampleStep)
            .append(" forceLoad=").append(result.forceLoad)
            .append(" sampleY=").append(result.sampleY)
            .append(" elapsedMs=").append(result.elapsedMs).append("\n");
        text.append("centerBiome=").append(result.centerBiomeId).append("\n");
        if (result.playerPos != null) {
            text.append("player=").append(result.playerPos.toShortString())
                .append(" biome=").append(result.playerBiomeId).append("\n");
            text.append("playerTouching=").append(joinSet(result.playerTouchingBiomes)).append("\n");
        }
        if (result.stevePos != null) {
            text.append("steveAI=").append(result.stevePos.toShortString())
                .append(" biome=").append(result.steveBiomeId).append("\n");
            text.append("steveTouching=").append(joinSet(result.steveTouchingBiomes)).append("\n");
        }
        text.append("loadedChunks=").append(result.loadedChunkCount)
            .append(" sampledCells=").append(result.sampledCellCount)
            .append(" areas=").append(result.areas.size())
            .append(" evidenceOnlyBiomes=").append(result.evidenceOnlyBiomes.size())
            .append("\n\n");

        text.append("nearbyBiomeAreas\n");
        for (BiomeArea area : result.areas) {
            text.append("biome=").append(area.biomeId)
                .append(" bbox=")
                .append(area.minX).append(",").append(area.minY).append(",").append(area.minZ)
                .append(" -> ")
                .append(area.maxX).append(",").append(area.maxY).append(",").append(area.maxZ)
                .append(" center=")
                .append(area.centerX).append(",").append(area.centerY).append(",").append(area.centerZ)
                .append(" samples=").append(area.sampleCount)
                .append(" evidenceScore=").append(area.evidenceScore);
            if (!area.evidenceSources.isEmpty()) {
                text.append(" evidence=").append(String.join(" | ", area.evidenceSources));
            }
            text.append("\n");
        }

        if (!result.evidenceOnlyBiomes.isEmpty()) {
            text.append("\nevidenceOnlyBiomes\n");
            for (EvidenceOnlyBiome biome : result.evidenceOnlyBiomes) {
                text.append("biome=").append(biome.biomeId)
                    .append(" evidenceScore=").append(biome.evidenceScore)
                    .append(" evidence=").append(String.join(" | ", biome.evidenceSources))
                    .append("\n");
            }
        }

        Files.writeString(
            outFile,
            text.toString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        return outFile;
    }

    private static List<EvidenceOnlyBiome> buildEvidenceOnlyBiomes(Map<String, BiomeAreaAccumulator> areaAccumulators) {
        List<EvidenceOnlyBiome> out = new ArrayList<>();
        for (BiomeAreaAccumulator accumulator : areaAccumulators.values()) {
            if (accumulator.sampleCount > 0 || accumulator.evidenceScore <= 0) {
                continue;
            }
            out.add(new EvidenceOnlyBiome(
                accumulator.biomeId,
                accumulator.evidenceScore,
                new ArrayList<>(accumulator.evidenceSources)
            ));
        }
        out.sort(
            Comparator
                .comparingInt((EvidenceOnlyBiome biome) -> biome.evidenceScore)
                .reversed()
                .thenComparing(biome -> biome.biomeId)
        );
        return out;
    }

    private static Map<String, Set<String>> buildBiomeAdjacency(Map<Long, String> sampledCells, int sampleStep) {
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : sampledCells.entrySet()) {
            long key = entry.getKey();
            String biome = entry.getValue();
            for (long neighborKey : List.of(key + sampleStep, key + (sampleStep << 32))) {
                String neighborBiome = sampledCells.get(neighborKey);
                if (neighborBiome == null || biome.equals(neighborBiome)) {
                    continue;
                }
                adjacency.computeIfAbsent(biome, ignored -> new LinkedHashSet<>()).add(neighborBiome);
                adjacency.computeIfAbsent(neighborBiome, ignored -> new LinkedHashSet<>()).add(biome);
            }
        }
        return adjacency;
    }

    private static void applyPoiEvidence(ServerLevel serverLevel, Map<String, BiomeAreaAccumulator> areaAccumulators) {
        Map<String, SteveAiCollectors.SeenSummary> scannedBlocks = SteveAiScanManager.getScannedBlocks();
        Map<String, SteveAiCollectors.SeenSummary> scannedEntities = SteveAiScanManager.getScannedEntities();
        Map<String, SteveAiCollectors.SeenSummary> scannedBlockEntities = SteveAiScanManager.getScannedBlockEntities();

        Set<String> blockKeys = lowerKeySet(scannedBlocks == null ? Map.of() : scannedBlocks);
        Set<String> entityKeys = lowerKeySet(scannedEntities == null ? Map.of() : scannedEntities);
        Set<String> blockEntityKeys = lowerKeySet(scannedBlockEntities == null ? Map.of() : scannedBlockEntities);

        for (PoiBiomeHint hint : loadPoiBiomeHints()) {
            int entityHits = overlapCount(hint.entities, entityKeys);
            int blockEntityHits = overlapCount(hint.blockEntities, blockEntityKeys);
            int blockHits = overlapCount(hint.blocks, blockKeys);
            int score = (entityHits * 4) + (blockEntityHits * 3) + blockHits;
            if (score <= 0) {
                continue;
            }

            String source = hint.id + " hits[e=" + entityHits + ",be=" + blockEntityHits + ",b=" + blockHits + "]";
            for (String biomeToken : hint.biomeTokens) {
                String normalized = normalizeBiomeToken(biomeToken);
                if (!isSpecificBiomeId(normalized)) {
                    continue;
                }
                areaAccumulators
                    .computeIfAbsent(normalized, BiomeAreaAccumulator::new)
                    .addEvidence(score, source);
            }
        }
    }

    private static List<PoiBiomeHint> loadPoiBiomeHints() {
        if (cachedPoiBiomeHints != null) {
            return cachedPoiBiomeHints;
        }

        List<PoiBiomeHint> hints = new ArrayList<>();
        try (InputStream input = SteveAiBiomeSurvey.class.getClassLoader().getResourceAsStream(VANILLA_POIS_JSON_RESOURCE_PATH)) {
            Reader reader;
            if (input != null) {
                reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            } else {
                Path fallback = Path.of("src/main/resources/" + VANILLA_POIS_JSON_RESOURCE_PATH);
                reader = Files.newBufferedReader(fallback, StandardCharsets.UTF_8);
            }

            try (reader) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (String arrayKey : new String[]{"structures", "pois"}) {
                    if (!root.has(arrayKey) || !root.get(arrayKey).isJsonArray()) {
                        continue;
                    }
                    JsonArray arr = root.getAsJsonArray(arrayKey);
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) {
                            continue;
                        }
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("id") ? obj.get("id").getAsString() : "unknown";
                        String biomeField = obj.has("biomes") ? "biomes" : (obj.has("natural_biomes") ? "natural_biomes" : null);

                        hints.add(new PoiBiomeHint(
                            id,
                            readLowerSet(obj, biomeField),
                            readLowerSet(obj, "entities"),
                            readLowerSet(obj, "block_entities"),
                            readLowerSet(obj, "blocks")
                        ));
                    }
                }
            }
        } catch (IOException e) {
            cachedPoiBiomeHints = List.of();
            return cachedPoiBiomeHints;
        }

        cachedPoiBiomeHints = Collections.unmodifiableList(hints);
        return cachedPoiBiomeHints;
    }

    private static Set<String> readLowerSet(JsonObject obj, String fieldName) {
        if (fieldName == null || !obj.has(fieldName) || !obj.get(fieldName).isJsonArray()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonElement entry : obj.getAsJsonArray(fieldName)) {
            out.add(entry.getAsString().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static Set<String> lowerKeySet(Map<String, ?> map) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String key : map.keySet()) {
            if (key != null) {
                out.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static int overlapCount(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String value : left) {
            if (right.contains(value)) {
                count++;
            }
        }
        return count;
    }

    private static String biomeIdFromHolder(ServerLevel serverLevel, Holder<Biome> holder) {
        if (serverLevel == null || holder == null) {
            return "unknown";
        }
        Identifier key = serverLevel.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .getKey(holder.value());
        return key == null ? "unknown" : key.toString();
    }

    private static boolean isSpecificBiomeId(String token) {
        return token != null
            && !token.isBlank()
            && !token.startsWith("any_")
            && !token.startsWith("all_")
            && !"unknown".equals(token);
    }

    private static String normalizeBiomeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return token.contains(":") || token.startsWith("any_") || token.startsWith("all_")
            ? token
            : "minecraft:" + token;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static long gridKey(int x, int z) {
        return (((long) z) << 32) ^ (x & 0xffffffffL);
    }

    private static int distanceSq(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2;
        int dz = z1 - z2;
        return (dx * dx) + (dz * dz);
    }

    private static String joinSet(Collection<String> values) {
        return values == null || values.isEmpty() ? "(none)" : String.join(", ", values);
    }
}