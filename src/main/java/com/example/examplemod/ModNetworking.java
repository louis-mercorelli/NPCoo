/**
 * File: ModNetworking.java
 *
 * Main intent:
 * Defines ModNetworking functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code register(...)}:
 *    Purpose: Registers the SteveAI chat request, reply, and session packets on the mod network channel.
 *    Input: none.
 *    Output: void.
 */
package com.example.examplemod;

import com.example.examplemod.chat.SteveAiChatReplyPacket;
import com.example.examplemod.chat.SteveAiChatRequestPacket;
import com.example.examplemod.chat.SteveAiChatSessionPacket;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public class ModNetworking {
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ExampleMod.MODID + ":main")
        .networkProtocolVersion(1)
        .simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(SteveAiChatRequestPacket.class, packetId++)
            .encoder(SteveAiChatRequestPacket::encode)
            .decoder(SteveAiChatRequestPacket::decode)
            .consumerNetworkThread(SteveAiChatRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(SteveAiChatReplyPacket.class, packetId++)
            .encoder(SteveAiChatReplyPacket::encode)
            .decoder(SteveAiChatReplyPacket::decode)
            .consumerNetworkThread(SteveAiChatReplyPacket::handle)
            .add();

        CHANNEL.messageBuilder(SteveAiChatSessionPacket.class, packetId++)
            .encoder(SteveAiChatSessionPacket::encode)
            .decoder(SteveAiChatSessionPacket::decode)
            .consumerNetworkThread(SteveAiChatSessionPacket::handle)
            .add();
    }
}
