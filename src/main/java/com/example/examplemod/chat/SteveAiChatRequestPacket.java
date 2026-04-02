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