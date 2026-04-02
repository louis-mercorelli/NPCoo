/**
 * File: ClientSetup.java
 *
 * Main intent:
 * Defines ClientSetup functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code onClientSetup(...)}:
 *    Purpose: Registers the SteveAI screen factory during client initialization.
 *    Input: FMLClientSetupEvent event.
 *    Output: void.
 * 2) {@code steveAiMenuType(...)}:
 *    Purpose: Returns the registered SteveAI menu type with the cast needed for screen registration.
 *    Input: none.
 *    Output: MenuType<MerchantMenu>.
 */
package com.example.examplemod;

import org.slf4j.Logger;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info(com.sai.NpcooLog.tag("### HELLO ClientSetup onClientSetup fired ###"));

        event.enqueueWork(() -> {
            LOGGER.info(com.sai.NpcooLog.tag("### HELLO Registering SteveAiScreen ###"));

            MenuScreens.register(
                (MenuType) steveAiMenuType(),
                (MenuScreens.ScreenConstructor) ((menu, playerInventory, title) ->
                    new SteveAiScreen((SteveAiMenu) menu, playerInventory, title)
                )
            );
        });
    }

    private static MenuType<MerchantMenu> steveAiMenuType() {
        LOGGER.info(com.sai.NpcooLog.tag("### HELLO ClientSetup MenuType<MerchantMenu> fired ###"));
        return (MenuType<MerchantMenu>) (MenuType<?>) ModMenus.STEVE_AI_MENU.get();
    }
}
