package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        LOGGER.info("Config.validateItemName START");
        try {
            return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(Identifier.tryParse(itemName));
        } finally {
            LOGGER.info("Config.validateItemName FINISH value={}", obj);
        }
  }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        LOGGER.info("Config.onLoad START");
        try {
                logDirtBlock = LOG_DIRT_BLOCK.get();
                magicNumber = MAGIC_NUMBER.get();
                magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

                // convert the list of strings into a set of items
                items = ITEM_STRINGS.get().stream()
                        .map(itemName -> ForgeRegistries.ITEMS.getValue(Identifier.tryParse(itemName)))
                        .collect(Collectors.toSet());
                
                LOGGER.info("Config loaded: logDirtBlock={}, magicNumber={}, itemCount={}",
                    logDirtBlock, magicNumber, items.size());
        } finally {
            LOGGER.info("Config.onLoad FINISH");
        }
    }

     @SubscribeEvent
     static void onReload(final ModConfigEvent.Reloading event) {
        LOGGER.info("Config.onReload START");
        try {
            // You could add code here to do something when the config is reloaded, such as clearing caches or updating values in other classes
            LOGGER.info("Config reloaded");
        } finally {
            LOGGER.info("Config.onReload FINISH");
        }
     }
}
