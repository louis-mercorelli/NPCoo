/**
 * File: SteveAiChunkForcing.java
 *
 * Main intent:
 * Defines SteveAiChunkForcing functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiChunkForcing(...)}:
 *    Purpose: Prevents instantiation of this static chunk-forcing helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code updateForcedChunkForSteveAi(...)}:
 *    Purpose: Moves the forced chunk marker to SteveAI's current chunk, unforcing the old one if needed.
 *    Input: ServerLevel serverLevel, Entity steveAiEntity.
 *    Output: void.
 * 3) {@code clearForcedSteveAiChunk(...)}:
 *    Purpose: Unforces the currently tracked SteveAI chunk and clears the cached chunk coordinates.
 *    Input: ServerLevel serverLevel.
 *    Output: void.
 */
package com.example.examplemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class SteveAiChunkForcing {

    private static Integer forcedSteveAiChunkX = null;
    private static Integer forcedSteveAiChunkZ = null;

    private SteveAiChunkForcing() {}

    static void updateForcedChunkForSteveAi(ServerLevel serverLevel, Entity steveAiEntity) {
        int chunkX = steveAiEntity.chunkPosition().x;
        int chunkZ = steveAiEntity.chunkPosition().z;

        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null
                && forcedSteveAiChunkX == chunkX && forcedSteveAiChunkZ == chunkZ) {
            return;
        }

        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null) {
            boolean removed = serverLevel.setChunkForced(forcedSteveAiChunkX, forcedSteveAiChunkZ, false);
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Unforced old steveAI chunk {},{} result={}"), forcedSteveAiChunkX, forcedSteveAiChunkZ, removed);
        }

        boolean added = serverLevel.setChunkForced(chunkX, chunkZ, true);
        forcedSteveAiChunkX = chunkX;
        forcedSteveAiChunkZ = chunkZ;

        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Forced steveAI chunk {},{} result={}"), chunkX, chunkZ, added);
    }

    static void clearForcedSteveAiChunk(ServerLevel serverLevel) {
        if (forcedSteveAiChunkX != null && forcedSteveAiChunkZ != null) {
            boolean removed = serverLevel.setChunkForced(forcedSteveAiChunkX, forcedSteveAiChunkZ, false);
            CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Cleared forced steveAI chunk {},{} result={}"), forcedSteveAiChunkX, forcedSteveAiChunkZ, removed);
        }

        forcedSteveAiChunkX = null;
        forcedSteveAiChunkZ = null;
    }
}
