# NITRO – v25: HUD editor, Anchor Macro rewrite, no hover sounds

- **HUD Editor** (ClickGUI sidebar, bottom): drag Target HUD, Spotify HUD, Keystrokes and Armor HUD anywhere. Snaps to the
  screen edges / centre (hold Alt to disable), arrow keys nudge (Shift = 5px), right click toggles a panel, Esc saves.
- **Anchor Macro** rewritten as a clean charge -> explode loop that reads the anchor's real charge level each step.
- Module hover sound removed (code, setting, sound asset).
- **Stun Slam** (Combat): falling onto a blocking player, your hit is made with a hotbar axe to break the shield, then
  a few ticks later the same target gets a mace hit, and your slot comes back. Needs an axe and a mace in the hotbar.
- Spotify HUD now has X / Y offset settings like the other HUD panels.

# NITRO – Original category-panel GUI restored

Fabric client mod for Minecraft 1.21.4. Contains the original NITRO ClickGUI layout with a separate panel for each category (Combat, Render, Visuals, Movement, World, and HUD), plus the current NITRO modules.
AutoBuild is **not** in this drop yet.

## Build
Push this folder to a GitHub repo. The workflow in `.github/workflows/build.yml` builds on every push; download
the jar from the run's **Artifacts** (`nitro-jar`). If the build fails, send me the red error lines from the log.
If `fabric_version` can't be resolved, set it to the newest `+1.21.4` Fabric API version in `gradle.properties`.

## Use
Right Shift opens the ClickGUI. The left sidebar jumps to a category; the centre shows separate category panels arranged in two scrollable columns; the header search filters modules. Click a module name to open its settings, or use its switch to enable / disable it. Themes and Settings are at the bottom of the sidebar. Settings are saved to `config/nitro.json` when the GUI closes and on game exit.

## Light Detector
Highlights loaded block light below Y=0 that is brighter than the sky light in the same cell. Denoise filter, colour by
light level (blue = dim, red = bright) or a custom colour, render radius 1-16 chunks, minimum light 1-15, opacity.
Scans a few chunks per tick nearest-first. Clears when disabled or when the world/dimension changes.

## Base Finder modes
Because I did not have your old Base Finder or the Amethyst Scanner source, the modes below are my own definitions:
- Classic: weighted block-clue score per chunk.
- ANBS+ Scan: clue + man-made-material score per 4x4x4 cell; chunk is flagged when enough cells pass (min-cell filter).
- Activity Clues: re-scans chunks and flags those whose clue/light signature changes between scans.
- Amethyst: Bypass: palette-only scan (does a section contain a clue block at all?), no per-block walk. Fast, coarse.
- Amethyst: Light: lit-above-sky samples below the surface, grouped into cells, min-cell filter.
- Amethyst: ANBS+ Scan: man-made blocks only, cell based.
Clue categories: containers, machinery, redstone, geodes, growth (toggle each in Filters). Natural geodes and generated
structures (mineshafts, villages, bastions) also match some clues, so expect false positives; use sensitivity and filters.

## Added in this drop
Categories: Render (Freecam, Storage ESP), HUD (Target HUD, Spotify HUD). All are toggleable in the ClickGUI, have a
Shortcut (keybind) row, and save their settings to `config/nitro.json`.

- **Freecam**: detaches the camera. WASD / Space / Shift fly, Sprint boosts, mouse looks. Your real player does not move
  or turn. Switches off on damage (setting), death, or world change. Uses two mixins (`nitro.mixins.json`).
- **Storage ESP**: outlines loaded chests, trapped chests, ender chests, barrels, shulker boxes (and optionally hoppers,
  dispensers/droppers, furnaces, brewing stands, chiseled bookshelves, decorated pots). Per-type toggles and colours, fill,
  tracers. Only block entities of chunks your client has loaded.
- **Target HUD**: name, head, health (+absorption), distance, armor and held items of the player under your crosshair, or
  the nearest player in range. Stays for a configurable hold time after you look away.
- **Spotify HUD**: current song, artist, album, artwork and a progress bar, read from the local Spotify app (no login).
  Windows: PowerShell + media session. macOS: AppleScript (allow the automation prompt). Linux: needs `playerctl`.

## Added in this drop (v2)
- **Fullbright** (Render): raises gamma past the normal limit. Original gamma is restored on disable, on leaving a world and on exit.
- **Motion Blur** (Render): frame-accumulation blur, "Blur amount" 10-90, optional frame-rate independence. Blurs the world and
  vanilla HUD; NITRO's own HUD panels are drawn after it and stay sharp. Keep it above the HUD modules in `ModuleManager`.
- **Player ESP** (Render): boxes/fill/tracers through walls, colour by custom / health / distance, optionally shows you in Freecam.
- **Storage ESP**: new "Spawners" filter and colour.
- **Spawner Alert** (World): scans loaded chunks (capped by your render distance) for mob spawners and plays a loud sound.
  Sound choice, loudness (identical sounds are layered, since one instance is capped at master volume), pitch, cooldown,
  optional repeat while in range, chat / action bar message, HUD indicator.
- **Target HUD**: redesigned. Glass / Neon / Flat styles, pop + fade animation, smooth health bar with damage trail,
  hurt flash on the head, win/lose tag, armor value, gear slots, scale and rainbow accent.
- **Spotify HUD**: redesigned. Accent colour taken from the album artwork, scrolling long titles, equalizer, progress bar with
  times, slide-in on track change, Compact mode, same three styles.
- **Song in the sky** (part of Spotify HUD): title, artist and a progress line drawn as big billboard text in the sky.
  Placement: fixed spot / slow orbit / drifts after your view. Height, distance, size, colours (artwork / rainbow / custom),
  and "hide behind terrain" are all settings.

## Added in this drop (v3)
- **Custom Sky** (Render): three skies: Violet Galaxy (aurora curtains, stars, ringed planet), Thunderstorm (dark clouds with lightning flashes) and Violet Marble (animated liquid swirl). Animation speed slider.

- **Choice settings** in the ClickGUI now list every option; click the one you want (works for the Custom Sky "Sky" picker and all other choice settings).

## Added in this drop (v4)
- **Clear Weather** (Render): hides rain, snow and thunder on your screen only (and the sky darkening that comes with them).
- **Keystrokes** (HUD): WASD, space and mouse buttons with CPS. Follows your key bindings.
- **Armor HUD** (HUD): armor pieces (optionally held items) with durability bars and a durability percentage; horizontal or vertical.
- Keystrokes and Armor HUD have a corner picker (Position), X/Y offsets and scale.

## Added in this drop (v5)
- **Floating lyrics** (Spotify HUD): time-synced lyrics of the current song hang in the air under the song title (or on their own
  if "Song in the sky" is off). The current line is big, animated with the artwork / rainbow / custom colours and gently bobs and sways;
  the previous and next lines show smaller and fainter. Settings: size, gap below title, previous/next lines, float amount and a timing
  offset (+/- 3 s) if the lyrics run early or late. Lyrics are fetched in the background from the public LRCLIB service (lrclib.net,
  no account), so the game needs internet access; songs without synced lyrics there simply show a short "no synced lyrics found" note.

## Custom Sky troubleshooting
- Setting **Draw stage**: "After terrain" (default, clouds/rain stay on top) or "End of frame" (fallback; may cover clouds and particles
  in empty sky but does not depend on the mid-frame render event). If the sky never appears, switch it to End of frame.
- If the early stage draws nothing, the module falls back to the end of the frame automatically.
- The module writes what it is doing to `logs/latest.log` with the prefix `[NITRO] Custom Sky:` (drawing / skipped and why).

## Added in this drop (v6, Combat for anarchy servers)
New **Combat** category in the ClickGUI. Intended for anarchy servers where hacked clients are allowed.
- **Auto Totem**: keeps a Totem of Undying in the offhand (swap delay, optional "only replace the offhand item when low on health",
  prefer-hotbar, warning when you run out).
- **Auto Crystal**: places crystals on obsidian/bedrock and breaks them. Picks the spot with the most estimated damage to nearby
  players, within place/break range, and refuses spots that exceed "Max self damage" or would kill you. Face-place below a target
  health, place/break delays, auto-switch to crystals in the hotbar (or uses an offhand crystal). Damage is an estimate (armor and
  Resistance are modelled, enchantments are not: use "Assumed enchant reduction").
- **Anchor Macro**: look at a respawn anchor and hold right click: swaps to glowstone, charges it, swaps to the explode slot and
  detonates it. Needs an offhand totem by default. Does nothing in the Nether (anchors do not explode there).
Not compiled in my environment: if the GitHub build shows red lines, send them to me.

## v7: new ClickGUI, Info HUD removed
Info HUD is gone. The ClickGUI was rebuilt: fade/slide-in, animated violet-cyan accent, category tabs with a sliding highlight and
enabled-count badges, search box (click it and type; Esc unfocuses), module cards (click = select, right click or the switch = toggle,
star = favourite), and a settings pane with a big enable button, sliders, option chips and RGB bars. Right Shift closes.

## Combat changes
- **Auto Crystal** now has a Mode: "Hold right click" (default) places a crystal on the obsidian/bedrock you look at and breaks the
  crystal you look at while right click is held, each at its own speed (1-20 per second). "Aura (automatic)" keeps the old behaviour.
- **Anchor Macro**: set "Totem slot" to the hotbar slot your totem is in (or enable automatic detection); after charging, it switches
  to that slot to ignite the anchor. "Speed" is the delay between steps.


## Block ESP
Outlines any blocks you pick through walls (Render tab). The "Blocks" setting shows every block that has an item as a
clickable icon grid: type in the search box (name or id), page with Prev/Next, click an icon to select it, and tick
"Selected only" to review your picks. Blocks without an icon (lava, water, fire...) can be typed into "Extra block ids".
Colour by block (automatic, one colour per block type) or one colour for all; range 1-16 chunks, max blocks drawn
(nearest first), fill/outline opacity, line width, tracers. Only loaded chunks are scanned.

## Seed Ore ESP
Predicts ore veins from the world seed (overworld, 1.21.4) and outlines them through walls, so anti-xray does not hide them.
It replays vanilla's ore generation: per-chunk population seed -> per-feature seed -> count / x,z / height -> the OreFeature
vein algorithm, using the terrain the client already has (stone vs. caves) to apply the "discard when touching air" rule.
Settings: world seed (default is the seed you gave), range, an icon grid where you click the ores you want (stone row and
deepslate row: coal, iron, gold, redstone, lapis, copper, diamond, emerald), a colour per ore, index offset, fill/outline/tracers. The HUD line shows how many *visible* (cave-exposed) ores
match the prediction: ~100% means the seed and indices are right; near 0% means wrong seed or a wrong feature index
(try Index offset -2..+2).
Limits: overworld only; predictions near player-mined or otherwise changed terrain can be off; the feature indices are
hard-coded from the vanilla feature order and not verified in-game; emerald/copper indices are the least certain.

## Themes
New **Themes** tab at the bottom of the left rail. Click a preset to apply it instantly: Nitro, Ocean, Sunset, Emerald,
Crimson, Sakura, Midnight, or Custom (two RGB accent colours). "Animated accent" turns the moving gradient on or off.
Saved in `config/nitro.json` under `theme`.

## GUI v22 (category panels restored)
The original NITRO panel-style ClickGUI is restored. Combat, Render, Visuals, Movement, World, and HUD each appear in their own panel, arranged in two independently stacked columns with one shared scroll area. The category rail jumps to a panel, search filters modules, clicking a module opens its settings, and the switch toggles it. The Violet theme remains the default.


## v16 updates
- **Hover Totem** now reacts to hovering a Totem of Undying inside the player's inventory screen. Its Destination setting chooses Offhand or a configurable Hotbar slot (1-9). It triggers once per hover so it does not repeatedly swap while the cursor stays on the same slot. The optional totem-count HUD remains available.
- **Custom Animation** (Render) adds Vanilla, Smooth, Snappy and Floaty first-person swing curves, plus an animation-speed slider. This is a local visual change only; it does not alter attack timing or server packets.
- **Custom Sky / Saturn**: enlarged the globe, added warmer latitude bands and layered ring lanes with a visible dark division, and adjusted the ring angle and proportions.
- Existing combat automation was not modified to hide from or bypass server anti-cheat systems. Check the rules of the server you play on before enabling automated gameplay modules.

Build remains available through the `.github/workflows/build.yml` workflow: push the project or run the workflow manually, then download the `nitro-jar` artifact from Actions.


## NITRO v0.1.10 – category panel GUI restored

This source update keeps **Custom Accessories / Custom Tools**, **Custom Glint**, and **Custom Crosshair** removed, including their classes, rendering hooks, and bundled galaxy tool resources.

The Right Shift ClickGUI is reverted to NITRO’s original category-panel layout. Each category is displayed in its own panel: Combat, Render, Visuals, Movement, World, and HUD. Panels are arranged in two independently stacked columns; the shared scroll area and category quick-jump rail make every section accessible. The earlier three-column Prestige-inspired board and its category-layout choice have been removed.

The Violet theme remains the default. Minecraft version: 1.21.4; Java 21; Fabric.

## Build

Push this project to GitHub and let `.github/workflows/build.yml` run, or use Gradle 8.12 with Java 21 and run `gradle build`. The built mod JAR is placed in `build/libs/`.


## v0.1.11 UI polish

- Added a fade, slide and gentle scale-in transition when opening the ClickGUI.
- Added a soft custom UI hover sound when the pointer enters a module row.
- Added **Settings → Module hover sound** and **Hover sound volume** controls. The volume control appears only while the sound is enabled.
- The hover effect is local to the client and does not send packets.


## v0.1.12 - Mace and crystal PvP combat modules
Nine new Combat modules. Swap modules share one attack hook (a mixin on `attackEntity`): the weapon is selected right
before the attack packet and your slot is restored right after, so the server sees weapon + attack in the same tick.
Swap priority when several are on: Shield Breaker, then Mace Swap, then Breach Swap.
- **Mace Swap**: hits with a mace from the hotbar while you fall (min fall distance setting, or always), then switches
  back. Uses Breach Swap's logic to pick the mace when that module is on.
- **Breach Swap**: holding a mace, an armored target is hit with your Breach mace, an unarmored one with your Density mace
  (any mace if the preferred one is missing).
- **Shield Breaker**: hits a blocking target with an axe from the hotbar, then switches back.
- **Auto Wind Charge**: give it a key. Press: silent straight-down wind charge throw (your camera does not move),
  switch to mace, and on the way down attack the nearest player once fall distance >= "Min smash height" and they are
  in reach. Switches itself off when the combo ends.
- **Smash Aim**: while falling (mace in hand), snaps or smoothly turns onto the nearest player below you. Turns are
  quantized to your mouse sensitivity.
- **Fall Reset**: after a smash hit, wind charge mode throws a charge at your feet just before the ground; water bucket
  mode places water just before landing and scoops it up after.
- **Elytra Swap**: give it a key. Press: elytra on (chestplate goes to the elytra's slot). Press while wearing the
  elytra: chestplate back on. "Re-equip chestplate on landing" swaps back by itself.
- **Auto Hit Crystal**: Right click mode breaks the crystal your own right click placed; Left click mode places it for
  you when you left click the obsidian/bedrock, then breaks it. Delay setting (+ optional humanizing).
- **Auto Inv Totem**: refills offhand and a hotbar slot from inventory totems; "Only after a pop" waits for a totem to
  be consumed; optional "inventory open only".
Keybind modules (Auto Wind Charge, Elytra Swap) act when you press their key and then switch themselves off.
Not compiled in my environment: if the GitHub build shows red lines, send them to me.
