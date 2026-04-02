package com.example.examplemod.chat;

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
                SteveAiContextFiles.startChatSession(serverLevel, player.getUUID());
            } else {
                SteveAiContextFiles.endChatSession(player.getUUID());
            }
        });

        ctx.setPacketHandled(true);
    }
}
