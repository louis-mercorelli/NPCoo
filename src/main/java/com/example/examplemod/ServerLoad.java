/**
 * File: ServerLoad.java
 *
 * Main intent:
 * Defines ServerLoad functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code ServerLoad(...)}:
 *    Purpose: Constructs ServerLoad.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code isWarmingUp(...)}:
 *    Purpose: Checks whether is warming up.
 *    Input: none.
 *    Output: boolean.
 * 3) {@code isHeartbeatLoggingEnabled(...)}:
 *    Purpose: Checks whether is heartbeat logging enabled.
 *    Input: none.
 *    Output: boolean.
 * 4) {@code setHeartbeatLoggingEnabled(...)}:
 *    Purpose: Sets set heartbeat logging enabled.
 *    Input: boolean enabled.
 *    Output: void.
 * 5) {@code getLoadEmaAlpha(...)}:
 *    Purpose: Returns get load ema alpha.
 *    Input: none.
 *    Output: double.
 * 6) {@code getIdleMaxMspt(...)}:
 *    Purpose: Returns get idle max mspt.
 *    Input: none.
 *    Output: double.
 * 7) {@code getBusyMaxMspt(...)}:
 *    Purpose: Returns get busy max mspt.
 *    Input: none.
 *    Output: double.
 * 8) {@code getBehindLagDebtMs(...)}:
 *    Purpose: Returns get behind lag debt ms.
 *    Input: none.
 *    Output: double.
 * 9) {@code tune(...)}:
 *    Purpose: Performs tune.
 *    Input: double idleMspt, double busyMspt, double behindDebtMs, double emaAlpha.
 *    Output: void.
 * 10) {@code resetTune(...)}:
 *    Purpose: Resets reset tune.
 *    Input: none.
 *    Output: void.
 * 11) {@code onServerTickPre(...)}:
 *    Purpose: Performs on server tick pre.
 *    Input: none.
 *    Output: void.
 * 12) {@code onServerTickPost(...)}:
 *    Purpose: Performs on server tick post.
 *    Input: net.minecraft.server.MinecraftServer server.
 *    Output: void.
 * 13) {@code buildServerLoadMessage(...)}:
 *    Purpose: Builds build server load message.
 *    Input: ServerLevel level, boolean includeTune.
 *    Output: String.
 * 14) {@code updateServerLoadMetrics(...)}:
 *    Purpose: Updates update server load metrics.
 *    Input: none.
 *    Output: void.
 * 15) {@code maybeSendServerLoadStatus(...)}:
 *    Purpose: Performs maybe send server load status.
 *    Input: net.minecraft.server.MinecraftServer server.
 *    Output: void.
 * 16) {@code classifyServerLoadState(...)}:
 *    Purpose: Classifies classify server load state.
 *    Input: double mspt, double currentLagDebtMs.
 *    Output: String.
 * 17) {@code colorForServerLoadState(...)}:
 *    Purpose: Performs color for server load state.
 *    Input: String state.
 *    Output: String.
 * 18) {@code buildBehindPart(...)}:
 *    Purpose: Builds build behind part.
 *    Input: double mspt, double currentLagDebtMs, boolean behind.
 *    Output: String.
 * 19) {@code recordServerLoadState(...)}:
 *    Purpose: Performs record server load state.
 *    Input: String state.
 *    Output: void.
 * 20) {@code buildServerLoadSummaryMessage(...)}:
 *    Purpose: Builds build server load summary message.
 *    Input: none.
 *    Output: String.
 * 21) {@code summarizeRecentServerLoadStates(...)}:
 *    Purpose: Performs summarize recent server load states.
 *    Input: int windowSeconds.
 *    Output: String.
 * 22) {@code stripMinecraftColorCodes(...)}:
 *    Purpose: Performs strip minecraft color codes.
 *    Input: String text.
 *    Output: String.
 * 23) {@code add(...)}:
 *    Purpose: Performs add.
 *    Input: String state.
 *    Output: void.
 * 24) {@code format(...)}:
 *    Purpose: Performs format.
 *    Input: int windowSeconds.
 *    Output: String.
 */
package com.example.examplemod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import net.minecraft.server.level.ServerLevel;

final class ServerLoad {

    static final double SERVER_TICK_BUDGET_MS = 50.0;
    static final double LOAD_EMA_ALPHA_DEFAULT = 0.08;
    static final double IDLE_MAX_MSPT_DEFAULT = 12.0;
    static final double BUSY_MAX_MSPT_DEFAULT = 20.0;
    static final double BEHIND_LAG_DEBT_MS_DEFAULT = 100.0;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    private static final double BEHIND_LOAD_MULTIPLIER = 1.5;
    private static final long SERVER_LOAD_SAMPLE_INTERVAL_TICKS = 20L;
    private static final long SERVER_LOAD_SUMMARY_INTERVAL_TICKS = 1200L;
    private static final int SERVER_LOAD_HISTORY_MAX_SAMPLES = 60;
    private static final int[] SERVER_LOAD_SUMMARY_WINDOWS = {60, 30, 20, 10};

    private static double loadEmaAlpha = LOAD_EMA_ALPHA_DEFAULT;
    private static double idleMaxMspt = IDLE_MAX_MSPT_DEFAULT;
    private static double busyMaxMspt = BUSY_MAX_MSPT_DEFAULT;
    private static double behindLagDebtMs = BEHIND_LAG_DEBT_MS_DEFAULT;

    private static long serverTickStartNs = 0L;
    private static long lastMeasuredTickNs = 0L;
    private static long measuredTickSamples = 0L;
    private static double rollingMspt = 0.0;
    private static double lagDebtMs = 0.0;

    private static boolean heartbeatLoggingEnabled = true;
    private static long nextServerLoadSampleGameTime = 0L;
    private static long nextServerLoadSummaryGameTime = 0L;
    private static final Deque<String> recentServerLoadStates = new ArrayDeque<>();

    private ServerLoad() {
    }

    static boolean isWarmingUp() {
        return measuredTickSamples == 0L;
    }

    static boolean isHeartbeatLoggingEnabled() {
        return heartbeatLoggingEnabled;
    }

    static void setHeartbeatLoggingEnabled(boolean enabled) {
        heartbeatLoggingEnabled = enabled;
    }

    static double getLoadEmaAlpha() {
        return loadEmaAlpha;
    }

    static double getIdleMaxMspt() {
        return idleMaxMspt;
    }

    static double getBusyMaxMspt() {
        return busyMaxMspt;
    }

    static double getBehindLagDebtMs() {
        return behindLagDebtMs;
    }

    static void tune(double idleMspt, double busyMspt, double behindDebtMs, double emaAlpha) {
        idleMaxMspt = idleMspt;
        busyMaxMspt = busyMspt;
        behindLagDebtMs = behindDebtMs;
        loadEmaAlpha = emaAlpha;
    }

    static void resetTune() {
        idleMaxMspt = IDLE_MAX_MSPT_DEFAULT;
        busyMaxMspt = BUSY_MAX_MSPT_DEFAULT;
        behindLagDebtMs = BEHIND_LAG_DEBT_MS_DEFAULT;
        loadEmaAlpha = LOAD_EMA_ALPHA_DEFAULT;
    }

    static void onServerTickPre() {
        serverTickStartNs = System.nanoTime();
    }

    static void onServerTickPost(net.minecraft.server.MinecraftServer server) {
        updateServerLoadMetrics();
        maybeSendServerLoadStatus(server);
    }

    static String buildServerLoadMessage(ServerLevel level, boolean includeTune) {
        int playerCount = level.getServer().getPlayerCount();

        double mspt = rollingMspt;
        double loadPct = Math.min(999.0, (mspt / SERVER_TICK_BUDGET_MS) * 100.0);
        String state = classifyServerLoadState(mspt, lagDebtMs);
        String color = colorForServerLoadState(state);
        String behindPart = buildBehindPart(mspt, lagDebtMs, "BEHIND".equals(state));

        if (includeTune) {
            return String.format(
                Locale.ROOT,
                "%s[serverLoad] state=%s mspt=%.2f load=%.0f%% players=%d tick=%.2fms samples=%d %s tune(idle<=%.1f busy<=%.1f debt>=%.0f alpha=%.2f)§r",
                color,
                state,
                mspt,
                loadPct,
                playerCount,
                lastMeasuredTickNs / 1_000_000.0,
                measuredTickSamples,
                behindPart,
                idleMaxMspt,
                busyMaxMspt,
                behindLagDebtMs,
                loadEmaAlpha
            );
        }

        return String.format(
            Locale.ROOT,
            "%s[serverLoad] %s mspt=%.2f load=%.0f%% players=%d %s§r",
            color,
            state,
            mspt,
            loadPct,
            playerCount,
            behindPart
        );
    }

    private static void updateServerLoadMetrics() {
        if (serverTickStartNs == 0L) {
            return;
        }

        long elapsedNs = System.nanoTime() - serverTickStartNs;
        if (elapsedNs <= 0L) {
            return;
        }

        serverTickStartNs = 0L;
        lastMeasuredTickNs = elapsedNs;
        measuredTickSamples++;

        double tickMs = elapsedNs / 1_000_000.0;
        if (measuredTickSamples == 1L) {
            rollingMspt = tickMs;
        } else {
            rollingMspt += (tickMs - rollingMspt) * loadEmaAlpha;
        }

        lagDebtMs += (tickMs - SERVER_TICK_BUDGET_MS);
        if (lagDebtMs < 0.0) {
            lagDebtMs = 0.0;
        }
    }

    private static void maybeSendServerLoadStatus(net.minecraft.server.MinecraftServer server) {
        if (measuredTickSamples == 0L) {
            return;
        }

        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }

        long now = level.getGameTime();
        if (nextServerLoadSampleGameTime == 0L) {
            nextServerLoadSampleGameTime = now;
        }
        if (nextServerLoadSummaryGameTime == 0L) {
            nextServerLoadSummaryGameTime = now + SERVER_LOAD_SUMMARY_INTERVAL_TICKS;
        }

        if (now >= nextServerLoadSampleGameTime) {
            nextServerLoadSampleGameTime = now + SERVER_LOAD_SAMPLE_INTERVAL_TICKS;

            String state = classifyServerLoadState(rollingMspt, lagDebtMs);
            recordServerLoadState(state);

            if (heartbeatLoggingEnabled) {
                String heartbeat = buildServerLoadMessage(level, false);
                LOGGER.info(com.sai.NpcooLog.tag(stripMinecraftColorCodes(heartbeat)));
            }
        }

        if (now >= nextServerLoadSummaryGameTime) {
            nextServerLoadSummaryGameTime = now + SERVER_LOAD_SUMMARY_INTERVAL_TICKS;
            LOGGER.info(com.sai.NpcooLog.tag(buildServerLoadSummaryMessage()));
        }
    }

    private static String classifyServerLoadState(double mspt, double currentLagDebtMs) {
        double behindLoadMsptThreshold = SERVER_TICK_BUDGET_MS * BEHIND_LOAD_MULTIPLIER;
        boolean behind = mspt > behindLoadMsptThreshold && currentLagDebtMs > behindLagDebtMs;
        if (behind) {
            return "BEHIND";
        }
        if (mspt <= idleMaxMspt) {
            return "IDLE";
        }
        if (mspt <= busyMaxMspt) {
            return "BUSY";
        }
        return "VERY_BUSY";
    }

    private static String colorForServerLoadState(String state) {
        return switch (state) {
            case "IDLE" -> "§a";
            case "BUSY" -> "§e";
            case "VERY_BUSY" -> "§6";
            default -> "§c";
        };
    }

    private static String buildBehindPart(double mspt, double currentLagDebtMs, boolean behind) {
        if (!behind && currentLagDebtMs <= 1.0) {
            return "behindEst=0ms";
        }

        double sparePerSecond = Math.max(0.0, (SERVER_TICK_BUDGET_MS - mspt) * 20.0);
        if (sparePerSecond > 0.0 && currentLagDebtMs > 0.0) {
            double catchupSec = currentLagDebtMs / sparePerSecond;
            return String.format(Locale.ROOT, "behindEst=%.0fms catchUp~=%.1fs", currentLagDebtMs, catchupSec);
        }

        return String.format(Locale.ROOT, "behindEst=%.0fms catchUp~=not_while_over_budget", currentLagDebtMs);
    }

    private static void recordServerLoadState(String state) {
        recentServerLoadStates.addLast(state);
        while (recentServerLoadStates.size() > SERVER_LOAD_HISTORY_MAX_SAMPLES) {
            recentServerLoadStates.removeFirst();
        }
    }

    private static String buildServerLoadSummaryMessage() {
        if (recentServerLoadStates.isEmpty()) {
            return "[serverLoadSummary] no samples collected yet";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[serverLoadSummary] samples=")
            .append(recentServerLoadStates.size());

        for (int window : SERVER_LOAD_SUMMARY_WINDOWS) {
            sb.append(' ')
                .append(summarizeRecentServerLoadStates(window));
        }

        return sb.toString();
    }

    private static String summarizeRecentServerLoadStates(int windowSeconds) {
        ServerLoadWindowCounts counts = new ServerLoadWindowCounts();
        int available = Math.min(windowSeconds, recentServerLoadStates.size());
        int skip = recentServerLoadStates.size() - available;
        int index = 0;

        for (String state : recentServerLoadStates) {
            if (index++ < skip) {
                continue;
            }
            counts.add(state);
        }

        return counts.format(windowSeconds);
    }

    private static String stripMinecraftColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("§.", "");
    }

    private static final class ServerLoadWindowCounts {
        private int idle;
        private int busy;
        private int veryBusy;
        private int behind;

        private void add(String state) {
            switch (state) {
                case "IDLE" -> idle++;
                case "BUSY" -> busy++;
                case "VERY_BUSY" -> veryBusy++;
                case "BEHIND" -> behind++;
                default -> {
                }
            }
        }

        private String format(int windowSeconds) {
            return String.format(
                Locale.ROOT,
                "%ds(IDLE=%d BUSY=%d VERY_BUSY=%d BEHIND=%d)",
                windowSeconds,
                idle,
                busy,
                veryBusy,
                behind
            );
        }
    }
}
