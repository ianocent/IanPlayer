# IanPlayer × Convx — Product Requirements Document

**Status:** Draft / Implementation Blueprint  
**Date:** 2026-09-06  
**Primary product:** IanPlayer Android music player  
**Reference:** Convx (`cosmictaserdev-creator/Convx`)  
**Canonical local baseline:** `ianplayer.zip` supplied for this task  
**Secondary baseline:** `ianocent/IanPlayer` GitHub repository

---

## 1. Product Vision

IanPlayer should evolve from a feature-rich music player into a **highly customizable, fluid, immersive music experience**.

The target experience is:

- fast enough to feel native even on large libraries;
- visually alive without becoming distracting;
- smooth during navigation, player expansion, lyrics expansion, queue changes, and track changes;
- deeply customizable;
- capable of switching between multiple player/layout personalities;
- capable of using animated Canvas/background artwork without making playback unstable;
- still recognizably IanPlayer rather than becoming a Convx clone.

### Core product principle

> **One playback engine, one data model, multiple visual experiences.**

The playback/data layer must remain independent from player presentation. A user changing from `Base IanPlayer` to `Spotify-inspired`, `YouTube Music-inspired`, or `Apple Music-inspired` should not change the queue, current song, lyrics, favorites, downloads, playback state, or streaming source.

---

# 2. Goals

## P0 — Must Have

1. Introduce a player-layout abstraction/registry.
2. Support four selectable full-player presets:
   - Base IanPlayer
   - YouTube Music-inspired
   - Spotify-inspired
   - Apple Music-inspired
3. Preserve a single playback/data engine behind all layouts.
4. Improve mini-player → full-player morphing.
5. Improve track-to-track visual transitions.
6. Expand Canvas from the current local/generated/video model into a provider-oriented system.
7. Add Canvas caching and prefetching.
8. Make Canvas failure non-blocking: playback must continue if Canvas fails.
9. Introduce spring-based motion primitives and consistent motion tokens.
10. Keep settings persistent.
11. Avoid unnecessary recomposition/redraws.

## P1 — High Value

1. Custom player section ordering.
2. Hide/show player sections.
3. Per-layout player preferences.
4. Wavy/squiggly progress slider.
5. Better artwork morphing.
6. Dynamic artwork-based color transitions.
7. Improved lyrics presentation with karaoke-style highlighting.
8. Queue/up-next sheet with smoother drag/reorder behavior.
9. Player customization preview.
10. Ambient mode / visualizer mode.
11. Canvas source priority:
   `Local -> Apple-style provider -> Tidal-style provider -> Vivi/other provider -> Generative fallback`.

## P2 — Nice to Have

1. Shareable player presets.
2. Export/import appearance presets.
3. User-created player configurations.
4. Sticker/decorative layer system.
5. Auto-DJ transitions.
6. Listen Together.
7. Discord Rich Presence.
8. Advanced equalizer/audio processing.
9. Tablet-specific layouts.
10. Android Auto-specific player presentation.

---

# 3. Non-Goals

The first implementation should NOT:

- replace IanPlayer's existing playback architecture unnecessarily;
- rewrite all of `MainActivity.kt` in one pass;
- directly copy Convx source files wholesale;
- make the UI dependent on online Canvas providers;
- make animated effects run continuously when the user is not playing music;
- introduce expensive blur/refraction everywhere by default;
- make every screen use glass effects;
- sacrifice accessibility for visual effects.

---

# 4. Current IanPlayer Baseline

The supplied project already contains a strong base.

### Existing playback

- Media3 ExoPlayer
- MediaSession/foreground playback
- queue and Up-Next
- shuffle/repeat
- crossfade
- stream resolution
- stream caching
- local MediaStore scanning
- YouTube Music streaming
- downloads
- persisted playback state

### Existing player

`ui/NowPlayingScreen.kt` already contains:

- album art;
- hero transition;
- animated ambient colors;
- gradient background;
- lyrics;
- Up-Next;
- drag-to-dismiss;
- favorite;
- Canvas mode switch;
- Wave Record;
- play/previous/next/shuffle/repeat;
- lyric card generation.

### Existing Canvas

`ui/canvas/PlayerCanvas.kt` currently supports:

- Generative Aurora
- Video
- Ken Burns
- Off

`data/SettingsStore.kt` persists Canvas mode.

### Existing mini-player

`MainActivity.kt` currently supports:

- Default
- Floating
- Compact

and swipe-driven mini-player layout changes.

### Existing settings

`SettingsStore.kt` already provides a natural persistence boundary for:

- dark mode;
- mini layout;
- Canvas mode;
- tab order;
- voice assistant;
- social signals;
- user profile;
- pill position.

This means the project is well positioned for a broader appearance/player preference architecture.

---

# 5. Proposed Player Architecture

## 5.1 Player Presentation Layer

Introduce a presentation abstraction:

```text
PlayerLayout
 ├── BaseIanPlayerLayout
 ├── YoutubeMusicLayout
 ├── SpotifyInspiredLayout
 └── AppleMusicInspiredLayout
```

All layouts consume the same:

```text
PlayerUiState
 ├── currentSong
 ├── albumArt
 ├── ambientColor
 ├── paletteColors
 ├── isPlaying
 ├── position
 ├── duration
 ├── queue
 ├── lyrics
 ├── favorite
 ├── shuffle
 ├── repeat
 ├── buffering
 └── canvasState
```

No layout may directly own playback state.

---

# 6. Player Presets

## 6.1 Base IanPlayer

Purpose: preserve the current IanPlayer identity.

Characteristics:

- current IanPlayer composition;
- ambient artwork colors;
- prominent artwork;
- lyric + Up-Next split;
- Wave Record;
- Canvas controls;
- existing gestures;
- current mini-player language.

This is the migration-safe default.

---

## 6.2 YouTube Music-inspired

Do not copy proprietary assets. Recreate the interaction philosophy:

- artwork-forward player;
- large title/artist;
- clean bottom control area;
- strong queue/up-next relationship;
- prominent lyrics entry;
- contextual actions;
- adaptive artwork color;
- rounded surfaces;
- simple navigation;
- minimal visual noise.

Suggested structure:

```text
Top bar
  Back
  Device/output
  More

Large artwork / Canvas

Song title
Artist

Progress
Elapsed        Remaining

Like       Previous       Play       Next       Repeat

Lyrics / Related / Queue
```

---

## 6.3 Spotify-inspired

Interaction philosophy:

- strong album artwork;
- compact but information-dense metadata;
- prominent progress control;
- large play/pause;
- secondary controls below;
- queue access;
- lyrics as a distinct experience;
- playlist/album context.

Suggested structure:

```text
Top context
Album / Playlist

Artwork

Song
Artist

Progress

Like
Previous
Play
Next
Queue

Lyrics
Up Next
```

---

## 6.4 Apple Music-inspired

Interaction philosophy:

- artwork as the visual hero;
- elegant spacing;
- large typography;
- soft adaptive background;
- subtle blur/glass;
- smooth sheet-like transitions;
- strong lyrics experience;
- visually quiet controls.

Suggested structure:

```text
Top
  Back
  Context
  More

Artwork / Canvas

Title
Artist

Large progress control

Playback controls

Volume / AirPlay-like output surface

Lyrics
Queue
```

---

# 7. Layout Switching Requirements

Settings should expose:

```text
Settings
 └── Player
      ├── Player Layout
      │    ├── Base IanPlayer
      │    ├── YouTube Music
      │    ├── Spotify
      │    └── Apple Music
      │
      ├── Mini Player
      ├── Canvas
      ├── Player Sections
      └── Motion
```

Changing layout:

- must take effect without restarting playback;
- must not reset queue;
- must not reset position;
- must not reset lyrics;
- must not reload the stream;
- should animate between compatible layouts where possible.

---

# 8. Custom Player Sections

Inspired by Convx's slot registry.

Proposed sections:

```text
ALBUM_ART
CANVAS
TRACK_INFO
SEEK_BAR
CONTROLS
ACTION_ROW
LYRICS
UP_NEXT
OUTPUT
EXTRA_INFO
```

Each section has:

- order;
- visibility;
- minimum size;
- preferred size;
- motion identity;
- accessibility label.

`CONTROLS` should never be fully hideable.

Persist:

```text
player_layout_order
player_hidden_sections
```

---

# 9. Motion System

Create centralized motion tokens.

## Standard transitions

| Motion | Duration | Recommended easing |
|---|---:|---|
| Small UI change | 150–200ms | FastOutSlowIn |
| Player section | 250–350ms | FastOutSlowIn |
| Mini → full player | 350–500ms | Spring |
| Artwork morph | 400–600ms | Spring |
| Palette transition | 800–1400ms | EaseInOut |
| Canvas fade | 300–700ms | EaseInOut |
| Lyrics state | 250–400ms | Spring |
| Queue sheet | 300–450ms | Spring |

## Principles

- Prefer spring for physical movement.
- Prefer tween for opacity/color.
- Avoid unrelated animations starting simultaneously.
- Pause expensive visual animations when playback is paused.
- Avoid infinite transitions on invisible composables.

---

# 10. Mini Player Evolution

Keep current layouts but add layout-aware rendering.

Potential modes:

```text
MiniPlayerStyle
 ├── Base
 ├── Floating
 ├── Compact
 ├── Apple
 ├── Spotify
 └── YouTube
```

The mini-player must support:

- artwork;
- title;
- artist;
- play/pause;
- next;
- progress;
- buffering state;
- favorite where appropriate;
- swipe up;
- drag/morph to full player.

---

# 11. Canvas System

## 11.1 Current IanPlayer

Current Canvas modes:

```text
OFF
GENERATIVE_AURORA
VIDEO
KEN_BURNS
```

These should become implementations of a common interface.

---

## 11.2 Proposed Canvas Provider Model

```text
CanvasManager
 ├── LocalCanvasProvider
 ├── AppleMusicCanvasProvider
 ├── TidalCanvasProvider
 ├── ViviMusicCanvasProvider
 ├── RemoteCanvasProvider
 └── GenerativeCanvasProvider
```

The exact provider list can evolve.

Each provider returns:

```text
CanvasArtwork
 ├── id
 ├── stillImage
 ├── animatedUrl
 ├── source
 ├── duration
 ├── aspectRatio
 ├── attribution
 └── cacheKey
```

---

# 12. Canvas Source Strategy

Default:

```text
AUTO
  ↓
Cached local canvas
  ↓
Apple-style provider
  ↓
Tidal-style provider
  ↓
Vivi-style provider
  ↓
Remote provider
  ↓
Ken Burns
  ↓
Generative Aurora
```

The app should never block playback while resolving Canvas.

---

# 13. Canvas Cache

Use separate caches for:

### Metadata cache

```text
songId
provider
canvasUrl
timestamp
expiresAt
```

### Media cache

```text
/files/canvas/
  <provider>/
    <song-id>.mp4
```

Requirements:

- bounded cache size;
- LRU eviction;
- stale URL detection;
- background prefetch;
- cancellation when song changes;
- no duplicate requests;
- disk cache before network;
- playback must continue if cache/network fails.

---

# 14. Canvas Playback

Canvas video:

- muted;
- looped;
- zoom/crop to avoid black borders;
- independent from audio playback;
- follows play/pause state;
- releases player when leaving screen;
- does not create a second audio session;
- must not interfere with the primary ExoPlayer.

Recommended:

```text
Primary audio player
       +
Muted Canvas video player
```

but lifecycle must be tightly scoped.

---

# 15. Canvas Visual Treatment

Use Canvas as a visual layer:

```text
Canvas
  ↓
Dark/light readability scrim
  ↓
Blur / refraction where supported
  ↓
Player controls
  ↓
Lyrics / sheets
```

Canvas should support:

- dim amount;
- blur amount;
- saturation;
- brightness;
- tint;
- vignette;
- motion intensity;
- reduce motion;
- battery saver mode.

---

# 16. Liquid Glass / Glass System

Convx's strongest visual lesson is not simply transparency.

The important concept is:

```text
background pixels
   ↓
backdrop capture
   ↓
blur
   ↓
lens/refraction
   ↓
surface tint
   ↓
highlight/specular edge
   ↓
content
```

IanPlayer should implement a lighter abstraction:

```text
IanGlass(
    backdrop = ...,
    blur = ...,
    tint = ...,
    vibrancy = ...,
    lens = ...,
    highlight = ...
)
```

Do not make every component glass.

Recommended targets:

- mini-player;
- floating nav;
- player action row;
- queue sheet;
- lyric sheet;
- floating buttons;
- settings cards.

---

# 17. Progress / Seek Experience

Implement a premium seek bar with:

- animated played portion;
- moving wave/squiggle;
- accurate drag;
- tap-to-seek;
- haptic feedback at meaningful positions;
- elapsed and remaining time;
- buffering visualization;
- reduced-motion fallback.

Convx's squiggly slider demonstrates a frame-driven wave phase and a custom Canvas path. IanPlayer should implement its own version rather than copying the source.

---

# 18. Lyrics

Improve current lyrics with:

- karaoke-style active line;
- optional word highlighting;
- smooth active-line scrolling;
- selected-line state;
- lyric source indicator;
- lyric translation when available;
- typography presets;
- full-screen lyrics mode;
- lyric background derived from album palette.

Interaction:

```text
Tap lyric
  → seek to line

Long press lyric
  → select line

Expand
  → full lyrics

Swipe
  → change lyric presentation
```

---

# 19. Up-Next

Current reorderable queue is a good base.

Improvements:

- spring drag feedback;
- animated insertion/removal;
- currently playing indicator;
- source indicator;
- downloaded/offline badge;
- duration;
- swipe actions;
- drag handle only where needed;
- queue virtualization;
- stable keys.

---

# 20. Track Transition

When the song changes:

```text
Old artwork
    ↓
shared element / morph
    ↓
new artwork
    ↓
palette extraction
    ↓
background transition
    ↓
Canvas resolve
    ↓
Canvas fade-in
    ↓
lyrics refresh
```

Do not make the entire screen disappear and recompose from scratch.

Target:

- artwork morph;
- palette crossfade;
- title/artist crossfade;
- Canvas crossfade;
- lyric state reset gracefully.

---

# 21. Performance Requirements

Target:

- 60 FPS baseline;
- 90/120Hz where device supports it;
- no continuous main-thread database work;
- no full-library recomposition for one row change;
- no full-screen redraw because playback position changed;
- image decode off main thread;
- Canvas work off main thread where possible;
- provider/network work on IO;
- stable Compose keys;
- `derivedStateOf` where useful;
- lifecycle-aware collection.

### Performance budget

- normal scrolling: no sustained frame drops;
- player expansion: no visible stutter;
- artwork transition: no blocking decode;
- Canvas provider lookup: never blocks audio;
- queue reorder: UI update immediately, persistence asynchronously.

---

# 22. Accessibility

All layouts must support:

- content descriptions;
- minimum touch targets;
- dynamic font scaling;
- reduced motion;
- sufficient contrast;
- screen readers;
- no essential information encoded only through color.

---

# 23. Settings Model

Recommended additions to `SettingsStore`:

```text
playerLayout
playerLayoutOrder
playerHiddenSections
miniPlayerStyle
canvasSource
canvasEnabled
canvasIntensity
canvasBlur
canvasDim
motionScale
reduceMotion
glassEnabled
glassBlur
glassVibrancy
glassLens
glassHighlight
seekBarStyle
lyricsStyle
```

Use enums instead of raw integer values where practical.

---

# 24. Success Criteria

The optimization is successful when:

1. User can switch between four player layouts.
2. Switching layout does not interrupt playback.
3. Mini-player and full player feel physically connected.
4. Track changes feel continuous instead of abrupt.
5. Canvas is visually rich but never required for playback.
6. Canvas is cached and prefetched.
7. Lyrics and Up-Next transitions feel smooth.
8. Large libraries remain responsive.
9. Settings survive restart.
10. The Base IanPlayer layout remains fully usable.
11. New layouts can be added without rewriting playback logic.

---

# 25. Licensing / Reference Boundary

Convx is GPL-3.0 and explicitly documents that it incorporates or is based on several other projects. The implementation plan therefore treats Convx primarily as an **architectural and UX reference**.

Before copying any Convx source code, vendored library, asset, or implementation verbatim into IanPlayer, perform a license-compatibility review.

Preferred approach:

- learn the architecture;
- reproduce behavior independently;
- use IanPlayer's own data/model/playback layer;
- write IanPlayer-native Compose components;
- preserve required notices if any compatible third-party code is actually incorporated.

---

# 26. Product North Star

IanPlayer should feel like:

> **"My music player, my layout, my atmosphere."**

The differentiator is not one copied UI. It is the combination of:

**playback reliability + customizable presentation + immersive Canvas + smooth motion + personal control.**
