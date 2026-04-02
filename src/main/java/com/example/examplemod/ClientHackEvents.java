package com.example.examplemod;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
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
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    private static final String CHAT_HINT_TEXT = "press <shift> <right click> to chat with steveAI";

    private static boolean wasShiftRightClickDown = false;
    private static String activeWorldHintKey = "";
    private static boolean showChatHint = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            wasShiftRightClickDown = false;
            return;
        }

        refreshHintState(mc);

        if (showChatHint) {
            mc.player.displayClientMessage(Component.literal(CHAT_HINT_TEXT), true);
        }

        boolean shiftDown = mc.options.keyShift.isDown();
        boolean rightMouseDown = mc.options.keyUse.isDown();
        boolean shiftRightClickDown = shiftDown && rightMouseDown;

        // fire once per click, not continuously while held down
        if (shiftRightClickDown && !wasShiftRightClickDown) {
            if (isLookingAtSteveAi(mc)) {
                LOGGER.info(com.sai.NpcooLog.tag("### ClientHackEvents opening SteveAiScreen from SHIFT-right-click ###"));

                markHintSeen(mc);

                SteveAiMenu menu = new SteveAiMenu(0, mc.player.getInventory());
                SteveAiScreen screen = new SteveAiScreen(
                    menu,
                    mc.player.getInventory(),
                    Component.literal("")
                );

                mc.setScreen(screen);
            }
        }

        wasShiftRightClickDown = shiftRightClickDown;
    }

    private static void refreshHintState(Minecraft mc) {
        String worldKey = getWorldHintKey(mc);
        if (!worldKey.equals(activeWorldHintKey)) {
            activeWorldHintKey = worldKey;
            showChatHint = !hasSeenHintForCurrentWorld(mc);
        }
    }

    private static void markHintSeen(Minecraft mc) {
        showChatHint = false;
        if (!isSingleplayerWorld(mc)) {
            return;
        }

        try {
            java.nio.file.Path hintFile = getHintFile(mc);
            if (hintFile == null) {
                return;
            }

            java.nio.file.Files.createDirectories(hintFile.getParent());
            java.nio.file.Files.writeString(
                hintFile,
                "seen=true\n",
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE
            );
        } catch (Exception ex) {
            LOGGER.warn(com.sai.NpcooLog.tag("Failed to persist SteveAI chat hint state"), ex);
        }
    }

    private static boolean hasSeenHintForCurrentWorld(Minecraft mc) {
        if (!isSingleplayerWorld(mc)) {
            return false;
        }

        try {
            java.nio.file.Path hintFile = getHintFile(mc);
            return hintFile != null && java.nio.file.Files.exists(hintFile);
        } catch (Exception ex) {
            LOGGER.warn(com.sai.NpcooLog.tag("Failed checking SteveAI chat hint state"), ex);
            return false;
        }
    }

    private static boolean isSingleplayerWorld(Minecraft mc) {
        return mc.getSingleplayerServer() != null;
    }

    private static String getWorldHintKey(Minecraft mc) {
        if (isSingleplayerWorld(mc)) {
            try {
                return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
            } catch (Exception ex) {
                LOGGER.warn(com.sai.NpcooLog.tag("Failed to compute world hint key"), ex);
            }
        }

        return "multiplayer:" + String.valueOf(mc.level.dimension());
    }

    private static java.nio.file.Path getHintFile(Minecraft mc) {
        if (!isSingleplayerWorld(mc)) {
            return null;
        }

        return mc.getSingleplayerServer()
            .getWorldPath(LevelResource.ROOT)
            .resolve("testmod")
            .resolve("steveai_chat_hint_seen.txt");
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