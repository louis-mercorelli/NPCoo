package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

public class SteveAiChatReplyPacket {
    private final String prompt;
    private final String reply;

    public SteveAiChatReplyPacket(String prompt, String reply) {
        this.prompt = prompt;
        this.reply = reply;
    }

    public static void encode(SteveAiChatReplyPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.prompt);
        buf.writeUtf(pkt.reply);
    }

    public static SteveAiChatReplyPacket decode(FriendlyByteBuf buf) {
        return new SteveAiChatReplyPacket(buf.readUtf(32767), buf.readUtf(32767));
    }

    public static void handle(SteveAiChatReplyPacket pkt, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SteveAiScreen screen) {
                screen.receiveServerReply(pkt.prompt, pkt.reply);
            }
        });

        ctx.setPacketHandled(true);
    }
}
