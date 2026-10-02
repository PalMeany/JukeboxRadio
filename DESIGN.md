---
name: JukeboxRadio menu
platform: minecraft-java-26.3 (vanilla chest screen + generated resource pack)
colors:
  panel: "#C6C6C6"
  light: "#FFFFFF"
  shade: "#555555"
  outline: "#000000"
  slot: "#8B8B8B"
  slot-edge: "#373737"
  row: "#2B2B2B"
  row-edge-dark: "#151515"
  row-edge-light: "#4A4A4A"
  track: "#2E2E2E"
  text: "#404040"
  text-strong: "#1E1E1E"
  text-muted: "#4C4C4C"
  text-error: "#AA0000"
  row-text: "#FFFFFF"
  row-text-muted: "#A8A8A8"
  button-face: "#6E6E6E"
  button-top: "#9A9A9A"
  button-bottom: "#4E4E4E"
  button-off-face: "#3A3A3A"
  button-on-face: "#7D7D7D"
  danger-face: "#7A1E1E"
  zoom-plate: "#1B1B1B"
  neutral-label: "#E8E4DA"
  accent: "per track — CoverArt.labelColor(cover)"
typography:
  face: vanilla Minecraft bitmap font (ascii, accented, nonlatin_european), 8-px lines, 1:1 GUI pixels
  line-pitch: 10
  list-row-lines: [0, 8]
spacing:
  module: 18   # slot pitch
  canvas: "176×222 chest + 120×130 side panel = 296 wide"
---

# JukeboxRadio — design system

## World

A vanilla container, not an app skin. The radio *is* the jukebox: a turntable you look down on, the
cover hung in an oak item frame like a map, the queue as a hotbar of discs. Grey container panel,
2-px white/grey bevels, black 1-px rounded outline, #8B8B8B slots, stone buttons. Everything is
achromatic **except one colour taken from the current cover**: the disc label, the progress fill and
the up-next disc labels (each in its own track's colour). The only other chroma: vanilla red for
errors and the two-second "Точно?" confirmation, and the vanilla tooltip purple.

## Surface and mechanics

- One six-row chest (54 slots) plus a 120-px panel beside its rows. The whole L-shape is one
  background drawn as two font glyphs (`BG_*_L` 176 px, `BG_*_R` 120 px) in the container title.
- Static content (backgrounds, text, progress bar, the 56×56 full cover) lives in the **title**:
  fonts `jukeboxradio:t{Y}` re-anchor the vanilla bitmap fonts so a line's glyph tops sit at
  container y = Y; pixel glyphs `PIXEL_BASE + y` place a 2×2 dot at any row; the pen is moved with
  ±2ⁿ space glyphs. Glyphs are emitted white (vanilla tints titles #404040). The title is updated in
  place (open-screen packet for the same window), so the cursor never jumps.
- Interactive and animated content lives in **slots** as paper items with pack models:
  buttons (16×16 stone), the 64-px record (`oversized_in_gui`, label tinted by
  `custom_model_data` colour 0), the tonearm, up-next mini discs, cover tiles (8×8 cells, 64 tints
  each, scale 1.125 so 4×4 tiles make a seamless 72×72 picture).
- Items draw in slot order; anything that must lie on top of the record uses a slot after 10.

## Screens

| Screen | Chest | Side panel |
|---|---|---|
| Играет | turntable (slots 0-3 × rows 0-3, disc in 10, arm in 30), tab column (4, 13, 22, 31), cover 4×4 (cols 5-8, rows 0-3), up-next hotbar (row 4), transport (row 5: prev, play, next, stop, repeat, shuffle, −, volume, +) | status · source, title (≤3 lines, #1E1E1E), artist, album, requester, progress bar (52 dots at y 90), times, "Далее" and queue summary |
| Поиск / Очередь | 5 dark rows: action button, 8×8 cover tile, hit items with tooltips over the text; row 5: pager, action A/B, to player, close | row text continues across the panel; header and page counter in the title line |
| Обложка | 56×56 cover as 2-px pixel glyphs in an oak frame on a dark plate | title and artist in white; any click returns |

## Motion

- Spin: 16 pre-rotated frames (pixel grid kept crisp), `frametime = spin-ticks / 16` (default 4).
- Track change: 4 eject frames (rise 22 px, fade 80 → 20 %), then 6 fly frames from up-next slot 1
  to the platter (scale 1 → 4, ease-out, slight arc, spin −150° → 0), the hotbar slides one slot left
  over 3 frames, the cover develops row by row (4 rows over 4 ticks).
- Tonearm: 7 pre-drawn poses (−12° rest … 27° near the label); swings one pose per 2 ticks and
  follows the track position across the four playing poses.

## Rules

1. **Cover owns colour.** Never add a chromatic accent that is not the cover's.
2. **Fit before send.** Every string goes through `Glyphs.fitPx`/`wrapPx`; unsupported characters
   become `?` (only the vanilla bitmap fonts can be shifted).
3. **Vanilla grammar.** New parts use the panel/slot/button textures above; no rounded cards, no
   gradients, no drop shadows beyond the vanilla bevel.
4. **Tooltips carry detail.** Buttons name their action; list hit items show title, artist, album,
   duration and the click legend.
5. **Destructive actions confirm.** Clearing the shared queue needs a second click within 3 s.

## Files

- Art: `menu/pack/PackArt.java` · pack assembly: `menu/pack/PackBuilder.java` · model ids:
  `menu/pack/Models.java` · geometry: `menu/MenuLayout.java` · palette: `menu/Tone.java`
- Real-client screenshots: `docs/screenshots/`.
