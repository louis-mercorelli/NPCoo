/**
 * File: CEHServerLoad.java
 *
 * Main intent:
 * Defines CEHServerLoad functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHServerLoad(...)}:
 *    Purpose: Prevents instantiation of this static server-load command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code handleServerLoadStatus(...)}:
 *    Purpose: Sends the current server-load status message to the command source.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code handleServerLoadTune(...)}:
 *    Purpose: Handles runtime tuning of the server-load thresholds and smoothing values.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 4) {@code handleServerLoadStreamStatus(...)}:
 *    Purpose: Reports whether periodic server-load heartbeat streaming is currently enabled.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 5) {@code handleServerLoadStreamToggle(...)}:
 *    Purpose: Enables or disables server-load heartbeat streaming and records who changed it.
 *    Input: CommandContext<CommandSourceStack> context, boolean enabled.
 *    Output: int.
 * 6) {@code handleServerLoadReset(...)}:
 *    Purpose: Restores the default server-load tuning configuration.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class CEHServerLoad {

    private CEHServerLoad() {}

    static int handleServerLoadStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        if (ServerLoad.isWarmingUp()) {
            source.sendSuccess(() -> Component.literal("§e[serverLoad] warming up: waiting for first tick samples§r"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(ServerLoad.buildServerLoadMessage(level, true)), false);
        return 1;
    }

    static int handleServerLoadTune(CommandContext<CommandSourceStack> context) {
        double newIdle = DoubleArgumentType.getDouble(context, "idleMspt");
        double newBusy = DoubleArgumentType.getDouble(context, "busyMspt");
        double newBehindDebt = DoubleArgumentType.getDouble(context, "behindDebtMs");
        double newAlpha = context.getNodes().stream().anyMatch(n -> "emaAlpha".equals(n.getNode().getName()))
            ? DoubleArgumentType.getDouble(context, "emaAlpha")
            : ServerLoad.getLoadEmaAlpha();

        if (newIdle > newBusy) {
            context.getSource().sendFailure(Component.literal("idleMspt must be <= busyMspt"));
            return 0;
        }

        ServerLoad.tune(newIdle, newBusy, newBehindDebt, newAlpha);

        context.getSource().sendSuccess(
            () -> Component.literal(String.format(
                Locale.ROOT,
                "§b[serverLoad] tuned idle<=%.1f busy<=%.1f debt>=%.0f alpha=%.2f§r",
                ServerLoad.getIdleMaxMspt(),
                ServerLoad.getBusyMaxMspt(),
                ServerLoad.getBehindLagDebtMs(),
                ServerLoad.getLoadEmaAlpha()
            )),
            false
        );
        return 1;
    }

    static int handleServerLoadStreamStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal(
                "§b[serverLoad] heartbeat stream "
                + (ServerLoad.isHeartbeatLoggingEnabled() ? "enabled" : "disabled")
                + "§r"),
            false
        );
        return 1;
    }

    static int handleServerLoadStreamToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        ServerLoad.setHeartbeatLoggingEnabled(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("§b[serverLoad] heartbeat stream " + (enabled ? "enabled" : "disabled") + "§r"),
            false
        );
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("serverLoad heartbeat stream {} by {}"),
            enabled ? "enabled" : "disabled",
            context.getSource().getTextName()
        );
        return 1;
    }

    static int handleServerLoadReset(CommandContext<CommandSourceStack> context) {
        ServerLoad.resetTune();
        context.getSource().sendSuccess(
            () -> Component.literal("§b[serverLoad] tuning reset to defaults§r"),
            false
        );
        return 1;
    }
}
