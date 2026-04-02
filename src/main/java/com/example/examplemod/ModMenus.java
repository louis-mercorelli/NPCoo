/**
 * File: ModMenus.java
 *
 * Main intent:
 * Defines ModMenus functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code createSteveAiMenuType(...)}:
 *    Purpose: Creates create steve ai menu type.
 *    Input: none.
 *    Output: MenuType<SteveAiMenu>.
 * 2) {@code ModMenus(...)}:
 *    Purpose: Constructs ModMenus.
 *    Input: none.
 *    Output: none (constructor).
 */
package com.example.examplemod;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

public final class ModMenus {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");

    public static final DeferredRegister<MenuType<?>> 
        MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ExampleMod.MODID);
        static {
            LOGGER.info(com.sai.NpcooLog.tag("ModMenus ### class initialized"));
        }
    
    public static final RegistryObject<MenuType<SteveAiMenu>> 
        STEVE_AI_MENU = MENUS.register("steve_ai_menu",
                    ModMenus::createSteveAiMenuType);
        static {
            LOGGER.info(com.sai.NpcooLog.tag("ModMenus ###steve_ai_menu registered"));
        }

    private static MenuType<SteveAiMenu> createSteveAiMenuType() {
        LOGGER.info(com.sai.NpcooLog.tag("ModMenus ### SteveAiMenu setting up "));
        return new MenuType<>(SteveAiMenu::new, FeatureFlags.DEFAULT_FLAGS);
    }

    private ModMenus() {
    }
}
