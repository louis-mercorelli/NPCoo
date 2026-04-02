/**
 * File: oldTickExplore.java
 *
 * Main intent:
 * Defines oldTickExplore functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code OldTickExplore(...)}:
 *    Purpose: Prevents instantiation of this legacy exploration helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code countNearbyVillagers(...)}:
 *    Purpose: Counts nearby villagers around SteveAI to help confirm village exploration success.
 *    Input: ServerLevel serverLevel, Villager steveAi, double radius.
 *    Output: int.
 * 3) {@code stopExploreTask(...)}:
 *    Purpose: Clears the current exploration state and target tracking.
 *    Input: none.
 *    Output: void.
 * 4) {@code tickExplore(...)}:
 *    Purpose: Advances one exploration tick by moving SteveAI toward the center or a local patrol target.
 *    Input: ServerLevel serverLevel, Villager steveAi.
 *    Output: void.
 * 5) {@code pickNextExploreTarget(...)}:
 *    Purpose: Chooses the next unexplored patrol target around the active exploration center.
 *    Input: ServerLevel serverLevel.
 *    Output: BlockPos.
 * 6) {@code reachedExploreTarget(...)}:
 *    Purpose: Checks whether SteveAI is close enough to consider an exploration target reached.
 *    Input: Villager steveAi, BlockPos target.
 *    Output: boolean.
 * 7) {@code isGoodExploreTarget(...)}:
 *    Purpose: Checks whether an exploration target is in bounds, walkable, and has headroom.
 *    Input: ServerLevel serverLevel, BlockPos pos.
 *    Output: boolean.
 */
package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

class OldTickExplore {

    private OldTickExplore() {}

    static int countNearbyVillagers(ServerLevel serverLevel, Villager steveAi, double radius) {
        var nearby = serverLevel.getEntities(
            (Entity) null,
            steveAi.getBoundingBox().inflate(radius),
            e -> e instanceof Villager && e != steveAi && e.isAlive()
        );

        return nearby.size();
    }

    static void stopExploreTask() {
        CommandEvents.exploreActive = false;
        CommandEvents.explorePoi = "";
        CommandEvents.explorePoiType = "";
        CommandEvents.exploreCenter = null;
        CommandEvents.currentExploreTarget = null;
        CommandEvents.exploredTargets.clear();
        CommandEvents.nextExploreRepathGameTime = 0L;
    }

    static void tickExplore(ServerLevel serverLevel, Villager steveAi) {
        if (!CommandEvents.exploreActive || CommandEvents.exploreCenter == null) {
            return;
        }

        long gameTime = serverLevel.getGameTime();

        double distToCenterSq = steveAi.blockPosition().distSqr(CommandEvents.exploreCenter);
        if ("village_candidate".equals(CommandEvents.explorePoi) || "village".equals(CommandEvents.explorePoi)) {
            int villagerCount = countNearbyVillagers(serverLevel, steveAi, 20.0);

            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Explore village check: nearby villager count={}"), villagerCount);

            if (villagerCount > 1) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Explore complete: found village population near SteveAI."));
                stopExploreTask();
                return;
            }
        }

        if (distToCenterSq > 100.0) {
            if (gameTime >= CommandEvents.nextExploreRepathGameTime) {
                boolean ok = steveAi.getNavigation().moveTo(
                    CommandEvents.exploreCenter.getX() + 0.5,
                    CommandEvents.exploreCenter.getY(),
                    CommandEvents.exploreCenter.getZ() + 0.5,
                    0.9
                );
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Explore travel-to-poi center={} result={}"), CommandEvents.exploreCenter, ok);
                CommandEvents.nextExploreRepathGameTime = gameTime + 40;
            }
            return;
        }

        if (CommandEvents.currentExploreTarget == null || reachedExploreTarget(steveAi, CommandEvents.currentExploreTarget)) {
            if (CommandEvents.currentExploreTarget != null) {
                CommandEvents.exploredTargets.add(CommandEvents.currentExploreTarget.immutable());
            }

            CommandEvents.currentExploreTarget = pickNextExploreTarget(serverLevel);
            CommandEvents.nextExploreRepathGameTime = 0L;

            if (CommandEvents.currentExploreTarget == null) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("No valid exploration target around POI center {}"), CommandEvents.exploreCenter);
                return;
            }

            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Explore local target={}"), CommandEvents.currentExploreTarget);
        }

        if (gameTime >= CommandEvents.nextExploreRepathGameTime) {
            boolean ok = steveAi.getNavigation().moveTo(
                CommandEvents.currentExploreTarget.getX() + 0.5,
                CommandEvents.currentExploreTarget.getY(),
                CommandEvents.currentExploreTarget.getZ() + 0.5,
                0.9
            );
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Explore patrol target={} result={}"), CommandEvents.currentExploreTarget, ok);
            CommandEvents.nextExploreRepathGameTime = gameTime + 40;
        }
    }

    static BlockPos pickNextExploreTarget(ServerLevel serverLevel) {
        RandomSource random = serverLevel.random;

        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(CommandEvents.exploreRadius * 2 + 1) - CommandEvents.exploreRadius;
            int dz = random.nextInt(CommandEvents.exploreRadius * 2 + 1) - CommandEvents.exploreRadius;

            int x = CommandEvents.exploreCenter.getX() + dx;
            int z = CommandEvents.exploreCenter.getZ() + dz;
            BlockPos top = serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, 0, z)
            );

            BlockPos candidate = top.immutable();

            if (CommandEvents.exploredTargets.contains(candidate)) continue;
            if (!isGoodExploreTarget(serverLevel, candidate)) continue;

            return candidate;
        }

        return null;
    }

    static boolean reachedExploreTarget(Villager steveAi, BlockPos target) {
        return steveAi.blockPosition().distSqr(target) <= 9.0;
    }

    static boolean isGoodExploreTarget(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isInWorldBounds(pos)) return false;

        BlockState at = serverLevel.getBlockState(pos);
        BlockState above = serverLevel.getBlockState(pos.above());
        BlockState below = serverLevel.getBlockState(pos.below());

        if (!below.blocksMotion()) return false;
        if (!at.isAir()) return false;
        if (!above.isAir()) return false;

        return true;
    }
}
