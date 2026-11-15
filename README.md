# Pixel Bead

A Minecraft mod (Kotlin) that adds **291 MARD solid-color bead blocks**, a **dye workbench**
that batch-converts plain blocks into beads, and a server-authoritative **bead building mode**
with a virtual 18-slot color hotbar and a full color picker.

- **Minecraft** 26.3 · **NeoForge** 26.3.0.99 · **Kotlin** 2.4.21 (via Kotlin for Forge 6.5.0)
- **ModDevGradle** 2.0.148 · **Gradle** 9.9.1 · **Java** 25
- modId `pixelbead`, package `com.pixelbead`

See `minecraft-bead-mod-design.md` for the full design and `color-data/NOTICE.md` for data attribution.

## Build & run

Requires network access to `maven.neoforged.net` (use a proxy/VPN if it is blocked). The Java 25
toolchain is auto-provisioned by the foojay resolver.

```bash
./gradlew build        # generate resources + compile + test + jar
./gradlew runClient    # launch a dev client
./gradlew runServer    # launch a dev dedicated server
```

> If the JVM does not pick up the system proxy, pass it to Gradle explicitly (no build-file
> changes needed):
> `GRADLE_OPTS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890" ./gradlew build`
> With that proxy the standard repository config resolves everything, including
> `neoform-runtime`, and ModDevGradle decompiles the 26.3 sources locally.

> The dev runs pass `-Dneoforge.warnings.logofile.hide=true` (see `build.gradle.kts`): KFF 6.5.0
> still declares the deprecated `logoFile` key, which makes NeoForge 26.3 show a dev-only
> "Warning while loading mods" screen. This suppresses just that one warning; real loading
> issues are still reported, and production builds never show it.

## What is implemented

- **Palette** (`palette/`): build-time CSV→JSON pipeline (`generateBeadResources` in `build.gradle.kts`)
  with hard validation (291 colors, exact per-series counts). Single source of truth read at runtime.
  Data-driven dye→series map (`data/pixelbead/pixelbead/dye_series_map.json`, datapack-overridable).
- **Blocks/items** (`blocks/`): 291 bead blocks + BlockItems, uniform properties, one shared white
  texture + cube model, color via `BlockTintSource`/`ItemTintSource`. Creative tab in series/code order.
- **Workbench** (`workbench/`): block + transient menu + screen, 16 blocks + 1 dye → 16 beads,
  input filtering (containers/BlockEntity/blacklist/whitelist), consume-on-take with re-validation.
- **Bead mode** (`beadmode/`, `inventory/`, `network/`): synced+serialized player attachment (no
  copyOnDeath), precise flight grant/restore, full interaction interception, placement through the
  vanilla BlockItem pipeline (claim-compatible, no client coordinates), survival consumption, cleanup
  on death/teleport/dimension/logout/gamemode/config. Admin command `/pixelbead enable|disable|status`.
- **Client UI** (`client/`): rebindable keys, an 18-slot (2 rows × 9) virtual hotbar decoupled
  from the real inventory, HUD layer, full color picker (series nav / search / favorites /
  recents / assign) with a bottom-flush mirrored hotbar, workbench screen.
- **Config** (`config/`): server + client `ModConfigSpec`.
- **Tests**: `PaletteTest`, `WorkbenchLogicTest` (pure logic) and `ResourceSchemaTest` (shipped
  datapack/asset JSON schema regression).

## Status / verification

The whole codebase now **compiles, tests and packages** against the real Minecraft 26.3 /
NeoForge 26.3.0.99 artifacts. `./gradlew build` succeeds with the configuration cache enabled
(`generateBeadResources` → 291 colors / 15 series, `compileKotlin`, 20 passing tests, `jar`). The first real
build decompiled 7301 Minecraft sources locally
(`build/moddev/artifacts/minecraft-patched-26.3.0.99-sources.jar`), so every `NOTE: verify against
26.3 source` marker was checked against actual 26.3 code and removed. Corrections made:

- `ModConfig.Type.SERVER` no longer exists → `SYNCED`; commands use the new
  `PermissionSet` / `Permissions.COMMANDS_GAMEMASTER` model instead of `hasPermission(2)`.
- `TriState` → `net.minecraft.util.TriState`; `ClientPlayNetworking` →
  `ClientPacketDistributor.sendToServer`; clientbound handlers moved to
  `RegisterClientPayloadHandlersEvent` so a dedicated server never loads client classes.
- `DeferredRegister.createBlocks/createItems` (constructors are now protected); explicit
  `Supplier<Properties>` for `registerBlock`; `RecordCodecBuilder.mapCodec` for attachments.
- RenderPearl: `GuiGraphicsExtractor.text/centeredText`, `mouseClicked(MouseButtonEvent, boolean)`,
  `VanillaGuiLayers.HOTBAR` confirmed; `Minecraft.gui.screen()/setScreen()`.
- SDL input: `InputConstants.Type.KEYBOARD`, `InputEvent.Key.getKey()` + `PRESS`,
  `Inventory.get/setSelectedSlot`; `KeyMapping.Category` is now a record.
- `SimpleContainer.addListener` removed → override `setChanged()`; `placeItemBackInInventory`
  needs `Prediction.SERVER_ONLY`; `SimpleContainer` lives in `net.minecraft.world`.
- Placement now goes through `ItemStack.useOn` (not `BlockItem.useOn`) — the only path firing
  `BlockEvent.EntityPlaceEvent` for claim mods; a reentrancy guard stops the blanket
  `UseItemOnBlockEvent` gate from vetoing our own placement.
- `ItemTintSource.type()` must return the exact registered `MapCodec` instance; the generated
  item-model `tints[].type` dispatch schema was checked against vanilla 26.3 assets.
- Datapack reload ids need the `.json` extension; `generateBeadResources` is now a standalone
  `Action<Task>` so Gradle 9's configuration cache can serialize it.
- `AddServerReloadListenersEvent` is a **game bus** event (`AddClientReloadListenersEvent` is mod
  bus); registering it on the mod bus crashes a dedicated server with "not valid for this bus".
- 26.3 datapack schemas differ from 1.21.x: shaped-recipe ingredients are plain strings
  (`"P": "#minecraft:planks"`, not `{"tag": ...}`), and `LootPool` uses a singular
  `"condition": { "type": "minecraft:survives_explosion" }` object, not a `"conditions"` array.
  Both the static workbench files and the 291 generated loot tables were corrected against
  vanilla 26.3 data; `ResourceSchemaTest` locks this in.

A **dedicated-server smoke test** (`./gradlew runServer`, with `eula=true` in `run/eula.txt`) also
passes: the mod list contains `Pixel Bead 0.1.0`, startup logs `Pixel Bead: loaded 291 MARD
colors`, the world is generated and the server reaches `Done (1.859s)! For help, type "help"`.
`run/logs/latest.log` has zero `ERROR`/`FATAL`/datapack parse failures; the remaining warnings are
only vanilla command ambiguities and the offline-mode notice.
(`runServer` is a long-lived task — terminating it manually makes Gradle report a non-zero exit,
which is not a mod failure.)

A **client bootstrap smoke test** (`./gradlew runClient`) also passes: the dev client (user `Dev`,
OpenGL/LWJGL 3.4.3, SDL video) loads `Pixel Bead 0.1.0`, logs `Pixel Bead: loaded 291 MARD
colors`, completes the first resource reload with `mod/pixelbead` in the pack list, builds every
texture atlas and reaches the menu with zero `ERROR`/`FATAL` and no new crash report. This
exercises the client-only registration chain (tint sources, key mappings, GUI layers, menu
screens, clientbound payload handlers). Logs are kept at `build/server-smoke-latest.log` and
`build/client-smoke-latest.log` (both are git-ignored). A fresh run after the warning fix has
**zero loading warnings** — no `logoFile` line, no `Mods loaded with N warning(s)` — and goes
straight to the main menu.

Remaining work is **visual / interactive in-game verification** (a headless smoke test cannot
cover it): colour and tint consistency in a world, HUD and picker layout, key rebinding, workbench
GUI, and the full interaction / death / dimension / logout cleanup matrix.

**In-game bug found & fixed (2026-11-15):** entering bead mode made *both* mouse buttons inert.
`BeadKeyHandler` cancelled `InteractionKeyMappingTriggered` for attack/use, and 26.3's
`Minecraft.startAttack()` / `startUseItem()` return immediately when that event is cancelled
(`continueAttack()` short-circuits too) — so no `START_DESTROY_BLOCK` and no
`ServerboundUseItemOnPacket` ever left the client, and the server-side `RightClickBlock` /
`BreakBlockEvent` chain (hence `BeadPlacement.place()`) never ran. Only middle-click is blocked
now; all filtering is server-side. Placement rejections now show a concrete HUD reason instead of
failing silently. **Restart `runClient` to pick up the rebuilt classes.** One behaviour is
intentional per design §6.5: in survival a right-click without the selected bead in inventory is
refused with an "insufficient" toast — test in creative mode first.

**Breaking is unrestricted (2026-11-15, setting change):** bead mode no longer blocks mining
non-bead blocks. `LeftClickBlock`, `BreakBlockEvent` and `BreakSpeed` interceptors were removed
from `BeadModeEvents`, so left-click mines beads and ordinary blocks alike at vanilla speed and
with vanilla drops. Only *placement* (always a bead) and *use* (chests, doors, buckets, food, …)
are still restricted.

**HUD / picker UX (2026-11-15):** the virtual hotbar is now **18 slots in two rows** — `1-9`
for the first row, `Shift+1-9` for the second, scroll cycles all 18. Both the in-game HUD and
the picker are drawn by one shared renderer (`BeadHotbarView`), and the picker shows a mirrored
copy of the hotbar. Old 9-slot saves are normalised on read. The selected-slot highlight is a
two-tone **outline** instead of an opaque white rectangle, so the underlying color stays visible;
the dark ring fills the 1px channel and the colored ring is drawn *inside* the swatch edge, which
keeps neighbours untouched.

**Bead-mode visuals & picker flow (2026-11-15):** while bead mode is active the whole vanilla
bottom HUD is hidden via `RenderGuiLayerEvent.Pre` — item bar, held-item name, health/armor/
food/air/vehicle bars and the experience bar/level (crosshair, effects, chat and boss bar stay).
With that space free the mod HUD sits **2px from the window bottom** (`BOTTOM_GAP`), and the
swatches are separated by a single 1px channel (`GAP`) so the bar reads as one tight color strip
instead of a loose grid. The picker's mirrored bar uses the exact same placement rule, so it is
equally flush with the bottom edge. The first-person hand
shows the selected bead: `BeadFirstPersonView` overrides the 26.3 first-person render state
(never the real inventory), so the virtual hotbar stays decoupled and a dedicated server sees
no change.

The picker flow is **slot first, color second**: click a slot to target it (gold cursor), then
click colors to write them — each click fills that slot and advances the cursor to the next one
(wrapping at 18), backed by a local optimistic hotbar copy so consecutive clicks feel instant.

**HUD docking (2026-11-15):** the bar can be docked to the **bottom** (9 × 2, the default) or to
either **side** (a single column of 18, flush with that edge and vertically centered; if the window
is too short for 305px it falls back to two columns of 9). The setting is
`ClientConfig.hudPosition` (`hud.position` in `pixelbead-client.toml`) and is edited on a
vanilla-styled settings screen (`PixelBeadOptionsScreen`: `OptionsSubScreen` + `OptionsList` +
`CycleButton`), reachable from the **Settings** button in the picker's top-right corner (same row and
same height as the search box, `SEARCH_Y`/`SEARCH_H`/`SEARCH_W` are shared constants so the two can
never drift apart) and from **Mods → Pixel Bead → Config**. The picker's own mirrored bar always
stays at the bottom: it is the editing surface.
Swatches come in three sizes — **small** (12px), **medium** (16px, the default) and **large**
(20px) — set by `ClientConfig.hudSwatchSize` (`hud.swatch_size`). The 1px channel between swatches
is constant, so every step still reads as one tight color strip, and the picker's mirrored bar
uses the same size as the HUD.
While the picker or the settings screen is open the in-game HUD is hidden and the two surfaces are
fully independent: there is no shared preview state or HUD/picker linkage to keep in sync.

**First join (2026-11-15):** the first time a player joins a world, `WelcomeMessage` posts two chat
lines — a greeting and a hint that renders the *actual* bound keys through `Component.keybind`
(`key.pixelbead.toggle_mode` / `key.pixelbead.open_picker`), so rebinding never makes the text lie.
The "already greeted" flag is its own attachment (`BeadModeAttachments.WELCOMED`: a `Boolean` with
`copyOnDeath`, never synced) rather than a field of `BeadModeState`, because that state is
deliberately not copied on death — otherwise dying and relogging would replay the greeting.
It is skipped (and not consumed) when the server config disables the feature.

## Bead tools (2026-11-15)

The efficiency layer on top of bead mode. Everything here is server-authoritative, sits behind
`tool.enabled`, and only ever moves **bead** blocks in bulk:

| Gesture | What happens |
|---|---|
| Hold RMB and sweep | **Stroke**: the cells the cursor skipped are filled with a Bresenham line, so dragging fast no longer leaves gaps. Bridged only within one face plane and `tool.max_stroke_gap` blocks; a pause (500 ms) or a face change breaks the stroke |
| Plain LMB on a bead block | **Eraser**: that bead disappears instantly, and the sweep is interpolated exactly like the brush |
| `~` + RMB | **Two-click fill**: the first click stores an anchor, the second fills the axis-aligned rectangle between the corners on that plane — hold sneak for a single line instead. Capped at `tool.max_area` (512) |
| `~` + LMB | **Area break**: clears a `(2 * tool.break_radius + 1)²` square of bead blocks in the face plane |
| `Z` | **Undo** the last stroke / fill / break (`tool.undo_steps`, default 8) |

Why it is built like this:

- **No coordinate payloads.** The anchor and the fill are two ordinary right clicks, so the server
  derives both corners from events it already receives. Every filled cell runs through
  `BeadPlacement.placeAt` → `ItemStack.useOn` → `EntityPlaceEvent`, so claim mods still veto cell by
  cell. The only new client→server payload is a boolean for the tool key (the same trick VeinMiner
  uses), plus a unit payload for undo.
- **Bulk removal only touches bead blocks** — a stray tool click can never eat a build. Every break
  goes through `ServerPlayerGameMode.destroyBlock`, so `BreakEvent` still fires and drops still work.
- **Undo moves material both ways**: undoing a fill hands the beads back, undoing a break charges
  them in again (and is refused while the drop is still on the ground). `break → undo → break`
  therefore cannot duplicate beads.
- **Nothing is persisted.** Tool/key/anchor/stroke/undo state lives in `ToolSessions`, a per-player
  map that is dropped on logout, death, teleport and dimension change, so a stale "tool key held"
  flag can never survive a crash.

## Color data license

MARD 291 color data © 2026 MakeBead (https://makebead.com), **MIT** — vendored from
`makebead/craft-color-codes` (`data/csv/mard-291.csv`). HEX/RGB are screen reference values, not
manufacturer measurements. See `color-data/LICENSE` and `color-data/NOTICE.md`.