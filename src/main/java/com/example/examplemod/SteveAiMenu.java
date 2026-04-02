package com.example.examplemod;

//import static net.minecraft.world.inventory.AbstractContainerMenu.LOGGER;

import java.util.Optional;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.entity.npc.ClientSideMerchant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class SteveAiMenu extends MerchantMenu {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");
    public SteveAiMenu(int containerId, Inventory playerInventory) {
        super(containerId, playerInventory, createMerchant(playerInventory));
        LOGGER.info(com.example.examplemod.NpcooLog.tag("SteveAiMenu setting up first trade"));
        // optional but helps pick the first recipe
        this.setSelectionHint(0);
    }

    private static ClientSideMerchant createMerchant(Inventory playerInventory) {
        ClientSideMerchant merchant = new ClientSideMerchant(playerInventory.player);
        LOGGER.info(com.example.examplemod.NpcooLog.tag("SteveAiMenu clientSideMerchant called"));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
            new ItemCost(Items.OAK_LOG, 1),
            Optional.empty(),
            new ItemStack(Items.APPLE, 1),
            9999,
            1,
            0.05f
        ));

        merchant.overrideOffers(offers);
        
        LOGGER.info(com.example.examplemod.NpcooLog.tag("SteveAiMenu offers count = " + merchant.getOffers().size()));
        return merchant;
    }
}