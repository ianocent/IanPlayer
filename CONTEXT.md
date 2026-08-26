# IanPlayer — Domain Glossary

Domain language for the codebase. Use these terms in code, reviews, and architecture discussions.

## Existing concepts

- **Song** — a playable track. Identified by `id` (local MediaStore id) or `remoteId` (YouTube/Tidal id). May be *unresolved* (no stream URI yet) or *resolved*. Carries an optional `source` tag naming its provider.
- **Queue** — the ordered list of Songs the player will play. Owned internally by the PlayerGateway; the Kotlin list and the ExoPlayer timeline are kept in step inside that one module — no code outside it reads or writes either copy.
- **Stream resolution** — turning an unresolved Song into a playable URI. Routing goes through the `StreamResolvers` port: YouTubeSource and TidalSource adapters; the placeholder URI convention is private to `data/SongSource.kt`.
- **Synced lyric / plain lyric** — timestamped LRC lines vs unformatted text. Fetched through `LyricRepository`, which owns source cascade and sync→plain fallback policy.
- **Playlist** — user-ordered Song list, persisted and exportable as M3U.
- **Recap** — year-in-review card built from play counts/history.
- **Social signals** — opt-in listening-event log (`{s,t}` pairs, 72h prune) feeding For-You recommendations.
- **Wave record** — screen recording of the Now Playing visualizer (MediaProjection + foreground service).

## Module names introduced by refactors

- **SongStore** — single owner of everything persisted about Songs: queue state, stream-songs cache, favorites, play counts, play history, playlists, trending/genre caches, social signals. Owns prefs keys, JSON codecs, and legacy-format migration. SharedPreferences today; storage swap is internal.
- **PlayerGateway** — the deep playback module: owns ExoPlayer handle, queue truth, restore, volume fades, repeat mode, audio session id. UI and ViewModel read derived StateFlows; nobody touches a raw `Player?` from outside.
- **StreamSource** — port for "turn this Song into something audible". Two adapters: YouTubeSource, TidalSource. The placeholder URI scheme is private to this seam.
- **ArtLoader** — album art loading via Coil: caching, dedup, high-res URL rewriting. One call per call site.
- **NavState** — navigation state machine for tabs, detail pages, and back ordering. The tab↔page encoding invariant is implementation detail.
- **RecapBuilder** — pure recap computation: play history window in, MonthlyRecap out. No Android dependencies; the ViewModel only fetches history and publishes.
- **SettingsStore** — single owner of user-facing settings keys and defaults (dark mode, voice assistant, social signals opt-in, pill position, mini layout, sort mode, recap check timestamp). Receivers and listener services read through `SettingsStore.from(context)`; no string literals elsewhere.
- **StreamSearchController** — the stream-search module: owns debounce, dual-provider fan-out (YouTube partial stream + Tidal), result merging, and visible-page slicing. The ViewModel exposes its StateFlows; eager resolution is triggered via a settle callback.

## Decisions recorded elsewhere

ADR folder: `docs/adr/` (created when a decision needs recording).
