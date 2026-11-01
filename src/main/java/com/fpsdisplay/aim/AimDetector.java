package com.fpsdisplay.aim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Read-only aim detection. Uses the client's own crosshair raycast (already limited to
 * vanilla reach and line of sight) plus an angle check for the cart. Never changes rotation.
 */
public final class AimDetector {
    private AimDetector() {}

    /** The block the crosshair currently targets, or null. */
    public static BlockHitResult blockTarget(MinecraftClient mc) {
        return mc.crosshairTarget instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    /** Crosshair is on the top face of the given block. */
    public static boolean onBlockTop(MinecraftClient mc, BlockPos pos) {
        BlockHitResult b = blockTarget(mc);
        return b != null && b.getBlockPos().equals(pos) && b.getSide() == Direction.UP;
    }

    /** Crosshair is on the given block (any face). */
    public static boolean onBlock(MinecraftClient mc, BlockPos pos) {
        BlockHitResult b = blockTarget(mc);
        return b != null && b.getBlockPos().equals(pos);
    }

    /**
     * Crosshair counts as "on the cart" if the raycast hits it, or the look direction is within
     * the angle tolerance of the cart's centre and the cart is within shot distance.
     */
    public static boolean onEntity(MinecraftClient mc, Entity target, double toleranceDeg, double maxDistance) {
        if (mc.crosshairTarget instanceof EntityHitResult e && e.getEntity() == target) return true;
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
        double dist = to.length();
        if (dist < 1.0E-4 || dist > maxDistance) return false;
        Vec3d look = p.getRotationVec(1.0F);
        double dot = Math.max(-1.0, Math.min(1.0, look.dotProduct(to.multiply(1.0 / dist))));
        return Math.toDegrees(Math.acos(dot)) <= toleranceDeg;
    }
}
