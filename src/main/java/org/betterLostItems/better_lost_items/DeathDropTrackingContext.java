package org.betterLostItems.better_lost_items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Thread-local marker used while Minecraft is converting a player's inventory into death drops.
 *
 * <p>The vanilla death-drop path creates {@code ItemEntity} instances from several different
 * methods. Rather than trying to pass a player UUID through every call, the player mixin pushes
 * the dying player's UUID before {@code dropAllDeathLoot} runs and pops it immediately after.
 * Item/drop mixins can then ask this class which player currently owns newly spawned death loot.</p>
 *
 * <p>The value is a stack instead of a single UUID so nested calls are safe. That matters for
 * container items such as shulker boxes: when a tagged shulker is destroyed by fire, vanilla can
 * emit its contents during the same call chain and those contents should inherit the same owner.</p>
 */
public final class DeathDropTrackingContext {
    private static final ThreadLocal<Deque<UUID>> PLAYER_DEATH_OWNER = new ThreadLocal<>();

    private DeathDropTrackingContext() {
    }

    /**
     * Starts a death-drop ownership scope for the current server thread.
     *
     * @param playerId player whose death drops are currently being created
     */
    public static void push(UUID playerId) {
        Deque<UUID> stack = PLAYER_DEATH_OWNER.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            PLAYER_DEATH_OWNER.set(stack);
        }

        stack.push(playerId);
    }

    /**
     * Ends the most recent death-drop ownership scope.
     */
    public static void pop() {
        Deque<UUID> stack = PLAYER_DEATH_OWNER.get();
        if (stack == null) {
            return;
        }

        if (!stack.isEmpty()) {
            stack.pop();
        }

        if (stack.isEmpty()) {
            PLAYER_DEATH_OWNER.remove();
        }
    }

    /**
     * @return player UUID at the top of the current death-drop scope, or {@code null}
     */
    public static UUID currentPlayerId() {
        Deque<UUID> stack = PLAYER_DEATH_OWNER.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }
}
