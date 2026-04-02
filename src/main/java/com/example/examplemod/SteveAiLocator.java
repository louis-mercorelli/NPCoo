package com.example.examplemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

public final class SteveAiLocator {

    public static final String STEVE_AI_NAME = "steveAI";
    public static final String STEVE_AI_TAG = "steveai_npc";

    private SteveAiLocator() {}

    public static boolean isSteveAi(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        if (entity.getTags().contains(STEVE_AI_TAG)) {
            return true;
        }

        return entity.hasCustomName()
            && entity.getCustomName() != null
            && STEVE_AI_NAME.equals(entity.getCustomName().getString());
    }

    public static Villager findSteveAi(ServerLevel serverLevel) {
        var matches = serverLevel.getEntities(
            EntityType.VILLAGER,
            new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
            SteveAiLocator::isSteveAi
        );

        if (matches.isEmpty()) {
            return null;
        }

        Entity e = matches.get(0);
        return e instanceof Villager v ? v : null;
    }

    static Entity findSteveAiAnywhere(ServerLevel serverLevel) {
        for (ServerLevel levelToCheck : serverLevel.getServer().getAllLevels()) {
            var matches = levelToCheck.getEntities(
                (Entity) null,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                SteveAiLocator::isSteveAi
            );

            if (!matches.isEmpty()) {
                Entity existing = matches.get(0);
                CommandEvents.LOGGER.info(
                    "Found existing steveAI in dimension {} at x={}, y={}, z={}",
                    levelToCheck.dimension(),
                    existing.getX(),
                    existing.getY(),
                    existing.getZ()
                );
                return existing;
            }
        }

        return null;
    }
}
