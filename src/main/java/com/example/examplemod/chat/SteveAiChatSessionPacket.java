/**
 * File: SteveAiChatSessionPacket.java
 *
 * Main intent:
 * Defines SteveAiChatSessionPacket functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiChatSessionPacket(...)}:
 *    Purpose: Stores whether a SteveAI chat session was opened or closed on the client.
 *    Input: boolean opened.
 *    Output: none (constructor).
 * 2) {@code encode(...)}:
 *    Purpose: Serializes the chat-session open or close flag into the network buffer.
 *    Input: SteveAiChatSessionPacket pkt, FriendlyByteBuf buf.
 *    Output: void.
 * 3) {@code decode(...)}:
 *    Purpose: Reconstructs a chat-session packet from the network buffer.
 *    Input: FriendlyByteBuf buf.
 *    Output: SteveAiChatSessionPacket.
 * 4) {@code handle(...)}:
 *    Purpose: Starts or ends the server-side chat session snapshot for the sending player.
 *    Input: SteveAiChatSessionPacket pkt, CustomPayloadEvent.Context ctx.
 *    Output: void.
 */
package com.example.examplemod.chat;

import com.example.examplemod.CommandEvents;
import com.example.examplemod.SteveAiContextFiles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SteveAiChatSessionPacket {
    private final boolean opened;

    public SteveAiChatSessionPacket(boolean opened) {
        this.opened = opened;
    }

    public static void encode(SteveAiChatSessionPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.opened);
    }

    public static SteveAiChatSessionPacket decode(FriendlyByteBuf buf) {
        return new SteveAiChatSessionPacket(buf.readBoolean());
    }

    public static void handle(SteveAiChatSessionPacket pkt, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            ServerLevel serverLevel = (ServerLevel) player.level();
            if (pkt.opened) {
                try {
                    // Preload scan context before opening chat:
                    // 1) broad scan around player, 2) SAI2 local chunk radius,
                    // 3) POIfind refresh, 4) flush to text files.
                    int x = player.blockPosition().getX();
                    int y = player.blockPosition().getY();
                    int z = player.blockPosition().getZ();

                    serverLevel.getServer()
                        .getCommands()
                        .performPrefixedCommand(player.createCommandSourceStack(), "testmod scanSAIBroad " + x + " " + y + " " + z + " 20");
                    serverLevel.getServer()
                        .getCommands()
                        .performPrefixedCommand(player.createCommandSourceStack(), "testmod scanSAI2 " + x + " " + y + " " + z + " 2");
                    serverLevel.getServer()
                        .getCommands()
                        .performPrefixedCommand(player.createCommandSourceStack(), "testmod POIfind");
                    serverLevel.getServer()
                        .getCommands()
                        .performPrefixedCommand(player.createCommandSourceStack(), "testmod writeT");
                } catch (Exception e) {
                    CommandEvents.LOGGER.warn(com.sai.NpcooLog.tag("Failed to run automatic chat-open scan sequence: {}"), e.getMessage());
                }
                SteveAiContextFiles.startChatSession(serverLevel, player.getUUID());
            } else {
                SteveAiContextFiles.endChatSession(player.getUUID());
            }
        });

        ctx.setPacketHandled(true);
    }
}