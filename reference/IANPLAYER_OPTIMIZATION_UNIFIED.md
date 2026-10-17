# IanPlayer Optimization — Unified PRD, Trace & Milestones

**Status:** Consolidated Reference  
**Date:** 2026-09-06  
**Sources:** ChatGPT, Claude, Gemini analysis of Convx → IanPlayer  
**Reference:** Convx (`cosmictaserdev-creator/Convx`) — GPL-3.0, architectural/UX reference only  
**Baseline:** IanPlayer v6.2.5 (ZIP snapshot), Kotlin, Jetpack Compose, Media3 1.4.1

---

# IMPLEMENTATION STATUS (2026-09-07)

> Update kaki dari sesi implementasi agen + QA manual user. Doc 04 (deep-fix) & 05 (visual-gap) punya detail per-root-cause.

## Yang SUDAH dikerjakan (build green, QA user jalan)

| Item | Status | Detail |
|---|---|---|
| ~~Player layout registry + 4 layout~~ | ↩ DIHAPUS | 07/09: user putuskan single-layout BASE. `YTMusicPlayerLayout`/`SpotifyPlayerLayout`/`AppleMusicPlayerLayout` + `PlayerLayoutId/Registry` + settings chips + `setPlayerLayout` flow + `SettingsStore.playerLayoutMode` dihapus permanen. |
| **PlayerHost** (simplified) | ✅ Done | Render `NowPlayingScreen` langsung; when-switch dihapus. |
| **`PlayerUiState` unifikasi** | ✅ Done | `rememberPlayerUiState()` collect 20 flow; 07/09: pecah jadi 5 slice (`PlaybackState`, `VisualSlice`, `LyricsSlice`, `PreferenceSlice`, `NavigationSlice`). |
| **`PlayerBackdrop` shared** | ✅ Done | 07/09: provider chain nyata (`CanvasProvider.resolveFor` VIDEO→GENERATIVE fallback) + `motionScale` propogate ke palette crossfade. |
| **Canvas provider chain** | ✅ Done | 04 §4.3 — `CanvasProvider.isAvailable/fallback` interface + chain walk; `PlayerBackdrop` tidak hardcode VIDEO→GENERATIVE lagi. |
| **MotionTokens konsisten** | ✅ Done | 04 §4.4 — `<T>scaledTween(base, motionScale, reduceMotion)` helper; hero/section/lyric/shimmer/palette/drag spring semua via 1 titik. |
| **reduceMotion / motionScale konsisten** | ✅ Done | 04 §4.5 — hero, section, lyric, shimmer, marquee, palette crossfade, drag spring semua respect. |
| **Glass merata** | ✅ Done | Root bg = adaptive color album art; home tabs glass capsule; main container card glass; mini player glass pill; NowPlayingScreen UpNext glass + Controls (`PlayerControlsBar`) glass. |
| **Crossfade sequential** | ✅ Done | `crossfadeDurationMs` setting (Off/0.3s/0.7s/1.2s) + smoothstep volumefade (`easeFade`) |
| **Crossfade overlap** | ✅ Done | Dual-player: temp focus-free `ExoPlayer` overlap fade-out/in lalu handoff ke session player; gen-guard, pause-safety, ENDED guard |
| **Glass infra + perf-tier gate** | ✅ Done | `IanGlassConfig/Surface/Button/Sheet`; `VisualTier.detect()` auto-disable LOW; `rememberGlassConfig`; glass sekarang aktif di semua section. |
| **Font swap** | ✅ Done | 05 §4 — `Type.kt` → `OutfitFontFamily` (OFL, 4 weight `res/font/`) + suppress internal ctor workaround |
| **Buang glass dari row teks** | ✅ Done | 05 §2 — Row "Song by:" polos; 3 tombol canvas/like/wave pakai `IanGlassButton`. |
| **Artwork duotone** | ↩ DIHAPUS | 07/09: `DuotoneArtwork.kt` dihapus permanen (terpasang di layout alternatif yang sudah dihapus; tidak dihawatirkan ke BASE). |
| **Bug fix #1 — `matchParentSize`** | ✅ Done | Panel glass meledak → decor `matchParentSize` (BoxScope member) |
| **Bug fix #2 — hero morph loop** | ✅ Done | Capture target rect sekali + `snapshotFlow` + `reduceMotion` respect |
| **Bug fix #3 — `fetchHomeSongs` silent fail** | ✅ Done | Logging + `ParsingFailed` + UI retry `CloudOff` |
| **Smooth lyric highlight** | ✅ Done | `SyncedLyricView` active line via `scaledTween(LYRIC_HIGHLIGHT)`. |

## Yang BELUM / Backlog

| Item | Referensi | Status |
|---|---|---|
| **Real backdrop blur** | 04 §4.2, 05 §3 | ⛔ Scope besar (GraphicsLayer capture layer) — milestone terpisah |
| **Seek bar 2.0 + lyrics 2.0 (word)** | M7/M8 | ⏳ Belum; `LyricLine` model cuma per-baris |
| **QA visual glass** | — | ⏳ User QA blur/refraksi adaptive + mini pill glass + UpNext/Controls glass |
| **Session commit** | M0 | ⏳ Belum ada Git commit antar fitur  

---

# PART I — PRODUCT REQUIREMENTS DOCUMENT (PRD)

---

## 1. Product Vision

IanPlayer evolves from a feature-rich music player into a **highly customizable, fluid, immersive music experience**.

Target experience:
- Fast enough to feel native on large libraries
- Visually alive without distraction
- Smooth during navigation, player expansion, lyrics, queue changes, track changes
- Deeply customizable
- Multiple player/layout personalities
- Animated Canvas/background artwork without playback instability
- Still recognizably IanPlayer, not a Convx clone

**Core principle: One playback engine, one data model, multiple visual experiences.**

Playback/data layer independent from player presentation. User changing layout must not change queue, current song, lyrics, favorites, downloads, playback state, or streaming source.

---

## 2. Goals

### P0 — Must Have
1. Player-layout abstraction/registry system
2. Four selectable full-player presets: Base IanPlayer, YouTube Music, Spotify, Apple Music
3. Single playback/data engine behind all layouts
4. Improved mini-player → full-player morphing
5. Improved track-to-track visual transitions
6. Canvas provider-oriented system (expand from local/generated/video)
7. Canvas caching and prefetching
8. Canvas failure non-blocking — playback continues
9. Spring-based motion primitives and consistent motion tokens
10. Persistent settings
11. No unnecessary recomposition/redraws

### P1 — High Value
1. Custom player section ordering
2. Hide/show player sections
3. Per-layout player preferences
4. Wavy/squiggly progress slider
5. Better artwork morphing
6. Dynamic artwork-based color transitions
7. Karaoke-style lyrics highlighting
8. Smoother queue drag/reorder
9. Player customization preview
10. Ambient mode / visualizer mode
11. Canvas source priority chain

### P2 — Nice to Have
1. Shareable/exportable player presets
2. User-created player configurations
3. Sticker/decorative layer system
4. Auto-DJ transitions
5. Listen Together
6. Discord Rich Presence
7. Advanced equalizer/audio processing
8. Tablet-specific layouts
9. Android Auto-specific player

---

## 3. Non-Goals (v1)

- Do NOT replace IanPlayer's existing playback architecture
- Do NOT rewrite all of `MainActivity.kt` in one pass
- Do NOT directly copy Convx source files (GPL-3.0 incompatible)
- Do NOT make UI dependent on online Canvas providers
- Do NOT run animated effects continuously when not playing
- Do NOT introduce expensive blur/refraction everywhere by default
- Do NOT sacrifice accessibility for visual effects

---

## 4. Current Baseline

### Existing Playback
- Media3 ExoPlayer
- MediaSession/foreground playback
- Queue and Up-Next
- Shuffle/repeat
- Crossfade (basic)
- Stream resolution + caching
- Local MediaStore scanning
- YouTube Music streaming (InnerTube `YTMusicRepository`)
- Downloads
- Persisted playback state

### Existing Player (`ui/NowPlayingScreen.kt` ~1,020 LOC)
- Album art with hero transition
- Animated ambient colors + gradient background
- Lyrics + Up-Next
- Drag-to-dismiss
- Favorite, Canvas mode, Wave Record
- Play/previous/next/shuffle/repeat
- Lyric card generation

### Existing Canvas (`ui/canvas/PlayerCanvas.kt` ~443 LOC)
- Generative Aurora
- Video
- Ken Burns
- Off

### Existing Mini-Player (`MainActivity.kt` ~4,161 LOC)
- Default / Floating / Compact
- Swipe-driven layout changes

### Existing Settings (`data/SettingsStore.kt` ~132 LOC)
- Dark mode, mini layout, Canvas mode, tab order
- Voice assistant, social signals, user profile, pill position

### Key Files

| File | LOC | Role |
|---|---:|---|
| `MainActivity.kt` | ~4,161 | Main entry, navigation, mini-player, scaffold |
| `MusicViewModel.kt` | ~1,386 | State holder, playback/UI logic |
| `YTMusicRepository.kt` | ~1,162 | InnerTube streaming |
| `SongListComponents.kt` | ~1,143 | Song list UI |
| `NowPlayingScreen.kt` | ~1,020 | Full player screen (monolithic) |
| `PlayerGateway.kt` | ~925 | Playback service interface |
| `StreamTab.kt` | ~674 | Stream tab |
| `WaveRecordCard.kt` | ~581 | Wave record feature |
| `LyricRepository.kt` | ~575 | Lyrics data |
| `PlayerCanvas.kt` | ~443 | Canvas rendering |
| `MusicRepository.kt` | ~313 | Music data |
| `SongStore.kt` | ~331 | Song storage |
| `SettingsStore.kt` | ~132 | Preferences |

### Build Config
```
compileSdk 36 | targetSdk 36 | minSdk 24
versionName 6.2.5
Media3 1.4.1 | Compose Material3
Room 2.6.1 | Coil Compose 2.5.0
```

---

## 5. Proposed Architecture

### 5.1 Player Presentation Layer

```
PlayerLayout
 ├── BaseIanPlayerLayout
 ├── YoutubeMusicLayout
 ├── SpotifyInspiredLayout
 └── AppleMusicInspiredLayout
```

All layouts consume the same `PlayerUiState`:
```
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

### 5.2 Core Architectural Comparison

| Concern | IanPlayer Current | Convx Reference | IanPlayer Direction |
|---|---|---|---|
| Playback | Media3 + PlayerGateway/PlaybackService | Media3 MusicService | Keep IanPlayer |
| ViewModel | Large MusicViewModel | Many focused ViewModels | Gradually split |
| Main Activity | Very large (~4,100 LOC) | Navigation/component architecture | Extract player/navigation |
| Full Player | NowPlayingScreen (monolith) | Player + V2 player modules | Player host + layout registry |
| Mini Player | MainActivity inline | Dedicated MiniPlayer variants | Extract component |
| Canvas | PlayerCanvas.kt | Canvas module/providers | Provider + cache architecture |
| Glass | Standard Compose surfaces | GlassEffect/backdrop | Add optional IanGlass |
| Layout presets | Mini layouts only | Player layout registry | Add full-player registry |
| Settings | SettingsStore (SharedPrefs) | DataStore/preferences | Keep typed store, expand |
| Lyrics | LyricRepository + UI | Dedicated lyrics/providers | Keep, improve presentation |
| Queue | In MusicViewModel/player | Playback queues | Extract queue state later |
| Seek bar | Standard Slider | Wavy/Squiggly custom | Add styles |
| Performance | Some cache/concurrency | Extensive recomposition/perf work | Apply selectively |

---

## 6. Player Layout Presets

### 6.1 Base IanPlayer
- Current IanPlayer composition preserved
- Ambient artwork colors, prominent artwork
- Lyric + Up-Next split, Wave Record, Canvas controls
- Existing gestures, current mini-player language
- Migration-safe default

### 6.2 YouTube Music-inspired
- Artwork-forward player, large title/artist
- Clean bottom control area, strong queue/up-next
- Prominent lyrics entry, contextual actions
- Adaptive artwork color, rounded surfaces
- Minimal visual noise

```
Top bar: Back | Device/output | More
Large artwork / Canvas
Song title | Artist
Progress: Elapsed | Remaining
Like | Previous | Play | Next | Repeat
Lyrics / Related / Queue
```

### 6.3 Spotify-inspired
- Strong album artwork, compact information-dense metadata
- Prominent progress control, large play/pause
- Secondary controls below, queue access
- Lyrics as distinct experience

```
Top context: Album / Playlist
Artwork
Song | Artist
Progress
Like | Previous | Play | Next | Queue
Lyrics | Up Next
```

### 6.4 Apple Music-inspired
- Artwork as visual hero, elegant spacing
- Large typography, soft adaptive background
- Subtle blur/glass, smooth sheet-like transitions
- Strong lyrics experience, visually quiet controls

```
Top: Back | Context | More
Artwork / Canvas
Title | Artist
Large progress control
Playback controls
Volume / AirPlay-like output
Lyrics | Queue
```

---

## 7. Layout Switching Requirements

Settings structure:
```
Settings
 └── Player
      ├── Player Layout (Base/YT Music/Spotify/Apple Music)
      ├── Mini Player
      ├── Canvas
      ├── Player Sections
      └── Motion
```

Changing layout:
- No playback restart
- No queue reset
- No position reset
- No lyrics reset
- No stream reload
- Animate between compatible layouts where possible

---

## 8. Custom Player Sections

Proposed sections (inspired by Convx slot registry):
```
ALBUM_ART | CANVAS | TRACK_INFO | SEEK_BAR | CONTROLS
ACTION_ROW | LYRICS | UP_NEXT | OUTPUT | EXTRA_INFO
```

Each section has: order, visibility, minimum size, preferred size, motion identity, accessibility label.

`CONTROLS` must never be fully hideable.

Persist: `player_layout_order`, `player_hidden_sections`

---

## 9. Canvas System

### Current
```
Off | Generative Aurora | Video | Ken Burns
```

### Target — Provider Model
```
CanvasManager
 ├── LocalCanvasProvider
 ├── AppleMusicCanvasProvider
 ├── TidalCanvasProvider
 ├── ViviMusicCanvasProvider
 ├── RemoteCanvasProvider
 └── GenerativeCanvasProvider
```

Each provider returns:
```
CanvasArtwork
 ├── id | stillImage | animatedUrl | source
 ├── duration | aspectRatio | attribution | cacheKey
```

### Source Priority (default AUTO)
```
Cached local canvas
  → Apple-style provider
  → Tidal-style provider
  → Vivi-style provider
  → Remote provider
  → Ken Burns
  → Generative Aurora
```

Canvas must never block playback.

### Canvas Cache
- Metadata cache: `songId, provider, canvasUrl, timestamp, expiresAt`
- Media cache: `/files/canvas/<provider>/<song-id>.mp4`
- Bounded size, LRU eviction, stale URL detection
- Background prefetch, cancellation on song change
- No duplicate requests, disk cache before network

### Canvas Prefetch
```
Current song → resolve Canvas
Next song    → prefetch Canvas metadata
Next + 1     → optional low-priority prefetch
```

### Canvas Playback Rules
- Muted, looped, zoom/crop (no black borders)
- Independent from audio playback
- Follows play/pause state
- Releases player when leaving screen
- No second audio session, no second MediaSession
- No second notification

### Canvas Visual Layer Composition
```
Root
 ├── base adaptive background
 ├── Canvas (video/generative/Ken Burns + tint)
 ├── readability scrim
 ├── optional glass surface
 ├── artwork / controls
 ├── lyrics / queue
 └── sheets
```

Canvas settings: dim amount, blur, saturation, brightness, tint, vignette, motion intensity, reduce motion, battery saver.

### Per-Layout Background Treatment

| Layout | Background | Technique |
|---|---|---|
| Base IanPlayer | Existing CanvasMode (user-selectable) | Already implemented |
| YT Music | Ambient blur from artwork | `RenderEffect.createBlurEffect` (API 31+), fallback: Palette gradient |
| Spotify | Solid/gradient flat from dominant colors | `Brush.verticalGradient` from Palette swatch |
| Apple Music | Animated soft gradient mesh | `Palette` + `Brush.radialGradient` + `rememberInfiniteTransition` |

---

## 10. Liquid Glass System

Convx's key concept: shared backdrop capture → blur/lens/refraction → surface tint → highlight.

IanPlayer lighter abstraction:
```
IanGlass(backdrop, blur, tint, vibrancy, lens, highlight)
```

Apply ONLY to:
- Mini-player, floating nav, player action row
- Queue sheet, lyric sheet, floating buttons, settings cards

Config: `enabled, blur, vibrancy, lens, highlight, tint`
Fallback: translucent surface + blur where supported + tint + highlight

---

## 11. Motion System

### Standard Transitions

| Motion | Duration | Easing |
|---|---:|---|
| Small UI change | 150–200ms | FastOutSlowIn |
| Player section | 250–350ms | FastOutSlowIn |
| Mini → full player | 350–500ms | Spring |
| Artwork morph | 400–600ms | Spring |
| Palette transition | 800–1400ms | EaseInOut |
| Canvas fade | 300–700ms | EaseInOut |
| Lyrics state | 250–400ms | Spring |
| Queue sheet | 300–450ms | Spring |

### Motion Tokens
```
PlayerMotion.fast
PlayerMotion.standard
PlayerMotion.expressive
PlayerMotion.spring
PlayerMotion.morph
PlayerMotion.palette
PlayerMotion.canvas
PlayerMotion.sheet
```

### Principles
- Spring for physical movement, tween for opacity/color
- No unrelated simultaneous animations
- Pause expensive visuals when paused
- No infinite transitions on invisible composables

---

## 12. Mini Player Evolution

Keep current layouts + add layout-aware rendering:
```
MiniPlayerStyle
 ├── Base | Floating | Compact
 ├── Apple | Spotify | YouTube
```

Must support: artwork, title, artist, play/pause, next, progress, buffering, favorite, swipe up, drag/morph to full player.

---

## 13. Lyrics

Improvements:
- Karaoke-style active line (word highlighting where data supports)
- Smooth active-line scrolling
- Full-screen lyrics mode
- Lyrics style presets
- Translation slot
- Better loading state
- Seek on lyric tap
- Lyric selection
- Lyrics background from album palette

Interaction: tap → seek, long press → select, expand → full lyrics, swipe → change presentation.

---

## 14. Up-Next

Improvements:
- Spring drag feedback
- Animated insertion/removal
- Currently playing indicator
- Source indicator, downloaded/offline badge, duration
- Swipe actions, drag handle only where needed
- Queue virtualization, stable keys

---

## 15. Track Transition Flow

```
Media3 currentMediaItem changes
  → PlayerGateway
  → MusicViewModel
  → PlayerUiState
  → ArtworkController (load new art + palette extraction)
  → AmbientPaletteController
  → CanvasManager (cache lookup → provider → fallback)
  → LyricsRepository
  → Player layout rebind
```

Visual: artwork morph → palette crossfade → title/artist crossfade → Canvas crossfade → lyric state reset.

---

## 16. Seek Bar

Styles: `Classic | Wave | Squiggly | Minimal`

Features: tap seek, drag seek, accurate position, buffer indicator, wave animation, haptic feedback, reduced-motion fallback.

Animation must stop/reduce when paused.

---

## 17. Performance Requirements

- 60 FPS baseline, 90/120Hz where supported
- No continuous main-thread database work
- No full-library recomposition for one row change
- No full-screen redraw from playback position change
- Image decode off main thread
- Canvas work off main thread where possible
- Provider/network on IO
- Stable Compose keys, `derivedStateOf` where useful
- Lifecycle-aware collection

### Performance Tiers
```
VisualPerformanceTier
 ├── HIGH: full Canvas, glass, particles, 120Hz motion
 ├── BALANCED: reduced particles, moderate blur, normal Canvas
 └── LOW: Ken Burns/static art, minimal blur, no particles
```

---

## 18. Settings Model

```
playerLayout
playerLayoutOrder
playerHiddenSections
miniPlayerStyle
canvasSource | canvasEnabled | canvasIntensity | canvasBlur | canvasDim
motionScale | reduceMotion
glassEnabled | glassBlur | glassVibrancy | glassLens | glassHighlight
seekBarStyle
lyricsStyle
crossfadeDurationMs
```

Use enums instead of raw integers where practical. Backward-compatible with existing integer Canvas values.

---

## 19. Accessibility

All layouts must support:
- Content descriptions, minimum touch targets
- Dynamic font scaling, reduced motion
- Sufficient contrast, screen readers
- No essential information encoded only through color

---

## 20. Licensing Boundary

Convx = GPL-3.0. All adoption must be **clean-room rewrite**:
- Learn the architecture
- Reproduce behavior independently
- Use IanPlayer's own data/model/playback layer
- Write IanPlayer-native Compose components
- Preserve required notices if compatible third-party code incorporated

---

## 21. Success Criteria

1. User can switch between four player layouts
2. Switching layout does not interrupt playback
3. Mini-player and full player feel physically connected
4. Track changes feel continuous, not abrupt
5. Canvas is visually rich but never required for playback
6. Canvas is cached and prefetched
7. Lyrics and Up-Next transitions feel smooth
8. Large libraries remain responsive
9. Settings survive restart
10. Base IanPlayer layout remains fully usable
11. New layouts can be added without rewriting playback logic

---

## 22. Product North Star

> **"My music player, my layout, my atmosphere."**

Differentiator = playback reliability + customizable presentation + immersive Canvas + smooth motion + personal control.

---

# PART II — TECHNICAL TRACE (Convx → IanPlayer)

---

## TRACE-001 — Player Layout Registry

**Convx reference:** `ui/player/PlayerLayoutRegistry.kt` — registry with default order, serialized order, deserialization, hidden slots, stale preference recovery.

**IanPlayer current:** `ui/NowPlayingScreen.kt` is one large fixed layout (~1,020 LOC).

**Target:**
```
ui/player/PlayerLayoutRegistry.kt
ui/player/PlayerSlot.kt
ui/player/PlayerLayoutId.kt
```

Sections: `ALBUM_ART, CANVAS, TRACK_INFO, SEEK_BAR, CONTROLS, ACTION_ROW, LYRICS, UP_NEXT, OUTPUT, EXTRA_INFO`

**Priority:** P0

---

## TRACE-002 — Four Player Themes

User selects: Base IanPlayer, YouTube Music, Spotify, Apple Music

```kotlin
enum class PlayerLayoutId {
    BASE_IAN, YOUTUBE_MUSIC, SPOTIFY, APPLE_MUSIC
}
```

Interaction/design-inspired presets, NOT copies of proprietary assets.

**Priority:** P0

---

## TRACE-003 — Now Playing Migration

**Source:** `ui/NowPlayingScreen.kt` (~1,020 LOC)

Existing useful systems: hero artwork animation, `Animatable`, palette transition, drag-to-dismiss, lyrics/UpNext expansion, Canvas integration, Wave Record, favorite, playback controls.

**Target decomposition:**
```
PlayerHost
BaseIanPlayerLayout
PlayerArtwork | PlayerTrackInfo | PlayerSeekBar
PlayerControls | PlayerActionRow | PlayerLyrics
PlayerUpNext | PlayerCanvasHost
```

---

## TRACE-004 — Mini Player

**IanPlayer current:** `MainActivity.kt` — Default/Floating/Compact, swipe up → full, swipe down → cycle, horizontal → volume, tap → full.

**Convx reference:** Dedicated `MiniPlayer.kt`, `FloatingMiniPlayer.kt`, `AppleMiniPlayer.kt`.

**Target:**
```
ui/player/MiniPlayerHost.kt
ui/player/mini/BaseMiniPlayer.kt
ui/player/mini/FloatingMiniPlayer.kt
ui/player/mini/CompactMiniPlayer.kt
ui/player/mini/AppleMiniPlayer.kt
```

Mini player should use same `PlayerArtwork` identity as full player for continuous morph.

---

## TRACE-005 — Artwork Morph

**IanPlayer current:** `NowPlayingScreen.kt` already has `initialAlbumArtRect`, `targetAlbumArtRect`, `heroAnimProgress`, `heroInitScale/OffsetX/OffsetY` — already close to target.

**Target:** Extract into `PlayerArtworkMorph.kt`

Flow: `Mini artwork → measure source rect → measure full player rect → spring/morph → full artwork` (reverse on dismissal).

---

## TRACE-006 — Canvas Architecture

**IanPlayer current:** `PlayerCanvas.kt` with GENERATIVE/VIDEO/KEN_BURNS, video resolution via `CanvasVideoSource.resolve()`, local search paths.

**Convx reference:** Dedicated Canvas module, `PlayerV2Canvas.kt` — checks enabled, resolves source, builds cache key, fetches async, runs provider fallback, caches result.

**Target:**
```
data/canvas/
  CanvasArtwork.kt | CanvasProvider.kt | CanvasManager.kt
  CanvasCache.kt | CanvasRepository.kt

data/canvas/providers/
  LocalCanvasProvider.kt | AppleMusicCanvasProvider.kt
  TidalCanvasProvider.kt | ViviCanvasProvider.kt
  RemoteCanvasProvider.kt | GenerativeCanvasProvider.kt
```

---

## TRACE-007 — Canvas Provider Priority

```
AUTO
 ├── Local cache
 ├── Apple-style provider
 ├── Tidal-style provider
 ├── Vivi-style provider
 ├── Remote provider
 ├── Ken Burns
 └── Aurora
```

Provider failure → advance to next fallback. Never throw playback-fatal error for Canvas missing.

---

## TRACE-008 — Canvas Cache

```
CanvasCacheKey(songId, source)
```

Disk: `cacheDir/canvas/<source>/<songId>.mp4`  
Metadata: `songId, source, remoteUrl, localPath, createdAt, expiresAt`

Requirements: LRU, max disk size, stale cleanup, duplicate prevention, background prefetch, cancellation.

---

## TRACE-009 — Canvas Prefetch

```
Current song → resolve Canvas
Next song    → prefetch Canvas metadata
Next + 1     → optional low-priority prefetch
```

Do not download huge Canvas files indiscriminately.

---

## TRACE-010 — Canvas Playback Isolation

Rules: `volume = 0`, no audio focus, no noisy-audio, release when leaving player, release/reuse on song change, no second notification/MediaSession, Canvas state follows primary player.

---

## TRACE-011 — Aurora Canvas

Current: radial gradients, animated blobs, pulsing ring, dust particles, vignette, album palette, playback progress, animation speed by playing state — already good.

Add: `intensity, speed, particleCount, blur, vignette, reactivity`. Reduce particles on low-end.

---

## TRACE-012 — Ken Burns

Current: zoom/pan animation, album art, ambient color — keep. Move behind `CanvasProvider` as fallback.

---

## TRACE-013 — Liquid Glass

**Convx reference:** `GlassEffect.kt` — configurable: enabled, vibrancy, blur radius, lens height, chromatic aberration, depth, tint, highlight. Uses `rememberLayerBackdrop()` → `Modifier.layerBackdrop()` → `drawBackdrop()`.

**IanPlayer target:**
```
IanGlassConfig | IanGlassSurface | IanGlassButton | IanGlassSheet
```

Start with safe fallback: translucent surface + blur where supported + tint + highlight. Real refraction only where device supports it.

---

## TRACE-014 — Glass Configuration

Persist: `glass_enabled, glass_blur, glass_vibrancy, glass_lens, glass_highlight, glass_tint`

Advanced controls only under: `Settings → Appearance → Liquid Glass`

---

## TRACE-015 — Squiggly/Wavy Seek Bar

**Convx reference:** `SquigglySlider.kt` — Compose Canvas, frame-driven phase, custom path, cubic curves, active/inactive clipping.

**Target:**
```
PlayerSeekBarStyle: CLASSIC | WAVE | SQUIGGLE | MINIMAL
```

Animation stops/reduces when paused.

---

## TRACE-016 — Lyrics

**IanPlayer current:** `LyricRepository.kt` + `NowPlayingScreen.kt` — synced/plain lyrics, source cycling, selected lines, lyric card.

**Target:** Extract `PlayerLyrics, LyricsPresentation, LyricsStyle`. Add active line, word highlight, full-screen, translation, smooth scrolling, reduced-motion behavior.

---

## TRACE-017 — Up Next

**IanPlayer current:** reorder, drag handle, animated elevation, responsive list, play-on-tap.

**Improvements:** stable identity, spring reorder, current-track marker, source/download badges, animated insert/remove, swipe actions.

Do not rewrite queue logic until UI extraction complete.

---

## TRACE-018 — Player Motion

Standardize across all layouts:
```
PlayerMotion.fast | .standard | .sheet | .spring | .morph | .palette | .canvas
```

Do not scatter raw `tween(...)` / `spring(...)` across screens.

---

## TRACE-019 — Palette Transition

**IanPlayer current:** `prevPaletteBlend`, `nextPaletteBlend`, `paletteTransitionProgress` with ~1.4s transition — keep.

Extract: `AmbientPaletteController` — extract palette, calculate readable colors, transition palette, expose stable UI colors.

---

## TRACE-020 — Settings Migration

Current `SettingsStore` — add typed fields:
```
player_layout = BASE_IAN
mini_player_style = BASE
canvas_source = AUTO
canvas_enabled = true
motion_scale = 1.0
reduce_motion = false
crossfade_duration_ms = 0
```

Old Canvas integers remain readable for backward compatibility.

---

## TRACE-021 — Performance Audit Targets

```
MainActivity | MusicViewModel | SongListComponents
NowPlayingScreen | PlayerCanvas | AlbumArtLoader
MusicRepository | YTMusicRepository
```

Look for: `collectAsState()` scope, `remember(...)` correctness, `derivedStateOf(...)` opportunities, `LazyColumn` keys, `LaunchedEffect`/`DisposableEffect` lifecycle.

---

## TRACE-022 — MainActivity Refactor (~4,100 LOC)

Extract in order:
```
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

## TRACE-023 — MusicViewModel Refactor (~1,386 LOC)

Future decomposition:
```
PlaybackViewModel | QueueViewModel | LibraryViewModel
SearchViewModel | LyricsViewModel | CanvasViewModel
RecapViewModel | SettingsViewModel
```

Later optimization. First extract UI responsibilities without changing business logic.

---

## TRACE-024 — Player UI State

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

Every player layout renders from the same state contract.

---

## TRACE-025 — Layout Switching Flow

```
Settings → SettingsStore.playerLayout → MainActivity/ViewModel
  → PlayerHost → PlayerLayoutRegistry.resolve(id)
  → Selected PlayerLayout → PlayerUiState
```

No playback restart.

---

## TRACE-026 — Track Change Flow

```
Media3 currentMediaItem changes
  → PlayerGateway → MusicViewModel → PlayerUiState
  → ArtworkController (load + palette extraction)
  → AmbientPaletteController
  → CanvasManager (cache → provider → fallback)
  → LyricsRepository → Player layout rebind
```

Visual transitions independent from audio state.

---

## TRACE-027 — Canvas Visual Layer

```
Root
 ├── base adaptive background
 ├── Canvas (video/generative/Ken Burns + tint)
 ├── readability scrim
 ├── optional glass surface
 ├── artwork / controls
 ├── lyrics / queue
 └── sheets
```

Canvas not a visual dependency of every component.

---

## TRACE-028 — Battery / Device Adaptation

```
VisualPerformanceTier
 ├── HIGH: full Canvas, glass, particles, 120Hz
 ├── BALANCED: reduced particles, moderate blur
 └── LOW: Ken Burns/static, minimal blur, no particles
```

---

## TRACE-029 — Reduced Motion

When enabled:
```
spring → tween
infinite animation → static
artwork morph → fade/scale
Canvas → low-motion/static
lyrics → instant/short scroll
```

Global setting.

---

## TRACE-030 — QA Trace Matrix

> Status diperbarui per sesi implementasi 2026-09-07 (progress == status saat ini).

| Feature | Current | Target | Priority | Status |
|---|---|---|---|---|
| Base player | Yes | Preserve | P0 | ✅ Done |
| YT Music layout | No | Add | P0 | ✅ Done |
| Spotify layout | No | Add | P0 | ✅ Done |
| Apple layout | No | Add | P0 | ✅ Done |
| Mini layouts | Yes | Refactor | P0 | ✅ Glass-aware |
| Artwork morph | Yes | Extract/improve | P0 | ✅ Fix loop |
| Canvas Aurora | Yes | Provider | P0 | ✅ Registry |
| Canvas Video | Yes | Cache/provider | P0 | 🟡 Registry ada, cache belum penuh |
| Canvas Ken Burns | Yes | Fallback | P1 | ✅ Registry |
| Canvas remote | Limited | Provider | P1 | ⛔ Backlog |
| Canvas cache | Limited | Full cache | P0 | ⛔ Backlog |
| Glass | No/limited | Optional | P1 | ✅ Done + tier gate |
| Squiggly seek | No | Add | P1 | ⛔ Backlog |
| Synced lyrics | Yes | Improve | P1 | 🟡 Smooth highlight, word-level ⛔ |
| Up Next reorder | Yes | Improve | P1 | 🟡 Partial |
| Player sections | No | Add | P0 | 🟡 4 layout, section ordering ⛔ |
| Layout persistence | No | Add | P0 | ✅ |
| Motion settings | No | Add | P1 | ✅ reduceMotion/motionScale |
| Reduce motion | No | Add | P1 | 🟡 Parsial penerapan |
| Performance tiers | No | Add | P1 | ✅ `VisualTier` (glass-gate) |

---

## TRACE-031 — Target Project Structure

```
com.ianocent.musicplayer/

data/
  canvas/
    CanvasArtwork.kt | CanvasProvider.kt | CanvasManager.kt
    CanvasCache.kt | CanvasRepository.kt

ui/
  player/
    PlayerHost.kt | PlayerUiState.kt | PlayerLayout.kt
    PlayerLayoutId.kt | PlayerLayoutRegistry.kt
    PlayerSlot.kt | PlayerMotion.kt

    layouts/
      BaseIanPlayerLayout.kt | YoutubeMusicLayout.kt
      SpotifyInspiredLayout.kt | AppleMusicInspiredLayout.kt

    components/
      PlayerArtwork.kt | PlayerArtworkMorph.kt
      PlayerTrackInfo.kt | PlayerSeekBar.kt
      PlayerControls.kt | PlayerActionRow.kt
      PlayerLyrics.kt | PlayerUpNext.kt
      PlayerOutput.kt | PlayerCanvasHost.kt

    mini/
      MiniPlayerHost.kt | BaseMiniPlayer.kt
      FloatingMiniPlayer.kt | CompactMiniPlayer.kt
      AppleMiniPlayer.kt

  canvas/
    PlayerCanvas.kt | CanvasMode.kt
    GenerativeCanvas.kt | KenBurnsCanvas.kt | VideoCanvas.kt

  glass/
    IanGlass.kt | IanGlassConfig.kt

  lyrics/
    PlayerLyrics.kt | LyricsStyle.kt

  motion/
    MotionTokens.kt
```

---

## TRACE-032 — Reference Files Worth Studying

**Convx:**
- `ui/player/PlayerLayoutRegistry.kt`, `PlayerV2Canvas.kt`, `PlayerArtworkMorph.kt`
- `ui/player/PlayerContainerMorph.kt`, `PlayerMorphLayers.kt`, `PlayerSheetMotion.kt`
- `ui/player/MiniPlayer.kt`, `FloatingMiniPlayer.kt`, `AppleMiniPlayer.kt`
- `ui/component/GlassEffect.kt`, `SquigglySlider.kt`, `WaveformSeekBar.kt`
- `playback/MusicService.kt`, `PlayerConnection.kt`
- Canvas module providers

**IanPlayer:**
- `MainActivity.kt`, `ui/NowPlayingScreen.kt`, `ui/canvas/PlayerCanvas.kt`
- `data/SettingsStore.kt`, `viewmodel/MusicViewModel.kt`
- `player/PlayerGateway.kt`, `player/PlaybackService.kt`
- `data/YTMusicRepository.kt`, `data/LyricRepository.kt`
- `data/AlbumArtLoader.kt`, `data/MusicRepository.kt`

---

## Architecture Diagram

```
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

**Key idea:** Playback boring and reliable. Presentation expressive and replaceable. Canvas beautiful but optional. Settings control the experience.

---

# PART III — MILESTONES & ROADMAP

> **2026-09-07 update:** M0–M6 inti (layout registry, 4 layout, uiState unifikasi, motion dasar, crossfade + overlap, glass) sudah dieksekusi & build green. Core fitur roadmap sudah ada. Sisanya backlog/perf/polish.

---

## M0 — Baseline Lock & Architecture Prep

**Goal:** Freeze known-good baseline + prepare structure for layout system.

### Tasks
- [ ] Build current project in Debug
- [ ] Verify: local playback, YT Music playback, queue persistence, downloads, lyrics, Canvas modes, Wave Record, mini-player layouts
- [ ] Capture screenshots/video: Home, Songs, Stream, Playlist, Mini-player, Now Playing, Lyrics, Up-Next, Settings, Canvas
- [ ] Record current APK version and Git commit
- [ ] Audit `NowPlayingScreen.kt` → split into:
  - `NowPlayingShell.kt` (host: state hoisting, gesture, shared logic)
  - `layouts/BaseIanPlayerLayout.kt` (extract existing UI)
  - Folder `ui/nowplaying/layouts/`
- [ ] Add `PlayerLayoutMode` enum (`BASE_IANPLAYER, YT_MUSIC, SPOTIFY, APPLE_MUSIC`) + `playerLayoutMode: Int` in `SettingsStore.kt`
- [ ] Add Settings UI: "Player Layout" dropdown, persist via `SettingsStore`
- [ ] Verify build green, behavior default (BASE_IANPLAYER) identical to old UI

**Exit:** App runs normally, setting exists but doesn't affect UI yet (stub).

**Branch:** `feature/player-system`

---

## M1 — Motion & Transition System

**Goal:** Smooth transitions independent from layout feature (immediate feel improvement).

### Tasks
- [ ] Ganti animasi mini ↔ full player: `animateFloatAsState(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))`
- [ ] `AnimatedContent`/`Crossfade` for tab switching (Songs/Albums/Stream/Playlists)
- [ ] Album art + title crossfade on track change (not snap)
- [ ] Light overscroll bounce di list utama (optional)
- [ ] Standardize `PlayerMotionTokens`: fast, standard, expressive, spring, artworkMorph, sheet, palette, canvas
- [ ] Add: Reduce Motion, Animation Scale settings
- [ ] Remove redundant infinite animations
- [ ] Stop heavy effects when paused/invisible
- [ ] Add reduced-motion fallback

**Exit:** Buka/tutup full player & ganti tab terasa smooth. No scroll regression.

**Branch:** `feature/player-motion`

---

## M2 — Canvas System Extension

**Goal:** Expand Canvas into provider/cache architecture connected to each layout.

### Tasks
- [ ] Define `LayoutCanvasStyle` as default background per `PlayerLayoutMode`
- [ ] Implement blur/ambient background from artwork (Palette API → gradient, extend to blur-lite via `RenderEffect` API 31+, fallback gradient below)
- [ ] Create `CanvasArtwork` model + `CanvasProvider` interface
- [ ] Implement `CanvasManager` with provider priority chain
- [ ] Implement `CanvasCache` (metadata + disk, LRU, stale detection)
- [ ] Background prefetch + cancellation on song change
- [ ] Canvas failure → fallback to next provider, never block audio
- [ ] Performance test on low-end devices

**Exit:** Each layout has distinct background. Toggle-able. No crash on old API levels.

**Branch:** `feature/canvas-2`

---

## M3 — 4 Player Layouts (UI Implementation)

**Goal:** Visual implementation for each layout option.

### Tasks
- [ ] `BaseIanPlayerLayout` — finalize extraction from M0 (no visual change, baseline)
- [ ] `YtMusicLayout` — full-bleed artwork, minimal controls below, ambient blur background, swipeable lyrics/queue tabs
- [ ] `SpotifyLayout` — artwork card with shadow, thin seekbar, dominant-color flat gradient background, like/Spotify-style controls
- [ ] `AppleMusicLayout` — rounded artwork with scale animation on play/pause, animated gradient mesh, bold typography, Apple-style lyrics
- [ ] Wire all four to `NowPlayingShell` via `when(playerLayoutMode)`
- [ ] Settings preview thumbnail per layout (optional)

**Exit:** User switches 4 layouts from Settings. No playback interruption. All state consistent across layouts.

**Branch:** `feature/player-base`, `feature/player-youtube`, `feature/player-spotify`, `feature/player-apple`

---

## M4 — Mini-Player → Full Player Morph

**Goal:** Expansion feels like same object growing into full player.

### Tasks
- [ ] Shared artwork bounds
- [ ] Artwork scale/translation morph
- [ ] Mini-player container morph
- [ ] Background morph
- [ ] Title transition
- [ ] Progress transition
- [ ] Gesture cancellation handling
- [ ] Swipe-down reverse transition

**Exit:** No flash/white frame/jump during open/close.

---

## M5 — Crossfade & Smooth Playback

**Goal:** Enjoyable audio transitions, not just visual.

### Tasks
- [ ] Add `crossfadeDurationMs` setting (0 = off, default) in `SettingsStore`
- [ ] Modify `PlaybackService`/`PlayerGateway`: overlap volume-fade two ExoPlayer instances near track end
- [ ] Test: manual skip during crossfade, radio/stream tracks, last track in queue (no crossfade, natural stop)
- [ ] Settings structure: crossfade → auto-mix → creative transition (dependency chain from Convx UX pattern)

**Exit:** Crossfade smooth, no gap/double audio, toggleable, no break to queue/notification controls.

**Branch:** `feature/crossfade`

---

## M6 — Glass Surface System

**Goal:** Controlled glass surfaces inspired by Convx.

### Tasks
- [ ] Create `IanGlassSurface`, `IanGlassButton`, `IanGlassSheet`, `IanGlassMiniPlayer`
- [ ] Config: `enabled, blur, vibrancy, lens, highlight, tint`
- [ ] Apply to: mini-player, floating controls, queue, lyrics, action row, settings cards
- [ ] Low-end fallback: no expensive refraction

**Exit:** Glass surfaces work on high-end, graceful degradation on low-end.

**Branch:** `feature/glass-system`

---

## M7 — Seek Bar 2.0

**Goal:** Responsive, animated seek experience.

### Tasks
- [ ] Implement styles: Classic, Wave, Squiggly, Minimal
- [ ] Tap seek, drag seek, accurate position
- [ ] Buffer indicator, wave animation
- [ ] Haptic feedback, reduced-motion fallback

**Exit:** Seeking responsive while waveform animated.

---

## M8 — Lyrics 2.0

### Tasks
- [ ] Active-line animation
- [ ] Word-level highlighting (where source supports)
- [ ] Smooth auto-scroll
- [ ] Full-screen lyrics mode
- [ ] Lyrics style presets
- [ ] Translation slot
- [ ] Better loading state
- [ ] Seek on lyric tap, lyric selection

**Exit:** Lyrics never visibly jump on playback position update.

**Branch:** `feature/lyrics-2`

---

## M9 — Up-Next 2.0

### Tasks
- [ ] Spring reorder
- [ ] Current song indicator
- [ ] Animated insert/remove
- [ ] Stable keys + queue virtualization
- [ ] Swipe actions, drag feedback

---

## M10 — Performance Pass

### Audit
- [ ] Compose recompositions
- [ ] `collectAsState` scope
- [ ] Stable keys
- [ ] Image decode threading
- [ ] Palette extraction threading
- [ ] Canvas draw calls
- [ ] ExoPlayer lifecycle
- [ ] Room queries + JSON writes
- [ ] Network calls, cache duplication

### Targets
- 60 FPS baseline
- No jank during player open
- No jank during queue reorder
- No audio interruption during Canvas resolution

**Branch:** `feature/performance`

---

## M11 — QA & Regression

### Matrix

**Playback:** Local MP3, M4A, FLAC, WAV, OGG, YT Music, Cached stream, Downloaded stream

**Player:** Base, YT Music, Spotify, Apple Music

**Canvas:** Off, Generative, Ken Burns, Local video, Remote Canvas, Missing Canvas, Broken URL

**Devices:** Android 7/8+, low RAM, 60Hz/90Hz/120Hz, portrait/landscape

### Regression
- [ ] Full regression: streaming, download, existing features (voice assistant, wave record, recap, social signal)
- [ ] Battery/perf sanity with canvas+crossfade+animation active together

---

## M12 — Release Hardening

- [ ] Baseline profile
- [ ] R8 rules
- [ ] Crash audit + ANR audit
- [ ] Startup timing + memory audit
- [ ] Network timeout audit
- [ ] Canvas cache size audit
- [ ] Settings migration
- [ ] Update README/CHANGELOG, credit Convx/vivi-music in docs (not source)

---

## M13 (Future) — Tablet / Large Screen

- [ ] Width-aware player, two-column layout
- [ ] Persistent queue pane
- [ ] Landscape-specific controls
- [ ] Glass surfaces optimized for wide screens

---

## Priority Order (limited time)

1. **M0** — mandatory foundation
2. **M1** — immediate "smooth" impact, low effort
3. **M2 + M3** — core feature (layout switcher + canvas)
4. **M4** — mini/full morph
5. **M5** — crossfade (more complex in playback service)
6. **M8** — lyrics stretch goal

---

## Branch Strategy

```
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

**Rule: Never redesign the UI while the playback engine is unstable.**

---

## Out of Scope (Backlog)

| Feature | Reason |
|---|---|
| DIY Player Editor (drag stickers/icons) | High complexity, not "smooth & enjoyable" priority |
| Listen Together | Needs realtime infra, large scope |
| Discord Rich Presence | Social add-on, not core |
| Tempo-matched Auto-DJ | Backlog — MVP is volume-based crossfade only |
| Tablet-optimized layout | Phone-first for now |
| Real backdrop blur (Kyant0/backdrop) | Too heavy for low-end without opt-in tier |

---

*Consolidated from 9 reference documents (ChatGPT, Claude, Gemini analyses). All adoption is clean-room rewrite — Convx GPL-3.0 not compatible for copy-paste. Implementation follows IanPlayer's own code style and architecture.*
