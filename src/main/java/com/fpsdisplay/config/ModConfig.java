package com.fpsdisplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config stored at config/fpsdisplay.json. Missing fields fall back to defaults. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    /** Consecutive ticks the crosshair must stay on the target before acting. */
    public int aimHoldTicks = 3;
    /** Max angle (degrees) between crosshair and cart centre to count as "aimed at the cart". */
    public double angleToleranceDegrees = 4.0;
    /** Delay between consecutive actions / after a hotbar switch is drawn from this range (ticks). */
    public int delayMinTicks = 3;
    public int delayMaxTicks = 8;
    /** Give up while waiting for the player to aim. */
    public int aimTimeoutSeconds = 30;
    /** Cart further than this (blocks) is not shot at. */
    public double maxShotDistance = 32.0;
    /** Number of times lighting the fire is retried (after a long pause). */
    public int fireRetries = 1;
    public boolean hudEnabled = true;
    /** Multiplies all action delays by 1.5. */
    public boolean cautiousMode = true;
    /** If true, pressing the start key during a run cancels it; otherwise it is ignored. */
    public boolean startKeyCancelsWhenRunning = false;

    public static ModConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fpsdisplay.json");
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                ModConfig loaded = GSON.fromJson(Files.readString(p), ModConfig.class);
                if (loaded != null) instance = loaded;
            }
        } catch (Exception e) {
            System.err.println("[fpsdisplay] Could not read config, using defaults: " + e);
        }
        instance.sanitize();
        save(); // writes defaults / newly added fields back
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[fpsdisplay] Could not write config: " + e);
        }
    }

    private void sanitize() {
        aimHoldTicks = Math.max(1, aimHoldTicks);
        angleToleranceDegrees = Math.max(0.5, Math.min(30.0, angleToleranceDegrees));
        delayMinTicks = Math.max(2, delayMinTicks); // never act on the tick of a slot change
        delayMaxTicks = Math.max(delayMinTicks, delayMaxTicks);
        aimTimeoutSeconds = Math.max(5, aimTimeoutSeconds);
        maxShotDistance = Math.max(4.0, maxShotDistance);
        fireRetries = Math.max(0, Math.min(2, fireRetries));
    }
}
