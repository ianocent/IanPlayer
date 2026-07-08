# IanPlayer - Progress Summary

## Current State
Streaming feature di tab "Stream" sudah bisa search (JioSaavn + YouTube Music + Regular YouTube + SoundCloud), tapi **audio playback selalu gagal** karena semua provider free-unauthenticated sudah memblokir akses.

### Flow search saat ini:
```
JioSaavn → YT Music (InnerTube) → Regular YouTube → SoundCloud
```
Search chain udah jalan, semua layer dikerjakan. Tapi actual streaming audio mentok.

### Yang udah dicoba & kenapa gagal:
| Pendekatan | Masalah |
|---|---|
| **YouTube Music InnerTube (WEB_REMIX)** | Search OK, player return "Video unavailable" — music content di YT Music wajib login |
| **YouTube InnerTube ANDROID client** | Return 400 "Precondition check failed" — key Android + UA ga diterima |
| **YouTube InnerTube IOS client** | Sama kaya ANDROID, 400 |
| **YouTube InnerTube WEB client (regular)** | Semua video return "Video unavailable" — unauthenticated playback diblokir total |
| **Invidious instances (6 instance)** | Semua mati atau return HTML bukan JSON |
| **SoundCloud** | HTML 66KB berhasil didownload, tapi string `client_id` ga muncul sama sekali (di-obfuscate). Semua 3 fallback key expired (401). |
| **Piped API** | User ga mau (request revert) |
| **JioSaavn (saavn.dev, saavn.me, vercel.app)** | Tidak muncul di log — kemungkinan semua down |

## Solusi yang Disepakati
**Backend proxy pake OAuth login + VPS**

Arsitektur:
```
[Android App] ──HTTP──> [VPS Lo] ──OAuth Token──> YouTube InnerTube
   (banyak user,              (1 IP tetap, 
    beda IP)                   ga mencurigakan)
```

### Yang sudah siap:
1. Akun Gmail dummy
2. VPS (pending)

### Yang perlu dikerjakan:

#### User (lo):
1. **Google Cloud Console**:
   - Bikin project baru
   - Enable YouTube Data API v3
   - Bikin OAuth consent screen (External)
   - Bikin OAuth Client ID — pilih tipe **"TV and Limited Input devices"** atau **"Web application"**
   - Redirect URI: bakal ditentuin pas backend jadi
   - Catat **Client ID** + **Client Secret**

2. **Dapetin Refresh Token**:
   - Pake tool `ytmusicapi` (Python) dari laptop
   - Login pake akun dummy → dapet refresh token
   - Simpen token ini di environment variable VPS

#### Backend (gw):
- Node.js / Express
- Endpoint: `POST /api/search`, `GET /api/stream?id=videoId`
- OAuth token management (auto-refresh)
- Forward InnerTube request ke YouTube pake token

#### Android App (gw):
- Ganti base URL dari `youtube.com/youtubei/v1` ke VPS lo
- Atau tambahin configurable server URL

## File yang Diubah Di Session Ini
- `app/src/main/java/.../data/YTMusicRepository.kt` — struktur ulang, tambah ANDROID/IOS client context, tambah regular YouTube search fallback
- `app/src/main/java/.../data/SoundCloudRepository.kt` — file baru, SoundCloud scraping
- `app/src/main/java/.../viewmodel/MusicViewModel.kt` — tambah SoundCloud di search chain

## Next Steps
1. Lo: setup OAuth Client ID di Google Cloud Console
2. Lo: VPS aktif
3. Gw: bikin backend Node.js proxy
4. Gw: update Android App pake backend
