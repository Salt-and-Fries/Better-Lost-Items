package org.betterLostItems.better_lost_items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads and exposes the server/client configuration for Better Lost Items.
 *
 * <p>The config is stored in Fabric's shared config directory as
 * {@code better_lost_items.json}. Both logical sides read the same file in
 * development/singleplayer, so this class owns not only gameplay rules but also
 * UI layout choices such as which recovery texture and fetch-slot positions to use.</p>
 *
 * <p>Public getters normalize invalid values instead of throwing. That keeps worlds playable
 * even when a server owner mistypes an item ID or amount; the bad value is logged and a safe
 * fallback is used until the config is fixed.</p>
 */
public final class LostItemsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "better_lost_items.json";
    private static final int HIDDEN_SLOT_X = -1000;

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

    private static ConfigData config = new ConfigData();

    private LostItemsConfig() {
    }

    /**
     * Reads the config file, falls back to defaults when needed, then writes the normalized file.
     */
    public static void load() {
        Path path = configPath();
        ConfigData loadedConfig = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loadedConfig = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException | RuntimeException exception) {
                Better_lost_items.LOGGER.error("Failed loading Better Lost Items config from {}", path, exception);
            }
        }

        config = loadedConfig == null ? new ConfigData() : loadedConfig;
        save(path);
    }

    /**
     * Replaces the live config and writes it to disk.
     *
     * <p>The rest of the mod reads config values through static getters, so replacing this object
     * immediately affects new menus, slot validation, pricing, and fetch behavior without a game
     * restart.</p>
     *
     * @param newConfig config values collected from an in-game editor
     */
    public static void apply(ConfigData newConfig) {
        config = newConfig.copy();
        save(configPath());
    }

    /**
     * @return deep copy of the live config for editors that need a draft copy
     */
    public static ConfigData copy() {
        return config.copy();
    }

    /**
     * @return live config data object used by debug tools and future integrations
     */
    public static ConfigData get() {
        return config;
    }

    /**
     * @return configured flat cost for moving all lost death loot into retrieved loot
     */
    public static int deathLootPaymentAmount() {
        return clampAmount(config.deathLootPaymentAmount);
    }

    /**
     * @return item required in the payment slot for death-loot recovery
     */
    public static Item deathLootPaymentItem() {
        return resolveItem(config.deathLootPaymentItem, Items.EMERALD);
    }

    /**
     * @return one-item ghost stack rendered in the recovery payment slot
     */
    public static ItemStack deathLootPaymentStack() {
        return new ItemStack(deathLootPaymentItem());
    }

    /**
     * @return whether the stack can be inserted into the death-loot payment slot
     */
    public static boolean isDeathLootPaymentItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(deathLootPaymentItem());
    }

    /**
     * @return whether the wandering-trader fetch/journey system is enabled at all
     */
    public static boolean isFetchEnabled() {
        return config.fetchEnabled;
    }

    /**
     * @return whether burned death loot can be recovered through fetch supplies
     */
    public static boolean isBurnedFetchEnabled() {
        return isFetchEnabled() && config.burnedItemsRetrievable;
    }

    /**
     * @return whether void-deleted death loot can be recovered through fetch supplies
     */
    public static boolean isVoidFetchEnabled() {
        return isFetchEnabled() && config.voidLostItemsRetrievable;
    }

    /**
     * @return {@code true} when the journey slot accepts broad food items instead of one item ID
     */
    public static boolean useFoodForJourney() {
        return config.useFoodForJourney;
    }

    /**
     * @return required count for the journey supply slot
     */
    public static int journeySupplyAmount() {
        return useFoodForJourney() ? clampAmount(config.journeyFoodAmount) : clampAmount(config.journeyCustomAmount);
    }

    /**
     * @return configured custom journey item, used only when food mode is disabled
     */
    public static Item journeyCustomItem() {
        return resolveItem(config.journeyCustomItem, Items.COOKED_BEEF);
    }

    /**
     * @return configured item for recovering burned loot
     */
    public static Item burnedFetchItem() {
        return resolveItem(config.burnedFetchItem, Items.POTION);
    }

    /**
     * @return required count for the burned-loot fetch slot
     */
    public static int burnedFetchAmount() {
        return clampAmount(config.burnedFetchAmount);
    }

    /**
     * @return configured item for recovering void/fallen loot
     */
    public static Item voidFetchItem() {
        return resolveItem(config.voidFetchItem, Items.ENDER_PEARL);
    }

    /**
     * @return required count for the void-loot fetch slot
     */
    public static int voidFetchAmount() {
        return clampAmount(config.voidFetchAmount);
    }

    /**
     * @return one-item ghost stack for the journey slot, including potion data when applicable
     */
    public static ItemStack journeyGhostStack() {
        Item item = useFoodForJourney() ? Items.COOKED_BEEF : journeyCustomItem();
        return ghostStack(item, resolvePotion(config.journeyCustomPotion, Potions.WATER));
    }

    /**
     * @return one-item ghost stack for the burned-loot fetch slot
     */
    public static ItemStack burnedGhostStack() {
        return ghostStack(burnedFetchItem(), resolvePotion(config.burnedFetchPotion, Potions.FIRE_RESISTANCE));
    }

    /**
     * @return one-item ghost stack for the void-loot fetch slot
     */
    public static ItemStack voidGhostStack() {
        return ghostStack(voidFetchItem(), resolvePotion(config.voidFetchPotion, Potions.WATER));
    }

    /**
     * @return whether the stack is food allowed by the default journey-food rule
     */
    public static boolean isJourneyFood(ItemStack stack) {
        return !stack.isEmpty()
                && stack.has(DataComponents.FOOD)
                && !DISALLOWED_JOURNEY_FOODS.contains(stack.getItem());
    }

    /**
     * @return whether the stack satisfies the active journey supply rule
     */
    public static boolean isJourneySupply(ItemStack stack) {
        if (!isFetchEnabled()) {
            return false;
        }

        if (useFoodForJourney()) {
            return isJourneyFood(stack);
        }

        return matchesConfiguredItem(stack, journeyCustomItem(), resolvePotion(config.journeyCustomPotion, Potions.WATER));
    }

    /**
     * @return whether the stack satisfies the burned-loot fetch slot rule
     */
    public static boolean isBurnedFetchSupply(ItemStack stack) {
        if (!isBurnedFetchEnabled() || stack.isEmpty()) {
            return false;
        }

        return matchesConfiguredItem(stack, burnedFetchItem(), resolvePotion(config.burnedFetchPotion, Potions.FIRE_RESISTANCE));
    }

    /**
     * @return whether the stack satisfies the void-loot fetch slot rule
     */
    public static boolean isVoidFetchSupply(ItemStack stack) {
        return isVoidFetchEnabled()
                && !stack.isEmpty()
                && matchesConfiguredItem(stack, voidFetchItem(), resolvePotion(config.voidFetchPotion, Potions.WATER));
    }

    /**
     * Selects the recovery-menu background and slot layout that matches enabled fetch features.
     */
    public static FetchLayout fetchLayout() {
        if (!isFetchEnabled()) {
            return FetchLayout.DISABLED;
        }

        int extraSlots = (isBurnedFetchEnabled() ? 1 : 0) + (isVoidFetchEnabled() ? 1 : 0);
        if (extraSlots == 0) {
            return FetchLayout.JOURNEY_ONLY;
        }

        return extraSlots == 1 ? FetchLayout.JOURNEY_PLUS_ONE : FetchLayout.FULL;
    }

    /**
     * @return X position for the journey slot in the active recovery texture
     */
    public static int journeySlotX() {
        return switch (fetchLayout()) {
            case DISABLED -> HIDDEN_SLOT_X;
            case JOURNEY_ONLY -> 47;
            case JOURNEY_PLUS_ONE -> 34;
            case FULL -> 21;
        };
    }

    /**
     * @return X position for the burned-loot slot, or hidden coordinate when disabled
     */
    public static int burnedSlotX() {
        if (!isBurnedFetchEnabled()) {
            return HIDDEN_SLOT_X;
        }

        return fetchLayout() == FetchLayout.FULL ? 47 : 60;
    }

    /**
     * @return X position for the void-loot slot, or hidden coordinate when disabled
     */
    public static int voidSlotX() {
        if (!isVoidFetchEnabled()) {
            return HIDDEN_SLOT_X;
        }

        return fetchLayout() == FetchLayout.FULL ? 73 : 60;
    }

    /**
     * @return path to the config file in Fabric's shared config directory
     */
    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    /**
     * Writes the current config object to disk. Defaults are written after first load so users get
     * a complete editable file.
     */
    private static void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            Better_lost_items.LOGGER.error("Failed saving Better Lost Items config to {}", path, exception);
        }
    }

    /**
     * Resolves a configured item ID with logging and fallback behavior.
     */
    private static Item resolveItem(String itemId, Item fallback) {
        if (itemId == null || itemId.isBlank()) {
            Better_lost_items.LOGGER.warn("Blank Better Lost Items config item, falling back to {}", BuiltInRegistries.ITEM.getKey(fallback));
            return fallback;
        }

        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            Better_lost_items.LOGGER.warn("Invalid Better Lost Items config item '{}', falling back to {}", itemId, BuiltInRegistries.ITEM.getKey(fallback));
            return fallback;
        }

        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    /**
     * Resolves a configured potion ID for potion-based custom requirements.
     */
    private static Holder<Potion> resolvePotion(String potionId, Holder<Potion> fallback) {
        if (potionId == null || potionId.isBlank()) {
            Better_lost_items.LOGGER.warn("Blank Better Lost Items config potion, falling back to {}", fallback.getRegisteredName());
            return fallback;
        }

        Identifier identifier = Identifier.tryParse(potionId);
        if (identifier == null) {
            Better_lost_items.LOGGER.warn("Invalid Better Lost Items config potion '{}', falling back to {}", potionId, fallback.getRegisteredName());
            return fallback;
        }

        return BuiltInRegistries.POTION.get(identifier).<Holder<Potion>>map(reference -> reference).orElseGet(() -> {
            Better_lost_items.LOGGER.warn("Unknown Better Lost Items config potion '{}', falling back to {}", potionId, fallback.getRegisteredName());
            return fallback;
        });
    }

    /**
     * Builds the display-only stack used by ghost slot rendering.
     */
    private static ItemStack ghostStack(Item item, Holder<Potion> potion) {
        if (isPotionItem(item)) {
            return PotionContents.createItemStack(item, potion);
        }

        return new ItemStack(item);
    }

    /**
     * Checks configured item identity, including potion contents for potion items.
     */
    private static boolean matchesConfiguredItem(ItemStack stack, Item item, Holder<Potion> potion) {
        if (!stack.is(item)) {
            return false;
        }

        if (!isPotionItem(item)) {
            return true;
        }

        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        return potionContents != null && potionContents.is(potion);
    }

    /**
     * @return whether an item can carry configured potion contents
     */
    private static boolean isPotionItem(Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    /**
     * Clamps configured costs into the item-count range supported by this UI and codecs.
     */
    private static int clampAmount(int amount) {
        return Math.max(1, Math.min(99, amount));
    }

    /**
     * Recovery-menu feature layout variants.
     *
     * <p>Each variant maps to a different 276x166 cropped section inside a larger texture. Slot
     * X positions are tied to these variants so custom textures can hide disabled fetch slots
     * without leaving clickable ghosts behind.</p>
     */
    public enum FetchLayout {
        DISABLED("textures/gui/lost_items_recovery_fetch_0.png"),
        JOURNEY_ONLY("textures/gui/lost_items_recovery_fetch_1.png"),
        JOURNEY_PLUS_ONE("textures/gui/lost_items_recovery_fetch_2.png"),
        FULL("textures/gui/lost_items_recovery.png");

        private final String texturePath;

        FetchLayout(String texturePath) {
            this.texturePath = texturePath;
        }

        /**
         * @return resource path for the background texture used by this layout
         */
        public String texturePath() {
            return this.texturePath;
        }
    }

    /**
     * Raw JSON-backed configuration values.
     *
     * <p>Fields remain public so Gson can populate them without a custom adapter. Gameplay code
     * should use the normalized static getters on {@link LostItemsConfig} instead of reading these
     * fields directly.</p>
     */
    public static final class ConfigData {
        @SerializedName("death_loot_payment_item")
        public String deathLootPaymentItem = "minecraft:emerald";

        @SerializedName("death_loot_payment_amount")
        public int deathLootPaymentAmount = 10;

        @SerializedName("fetch_enabled")
        public boolean fetchEnabled = true;

        @SerializedName("use_food_for_fetch_journey")
        public boolean useFoodForJourney = true;

        @SerializedName("journey_food_amount")
        public int journeyFoodAmount = 8;

        @SerializedName("journey_custom_item")
        public String journeyCustomItem = "minecraft:cooked_beef";

        @SerializedName("journey_custom_potion")
        public String journeyCustomPotion = "minecraft:water";

        @SerializedName("journey_custom_amount")
        public int journeyCustomAmount = 8;

        @SerializedName("burned_items_retrievable")
        public boolean burnedItemsRetrievable = true;

        @SerializedName("burned_fetch_item")
        public String burnedFetchItem = "minecraft:potion";

        @SerializedName("burned_fetch_potion")
        public String burnedFetchPotion = "minecraft:fire_resistance";

        @SerializedName("burned_fetch_amount")
        public int burnedFetchAmount = 1;

        @SerializedName("void_lost_items_retrievable")
        public boolean voidLostItemsRetrievable = true;

        @SerializedName("void_fetch_item")
        public String voidFetchItem = "minecraft:ender_pearl";

        @SerializedName("void_fetch_potion")
        public String voidFetchPotion = "minecraft:water";

        @SerializedName("void_fetch_amount")
        public int voidFetchAmount = 2;

        /**
         * Creates a detached copy for UI editing.
         *
         * @return copied config data with the same raw values
         */
        public ConfigData copy() {
            ConfigData copy = new ConfigData();
            copy.deathLootPaymentItem = this.deathLootPaymentItem;
            copy.deathLootPaymentAmount = this.deathLootPaymentAmount;
            copy.fetchEnabled = this.fetchEnabled;
            copy.useFoodForJourney = this.useFoodForJourney;
            copy.journeyFoodAmount = this.journeyFoodAmount;
            copy.journeyCustomItem = this.journeyCustomItem;
            copy.journeyCustomPotion = this.journeyCustomPotion;
            copy.journeyCustomAmount = this.journeyCustomAmount;
            copy.burnedItemsRetrievable = this.burnedItemsRetrievable;
            copy.burnedFetchItem = this.burnedFetchItem;
            copy.burnedFetchPotion = this.burnedFetchPotion;
            copy.burnedFetchAmount = this.burnedFetchAmount;
            copy.voidLostItemsRetrievable = this.voidLostItemsRetrievable;
            copy.voidFetchItem = this.voidFetchItem;
            copy.voidFetchPotion = this.voidFetchPotion;
            copy.voidFetchAmount = this.voidFetchAmount;
            return copy;
        }
    }
}
