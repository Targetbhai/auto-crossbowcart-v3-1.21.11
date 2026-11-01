package com.fpsdisplay.hud;

import com.fpsdisplay.config.ModConfig;
import com.fpsdisplay.state.SequenceController;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Small status text under the crosshair. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.of("fpsdisplay", "status"), (DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) -> {
            if (!ModConfig.get().hudEnabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            SequenceController c = SequenceController.get();
            String[] lines = c.hudLines();
            int y = ctx.getScaledWindowHeight() / 2 + 24;
            int color = c.currentMessageIsError() && !c.isRunning() ? 0xFFFF5555 : 0xFFFFFFFF;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                Text t = Text.literal(lines[i]);
                int x = (ctx.getScaledWindowWidth() - mc.textRenderer.getWidth(t)) / 2;
                ctx.drawTextWithShadow(mc.textRenderer, t, x, y, i == 0 ? color : 0xFFFFFF55);
                y += 11;
            }
        });
    }
}
