package org.betterLostItems.better_lost_items;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Administrative command entry point for inspecting and editing a player's death-loot cache.
 *
 * <p>The command is intentionally gated behind gamemaster permissions because it can delete a
 * player's saved recovery state or move their entire inventory into the lost-loot pool. It is
 * primarily a testing/debugging helper for server owners and mod developers.</p>
 */
public final class DeathLootCacheCommand {
    private DeathLootCacheCommand() {
    }

    /**
     * Registers {@code /deathlootcache <players> <clear|add|list>}.
     *
     * @param dispatcher Brigadier command dispatcher supplied by NeoForge
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deathlootcache")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.literal("clear")
                                .executes(context -> clearPlayers(context, EntityArgument.getPlayers(context, "player"))))
                        .then(Commands.literal("Clear")
                                .executes(context -> clearPlayers(context, EntityArgument.getPlayers(context, "player"))))
                        .then(Commands.literal("add")
                                .executes(context -> addPlayers(context, EntityArgument.getPlayers(context, "player"))))
                        .then(Commands.literal("Add")
                                .executes(context -> addPlayers(context, EntityArgument.getPlayers(context, "player"))))
                        .then(Commands.literal("list")
                                .executes(context -> listPlayers(context, EntityArgument.getPlayers(context, "player"))))
                        .then(Commands.literal("List")
                                .executes(context -> listPlayers(context, EntityArgument.getPlayers(context, "player"))))
                )
        );
    }

    /**
     * Clears every saved recovery bucket for the selected players.
     */
    private static int clearPlayers(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        LostItemsStorage storage = LostItemsStorageManager.get(context.getSource().getServer());
        int totalCleared = 0;
        for (ServerPlayer player : players) {
            int lostCount = storage.getPlayerLostItems(player.getUUID()).size();
            int retrievedCount = storage.getPlayerPurchasedItems(player.getUUID()).size();
            int burnedCount = storage.getPlayerBurnedItems(player.getUUID()).size();
            int fallenCount = storage.getPlayerFallenItems(player.getUUID()).size();
            int pendingFetchCount = storage.getPlayerPendingFetchItems(player.getUUID()).size();
            int trackedChunkCount = storage.getTrackedDeathChunks(player.getUUID()).size();
            int clearedCount = storage.clearPlayerRecoveryCache(player.getUUID());
            totalCleared += clearedCount;

            int finalLostCount = lostCount;
            int finalRetrievedCount = retrievedCount;
            int finalBurnedCount = burnedCount;
            int finalFallenCount = fallenCount;
            int finalPendingFetchCount = pendingFetchCount;
            int finalTrackedChunkCount = trackedChunkCount;
            int finalClearedCount = clearedCount;
            context.getSource().sendSuccess(() -> Component.literal(
                    "Cleared " + finalClearedCount + " cached stacks for " + player.getGameProfile().getName()
                            + " (" + finalLostCount + " lost, " + finalRetrievedCount + " retrieved, " + finalBurnedCount + " burned, " + finalFallenCount + " fallen, " + finalPendingFetchCount + " pending fetch, " + finalTrackedChunkCount + " tracked chunks)."
            ), true);
        }

        return totalCleared;
    }

    /**
     * Moves all non-empty inventory stacks into each selected player's lost-loot cache.
     *
     * <p>This consumes the live inventory, matching the command's role as a test fixture for the
     * recovery UI rather than a duplication tool.</p>
     */
    private static int addPlayers(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        LostItemsStorage storage = LostItemsStorageManager.get(context.getSource().getServer());
        int totalAdded = 0;
        for (ServerPlayer player : players) {
            List<ItemStack> inventoryStacks = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().removeItemNoUpdate(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                inventoryStacks.add(stack);
            }

            int addedCount = storage.addPlayerLostItems(player.getUUID(), inventoryStacks);
            totalAdded += addedCount;

            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();

            int finalAddedCount = addedCount;
            context.getSource().sendSuccess(() -> Component.literal(
                    "Moved " + finalAddedCount + " inventory stacks into " + player.getGameProfile().getName() + "'s death loot cache."
            ), true);
        }

        return totalAdded;
    }

    /**
     * Prints a compact summary of every recovery bucket for the selected players.
     */
    private static int listPlayers(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        LostItemsStorage storage = LostItemsStorageManager.get(context.getSource().getServer());
        int totalListed = 0;
        for (ServerPlayer player : players) {
            List<LostItemEntry> lostItems = storage.getPlayerLostItems(player.getUUID());
            List<LostItemEntry> retrievedItems = storage.getPlayerPurchasedItems(player.getUUID());
            List<LostItemEntry> burnedItems = storage.getPlayerBurnedItems(player.getUUID());
            List<LostItemEntry> fallenItems = storage.getPlayerFallenItems(player.getUUID());
            List<LostItemEntry> pendingFetchItems = storage.getPlayerPendingFetchItems(player.getUUID());
            List<TrackedDeathChunk> trackedChunks = storage.getTrackedDeathChunks(player.getUUID());
            totalListed += lostItems.size() + retrievedItems.size() + burnedItems.size() + fallenItems.size() + pendingFetchItems.size();

            int finalLostCount = lostItems.size();
            int finalRetrievedCount = retrievedItems.size();
            int finalBurnedCount = burnedItems.size();
            int finalFallenCount = fallenItems.size();
            int finalPendingFetchCount = pendingFetchItems.size();
            int finalTrackedChunkCount = trackedChunks.size();
            context.getSource().sendSuccess(() -> Component.literal(
                    player.getGameProfile().getName() + ": " + finalLostCount + " lost stacks, " + finalRetrievedCount + " retrieved stacks, " + finalBurnedCount + " burned stacks, " + finalFallenCount + " fallen stacks, " + finalPendingFetchCount + " pending fetch stacks, " + finalTrackedChunkCount + " tracked chunks."
            ), false);

            if (lostItems.isEmpty() && retrievedItems.isEmpty() && burnedItems.isEmpty() && fallenItems.isEmpty() && pendingFetchItems.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("  No cached items."), false);
            }

            sendEntries(context.getSource(), "  Lost Items:", lostItems);
            sendEntries(context.getSource(), "  Retrieved Items:", retrievedItems);
            sendEntries(context.getSource(), "  Burned Items:", burnedItems);
            sendEntries(context.getSource(), "  Fallen Items:", fallenItems);
            sendEntries(context.getSource(), "  Pending Fetch Items:", pendingFetchItems);
        }

        return totalListed;
    }

    /**
     * Sends one formatted list section to the command source.
     */
    private static void sendEntries(CommandSourceStack source, String title, List<LostItemEntry> entries) {
        source.sendSuccess(() -> Component.literal(title), false);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("    (none)"), false);
            return;
        }

        for (LostItemEntry entry : entries) {
            ItemStack stack = entry.stack();
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Component line = Component.literal("    - " + stack.getCount() + "x ")
                    .append(stack.getHoverName())
                    .append(Component.literal(" (" + itemId + ")"));
            source.sendSuccess(() -> line, false);
        }
    }
}
