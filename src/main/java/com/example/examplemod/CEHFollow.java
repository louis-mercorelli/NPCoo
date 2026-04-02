/**
 * File: CEHFollow.java
 *
 * Main intent:
 * Defines CEHFollow functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: CEHFollow() {}.
 * 2) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 4) {@code speed)(...)}:
 *    Purpose: Implements speed) logic in this file.
 *    Input: ServerLevel serverLevel, ServerPlayer player, double speed.
 *    Output: boolean.
 * 5) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHFollow {

    private CEHFollow() {}

    static int handleFollowMe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI."));
            return 0;
        }

        CommandEvents.steveAiFollowMode = true;
        CommandEvents.steveAiFollowPlayerUuid = player.getUUID();
        boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);

        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI is now following you."), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("steveAI followMe enabled for player {}"), player.getName().getString());
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Initial follow path start result: {}"), started);
        return 1;
    }

    static int handleFindMe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI."));
            return 0;
        }

        boolean started = moveSteveAiTowardPlayer(serverLevel, player, 1.0D);
        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI is trying to find you."), false);
        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("steveAI findMe started for player {} result={}"),
            player.getName().getString(), started
        );
        return 1;
    }

    static boolean moveSteveAiTowardPlayer(ServerLevel serverLevel, ServerPlayer player, double speed) {
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            return false;
        }

        PathNavigation nav = steveAi.getNavigation();
        return nav.moveTo(player, speed);
    }

    static int handleStopFollow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommandEvents.steveAiFollowMode = false;
        CommandEvents.steveAiFollowPlayerUuid = null;

        if (source.getEntity() instanceof ServerPlayer player) {
            Villager steveAi = SteveAiLocator.findSteveAi((ServerLevel) player.level());
            if (steveAi != null) steveAi.getNavigation().stop();
        }

        source.sendSuccess(() -> Component.literal("§6[testmod] steveAI follow stopped."), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("steveAI follow mode disabled"));
        return 1;
    }
}
