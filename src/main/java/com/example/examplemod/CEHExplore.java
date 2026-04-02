/**
 * File: CEHExplore.java
 *
 * Main intent:
 * Defines CEHExplore functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHExplore(...)}:
 *    Purpose: Prevents instantiation of this static explore-command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code handleExplorePoi(...)}:
 *    Purpose: Starts exploration toward the nearest known POI of the requested type.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code handleExploreStop(...)}:
 *    Purpose: Stops the active exploration task and clears exploration state.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 4) {@code handleExploreStatus(...)}:
 *    Purpose: Reports the current exploration target, center, and radius.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 5) {@code mapExplorePoiToPoiType(...)}:
 *    Purpose: Maps user-facing explore keywords onto the internal POI type names.
 *    Input: String poi.
 *    Output: String.
 */
package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.example.examplemod.poi.PoiManager;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHExplore {

    private CEHExplore() {}

    static int handleExplorePoi(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String poi = StringArgumentType.getString(context, "poi").toLowerCase(Locale.ROOT).trim();
        String poiType = mapExplorePoiToPoiType(poi);

        if (poiType == null) {
            source.sendFailure(Component.literal("Unsupported explore POI: " + poi));
            return 0;
        }

        BlockPos nearest = poi.equals("village_candidate") || poi.equals("village")
            ? PoiManager.findNearestVillageForExplore(steveAi.blockPosition())
            : PoiManager.findNearestPoiCenter(poiType, steveAi.blockPosition());

        if (nearest == null) {
            source.sendFailure(Component.literal("No known " + poi + " POI found."));
            return 0;
        }

        CommandEvents.exploreActive = true;
        CommandEvents.explorePoi = poi;
        CommandEvents.explorePoiType = poiType;
        CommandEvents.exploreCenter = nearest.immutable();
        CommandEvents.exploreRadius = 24;
        CommandEvents.currentExploreTarget = null;
        CommandEvents.exploredTargets.clear();
        CommandEvents.nextExploreRepathGameTime = 0L;

        source.sendSuccess(() -> Component.literal(
            "SteveAI exploring nearest " + poi
            + " at " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
        ), false);

        CommandEvents.LOGGER.info(
            com.sai.NpcooLog.tag("Explore started: poi={} poiType={} center={}"),
            CommandEvents.explorePoi, CommandEvents.explorePoiType, CommandEvents.exploreCenter
        );
        return 1;
    }

    static int handleExploreStop(CommandContext<CommandSourceStack> context) {
        OldTickExplore.stopExploreTask();
        context.getSource().sendSuccess(() -> Component.literal("SteveAI exploration stopped."), false);
        return 1;
    }

    static int handleExploreStatus(CommandContext<CommandSourceStack> context) {
        if (!CommandEvents.exploreActive || CommandEvents.exploreCenter == null) {
            context.getSource().sendSuccess(() -> Component.literal("No active exploration task."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "Exploring poi=" + CommandEvents.explorePoi
            + " poiType=" + CommandEvents.explorePoiType
            + " center=" + CommandEvents.exploreCenter.getX()
                + "," + CommandEvents.exploreCenter.getY()
                + "," + CommandEvents.exploreCenter.getZ()
            + " radius=" + CommandEvents.exploreRadius
            + " target=" + (CommandEvents.currentExploreTarget == null
                ? "none"
                : CommandEvents.currentExploreTarget.toShortString())
        ), false);

        return 1;
    }

    private static String mapExplorePoiToPoiType(String poi) {
        return switch (poi) {
            case "village" -> "village_candidate";
            case "dungeon" -> "dungeon";
            case "trial", "trial_chamber" -> "trial_chamber";
            case "archaeology", "archaeology_site" -> "archaeology_site";
            default -> null;
        };
    }
}
