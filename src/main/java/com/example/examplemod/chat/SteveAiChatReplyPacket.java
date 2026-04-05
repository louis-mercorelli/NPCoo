/**
 * File: SteveAiChatReplyPacket.java
 *
 * Main intent:
 * Defines SteveAiChatReplyPacket functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiChatReplyPacket(...)}:
 *    Purpose: Stores the original prompt and generated reply being returned to the client.
 *    Input: String prompt, String reply.
 *    Output: none (constructor).
 * 2) {@code encode(...)}:
 *    Purpose: Serializes the prompt and reply into the network buffer.
 *    Input: SteveAiChatReplyPacket pkt, FriendlyByteBuf buf.
 *    Output: void.
 * 3) {@code decode(...)}:
 *    Purpose: Reconstructs a chat reply packet from the network buffer.
 *    Input: FriendlyByteBuf buf.
 *    Output: SteveAiChatReplyPacket.
 * 4) {@code handle(...)}:
 *    Purpose: Delivers the server reply to the open SteveAI screen on the client.
 *    Input: SteveAiChatReplyPacket pkt, CustomPayloadEvent.Context ctx.
 *    Output: void.
 */
package com.example.examplemod.chat;

import com.example.examplemod.gui.SteveAiScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SteveAiChatReplyPacket {
    private final String prompt;
    private final String reply;
    private final long serverProcessingMs;
    private final String modeLabel;

    public SteveAiChatReplyPacket(String prompt, String reply, long serverProcessingMs, String modeLabel) {
        this.prompt = prompt;
        this.reply = reply;
        this.serverProcessingMs = Math.max(0L, serverProcessingMs);
        this.modeLabel = modeLabel == null ? "fast" : modeLabel;
    }

    public static void encode(SteveAiChatReplyPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.prompt);
        buf.writeUtf(pkt.reply);
        buf.writeLong(pkt.serverProcessingMs);
        buf.writeUtf(pkt.modeLabel);
    }

    public static SteveAiChatReplyPacket decode(FriendlyByteBuf buf) {
        return new SteveAiChatReplyPacket(buf.readUtf(32767), buf.readUtf(32767), buf.readLong(), buf.readUtf(32));
    }

    public static void handle(SteveAiChatReplyPacket pkt, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SteveAiScreen screen) {
                screen.receiveServerReply(pkt.prompt, pkt.reply, pkt.serverProcessingMs, pkt.modeLabel);
            }
        });

        ctx.setPacketHandled(true);
    }
}