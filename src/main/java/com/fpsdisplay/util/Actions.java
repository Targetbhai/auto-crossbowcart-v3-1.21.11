package com.fpsdisplay.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/** Thin wrappers over the vanilla interaction manager, so every action takes the normal client path. */
public final class Actions {
    private Actions() {}

    /** Right-click a block face with the main hand. Returns true if the client accepted it. */
    public static boolean useBlock(MinecraftClient mc, BlockHitResult hit) {
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        swingIfNeeded(mc.player, r);
        return r.isAccepted();
    }

    /** Right-click in the air with the main hand (fires a loaded crossbow / starts charging one). */
    public static boolean useItem(MinecraftClient mc) {
        ActionResult r = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        swingIfNeeded(mc.player, r);
        return r.isAccepted();
    }

    // Mirrors what vanilla MinecraftClient.doItemUse does with the result.
    private static void swingIfNeeded(ClientPlayerEntity p, ActionResult r) {
        if (r instanceof ActionResult.Success s && s.swingSource() == ActionResult.SwingSource.CLIENT) {
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    public static boolean isCrossbow(ItemStack s) {
        return s.isOf(Items.CROSSBOW);
    }

    /** A crossbow is "charged" when it holds at least one charged projectile. */
    public static boolean isCharged(ItemStack s) {
        if (!isCrossbow(s)) return false;
        ChargedProjectilesComponent c = s.get(DataComponentTypes.CHARGED_PROJECTILES);
        return c != null && !c.isEmpty();
    }

    /** Hotbar slot of a loaded crossbow, or -1. */
    public static int findChargedCrossbow(ClientPlayerEntity p) {
        return Hotbar.find(p, Actions::isCharged);
    }

    /** Ticks of holding use required to finish charging (includes Quick Charge). */
    public static int pullTicks(ClientPlayerEntity p, ItemStack crossbow) {
        return CrossbowItem.getPullTime(crossbow, p);
    }
}
