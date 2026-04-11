package org.betterLostItems.better_lost_items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Set;

/**
 * Validation helpers for the supplies placed into the fetch slots.
 *
 * <p>Most checks delegate to {@link LostItemsConfig} so server owners can change requirements
 * without touching the menu code. The legacy helper methods are kept small and side-effect-free
 * because slot validation calls them frequently while players move items around.</p>
 */
public final class LostItemsFetchSupplies {
    private static final Set<Item> DISALLOWED_JOURNEY_FOODS = Set.of(
            Items.BEEF,
            Items.PORKCHOP,
            Items.CHICKEN,
            Items.MUTTON,
            Items.RABBIT,
            Items.COD,
            Items.SALMON,
            Items.TROPICAL_FISH,
            Items.PUFFERFISH
    );

    private LostItemsFetchSupplies() {
    }

    /**
     * @return {@code true} when the stack satisfies the configured journey supply slot
     */
    public static boolean isJourneySupply(ItemStack stack) {
        return LostItemsConfig.isJourneySupply(stack);
    }

    /**
     * @return {@code true} when the stack satisfies the configured burned-loot fetch slot
     */
    public static boolean isBurnedFetchSupply(ItemStack stack) {
        return LostItemsConfig.isBurnedFetchSupply(stack);
    }

    /**
     * @return {@code true} when the stack satisfies the configured void-loot fetch slot
     */
    public static boolean isVoidFetchSupply(ItemStack stack) {
        return LostItemsConfig.isVoidFetchSupply(stack);
    }

    /**
     * Checks the default journey-food rule: any food except raw meat/fish.
     *
     * @param stack candidate item stack
     * @return whether the stack is valid journey food
     */
    public static boolean isJourneyFood(ItemStack stack) {
        return !stack.isEmpty()
                && stack.has(DataComponents.FOOD)
                && !DISALLOWED_JOURNEY_FOODS.contains(stack.getItem());
    }

    /**
     * Legacy convenience check for fire-resistance potion stacks.
     *
     * @param stack candidate potion item
     * @return whether the stack has a fire-resistance potion effect
     */
    public static boolean isFireResistancePotion(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents == null) {
            return false;
        }

        for (MobEffectInstance effect : potionContents.getAllEffects()) {
            if (effect.getEffect().is(MobEffects.FIRE_RESISTANCE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Legacy convenience alias for the configured void-fetch requirement.
     */
    public static boolean isFallenFetchPearls(ItemStack stack) {
        return LostItemsConfig.isVoidFetchSupply(stack);
    }
}
