package com.example.examplemod;

import com.mojang.logging.LogUtils;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("### HELLO ClientSetup onClientSetup fired ###");

        event.enqueueWork(() -> {
            LOGGER.info("### HELLO Registering SteveAiScreen ###");

            MenuScreens.register(
                (MenuType) steveAiMenuType(),
                (MenuScreens.ScreenConstructor) ((menu, playerInventory, title) ->
                    new SteveAiScreen((SteveAiMenu) menu, playerInventory, title)
                )
            );
        });
    }

    private static MenuType<MerchantMenu> steveAiMenuType() {
        LOGGER.info("### HELLO ClientSetup MenuType<MerchantMenu> fired ###");
        return (MenuType<MerchantMenu>) (MenuType<?>) ModMenus.STEVE_AI_MENU.get();
    }
}