package com.sai;

import com.example.examplemod.CommandEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class InventoryService {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static String getInventorySummary(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty()) {
                sb.append("slot ")
                    .append(i)
                    .append(": ")
                    .append(slot.getHoverName().getString())
                    .append(" x")
                    .append(slot.getCount())
                    .append("; ");
            }
        }

        return sb.length() == 0 ? "Inventory empty." : sb.toString();
    }

    public static Item findItemByName(String itemName) {
        Identifier id;

        if (itemName.contains(":")) {
            id = Identifier.tryParse(itemName);
        } else {
            id = Identifier.tryParse("minecraft:" + itemName);
        }

        if (id == null) {
            return null;
        }

        var itemRef = BuiltInRegistries.ITEM.get(id);
        return itemRef.map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    public static boolean addItemToInventory(Villager villager, ItemStack toAdd) {
        try {
            SimpleContainer inv = villager.getInventory();

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);

                if (slot.isEmpty()) {
                    inv.setItem(i, toAdd.copy());
                    LOGGER.info("Added {} x{} to SteveAI inventory slot {}",
                        toAdd.getItem(), toAdd.getCount(), i);
                    return true;
                }

                if (ItemStack.isSameItem(slot, toAdd) && slot.getCount() < slot.getMaxStackSize()) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int move = Math.min(space, toAdd.getCount());

                    slot.grow(move);
                    toAdd.shrink(move);

                    LOGGER.info("Stacked {} of {} into SteveAI inventory slot {}",
                        move, slot.getItem(), i);

                    if (toAdd.isEmpty()) {
                        return true;
                    }
                }
            }

            LOGGER.info("SteveAI inventory full; could not add {}", toAdd.getItem());
            return false;

        } catch (Exception e) {
            LOGGER.error("Failed adding item to SteveAI inventory", e);
            return false;
        }
    }

    public static ItemStack removeItemFromInventory(Villager villager, Item item, int count) {
        try {
            SimpleContainer inv = villager.getInventory();
            int remaining = count;
            ItemStack removedTotal = ItemStack.EMPTY;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);

                if (slot.isEmpty()) {
                    continue;
                }

                if (slot.getItem() != item) {
                    continue;
                }

                int take = Math.min(slot.getCount(), remaining);
                ItemStack taken = slot.split(take);

                if (removedTotal.isEmpty()) {
                    removedTotal = taken.copy();
                } else {
                    removedTotal.grow(taken.getCount());
                }

                remaining -= take;

                if (slot.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }

                if (remaining <= 0) {
                    break;
                }
            }

            return removedTotal.isEmpty() ? ItemStack.EMPTY : removedTotal;

        } catch (Exception e) {
            LOGGER.error("Failed removing item from SteveAI inventory", e);
            return ItemStack.EMPTY;
        }
    }

    public static void dropItem(ServerLevel serverLevel, Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(
            serverLevel,
            villager.getX(),
            villager.getY() + 0.5,
            villager.getZ(),
            stack
        );

        itemEntity.setDefaultPickUpDelay();

        double dx = (serverLevel.random.nextDouble() - 0.5) * 0.2;
        double dy = 0.2;
        double dz = (serverLevel.random.nextDouble() - 0.5) * 0.2;
        itemEntity.setDeltaMovement(dx, dy, dz);

        serverLevel.addFreshEntity(itemEntity);
    }

    public static int handleInvAdd(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = CommandEvents.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String itemName = StringArgumentType.getString(context, "itemName");
        Item item = findItemByName(itemName);

        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + itemName));
            return 0;
        }

        boolean added = addItemToInventory(steveAi, new ItemStack(item, count));

        if (added) {
            String summary = getInventorySummary(steveAi);
            String displayName = new ItemStack(item).getHoverName().getString();
            source.sendSuccess(
                () -> Component.literal("Added " + count + " " + displayName + " to SteveAI. " + summary),
                false
            );
            LOGGER.info("SteveAI invAdd -> {} x{} ; {}", itemName, count, summary);
            return 1;
        } else {
            source.sendFailure(Component.literal("SteveAI inventory full."));
            return 0;
        }
    }

    public static int handleInvDrop(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Not on server level."));
            return 0;
        }

        Villager steveAi = CommandEvents.findSteveAi(serverLevel);
        if (steveAi == null) {
            source.sendFailure(Component.literal("SteveAI not found."));
            return 0;
        }

        String itemName = StringArgumentType.getString(context, "itemName");
        Item item = findItemByName(itemName);

        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + itemName));
            return 0;
        }

        ItemStack removed = removeItemFromInventory(steveAi, item, count);

        if (removed.isEmpty()) {
            source.sendFailure(Component.literal("SteveAI does not have that item."));
            return 0;
        }

        dropItem(serverLevel, steveAi, removed);

        String summary = getInventorySummary(steveAi);
        source.sendSuccess(
            () -> Component.literal(
                "SteveAI dropped " + removed.getCount() + " " +
                removed.getHoverName().getString() + ". " + summary
            ),
            false
        );

        LOGGER.info("SteveAI invDrop -> item={} requested={} dropped={} ; {}",
            itemName, count, removed.getCount(), summary);

        return 1;
    }
}
