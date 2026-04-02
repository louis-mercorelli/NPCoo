package com.example.examplemod;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

final class CEHNavigationChunk {

    private CEHNavigationChunk() {}

    static int handleForceChunkOn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi((ServerLevel) player.level());
        if (steveAi == null) {
            source.sendFailure(Component.literal("Could not find steveAI in loaded chunks."));
            return 0;
        }

        CommandEvents.steveAiChunkForceEnabled = true;
        SteveAiChunkForcing.updateForcedChunkForSteveAi((ServerLevel) player.level(), steveAi);
        source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing enabled."), false);
        return 1;
    }

    static int handleForceChunkOff(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        CommandEvents.steveAiChunkForceEnabled = false;
        SteveAiChunkForcing.clearForcedSteveAiChunk((ServerLevel) player.level());
        source.sendSuccess(() -> Component.literal("§6[testmod] SteveAI chunk forcing disabled."), false);
        return 1;
    }

    static int handleWhereRu(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);

        if (steveAi != null) {
            String msg = String.format(
                "§6[testmod] steveAI is loaded now at dimension=%s x=%.2f y=%.2f z=%.2f",
                steveAi.level().dimension(),
                steveAi.getX(), steveAi.getY(), steveAi.getZ()
            );
            source.sendSuccess(() -> Component.literal(msg), false);
            CommandEvents.LOGGER.info(
                "whereRu loaded -> dimension={} x={} y={} z={}",
                steveAi.level().dimension(), steveAi.getX(), steveAi.getY(), steveAi.getZ()
            );
            return 1;
        }

        if (CommandEvents.lastSteveAiKnownPos != null && CommandEvents.lastSteveAiKnownDimension != null) {
            String msg = String.format(
                "§6[testmod] steveAI is not loaded. Last known position: dimension=%s x=%d y=%d z=%d",
                CommandEvents.lastSteveAiKnownDimension,
                CommandEvents.lastSteveAiKnownPos.getX(),
                CommandEvents.lastSteveAiKnownPos.getY(),
                CommandEvents.lastSteveAiKnownPos.getZ()
            );
            source.sendSuccess(() -> Component.literal(msg), false);
            CommandEvents.LOGGER.info(
                "whereRu unloaded -> last known dimension={} x={} y={} z={}",
                CommandEvents.lastSteveAiKnownDimension,
                CommandEvents.lastSteveAiKnownPos.getX(),
                CommandEvents.lastSteveAiKnownPos.getY(),
                CommandEvents.lastSteveAiKnownPos.getZ()
            );
            return 1;
        }

        source.sendFailure(Component.literal("Could not find steveAI, and no last known position is recorded."));
        return 0;
    }

    static int handleTele(CommandContext<CommandSourceStack> context) {
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

        BlockPos safePos = findSafeTeleportPosNearPlayer(serverLevel, player);
        if (safePos == null) {
            source.sendFailure(Component.literal("Could not find a safe place to teleport steveAI."));
            return 0;
        }

        steveAi.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        steveAi.getNavigation().stop();

        source.sendSuccess(() -> Component.literal(String.format(
            "§6[testmod] steveAI teleported to x=%d y=%d z=%d",
            safePos.getX(), safePos.getY(), safePos.getZ()
        )), false);
        CommandEvents.LOGGER.info(
            "steveAI teleported safely to x={}, y={}, z={}",
            safePos.getX(), safePos.getY(), safePos.getZ()
        );
        return 1;
    }

    static BlockPos findSafeTeleportPosNearPlayer(ServerLevel serverLevel, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos base = playerPos.offset(dx, 0, dz);

                    BlockPos safe = findSafeStandingPos(serverLevel, base);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }

        return findSafeStandingPos(serverLevel, playerPos);
    }

    static BlockPos findSafeStandingPos(ServerLevel serverLevel, BlockPos nearPos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos feetPos = nearPos.offset(0, dy, 0);
            BlockPos headPos = feetPos.above();
            BlockPos groundPos = feetPos.below();

            var feetState = serverLevel.getBlockState(feetPos);
            var headState = serverLevel.getBlockState(headPos);
            var groundState = serverLevel.getBlockState(groundPos);

            boolean feetClear = feetState.isAir();
            boolean headClear = headState.isAir();
            boolean solidGround = groundState.isCollisionShapeFullBlock(serverLevel, groundPos);

            if (feetClear && headClear && solidGround) {
                return feetPos;
            }
        }

        return null;
    }
}
