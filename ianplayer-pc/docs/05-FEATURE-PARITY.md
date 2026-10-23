# IanPlayer PC — Feature Parity Matrix

Sumber: eksplor penuh codebase Android v6.3.4 (versionCode 43). Arti kolom:
- Kolom "Android": fitur ada di Android (dimit = ya).
- **port**: dibuat di PC, target milestone M#
- **v2**: dipindah ke backlog versi 2 (bisa, tapi kerjanya besar)
- **n.a.**: tidak relevan di desktop

| # | Fitur | Status Android | PC plan | Milestone |
|---|---|---|---|---|
| 1 | Tab reorderable (stream/songs/playlists/albums) | o | port 1:1 | M0.4 |
| 2 | Stream tab: home/trending dari YT Music | o | port (InnerTube browse) | M3.2/3.4 |
| 3 | Genre browsing (12 genre) | o | port | M3.2 |
| 4 | Moods (12) + time-of-day queries | o | port | M3.2 |
| 5 | Songs tab: local library dari MediaStore | o | scan filesystem + DB | M1.1/1.2 |
| 6 | Sort modes + search + fast scroller | o | port | M1.2 |
| 7 | Favorites + play counts + history 90d | o | port ke SQLite | M1.1 |
| 8 | Playlists CRUD + reorder + image | o | port | M1.3 |
| 9 | Album/Artist/Playlist detail screens | o | port | M1.2 |
| 10 | Monthly Recap (Wrapped-style) | o | port + export PNG (baru) | M5.5 |
| 11 | Stream search dual-provider (YTM + Tidal) | o | port (fan-out) | M3.3/3.4 |
| 12 | Tidal search dengan cover art | o | port | M3.3 |
| 13 | Full-screen Now Playing | o | panel besar + minimize (adaptasi desktop) | M2.2 |
| 14 | Synced lyrics karaoke + plain fallback | o | port (8 providers) | M4.1/4.2 |
| 15 | Lyrics source cycling 1..8 + persist | o | port | M4.1 |
| 16 | Up Next queue drag-reorder + remove | o | port | M2.1 |
| 17 | Shuffle / repeat off-all-one | o | port | M2.1 |
| 18 | Crossfade (100/300/700/1200ms) | o tapi UI hidden | port full expose settings | M2.4 |
| 19 | Vertical drag dismiss Now Playing | o | adaptasi: tombol minimize | M2.2 |
| 20 | Seek bar styles (classic/wave/squiggle/minimal) | o | port 4 style | M2.2 + M5.4 |
| 21 | Video mode (ianvideo:// merged stream) | o | stretch opsional (libmpv render) | M3.5 (backlog) |
| 22 | Auto-revert video ke audio saat fail | o | ikut M3.5 kalau video mode jalan | backlog |
| 23 | Prefetch next <8s + expiry re-resolve | o | port | M2.1 + M3.1 |
| 24 | Related auto-fill Up Next (5 lagu) | o | port | M3.4 |
| 25 | Restore queue/track/index cold start | o | port | M2.1 |
| 26 | Downloader: pilih kualitas (bitrate sort) | o | port | M4.3 |
| 27 | Tag enrichment iTunes + Deezer | o | port | M4.3 |
| 28 | ID3/MP4 tag embedding | o | TagLib | M4.3 |
| 29 | Publish ke MediaStore Music/IanPlayer | o | n.a. — save ke `Music\IanPlayer\`, rescan | M4.3 |
| 30 | Title cleaning regex + "Artist - Title" split | o | port 1:1 | M1.1 + M4.3 |
| 31 | Tag editor UI | o | port | M4.3 |
| 32 | Delete file dengan OS confirmation | o (RecoverableSecurityException) | Recycle Bin via IFileOperation | M4.3 |
| 33 | Canvas: aurora generative | o | QML ShaderEffect | M5.3 |
| 34 | Canvas: video lokal matched | o | libmpv 2nd instance | M5.3 |
| 35 | Canvas: Ken Burns | o | QML transforms | M5.3 |
| 36 | Spotify Canvas (GraphQL + TOTP + proxy) | o, sp_dc default ter-commit | port tapi user isi cookie sendiri (security) | M5.3 |
| 37 | Liquid glass UI (Haze, blur/vibrancy/lens) | o | QML MultiEffect tier | M5.2 |
| 38 | Ambient palette dari artwork | o (Palette lib + frame sampling) | quantize 2 warna | M5.2 |
| 39 | VisualPerformanceTier | o | deteksi renderer | M5.2 |
| 40 | Mini player 4 layouts + pill top/bottom | o | port 4 density mode | M5.4 |
| 41 | Voice assistant "Halo Yan" (wake word, id-ID) | o | teknik beda total di desktop | v2 (stretch M6.5) |
| 42 | Music recognition ambient (Shazam-style) | stub doang | tidak dipindah | v2 / abort |
| 43 | Wave Recording (visualizer -> video, capture audio) | o | Media Foundation + WASAPI loopback | v2 (stretch M6.5) |
| 44 | Update via GitHub Release + self-install | o | port + update yt-dlp ekstra | M6.1 |
| 45 | Deep link ianplayer:// + open-with YouTube | o | registry protocol + CLI | M3.5/M6.2 |
| 46 | Profile user (name/avatar/banner) | o | file picker | M5.1 |
| 47 | Social links + Support Developer | o | port statis | M5.1 |
| 48 | Donasi saweria | o | port link | M5.1 |
| 49 | Dark mode default | o | dark-only saat v1 | M0.4 (light v2) |
| 50 | Dynamic color Android 12+ | o | n.a. | n.a. |
| 51 | Media keys / hardware volume OSD | implicit (Media3) | SMTC | M2.3 |
| 52 | Headset/BT plug routing | o | audio device switch (MMDevice) | M6.2 |
| 53 | PiP video | tidak ada | tidak diport | n.a. |
| 54 | Chromecast | tidak ada | tidak diport | n.a. |
| 55 | SMB/FTP/streaming file tanpa lagu YT | tidak ada | tidak diport | n.a. |
| 56 | Subtitle file/track | tidak ada | tidak diport (app musik) | n.a. |
| 57 | Equalizer audio | tidak ada | tidak diport | n.a. (bisa v2) |
| 58 | Sleep timer | tidak ada | tidak diport | n.a. (bisa v2) |
| 59 | Playback speed | tidak ada | gampang tambah (libmpv `speed`) | v2 (fast win) |
| 60 | Software-decoder preference (cosmetic Android) | o (Logic MediaCodecSelector software-first) | n.a. — libmpv auto decode; HW/WASAPI tidak masalah | n.a. |

## Settings keys Android (semua dipetakan 1:1)

`is_dark_mode`, `voice_assistant_enabled`, `is_pill_at_bottom`, `mini_layout_index`, `sort_mode`, `last_recap_check_ts`, `user_name`, `avatar_uri`, `banner_uri`, `tab_order`, `dont_show_update`, `canvas_mode`, `spotify_sp_dc`, `mini_player_style`, `canvas_source`, `canvas_enabled`, `canvas_intensity`, `canvas_blur`, `canvas_dim`, `motion_scale`, `reduce_motion`, `glass_enabled`, `canvas_fade_before_upnext`, `glass_blur`, `glass_vibrancy`, `glass_lens`, `glass_highlight`, `seek_bar_style`, `lyrics_style`, `crossfade_duration_ms`

PC-only baru: `library_folders[]`, `download_dir`, `cache_size_mb`, `ytdlp_last_check`, `protocol_registered`.

## Catatan port yang tidak bare

- **#36 Spotify Canvas**: Android punya default `sp_dc` live ter-commit di source (`SettingsStore.DEFAULT_SPOTIFY_SP_DC`) — security leak kalau di-port. Di PC: kosong, user isi sendiri; muncul error jelas kalau kosong.
- **#21 Video mode**: Android render via TextureView di Now Playing. PC: libmpv video render ke QQuickItem (more work). Buat backlog, bukan blocker M3.
- **#41/#43**: dikerjakan Android pakai API Android-only (Speech/Projection). PC jadi project sendiri, bukan port.
- **#50 Dynamic color**: konsep Android 12 system palette; desktop pakai ambient palette (#38) yang jauh lebih bermakna untuk app ini.
