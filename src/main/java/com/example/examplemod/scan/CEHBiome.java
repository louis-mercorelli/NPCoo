package com.example.examplemod.scan;

import com.example.examplemod.steveAI.SteveAiLocator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

public final class CEHBiome {

    private CEHBiome() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildBiomeHereCommand() {
        return Commands.literal("biomeHere").executes(CEHBiome::handleBiomeHere);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBiomeMapCommand() {
        return Commands.literal("biomeMap")
            .executes(ctx -> CEHBiome.handleBiomeMapAtPlayer(ctx, 6, 8, false))
            .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 64))
                .executes(ctx -> CEHBiome.handleBiomeMapAtPlayer(
                    ctx,
                    IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                    8,
                    false
                ))
                .then(Commands.argument("sampleStep", IntegerArgumentType.integer(4, 32))
                    .executes(ctx -> CEHBiome.handleBiomeMapAtPlayer(
                        ctx,
                        IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                        IntegerArgumentType.getInteger(ctx, "sampleStep"),
                        false
                    ))
                    .then(Commands.literal("ForceLoad")
                        .executes(ctx -> CEHBiome.handleBiomeMapAtPlayer(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                            IntegerArgumentType.getInteger(ctx, "sampleStep"),
                            true
                        ))))
                .then(Commands.literal("ForceLoad")
                    .executes(ctx -> CEHBiome.handleBiomeMapAtPlayer(
                        ctx,
                        IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                        8,
                        true
                    ))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBiomeMapAtCommand() {
        return Commands.literal("biomeMapAt")
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 64))
                            .executes(ctx -> CEHBiome.handleBiomeMapAtPos(
                                ctx,
                                new BlockPos(
                                    IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"),
                                    IntegerArgumentType.getInteger(ctx, "z")
                                ),
                                IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                                8,
                                false
                            ))
                            .then(Commands.argument("sampleStep", IntegerArgumentType.integer(4, 32))
                                .executes(ctx -> CEHBiome.handleBiomeMapAtPos(
                                    ctx,
                                    new BlockPos(
                                        IntegerArgumentType.getInteger(ctx, "x"),
                                        IntegerArgumentType.getInteger(ctx, "y"),
                                        IntegerArgumentType.getInteger(ctx, "z")
                                    ),
                                    IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                                    IntegerArgumentType.getInteger(ctx, "sampleStep"),
                                    false
                                ))
                                .then(Commands.literal("ForceLoad")
                                    .executes(ctx -> CEHBiome.handleBiomeMapAtPos(
                                        ctx,
                                        new BlockPos(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z")
                                        ),
                                        IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                                        IntegerArgumentType.getInteger(ctx, "sampleStep"),
                                        true
                                    ))))
                            .then(Commands.literal("ForceLoad")
                                .executes(ctx -> CEHBiome.handleBiomeMapAtPos(
                                    ctx,
                                    new BlockPos(
                                        IntegerArgumentType.getInteger(ctx, "x"),
                                        IntegerArgumentType.getInteger(ctx, "y"),
                                        IntegerArgumentType.getInteger(ctx, "z")
                                    ),
                                    IntegerArgumentType.getInteger(ctx, "chunkRadius"),
                                    8,
                                    true
                                )))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBiomeLocateCommand() {
        return Commands.literal("biomeLocate")
            .then(Commands.argument("biome", StringArgumentType.word())
                .executes(ctx -> CEHBiome.handleBiomeLocate(
                    ctx,
                    StringArgumentType.getString(ctx, "biome"),
                    4096,
                    32,
                    64
                ))
                .then(Commands.argument("radius", IntegerArgumentType.integer(32, 200000))
                    .executes(ctx -> CEHBiome.handleBiomeLocate(
                        ctx,
                        StringArgumentType.getString(ctx, "biome"),
                        IntegerArgumentType.getInteger(ctx, "radius"),
                        32,
                        64
                    ))
                    .then(Commands.argument("horizontalInterval", IntegerArgumentType.integer(4, 256))
                        .executes(ctx -> CEHBiome.handleBiomeLocate(
                            ctx,
                            StringArgumentType.getString(ctx, "biome"),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "horizontalInterval"),
                            64
                        ))
                        .then(Commands.argument("verticalInterval", IntegerArgumentType.integer(4, 256))
                            .executes(ctx -> CEHBiome.handleBiomeLocate(
                                ctx,
                                StringArgumentType.getString(ctx, "biome"),
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                IntegerArgumentType.getInteger(ctx, "horizontalInterval"),
                                IntegerArgumentType.getInteger(ctx, "verticalInterval")
                            ))))));
    }

    public static int handleBiomeHere(CommandContext<CommandSourceStack> context) {
        return handleBiomeMapAtPlayer(context, 3, 8, false);
    }

    public static int handleBiomeMapAtPlayer(
        CommandContext<CommandSourceStack> context,
        int chunkRadius,
        int sampleStep,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("biomeMap requires a player command source."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        BlockPos playerPos = player.blockPosition();
        BlockPos stevePos = steveAi == null ? null : steveAi.blockPosition();
        SteveAiBiomeSurvey.BiomeSurveyResult result = SteveAiBiomeSurvey.surveyAround(
            serverLevel,
            playerPos,
            playerPos,
            stevePos,
            chunkRadius,
            sampleStep,
            forceLoad
        );

        return sendSurveyResponse(source, serverLevel, "biomeMap", result);
    }

    public static int handleBiomeMapAtPos(
        CommandContext<CommandSourceStack> context,
        BlockPos center,
        int chunkRadius,
        int sampleStep,
        boolean forceLoad
    ) {
        CommandSourceStack source = context.getSource();
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        SteveAiBiomeSurvey.BiomeSurveyResult result = SteveAiBiomeSurvey.surveyAround(
            serverLevel,
            center,
            player == null ? null : player.blockPosition(),
            steveAi == null ? null : steveAi.blockPosition(),
            chunkRadius,
            sampleStep,
            forceLoad
        );

        return sendSurveyResponse(source, serverLevel, "biomeMapAt", result);
    }

    public static int handleBiomeLocate(
        CommandContext<CommandSourceStack> context,
        String biomeQuery,
        int radius,
        int horizontalInterval,
        int verticalInterval
    ) {
        CommandSourceStack source = context.getSource();
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("biomeLocate requires a player command source."));
            return 0;
        }

        SteveAiBiomeSurvey.LocateBiomeResult result = SteveAiBiomeSurvey.locateNearestBiome(
            serverLevel,
            player.blockPosition(),
            biomeQuery,
            radius,
            horizontalInterval,
            verticalInterval
        );

        if (!result.found) {
            source.sendFailure(Component.literal("biomeLocate: " + result.message));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "biomeLocate: query=" + result.queryBiomeId
                + " found=" + result.foundBiomeId
                + " pos=" + result.foundPos.toShortString()
                + " manhattanDistance=" + result.manhattanDistance
        ), false);
        return 1;
    }

    private static int sendSurveyResponse(
        CommandSourceStack source,
        ServerLevel serverLevel,
        String prefix,
        SteveAiBiomeSurvey.BiomeSurveyResult result
    ) {
        Path reportFile = null;
        try {
            reportFile = SteveAiBiomeSurvey.writeSurveyReport(serverLevel, prefix, result);
        } catch (IOException ignored) {
        }

        Path finalReportFile = reportFile;
        source.sendSuccess(() -> Component.literal(
            prefix + ": centerBiome=" + result.centerBiomeId
                + " playerBiome=" + result.playerBiomeId
                + " steveBiome=" + result.steveBiomeId
                + " loadedChunks=" + result.loadedChunkCount
                + " sampledCells=" + result.sampledCellCount
                + " areas=" + result.areas.size()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "playerTouching=" + String.join(", ", result.playerTouchingBiomes.isEmpty() ? java.util.List.of("(none)") : result.playerTouchingBiomes)
                + " | steveTouching=" + String.join(", ", result.steveTouchingBiomes.isEmpty() ? java.util.List.of("(none)") : result.steveTouchingBiomes)
        ), false);
        if (!result.areas.isEmpty()) {
            SteveAiBiomeSurvey.BiomeArea top = result.areas.get(0);
            source.sendSuccess(() -> Component.literal(
                "nearestArea=" + top.biomeId
                    + " bbox=" + top.minX + "," + top.minY + "," + top.minZ
                    + " -> " + top.maxX + "," + top.maxY + "," + top.maxZ
                    + " center=" + top.centerX + "," + top.centerY + "," + top.centerZ
            ), false);
        }
        if (finalReportFile != null) {
            source.sendSuccess(() -> Component.literal(prefix + " reportFile: " + finalReportFile.toAbsolutePath()), false);
        }
        return 1;
    }
}