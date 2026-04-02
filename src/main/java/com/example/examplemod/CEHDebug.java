/**
 * File: CEHDebug.java
 *
 * Main intent:
 * Defines CEHDebug functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: CEHDebug() {}.
 * 2) {@code enabled)(...)}:
 *    Purpose: Implements enabled) logic in this file.
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
