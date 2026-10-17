# IanPlayer × Convx — Technical Trace / Reference Mapping

**Purpose:** map Convx concepts to the supplied IanPlayer implementation and define exactly what should be reused conceptually, adapted, or newly implemented.

---

# 1. Repository Snapshot

## IanPlayer

The supplied ZIP is treated as the primary implementation baseline.

Important observed files:

```text
MainActivity.kt                         ~4,161 LOC
viewmodel/MusicViewModel.kt             ~1,386 LOC
data/YTMusicRepository.kt               ~1,162 LOC
ui/SongListComponents.kt                ~1,143 LOC
ui/NowPlayingScreen.kt                  ~1,020 LOC
player/PlayerGateway.kt                   ~925 LOC
ui/tabs/StreamTab.kt                      ~674 LOC
ui/WaveRecordCard.kt                      ~581 LOC
data/LyricRepository.kt                   ~575 LOC
ui/canvas/PlayerCanvas.kt                 ~443 LOC
data/MusicRepository.kt                   ~313 LOC
data/SongStore.kt                         ~331 LOC
data/SettingsStore.kt                     ~132 LOC
```

The supplied build file reports:

```text
compileSdk 36
targetSdk 36
minSdk 24
versionName 6.2.5
Media3 1.4.1
Compose Material3
Room 2.6.1
Coil Compose 2.5.0
```

The public GitHub repository currently exposes a README describing IanPlayer v5.5.5, so the ZIP and GitHub repository are not the same snapshot. For implementation work, use the ZIP as the current source of truth and the GitHub repository as the public project reference.

---

# 2. Convx Reference Snapshot

Convx currently presents itself as a Jetpack Compose + Media3 music player with:

- Liquid Glass UI;
- real backdrop blur/refraction;
- adaptive artwork colors;
- bouncy/iOS-style motion;
- YouTube Music streaming;
- offline downloads;
- high-quality/lossless audio;
- synced lyrics;
- Discord Rich Presence;
- Listen Together;
- local preferences/library;
- modular playback;
- customizable player layouts;
- Canvas integrations;
- player customization;
- performance optimizations.

Convx's repository is structured into explicit modules such as:

```text
app
canvas
innertube
betterlyrics
applecanvas
vivimusiccanvas
spotify
spine
listen-together-server
...
```

Inside the Android app it separates:

```text
models
db
playback
lyrics
viewmodels
ui
constants
utils
```

The UI is further split into:

```text
ui/component
ui/player
ui/screens
ui/theme
ui/utils
```

This modularity is one of the most important architectural references for IanPlayer.

---

# 3. Core Architectural Comparison

| Concern | IanPlayer Current | Convx Reference | IanPlayer Direction |
|---|---|---|---|
| Playback | Media3 + PlayerGateway/PlaybackService | Media3 MusicService | Keep IanPlayer |
| ViewModel | Large MusicViewModel | Many focused ViewModels | Gradually split |
| Main Activity | Very large | Navigation/component architecture | Extract player/navigation |
| Full Player | NowPlayingScreen | Player + V2 player modules | Player host + layout registry |
| Mini Player | MainActivity | Dedicated MiniPlayer/FloatingMiniPlayer | Extract component |
| Canvas | PlayerCanvas.kt | Canvas module/providers | Provider + cache architecture |
| Glass | Mostly standard Compose surfaces | GlassEffect/backdrop | Add optional IanGlass |
| Layout presets | Mini layouts only | Player layout registry | Add full-player registry |
| Settings | SettingsStore | DataStore/preferences | Keep typed store, expand |
| Lyrics | LyricRepository + UI | Dedicated lyrics/providers | Keep, improve presentation |
| Queue | In MusicViewModel/player | Playback queues | Extract queue state later |
| Seek bar | Standard Slider | Wavy/Squiggly custom components | Add styles |
| Performance | Already some cache/concurrency work | Extensive recomposition/perf work | Apply selectively |

---

# 4. Convx → IanPlayer Trace

## TRACE-001 — Player Layout Registry

### Convx reference

`ui/player/PlayerLayoutRegistry.kt`

Convx models player sections as a registry:

```text
ALBUM_ART
TRACK_INFO
SEEK_BAR
CONTROLS
ACTION_ROW
```

It supports:

- default order;
- serialized order;
- deserialization;
- hidden slots;
- stale preference recovery.

### IanPlayer current

`ui/NowPlayingScreen.kt` is effectively one large fixed layout.

### Target

Create:

```text
ui/player/PlayerLayoutRegistry.kt
ui/player/PlayerSlot.kt
ui/player/PlayerLayoutId.kt
```

Add sections:

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

### Priority

**P0**

---

# 5. TRACE-002 — Four Player Themes

### Requirement

User must be able to select:

```text
Base IanPlayer
YouTube Music
Spotify
Apple Music
```

### Implementation

```text
PlayerLayoutId
 ├── BASE_IAN
 ├── YOUTUBE_MUSIC
 ├── SPOTIFY
 └── APPLE_MUSIC
```

### Important

These should be **interaction/design-inspired presets**, not copies of proprietary source/assets.

### Priority

**P0**

---

# 6. TRACE-003 — Current IanPlayer Now Playing Migration

### Source

```text
ui/NowPlayingScreen.kt
```

### Existing useful systems

- hero artwork animation;
- `Animatable`;
- palette transition;
- drag-to-dismiss;
- lyrics/UpNext expansion;
- Canvas integration;
- Wave Record;
- favorite;
- playback controls.

### Problem

The screen is approximately 1,000 LOC and owns too many concerns.

### Target

Break into:

```text
PlayerHost
BaseIanPlayerLayout
PlayerArtwork
PlayerTrackInfo
PlayerSeekBar
PlayerControls
PlayerActionRow
PlayerLyrics
PlayerUpNext
PlayerCanvasHost
```

---

# 7. TRACE-004 — Mini Player

### IanPlayer current

`MainActivity.kt`

Current layouts:

```text
Default
Floating
Compact
```

Current behavior:

- swipe up → full player;
- swipe down → cycle layout;
- horizontal gesture → volume;
- tap → full player.

### Convx reference

Dedicated:

```text
MiniPlayer.kt
FloatingMiniPlayer.kt
AppleMiniPlayer.kt
```

### Target

Extract:

```text
ui/player/MiniPlayerHost.kt
ui/player/mini/BaseMiniPlayer.kt
ui/player/mini/FloatingMiniPlayer.kt
ui/player/mini/CompactMiniPlayer.kt
ui/player/mini/AppleMiniPlayer.kt
```

### Improvement

The mini player should use the same `PlayerArtwork` identity as the full player so the morph looks continuous.

---

# 8. TRACE-005 — Artwork Morph

### IanPlayer current

`NowPlayingScreen.kt` already has:

```text
initialAlbumArtRect
targetAlbumArtRect
heroAnimProgress
heroInitScale
heroInitOffsetX
heroInitOffsetY
```

This is already close to the desired direction.

### Target

Extract into:

```text
PlayerArtworkMorph.kt
```

Conceptually similar to Convx's:

```text
PlayerArtworkMorph
PlayerContainerMorph
PlayerMorphLayers
```

### Behavior

```text
Mini artwork
   ↓
measure source rect
   ↓
measure full player rect
   ↓
spring/morph
   ↓
full artwork
```

Reverse on dismissal.

---

# 9. TRACE-006 — Canvas Architecture

## IanPlayer current

`ui/canvas/PlayerCanvas.kt`

Modes:

```text
GENERATIVE
VIDEO
KEN_BURNS
```

Video resolution:

```text
CanvasVideoSource.resolve()
```

Local search includes:

```text
<song>.canvas.mp4
<song>.mp4
Music/IanCanvas/
```

### Convx reference

Convx has a dedicated Canvas module and player integration.

`PlayerV2Canvas.kt`:

- checks Canvas enabled;
- resolves Canvas source;
- builds cache key;
- fetches asynchronously;
- runs provider fallback;
- caches playback result.

### Target

Create:

```text
data/canvas/
  CanvasArtwork.kt
  CanvasProvider.kt
  CanvasManager.kt
  CanvasCache.kt
  CanvasRepository.kt

data/canvas/providers/
  LocalCanvasProvider.kt
  AppleMusicCanvasProvider.kt
  TidalCanvasProvider.kt
  ViviCanvasProvider.kt
  RemoteCanvasProvider.kt
  GenerativeCanvasProvider.kt
```

---

# 10. TRACE-007 — Canvas Provider Priority

Recommended:

```text
AUTO
 ├── Local cache
 ├── Apple-style provider
 ├── Tidal-style provider
 ├── Vivi-style provider
 ├── Remote provider
 ├── Ken Burns
 └── Aurora
```

A provider failure should simply advance to the next fallback.

Never throw a playback-fatal error because Canvas is missing.

---

# 11. TRACE-008 — Canvas Cache

### Convx lesson

Canvas artwork can be cached by:

```text
media id + source
```

### IanPlayer target

```text
CanvasCacheKey(
    songId,
    source
)
```

Disk:

```text
cacheDir/canvas/<source>/<songId>.mp4
```

Metadata:

```text
songId
source
remoteUrl
localPath
createdAt
expiresAt
```

### Requirements

- LRU;
- max disk size;
- stale entry cleanup;
- duplicate-request prevention;
- background prefetch;
- cancellation.

---

# 12. TRACE-009 — Canvas Prefetch

IanPlayer already prefetches audio-related data.

Extend this concept:

```text
Current song
    ↓
resolve Canvas

Next song
    ↓
prefetch Canvas metadata

Next + 1
    ↓
optional low-priority prefetch
```

Do not download huge Canvas files indiscriminately.

---

# 13. TRACE-010 — Canvas Playback Isolation

IanPlayer currently uses a separate muted ExoPlayer for video Canvas.

This is acceptable if lifecycle is controlled.

Rules:

- `volume = 0`;
- no audio focus;
- no noisy-audio behavior;
- release when leaving player;
- release/reuse correctly when song changes;
- no second notification;
- no second MediaSession;
- Canvas state follows primary player.

---

# 14. TRACE-011 — Aurora Canvas

### Current implementation

`PlayerCanvas.kt` uses:

- radial gradients;
- multiple animated blobs;
- pulsing ring;
- dust particles;
- vignette;
- album palette;
- playback progress;
- animation speed affected by playing state.

This is already a good IanPlayer-native visual system.

### Improve

Add:

```text
intensity
speed
particleCount
blur
vignette
reactivity
```

### Performance

Reduce particle count on low-end devices.

---

# 15. TRACE-012 — Ken Burns

Current IanPlayer implementation uses:

- zoom animation;
- pan animation;
- album art;
- ambient color.

Keep it.

Move it behind:

```text
CanvasProvider
```

so it becomes a fallback rather than a separate special mode.

---

# 16. TRACE-013 — Liquid Glass

### Convx reference

`GlassEffect.kt` defines configurable parameters such as:

- enabled;
- vibrancy;
- blur radius;
- lens height;
- lens amount;
- chromatic aberration;
- depth effect;
- surface tint;
- highlight.

Convx's important architectural concept is a shared backdrop:

```text
rememberLayerBackdrop()
        ↓
Modifier.layerBackdrop()
        ↓
drawBackdrop()
        ↓
blur/lens/color effects
```

### IanPlayer target

Do NOT glassify the whole app.

Implement:

```text
IanGlassConfig
IanGlassSurface
IanGlassButton
IanGlassSheet
```

Start with a safe fallback:

```text
translucent surface
+
blur where supported
+
tint
+
highlight
```

Only use real refraction where device support/performance is acceptable.

---

# 17. TRACE-014 — Glass Configuration

Persist:

```text
glass_enabled
glass_blur
glass_vibrancy
glass_lens
glass_highlight
glass_tint
```

Expose advanced controls only under:

```text
Settings
 → Appearance
 → Liquid Glass
```

Simple users should not need to understand lens/refraction parameters.

---

# 18. TRACE-015 — Squiggly/Wavy Seek Bar

### Convx reference

`SquigglySlider.kt` uses:

- Compose Canvas;
- frame-driven phase;
- custom path;
- cubic curves;
- active/inactive clipping;
- animated wave height.

### IanPlayer target

Add:

```text
PlayerSeekBarStyle
 ├── CLASSIC
 ├── WAVE
 ├── SQUIGGLE
 └── MINIMAL
```

The animation must stop or reduce when paused.

---

# 19. TRACE-016 — Lyrics

### IanPlayer current

`LyricRepository.kt` + `NowPlayingScreen.kt`.

Already supports:

- synced lyrics;
- plain lyrics;
- source cycling;
- selected lines;
- lyric card.

### Convx reference

Convx separates lyrics UI/provider concerns more aggressively.

### Target

Extract:

```text
PlayerLyrics
LyricsPresentation
LyricsStyle
```

Add:

- active line;
- word highlight;
- full-screen mode;
- translation;
- smooth scrolling;
- reduced-motion behavior.

---

# 20. TRACE-017 — Up Next

### IanPlayer current

Already has:

- reorder;
- drag handle;
- animated elevation;
- responsive list;
- play-on-tap.

### Target improvements

- stable item identity;
- spring reorder;
- current-track marker;
- source badge;
- download badge;
- animated insertion/removal;
- swipe actions.

Do not rewrite the queue logic until UI extraction is complete.

---

# 21. TRACE-018 — Player Motion

Convx uses bouncy/iOS-style motion.

IanPlayer should standardize:

```text
PlayerMotion.fast
PlayerMotion.standard
PlayerMotion.sheet
PlayerMotion.spring
PlayerMotion.morph
PlayerMotion.palette
PlayerMotion.canvas
```

Do not scatter raw:

```kotlin
tween(...)
spring(...)
```

across every screen.

---

# 22. TRACE-019 — Palette Transition

IanPlayer already has palette interpolation.

Current pattern:

```text
prevPaletteBlend
nextPaletteBlend
paletteTransitionProgress
```

and ~1.4s transition.

Keep this architecture but extract:

```text
AmbientPaletteController
```

Responsibilities:

- extract palette;
- calculate readable colors;
- transition palette;
- expose stable UI colors.

---

# 23. TRACE-020 — Settings Migration

Current:

```text
SettingsStore
```

Add typed fields.

Suggested migration:

```text
player_layout = BASE_IAN
mini_player_style = BASE
canvas_source = AUTO
canvas_enabled = true
motion_scale = 1.0
reduce_motion = false
```

Old Canvas integers remain readable for backward compatibility.

---

# 24. TRACE-021 — Performance

Convx release history highlights several useful optimization patterns:

- avoid per-row subscriptions to large maps;
- stable keys;
- lazy settings lists;
- hardware bitmap decoding;
- fewer idle redraws;
- cheaper glass transitions;
- background sorting;
- coalesced stats writes.

### IanPlayer application

Audit:

```text
MainActivity
MusicViewModel
SongListComponents
NowPlayingScreen
PlayerCanvas
AlbumArtLoader
MusicRepository
YTMusicRepository
```

Specifically look for:

```text
collectAsState()
collectAsStateWithLifecycle()
remember(...)
derivedStateOf(...)
LazyColumn key
LaunchedEffect(...)
DisposableEffect(...)
```

---

# 25. TRACE-022 — MainActivity Refactor

Current `MainActivity.kt` is ~4,100 LOC.

Extract in this order:

```text
MainActivity
 ├── AppNavigation
 ├── MainScaffold
 ├── MiniPlayerHost
 ├── SettingsBottomSheet
 ├── TabNavigation
 └── PlayerHost
```

Do not refactor everything simultaneously.

---

# 26. TRACE-023 — MusicViewModel Refactor

Current `MusicViewModel.kt` is ~1,386 LOC.

Future decomposition:

```text
PlaybackViewModel
QueueViewModel
LibraryViewModel
SearchViewModel
LyricsViewModel
CanvasViewModel
RecapViewModel
SettingsViewModel
```

But this is a later optimization.

First extract UI responsibilities without changing business logic.

---

# 27. TRACE-024 — Player UI State

Create a stable immutable state:

```kotlin
data class PlayerUiState(
    val song: Song?,
    val albumArt: ImageBitmap?,
    val ambientColor: Color,
    val paletteColors: List<Color>,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val position: Long,
    val duration: Long,
    val isFavorite: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val queue: List<Song>,
    val lyrics: LyricsState,
    val canvas: CanvasState
)
```

The exact types can evolve.

Goal:

> Every player layout renders from the same state contract.

---

# 28. TRACE-025 — Layout Switching Flow

```text
Settings
   ↓
SettingsStore.playerLayout
   ↓
MainActivity/ViewModel
   ↓
PlayerHost
   ↓
PlayerLayoutRegistry.resolve(id)
   ↓
Selected PlayerLayout
   ↓
PlayerUiState
```

No playback restart.

---

# 29. TRACE-026 — Track Change Flow

Desired:

```text
Media3 currentMediaItem changes
        ↓
PlayerGateway
        ↓
MusicViewModel
        ↓
PlayerUiState
        ↓
ArtworkController
        ├── load new art
        └── palette extraction
        ↓
AmbientPaletteController
        ↓
CanvasManager
        ├── cache lookup
        ├── provider lookup
        └── fallback
        ↓
LyricsRepository
        ↓
Player layout rebind
```

Visual transitions occur independently from audio state.

---

# 30. TRACE-027 — Canvas Visual Layer

Recommended composition:

```text
Root
 ├── base adaptive background
 ├── Canvas
 │    ├── video/generative/Ken Burns
 │    └── tint
 ├── readability scrim
 ├── optional glass surface
 ├── artwork / controls
 ├── lyrics / queue
 └── sheets
```

This prevents the Canvas from becoming a visual dependency of every component.

---

# 31. TRACE-028 — Battery / Device Adaptation

Create:

```text
VisualPerformanceTier
 ├── HIGH
 ├── BALANCED
 └── LOW
```

HIGH:

- full Canvas;
- glass;
- particles;
- high-quality blur;
- 120Hz-friendly motion.

BALANCED:

- reduced particles;
- moderate blur;
- normal Canvas.

LOW:

- Ken Burns/static art;
- minimal blur;
- no particles;
- simplified motion.

---

# 32. TRACE-029 — Reduced Motion

When enabled:

```text
spring → tween
infinite animation → static
artwork morph → fade/scale
Canvas → low-motion/static
lyrics → instant/short scroll
```

This should be global.

---

# 33. TRACE-030 — QA Trace Matrix

| Feature | Current | Target | Priority |
|---|---|---|---|
| Base player | Yes | Preserve | P0 |
| YT Music layout | No | Add | P0 |
| Spotify layout | No | Add | P0 |
| Apple layout | No | Add | P0 |
| Mini layouts | Yes | Refactor | P0 |
| Artwork morph | Yes | Extract/improve | P0 |
| Canvas Aurora | Yes | Provider | P0 |
| Canvas Video | Yes | Cache/provider | P0 |
| Canvas Ken Burns | Yes | Fallback | P1 |
| Canvas remote | Limited | Provider | P1 |
| Canvas cache | Limited | Full cache | P0 |
| Glass | No/limited | Optional | P1 |
| Squiggly seek | No | Add | P1 |
| Synced lyrics | Yes | Improve | P1 |
| Up Next reorder | Yes | Improve | P1 |
| Player sections | No | Add | P0 |
| Layout persistence | No | Add | P0 |
| Motion settings | No | Add | P1 |
| Reduce motion | No | Add | P1 |
| Performance tiers | No | Add | P1 |

---

# 34. Suggested New Project Structure

```text
com.ianocent.musicplayer/

data/
  canvas/
    CanvasArtwork.kt
    CanvasProvider.kt
    CanvasManager.kt
    CanvasCache.kt
    CanvasRepository.kt

ui/
  player/
    PlayerHost.kt
    PlayerUiState.kt
    PlayerLayout.kt
    PlayerLayoutId.kt
    PlayerLayoutRegistry.kt
    PlayerSlot.kt
    PlayerMotion.kt

    layouts/
      BaseIanPlayerLayout.kt
      YoutubeMusicLayout.kt
      SpotifyInspiredLayout.kt
      AppleMusicInspiredLayout.kt

    components/
      PlayerArtwork.kt
      PlayerArtworkMorph.kt
      PlayerTrackInfo.kt
      PlayerSeekBar.kt
      PlayerControls.kt
      PlayerActionRow.kt
      PlayerLyrics.kt
      PlayerUpNext.kt
      PlayerOutput.kt
      PlayerCanvasHost.kt

    mini/
      MiniPlayerHost.kt
      BaseMiniPlayer.kt
      FloatingMiniPlayer.kt
      CompactMiniPlayer.kt
      AppleMiniPlayer.kt

  canvas/
    PlayerCanvas.kt
    CanvasMode.kt
    GenerativeCanvas.kt
    KenBurnsCanvas.kt
    VideoCanvas.kt

  glass/
    IanGlass.kt
    IanGlassConfig.kt

  lyrics/
    PlayerLyrics.kt
    LyricsStyle.kt

  motion/
    MotionTokens.kt
```

---

# 35. Reference Files Worth Studying

## Convx

- `ui/player/PlayerLayoutRegistry.kt`
- `ui/player/PlayerV2Canvas.kt`
- `ui/player/PlayerArtworkMorph.kt`
- `ui/player/PlayerContainerMorph.kt`
- `ui/player/PlayerMorphLayers.kt`
- `ui/player/PlayerSheetMotion.kt`
- `ui/player/MiniPlayer.kt`
- `ui/player/FloatingMiniPlayer.kt`
- `ui/player/AppleMiniPlayer.kt`
- `ui/component/GlassEffect.kt`
- `ui/component/SquigglySlider.kt`
- `ui/component/WaveformSeekBar.kt`
- `playback/MusicService.kt`
- `playback/PlayerConnection.kt`
- Canvas module providers.

## IanPlayer

- `MainActivity.kt`
- `ui/NowPlayingScreen.kt`
- `ui/canvas/PlayerCanvas.kt`
- `data/SettingsStore.kt`
- `viewmodel/MusicViewModel.kt`
- `player/PlayerGateway.kt`
- `player/PlaybackService.kt`
- `data/YTMusicRepository.kt`
- `data/LyricRepository.kt`
- `data/AlbumArtLoader.kt`
- `data/MusicRepository.kt`

---

# 36. Final Implementation Rule

The intended architecture is:

```text
                    ┌─────────────────────┐
                    │     Playback Core   │
                    │ Media3 / IanPlayer  │
                    └──────────┬──────────┘
                               │
                       PlayerUiState
                               │
                ┌──────────────┼──────────────┐
                │              │              │
          Base IanPlayer      YTM          Spotify
                │              │              │
                └──────────────┼──────────────┘
                               │
                         Apple Music
                               │
                    Shared Player Components
                               │
              ┌────────────────┼────────────────┐
              │                │                │
            Canvas          Lyrics            Queue
              │                │                │
       Provider + Cache    Repository       Playback
              │
       Local / Remote / Generative
```

The key idea is separation:

**Playback should be boring and reliable.  
The presentation should be expressive and replaceable.  
Canvas should be beautiful but optional.  
Settings should control the experience.**
