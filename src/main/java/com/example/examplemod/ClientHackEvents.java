package com.example.examplemod;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ClientHackEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean wasShiftRightClickDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            wasShiftRightClickDown = false;
            return;
        }

        boolean shiftDown = mc.options.keyShift.isDown();
        boolean rightMouseDown = mc.options.keyUse.isDown();
        boolean shiftRightClickDown = shiftDown && rightMouseDown;

        // fire once per click, not continuously while held down
        if (shiftRightClickDown && !wasShiftRightClickDown) {
            if (isLookingAtSteveAi(mc)) {
                LOGGER.info("### ClientHackEvents opening SteveAiScreen from SHIFT-right-click ###");

                SteveAiMenu menu = new SteveAiMenu(0, mc.player.getInventory());
                SteveAiScreen screen = new SteveAiScreen(
                    menu,
                    mc.player.getInventory(),
                    Component.literal("SteveAI")
                );

                mc.setScreen(screen);
            }
        }

        wasShiftRightClickDown = shiftRightClickDown;
    }

    private static boolean isLookingAtSteveAi(Minecraft mc) {
        HitResult hit = mc.hitResult;

        if (!(hit instanceof EntityHitResult entityHit)) {
            return false;
        }

        Entity entity = entityHit.getEntity();

        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        if (entity.hasCustomName() && entity.getCustomName() != null) {
            return "steveAI".equals(entity.getCustomName().getString());
        }

        return false;
    }
}