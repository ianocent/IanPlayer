# IanPlayer × Convx — Implementation Milestones

**Target:** incremental modernization without destabilizing playback  
**Strategy:** build foundations first, then migrate visuals  
**Primary source:** supplied IanPlayer ZIP  
**Reference:** Convx repository and release history

---

# Milestone 0 — Baseline Lock

## Objective

Freeze a known-good IanPlayer baseline before visual refactoring.

## Tasks

- [ ] Build current project in Debug.
- [ ] Verify local playback.
- [ ] Verify YouTube Music playback.
- [ ] Verify queue persistence.
- [ ] Verify downloads.
- [ ] Verify lyrics.
- [ ] Verify Canvas modes.
- [ ] Verify Wave Record.
- [ ] Verify mini-player layouts.
- [ ] Capture screenshots/video of:
  - [ ] Home
  - [ ] Songs
  - [ ] Stream
  - [ ] Playlist
  - [ ] Mini-player
  - [ ] Now Playing
  - [ ] Lyrics
  - [ ] Up-Next
  - [ ] Settings
  - [ ] Canvas
- [ ] Record current APK version and Git commit.

## Exit criteria

No known playback regression introduced by the modernization branch.

---

# Milestone 1 — Architecture Extraction

## Objective

Reduce coupling in the current monolithic UI.

## Current pressure points

- `MainActivity.kt` ~4,100 LOC.
- `NowPlayingScreen.kt` ~1,000 LOC.
- `MusicViewModel.kt` ~1,300 LOC.

## Tasks

Create:

```text
ui/player/
  PlayerHost.kt
  PlayerUiState.kt
  PlayerLayout.kt
  PlayerLayoutRegistry.kt
  PlayerLayoutId.kt
  PlayerMotion.kt
  PlayerSection.kt
  MiniPlayerHost.kt

ui/player/layouts/
  BaseIanPlayerLayout.kt
  YoutubeMusicLayout.kt
  SpotifyInspiredLayout.kt
  AppleMusicInspiredLayout.kt

ui/player/components/
  PlayerArtwork.kt
  PlayerTrackInfo.kt
  PlayerSeekBar.kt
  PlayerControls.kt
  PlayerActionRow.kt
  PlayerLyrics.kt
  PlayerUpNext.kt
  PlayerOutput.kt
```

## Acceptance

- Playback code remains in existing player/viewmodel layer.
- Layouts receive state and callbacks only.
- No layout calls repository/network APIs directly.

---

# Milestone 2 — Player Layout Registry

## Objective

Make layouts pluggable.

## Data model

```kotlin
enum class PlayerLayoutId {
    BASE_IAN,
    YOUTUBE_MUSIC,
    SPOTIFY,
    APPLE_MUSIC
}
```

Registry:

```text
PlayerLayoutRegistry
  getDefault()
  getAvailable()
  serialize()
  deserialize()
  resolve()
```

## Persistence

Add:

```text
player_layout
```

to `SettingsStore`.

## Acceptance

Changing layout in settings immediately changes the full player.

---

# Milestone 3 — Player Slot System

## Objective

Allow the user to customize sections.

## Slots

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

## Tasks

- [ ] Registry.
- [ ] Serialize order.
- [ ] Deserialize safely.
- [ ] Hidden sections.
- [ ] Never hide Controls.
- [ ] Settings UI for reorder/hide.
- [ ] Preview layout.

## Acceptance

A stale/corrupt preference never removes a required section from the player.

---

# Milestone 4 — Motion System

## Objective

Replace ad-hoc animations with reusable motion tokens.

## Tasks

Create:

```text
PlayerMotionTokens
  fast
  standard
  expressive
  spring
  artworkMorph
  sheet
  palette
  canvas
```

Add:

```text
Reduce Motion
Animation Scale
```

## Tasks

- [ ] Standardize durations.
- [ ] Standardize spring stiffness/damping.
- [ ] Remove redundant infinite animations.
- [ ] Stop heavy effects when paused/invisible.
- [ ] Add reduced-motion fallback.

## Acceptance

All four layouts share the same motion language.

---

# Milestone 5 — Mini Player → Full Player Morph

## Objective

Make expansion feel like the same object growing into the full player.

## Tasks

- [ ] Shared artwork bounds.
- [ ] Artwork scale/translation.
- [ ] Mini-player container morph.
- [ ] Background morph.
- [ ] Title transition.
- [ ] Progress transition.
- [ ] Gesture cancellation.
- [ ] Swipe-down reverse transition.

## Acceptance

No flash/white frame/jump during open/close.

---

# Milestone 6 — Base IanPlayer Layout Migration

## Objective

Move the existing Now Playing experience into the new layout system.

## Preserve

- current artwork;
- ambient palette;
- lyrics;
- Up-Next;
- Wave Record;
- Canvas;
- controls;
- drag-to-dismiss;
- favorite;
- shuffle/repeat.

## Acceptance

Base layout is visually equivalent or better than the current ZIP version.

---

# Milestone 7 — YouTube Music-inspired Layout

## Objective

Add a distinct clean streaming-oriented player.

## Features

- artwork hero;
- title/artist;
- large seek bar;
- playback controls;
- contextual actions;
- lyrics;
- queue;
- adaptive palette;
- subtle background.

## Acceptance

Switching from Base to YTM layout does not interrupt audio.

---

# Milestone 8 — Spotify-inspired Layout

## Objective

Add an information-dense, playlist-oriented player.

## Features

- artwork;
- title/artist;
- like;
- seek;
- large play;
- previous/next;
- shuffle/repeat;
- queue;
- lyrics;
- playlist context.

## Acceptance

Player state remains identical when switching layouts.

---

# Milestone 9 — Apple Music-inspired Layout

## Objective

Add an elegant artwork-first layout.

## Features

- large artwork;
- adaptive background;
- soft blur/glass;
- typography-first hierarchy;
- sheet-style lyrics;
- queue;
- output/action surface.

## Acceptance

Apple-inspired layout is visually distinct while remaining IanPlayer-native.

---

# Milestone 10 — Canvas 2.0 Architecture

## Objective

Transform Canvas from a mode switch into a provider/cache system.

## Current

```text
Off
Generative
Video
Ken Burns
```

## Target

```text
CanvasManager
 ├── Local
 ├── Apple-style
 ├── Tidal-style
 ├── Vivi-style
 ├── Remote
 └── Generative
```

## Tasks

- [ ] CanvasArtwork model.
- [ ] Provider interface.
- [ ] Provider priority.
- [ ] Provider timeout.
- [ ] Failure fallback.
- [ ] Cache key.
- [ ] Disk cache.
- [ ] Metadata cache.
- [ ] Prefetch.
- [ ] Cancellation.

## Acceptance

Canvas lookup never blocks audio playback.

---

# Milestone 11 — Canvas Visual Pipeline

## Objective

Make Canvas part of the player composition rather than an isolated background.

## Pipeline

```text
Canvas
 ↓
Color/tint
 ↓
Blur
 ↓
Readability scrim
 ↓
Player content
```

## Tasks

- [ ] Canvas dim.
- [ ] Canvas blur.
- [ ] Canvas saturation.
- [ ] Canvas brightness.
- [ ] Vignette.
- [ ] Motion intensity.
- [ ] Reduce motion.
- [ ] Battery saver mode.

---

# Milestone 12 — Glass Surface System

## Objective

Introduce controlled glass surfaces inspired by Convx.

## Components

```text
IanGlassSurface
IanGlassButton
IanGlassSheet
IanGlassMiniPlayer
```

## Config

```text
enabled
blur
vibrancy
lens
highlight
tint
```

## Apply only to

- mini-player;
- floating controls;
- queue;
- lyrics;
- player action row;
- settings cards.

## Acceptance

Low-end devices have a fallback path with no expensive refraction.

---

# Milestone 13 — Seek Bar 2.0

## Objective

Create a responsive, animated seek experience.

## Styles

```text
Classic
Wavy
Squiggly
Minimal
```

## Tasks

- [ ] Tap seek.
- [ ] Drag seek.
- [ ] Accurate position.
- [ ] Buffer indicator.
- [ ] Wave animation.
- [ ] Haptic feedback.
- [ ] Reduced motion.

## Acceptance

Seeking remains responsive while the waveform is animated.

---

# Milestone 14 — Lyrics 2.0

## Tasks

- [ ] Active-line animation.
- [ ] Word-level highlighting where source supports it.
- [ ] Smooth auto-scroll.
- [ ] Full-screen lyrics.
- [ ] Lyrics style presets.
- [ ] Translation slot.
- [ ] Better loading state.
- [ ] Seek on lyric tap.
- [ ] Lyric selection.

## Acceptance

Lyrics never visibly jump when current playback position updates.

---

# Milestone 15 — Up-Next 2.0

## Tasks

- [ ] Spring reorder.
- [ ] Current song indicator.
- [ ] Animated insert/remove.
- [ ] Stable keys.
- [ ] Queue virtualization.
- [ ] Swipe actions.
- [ ] Drag feedback.

---

# Milestone 16 — Performance Pass

## Audit

- [ ] Compose recompositions.
- [ ] `collectAsState` scope.
- [ ] Stable keys.
- [ ] Image decode.
- [ ] Palette extraction.
- [ ] Canvas draw calls.
- [ ] ExoPlayer lifecycle.
- [ ] Room queries.
- [ ] JSON writes.
- [ ] network calls.
- [ ] cache duplication.

## Targets

- 60 FPS baseline.
- No obvious jank during player open.
- No obvious jank during queue reorder.
- No audio interruption during Canvas resolution.

---

# Milestone 17 — Tablet / Large Screen

## Tasks

- [ ] Width-aware player.
- [ ] Two-column layout.
- [ ] Persistent queue pane.
- [ ] Landscape-specific controls.
- [ ] Glass surfaces optimized for wide screens.

---

# Milestone 18 — QA / Regression

## Matrix

### Playback

- [ ] Local MP3
- [ ] M4A
- [ ] FLAC
- [ ] WAV
- [ ] OGG
- [ ] YT Music
- [ ] Cached stream
- [ ] Downloaded stream

### Player

- [ ] Base
- [ ] YT Music
- [ ] Spotify
- [ ] Apple Music

### Canvas

- [ ] Off
- [ ] Generative
- [ ] Ken Burns
- [ ] Local video
- [ ] Remote Canvas
- [ ] Missing Canvas
- [ ] Broken URL

### Devices

- [ ] Android 7/8+
- [ ] low RAM
- [ ] 60Hz
- [ ] 90Hz
- [ ] 120Hz
- [ ] portrait
- [ ] landscape

---

# Milestone 19 — Release Hardening

## Tasks

- [ ] Baseline profile.
- [ ] R8 rules.
- [ ] Crash audit.
- [ ] ANR audit.
- [ ] Startup timing.
- [ ] Memory audit.
- [ ] Network timeout audit.
- [ ] Canvas cache size audit.
- [ ] Settings migration.
- [ ] Existing preference migration.

---

# Suggested Branch Strategy

```text
main
 └── feature/player-system
      ├── feature/player-registry
      ├── feature/player-motion
      ├── feature/player-base
      ├── feature/player-youtube
      ├── feature/player-spotify
      ├── feature/player-apple
      ├── feature/canvas-2
      ├── feature/glass-system
      ├── feature/lyrics-2
      └── feature/performance
```

Do not merge all work into one giant commit.

---

# Recommended Delivery Order

```text
1. Baseline
2. Player architecture
3. Registry
4. Motion
5. Base migration
6. Mini/full morph
7. Canvas 2
8. YouTube layout
9. Spotify layout
10. Apple layout
11. Glass
12. Lyrics
13. Seek bar
14. Up Next
15. Performance
16. QA
```

The important rule:

> **Never redesign the UI while the playback engine is unstable.**
