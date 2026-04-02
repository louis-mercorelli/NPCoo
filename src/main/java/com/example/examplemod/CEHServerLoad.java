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
