/**
 * File: CEHLookSee.java
 *
 * Main intent:
 * Defines CEHLookSee functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: CEHLookSee() {}.
 * 2) {@code context)(...)}:
 *    Purpose: Implements context) logic in this file.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code grouped)(...)}:
 *    Purpose: Implements grouped) logic in this file.
 *    Input: Map<String, SteveAiCollectors.SeenSummary> grouped.
 *    Output: int.
 * 4) {@code key)(...)}:
 *    Purpose: Implements key) logic in this file.
 *    Input: Map<String, SteveAiCollectors.SeenSummary> grouped, String key.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.context.CommandContext;

import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHLookSee {

    private CEHLookSee() {}

    static int handleLookSee(CommandContext<CommandSourceStack> context) {
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

        BlockPos center = steveAi.blockPosition();
        int chunkRadius = 5;

        long lookSeeStartNs = System.nanoTime();
        long blocksStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> blocks =
            SteveAiScanManager.scanB(serverLevel, center, chunkRadius, false);
        long blocksMs = (System.nanoTime() - blocksStartNs) / 1_000_000L;

        long entitiesStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> entities =
            SteveAiScanManager.scanE(serverLevel, center, chunkRadius, false);
        long entitiesMs = (System.nanoTime() - entitiesStartNs) / 1_000_000L;

        long blockEntitiesStartNs = System.nanoTime();
        Map<String, SteveAiCollectors.SeenSummary> blockEntities =
            SteveAiScanManager.scanBE(serverLevel, center, chunkRadius, false);
        long blockEntitiesMs = (System.nanoTime() - blockEntitiesStartNs) / 1_000_000L;

        long totalMs = (System.nanoTime() - lookSeeStartNs) / 1_000_000L;

        int blocksTotal = totalCount(blocks);
        int entitiesTotal = totalCount(entities);
        int blockEntitiesTotal = totalCount(blockEntities);

        int villagers = entryCount(entities, "minecraft:villager");
        int ironGolems = entryCount(entities, "minecraft:iron_golem");
        int bellsBlocks = entryCount(blocks, "minecraft:bell");
        int bellsBlockEntities = entryCount(blockEntities, "minecraft:bell");
        int beds = entryCount(blocks, "minecraft:bed");

        source.sendSuccess(() -> Component.literal(
            "lookSee center=" + center.toShortString()
                + " chunkRadius=" + chunkRadius
                + " groups(B/E/BE)=" + blocks.size() + "/" + entities.size() + "/" + blockEntities.size()
                + " totals(B/E/BE)=" + blocksTotal + "/" + entitiesTotal + "/" + blockEntitiesTotal
                + " signals[villager=" + villagers
                + ", iron_golem=" + ironGolems
                + ", bell(B/BE)=" + bellsBlocks + "/" + bellsBlockEntities
                + ", bed=" + beds + "]"
                + " time=" + totalMs + "ms"
                + " per(B/E/BE)=" + blocksMs + "/" + entitiesMs + "/" + blockEntitiesMs + "ms"
        ), false);

        return 1;
    }

    private static int totalCount(Map<String, SteveAiCollectors.SeenSummary> grouped) {
        if (grouped == null || grouped.isEmpty()) return 0;
        int total = 0;
        for (SteveAiCollectors.SeenSummary s : grouped.values()) {
            if (s != null) total += s.count;
        }
        return total;
    }

    private static int entryCount(Map<String, SteveAiCollectors.SeenSummary> grouped, String key) {
        if (grouped == null || key == null) return 0;
        SteveAiCollectors.SeenSummary s = grouped.get(key);
        return s == null ? 0 : s.count;
    }
}
