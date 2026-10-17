# IanPlayer Optimization — Phase Execution Plan

**Base doc:** `IANPLAYER_OPTIMIZATION_UNIFIED.md`  
**Rule:** Never redesign UI while playback engine unstable. Each phase must build green + pass manual QA before next.

---

# PROGRESS BOARD (2026-09-07)

> Update sesi implementasi. `[x]` = selesai & build user green. Fase di bawah yang sama nomornya dengan nomor heading.

| Phase | Area | Status |
|---|---|---|
| Phase 0 | Baseline & scaffold | [x] struktur `ui/player/**` berdiri |
| Phase 1 | Motion tokens | [x] `MotionTokens.kt` + reduceMotion/motionScale setting |
| Phase 2 | Extracted components | [x] PlayerSeekBar, PlayerControlsBar, Lyric/Upnext section header |
| Phase 3 | PlayerUiState unifikasi | [x] `rememberPlayerUiState()` 1-titik collect 20 flow; 26/09/07 di-pecah jadi 5 slice (playback/visuals/lyrics/prefs/nav) |
| Phase 4 | YT Music layout | ↩ DIHAPUS 07/09 — user putuskan single-layout BASE. File `YTMusicPlayerLayout.kt` dihapus; `PlayerLayoutId/Registry` + settings chips + `setPlayerLayout` flow dicabut |
| Phase 5 | Spotify layout | ↩ DIHAPUS 07/09 — `SpotifyPlayerLayout.kt` dihapus (lihat Phase 4) |
| Phase 6 | Apple layout + backdrop | ↩ DIHAPUS 07/09 — `AppleMusicPlayerLayout.kt` dihapus; `PlayerBackdrop` shared TETAP dipakai by BASE |
| Phase 7 | Glass surface system | [x] IanGlass infra + mini/pill/action-row + perf-tier gate. 07/09: glass diperluas — home bg ikut adaptive color, tabs + container + mini player glass, UpNext + Controls glass |
| — | Crossfade sequential | [x] `easeFade` smoothstep + setting Off/0.3/0.7/1.2s |
| — | Crossfade overlap (M5) | [x] dual-ExoPlayer overlap + handoff + guards |
| — | Bug fix trio (04) | [x] matchParentSize, hero morph loop, fetchHomeSongs silent fail |
| — | Smooth lyric highlight | [x] tween 350ms active line |
| — | Motion tokens konsisten (04 §4.4/4.5) | [x] `scaledTween()` 1-titik; hero/section/lyric/shimmer/palette/drag spring semua respect reduceMotion+motionScale |
| — | Canvas provider chain (04 §4.3) | [x] `CanvasProvider.resolveFor()` chain VIDEO→GENERATIVE; hapus hardcode di `PlayerBackdrop` |
| — | Glass merata (07/09) | [x] root bg = adaptive color album art; tabs glass capsule; container card glass; mini player glass; NowPlayingScreen UpNext + Controls (PlayerControlsBar) glass |
| Phase 12–14 | Perf/QA/release | ⏳ manual — QA visual glass baru |

**Real yang next:** QA visual glass merata (blur/refraksi di device). Sisa backlog: real backdrop sampling (04 §4.2) → milestone sendiri. `DuotoneArtwork` DIHAPUS 07/09 (tak terpakai). Doc 05 §6/§7 (lyrics styling, nav bar floating) bisa lanjut tapi low priority. Layout alternatif (YT/Spotify/Apple) DIHAPUS permanen — arsitektur BASE-only.

---

## PHASE 0 — Baseline Lock & Project Scaffold

**Goal:** Freeze current state. Create folder structure. Prep for layout system.  
**Branch:** `main` → tag `pre-optimization-baseline`  
**Duration estimate:** 1 session  
**Dependencies:** None

### 0.1 — Snapshot Current State
- [ ] Build APK Debug, install on device
- [ ] Record APK versionCode/versionName, current Git SHA
- [ ] Screenshot + video capture all screens:
  - Home, Songs, Albums, Stream, Playlists
  - Mini-player (Default, Floating, Compact)
  - Now Playing (with lyrics, with Canvas, with Up-Next)
  - Settings
  - Wave Record
- [ ] Test matrix (5 min each):
  - [ ] Play local MP3/M4A/FLAC
  - [ ] Play YT Music stream
  - [ ] Queue: add, reorder, remove
  - [ ] Download a track
  - [ ] Lyrics load
  - [ ] Canvas modes: Off, Generative, Video, Ken Burns
  - [ ] Mini-player swipe gestures
  - [ ] Favorites toggle
- [ ] Tag Git commit: `baseline/pre-optimization-v6.2.5`

### 0.2 — Create Target Folder Structure
```
ui/player/                          ← NEW
ui/player/layouts/                  ← NEW
ui/player/components/               ← NEW
ui/player/mini/                     ← NEW
data/canvas/                        ← NEW
data/canvas/providers/              ← NEW
ui/canvas/                          ← EXISTS (rename existing contents)
ui/glass/                           ← NEW
ui/lyrics/                          ← NEW
ui/motion/                          ← NEW
```

- [ ] Create all directories
- [ ] Add placeholder `package-info.kt` or README per directory (optional, just for structure)

### 0.3 — SettingsStore Expansion (Stub)
**File:** `data/SettingsStore.kt`

- [ ] Add enum:
  ```kotlin
  enum class PlayerLayoutMode(val persisted: Int) {
      BASE_IANPLAYER(0),
      YT_MUSIC(1),
      SPOTIFY(2),
      APPLE_MUSIC(3);
      
      companion object {
          fun fromInt(value: Int) = entries.firstOrNull { it.persisted == value } ?: BASE_IANPLAYER
      }
  }
  ```
- [ ] Add property: `var playerLayoutMode: PlayerLayoutMode` (read/write to SharedPrefs)
- [ ] Add stub fields (with defaults, no UI yet):
  ```kotlin
  var miniPlayerStyle: Int = 0      // Base/Floating/Compact
  var canvasSource: Int = 0         // AUTO
  var canvasEnabled: Boolean = true
  var motionScale: Float = 1.0f
  var reduceMotion: Boolean = false
  var glassEnabled: Boolean = false
  var glassBlur: Float = 16f
  var glassVibrancy: Float = 0.3f
  var seekBarStyle: Int = 0         // CLASSIC
  var lyricsStyle: Int = 0          // DEFAULT
  var crossfadeDurationMs: Int = 0  // OFF
  ```
- [ ] Backward compatible: existing `canvasMode` (Int) still works, new enum reads from same key

### 0.4 — Build Verification
- [ ] Project builds clean
- [ ] All existing functionality works identically
- [ ] No new warnings in logcat
- [ ] Git commit: `chore: scaffold folder structure + expand SettingsStore stubs`

**Exit Criteria:** Tagged baseline. All tests pass. Folder structure exists. SettingsStore has new fields (unused by UI).

---

## PHASE 1 — Motion System & Transition Tokens

**Goal:** Replace ad-hoc animations with centralized motion tokens. Immediate "smooth feel" upgrade.  
**Branch:** `feature/player-motion`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 0 complete

### 1.1 — Create Motion Tokens
**New file:** `ui/motion/MotionTokens.kt`

```kotlin
object MotionTokens {
    // Durations (ms)
    const val FAST = 150
    const val STANDARD = 250
    const val EXPRESSIVE = 400
    const val PALETTE = 1200
    const val CANVAS = 500

    // Spring specs
    val fastSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    val standardSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val morphSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val sheetSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Easing
    val fastOutSlowIn = FastOutSlowInEasing
    val easeInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // Combined specs for common uses
    val smallUiChange = tween<Float>(FAST, easing = fastOutSlowIn)
    val playerSection = tween<Float>(STANDARD, easing = fastOutSlowIn)
    val artworkMorph = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val paletteTransition = tween<Float>(PALETTE, easing = easeInOut)
    val canvasFade = tween<Float>(CANVAS, easing = easeInOut)
}
```

- [ ] Implement `MotionTokens` object
- [ ] Add `reduceMotionScale` computed from `SettingsStore.reduceMotion` + `SettingsStore.motionScale`
- [ ] Add reduced-motion variants that replace spring → short tween, disable infinite animations

### 1.2 — Create AmbientPaletteController
**New file:** `ui/motion/AmbientPaletteController.kt`

Extract from `NowPlayingScreen.kt` the palette transition logic:
- [ ] Extract `prevPaletteBlend`, `nextPaletteBlend`, `paletteTransitionProgress`
- [ ] Create `AmbientPaletteController` composable/state class
- [ ] Input: `currentSong`, `previousSong`
- [ ] Output: `ambientColor`, `paletteColors`, `gradientBrush`
- [ ] Use `MotionTokens.paletteTransition` for duration
- [ ] Keep existing Palette API usage, just move to dedicated class

### 1.3 — Upgrade Mini ↔ Full Player Animation
**File:** `MainActivity.kt` (target: mini-player section)

- [ ] Find current mini-player → full-player transition logic
- [ ] Replace tween/linear with `MotionTokens.standardSpring`
- [ ] Ensure: no white frame flash, no position jump, smooth scale + translate
- [ ] Add gesture cancellation handling (if user reverses mid-swipe)

### 1.4 — Tab Switch Transitions
**File:** `MainActivity.kt` (target: tab navigation)

- [ ] Replace instant tab swap with `AnimatedContent` + slide + fade
- [ ] Use `MotionTokens.fastOutSlowIn` for tab transitions
- [ ] Test: no scroll position loss in song list after tab switch

### 1.5 — Track Change Visual Transition
**File:** `ui/NowPlayingScreen.kt`

- [ ] Find album art + title update on track change
- [ ] Add crossfade (not snap) for artwork using `animateFloatAsState`
- [ ] Add crossfade for title/artist text
- [ ] Duration: `MotionTokens.fast` (150-200ms)
- [ ] Ensure no audio interruption

### 1.6 — Settings UI for Motion
**File:** Settings screen

- [ ] Add toggle: "Reduce Motion" → `SettingsStore.reduceMotion`
- [ ] Add slider: "Animation Scale" → `SettingsStore.motionScale`
- [ ] Both apply globally via `MotionTokens.reduceMotionScale`

### 1.7 — Cleanup
- [ ] Search codebase for scattered `tween(...)` / `spring(...)` in player-related files
- [ ] Replace raw specs with `MotionTokens.*` references (gradual, not all at once)
- [ ] Remove redundant infinite animations in `NowPlayingScreen.kt`
- [ ] Stop heavy visual effects when `isPlaying == false`

### 1.8 — Verification
- [ ] Build clean
- [ ] Mini ↔ full player: smooth, no flash
- [ ] Tab switching: smooth slide+fade
- [ ] Track change: artwork + title crossfade
- [ ] Reduce Motion toggle: all animations simplify
- [ ] Scroll performance: no regression in long lists
- [ ] Git commit: `feat: motion system + transition tokens + AmbientPaletteController`

**Exit Criteria:** All transitions feel smooth. MotionTokens used consistently. Reduce Motion works. No scroll/perf regression.

---

## PHASE 2 — Player Architecture Extraction

**Goal:** Break monolithic `NowPlayingScreen.kt` into host + components. Create PlayerUiState contract.  
**Branch:** `feature/player-registry`  
**Duration estimate:** 3-4 sessions  
**Dependencies:** Phase 0 complete (can run parallel with Phase 1)

### 2.1 — Define PlayerUiState
**New file:** `ui/player/PlayerUiState.kt`

```kotlin
data class PlayerUiState(
    val song: Song? = null,
    val albumArt: ImageBitmap? = null,
    val ambientColor: Color = Color.Unspecified,
    val paletteColors: List<Color> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isFavorite: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val queue: List<Song> = emptyList(),
    val lyrics: LyricsState = LyricsState(),
    val canvas: CanvasState = CanvasState(),
    val canvasMode: CanvasMode = CanvasMode.OFF,
    val playerLayoutMode: PlayerLayoutMode = PlayerLayoutMode.BASE_IANPLAYER
)

data class LyricsState(
    val lines: List<LyricLine> = emptyList(),
    val currentLineIndex: Int = -1,
    val isLoading: Boolean = false,
    val source: String = ""
)

data class CanvasState(
    val isActive: Boolean = false,
    val currentArtwork: CanvasArtwork? = null,
    val isLoading: Boolean = false
)
```

- [ ] Create `PlayerUiState.kt` with all fields
- [ ] Create `LyricsState.kt` and `CanvasState.kt` data classes
- [ ] Ensure immutability (all vals, no mutable state in data class)

### 2.2 — Create PlayerHost
**New file:** `ui/player/PlayerHost.kt`

PlayerHost is the top-level composable that:
- [ ] Hoists state from `MusicViewModel`
- [ ] Maps ViewModel state → `PlayerUiState`
- [ ] Handles gesture layer (drag-to-dismiss, swipe)
- [ ] Delegates to layout via `PlayerLayoutRegistry`
- [ ] Manages shared elements (artwork morph bounds)

```kotlin
@Composable
fun PlayerHost(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val playerUiState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val layoutMode = SettingsStore.playerLayoutMode
    
    // Gesture layer
    // Layout delegation
    // Shared element transitions
}
```

### 2.3 — Extract Shared Components from NowPlayingScreen
**New files in `ui/player/components/`:**

**2.3.1 — PlayerArtwork.kt**
- [ ] Extract album art rendering (currently in NowPlayingScreen)
- [ ] Accept: `albumArt: ImageBitmap?`, `size: Dp`, `shape: RoundedCornerShape`
- [ ] Handle: loading state, placeholder, error state
- [ ] Keep Coil usage from existing code
- [ ] Export `albumArtBounds: IntSize` for morph system

**2.3.2 — PlayerTrackInfo.kt**
- [ ] Extract song title + artist rendering
- [ ] Accept: `song: Song?`, `textStyle: TextStyle`
- [ ] Crossfade animation on song change (use `MotionTokens.fast`)

**2.3.3 — PlayerSeekBar.kt**
- [ ] Extract current seek bar (standard Slider for now)
- [ ] Accept: `position: Long`, `duration: Long`, `isBuffering: Boolean`, `onSeek: (Long) -> Unit`
- [ ] Show elapsed/remaining time
- [ ] Buffer indicator overlay

**2.3.4 — PlayerControls.kt**
- [ ] Extract play/pause, previous, next, shuffle, repeat
- [ ] Accept callbacks: `onPlayPause`, `onPrevious`, `onNext`, `onShuffle`, `onRepeat`
- [ ] Accept state: `isPlaying`, `shuffleEnabled`, `repeatMode`
- [ ] Keep existing icon choices

**2.3.5 — PlayerActionRow.kt**
- [ ] Extract favorite, lyrics toggle, queue toggle, share, download buttons
- [ ] Accept callbacks for each action
- [ ] Accept state: `isFavorite`, `isDownloaded`

**2.3.6 — PlayerLyrics.kt**
- [ ] Extract lyrics section (expansion/collapse)
- [ ] Accept: `lyricsState: LyricsState`, `onSeekToLine: (Int) -> Unit`
- [ ] Keep existing scroll behavior for now (enhancement in Phase 7)

**2.3.7 — PlayerUpNext.kt**
- [ ] Extract Up-Next queue section
- [ ] Accept: `queue: List<Song>`, `currentIndex: Int`, callbacks
- [ ] Keep existing reorder logic

**2.3.8 — PlayerCanvasHost.kt**
- [ ] Extract Canvas rendering from NowPlayingScreen
- [ ] Accept: `canvasState: CanvasState`, `canvasMode: CanvasMode`
- [ ] Delegate to existing `PlayerCanvas.kt`

### 2.4 — Create BaseIanPlayerLayout
**New file:** `ui/player/layouts/BaseIanPlayerLayout.kt`

- [ ] Recreate current NowPlayingScreen composition using extracted components
- [ ] Order: Artwork → TrackInfo → SeekBar → Controls → ActionRow → Lyrics/UpNext → Canvas
- [ ] All state comes from `PlayerUiState` (no direct ViewModel access)
- [ ] Keep existing gestures (drag-to-dismiss)
- [ ] Keep existing Canvas integration
- [ ] Visual must be IDENTICAL to current NowPlayingScreen

### 2.5 — Create PlayerLayoutId & Registry
**New files:**
- `ui/player/PlayerLayoutId.kt`
- `ui/player/PlayerLayoutRegistry.kt`

```kotlin
enum class PlayerLayoutId {
    BASE_IAN, YOUTUBE_MUSIC, SPOTIFY, APPLE_MUSIC
}

object PlayerLayoutRegistry {
    @Composable
    fun resolve(layoutId: PlayerLayoutId): @Composable (PlayerUiState, PlayerCallbacks) -> Unit {
        return when (layoutId) {
            PlayerLayoutId.BASE_IAN -> { state, callbacks -> BaseIanPlayerLayout(state, callbacks) }
            PlayerLayoutId.YOUTUBE_MUSIC -> { state, callbacks -> YoutubeMusicLayout(state, callbacks) }
            PlayerLayoutId.SPOTIFY -> { state, callbacks -> SpotifyInspiredLayout(state, callbacks) }
            PlayerLayoutId.APPLE_MUSIC -> { state, callbacks -> AppleMusicInspiredLayout(state, callbacks) }
        }
    }
}
```

- [ ] Implement `PlayerLayoutId` enum
- [ ] Implement `PlayerLayoutRegistry` with `resolve()`, `getAvailable()`, `getDefault()`
- [ ] Implement `PlayerCallbacks` data class (all action lambdas)

### 2.6 — Wire PlayerHost to NowPlayingScreen
**Files:** `MainActivity.kt`, `ui/NowPlayingScreen.kt`

- [ ] Replace direct NowPlayingScreen call with `PlayerHost`
- [ ] Inside `PlayerHost`, use `PlayerLayoutRegistry.resolve(layoutMode)`
- [ ] Pass `PlayerUiState` from ViewModel
- [ ] `NowPlayingScreen.kt` becomes thin wrapper → `PlayerHost`

### 2.7 — Settings UI for Layout Selection
**File:** Settings screen

- [ ] Add "Player Layout" dropdown/segmented control
- [ ] Options: Base IanPlayer, YouTube Music, Spotify, Apple Music
- [ ] Persist to `SettingsStore.playerLayoutMode`
- [ ] Test: switching layout doesn't restart playback

### 2.8 — Verification
- [ ] Build clean
- [ ] Base layout: visually identical to old NowPlayingScreen
- [ ] Layout switch in Settings: instant change, no playback interruption
- [ ] All controls work in Base layout
- [ ] Lyrics load, Up-Next reorder, Canvas modes all functional
- [ ] Git commit: `feat: player architecture extraction + layout registry`

**Exit Criteria:** NowPlayingScreen decomposed. PlayerHost + BaseIanPlayerLayout working. Layout switching functional. All existing features preserved.

---

## PHASE 3 — Canvas Provider Architecture

**Goal:** Transform Canvas from mode switch to provider/cache system with per-layout backgrounds.  
**Branch:** `feature/canvas-2`  
**Duration estimate:** 3-4 sessions  
**Dependencies:** Phase 0, Phase 1 (for motion tokens)

### 3.1 — Canvas Data Models
**New files in `data/canvas/`:**

**3.1.1 — CanvasArtwork.kt**
```kotlin
data class CanvasArtwork(
    val id: String,
    val stillImage: ImageBitmap? = null,
    val animatedUrl: String? = null,
    val source: CanvasSource,
    val duration: Long = 0L,
    val aspectRatio: Float = 16f/9f,
    val attribution: String = "",
    val cacheKey: String
)

enum class CanvasSource {
    LOCAL, APPLE, TIDAL, VIVI, REMOTE, GENERATIVE, KEN_BURNS
}
```

**3.1.2 — CanvasProvider.kt**
```kotlin
interface CanvasProvider {
    val source: CanvasSource
    val priority: Int
    
    suspend fun resolve(songId: String, song: Song): CanvasArtwork?
    suspend fun prefetch(songId: String, song: Song)
    fun cancel(songId: String)
}
```

**3.1.3 — CanvasCacheKey.kt**
```kotlin
data class CanvasCacheKey(
    val songId: String,
    val source: CanvasSource
)
```

### 3.2 — Canvas Cache
**New file:** `data/canvas/CanvasCache.kt`

- [ ] Metadata cache: `Map<CanvasCacheKey, CanvasMetadata>`
- [ ] Metadata: `songId, source, remoteUrl, localPath, createdAt, expiresAt`
- [ ] Disk cache: `cacheDir/canvas/<source>/<songId>.mp4`
- [ ] LRU eviction with max size (configurable, default 200MB)
- [ ] Stale detection: check `expiresAt` before using cached entry
- [ ] Duplicate prevention: in-memory map of in-flight requests
- [ ] `save()`, `get()`, `evict()`, `clearStale()`, `getDiskUsage()`

### 3.3 — Canvas Providers (Minimal Viable)
**New files in `data/canvas/providers/`:**

**3.3.1 — LocalCanvasProvider.kt**
- [ ] Search: `<song>.canvas.mp4`, `<song>.mp4`, `Music/IanCanvas/`
- [ ] Wrap existing `CanvasVideoSource.resolve()` logic
- [ ] Return `CanvasArtwork` with `CanvasSource.LOCAL`

**3.3.2 — GenerativeCanvasProvider.kt**
- [ ] Return `CanvasArtwork` with `CanvasSource.GENERATIVE`
- [ ] No network call — generative mode rendered locally
- [ ] Always succeeds (fallback)

**3.3.3 — KenBurnsProvider.kt**
- [ ] Return `CanvasArtwork` with `CanvasSource.KEN_BURNS`
- [ ] Uses album art for Ken Burns animation
- [ ] Always succeeds (fallback)

### 3.4 — CanvasManager
**New file:** `data/canvas/CanvasManager.kt`

```kotlin
class CanvasManager(
    private val providers: List<CanvasProvider>,
    private val cache: CanvasCache
) {
    suspend fun resolve(songId: String, song: Song): CanvasArtwork? {
        // 1. Check cache
        // 2. Iterate providers by priority
        // 3. First success → cache + return
        // 4. All fail → return null (non-blocking)
    }
    
    fun prefetchNext(nextSongId: String, nextSong: Song) {
        // Low-priority background fetch
    }
    
    fun cancelAll() {
        // Cancel in-flight requests
    }
}
```

- [ ] Implement priority chain resolution
- [ ] Cache-first lookup
- [ ] Provider timeout (3s default)
- [ ] Non-blocking: never throw, always return null on failure
- [ ] Prefetch for next track (low priority)

### 3.5 — Per-Layout Background Treatment
**New file:** `ui/player/components/PlayerBackground.kt`

```kotlin
@Composable
fun PlayerBackground(
    layoutMode: PlayerLayoutMode,
    albumArt: ImageBitmap?,
    paletteColors: List<Color>,
    canvasState: CanvasState
) {
    when (layoutMode) {
        BASE_IANPLAYER -> ExistingCanvasBackground(canvasState)  // reuse PlayerCanvas
        YT_MUSIC -> AmbientBlurBackground(albumArt)              // RenderEffect or gradient
        SPOTIFY -> FlatGradientBackground(paletteColors)         // Brush.verticalGradient
        APPLE_MUSIC -> AnimatedGradientMesh(paletteColors)       // radialGradient + infiniteTransition
    }
}
```

**3.5.1 — AmbientBlurBackground (YT Music style)**
- [ ] Use `RenderEffect.createBlurEffect()` on API 31+
- [ ] Fallback: `Palette`-based gradient overlay for API <31
- [ ] Input: album art `ImageBitmap`
- [ ] Darken overlay on top for readability

**3.5.2 — FlatGradientBackground (Spotify style)**
- [ ] `Brush.verticalGradient` from `paletteColors` (dominant + vibrant swatches)
- [ ] No blur needed — lightest option
- [ ] Static, no animation

**3.5.3 — AnimatedGradientMesh (Apple Music style)**
- [ ] Use `rememberInfiniteTransition` for subtle position animation
- [ ] 2-3 `Brush.radialGradient` layers with animated centers
- [ ] Colors from `paletteColors`
- [ ] Same animation pattern as existing generative canvas in `PlayerCanvas.kt`

### 3.6 — Extend SettingsStore for Canvas
- [ ] `canvasSource: CanvasSource` (AUTO by default)
- [ ] `canvasEnabled: Boolean`
- [ ] `canvasIntensity: Float` (0-1)
- [ ] `canvasDim: Float` (0-1)

### 3.7 — Wire CanvasManager into MusicViewModel
**File:** `viewmodel/MusicViewModel.kt`

- [ ] Initialize `CanvasManager` with providers + cache
- [ ] On track change: call `CanvasManager.resolve()` asynchronously
- [ ] Update `PlayerUiState.canvas` with result
- [ ] Prefetch next track's Canvas
- [ ] Cancel previous in-flight requests on song change

### 3.8 — Aurora Canvas Enhancement
**File:** `ui/canvas/PlayerCanvas.kt`

- [ ] Add configurable parameters: `intensity`, `speed`, `particleCount`, `vignette`
- [ ] Reduce particle count on low-end devices (check `VisualPerformanceTier`)
- [ ] Pause animation when `isPlaying == false`

### 3.9 — Verification
- [ ] Build clean
- [ ] Each layout has distinct background (Base=canvas, YT=blur, Spotify=gradient, Apple=animated mesh)
- [ ] Canvas modes still work in Base layout
- [ ] Canvas failure → graceful fallback, no audio interruption
- [ ] Cache: second play of same song uses cache
- [ ] Prefetch: next song's Canvas loads before it plays
- [ ] Low-end device test: no FPS drop
- [ ] Git commit: `feat: canvas provider architecture + per-layout backgrounds`

**Exit Criteria:** Canvas system is provider-based. Per-layout backgrounds work. Cache + prefetch functional. Non-blocking on failure.

---

## PHASE 4 — YouTube Music Layout

**Goal:** First alternative layout — clean, streaming-oriented.  
**Branch:** `feature/player-youtube`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 2 (PlayerHost + registry), Phase 3 (per-layout backgrounds)

### 4.1 — YoutubeMusicLayout Composable
**New file:** `ui/player/layouts/YoutubeMusicLayout.kt`

Structure:
```
TopBar
  BackButton | DeviceOutput | MoreMenu

ArtworkSection (large, full-bleed with padding)
  PlayerCanvasHost (behind artwork)
  PlayerArtwork (rounded corners)

TrackInfo
  SongTitle (large, bold)
  ArtistName (muted)

SeekBar (full width, thin)
  Elapsed | Remaining

ControlRow
  Like | Previous | Play/Pause (large) | Next | Repeat

TabSection (swipeable)
  Lyrics | Related | Queue
```

### 4.2 — YT Music Style Background
- [ ] Use `AmbientBlurBackground` from Phase 3
- [ ] Dark scrim overlay for text readability
- [ ] Blur intensity configurable via `canvasIntensity`

### 4.3 — YT Music Controls Style
- [ ] Large centered play/pause
- [ ] Previous/Next smaller, flanking
- [ ] Like (heart) on far left
- [ ] Repeat on far right
- [ ] Shuffle accessible via long-press or extra menu

### 4.4 — Tab Section
- [ ] Horizontal pager for Lyrics / Related / Queue
- [ ] Tab indicator at top
- [ ] Swipe gesture between tabs
- [ ] Each tab uses extracted components (PlayerLyrics, PlayerUpNext)

### 4.5 — Settings Preview
- [ ] Add thumbnail preview of YT Music layout in Settings (optional)

### 4.6 — Verification
- [ ] Build clean
- [ ] Switch to YT Music in Settings → layout appears
- [ ] All controls functional
- [ ] Lyrics, Queue tabs work
- [ ] Background blur from artwork
- [ ] Track change: artwork + background update smoothly
- [ ] No playback interruption
- [ ] Git commit: `feat: YouTube Music-inspired layout`

**Exit Criteria:** YT Music layout fully functional, visually distinct, all features working.

---

## PHASE 5 — Spotify Layout

**Goal:** Information-dense, playlist-oriented layout.  
**Branch:** `feature/player-spotify`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 2, Phase 3

### 5.1 — SpotifyInspiredLayout Composable
**New file:** `ui/player/layouts/SpotifyInspiredLayout.kt`

Structure:
```
TopContext
  Album/Playlist name + back

ArtworkCard (with shadow, rounded corners)
  AlbumArt

TrackInfo
  SongTitle (bold)
  ArtistName (secondary)

SeekBar (thin, minimal)

ControlRow
  Like | Previous | Play/Pause | Next | Queue

BottomSection (expandable)
  Lyrics | UpNext
```

### 5.2 — Spotify Style Background
- [ ] Use `FlatGradientBackground` from Phase 3
- [ ] 2-3 colors from palette dominant/vibrant swatches
- [ ] Subtle vertical gradient (dark at top, slightly lighter at bottom)

### 5.3 — Spotify Controls Style
- [ ] Play/Pause: large, centered
- [ ] Previous/Next: medium, flanking
- [ ] Like: heart icon, left side
- [ ] Queue: right side
- [ ] Shuffle/Repeat: secondary row below main controls

### 5.4 — Artwork Card
- [ ] Rounded corners (12dp)
- [ ] Subtle shadow/elevation
- [ ] Slightly smaller than full-width (centered, 80% width)

### 5.5 — Verification
- [ ] Build clean
- [ ] Switch to Spotify in Settings → layout appears
- [ ] Flat gradient background from album colors
- [ ] All controls functional
- [ ] Lyrics/UpNext expandable bottom section
- [ ] No playback interruption
- [ ] Git commit: `feat: Spotify-inspired layout`

**Exit Criteria:** Spotify layout functional, distinct visual identity, gradient background.

---

## PHASE 6 — Apple Music Layout

**Goal:** Elegant, artwork-first layout with glass/blur effects.  
**Branch:** `feature/player-apple`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 2, Phase 3, Phase 7 (glass system — can stub without)

### 6.1 — AppleMusicInspiredLayout Composable
**New file:** `ui/player/layouts/AppleMusicInspiredLayout.kt`

Structure:
```
TopBar
  BackButton | Context (album/playlist) | MoreMenu

ArtworkSection (hero, large, rounded)
  CanvasBackground (animated gradient mesh)
  Artwork (large, rounded corners, subtle scale anim on play/pause)

TrackInfo
  SongTitle (large, bold, center)
  ArtistName (medium, center)

SeekBar (large, prominent)

PlaybackControls
  Previous | Rewind 15s | Play/Pause | Forward 15s | Next

OutputSection (volume/AirPlay placeholder)

BottomSection (sheet-style)
  Lyrics | Queue
```

### 6.2 — Apple Music Style Background
- [ ] Use `AnimatedGradientMesh` from Phase 3
- [ ] 2-3 colors from album palette
- [ ] Subtle radial gradient animation (position shifts slowly)
- [ ] Smooth color transition on track change

### 6.3 — Artwork Treatment
- [ ] Large, centered, rounded corners (16dp)
- [ ] Subtle scale animation: `1.0` when paused, `1.02` when playing
- [ ] Smooth spring animation for scale
- [ ] Optional: slight shadow for depth

### 6.4 — Controls Style
- [ ] Large, clean icons
- [ ] 15s rewind/forward flanking play/pause
- [ ] Shuffle/Repeat in secondary row
- [ ] Controls slightly transparent (glass-like if enabled)

### 6.5 — Glass Integration (Stub)
- [ ] If `glassEnabled`: apply `IanGlassSurface` to controls row
- [ ] Fallback: translucent surface with slight tint
- [ ] Full glass in Phase 7

### 6.6 — Verification
- [ ] Build clean
- [ ] Switch to Apple Music in Settings → layout appears
- [ ] Animated gradient mesh background
- [ ] Artwork scale on play/pause
- [ ] All controls functional
- [ ] Lyrics/Queue sheet-style sections
- [ ] No playback interruption
- [ ] Git commit: `feat: Apple Music-inspired layout`

**Exit Criteria:** Apple Music layout functional, animated background, artwork animation, distinct feel.

---

## PHASE 7 — Glass Surface System

**Goal:** Controlled glass/frosted effects for UI surfaces.  
**Branch:** `feature/glass-system`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 1 (motion tokens), Phase 2 (extracted components)

### 7.1 — IanGlassConfig
**New file:** `ui/glass/IanGlassConfig.kt`

```kotlin
data class IanGlassConfig(
    val enabled: Boolean = false,
    val blurRadius: Float = 16f,
    val vibrancy: Float = 0.3f,
    val lensHeight: Float = 0f,
    val lensAmount: Float = 0f,
    val highlight: Float = 0.1f,
    val tint: Color = Color.White.copy(alpha = 0.05f)
)
```

### 7.2 — IanGlassSurface Composable
**New file:** `ui/glass/IanGlass.kt`

- [ ] `IanGlassSurface` — wrapper composable
- [ ] On API 31+: `Modifier.graphicsLayer { renderEffect = BlurEffect(...) }`
- [ ] Below API 31: translucent surface with gradient overlay
- [ ] Parameters: `config: IanGlassConfig`, `content: @Composable () -> Unit`
- [ ] Always include: surface tint + highlight edge

### 7.3 — IanGlassButton
- [ ] Small glass surface for buttons
- [ ] Subtle highlight edge
- [ ] Press state: slight opacity change

### 7.4 — IanGlassSheet
- [ ] Glass bottom sheet
- [ ] Blurred backdrop
- [ ] Rounded top corners
- [ ] Used for: queue sheet, lyrics sheet

### 7.5 — Apply Glass to Components
Target surfaces (one at a time):
- [ ] Mini-player background
- [ ] Player action row (favorite, lyrics, queue buttons)
- [ ] Queue sheet header
- [ ] Lyrics sheet
- [ ] Settings cards (optional)

### 7.6 — Glass Settings
- [ ] Settings → Appearance → Liquid Glass
- [ ] Toggle: `glassEnabled`
- [ ] Advanced: blur radius, vibrancy sliders (hidden by default)
- [ ] Persist to `SettingsStore`

### 7.7 — Low-End Fallback
- [ ] Detect device tier via `VisualPerformanceTier`
- [ ] LOW tier: glass disabled automatically, translucent surface only
- [ ] BALANCED: glass with reduced blur
- [ ] HIGH: full glass

### 7.8 — Verification
- [ ] Build clean
- [ ] Glass toggle ON: surfaces show frosted effect
- [ ] Glass toggle OFF: standard surfaces
- [ ] API <31: graceful fallback (translucent, no blur)
- [ ] Low-end device: glass auto-disabled
- [ ] No performance regression
- [ ] Git commit: `feat: glass surface system`

**Exit Criteria:** Glass system works on supported devices, degrades gracefully. Applied to mini-player + action row.

---

## PHASE 8 — Mini-Player Morph & Extraction

**Goal:** Extract mini-player from MainActivity. Implement smooth morph to full player.  
**Branch:** `feature/player-system` (continued)  
**Duration estimate:** 3-4 sessions  
**Dependencies:** Phase 2 (PlayerHost), Phase 1 (motion tokens)

### 8.1 — Extract MiniPlayerHost
**New file:** `ui/player/MiniPlayerHost.kt`

- [ ] Extract mini-player composable from `MainActivity.kt`
- [ ] Accept: `playerUiState`, `onExpand`, `onCollapse`
- [ ] Support: Default, Floating, Compact styles
- [ ] Mini-player uses same `PlayerArtwork` as full player (shared identity)

### 8.2 — MiniPlayer Variants
**New files in `ui/player/mini/`:**

- [ ] `BaseMiniPlayer.kt` — current default style
- [ ] `FloatingMiniPlayer.kt` — floating card style
- [ ] `CompactMiniPlayer.kt` — minimal bar style
- [ ] `AppleMiniPlayer.kt` — Apple-style (for Apple Music layout)
- [ ] `SpotifyMiniPlayer.kt` — Spotify-style (for Spotify layout)
- [ ] `YouTubeMiniPlayer.kt` — YouTube-style (for YT Music layout)

### 8.3 — Artwork Morph System
**New file:** `ui/player/components/PlayerArtworkMorph.kt`

- [ ] Extract morph logic from `NowPlayingScreen.kt`
- [ ] Measure mini-player artwork bounds
- [ ] Measure full-player artwork bounds
- [ ] Animate: scale + translate using `MotionTokens.morphSpring`
- [ ] Reverse on dismissal
- [ ] No white frame flash

### 8.4 — Container Morph
- [ ] Mini-player background → full-player background transition
- [ ] Title text position morph
- [ ] Progress bar morph
- [ ] All using shared element transitions where possible

### 8.5 — Gesture Handling
- [ ] Swipe up: expand to full player
- [ ] Swipe down: collapse to mini-player
- [ ] Horizontal swipe on mini: volume (keep existing)
- [ ] Tap: expand
- [ ] Gesture cancellation: if user reverses mid-swipe

### 8.6 — Verification
- [ ] Build clean
- [ ] Mini-player → full player: smooth morph, no flash
- [ ] Full player → mini-player: reverse morph
- [ ] All mini-player styles work
- [ ] Swipe gestures responsive
- [ ] No playback interruption during morph
- [ ] Git commit: `feat: mini-player extraction + morph system`

**Exit Criteria:** Mini-player extracted from MainActivity. Morph feels continuous. All styles functional.

---

## PHASE 9 — Lyrics 2.0

**Goal:** Enhanced lyrics with karaoke highlighting, smooth scroll, full-screen mode.  
**Branch:** `feature/lyrics-2`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** Phase 2 (PlayerLyrics component)

### 9.1 — Lyrics Presentation Layer
**New file:** `ui/lyrics/LyricsPresentation.kt`

```kotlin
enum class LyricsStyle {
    DEFAULT,      // Current style
    KARAOKE,      // Word-by-word highlight
    FULL_SCREEN,  // Immersive mode
    COMPACT       // Minimal inline
}
```

### 9.2 — Active Line Animation
- [ ] Highlight current line based on playback position
- [ ] Smooth scroll to keep active line centered
- [ ] Spring animation for scroll (not instant snap)
- [ ] Active line: larger font, brighter color
- [ ] Inactive lines: smaller, dimmer

### 9.3 — Word-Level Highlighting (Where Supported)
- [ ] Parse word timestamps from lyrics data (if available)
- [ ] Highlight individual words as they're sung
- [ ] Smooth color transition per word
- [ ] Fallback: per-line highlight if no word timestamps

### 9.4 — Full-Screen Lyrics Mode
- [ ] Tap to enter full-screen lyrics
- [ ] Background: album art blurred or palette color
- [ ] Large text, centered
- [ ] Minimal controls overlay (play/pause only)
- [ ] Swipe down to exit

### 9.5 — Lyrics Interactions
- [ ] Tap line → seek to that timestamp
- [ ] Long press → select line (for copy/share)
- [ ] Swipe between styles (Default/Karaoke/FullScreen)

### 9.6 — Translation Slot
- [ ] If translation available, show below main lyrics
- [ ] Toggle: show/hide translation
- [ ] Secondary text style

### 9.7 — Lyrics Loading State
- [ ] Skeleton loading indicator
- [ ] "No lyrics available" state
- [ ] Source indicator (e.g., "Synced from Musixmatch")

### 9.8 — Settings
- [ ] `lyricsStyle` setting (persisted)
- [ ] Toggle: word-level highlighting
- [ ] Toggle: show translation

### 9.9 — Verification
- [ ] Build clean
- [ ] Active line highlights and scrolls smoothly
- [ ] Word highlighting works (if data available)
- [ ] Full-screen mode enters/exits
- [ ] Tap to seek works
- [ ] Translation toggle works
- [ ] No lyrics jump on track change
- [ ] Git commit: `feat: lyrics 2.0 — karaoke + full-screen`

**Exit Criteria:** Lyrics feel alive. Smooth scrolling, highlighting, full-screen mode.

---

## PHASE 10 — Seek Bar 2.0

**Goal:** Animated, responsive seek bar with style options.  
**Branch:** `feature/player-system` (continued)  
**Duration estimate:** 1-2 sessions  
**Dependencies:** Phase 2 (PlayerSeekBar component)

### 10.1 — SeekBar Style Enum
```kotlin
enum class PlayerSeekBarStyle {
    CLASSIC,    // Standard Material Slider
    WAVE,      // Animated wave line
    SQUIGGLE,  // Convx-inspired squiggly path
    MINIMAL    // Thin line, no animation
}
```

### 10.2 — Wave/Squiggly SeekBar
**New file:** `ui/player/components/AnimatedSeekBar.kt`

- [ ] Custom Compose Canvas drawing
- [ ] Frame-driven phase animation (like Convx's `SquigglySlider.kt`)
- [ ] Cubic bezier path for squiggly shape
- [ ] Active portion: filled/bright
- [ ] Inactive portion: dim/outline
- [ ] Animation speed tied to playback (faster when playing)
- [ ] Animation stops when paused

### 10.3 — Seek Interactions
- [ ] Tap to seek
- [ ] Drag to seek with live position preview
- [ ] Haptic feedback at meaningful positions (start, end, quarter marks)
- [ ] Buffer indicator (progressively filled bar)

### 10.4 — Reduced Motion
- [ ] When `reduceMotion == true`: use CLASSIC style regardless of setting
- [ ] Wave/squiggle animation disabled

### 10.5 — Settings
- [ ] `seekBarStyle` setting (persisted)
- [ ] Available in Settings → Player → Seek Bar Style

### 10.6 — Verification
- [ ] Build clean
- [ ] All 4 styles render correctly
- [ ] Wave/squiggle animates when playing
- [ ] Animation pauses when playback pauses
- [ ] Seek is accurate
- [ ] Haptic feedback works
- [ ] Git commit: `feat: seek bar 2.0 — wave/squiggly styles`

**Exit Criteria:** Seek bar has personality. Wave animation smooth. All styles selectable.

---

## PHASE 11 — Up-Next 2.0

**Goal:** Polished queue with spring reorder, indicators, swipe actions.  
**Branch:** `feature/player-system` (continued)  
**Duration estimate:** 1-2 sessions  
**Dependencies:** Phase 2 (PlayerUpNext component)

### 11.1 — Spring Reorder
- [ ] Replace current reorder animation with `MotionTokens.standardSpring`
- [ ] Items smoothly animate to new positions
- [ ] Drag handle with visual feedback (elevation change)

### 11.2 — Current Track Indicator
- [ ] Highlight currently playing track
- [ ] Animated equalizer bars or pulsing dot
- [ ] Always visible at top or auto-scrolled to

### 11.3 — Source/Download Badges
- [ ] Show source icon (YT Music, Local, Downloaded)
- [ ] Download badge for offline tracks
- [ ] Duration per track

### 11.4 — Animated Insert/Remove
- [ ] New track added: slide in from bottom
- [ ] Track removed: slide out + collapse
- [ ] Use `AnimatedVisibility` with `MotionTokens.standard`

### 11.5 — Queue Virtualization
- [ ] Ensure `LazyColumn` has stable keys
- [ ] Test with 500+ track queue
- [ ] No jank during rapid reorder

### 11.6 — Verification
- [ ] Build clean
- [ ] Spring reorder feels smooth
- [ ] Current track always visible
- [ ] Source badges correct
- [ ] Large queue performs well
- [ ] Git commit: `feat: up-next 2.0 — spring reorder + indicators`

**Exit Criteria:** Queue feels premium. Reorder smooth. Indicators clear.

---

## PHASE 12 — Performance Pass & Audit

**Goal:** Systematic performance audit and optimization.  
**Branch:** `feature/performance`  
**Duration estimate:** 2-3 sessions  
**Dependencies:** All feature phases complete

### 12.1 — Recomposition Audit
- [ ] Enable Compose recomposition counts in debug
- [ ] Audit: `MainActivity`, `MusicViewModel`, `NowPlayingScreen`, `PlayerCanvas`
- [ ] Fix: unnecessary recompositions, wrong `remember` usage
- [ ] Add `derivedStateOf` where beneficial
- [ ] Ensure stable keys in all `LazyColumn`/`LazyRow`

### 12.2 — Threading Audit
- [ ] Image decode: ensure off main thread (Coil config)
- [ ] Palette extraction: ensure background
- [ ] Canvas draw calls: profile, move to background where possible
- [ ] Network/IO: ensure not on main thread

### 12.3 — Memory Audit
- [ ] Profile with Android Profiler
- [ ] Check for memory leaks in Canvas player lifecycle
- [ ] Check for bitmap leaks in artwork loading
- [ ] Room queries: ensure not on main thread

### 12.4 — VisualPerformanceTier
**New file:** `ui/motion/VisualPerformanceTier.kt`

```kotlin
enum class VisualPerformanceTier { HIGH, BALANCED, LOW }

fun detectTier(): VisualPerformanceTier {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory() / (1024 * 1024)  // MB
    val processorCount = runtime.availableProcessors()
    
    return when {
        maxMemory >= 6000 && processorCount >= 8 -> HIGH
        maxMemory >= 4000 && processorCount >= 4 -> BALANCED
        else -> LOW
    }
}
```

- [ ] Implement device tier detection
- [ ] Apply tier to: Canvas particle count, glass enabled, animation complexity
- [ ] LOW: no particles, no glass, simplified motion
- [ ] BALANCED: reduced particles, moderate effects
- [ ] HIGH: full effects

### 12.5 — Settings Performance
- [ ] Audit SettingsStore read/write frequency
- [ ] Batch writes where possible
- [ ] Ensure not reading SharedPrefs on every frame

### 12.6 — Network Audit
- [ ] Canvas provider timeouts
- [ ] ytMusic repository timeouts
- [ ] Connection pooling
- [ ] Cache duplication check

### 12.7 — Targets Verification
- [ ] 60 FPS baseline confirmed via profiler
- [ ] No jank during player open (measure)
- [ ] No jank during queue reorder (measure)
- [ ] No audio interruption during Canvas resolution
- [ ] Memory usage stable during 30-min playback

### 12.8 — Git commit
- [ ] `perf: performance audit + optimization pass`

**Exit Criteria:** Profiler confirms 60 FPS. No memory leaks. Threading correct. Device tier adaptation works.

---

## PHASE 13 — QA & Regression

**Goal:** Full regression test across all features, layouts, devices.  
**Branch:** `feature/player-system` (merge to main)  
**Duration estimate:** 2-3 sessions  
**Dependencies:** All phases complete

### 13.1 — Playback Regression Matrix
| Format | Status |
|---|---|
| Local MP3 | [ ] |
| Local M4A | [ ] |
| Local FLAC | [ ] |
| Local WAV | [ ] |
| Local OGG | [ ] |
| YT Music stream | [ ] |
| Cached stream | [ ] |
| Downloaded stream | [ ] |

### 13.2 — Layout Regression
| Layout | Play | Controls | Lyrics | Queue | Canvas | Background |
|---|---|---|---|---|---|---|
| Base | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| YT Music | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Spotify | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Apple Music | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |

### 13.3 — Canvas Regression
| Mode | Status |
|---|---|
| Off | [ ] |
| Generative Aurora | [ ] |
| Ken Burns | [ ] |
| Local video | [ ] |
| Provider (remote) | [ ] |
| Missing Canvas | [ ] |
| Broken URL | [ ] |

### 13.4 — Device Matrix
| Device | Android | RAM | Hz | Status |
|---|---|---|---|---|
| Low-end (API 24-26) | 7-8 | 2-3GB | 60Hz | [ ] |
| Mid-range (API 28-30) | 9-11 | 4-6GB | 60-90Hz | [ ] |
| High-end (API 31+) | 12+ | 8GB+ | 120Hz | [ ] |

### 13.5 — Existing Feature Regression
- [ ] Voice assistant
- [ ] Wave record
- [ ] Recap builder
- [ ] Social signals
- [ ] Downloads
- [ ] Favorites
- [ ] Search
- [ ] Playlist management

### 13.6 — Battery/Performance Sanity
- [ ] 30-min playback with Canvas + animation + glass
- [ ] Battery drain acceptable
- [ ] No thermal throttling
- [ ] Memory stable

### 13.7 — Edge Cases
- [ ] Empty library
- [ ] Single track queue
- [ ] 500+ track queue
- [ ] Network loss during Canvas load
- [ ] Rapid layout switching
- [ ] Rapid track skipping
- [ ] Background/foreground transitions
- [ ] Orientation changes

### 13.8 — Git commit
- [ ] `test: full regression pass — all layouts, canvas, playback`

**Exit Criteria:** All matrix items pass. No regression from baseline. Performance acceptable across device tiers.

---

## PHASE 14 — Release Hardening

**Goal:** Prepare for stable release.  
**Branch:** `main` (final merge)  
**Duration estimate:** 1-2 sessions  
**Dependencies:** Phase 13 complete

### 14.1 — Build Optimization
- [ ] Baseline profile generation
- [ ] R8/ProGuard rules review
- [ ] Verify minification works
- [ ] APK size check (compare to baseline)

### 14.2 — Crash/ANR Audit
- [ ] Check Crashlytics (if available) for new crashes
- [ ] ANR traces review
- [ ] Startup time measurement
- [ ] Cold start < 2s target

### 14.3 — Settings Migration
- [ ] Old `canvasMode` integer → new `CanvasSource` enum
- [ ] Existing preferences preserved
- [ ] No data loss on upgrade

### 14.4 — Documentation
- [ ] Update CHANGELOG.md
- [ ] Update README.md with new features
- [ ] Credit Convx/vivi-music in docs (not source code)
- [ ] Document new Settings options

### 14.5 — Version Bump
- [ ] Bump versionName (6.3.0?)
- [ ] Bump versionCode
- [ ] Git tag: `release/v6.3.0`

### 14.6 — Final Build
- [ ] Release APK build
- [ ] Install + smoke test
- [ ] Git commit: `chore: release v6.3.0`

**Exit Criteria:** Release-ready APK. Clean build. No known crashes. Settings migrate correctly.

---

## PHASE 15 (Future) — Tablet / Large Screen

**Goal:** Optimize for tablets and large screens.  
**Dependencies:** All previous phases  
**Scope:** Out of v1 scope, backlog

### Tasks (when ready)
- [ ] Width-aware player layout
- [ ] Two-column layout (artwork left, controls right)
- [ ] Persistent queue pane
- [ ] Landscape-specific controls
- [ ] Glass surfaces optimized for wide screens

---

## Execution Priority Summary

```
Phase 0  ██████████  Baseline Lock           ← DO FIRST
Phase 1  ████████    Motion System           ← Can parallel Phase 2
Phase 2  ██████████  Player Architecture     ← Can parallel Phase 1
Phase 3  ████████    Canvas Providers        ← After Phase 0, 1
Phase 4  ██████      YouTube Music Layout    ← After Phase 2, 3
Phase 5  ██████      Spotify Layout          ← After Phase 2, 3
Phase 6  ██████      Apple Music Layout      ← After Phase 2, 3
Phase 7  ██████      Glass System            ← After Phase 1, 2
Phase 8  ████████    Mini-Player Morph       ← After Phase 1, 2
Phase 9  ██████      Lyrics 2.0             ← After Phase 2
Phase 10 ████        Seek Bar 2.0           ← After Phase 2
Phase 11 ████        Up-Next 2.0            ← After Phase 2
Phase 12 ████████    Performance Pass        ← After all features
Phase 13 ██████████  QA & Regression         ← After Phase 12
Phase 14 ██████      Release Hardening       ← After Phase 13
Phase 15 ██          Tablet (future)         ← Backlog
```

**Parallel opportunities:**
- Phase 1 + Phase 2 (motion + architecture)
- Phase 4 + Phase 5 + Phase 6 (layouts after foundation)
- Phase 9 + Phase 10 + Phase 11 (polish features)

**Critical path:** Phase 0 → Phase 2 → Phase 3 → Phase 4/5/6 → Phase 12 → Phase 13 → Phase 14

---

*Each phase builds on previous. Never skip ahead. Build green after every phase. Manual QA before merge.*
