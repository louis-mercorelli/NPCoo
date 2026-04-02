/**
 * File: CEHDebug.java
 *
 * Main intent:
 * Defines CEHDebug functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHDebug(...)}:
 *    Purpose: Prevents instantiation of this static debug-command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code handleDebugScreenToggle(...)}:
 *    Purpose: Toggles whether SteveAI screen debug messages are shown and logs who changed the setting.
 *    Input: CommandContext<CommandSourceStack> context, boolean enabled.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class CEHDebug {

    private CEHDebug() {}

    static int handleDebugScreenToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandEvents.screenDebugEnabled = enabled;
        context.getSource().sendSuccess(
            () -> Component.literal("SteveAI screen debug messages " + (enabled ? "enabled" : "disabled") + "."),
            false
        );
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("SteveAI screen debug messages set to {} by {}"),
            enabled, context.getSource().getTextName()
        );
        return 1;
    }
}
