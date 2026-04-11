package org.betterLostItems.better_lost_items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;

import java.util.Optional;
import java.util.UUID;

/**
 * Formatting helpers for verbose development logs.
 *
 * <p>These methods keep debug logging readable without affecting gameplay. They are deliberately
 * centralized because item stacks and merchant costs are noisy to print directly.</p>
 */
public final class LostItemsDebug {
    private LostItemsDebug() {
    }

    /**
     * @return compact item-stack description for logs
     */
    public static String stack(ItemStack stack) {
        if (stack == null) {
            return "<null>";
        }

        return stack.getCount() + "x " + stack.getItem().toString()
                + " [" + stack.getHoverName().getString() + "]";
    }

    /**
     * @return compact merchant cost description for logs
     */
    public static String cost(ItemCost cost) {
        return cost == null ? "<none>" : stack(cost.itemStack());
    }

    /**
     * @return compact optional merchant cost description for logs
     */
    public static String optionalCost(Optional<ItemCost> cost) {
        return cost.map(LostItemsDebug::cost).orElse("<none>");
    }

    /**
     * Counts emeralds in the player's non-equipment inventory for debugging trade bugs.
     */
    public static int emeraldCount(ServerPlayer player) {
        int emeralds = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.EMERALD)) {
                emeralds += stack.getCount();
            }
        }
        return emeralds;
    }

    /**
     * @return player name and UUID in one log-friendly string
     */
    public static String player(ServerPlayer player) {
        return player.getName().getString() + " (" + player.getUUID() + ")";
    }

    /**
     * @return UUID string or a readable placeholder
     */
    public static String uuid(UUID id) {
        return id == null ? "<none>" : id.toString();
    }
}
