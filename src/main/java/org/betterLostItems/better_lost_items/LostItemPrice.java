package org.betterLostItems.better_lost_items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;

import java.util.Optional;

/**
 * Price estimate for one public wandering-trader lost-item offer.
 *
 * <p>The public market uses per-item pricing instead of the flat death-loot recovery cost. The
 * estimate is intentionally simple and deterministic: rarity, enchantments, custom names,
 * durability, and stack count nudge the emerald value while the final price is capped so no
 * generated trade becomes absurdly expensive.</p>
 *
 * @param primaryCost primary merchant cost slot
 * @param secondaryCost optional secondary cost slot
 * @param emeraldValue unclamped value exposed for debug logging
 */
public record LostItemPrice(ItemCost primaryCost, Optional<ItemCost> secondaryCost, int emeraldValue) {
    private static final int MAX_EMERALD_VALUE = 32;

    /**
     * Builds a merchant price for a concrete result stack.
     *
     * @param stack item stack being sold by the wandering trader
     * @return one emerald-based price definition
     */
    public static LostItemPrice fromStack(ItemStack stack) {
        int emeraldValue = Math.clamp(estimateEmeraldValue(stack), 1, MAX_EMERALD_VALUE);
        return new LostItemPrice(new ItemCost(Items.EMERALD, emeraldValue), Optional.empty(), emeraldValue);
    }

    /**
     * Estimates item worth using only stable item-stack data available server-side.
     */
    private static int estimateEmeraldValue(ItemStack stack) {
        double value = switch (stack.getRarity()) {
            case COMMON -> 1.0D;
            case UNCOMMON -> 2.5D;
            case RARE -> 5.0D;
            case EPIC -> 9.0D;
        };

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments storedEnchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        value += enchantments.entrySet().stream().mapToDouble(entry -> 1.1D * entry.getIntValue()).sum();
        value += storedEnchantments.entrySet().stream().mapToDouble(entry -> 1.4D * entry.getIntValue()).sum();

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            value += 0.75D;
        }

        if (stack.isDamageableItem()) {
            double durabilityRatio = 1.0D - ((double) stack.getDamageValue() / Math.max(1, stack.getMaxDamage()));
            value += Math.min(4.0D, stack.getMaxDamage() / 350.0D);
            value *= Math.max(0.35D, durabilityRatio);
        }

        if (!stack.isStackable()) {
            value += switch (stack.getRarity()) {
                case COMMON -> 0.5D;
                case UNCOMMON -> 1.0D;
                case RARE -> 1.5D;
                case EPIC -> 2.5D;
            };
        }

        if (stack.getCount() > 1) {
            double countBonus = Math.min(2.4D, (Math.log(stack.getCount()) / Math.log(2.0D)) * 0.35D);
            value *= 1.0D + countBonus;
        }

        return (int) Math.ceil(value);
    }
}
