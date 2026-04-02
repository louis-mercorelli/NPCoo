/**
 * File: SteveAiChatRequestPacket.java
 *
 * Main intent:
 * Defines SteveAiChatRequestPacket functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiChatRequestPacket(...)}:
 *    Purpose: Constructs SteveAiChatRequestPacket.
 *    Input: String message.
 *    Output: none (constructor).
 * 2) {@code encode(...)}:
 *    Purpose: Performs encode.
 *    Input: SteveAiChatRequestPacket pkt, FriendlyByteBuf buf.
 *    Output: void.
 * 3) {@code decode(...)}:
 *    Purpose: Performs decode.
 *    Input: FriendlyByteBuf buf.
 *    Output: SteveAiChatRequestPacket.
 * 4) {@code handle(...)}:
 *    Purpose: Performs handle.
 *    Input: SteveAiChatRequestPacket pkt, CustomPayloadEvent.Context ctx.
 *    Output: void.
 */
package com.example.examplemod.chat;

import com.example.examplemod.ModNetworking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;


public class SteveAiChatRequestPacket {
    private final String message;

    public SteveAiChatRequestPacket(String message) {
        this.message = message;
    }

    public static void encode(SteveAiChatRequestPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.message);
    }

    public static SteveAiChatRequestPacket decode(FriendlyByteBuf buf) {
        return new SteveAiChatRequestPacket(buf.readUtf(32767));
    }

    public static void handle(SteveAiChatRequestPacket pkt, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            ServerLevel serverLevel = (ServerLevel) player.level();
            String reply;

            try {
                reply = CEHChat.askSteveAi(serverLevel, player.getUUID(), pkt.message);
            } catch (Exception e) {
                reply = "Something went wrong.";
            }

            ModNetworking.CHANNEL.send(
                new SteveAiChatReplyPacket(pkt.message, reply),
                PacketDistributor.PLAYER.with(player)
            );
        });

        ctx.setPacketHandled(true);
    }
}