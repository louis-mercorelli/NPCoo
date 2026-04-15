package com.example.examplemod.marker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = com.example.examplemod.ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientMarkerOverlayEvents {
    private static int tickCounter = 0;

    private ClientMarkerOverlayEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        List<BlockPos> markers = ClientMarkerState.getMarkersSnapshot();
        if (markers.isEmpty()) {
            return;
        }

        tickCounter++;
        if (tickCounter % 10 != 0) {
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        BlockPos nearest = findNearest(playerPos, markers);
        if (nearest == null) {
            return;
        }

        int dx = nearest.getX() - playerPos.getX();
        int dy = nearest.getY() - playerPos.getY();
        int dz = nearest.getZ() - playerPos.getZ();
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);

        String heading = headingIndicator(mc.player.getYRot(), dx, dz);

        String msg = String.format(
            "Marker %s dist=%.1f %s",
            nearest.toShortString(),
            dist,
            heading
        ).stripTrailing();

        mc.player.displayClientMessage(Component.literal(msg), true);

        // Small local particle hint on the marker when it is in loaded range.
        spawnMarkerParticleHint(mc, nearest);
    }

    private static BlockPos findNearest(BlockPos playerPos, List<BlockPos> markers) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos marker : markers) {
            double d = marker.distSqr(playerPos);
            if (d < bestDist) {
                bestDist = d;
                best = marker;
            }
        }
        return best;
    }

    private static String headingIndicator(float playerYaw, int dx, int dz) {
        // bearing in Minecraft yaw convention: 0=south, 90=west, -90=east, 180=north
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = bearing - playerYaw;
        // normalize to -180..180
        diff = ((diff % 360.0) + 360.0) % 360.0;
        if (diff > 180.0) diff -= 360.0;

        if (Math.abs(diff) <= 45.0) return "^";
        if (diff > 45.0 && diff < 135.0) return "<";
        if (diff < -45.0 && diff > -135.0) return ">";
        return ""; // behind
    }

    private static void spawnMarkerParticleHint(Minecraft mc, BlockPos marker) {
        Vec3 camera = mc.player.getEyePosition();
        double dx = marker.getX() + 0.5 - camera.x;
        double dy = marker.getY() + 1.0 - camera.y;
        double dz = marker.getZ() + 0.5 - camera.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Avoid excessive particles when target is very far from loaded client chunks.
        if (distSq > 256.0 * 256.0) {
            return;
        }

        if (mc.level != null) {
            mc.level.addParticle(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                marker.getX() + 0.5,
                marker.getY() + 1.1,
                marker.getZ() + 0.5,
                0.0,
                0.02,
                0.0
            );
        }
    }
}
