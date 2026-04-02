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
