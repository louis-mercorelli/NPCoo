/**
 * File: SteveAiLocator.java
 *
 * Main intent:
 * Defines SteveAiLocator functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiLocator(...)}:
 *    Purpose: Prevents instantiation of this static SteveAI lookup helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code isSteveAi(...)}:
 *    Purpose: Checks whether an entity matches SteveAI by villager type, tag, or custom name.
 *    Input: Entity entity.
 *    Output: boolean.
 * 3) {@code findSteveAi(...)}:
 *    Purpose: Finds the SteveAI villager in the current server level.
 *    Input: ServerLevel serverLevel.
 *    Output: Villager.
 * 4) {@code findSteveAiAnywhere(...)}:
 *    Purpose: Searches every loaded dimension for an existing SteveAI entity.
 *    Input: ServerLevel serverLevel.
 *    Output: Entity.
 */
package com.example.examplemod.steveAI;

import com.example.examplemod.CommandEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

public final class SteveAiLocator {

    public static final String STEVE_AI_NAME = "steveAI";
    public static final String STEVE_AI_TAG = "steveai_npc";
    public static final String STEVE_AI_ENTITY_KEY = "examplemod:steveai";

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

    public static Entity findSteveAiAnywhere(ServerLevel serverLevel) {
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
