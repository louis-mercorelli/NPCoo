/**
 * File: SteveAiContextFiles.java
 *
 * Main intent:
 * Defines SteveAiContextFiles functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code getSteveAiDataDir(...)}:
 *    Purpose: Ensures the SteveAI per-world data directory exists and returns its path.
 *    Input: ServerLevel serverLevel.
 *    Output: Path.
 * 2) {@code buildChatContext(...)}:
 *    Purpose: Builds the OpenAI prompt context from live POI data plus the latest chat and scan files.
 *    Input: ServerLevel serverLevel, UUID playerUuid, int tailLines.
 *    Output: String.
 * 3) {@code appendChatLine(...)}:
 *    Purpose: Appends one line to the player's SteveAI chat transcript file.
 *    Input: ServerLevel serverLevel, UUID playerUuid, String line.
 *    Output: void.
 * 4) {@code readWholeFile(...)}:
 *    Purpose: Reads an entire file and returns an empty string when the file is missing.
 *    Input: Path file.
 *    Output: String.
 * 5) {@code buildLivePoiSummaryText(...)}:
 *    Purpose: Builds a text snapshot of the current in-memory POI summary.
 *    Input: none.
 *    Output: String.
 * 6) {@code startChatSession(...)}:
 *    Purpose: Captures a POI summary snapshot for a player when a chat session begins.
 *    Input: ServerLevel serverLevel, UUID playerUuid.
 *    Output: void.
 * 7) {@code endChatSession(...)}:
 *    Purpose: Removes the cached POI snapshot for a player when a chat session ends.
 *    Input: UUID playerUuid.
 *    Output: void.
 * 8) {@code findLatestMatchingFile(...)}:
 *    Purpose: Finds the most recently modified file in a directory that matches a glob pattern.
 *    Input: Path dir, String globPattern.
 *    Output: Path.
 * 9) {@code logFileTail(...)}:
 *    Purpose: Logs the tail of a file for debugging when that file is present.
 *    Input: String logPrefix, Path file, int maxLines.
 *    Output: void.
 * 10) {@code readLastNLines(...)}:
 *    Purpose: Reads the last N lines of a file for chat-context assembly.
 *    Input: Path file, int maxLines.
 *    Output: String.
 */
package com.example.examplemod;

import com.mojang.logging.LogUtils;
import com.example.examplemod.poi.PoiManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SteveAiContextFiles {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    private static final Map<UUID, String> chatSessionPoiSummary = new ConcurrentHashMap<>();

    public static Path getSteveAiDataDir(ServerLevel serverLevel) throws IOException {
        if (serverLevel == null) {
            throw new IllegalArgumentException("serverLevel is null");
        }

        Path dir = serverLevel.getServer()
            .getWorldPath(LevelResource.ROOT)
            .resolve("testmod")
            .resolve("player");

        Files.createDirectories(dir);
        return dir;
    }

    public static String buildChatContext(ServerLevel serverLevel, UUID playerUuid, int tailLines) {
        return buildChatContext(serverLevel, playerUuid, tailLines, true, true);
    }

    public static String buildQuickChatContext(ServerLevel serverLevel, UUID playerUuid, int tailLines) {
        return buildChatContext(serverLevel, playerUuid, tailLines, false, false);
    }

    private static String buildChatContext(ServerLevel serverLevel, UUID playerUuid, int tailLines,
                                           boolean includeChatFile, boolean includeDetailFiles) {
        if (serverLevel == null) {
            return "No server level available. Context files could not be loaded.";
        }

        try {
            LOGGER.info(com.sai.NpcooLog.tag("[OPENAI DEBUG] buildChatContext start playerUuid={} tailLines={}"), playerUuid, tailLines);

            Path playerDataDir = getSteveAiDataDir(serverLevel);

            Path rawFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI.txt");
            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");
            Path poiSummaryFile = findLatestMatchingFile(playerDataDir, "poiSummary*.txt");
            Path scannedBlocksFile = findLatestMatchingFile(playerDataDir, "scannedBlocks*.txt");
            Path scannedEntitiesFile = findLatestMatchingFile(playerDataDir, "scannedEntities*.txt");
            Path scannedBlockEntitiesFile = findLatestMatchingFile(playerDataDir, "scannedBlockEntities*.txt");
            Path detailBlockEntitiesFile = findLatestMatchingFile(playerDataDir, "detailBlockEntities*.txt");
            Path detailEntitiesFile = findLatestMatchingFile(playerDataDir, "detailEntities*.txt");
            Path detailStatusFile = findLatestMatchingFile(playerDataDir, "detailStatus*.txt");
            Path poiFindFile = findLatestMatchingFile(playerDataDir, "POIfind_*.txt");

            logFileTail("OPENAI DEBUG", poiSummaryFile, 10);
            logFileTail("OPENAI DEBUG", scannedBlocksFile, 10);
            logFileTail("OPENAI DEBUG", scannedEntitiesFile, 10);
            logFileTail("OPENAI DEBUG", scannedBlockEntitiesFile, 10);
            logFileTail("OPENAI DEBUG", rawFile, 10);
            logFileTail("OPENAI DEBUG", chatFile, 10);
            logFileTail("OPENAI DEBUG", detailBlockEntitiesFile, 10);
            logFileTail("OPENAI DEBUG", detailEntitiesFile, 10);
            logFileTail("OPENAI DEBUG", detailStatusFile, 10);
            logFileTail("OPENAI DEBUG", poiFindFile, 10);

            String poiSummaryText = readWholeFile(poiSummaryFile);
            String scannedBlocksText = readWholeFile(scannedBlocksFile);
            String scannedEntitiesText = readWholeFile(scannedEntitiesFile);
            String scannedBlockEntitiesText = readWholeFile(scannedBlockEntitiesFile);
            String sessionPoiSummaryText = (playerUuid == null) ? "" : chatSessionPoiSummary.getOrDefault(playerUuid, "");
            String livePoiSummaryText = buildLivePoiSummaryText();
            String rawTailText = readLastNLines(rawFile, tailLines);
            String detailBlockEntitiesText = readWholeFile(detailBlockEntitiesFile);
            String detailEntitiesText = readWholeFile(detailEntitiesFile);
            String detailStatusText = readWholeFile(detailStatusFile);
            String poiFindText = readWholeFile(poiFindFile);

            StringBuilder sb = new StringBuilder();

            sb.append("=== SteveAI POINTS OF INTEREST Summary (live) ===\n");
            if (!sessionPoiSummaryText.isBlank()) {
                sb.append(sessionPoiSummaryText).append("\n");
            } else if (!livePoiSummaryText.isBlank()) {
                sb.append(livePoiSummaryText).append("\n");
            } else {
                sb.append(poiSummaryText.isBlank() ? "(POI summary file empty or missing)\n" : poiSummaryText).append("\n");
            }

            sb.append("=== SteveAI Raw File (last ").append(tailLines).append(" lines) ===\n");
            sb.append(rawTailText.isBlank() ? "(raw file empty or missing)\n" : rawTailText).append("\n");

            sb.append("=== SteveAI Scanned Blocks Summary ===\n");
            sb.append(scannedBlocksText.isBlank() ? "(latest scannedBlocks*.txt empty or missing)\n" : scannedBlocksText).append("\n");

            sb.append("=== SteveAI Scanned Entities Summary ===\n");
            sb.append(scannedEntitiesText.isBlank() ? "(latest scannedEntities*.txt empty or missing)\n" : scannedEntitiesText).append("\n");

            sb.append("=== SteveAI Scanned Block Entities Summary ===\n");
            sb.append(scannedBlockEntitiesText.isBlank() ? "(latest scannedBlockEntities*.txt empty or missing)\n" : scannedBlockEntitiesText).append("\n");

            if (includeChatFile) {
                sb.append("=== SteveAI Chat File ===\n");
                sb.append(chatFile == null ? "(chat file empty or missing)\n" : readWholeFile(chatFile)).append("\n");
            }

            if (includeDetailFiles) {
                sb.append("=== SteveAI Detail Block Entities ===\n");
                sb.append(detailBlockEntitiesText.isBlank() ? "(latest detailBlockEntities*.txt empty or missing)\n" : detailBlockEntitiesText).append("\n");

                sb.append("=== SteveAI Detail Entities ===\n");
                sb.append(detailEntitiesText.isBlank() ? "(latest detailEntities*.txt empty or missing)\n" : detailEntitiesText).append("\n");

                sb.append("=== SteveAI Detail Status ===\n");
                sb.append(detailStatusText.isBlank() ? "(latest detailStatus*.txt empty or missing)\n" : detailStatusText).append("\n");
            }

            sb.append("=== SteveAI POIfind Report ===\n");
            sb.append(poiFindText.isBlank() ? "(latest POIfind_*.txt empty or missing)\n" : poiFindText).append("\n");

            LOGGER.info(com.sai.NpcooLog.tag("[OPENAI DEBUG] buildChatContext finished playerDataDir={}"), playerDataDir.toAbsolutePath());

            return sb.toString();

        } catch (Exception e) {
            return "Failed to load SteveAI context files: " + e.getMessage();
        }
    }

    public static void appendChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = getSteveAiDataDir(serverLevel);
            LOGGER.info(com.sai.NpcooLog.tag("SteveAiContextFiles ready to append to chat file"));
            LOGGER.info(com.sai.NpcooLog.tag("SteveAiContextFiles playerDataDir: {}"), playerDataDir.toAbsolutePath());

            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");
            LOGGER.info(com.sai.NpcooLog.tag("SteveAiContextFiles chatFile: {}"), chatFile.toAbsolutePath());

            String out = (line == null ? "" : line);
            if (!out.endsWith("\n")) {
                out += "\n";
            }

            Files.writeString(
                chatFile,
                out,
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

            LOGGER.info(com.sai.NpcooLog.tag("SteveAiContextFiles wrote to chat file"));
        } catch (IOException e) {
            LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI chat file"), e);
        }
    }

    private static String readWholeFile(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            return "";
        }
        return Files.readString(file);
    }

    private static String buildLivePoiSummaryText() {
        List<String> lines = PoiManager.buildSummaryLines();
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("POI SUMMARY\n");
        sb.append("count=").append(lines.size()).append("\n\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public static void startChatSession(ServerLevel serverLevel, UUID playerUuid) {
        if (serverLevel == null || playerUuid == null) {
            return;
        }
        String snapshot = buildLivePoiSummaryText();
        if (!snapshot.isBlank()) {
            chatSessionPoiSummary.put(playerUuid, snapshot);
        } else {
            chatSessionPoiSummary.remove(playerUuid);
        }
    }

    public static void endChatSession(UUID playerUuid) {
        if (playerUuid != null) {
            chatSessionPoiSummary.remove(playerUuid);
        }
    }

    private static Path findLatestMatchingFile(Path dir, String globPattern) throws IOException {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return null;
        }

        Path latest = null;
        java.nio.file.attribute.FileTime latestTime = null;

        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, globPattern)) {
            for (Path candidate : stream) {
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }

                java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(candidate);
                if (latest == null || modified.compareTo(latestTime) > 0) {
                    latest = candidate;
                    latestTime = modified;
                }
            }
        }

        return latest;
    }

    private static void logFileTail(String logPrefix, Path file, int maxLines) {
        try {
            if (file == null || !Files.exists(file)) {
                LOGGER.info(com.sai.NpcooLog.tag("[{}] tail skipped, file missing: {}"), logPrefix, file);
                return;
            }

            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                LOGGER.info(com.sai.NpcooLog.tag("[{}] tail for {} -> (file empty)"), logPrefix, file.getFileName());
                return;
            }

            int start = Math.max(0, lines.size() - maxLines);
            List<String> tail = lines.subList(start, lines.size());

            LOGGER.info(com.sai.NpcooLog.tag("[{}] tail for {} (last {} lines):"), logPrefix, file.getFileName(), tail.size());
            for (String line : tail) {
                LOGGER.info(com.sai.NpcooLog.tag("[{}] {}"), logPrefix, line);
            }
        } catch (IOException e) {
            LOGGER.error(com.sai.NpcooLog.tag("Failed to log [{}] tail for file {}"), logPrefix, file, e);
        }
    }

    private static String readLastNLines(Path file, int maxLines) throws IOException {
        if (file == null || !Files.exists(file)) {
            return "";
        }

        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            return "";
        }

        int start = Math.max(0, lines.size() - maxLines);
        List<String> tail = lines.subList(start, lines.size());
        return String.join("\n", tail);
    }
}
