/**
 * File: CEHChat.java
 *
 * Main intent:
 * Defines CEHChat functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code handleSteveAiChat(...)}:
 *    Purpose: Handles chat commands directed to Steve AI.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.  
 * 2) {@code appendSteveAiChatLine(...)}:
 *    Purpose: Appends a chat line to the player's Steve AI chat log file.
 *    Input: ServerLevel serverLevel, UUID playerUuid, String line.
 *    Output: void.
 * 3) {@code chatTs(...)}:
 *    Purpose: Generates a timestamp string for chat log entries.
 *    Input: none.
 *    Output: String.
 * 4) {@code oneLine(...)}:
 *    Purpose: Normalizes text into a single line by removing newlines and trimming.
 *    Input: String s.
 *    Output: String.
 * 5) {@code askSteveAi(...)}:
 *    Purpose: Builds context, calls OpenAI, stores transcript lines, and returns reply text.
 *    Input: ServerLevel serverLevel, UUID playerUuid, String message.
 *    Output: String.
  
 */
package com.example.examplemod.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.examplemod.SteveAiContextFiles;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CEHChat {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<UUID, LastResponse> LAST_RESPONSE_BY_PLAYER = new ConcurrentHashMap<>();

    private CEHChat() {}

    public static int handleSteveAiChat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String message = StringArgumentType.getString(context, "message");

        LOGGER.info(com.sai.NpcooLog.tag("TESTMOD asking OpenAI...: {}"), message);
        source.sendSuccess(() -> Component.literal("§6[testmod] Asking OpenAI: " + message), false);

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        UUID playerUuid = player.getUUID();

        String reply = askSteveAi(serverLevel, playerUuid, message);
        source.sendSuccess(() -> Component.literal("§6[testmod] OpenAI reply: " + reply), false);
        LOGGER.info(com.sai.NpcooLog.tag("ExampleMod OpenAI response... {}"), reply);
        return 1;
    }

    private static void appendSteveAiChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");

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
        } catch (IOException e) {
            LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI chat file"), e);
        }
    }

    private static String chatTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static JsonElement tryParseJson(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String trimmed = s.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        try {
            return JsonParser.parseString(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendPrettyAgentJson(ServerLevel serverLevel, UUID playerUuid, String prompt, JsonElement responseJson) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path prettyFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat_pretty.json");

            JsonArray history = new JsonArray();
            if (Files.exists(prettyFile)) {
                String existing = Files.readString(prettyFile, java.nio.charset.StandardCharsets.UTF_8);
                try {
                    JsonElement parsed = JsonParser.parseString(existing);
                    if (parsed.isJsonArray()) {
                        history = parsed.getAsJsonArray();
                    }
                } catch (Exception ignored) {
                    // If file was corrupted, start a fresh history array.
                }
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", chatTs());
            entry.addProperty("prompt", prompt == null ? "" : prompt);
            entry.add("response", responseJson == null ? new JsonObject() : responseJson.deepCopy());
            history.add(entry);

            Files.writeString(
                prettyFile,
                PRETTY_GSON.toJson(history),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (Exception e) {
            LOGGER.error(com.sai.NpcooLog.tag("Failed to write pretty SteveAI JSON chat file"), e);
        }
    }

    private static String summarizeJsonForChat(UUID playerUuid, String message, JsonElement json) {
        String fileName = playerUuid.toString() + "_steveAI_chat_pretty.json";

        if (json == null || !json.isJsonObject()) {
            return "Structured response saved to " + fileName;
        }

        JsonObject obj = json.getAsJsonObject();
        boolean detailRequested = obj.has("detailRequested") && obj.get("detailRequested").isJsonPrimitive()
            ? obj.get("detailRequested").getAsBoolean()
            : SteveAiThreeStageAgent.wantsDetailedAnswer(message);
        String stage = obj.has("stage") && obj.get("stage").isJsonPrimitive()
            ? obj.get("stage").getAsString()
            : "unknown";

        if ("type1_fast_answer".equals(stage)) {
            String text = detailRequested
                ? getJsonString(obj, "detail", getJsonString(obj, "summary", "No answer."))
                : getJsonString(obj, "summary", getJsonString(obj, "detail", "No answer."));
            return oneLine(text);
        }

        if ("type2_command_plan".equals(stage)) {
            JsonArray candidates = obj.has("candidates") && obj.get("candidates").isJsonArray()
                ? obj.getAsJsonArray("candidates")
                : new JsonArray();

            List<NumberedCandidate> commandCandidates = new ArrayList<>();
            for (int idx = 0; idx < candidates.size(); idx++) {
                JsonElement el = candidates.get(idx);
                if (el.isJsonObject()) {
                    commandCandidates.add(new NumberedCandidate(idx + 1, el.getAsJsonObject()));
                }
            }

            commandCandidates.sort(Comparator.comparingDouble((NumberedCandidate nc) -> confidenceOf(nc.candidate)).reversed());

            StringBuilder sb = new StringBuilder();
            String answerText = detailRequested
                ? getJsonString(obj, "detail", getJsonString(obj, "summary", "No answer."))
                : getJsonString(obj, "summary", getJsonString(obj, "detail", "No answer."));
            sb.append(oneLine(answerText));

            for (NumberedCandidate nc : commandCandidates) {
                JsonObject cmd = nc.candidate;
                if (sb.length() > 0) {
                    sb.append("\n");
                }

                String tool = cmd.has("tool") && cmd.get("tool").isJsonPrimitive()
                    ? cmd.get("tool").getAsString()
                    : "unknownTool";

                sb.append("#").append(nc.candidateNumber).append(" ").append(tool);

                if (cmd.has("args") && cmd.get("args").isJsonArray() && cmd.getAsJsonArray("args").size() > 0) {
                    JsonArray args = cmd.getAsJsonArray("args");
                    List<String> argParts = new ArrayList<>();
                    for (JsonElement arg : args) {
                        if (arg != null && arg.isJsonPrimitive()) {
                            argParts.add(arg.getAsString());
                        }
                    }
                    if (!argParts.isEmpty()) {
                        sb.append(" ").append(String.join(" ", argParts));
                    }
                }

                String purpose = cmd.has("purpose") && cmd.get("purpose").isJsonPrimitive()
                    ? cmd.get("purpose").getAsString()
                    : "";
                if (!purpose.isBlank()) {
                    sb.append(" — ").append(oneLine(purpose));
                }
            }

            if (sb.length() == 0) {
                return "No candidates available. Full JSON saved to " + fileName;
            }
            return sb.toString();
        }

        if ("stage3_final".equals(stage)) {
            String finalAnswer = detailRequested
                ? getJsonString(obj, "detail", getJsonString(obj, "finalAnswer", "Done."))
                : getJsonString(obj, "summary", getJsonString(obj, "finalAnswer", "Done."));
            if (detailRequested) {
                return oneLine(finalAnswer) + " (Full JSON saved to " + fileName + ")";
            }
            return oneLine(finalAnswer);
        }

        if ("error".equals(stage)) {
            String msg = obj.has("message") && obj.get("message").isJsonPrimitive()
                ? obj.get("message").getAsString()
                : "Agent error.";
            return oneLine(msg) + " (Full JSON saved to " + fileName + ")";
        }

        return "Agent stage '" + stage + "' complete. Full JSON saved to " + fileName;
    }

    private static double confidenceOf(JsonObject candidate) {
        if (candidate == null || !candidate.has("confidence") || !candidate.get("confidence").isJsonPrimitive()) {
            return 0.0;
        }
        double c = candidate.get("confidence").getAsDouble();
        if (c < 0.0) return 0.0;
        if (c > 1.0) return 1.0;
        return c;
    }

    private static String getJsonString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }

    private static void cacheLastResponse(UUID playerUuid, String summary, String detail) {
        if (playerUuid == null) {
            return;
        }

        String cachedSummary = oneLine(summary);
        String cachedDetail = oneLine(detail == null || detail.isBlank() ? summary : detail);
        LAST_RESPONSE_BY_PLAYER.put(playerUuid, new LastResponse(cachedSummary, cachedDetail));
    }

    private static void cacheLastResponseFromJson(UUID playerUuid, JsonElement json) {
        if (playerUuid == null || json == null || !json.isJsonObject()) {
            return;
        }

        JsonObject obj = json.getAsJsonObject();
        String summary = getJsonString(obj, "summary", getJsonString(obj, "finalAnswer", ""));
        String detail = getJsonString(obj, "detail", summary);
        if (summary.isBlank() && detail.isBlank()) {
            return;
        }

        cacheLastResponse(playerUuid, summary, detail);
    }

    private static String replayLastDetail(UUID playerUuid) {
        LastResponse last = playerUuid == null ? null : LAST_RESPONSE_BY_PLAYER.get(playerUuid);
        if (last == null || last.detail.isBlank()) {
            return "I don't have a previous detailed response to replay yet.";
        }
        return last.detail;
    }

    private static final class NumberedCandidate {
        final int candidateNumber;
        final JsonObject candidate;

        NumberedCandidate(int candidateNumber, JsonObject candidate) {
            this.candidateNumber = candidateNumber;
            this.candidate = candidate;
        }
    }

    private static final class LastResponse {
        final String summary;
        final String detail;

        LastResponse(String summary, String detail) {
            this.summary = summary == null ? "" : summary;
            this.detail = detail == null ? this.summary : detail;
        }
    }

    public static String askSteveAi(ServerLevel serverLevel, UUID playerUuid, String message) {
        LOGGER.info(com.sai.NpcooLog.tag("askSteveAi START playerUuid={} message={}"), playerUuid, message);

        if (SteveAiThreeStageAgent.wantsDetailedAnswer(message)
            && SteveAiThreeStageAgent.stripDetailRequestMarkers(message).isBlank()) {
            String replay = replayLastDetail(playerUuid);
            appendSteveAiChatLine(serverLevel, playerUuid,
                "[" + chatTs() + "] YOU: " + oneLine(message));
            appendSteveAiChatLine(serverLevel, playerUuid,
                "[" + chatTs() + "] STEVEAI: " + oneLine(replay));
            appendSteveAiChatLine(serverLevel, playerUuid, "");

            LOGGER.info(com.sai.NpcooLog.tag("askSteveAi REPLAY-DETAIL playerUuid={} reply={}"), playerUuid, replay);
            return replay;
        }

        String immediateReply = SteveAiThreeStageAgent.tryImmediateChatReply(serverLevel, playerUuid, message);
        if (immediateReply != null) {
            cacheLastResponse(playerUuid, immediateReply, immediateReply);
            appendSteveAiChatLine(serverLevel, playerUuid,
                "[" + chatTs() + "] YOU: " + oneLine(message));
            appendSteveAiChatLine(serverLevel, playerUuid,
                "[" + chatTs() + "] STEVEAI: " + oneLine(immediateReply));
            appendSteveAiChatLine(serverLevel, playerUuid, "");

            LOGGER.info(com.sai.NpcooLog.tag("askSteveAi FAST-PATH playerUuid={} reply={}"), playerUuid, immediateReply);
            return immediateReply;
        }

        SteveAiThreeStageAgent.QueryMode mode = SteveAiThreeStageAgent.classifyInitialQuery(message);
        String fileContext = mode == SteveAiThreeStageAgent.QueryMode.FAST_ANSWER
            ? SteveAiContextFiles.buildQuickChatContext(serverLevel, playerUuid, 60)
            : SteveAiContextFiles.buildChatContext(serverLevel, playerUuid, 200);
        String rawReply = SteveAiThreeStageAgent.process(serverLevel, playerUuid, message, fileContext);
        JsonElement parsedJson = tryParseJson(rawReply);

        if (parsedJson != null) {
            appendPrettyAgentJson(serverLevel, playerUuid, message, parsedJson);
            cacheLastResponseFromJson(playerUuid, parsedJson);
        }

        String reply = parsedJson != null
            ? summarizeJsonForChat(playerUuid, message, parsedJson)
            : rawReply;

        if (parsedJson == null) {
            cacheLastResponse(playerUuid, rawReply, rawReply);
        }

        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] YOU: " + oneLine(message));
        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] STEVEAI: " + oneLine(reply));
        appendSteveAiChatLine(serverLevel, playerUuid, "");

        LOGGER.info(com.sai.NpcooLog.tag("askSteveAi FINISH playerUuid={} reply={}"), playerUuid, reply);
        return reply;
    }
}