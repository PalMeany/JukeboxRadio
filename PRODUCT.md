# Product

<!-- impeccable:product-schema 1 -->

> Все факты ниже взяты из исходного брифа пользователя. Пользователь явно делегировал дизайн
> («по дизайну реши сам», вопросы — только когда без них нельзя), поэтому интервью не проводилось.
> Предположения помечены *(inferred)*.

## Platform

native — Minecraft Java Edition 26.3 client (vanilla, no client mods for the menu). The menu is a
six-row chest drawn by the plugin's own resource pack (title glyphs + item models); search input is a
native Paper dialog. (GameUI was dropped: it does not run on the client's 26.3 server.)

## Stack

Paper plugin for Minecraft 26.3, Java 25. UI: vanilla chest menu + generated resource pack + Paper
Dialog API. Audio: Simple Voice Chat server API, yt-dlp + lavaplayer. Music metadata: Spotify Web API
(with embed/oEmbed fallbacks) behind a swappable catalog interface.

## Users

Players on a Minecraft server who walk up to a jukebox block and want music playing around it for
everyone nearby. They are mid-game, standing in the world, often with friends. *(inferred)*

## Product Purpose

Turns any vanilla jukebox into a shared radio: Shift + use on the jukebox opens a menu where a
player pastes a Spotify / YouTube link (track or playlist) or searches all Spotify tracks, builds a
queue and controls playback. Sound is positional through Simple Voice Chat, so people near the
jukebox hear it. Success: a player gets a song playing in under ten seconds.

## Positioning

A real streaming radio inside a vanilla client: no mods, no note blocks, real recordings, with a
turntable metaphor (discs swap on track change, the label takes the cover's colour).

## Operating Context

- Opened by Shift + right-click on a jukebox; a normal container screen with the mouse pointer.
- Players may be standing in a base, at night, in caves — lighting of the world behind the menu varies.
- Menus are short sessions: pick, queue, close. Playback continues after the menu closes.
- Several players can open the same jukebox; the queue is shared per jukebox. *(inferred)*

## Capabilities and Constraints

- Inputs: Spotify track / album / playlist links, YouTube video / playlist links, free-text search
  over the whole Spotify catalogue.
- The music-search provider will likely be replaced by another service's API later: the catalogue
  layer must stay provider-agnostic.
- UI primitives available: a 176×222 chest plus a 120-px side panel drawn as font glyphs in the
  container title (updated in place, no cursor jump), 54 slots holding item models (buttons, discs,
  cover tiles tinted per pixel through custom_model_data), vanilla bitmap fonts shifted to any line
  (Latin, Cyrillic, Greek, accented), no runtime textures — covers are built from tinted tiles.
- Disc-swap animation exists only inside the menu, not in the world.
- Text input is a native dialog (Paper Dialog API): paste works, "Найти" submits.

## Brand Commitments

- Required: animated vinyl disc switching in the menu; the disc's centre label colour follows the
  current track's cover; the full cover art is shown somewhere in the UI.
- UI copy language: Russian (the user writes in Russian). *(inferred)*

## Evidence on Hand

No existing assets, names or logos. Covers and track data come live from the catalogue; nothing may
be fabricated as real track data in screenshots — mock previews must be labelled as mock.

## Product Principles

1. Music first: the current track (disc, cover, title) is always the largest thing on screen.
2. Three clicks to sound: paste/search → pick → it plays.
3. Shared object, shared state: every player at a jukebox sees the same queue and disc.
4. Vanilla-honest: everything must read on a stock client at any GUI scale.
