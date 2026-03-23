package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STEVE_AI_NAME = "steveAI";
    private static final String STEVE_AI_TAG = "steveai_npc";   

    @SubscribeEvent
        public static void onCommandsRegister(RegisterCommandsEvent event) {
            LOGGER.info("CommandEvents.onCommandsRegister START");
            try {
                event.getDispatcher().register(
                    Commands.literal("testmod")
                        // optional: /testmod with no argument
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            source.sendSuccess(
                                () -> Component.literal("§6[testmod] The mod command system is working!"),
                                false
                            );
                            LOGGER.info("ExampleMod command executed with no message");
                            return 1;
                        })
                        // testmod opengui will open trading my custom trading window 
                        .then(Commands.literal("opengui")
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();

                                if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {

                                    MerchantOffers offers = new MerchantOffers();
                                    offers.add(new MerchantOffer(
                                        new ItemCost(Items.OAK_LOG, 1),
                                        Optional.empty(),
                                        new ItemStack(Items.APPLE, 1),
                                        9999,
                                        1,
                                        0.05f
                                    ));

                                    java.util.OptionalInt containerId = player.openMenu(new net.minecraft.world.MenuProvider() {

                                        @Override
                                        public Component getDisplayName() {
                                            return Component.literal("SteveAI");
                                        }

                                        @Override
                                        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                                                int containerId,
                                                net.minecraft.world.entity.player.Inventory playerInventory,
                                                net.minecraft.world.entity.player.Player pPlayer) {
                                            LOGGER.info("CommandEvents.opengui ### CREATE MENU ### called ");
                                            return new SteveAiMenu(containerId, playerInventory);
                                        }
                                    });

                                    if (containerId.isPresent()) {
                                        LOGGER.info("Sending merchant offers to client, count={}", offers.size());

                                        player.sendMerchantOffers(
                                            containerId.getAsInt(),
                                            offers,
                                            2,      // villager level
                                            0,      // villager xp
                                            false,  // show progress bar
                                            false   // can restock
                                        );
                                    } else {
                                        LOGGER.warn("openMenu returned empty OptionalInt");
                                    }
                                }

                                return 1;
                            })
                        )
                        // /testmod hello John
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                String message = StringArgumentType.getString(context, "message");

                                LOGGER.info("TESTMOD asking OpenAI...: {}", message);

                                source.sendSuccess(
                                    () -> Component.literal("§6[testmod] Asking OpenAI: " + message),
                                    false
                                );

                                String reply = OpenAiService.ask("you are a Minecraft Villager. Your reply should be short so it doens't fill the screen. The Player asks: " + message);
                                        
                                source.sendSuccess(
                                    () -> Component.literal("§6[testmod] OpenAI reply: " + reply),
                                    false
                                );

                                LOGGER.info("ExampleMod OpenAI response... {}", reply);
                                return 1;
                            })
                        )
                );
            } finally {
                LOGGER.info("CommandEvents.onCommandsRegister FINISH");
            }
        }

    @SubscribeEvent
        public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
            var server = event.server();
            if (server.overworld() == null) {
                return;
            }

            // every 60 seconds
            if (server.overworld().getGameTime() % 1200 != 0) {
                return;
            }

            for (ServerLevel serverLevel : server.getAllLevels()) {
                var matches = serverLevel.getEntities(
                    (Entity) null,
                    new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                    entity -> isSteveAi(entity)
                );

                for (Entity entity : matches) {
                    LOGGER.info(
                        "steveAI location -> dimension: {}, x: {}, y: {}, z: {}",
                        serverLevel.dimension(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                    );
                }
            }
        }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("CommandEvents.onPlayerLoggedIn START ");
        try {
            var player = event.getEntity();
            var level = player.level();

            LOGGER.info("Player UUID: {}", player.getUUID());
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            LOGGER.info("Player {} logged in", player.getName().getString());

            if (findSteveAiAnywhere(serverLevel) != null) {
                LOGGER.info("steveAI already exists somewhere in the world, skipping spawn");
                return;
            }

            // Spawn steveAI 4 blocks away in a random direction
            double angle = Math.random() * 2 * Math.PI;
            double distance = 4.0;
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            double spawnX = player.getX() + offsetX;
            double spawnY = player.getY();
            double spawnZ = player.getZ() + offsetZ;

            var steveAi = EntityType.VILLAGER.create(serverLevel, EntitySpawnReason.EVENT);
            if (steveAi instanceof Villager villager) {
                villager.setPos(spawnX, spawnY, spawnZ);
                villager.setCustomName(Component.literal(STEVE_AI_NAME));
                villager.setCustomNameVisible(true);
                villager.setPersistenceRequired();
                villager.addTag(STEVE_AI_TAG);

                villager.setVillagerData(
                    villager.getVillagerData()
                        .withProfession(serverLevel.registryAccess(), VillagerProfession.FARMER)
                        .withLevel(2)
                );

                serverLevel.addFreshEntity(villager);
                LOGGER.info("Spawned steveAI at {}, {}, {}", spawnX, spawnY, spawnZ);
            } else {
                LOGGER.warn("Failed to create villager entity for steveAI");
            }
        } finally {
            LOGGER.info("CommandEvents.onPlayerLoggedIn FINISH");
        }
    }

    private static boolean isSteveAi(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        if (entity.getTags().contains(STEVE_AI_TAG)) {
            return true;
        }

        return entity.hasCustomName()
            && entity.getCustomName() != null
            && STEVE_AI_NAME.equals(entity.getCustomName().getString());
    }

    private static Entity findSteveAiAnywhere(ServerLevel serverLevel) {
        for (ServerLevel levelToCheck : serverLevel.getServer().getAllLevels()) {
            var matches = levelToCheck.getEntities(
                (Entity) null,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                entity -> isSteveAi(entity)
            );

            if (!matches.isEmpty()) {
                Entity existing = matches.get(0);
                LOGGER.info(
                    "Found existing steveAI in dimension {} at x={}, y={}, z={}",
                    levelToCheck.dimension(),
                    existing.getX(),
                    existing.getY(),
                    existing.getZ()
                );
                return existing;
            }
        }

        return null;
    }

}
