package com.fpsdisplay.client;

import com.fpsdisplay.config.ModConfig;
import com.fpsdisplay.hud.HudRenderer;
import com.fpsdisplay.state.SequenceController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class FpsDisplayClient implements ClientModInitializer {
    public static final String MOD_ID = "fpsdisplay";

    private static KeyBinding startKey;
    private static KeyBinding cancelKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // Since 1.21.9 key bindings take a KeyBinding.Category object, not a translation string.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fpsdisplay.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        cancelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fpsdisplay.cancel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SequenceController c = SequenceController.get();
            // Cancel first so it always wins over a same-tick start press.
            boolean cancel = false;
            while (cancelKey.wasPressed()) cancel = true;
            if (cancel) c.cancel(client);

            while (startKey.wasPressed()) {
                if (!cancel) c.onStartKey(client);
            }
            c.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SequenceController.get().onDisconnect(client));

        HudRenderer.register();
    }
}
