package com.fpsdisplay.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

import java.util.function.Predicate;

/** Hotbar / inventory lookups and slot switching. */
public final class Hotbar {
    private Hotbar() {}

    /** First hotbar slot (0-8) whose stack matches, or -1. */
    public static int find(ClientPlayerEntity p, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) {
            if (test.test(p.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    public static int selected(ClientPlayerEntity p) {
        return p.getInventory().getSelectedSlot();
    }

    public static ItemStack stack(ClientPlayerEntity p, int slot) {
        return p.getInventory().getStack(slot);
    }

    /**
     * Selects a slot. Returns true if the slot actually changed. The vanilla client syncs the
     * selected slot to the server at the start of its next tick (syncSelectedSlot).
     */
    public static boolean select(ClientPlayerEntity p, int slot) {
        if (slot < 0 || slot > 8 || Hotbar.selected(p) == slot) return false;
        p.getInventory().setSelectedSlot(slot);
        return true;
    }

    /** Any arrow anywhere in the inventory (main, armor, offhand slots included in size()). */
    public static boolean hasArrow(ClientPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && s.isIn(ItemTags.ARROWS)) return true;
        }
        return false;
    }
}
