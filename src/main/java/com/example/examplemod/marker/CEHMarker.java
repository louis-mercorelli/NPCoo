package com.example.examplemod.marker;

import com.example.examplemod.ModNetworking;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CEHMarker {
    private static final Map<UUID, Set<BlockPos>> MARKERS_BY_PLAYER = new ConcurrentHashMap<>();

    private CEHMarker() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildSetMarkerCommand() {
        return buildMarkerCommand("setMarker");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMarkerCommand(String literalName) {
        return Commands.literal(literalName)
            .then(Commands.literal("list").executes(CEHMarker::handleList))
            .then(Commands.literal("resetALL").executes(CEHMarker::handleResetAll))
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> CEHMarker.handleSetMarker(ctx,
                            new BlockPos(
                                IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "y"),
                                IntegerArgumentType.getInteger(ctx, "z")
                            ),
                            true
                        ))
                        .then(Commands.literal("ON")
                            .executes(ctx -> CEHMarker.handleSetMarker(ctx,
                                new BlockPos(
                                    IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"),
                                    IntegerArgumentType.getInteger(ctx, "z")
                                ),
                                true
                            )))
                        .then(Commands.literal("OFF")
                            .executes(ctx -> CEHMarker.handleSetMarker(ctx,
                                new BlockPos(
                                    IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"),
                                    IntegerArgumentType.getInteger(ctx, "z")
                                ),
                                false
                            ))))));
    }

    public static int handleSetMarker(CommandContext<CommandSourceStack> context, BlockPos pos, boolean enabled) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("setMarker is a player-only command."));
            return 0;
        }

        UUID playerId = player.getUUID();
        Set<BlockPos> markers = MARKERS_BY_PLAYER.computeIfAbsent(playerId, k -> new LinkedHashSet<>());
        BlockPos immutable = pos.immutable();
        if (enabled) {
            markers.add(immutable);
        } else {
            markers.remove(immutable);
            if (markers.isEmpty()) {
                MARKERS_BY_PLAYER.remove(playerId);
            }
        }

        ModNetworking.CHANNEL.send(
            new SteveAiMarkerPacket(enabled ? SteveAiMarkerPacket.Action.SET_ON : SteveAiMarkerPacket.Action.SET_OFF, pos),
            PacketDistributor.PLAYER.with(player)
        );

        source.sendSuccess(() -> Component.literal(
            "setMarker " + pos.toShortString() + " " + (enabled ? "ON" : "OFF")
        ), false);
        return 1;
    }

    public static int handleResetAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("setMarker resetALL is player-only."));
            return 0;
        }

        MARKERS_BY_PLAYER.remove(player.getUUID());

        ModNetworking.CHANNEL.send(
            new SteveAiMarkerPacket(SteveAiMarkerPacket.Action.RESET_ALL, null),
            PacketDistributor.PLAYER.with(player)
        );

        source.sendSuccess(() -> Component.literal("setMarker resetALL"), false);
        return 1;
    }

    public static int handleList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("setMarker list is player-only."));
            return 0;
        }

        Set<BlockPos> markers = MARKERS_BY_PLAYER.getOrDefault(player.getUUID(), Set.of());
        if (markers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("setMarker list: no active markers"), false);
            return 1;
        }

        List<String> markerStrings = new ArrayList<>();
        for (BlockPos marker : markers) {
            markerStrings.add("(" + marker.getX() + "," + marker.getY() + "," + marker.getZ() + ")");
        }

        source.sendSuccess(() -> Component.literal(
            "setMarker list count=" + markers.size() + " markers=" + String.join("; ", markerStrings)
        ), false);
        return 1;
    }
}
