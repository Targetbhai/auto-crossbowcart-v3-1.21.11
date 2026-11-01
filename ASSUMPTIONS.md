# Assumptions and mapping uncertainties (please verify; I could not compile this)

I had no access to the Fabric maven in my sandbox, so the code is **not compiled**. Fix any red names with `./gradlew genSources` + IDE autocomplete.

## Versions
- `loader_version=0.19.2` is what you asked for; I could not confirm it exists.
- `yarn_mappings=1.21.11+build.4`, `fabric_api_version=0.141.1+1.21.11`, `loom_version=1.14-SNAPSHOT` came from a third-party snippet page. Fabric docs mention newer Yarn builds (e.g. build.31). Take the latest from https://fabricmc.net/develop/.

## Mapping names to check
- `PlayerInventory.getSelectedSlot()` / `setSelectedSlot(int)` (private field since 1.21.5).
- `KeyBinding.Category.create(Identifier)` and the 4-arg `KeyBinding` constructor (confirmed by Fabric docs for 1.21.9+; lang key is `key.category.fpsdisplay.main`).
- `HudElementRegistry.addLast(Identifier, HudElement)` in `net.fabricmc.fabric.api.client.rendering.v1.hud`; `HudElement.render(DrawContext, RenderTickCounter)`. I used a lambda with `net.minecraft.client.render.RenderTickCounter`.
- `DrawContext.drawTextWithShadow(TextRenderer, Text, int, int, int)` with ARGB colors (alpha required).
- `ActionResult.Success#swingSource()` and `ActionResult.SwingSource.CLIENT`.
- `CrossbowItem.getPullTime(ItemStack, LivingEntity)` (Quick Charge aware). If missing, compute `25 - 5 * level` ticks from the enchantment instead.
- `ChargedProjectilesComponent#isEmpty()` and `DataComponentTypes.CHARGED_PROJECTILES`.
- `Direction.getFacing(double, double, double)`.
- `ItemTags.ARROWS`, `BlockTags.FIRE`, `LivingEntity#getItemUseTime()`, `World#getRegistryKey()`, `Box(BlockPos)` + `expand(double)`.
- TNT minecart is matched by `EntityType.TNT_MINECART` to avoid guessing its package.
- `ClientPlayConnectionEvents.DISCONNECT` handler signature `(handler, client)`.

## Behavior assumptions
- You must look at the **top face** of the target block, and aim at the **top face** of the gap block (so flint and steel places fire on top of it, next to the rail).
- Gap block = neighbour of the target block on the dominant horizontal axis toward you at press time.
- After the rail is placed, the same crosshair position hits the rail block (thin outline); if you move your aim off it, the mod waits (up to the aim timeout).
- "Retry fire once" is kept but only after a long pause and requires you to re-aim; it is disabled with `fireRetries: 0`. Other rejected actions abort immediately.
- Crossbow reload holds the use key via `KeyBinding#setPressed`; if you physically hold right-click, release it after cancel.
- Only plain `Items.RAIL` is accepted.
- This runs on anarchy servers where such tools are normal; on servers with rules against automation, using it may still get you punished. Timing variation reduces but does not remove that risk.
