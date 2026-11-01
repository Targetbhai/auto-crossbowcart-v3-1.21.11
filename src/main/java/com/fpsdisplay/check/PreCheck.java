package com.fpsdisplay.check;

import com.fpsdisplay.aim.AimDetector;
import com.fpsdisplay.util.Actions;
import com.fpsdisplay.util.Hotbar;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** Validates everything before the sequence starts. Has no side effects at all. */
public final class PreCheck {
    private PreCheck() {}

    public record Result(boolean ok, String message, BlockPos targetPos, BlockPos railPos, BlockPos gapPos) {}

    public static Result run(MinecraftClient mc) {
        List<String> problems = new ArrayList<>();
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        BlockPos target = null, rail = null, gap = null;

        BlockHitResult hit = AimDetector.blockTarget(mc);
        if (hit == null || hit.getSide() != Direction.UP) {
            problems.add("Look at the top face of a block");
        } else {
            target = hit.getBlockPos();
            BlockState ts = w.getBlockState(target);
            rail = target.up();
            if (!ts.isSideSolidFullSquare(w, target, Direction.UP)) {
                problems.add("Target block is not solid on top");
            } else if (!w.getBlockState(rail).isAir() || !Blocks.RAIL.getDefaultState().canPlaceAt(w, rail)) {
                problems.add("No space above the block for a rail");
            }

            // Gap block: neighbour of the target on the side facing the player (dominant horizontal axis).
            double dx = p.getX() - (target.getX() + 0.5);
            double dz = p.getZ() - (target.getZ() + 0.5);
            if (Math.hypot(dx, dz) < 1.5) {
                problems.add("Stand further from the target block");
            } else {
                Direction toward = Direction.getFacing(dx, 0.0, dz);
                gap = target.offset(toward);
                if (!w.getBlockState(gap).isSideSolidFullSquare(w, gap, Direction.UP)
                        || !w.getBlockState(gap.up()).isAir()) {
                    problems.add("Gap block in front of the rail is not solid with free space above");
                }
            }
        }

        if (Hotbar.find(p, s -> s.isOf(Items.RAIL)) < 0) problems.add("No rail in hotbar");
        if (Hotbar.find(p, s -> s.isOf(Items.TNT_MINECART)) < 0) problems.add("No TNT minecart in hotbar");
        if (Hotbar.find(p, s -> s.isOf(Items.FLINT_AND_STEEL)) < 0) problems.add("No flint and steel in hotbar");
        if (Hotbar.find(p, Actions::isCrossbow) < 0) problems.add("No crossbow in hotbar");
        else if (Actions.findChargedCrossbow(p) < 0) problems.add("Crossbow is not loaded");
        if (!Hotbar.hasArrow(p)) problems.add("No arrows in inventory (needed for reload)");

        if (!problems.isEmpty()) {
            return new Result(false, String.join(" | ", problems), null, null, null);
        }
        return new Result(true, "", target, rail, gap);
    }
}
