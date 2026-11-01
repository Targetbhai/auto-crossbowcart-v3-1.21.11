package com.fpsdisplay.util;

import com.fpsdisplay.config.ModConfig;

import java.util.Random;

/**
 * Produces randomized tick delays. Each delay is drawn independently from a clamped
 * bell-shaped distribution over [min, max]; a per-sequence speed factor makes whole runs
 * differ from each other, and rare longer pauses break up the rhythm.
 */
public final class Humanizer {
    private final Random rng = new Random();
    private double sequenceFactor = 1.0;

    public void newSequence() {
        sequenceFactor = 0.85 + rng.nextDouble() * 0.4; // 0.85 .. 1.25
    }

    public int delay() {
        ModConfig c = ModConfig.get();
        int lo = c.delayMinTicks;
        int hi = c.delayMaxTicks;
        double mean = (lo + hi) / 2.0;
        double sd = Math.max(0.5, (hi - lo) / 4.0);
        double v = mean + rng.nextGaussian() * sd;
        v = Math.max(lo, Math.min(hi, v));
        v *= sequenceFactor;
        if (rng.nextDouble() < 0.06) v += 4 + rng.nextInt(7); // occasional hesitation
        if (c.cautiousMode) v *= 1.5;
        int out = (int) Math.round(v);
        int floor = c.cautiousMode ? (int) Math.ceil(lo * 1.5) : lo;
        return Math.max(floor, out);
    }

    /** A longer pause, used before a retry. */
    public int longPause() {
        return delay() * 2 + 10 + rng.nextInt(10);
    }
}
