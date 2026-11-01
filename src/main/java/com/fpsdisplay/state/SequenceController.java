package com.fpsdisplay.state;

import com.fpsdisplay.aim.AimDetector;
import com.fpsdisplay.check.PreCheck;
import com.fpsdisplay.config.ModConfig;
import com.fpsdisplay.util.Actions;
import com.fpsdisplay.util.Hotbar;
import com.fpsdisplay.util.Humanizer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * The whole sequence as a tick-driven state machine. The camera is never touched: states that need
 * aim simply poll the crosshair until the player has aimed correctly.
 *
 * Every action state is preceded by a randomized wait (see {@link Humanizer}); a slot switch always
 * happens in a "*_SWITCH" state and the actual use happens in a later state, so a switch and a use
 * never share a tick. Anything unexpected aborts instead of retrying.
 */
public final class SequenceController {
    public enum State {
        IDLE,
        RAIL_SWITCH, RAIL_PLACE, RAIL_VERIFY,
        CART_SWITCH, CART_PLACE, CART_VERIFY,
        GAP_AIM, FIRE_SWITCH, FIRE_USE, FIRE_VERIFY,
        SHOOT_AIM, SHOOT_SWITCH, SHOOT_FIRE, SHOOT_VERIFY,
        RELOAD_SWITCH, RELOAD_START, RELOADING, RELOAD_VERIFY,
        FINISH
    }

    private static final SequenceController INSTANCE = new SequenceController();
    public static SequenceController get() { return INSTANCE; }

    private final Humanizer human = new Humanizer();

    private State state = State.IDLE;
    private int wait;          // ticks left before the current state may act
    private int phaseTicks;    // ticks spent acting in the current state (for polling/verify)
    private int waitingTicks;  // ticks spent waiting for the player's aim in this step (timeout)
    private int aimCounter;    // consecutive ticks the aim has been correct
    private int fireTries;

    private int originalSlot = -1;
    private int crossbowSlot = -1;
    private BlockPos targetPos, railPos, gapPos;
    private Entity cart;
    private RegistryKey<World> startDimension;

    // HUD text
    private String message = "";
    private boolean messageIsError;
    private int messageTicks;

    // ---------------------------------------------------------------- public API

    public boolean isRunning() { return state != State.IDLE; }

    /** Called when the start key is pressed. */
    public void onStartKey(MinecraftClient mc) {
        if (isRunning()) {
            if (ModConfig.get().startKeyCancelsWhenRunning) cancel(mc);
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        PreCheck.Result r = PreCheck.run(mc); // no side effects
        if (!r.ok()) {
            say(r.message(), true, 100);
            return;
        }
        human.newSequence();
        targetPos = r.targetPos();
        railPos = r.railPos();
        gapPos = r.gapPos();
        originalSlot = Hotbar.selected(mc.player);
        startDimension = mc.world.getRegistryKey();
        cart = null;
        crossbowSlot = -1;
        fireTries = 0;
        aimCounter = 0;
        waitingTicks = 0;
        message = "";
        go(State.RAIL_SWITCH, human.delay()); // "reaction time" after the key press
    }

    public void cancel(MinecraftClient mc) {
        if (isRunning()) abort(mc, "Cancelled");
        else releaseUse(mc);
    }

    public void onDisconnect(MinecraftClient mc) {
        if (isRunning()) {
            state = State.IDLE;
            releaseUse(mc);
        }
    }

    /** Lines for the HUD: [0] headline, [1] hint (may be empty). */
    public String[] hudLines() {
        if (messageTicks > 0 && !message.isEmpty() && !isRunning()) return new String[]{message, ""};
        if (!isRunning()) return new String[]{"", ""};
        String hint = (messageTicks > 0) ? message : "";
        return switch (state) {
            case RAIL_SWITCH, RAIL_PLACE, RAIL_VERIFY ->
                    new String[]{"Step 1/4: placing rail", "Keep the crosshair on the target block"};
            case CART_SWITCH, CART_PLACE, CART_VERIFY ->
                    new String[]{"Step 1/4: placing TNT minecart", "Keep the crosshair on the rail"};
            case GAP_AIM, FIRE_SWITCH, FIRE_USE, FIRE_VERIFY ->
                    new String[]{"Step 2/4: aim at the gap block", hint.isEmpty() ? "Aim at the block between you and the cart (top face)" : hint};
            case SHOOT_AIM, SHOOT_SWITCH, SHOOT_FIRE, SHOOT_VERIFY ->
                    new String[]{"Step 3/4: look back at the TNT minecart", hint};
            case RELOAD_SWITCH, RELOAD_START, RELOADING, RELOAD_VERIFY, FINISH ->
                    new String[]{"Step 4/4: reloading crossbow", ""};
            default -> new String[]{"", ""};
        };
    }

    public boolean currentMessageIsError() { return messageIsError; }

    // ---------------------------------------------------------------- tick

    public void tick(MinecraftClient mc) {
        if (messageTicks > 0) messageTicks--;
        if (state == State.IDLE) return;

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        if (!p.isAlive() || p.isSpectator()) { abort(mc, "Player died"); return; }
        if (mc.world.getRegistryKey() != startDimension) { abort(mc, "Dimension changed"); return; }
        if (mc.currentScreen != null) return; // paused while a screen is open

        if (isAimState(state)) {
            waitingTicks++;
            if (waitingTicks > ModConfig.get().aimTimeoutSeconds * 20) {
                abort(mc, "Timed out waiting for aim");
                return;
            }
        }
        if (wait > 0) { wait--; return; }
        phaseTicks++;

        ModConfig cfg = ModConfig.get();

        switch (state) {
            // ---------------- step 1: rail
            case RAIL_SWITCH -> {
                int slot = Hotbar.find(p, s -> s.isOf(Items.RAIL));
                if (slot < 0) { abort(mc, "Rail is gone"); return; }
                Hotbar.select(p, slot);
                go(State.RAIL_PLACE, human.delay());
            }
            case RAIL_PLACE -> {
                if (!p.getMainHandStack().isOf(Items.RAIL)) { abort(mc, "Rail is gone or slot changed"); return; }
                BlockHitResult hit = AimDetector.blockTarget(mc);
                if (hit == null || !hit.getBlockPos().equals(targetPos) || hit.getSide() != net.minecraft.util.math.Direction.UP) {
                    return; // wait for the player to keep the crosshair on the target
                }
                Actions.useBlock(mc, hit);
                waitingTicks = 0;
                go(State.RAIL_VERIFY, human.delay() + 2);
            }
            case RAIL_VERIFY -> {
                if (mc.world.getBlockState(railPos).isOf(Blocks.RAIL)) {
                    go(State.CART_SWITCH, human.delay());
                } else if (phaseTicks > 10) {
                    abort(mc, "Rail was rejected by the server");
                }
            }
            // ---------------- step 1: cart
            case CART_SWITCH -> {
                int slot = Hotbar.find(p, s -> s.isOf(Items.TNT_MINECART));
                if (slot < 0) { abort(mc, "TNT minecart is gone"); return; }
                Hotbar.select(p, slot);
                go(State.CART_PLACE, human.delay());
            }
            case CART_PLACE -> {
                if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL)) { abort(mc, "Rail is gone"); return; }
                if (!p.getMainHandStack().isOf(Items.TNT_MINECART)) { abort(mc, "TNT minecart is gone or slot changed"); return; }
                // After the rail exists, the crosshair (still on the old spot) hits the rail block.
                if (!AimDetector.onBlock(mc, railPos)) return;
                Actions.useBlock(mc, AimDetector.blockTarget(mc));
                waitingTicks = 0;
                go(State.CART_VERIFY, human.delay() + 2);
            }
            case CART_VERIFY -> {
                cart = findCart(mc);
                if (cart != null) {
                    aimCounter = 0;
                    waitingTicks = 0;
                    go(State.GAP_AIM, human.delay());
                } else if (phaseTicks > 10) {
                    abort(mc, "TNT minecart was not placed (rejected by server)");
                }
            }
            // ---------------- step 2: fire
            case GAP_AIM -> {
                String bad = cartAndRailProblem(mc);
                if (bad != null) { abort(mc, bad); return; }
                if (Hotbar.find(p, s -> s.isOf(Items.FLINT_AND_STEEL)) < 0) { abort(mc, "Flint and steel is gone"); return; }
                if (!mc.world.getBlockState(gapPos.up()).isAir()) { abort(mc, "Space above the gap block is blocked"); return; }
                aimCounter = AimDetector.onBlockTop(mc, gapPos) ? aimCounter + 1 : 0;
                if (aimCounter >= cfg.aimHoldTicks) go(State.FIRE_SWITCH, human.delay());
            }
            case FIRE_SWITCH -> {
                int slot = Hotbar.find(p, s -> s.isOf(Items.FLINT_AND_STEEL));
                if (slot < 0) { abort(mc, "Flint and steel is gone"); return; }
                Hotbar.select(p, slot);
                go(State.FIRE_USE, human.delay());
            }
            case FIRE_USE -> {
                String bad = cartAndRailProblem(mc);
                if (bad != null) { abort(mc, bad); return; }
                if (!p.getMainHandStack().isOf(Items.FLINT_AND_STEEL)) { abort(mc, "Flint and steel is gone or slot changed"); return; }
                if (!AimDetector.onBlockTop(mc, gapPos)) { // aim drifted during the delay
                    aimCounter = 0;
                    go(State.GAP_AIM, 0);
                    return;
                }
                Actions.useBlock(mc, AimDetector.blockTarget(mc));
                go(State.FIRE_VERIFY, human.delay() + 2);
            }
            case FIRE_VERIFY -> {
                if (mc.world.getBlockState(gapPos.up()).isIn(BlockTags.FIRE)) {
                    aimCounter = 0;
                    waitingTicks = 0;
                    go(State.SHOOT_AIM, human.delay());
                } else if (phaseTicks > 10) {
                    // Server did not give us fire. One retry only, after a long pause, never rapid.
                    if (fireTries < cfg.fireRetries) {
                        fireTries++;
                        aimCounter = 0;
                        say("Fire not lit - aim at the gap block again", false, 60);
                        go(State.GAP_AIM, human.longPause());
                    } else {
                        abort(mc, "Could not light the fire (rejected by server)");
                    }
                }
            }
            // ---------------- step 3: shoot
            case SHOOT_AIM -> {
                String bad = shootProblem(mc);
                if (bad != null) { abort(mc, bad); return; }
                aimCounter = AimDetector.onEntity(mc, cart, cfg.angleToleranceDegrees, cfg.maxShotDistance) ? aimCounter + 1 : 0;
                if (aimCounter >= cfg.aimHoldTicks) go(State.SHOOT_SWITCH, human.delay());
            }
            case SHOOT_SWITCH -> {
                int slot = Actions.findChargedCrossbow(p);
                if (slot < 0) { abort(mc, "Crossbow is no longer loaded"); return; }
                crossbowSlot = slot;
                Hotbar.select(p, slot);
                go(State.SHOOT_FIRE, human.delay());
            }
            case SHOOT_FIRE -> {
                String bad = shootProblem(mc);
                if (bad != null) { abort(mc, bad); return; }
                if (!Actions.isCharged(p.getMainHandStack())) { abort(mc, "Crossbow is no longer loaded"); return; }
                if (!AimDetector.onEntity(mc, cart, cfg.angleToleranceDegrees, cfg.maxShotDistance)) {
                    aimCounter = 0; // drifted: keep waiting (crossbow stays selected)
                    go(State.SHOOT_AIM, 0);
                    return;
                }
                Actions.useItem(mc);
                go(State.SHOOT_VERIFY, human.delay() + 2);
            }
            case SHOOT_VERIFY -> {
                if (!Actions.isCharged(Hotbar.stack(p, crossbowSlot))) {
                    go(State.RELOAD_SWITCH, human.delay() + 4);
                } else if (phaseTicks > 10) {
                    abort(mc, "Crossbow did not fire (rejected by server)");
                }
            }
            // ---------------- step 4: reload
            case RELOAD_SWITCH -> {
                if (!Actions.isCrossbow(Hotbar.stack(p, crossbowSlot))) { abort(mc, "Crossbow is gone"); return; }
                if (!Hotbar.hasArrow(p)) { abort(mc, "No arrows left to reload"); return; }
                Hotbar.select(p, crossbowSlot);
                go(State.RELOAD_START, human.delay());
            }
            case RELOAD_START -> {
                if (!Actions.isCrossbow(p.getMainHandStack())) { abort(mc, "Crossbow is gone or slot changed"); return; }
                if (!Hotbar.hasArrow(p)) { abort(mc, "No arrows left to reload"); return; }
                // interactItem starts the "use" locally; holding the use key keeps vanilla from cancelling it.
                if (!Actions.useItem(mc)) { abort(mc, "Reload could not start"); return; }
                mc.options.useKey.setPressed(true);
                go(State.RELOADING, 1);
            }
            case RELOADING -> {
                if (Hotbar.selected(p) != crossbowSlot) { abort(mc, "Slot changed during reload"); return; }
                if (!p.isUsingItem()) {
                    if (phaseTicks > 4) abort(mc, "Reload did not start");
                    return;
                }
                if (phaseTicks > 200) { abort(mc, "Reload took too long"); return; }
                // Release only once the real charge time (Quick Charge aware) has fully elapsed.
                if (p.getItemUseTime() >= Actions.pullTicks(p, p.getMainHandStack()) + 1) {
                    mc.options.useKey.setPressed(false);
                    go(State.RELOAD_VERIFY, human.delay() + 3);
                }
            }
            case RELOAD_VERIFY -> {
                if (Actions.isCharged(Hotbar.stack(p, crossbowSlot))) {
                    go(State.FINISH, human.delay());
                } else if (phaseTicks > 12) {
                    abort(mc, "Reload failed");
                }
            }
            case FINISH -> {
                if (originalSlot >= 0) Hotbar.select(p, originalSlot);
                state = State.IDLE;
                say("Sequence complete", false, 60);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- helpers

    private void go(State s, int delay) {
        state = s;
        wait = Math.max(0, delay);
        phaseTicks = 0;
    }

    private static boolean isAimState(State s) {
        return s == State.RAIL_PLACE || s == State.CART_PLACE || s == State.GAP_AIM || s == State.SHOOT_AIM;
    }

    private void say(String text, boolean error, int ticks) {
        message = text;
        messageIsError = error;
        messageTicks = ticks;
    }

    private void releaseUse(MinecraftClient mc) {
        if (mc.options != null) mc.options.useKey.setPressed(false);
    }

    /** Stops everything, restores the original hotbar slot and reports why. */
    private void abort(MinecraftClient mc, String reason) {
        releaseUse(mc);
        if (mc.player != null && originalSlot >= 0) Hotbar.select(mc.player, originalSlot);
        state = State.IDLE;
        wait = 0;
        say(reason, true, 120);
    }

    /** Looks for a TNT minecart entity at the rail. Matches by entity type to avoid package-name risk. */
    private Entity findCart(MinecraftClient mc) {
        Box box = new Box(railPos).expand(0.6);
        List<Entity> found = mc.world.getEntitiesByClass(Entity.class, box, e -> e.getType() == EntityType.TNT_MINECART);
        return found.isEmpty() ? null : found.get(0);
    }

    private String cartAndRailProblem(MinecraftClient mc) {
        if (cart == null || cart.isRemoved() || !cart.isAlive()) return "TNT minecart is gone";
        if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL)) return "Rail is gone";
        return null;
    }

    private String shootProblem(MinecraftClient mc) {
        String bad = cartAndRailProblem(mc);
        if (bad != null) return bad;
        if (!mc.world.getBlockState(gapPos.up()).isIn(BlockTags.FIRE)) return "Fire went out";
        if (Actions.findChargedCrossbow(mc.player) < 0) return "Crossbow is no longer loaded";
        return null;
    }
}
