# FpsDisplay

Client-only Fabric mod (Minecraft 1.21.11, Yarn, Java 21). One key press runs a semi-automatic TNT-minecart trap. **You always aim; the mod never rotates the camera** and only polls your crosshair.

## Build
1. Install JDK 21 and Gradle 9.2+ (or run `gradle wrapper --gradle-version 9.2.1` once to create a wrapper).
2. `gradle build` (or `./gradlew build`)
3. The jar is `build/libs/fpsdisplay-1.0.0.jar`. Put it in `mods/` with Fabric API.

GitHub: push the project; `.github/workflows/build.yml` builds and uploads the jar as an artifact.

## Keybinds (Options > Controls > FpsDisplay)
- **V** - start sequence (runs the pre-check first)
- **Backspace** - cancel/reset instantly (releases use key, restores hotbar slot)

## Pre-check (nothing happens if any item fails; the HUD lists what is missing)
- Crosshair on the **top face** of a solid block with air above (rail fits) and a solid gap block in front of it, on the side facing you (stand >= 1.5 blocks away)
- Rail, TNT minecart, flint and steel in the hotbar
- A crossbow in the hotbar that is **already loaded** (the mod never loads it before the shot)
- At least one arrow anywhere in the inventory (for the final reload)

## Sequence
1. **Place** a rail on the block, then (crosshair still on that spot, which now hits the rail) the TNT minecart.
2. **Aim at the top of the gap block** (HUD prompts). After the crosshair stays there `aimHoldTicks`, flint and steel is selected and used; the fire is verified.
3. **Look at the cart.** Within `angleToleranceDegrees` (or raycast hits it) for `aimHoldTicks`: the loaded crossbow is selected and fired. If aim drifts, it keeps waiting.
4. **Reload** by holding use for the full Quick Charge-aware time, restore the original slot, return to idle.

Any missing/invalid item, cart, fire, rejected action (rail/cart/fire/shot not confirmed by the server), death, dimension change, disconnect or aim timeout aborts, restores the slot and shows the reason.

## Config: `config/fpsdisplay.json`
`aimHoldTicks`, `angleToleranceDegrees`, `delayMinTicks`/`delayMaxTicks` (each delay drawn from a bell-shaped distribution, plus per-run speed variation and rare longer pauses), `aimTimeoutSeconds`, `maxShotDistance`, `fireRetries`, `hudEnabled`, `cautiousMode` (delays x1.5, default on), `startKeyCancelsWhenRunning`.

## Design notes
- All actions go through `ClientPlayerInteractionManager` (`interactBlock`, `interactItem`); no custom packets, no rotation changes.
- Block uses rely on the vanilla crosshair raycast, so reach and line of sight are the vanilla ones.
- A slot switch and the following use are always separate states with a randomized wait between them.
- The brief's two timing requirements (fixed delays plus jitter vs. distribution-based delays) are merged: there is no separate jitter setting.
