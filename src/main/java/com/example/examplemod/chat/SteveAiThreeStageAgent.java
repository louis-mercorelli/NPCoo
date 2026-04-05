package com.example.examplemod.chat;

import com.example.examplemod.CommandEvents;
import com.example.examplemod.SteveAiContextFiles;
import com.example.examplemod.scan.SteveAiCollectors;
import com.example.examplemod.scan.SteveAiScanManager;
import com.example.examplemod.steveAI.SteveAiLocator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Three-stage chat agent flow:
 * 1) Stage 1: generate candidate answer + command options as JSON.
 * 2) Stage 2: execute one approved command candidate.
 * 3) Stage 3: return final grounded answer JSON using execution + refreshed context.
 */
public final class SteveAiThreeStageAgent {

    public enum QueryMode {
        FAST_ANSWER,
        DETAILED_COMMAND
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern APPROVE_PATTERN = Pattern.compile("^\\s*approve\\s+#?(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPLICIT_APPROVE_PATTERN = Pattern.compile(
        "\\b(yes|yeah|yep|sure|ok|okay|go ahead|do it|run|execute|proceed|confirm)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WHERE_ARE_YOU_PATTERN = Pattern.compile(
        "(?i)\\b(where\\s*(are|r)?\\s*(you|u)|where\\s*u\\s*at|wya|where\\s*is\\s*steve(?:ai)?|where\\s*steve(?:ai)?)\\b"
    );
    private static final Pattern DETAIL_REQUEST_PATTERN = Pattern.compile(
        "(?i)(?:\\blwtk\\b|\\blou\\s+wants\\s+to\\s+know\\b)\\s*[:,;-]*\\s*"
    );
    private static final Pattern DETAILED_COMMAND_PATTERN = Pattern.compile(
        "(?i)(\\b(scan|check|inspect|search|explore|verify|confirm|run|use|try|mine|dig|build|craft|gather|collect|locate|survey|go\\s+to|move\\s+to|bring|map)\\b|more\\s+detail|more\\s+details|what\\s+command|which\\s+command|look\\s+for|find\\s+out|list\\s+all|show\\s+all|how\\s+many)"
    );
    private static final Pattern BLOCK_QUERY_PATTERN = Pattern.compile(
        "(?i)\\b(is\\s+there|any|where|find|locat|near|nearby|around|have\\s+you\\s+seen|do\\s+you\\s+see)\\b"
    );
    private static final Map<UUID, PendingPlan> PENDING_BY_PLAYER = new ConcurrentHashMap<>();

    private static volatile Map<String, Boolean> toolMutatesCache = null;

    private SteveAiThreeStageAgent() {}

    public static boolean wantsDetailedAnswer(String message) {
        return message != null && DETAIL_REQUEST_PATTERN.matcher(message).find();
    }

    public static String stripDetailRequestMarkers(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String cleaned = DETAIL_REQUEST_PATTERN.matcher(message).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[,:;\\-]+\\s*", "").trim();
        return cleaned;
    }

    public static QueryMode classifyInitialQuery(String message) {
        String cleanedMessage = stripDetailRequestMarkers(message);
        if (cleanedMessage.isBlank()) {
            return QueryMode.FAST_ANSWER;
        }
        return DETAILED_COMMAND_PATTERN.matcher(cleanedMessage).find()
            ? QueryMode.DETAILED_COMMAND
            : QueryMode.FAST_ANSWER;
    }

    public static String detectModeLabel(String message) {
        String cleanedMessage = stripDetailRequestMarkers(message);
        if (cleanedMessage.isBlank()) return "fast";
        if (APPROVE_PATTERN.matcher(cleanedMessage).find()) return "exec";
        if (IMPLICIT_APPROVE_PATTERN.matcher(cleanedMessage).find() && PENDING_BY_PLAYER.values().stream().findAny().isPresent()) return "exec";
        if (WHERE_ARE_YOU_PATTERN.matcher(cleanedMessage).find()) return "instant";
        return classifyInitialQuery(cleanedMessage) == QueryMode.DETAILED_COMMAND ? "planning" : "fast";
    }

    public static String process(ServerLevel serverLevel, UUID playerUuid, String message, String fileContext) {
        boolean detailRequested = wantsDetailedAnswer(message);
        String cleanedMessage = stripDetailRequestMarkers(message);

        String immediate = tryImmediateChatReply(serverLevel, playerUuid, message);
        if (immediate != null) {
            JsonObject out = new JsonObject();
            out.addProperty("stage", "stage3_final");
            out.addProperty("question", cleanedMessage);
            out.addProperty("summary", immediate);
            out.addProperty("detail", immediate);
            out.addProperty("detailRequested", detailRequested);
            out.addProperty("finalAnswer", immediate);
            return GSON.toJson(out);
        }

        Integer approvedIndex = parseApprovalIndex(cleanedMessage);
        if (approvedIndex != null) {
            return runApprovedCandidate(serverLevel, playerUuid, approvedIndex);
        }

        PendingPlan pending = PENDING_BY_PLAYER.get(playerUuid);
        Integer implicitApprovedIndex = inferImplicitApprovalIndex(cleanedMessage, pending);
        if (implicitApprovedIndex != null) {
            return runApprovedCandidate(serverLevel, playerUuid, implicitApprovedIndex);
        }

        QueryMode mode = classifyInitialQuery(cleanedMessage);
        if (mode == QueryMode.DETAILED_COMMAND) {
            return planDetailedCommands(playerUuid, cleanedMessage, fileContext, detailRequested);
        }
        return answerQuickly(cleanedMessage, fileContext, detailRequested);
    }

    private static String answerQuickly(String question, String fileContext, boolean detailRequested) {
        String prompt =
            "You are SteveAI answering a Minecraft question.\n" +
            "Return ONLY valid JSON. No markdown. No prose outside the JSON.\n" +
            "There are exactly two actors: the player and SteveAI.\n" +
            "Use first-person SteveAI wording like 'my POI scan' and 'my scan data'.\n" +
            "Focus on speed. Answer directly from the provided context. Do not suggest commands.\n" +
            "Schema:\n" +
            "{\n" +
            "  \"question\": string,\n" +
            "  \"summary\": string,\n" +
            "  \"detail\": string\n" +
            "}\n" +
            "Rules:\n" +
            "- summary must be brief, conversational, direct, and about 1 to 5 lines max.\n" +
            "- detail must support the summary with fuller explanation from context.\n" +
            "- Do not suggest commands or next actions unless the player explicitly asked for that.\n" +
            "- If context is insufficient, say that clearly in both summary and detail.\n\n" +
            "Context:\n" + fileContext + "\n\n" +
            "Player question: " + question;

        JsonObject out = parseJsonObject(OpenAiService.askFast(prompt));
        if (out == null) {
            out = fallbackQuickAnswer(question);
        }

        normalizeAnswerEnvelope(out, question);
        out.addProperty("stage", "type1_fast_answer");
        out.addProperty("detailRequested", detailRequested);
        out.addProperty("finalAnswer", detailRequested
            ? getString(out, "detail", getString(out, "summary", ""))
            : getString(out, "summary", getString(out, "detail", "")));
        return GSON.toJson(out);
    }

    private static String planDetailedCommands(UUID playerUuid, String question, String fileContext, boolean detailRequested) {
        String toolCatalogJson = loadToolCommandsJson();

        String prompt =
            "You are an AI planner for a Minecraft mod assistant.\n" +
            "Return ONLY valid JSON (no markdown, no prose).\n" +
            "There are exactly two actors: the player (question asker) and SteveAI (you).\n" +
            "Never invent a third actor. Resolve pronouns using that rule.\n" +
            "Schema:\n" +
            "{\n" +
            "  \"question\": string,\n" +
            "  \"summary\": string,\n" +
            "  \"detail\": string,\n" +
            "  \"candidates\": [\n" +
            "    {\"tool\": string, \"args\": [string], \"purpose\": string, \"confidence\": number, \"requiresApproval\": true, \"mutatesWorld\": boolean}\n" +
            "  ]\n" +
            "}\n" +
            "Rules:\n" +
            "- Include 1-3 command candidates only when more detail likely requires fresh world data or command execution.\n" +
            "- Confidence is 0.0-1.0.\n" +
            "- Command tool names must match tools from catalog.\n" +
            "- Always set requiresApproval=true for command candidates.\n" +
            "- Keep args minimal and concrete.\n" +
            "- summary should briefly explain what is already known from context.\n" +
            "- detail should explain what is missing and why a command may help.\n\n" +
            "- If the question asks where SteveAI is, prefer answering directly from current context/status. Do NOT propose whereRu unless SteveAI location is missing/unknown.\n\n" +
            "- When discussing scan evidence or POI results, speak in first person as SteveAI. Use wording like 'my POI scan' or 'my scan data', never 'your POI scan'.\n\n" +
            "Tool catalog:\n" + toolCatalogJson + "\n\n" +
            "Game context:\n" + fileContext + "\n\n" +
            "Player question: " + question;

        String raw = OpenAiService.ask(prompt);
        JsonObject planned = parseJsonObject(raw);
        if (planned == null) {
            planned = fallbackCommandPlan(question);
        }

        normalizeCommandPlan(planned, question);

        PendingPlan pending = new PendingPlan(question, planned, detailRequested);
        if (playerUuid != null) {
            PENDING_BY_PLAYER.put(playerUuid, pending);
        }

        planned.addProperty("stage", "type2_command_plan");
        planned.addProperty("createdAt", Instant.ofEpochMilli(pending.createdAtMs).toString());
        planned.addProperty("approvalHint", "Reply with: approve <candidateNumber>");
        planned.addProperty("detailRequested", detailRequested);
        planned.addProperty("finalAnswer", detailRequested
            ? getString(planned, "detail", getString(planned, "summary", ""))
            : getString(planned, "summary", getString(planned, "detail", "")));

        return GSON.toJson(planned);
    }

    private static String runApprovedCandidate(ServerLevel serverLevel, UUID playerUuid, int candidateNumber) {
        PendingPlan pending = PENDING_BY_PLAYER.get(playerUuid);
        if (pending == null) {
            return GSON.toJson(errorJson("No pending candidates. Ask a question first."));
        }

        JsonArray candidates = pending.plan.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return GSON.toJson(errorJson("No candidates available for approval."));
        }
        if (candidateNumber < 1 || candidateNumber > candidates.size()) {
            return GSON.toJson(errorJson("Candidate number out of range. Choose 1-" + candidates.size() + "."));
        }

        JsonObject candidate = candidates.get(candidateNumber - 1).getAsJsonObject();
        if (!hasCommandShape(candidate)) {
            return GSON.toJson(errorJson("Selected candidate is not a command."));
        }

        String tool = getString(candidate, "tool", "").trim();
        JsonArray args = candidate.has("args") && candidate.get("args").isJsonArray()
            ? candidate.getAsJsonArray("args")
            : new JsonArray();

        String commandText = buildServerCommand(tool, args);

        boolean dispatched = false;
        String execError = null;
        try {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                execError = "Player is not online.";
            } else {
                serverLevel.getServer()
                    .getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), commandText);
                dispatched = true;
                
                // Wait for command to execute, then flush results to disk.
                // Poll for file changes up to 5 seconds; exit early if files update.
                try {
                    long startWaitMs = System.currentTimeMillis();
                    long maxWaitMs = 5000L;
                    long pollIntervalMs = 250L;
                    
                    // Record initial mod times of result files
                    long initialModTime = getLatestResultFileModTime(serverLevel, playerUuid);
                    
                    while (System.currentTimeMillis() - startWaitMs < maxWaitMs) {
                        Thread.sleep(pollIntervalMs);
                        
                        // Check if result files have been updated
                        long currentModTime = getLatestResultFileModTime(serverLevel, playerUuid);
                        if (currentModTime > initialModTime) {
                            // Results are ready!
                            break;
                        }
                    }
                    
                    // Flush results to disk one final time
                    serverLevel.getServer()
                        .getCommands()
                        .performPrefixedCommand(player.createCommandSourceStack(), "testmod writeT");
                    
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    // Server shutdown during wait; continue anyway.
                }
            }
        } catch (Exception e) {
            execError = e.getMessage();
        }

        String refreshedContext = SteveAiContextFiles.buildChatContext(serverLevel, playerUuid, 200);

        String finalPrompt =
            "You are SteveAI answering after a command was executed.\n" +
            "Return ONLY valid JSON. No markdown. No prose outside the JSON.\n" +
            "There are exactly two actors: player and SteveAI.\n" +
            "Use first-person SteveAI wording like 'my POI scan' and 'my scan data'.\n" +
            "Schema:\n" +
            "{\n" +
            "  \"question\": string,\n" +
            "  \"summary\": string,\n" +
            "  \"detail\": string\n" +
            "}\n" +
            "Rules:\n" +
            "- summary must be brief, conversational, direct, and about 1 to 5 lines max.\n" +
            "- detail must explain the executed command result in more depth.\n\n" +
            "Question: " + pending.question + "\n" +
            "Executed command: " + commandText + "\n" +
            "Execution status: " + (dispatched ? "dispatched" : "failed") + "\n" +
            (execError == null ? "" : ("Execution error: " + execError + "\n")) +
            "\nContext:\n" + refreshedContext;

        JsonObject finalEnvelope = parseJsonObject(OpenAiService.ask(finalPrompt));
        if (finalEnvelope == null) {
            finalEnvelope = fallbackQuickAnswer(pending.question);
        }
        normalizeAnswerEnvelope(finalEnvelope, pending.question);

        JsonObject out = new JsonObject();
        out.addProperty("stage", "stage3_final");
        out.addProperty("question", pending.question);
        out.addProperty("approvedCandidate", candidateNumber);

        JsonObject execution = new JsonObject();
        execution.addProperty("stage", "stage2_execute");
        execution.addProperty("tool", tool);
        execution.add("args", args.deepCopy());
        execution.addProperty("command", commandText);
        execution.addProperty("status", dispatched ? "dispatched" : "failed");
        if (execError != null) {
            execution.addProperty("error", execError);
        }
        out.add("execution", execution);
        out.addProperty("detailRequested", pending.detailRequested);
        out.addProperty("summary", getString(finalEnvelope, "summary", ""));
        out.addProperty("detail", getString(finalEnvelope, "detail", ""));
        out.addProperty("finalAnswer", pending.detailRequested
            ? getString(finalEnvelope, "detail", getString(finalEnvelope, "summary", ""))
            : getString(finalEnvelope, "summary", getString(finalEnvelope, "detail", "")));

        // Consume the plan after an approval action.
        PENDING_BY_PLAYER.remove(playerUuid);

        return GSON.toJson(out);
    }

    private static void normalizeCommandPlan(JsonObject planned, String fallbackQuestion) {
        if (!planned.has("question") || !planned.get("question").isJsonPrimitive()) {
            planned.addProperty("question", fallbackQuestion == null ? "" : fallbackQuestion);
        }
        if (!planned.has("summary") || !planned.get("summary").isJsonPrimitive()) {
            planned.addProperty("summary", "I can answer part of this from my current context, but fresh command data may help.");
        }
        if (!planned.has("detail") || !planned.get("detail").isJsonPrimitive()) {
            planned.addProperty("detail", "My current context may not contain enough detail to answer fully without running an additional command.");
        }

        JsonArray candidates;
        if (planned.has("candidates") && planned.get("candidates").isJsonArray()) {
            candidates = planned.getAsJsonArray("candidates");
        } else {
            candidates = new JsonArray();
            planned.add("candidates", candidates);
        }

        Map<String, Boolean> mutatesMap = getToolMutatesMap();
        for (JsonElement el : candidates) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            c.addProperty("requiresApproval", true);

            String tool = getString(c, "tool", "");
            boolean mutates = mutatesMap.getOrDefault(tool, false);
            c.addProperty("mutatesWorld", mutates);

            if (!c.has("args") || !c.get("args").isJsonArray()) {
                c.add("args", new JsonArray());
            }

            double confidence = c.has("confidence") && c.get("confidence").isJsonPrimitive()
                ? c.get("confidence").getAsDouble() : 0.5;
            if (confidence < 0.0) confidence = 0.0;
            if (confidence > 1.0) confidence = 1.0;
            c.addProperty("confidence", confidence);
        }
    }

    private static void normalizeAnswerEnvelope(JsonObject answer, String fallbackQuestion) {
        if (!answer.has("question") || !answer.get("question").isJsonPrimitive()) {
            answer.addProperty("question", fallbackQuestion == null ? "" : fallbackQuestion);
        }
        if (!answer.has("summary") || !answer.get("summary").isJsonPrimitive()) {
            answer.addProperty("summary", "I don't have a solid answer from my current context yet.");
        }
        if (!answer.has("detail") || !answer.get("detail").isJsonPrimitive()) {
            answer.addProperty("detail", getString(answer, "summary", ""));
        }
    }

    private static String buildServerCommand(String tool, JsonArray args) {
        StringBuilder sb = new StringBuilder("testmod");
        if (tool != null && !tool.isBlank() && !"testmod".equalsIgnoreCase(tool)) {
            sb.append(" ").append(tool.trim());
        }
        for (JsonElement arg : args) {
            String v = arg == null || arg.isJsonNull() ? "" : arg.getAsString();
            if (v.contains(" ")) {
                sb.append(" \"").append(v.replace("\"", "\\\"")).append("\"");
            } else if (!v.isEmpty()) {
                sb.append(" ").append(v);
            }
        }
        return sb.toString();
    }

    private static Integer parseApprovalIndex(String message) {
        if (message == null) return null;
        Matcher m = APPROVE_PATTERN.matcher(message);
        if (!m.matches()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer inferImplicitApprovalIndex(String message, PendingPlan pending) {
        if (pending == null || message == null || message.isBlank()) {
            return null;
        }

        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (!IMPLICIT_APPROVE_PATTERN.matcher(lower).find()) {
            return null;
        }

        JsonArray candidates = pending.plan.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        int bestCandidateNumber = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < candidates.size(); i++) {
            JsonElement el = candidates.get(i);
            if (!el.isJsonObject()) {
                continue;
            }

            JsonObject candidate = el.getAsJsonObject();
            if (!hasCommandShape(candidate)) {
                continue;
            }

            String tool = getString(candidate, "tool", "").toLowerCase(Locale.ROOT);
            String purpose = getString(candidate, "purpose", "").toLowerCase(Locale.ROOT);
            JsonArray args = candidate.has("args") && candidate.get("args").isJsonArray()
                ? candidate.getAsJsonArray("args")
                : new JsonArray();

            double score = candidate.has("confidence") && candidate.get("confidence").isJsonPrimitive()
                ? candidate.get("confidence").getAsDouble()
                : 0.0;

            if (!tool.isBlank() && lower.contains(tool.toLowerCase(Locale.ROOT))) {
                score += 1.0;
            }

            if (lower.contains("detail") && (purpose.contains("detail") || tool.contains("detail") || argsToText(args).contains("detail"))) {
                score += 0.8;
            }
            if (lower.contains("scan") && (tool.contains("scan") || purpose.contains("scan"))) {
                score += 0.6;
            }
            if (lower.contains("map") && (tool.contains("map") || purpose.contains("map"))) {
                score += 0.6;
            }
            if (lower.contains("village") && (purpose.contains("village") || argsToText(args).contains("village"))) {
                score += 0.6;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidateNumber = i + 1;
            }
        }

        return bestCandidateNumber > 0 ? bestCandidateNumber : null;
    }

    public static String tryImmediateChatReply(ServerLevel serverLevel, UUID playerUuid, String message) {
        if (serverLevel == null || message == null || message.isBlank()) {
            return null;
        }

        String cleanedMessage = stripDetailRequestMarkers(message);

        String blockReply = tryImmediateBlockReply(message);
        if (blockReply != null) {
            return blockReply;
        }

        if (!isWhereAreYouQuestion(cleanedMessage)) {
            return null;
        }

        boolean lwtkMode = wantsDetailedAnswer(message);
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        ServerPlayer player = playerUuid == null ? null : serverLevel.getServer().getPlayerList().getPlayer(playerUuid);

        if (steveAi != null) {
            BlockPos stevePos = steveAi.blockPosition();
            if (lwtkMode) {
                String dim = steveAi.level().dimension().toString();
                String rel = player == null ? "" : buildRelativeDirectionText(player.blockPosition(), stevePos);
                return "I am at x=" + stevePos.getX() + ", y=" + stevePos.getY() + ", z=" + stevePos.getZ()
                    + " in " + dim + rel + ".";
            }
            String rel = player == null ? "I'm currently loaded." : buildFriendlyRelativeText(player.blockPosition(), stevePos);
            return rel + " I already have a recent scan, so no extra command is needed.";
        }

        if (CommandEvents.lastSteveAiKnownPos != null) {
            BlockPos last = CommandEvents.lastSteveAiKnownPos;
            if (lwtkMode) {
                String dim = CommandEvents.lastSteveAiKnownDimension == null
                    ? "unknown"
                    : CommandEvents.lastSteveAiKnownDimension.toString();
                return "I'm not currently loaded. Last known position was x=" + last.getX() + ", y=" + last.getY()
                    + ", z=" + last.getZ() + " in " + dim + ".";
            }
            return "I'm not currently loaded, but I still have my last known location from recent data.";
        }

        return "I can't see my current location yet from memory. Ask me again after the next scan refresh.";
    }

    private static String tryImmediateBlockReply(String message) {
        String lower = stripDetailRequestMarkers(message).toLowerCase(Locale.ROOT);
        if (!BLOCK_QUERY_PATTERN.matcher(lower).find()) {
            return null;
        }

        boolean lwtkMode = wantsDetailedAnswer(message);
        Map<String, SteveAiCollectors.SeenSummary> blocks = SteveAiScanManager.getScannedBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }

        String bestBlockKey = null;
        String bestLabel = null;
        int totalCount = 0;
        double nearest = -1.0;
        SteveAiCollectors.SeenSummary best = null;

        for (Map.Entry<String, SteveAiCollectors.SeenSummary> entry : blocks.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }

            String label = humanizeBlockKey(key);
            if (!messageMatchesBlockQuery(lower, key, label)) {
                continue;
            }

            SteveAiCollectors.SeenSummary s = entry.getValue();
            if (s == null) {
                continue;
            }

            if (bestBlockKey == null) {
                bestBlockKey = key;
                bestLabel = label;
            }
            totalCount += Math.max(0, s.count);
            if (s.minDistanceFromCenter >= 0.0 && (nearest < 0.0 || s.minDistanceFromCenter < nearest)) {
                nearest = s.minDistanceFromCenter;
                best = s;
                bestBlockKey = key;
                bestLabel = label;
            } else if (best == null) {
                best = s;
                bestBlockKey = key;
                bestLabel = label;
            }
        }

        if (bestBlockKey == null || bestLabel == null) {
            return null;
        }

        if (totalCount <= 0) {
            return "From my latest block scan, I don't see any " + bestLabel + " nearby right now.";
        }

        if (lwtkMode && best != null) {
            String nearText = nearest >= 0.0
                ? (", nearest is about " + String.format(Locale.ROOT, "%.1f", nearest) + " blocks from my scan center")
                : "";
            return "My latest block scan found " + totalCount + " " + bestLabel
                + nearText
                + ". One observed location is x=" + best.x + ", y=" + best.y + ", z=" + best.z + ".";
        }

        if (nearest >= 0.0) {
            return "Yes, my latest block scan shows " + bestLabel + " nearby. I found " + totalCount
                + " matches, with the nearest about " + String.format(Locale.ROOT, "%.0f", nearest)
                + " blocks from my scan center.";
        }

        return "Yes, my latest block scan shows " + bestLabel + " nearby, with " + totalCount
            + " matches in my current scan data.";
    }

    private static boolean messageMatchesBlockQuery(String lowerMessage, String blockKey, String humanLabel) {
        String normalizedKey = blockKey.toLowerCase(Locale.ROOT);
        String noNamespace = normalizedKey.startsWith("minecraft:")
            ? normalizedKey.substring("minecraft:".length())
            : normalizedKey;
        String spacedKey = noNamespace.replace('_', ' ');
        String singularLabel = humanLabel.endsWith("s") ? humanLabel.substring(0, humanLabel.length() - 1) : humanLabel;

        return lowerMessage.contains(normalizedKey)
            || lowerMessage.contains(noNamespace)
            || lowerMessage.contains(spacedKey)
            || lowerMessage.contains(humanLabel)
            || (!singularLabel.isBlank() && lowerMessage.contains(singularLabel));
    }

    private static String humanizeBlockKey(String blockKey) {
        String noNamespace = blockKey == null ? "" : blockKey;
        int idx = noNamespace.indexOf(':');
        if (idx >= 0 && idx + 1 < noNamespace.length()) {
            noNamespace = noNamespace.substring(idx + 1);
        }
        return noNamespace.replace('_', ' ');
    }

    private static boolean isWhereAreYouQuestion(String message) {
        return WHERE_ARE_YOU_PATTERN.matcher(message).find();
    }

    private static String buildFriendlyRelativeText(BlockPos playerPos, BlockPos stevePos) {
        int dx = stevePos.getX() - playerPos.getX();
        int dz = stevePos.getZ() - playerPos.getZ();
        int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));

        if (distance <= 12) {
            return "I'm right near you.";
        }
        if (distance <= 48) {
            return "I'm nearby, not far from you.";
        }
        return "I'm a bit farther out from your current spot.";
    }

    private static String buildRelativeDirectionText(BlockPos playerPos, BlockPos stevePos) {
        int dx = stevePos.getX() - playerPos.getX();
        int dz = stevePos.getZ() - playerPos.getZ();

        String ew = dx == 0 ? "" : (dx > 0 ? "east" : "west");
        String ns = dz == 0 ? "" : (dz > 0 ? "south" : "north");
        String dir;
        if (!ns.isEmpty() && !ew.isEmpty()) {
            dir = ns + "-" + ew;
        } else if (!ns.isEmpty()) {
            dir = ns;
        } else if (!ew.isEmpty()) {
            dir = ew;
        } else {
            dir = "same spot as you";
        }

        int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        if ("same spot as you".equals(dir)) {
            return " (same location as player)";
        }
        return " (about " + distance + " blocks " + dir + " of player)";
    }

    private static String argsToText(JsonArray args) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement arg : args) {
            if (arg != null && arg.isJsonPrimitive()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(arg.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static JsonObject parseJsonObject(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();

        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            trimmed = trimmed.substring(first, last + 1);
        }

        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static JsonObject fallbackQuickAnswer(String question) {
        JsonObject root = new JsonObject();
        root.addProperty("question", question == null ? "" : question);
        root.addProperty("summary", "I can't answer that confidently from my current context yet.");
        root.addProperty("detail", "My current context does not contain enough reliable detail to answer that directly.");
        return root;
    }

    private static JsonObject fallbackCommandPlan(String question) {
        JsonObject root = new JsonObject();
        root.addProperty("question", question == null ? "" : question);
        root.addProperty("summary", "I can only answer this partially from my current context.");
        root.addProperty("detail", "I likely need fresh command output to answer this in detail, so here is a candidate command that can gather better evidence.");

        JsonArray candidates = new JsonArray();

        JsonObject cmd = new JsonObject();
        cmd.addProperty("tool", "scanSAI");
        JsonArray args = new JsonArray();
        args.add("all");
        args.add("3");
        cmd.add("args", args);
        cmd.addProperty("purpose", "Collect nearby evidence for a better answer");
        cmd.addProperty("confidence", 0.75);
        cmd.addProperty("requiresApproval", true);
        cmd.addProperty("mutatesWorld", false);
        candidates.add(cmd);

        root.add("candidates", candidates);
        return root;
    }

    private static boolean hasCommandShape(JsonObject candidate) {
        if (candidate == null) {
            return false;
        }
        String type = getString(candidate, "type", "");
        if ("command".equalsIgnoreCase(type)) {
            return true;
        }
        return candidate.has("tool") && candidate.get("tool").isJsonPrimitive();
    }

    private static JsonObject errorJson(String msg) {
        JsonObject out = new JsonObject();
        out.addProperty("stage", "error");
        out.addProperty("message", msg);
        return out;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }

    private static String loadToolCommandsJson() {
        String resourcePath = "data/examplemod/reference/toolCommands.json";
        try (InputStream in = SteveAiThreeStageAgent.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return "[]";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[]";
        }
    }

    private static Map<String, Boolean> getToolMutatesMap() {
        Map<String, Boolean> cached = toolMutatesCache;
        if (cached != null) {
            return cached;
        }

        synchronized (SteveAiThreeStageAgent.class) {
            if (toolMutatesCache != null) {
                return toolMutatesCache;
            }

            Map<String, Boolean> map = new HashMap<>();
            JsonObject parsed = parseJsonObject("{\"list\":" + loadToolCommandsJson() + "}");
            if (parsed != null && parsed.has("list") && parsed.get("list").isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray("list");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String tool = getString(o, "tool", "");
                    boolean mutates = o.has("mutatesWorld") && o.get("mutatesWorld").isJsonPrimitive()
                        && o.get("mutatesWorld").getAsBoolean();
                    if (!tool.isBlank()) {
                        map.put(tool, mutates);
                    }
                }
            }

            toolMutatesCache = Collections.unmodifiableMap(map);
            return toolMutatesCache;
        }
    }

    private static long getLatestResultFileModTime(ServerLevel serverLevel, UUID playerUuid) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            long latestModTime = 0L;
            
            // Check modification times of all result files
            String[] globs = {"POIfind_*.txt", "scanSAI_*.txt", "detailBlockEntities*.txt", 
                             "detailEntities*.txt", "detailStatus*.txt", "poiSummary*.txt"};
            
            for (String glob : globs) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerDataDir, glob)) {
                    for (Path file : stream) {
                        long modTime = Files.getLastModifiedTime(file).toMillis();
                        if (modTime > latestModTime) {
                            latestModTime = modTime;
                        }
                    }
                } catch (Exception ignored) {
                    // File not found or other IO error; continue checking other globs
                }
            }
            
            return latestModTime;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static final class PendingPlan {
        final String question;
        final JsonObject plan;
        final boolean detailRequested;
        final long createdAtMs;

        PendingPlan(String question, JsonObject plan, boolean detailRequested) {
            this.question = question == null ? "" : question;
            this.plan = plan == null ? new JsonObject() : plan.deepCopy();
            this.detailRequested = detailRequested;
            this.createdAtMs = System.currentTimeMillis();
        }
    }
}
