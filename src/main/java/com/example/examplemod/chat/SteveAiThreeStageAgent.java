package com.example.examplemod.chat;

import com.example.examplemod.SteveAiContextFiles;
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

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Three-stage chat agent flow:
 * 1) Stage 1: generate candidate answer + command options as JSON.
 * 2) Stage 2: execute one approved command candidate.
 * 3) Stage 3: return final grounded answer JSON using execution + refreshed context.
 */
public final class SteveAiThreeStageAgent {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern APPROVE_PATTERN = Pattern.compile("^\\s*approve\\s+#?(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPLICIT_APPROVE_PATTERN = Pattern.compile(
        "\\b(yes|yeah|yep|sure|ok|okay|go ahead|do it|run|execute|proceed|confirm)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Map<UUID, PendingPlan> PENDING_BY_PLAYER = new ConcurrentHashMap<>();

    private static volatile Map<String, Boolean> toolMutatesCache = null;

    private SteveAiThreeStageAgent() {}

    public static String process(ServerLevel serverLevel, UUID playerUuid, String message, String fileContext) {
        Integer approvedIndex = parseApprovalIndex(message);
        if (approvedIndex != null) {
            return runApprovedCandidate(serverLevel, playerUuid, approvedIndex);
        }

        PendingPlan pending = PENDING_BY_PLAYER.get(playerUuid);
        Integer implicitApprovedIndex = inferImplicitApprovalIndex(message, pending);
        if (implicitApprovedIndex != null) {
            return runApprovedCandidate(serverLevel, playerUuid, implicitApprovedIndex);
        }

        return planCandidates(serverLevel, playerUuid, message, fileContext);
    }

    private static String planCandidates(ServerLevel serverLevel, UUID playerUuid, String question, String fileContext) {
        String toolCatalogJson = loadToolCommandsJson();

        String prompt =
            "You are an AI planner for a Minecraft mod assistant.\n" +
            "Return ONLY valid JSON (no markdown, no prose).\n" +
            "Schema:\n" +
            "{\n" +
            "  \"question\": string,\n" +
            "  \"candidates\": [\n" +
            "    {\"type\": \"answer\", \"text\": string, \"confidence\": number},\n" +
            "    {\"type\": \"command\", \"tool\": string, \"args\": [string], \"purpose\": string, \"confidence\": number, \"requiresApproval\": true, \"mutatesWorld\": boolean}\n" +
            "  ]\n" +
            "}\n" +
            "Rules:\n" +
            "- Include 1 answer candidate and 1-3 command candidates if useful.\n" +
            "- Confidence is 0.0-1.0.\n" +
            "- Command tool names must match tools from catalog.\n" +
            "- Always set requiresApproval=true for command candidates.\n" +
            "- Keep args minimal and concrete.\n\n" +
            "Tool catalog:\n" + toolCatalogJson + "\n\n" +
            "Game context:\n" + fileContext + "\n\n" +
            "Player question: " + question;

        String raw = OpenAiService.ask(prompt);
        JsonObject planned = parseJsonObject(raw);
        if (planned == null) {
            planned = fallbackPlan(question);
        }

        normalizePlan(planned, question);

        PendingPlan pending = new PendingPlan(question, planned);
        PENDING_BY_PLAYER.put(playerUuid, pending);

        planned.addProperty("stage", "stage1_candidates");
        planned.addProperty("createdAt", Instant.ofEpochMilli(pending.createdAtMs).toString());
        planned.addProperty("approvalHint", "Reply with: approve <candidateNumber>");

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
        String type = getString(candidate, "type", "");
        if (!"command".equalsIgnoreCase(type)) {
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
            "You are SteveAI. Give a short grounded answer based on the question, executed command, and context.\n" +
            "Keep under 3 sentences.\n\n" +
            "Question: " + pending.question + "\n" +
            "Executed command: " + commandText + "\n" +
            "Execution status: " + (dispatched ? "dispatched" : "failed") + "\n" +
            (execError == null ? "" : ("Execution error: " + execError + "\n")) +
            "\nContext:\n" + refreshedContext;

        String finalAnswer = OpenAiService.ask(finalPrompt);

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
        out.addProperty("finalAnswer", finalAnswer == null ? "" : finalAnswer.trim());

        // Consume the plan after an approval action.
        PENDING_BY_PLAYER.remove(playerUuid);

        return GSON.toJson(out);
    }

    private static void normalizePlan(JsonObject planned, String fallbackQuestion) {
        if (!planned.has("question") || !planned.get("question").isJsonPrimitive()) {
            planned.addProperty("question", fallbackQuestion == null ? "" : fallbackQuestion);
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
            String type = getString(c, "type", "").toLowerCase(Locale.ROOT);

            if ("command".equals(type)) {
                c.addProperty("requiresApproval", true);

                String tool = getString(c, "tool", "");
                boolean mutates = mutatesMap.getOrDefault(tool, false);
                c.addProperty("mutatesWorld", mutates);

                if (!c.has("args") || !c.get("args").isJsonArray()) {
                    c.add("args", new JsonArray());
                }
            } else if ("answer".equals(type)) {
                if (!c.has("text")) c.addProperty("text", "No answer candidate provided.");
            }

            double confidence = c.has("confidence") && c.get("confidence").isJsonPrimitive()
                ? c.get("confidence").getAsDouble() : 0.5;
            if (confidence < 0.0) confidence = 0.0;
            if (confidence > 1.0) confidence = 1.0;
            c.addProperty("confidence", confidence);
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
            String type = getString(candidate, "type", "");
            if (!"command".equalsIgnoreCase(type)) {
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

    private static JsonObject fallbackPlan(String question) {
        JsonObject root = new JsonObject();
        root.addProperty("question", question == null ? "" : question);

        JsonArray candidates = new JsonArray();

        JsonObject answer = new JsonObject();
        answer.addProperty("type", "answer");
        answer.addProperty("text", "I am not fully sure yet. I can run a scan command to verify.");
        answer.addProperty("confidence", 0.35);
        candidates.add(answer);

        JsonObject cmd = new JsonObject();
        cmd.addProperty("type", "command");
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
        final long createdAtMs;

        PendingPlan(String question, JsonObject plan) {
            this.question = question == null ? "" : question;
            this.plan = plan == null ? new JsonObject() : plan.deepCopy();
            this.createdAtMs = System.currentTimeMillis();
        }
    }
}
