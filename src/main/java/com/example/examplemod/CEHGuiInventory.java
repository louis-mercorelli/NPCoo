/**
 * File: CEHGuiInventory.java
 *
 * Main intent:
 * Defines CEHGuiInventory functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code CEHGuiInventory(...)}:
 *    Purpose: Prevents instantiation of this static GUI-and-inventory command helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code handleOpenGui(...)}:
 *    Purpose: Opens the SteveAI merchant-style GUI for the player and sends its initial offers.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 * 3) {@code getDisplayName(...)}:
 *    Purpose: Supplies the title text shown by the temporary menu provider used when opening the GUI.
 *    Input: none.
 *    Output: Component.
 * 4) {@code createMenu(...)}:
 *    Purpose: Creates the SteveAI merchant menu instance for the newly opened GUI container.
 *    Input: int containerId, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.world.entity.player.Player pPlayer.
 *    Output: net.minecraft.world.inventory.AbstractContainerMenu.
 * 5) {@code handleInv(...)}:
 *    Purpose: Shows a text summary of SteveAI's inventory contents.
 *    Input: CommandContext<CommandSourceStack> context.
 *    Output: int.
 */
package com.example.examplemod;

import com.mojang.brigadier.context.CommandContext;

import java.util.Optional;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.Villager;
import com.sai.InventoryService;

final class CEHGuiInventory {

    private CEHGuiInventory() {}

    static int handleOpenGui(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 1),
                Optional.empty(),
                new ItemStack(Items.APPLE, 1),
                9999, 1, 0.05f
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
                    CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("CommandEvents.opengui ### CREATE MENU ### called "));
                    return new SteveAiMenu(containerId, playerInventory);
                }
            });

            if (containerId.isPresent()) {
                CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("Sending merchant offers to client, count={}"), offers.size());
                player.sendMerchantOffers(containerId.getAsInt(), offers, 2, 0, false, false);
            } else {
                CommandEvents.LOGGER.warn(com.sai.NpcooLog.tag("openMenu returned empty OptionalInt"));
            }
        }

        return 1;
    }

    static int handleInv(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = SteveAiLocator.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String summary = InventoryService.getInventorySummary(steveAi);
        source.sendSuccess(() -> Component.literal("SteveAI inventory: " + summary), false);
        CommandEvents.LOGGER.info(com.sai.NpcooLog.tag("SteveAI inventory command -> {}"), summary);
        return 1;
    }
}
