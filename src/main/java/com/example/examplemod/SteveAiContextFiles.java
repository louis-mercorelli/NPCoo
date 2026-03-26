package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SteveAiContextFiles {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static String buildChatContext(UUID playerUuid, int tailLines) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.getSingleplayerServer() == null) {
            return "No integrated server available. Context files could not be loaded.";
        }

        try {
            Path playerDataDir = mc.getSingleplayerServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);

            Path rawFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI.txt");
            Path summaryFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_summary.txt");

            String summaryText = readWholeFile(summaryFile);
            String rawTailText = readLastNLines(rawFile, tailLines);

            StringBuilder sb = new StringBuilder();

            sb.append("=== SteveAI Summary File ===\n");
            sb.append(summaryText.isBlank() ? "(summary file empty or missing)\n" : summaryText).append("\n");

            sb.append("=== SteveAI Raw File (last ").append(tailLines).append(" lines) ===\n");
            sb.append(rawTailText.isBlank() ? "(raw file empty or missing)\n" : rawTailText).append("\n");

            return sb.toString();

        } catch (Exception e) {
            return "Failed to load SteveAI context files: " + e.getMessage();
        }
    }

    public static void appendChatLine(UUID playerUuid, String line) {
        try {
            Path playerDataDir = java.nio.file.Paths.get("saves", "steveAI 2 desert", "playerdata");
            LOGGER.info("SteveAiContextFiles ready to append to chat file");
            Files.createDirectories(playerDataDir);
            LOGGER.info("SteveAiContextFiles playerDataDir: " + playerDataDir.toAbsolutePath().toString());
            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");
            LOGGER.info("SteveAiContextFiles chatFile: " + chatFile.toAbsolutePath().toString());


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
            LOGGER.info("SteveAiContextFiles wrote to chat file");
        } catch (IOException e) {
            LOGGER.error("Failed to write steveAI chat file", e);
        }
    }

    private static String readWholeFile(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            return "";
        }
        return Files.readString(file);
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
