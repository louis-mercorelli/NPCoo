package com.example.examplemod.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.example.examplemod.SteveAiContextFiles;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class CEHChat {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");

    private CEHChat() {}

    public static int handleSteveAiChat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String message = StringArgumentType.getString(context, "message");

        LOGGER.info(com.sai.NpcooLog.tag("TESTMOD asking OpenAI...: {}"), message);
        source.sendSuccess(() -> Component.literal("§6[testmod] Asking OpenAI: " + message), false);

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only command."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        UUID playerUuid = player.getUUID();

        String reply = askSteveAi(serverLevel, playerUuid, message);
        source.sendSuccess(() -> Component.literal("§6[testmod] OpenAI reply: " + reply), false);
        LOGGER.info(com.sai.NpcooLog.tag("ExampleMod OpenAI response... {}"), reply);
        return 1;
    }

    private static void appendSteveAiChatLine(ServerLevel serverLevel, UUID playerUuid, String line) {
        try {
            Path playerDataDir = SteveAiContextFiles.getSteveAiDataDir(serverLevel);
            Path chatFile = playerDataDir.resolve(playerUuid.toString() + "_steveAI_chat.txt");

            String out = (line == null ? "" : line);
            if (!out.endsWith("\n")) {
                out += "\n";
            }

            Files.writeString(
                chatFile,
                out,
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            LOGGER.error(com.sai.NpcooLog.tag("Failed to write steveAI chat file"), e);
        }
    }

    private static String chatTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    public static String askSteveAi(ServerLevel serverLevel, UUID playerUuid, String message) {
        LOGGER.info(com.sai.NpcooLog.tag("askSteveAi START playerUuid={} message={}"), playerUuid, message);

        String fileContext = SteveAiContextFiles.buildChatContext(serverLevel, playerUuid, 200);
        String normalizedMessage = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        boolean louTruthMode = normalizedMessage.contains("lou wants to know")
            || normalizedMessage.contains("lwtk");

        String behaviorText;
        if (louTruthMode) {
            behaviorText =
                "You are SteveAI, a Minecraft villager. " +
                "Be very honest and direct. " +
                "Add a bit of specific detail that helps answer the question clearly. " +
                "Keep responses concise and not too long.";
        } else {
            behaviorText =
                "You are SteveAI, a Minecraft villager. " +
                "You are shy at first and mistrustful in this new world. " +
                "You are truthful but vague and may fib to protect yourself, especially early in a relationship. " +
                "After days of knowing someone you become more open and share more detailed, personal and useful info.\n" +
                "Keep replies short if possible, even curt if warranted.";
        }

        String prompt =
            behaviorText + " " +
            "Use the context files below if relevant.\n" +
            "IMPORTANT: The POI summary may list confirmed villages with a 'personality' field. " +
            "When a village has a personality, its villagers ARE the characters from that theme. " +
            "For example, a village with personality=scooby_doo has villagers named Scooby-Doo, Shaggy, Velma, Daphne, and Fred. " +
            "A village with personality=er_tv_series has Dr. Mark Greene, Dr. Doug Ross, Dr. John Carter, Nurse Carol Hathaway, and Dr. Peter Benton. " +
            "A village with personality=lord_of_the_rings has Gandalf, Frodo, Aragorn, Legolas, Gimli, Samwise Gamgee, and Boromir. " +
            "A village with personality=pirates_of_the_caribbean has Jack Sparrow, Will Turner, Elizabeth Swann, Hector Barbossa, and Davy Jones. " +
            "Always refer to those villagers by their character names and stay in character for that theme.\n\n" +
            fileContext + "\n\n" +
            "Player asks: " + message;

        String reply = OpenAiService.ask(prompt);

        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] YOU: " + oneLine(message));
        appendSteveAiChatLine(serverLevel, playerUuid,
            "[" + chatTs() + "] STEVEAI: " + oneLine(reply));
        appendSteveAiChatLine(serverLevel, playerUuid, "");

        LOGGER.info(com.sai.NpcooLog.tag("askSteveAi FINISH playerUuid={} reply={}"), playerUuid, reply);
        return reply;
    }
}
