package com.example.examplemod.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class SteveAiMarkerPacket {
    public enum Action {
        SET_ON,
        SET_OFF,
        RESET_ALL
    }

    private final Action action;
    private final BlockPos pos;

    public SteveAiMarkerPacket(Action action, BlockPos pos) {
        this.action = action == null ? Action.RESET_ALL : action;
        this.pos = pos == null ? null : pos.immutable();
    }

    public static void encode(SteveAiMarkerPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        boolean hasPos = pkt.pos != null;
        buf.writeBoolean(hasPos);
        if (hasPos) {
            buf.writeBlockPos(pkt.pos);
        }
    }

    public static SteveAiMarkerPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new SteveAiMarkerPacket(action, pos);
    }

    public static void handle(SteveAiMarkerPacket pkt, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }

            if (pkt.action == Action.RESET_ALL) {
                ClientMarkerState.resetAll();
                return;
            }

            if (pkt.pos == null) {
                return;
            }

            ClientMarkerState.setMarker(pkt.pos, pkt.action == Action.SET_ON);
        });

        ctx.setPacketHandled(true);
    }
}
