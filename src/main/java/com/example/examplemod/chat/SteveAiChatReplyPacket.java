/**
 * File: SteveAiChatReplyPacket.java
 *
 * Main intent:
 * Defines SteveAiChatReplyPacket functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code reply)(...)}:
 *    Purpose: Implements reply) logic in this file.
 *    Input: String prompt, String reply.
 *    Output: SteveAiChatReplyPacket(String prompt, String reply).
 * 2) {@code buf)(...)}:
 *    Purpose: Implements buf) logic in this file.
 *    Input: SteveAiChatReplyPacket pkt, FriendlyByteBuf buf.
 *    Output: void.
 * 3) {@code buf)(...)}:
 *    Purpose: Implements buf) logic in this file.
 *    Input: FriendlyByteBuf buf.
 *    Output: SteveAiChatReplyPacket.
 * 4) {@code ctx)(...)}:
 *    Purpose: Implements ctx) logic in this file.
 *    Input: SteveAiChatReplyPacket pkt, CustomPayloadEvent.Context ctx.
 *    Output: void.
 */
package com.example.examplemod.chat;

import com.example.examplemod.SteveAiScreen;

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