# Dokumentasi Integrasi Spotify Canvas API (Unofficial)

Dokumentasi ini menjelaskan secara mendetail tentang cara kerja, payload, struktur *request*, dan skema *response* untuk mengambil (fetch) video Canvas dari Spotify untuk diintegrasikan ke dalam *custom music player*.

> **Catatan:** Bagian 1–5 di bawah adalah referensi riset awal (termasuk opsi public proxy).
> **Keputusan implementasi final ada di [Bagian 6](#6-keputusan-final--spesifikasi-implementasi-v2)** —
> self-hosted worker, streaming URL langsung, semua song (stream + local), dan canvas otomatis tanpa tombol.

---

## 1. Mekanisme Internal (Reverse Engineered)

Secara bawaan, aplikasi resmi Spotify mengambil video Canvas dengan mengirimkan *request* ke *gateway* internal mereka menggunakan protokol **Protocol Buffers (Protobuf)**.

- **Endpoint Internal:** `POST https://spclient.wg.spotify.com/canvaz-cache/v0/canvases`
- **Autentikasi:** Membutuhkan OAuth Token resmi dari aplikasi Spotify yang sedang aktif (`Bearer BQ...`).
- **Payload Request (Protobuf Binari):**
  ```protobuf
  message CanvasRequest {
    message Track {
      bytes track_uri = 1; // Konversi dari Spotify Track ID ke format byte hex
    }
    repeated Track tracks = 1;
  }
  ```

---

## 2. Menggunakan Public Proxy API (Rekomendasi)

Untuk menghindari rumitnya *parsing* Protobuf dan manajemen token OAuth yang cepat kedaluwarsa, komunitas developer menggunakan **Cloudflare Workers** sebagai *reverse-proxy*. Salah satu yang paling stabil adalah endpoint publik dari repositori komunitas.

### A. HTTP Request
- **Method:** `GET`
- **URL Format:** `https://canvas.canvasapi.workers.dev/api/canvas/{TRACK_ID}`
- **Content-Type:** `application/json`

> **Note:** `{TRACK_ID}` adalah 22 karakter alfanumerik unik Spotify. 
> Contoh: `4PTG3Z6ehGkBF3zI7YkR6C` (diambil dari URL `https://open.spotify.com/track/4PTG3Z6ehGkBF3zI7YkR6C`).

### B. Payload / Parameter
API ini tidak membutuhkan request body (`payload`). Seluruh informasi dikirimkan langsung via **URL Path Parameter** (`TRACK_ID`).

---

## 3. Skema Response

API akan mengembalikan format data JSON standar. Struktur *response* dibedakan menjadi dua kondisi utama:

### A. Response Sukses (Lagu Memiliki Canvas)
Jika lagu memiliki Canvas, API mengembalikan status `200 OK` dengan direct URL video `.mp4` dari CDN Spotify (`canvaz.scdn.co`).

```json
{
  "success": true,
  "canvas": {
    "url": "https://canvaz.scdn.co/linear/v1/m/4PTG3Z6ehGkBF3zI7YkR6C/video.mp4",
    "type": "VIDEO",
    "artistId": "41MozwPIwD1RMIj67G411j",
    "artistName": "Nama Artis/Band"
  }
}
```

**Detail Field:**
- `success` (boolean): Menandakan status pencarian Canvas berhasil atau tidak.
- `canvas.url` (string): URL langsung menuju file video mentah. Sangat dianjurkan untuk langsung di-load ke elemen HTML5 `<video>`.
- `canvas.type` (string): Biasanya bernilai `"VIDEO"` atau `"IMAGE"`.
- `canvas.artistId` (string): ID Spotify dari artis pemilik lagu.
- `canvas.artistName` (string): Nama lengkap artis.

### B. Response Gagal / Tidak Ditemukan (Lagu Tanpa Canvas)
Jika lagu tersebut legal/terdaftar namun artisnya tidak mengunggah Canvas, API akan mengembalikan status `200 OK` atau `404 Not Found` dengan struktur:

```json
{
  "success": false,
  "message": "Canvas not found for the provided track ID"
}
```

---

## 4. Implementasi pada Front-End Music Player

### A. Integrasi JavaScript (Fetch API)
Berikut adalah logika standar untuk menangani *response* dan *payload* di sisi *client*:

```javascript
async function fetchTrackCanvas(trackId) {
  const apiUrl = `https://canvas.canvasapi.workers.dev/api/canvas/${trackId}`;
  
  try {
    const response = await fetch(apiUrl);
    const data = await response.json();
    
    if (data.success && data.canvas && data.canvas.url) {
      return {
        hasCanvas: true,
        videoUrl: data.canvas.url,
        artist: data.canvas.artistName
      };
    } else {
      return { hasCanvas: false, error: "No canvas available" };
    }
  } catch (err) {
    return { hasCanvas: false, error: err.message };
  }
}
```

### B. Best Practices UI/UX Player
1. **Fallback Background:** Dikarenakan banyak lagu *indie* atau lagu lama tidak memiliki Canvas, selalu siapkan *fallback* ke **Album Art (Cover Image)** bawaan dari Spotify API biasa.
2. **Atribut Video:** Agar berjalan mulus di Android dan iOS tanpa melanggar kebijakan *autoplay*, tag video wajib dikonfigurasi seperti ini:
   ```html
   <video src="URL_DARI_API" autoplay loop muted playsinline></video>
   ```

---

## 5. Batasan & Risiko (Disclaimer)
- **Status API:** API ini bersifat *unofficial* (tidak resmi). Spotify sewaktu-waktu dapat mengubah struktur Protobuf atau memblokir *gateway* `canvaz-cache` mereka yang akan menyebabkan proxy API ini mati.
- **Caching:** Beberapa proxy menerapkan *cache* selama beberapa jam untuk menghemat kuota *request* ke server Spotify.

# 🎵 Spotify Canvas API Guide (No-Auth & Open Source Setup)

Dokumentasi ini menjelaskan cara mengintegrasikan video **Spotify Canvas** ke dalam aplikasi *music player* secara **100% Free, Open Source, dan Tanpa Auth (No Login / No Developer Token Required)**.

---

## 🏗️ Arsitektur Overview
Karena aplikasi lu bersifat murni *no-auth*, kita tidak bisa menggunakan alur OAuth resmi Spotify (`Client ID` & `Client Secret`). Sebagai gantinya, kita memanfaatkan **Anonymous Access Token** yang digunakan oleh Spotify Web Player publik (`open.spotify.com`), lalu menjembataninya menggunakan **Proxy API** untuk menghindari masalah CORS di browser.

```
[Music Player Front-end] ──(GET Track ID)──> [Proxy/Public Worker] ──(Anonymous Auth)──> [Spotify Internal API]
       ▲                                                                                          │
       └───────────────────────(Mengembalikan URL .mp4 CDN)───────────────────────────────────────┘
```

---

## 🚀 Opsi 1: Menggunakan Public Worker API (Langsung Pakai)
Cara paling instan tanpa perlu bikin backend sendiri adalah menembak proxy publik yang dirawat oleh komunitas developer open-source.

*   **Base URL:** `https://canvas.canvasapi.workers.dev/api/canvas/`
*   **Method:** `GET`
*   **Authentication:** `None` (Bebas API Key/Token)

### Contoh Request
Kirim `GET` request dengan menempelkan 22 karakter Spotify Track ID di akhir URL:
```text
GET https://canvas.canvasapi.workers.dev/api/canvas/4PTG3Z6ehGkBF3zI7YkR6C
```

### Skema Response JSON (Jika Lagu Memiliki Canvas)
```json
{
  "success": true,
  "canvas": {
    "url": "https://canvaz.scdn.co/linear/v1/m/video/140s/68903c08f4c208c9038234",
    "type": "VIDEO",
    "artistId": "41MozwPIwD1RMIj67G411j",
    "artistName": "Artist Name"
  }
}
```

---

## 💻 Implementasi Code di Front-End (JavaScript)

```javascript
async function fetchTrackCanvas(trackId) {
  const proxyEndpoint = `https://canvas.canvasapi.workers.dev/api/canvas/${trackId}`;
  try {
    const response = await fetch(proxyEndpoint);
    const data = await response.json();
    if (data.success && data.canvas && data.canvas.type === "VIDEO") {
      return data.canvas.url;
    }
  } catch (error) {
    console.error("⚠️ Gagal mengambil Canvas Spotify:", error.message);
  }
  return null;
}
```

---

# 6. Keputusan Final — Spesifikasi Implementasi (v2)

Bagian ini menggantikan rekomendasi public proxy di atas. Semua keputusan di bawah sudah dipilih
untuk **stabilitas jangka panjang**, walaupun implementasinya lebih ribet.

## 6.1 Ringkasan Keputusan

| Topik | Keputusan |
|---|---|
| Backend | **Cloudflare Worker milik sendiri** (self-hosted), bukan public worker |
| Auth | Tidak ada login user. Worker memakai **anonymous web-player token** milik Spotify |
| Video | **Streaming URL langsung** ke ExoPlayer. Tidak download file `.mp4` permanen |
| Cache | Cache mapping + cache URL (TTL), bukan cache file video |
| Cakupan | **Fase 1: semua song** — stream (YouTube) dan local file |
| UI | **Tanpa tombol canvas.** Otomatis muncul kalau lagu punya canvas |
| Layout | Canvas jadi background area atas Now Playing; bawahnya gradient dari warna album art |
| Fallback | Lagu tanpa canvas → Now Playing tampil persis seperti sekarang (tanpa placeholder) |

## 6.2 Arsitektur Target

```text
┌─────────────────────────────── Android (IanPlayer) ───────────────────────────────┐
│  MusicViewModel                                                                   │
│    currentSong ──► SpotifyCanvasRepository                                        │
│                       │                                                           │
│                       ├─ Room: mapping songKey → spotifyId / canvasUrl / TTL       │
│                       │                                                           │
│                       └─ HTTPS GET /v1/resolve?...                                │
│                                  │                                                │
│  NowPlayingScreen ◄── canvasUrl ─┘                                                │
│    └─ ExoPlayer (muted, loop) streaming canvaz.scdn.co/....mp4                    │
└───────────────────────────────────┬───────────────────────────────────────────────┘
                                    │
                     ┌──────────────▼──────────────┐
                     │  Cloudflare Worker (own)    │
                     │                             │
                     │  1. Token manager           │
                     │  2. Match resolver          │
                     │     • YouTube ID → Spotify  │
                     │     • title/artist/duration │
                     │  3. Canvas fetcher          │
                     │  4. KV cache + rate limit   │
                     └──────────────┬──────────────┘
                                    │
                     ┌──────────────▼──────────────┐
                     │ Spotify internal endpoints  │
                     │  • open.spotify.com token   │
                     │  • api.spotify.com/search   │
                     │  • spclient canvaz-cache    │
                     └─────────────────────────────┘
```

Prinsip:
- Worker adalah satu-satunya pihak yang bicara ke Spotify. App tidak pernah menyimpan token Spotify.
- App hanya tahu satu endpoint worker + satu shared app key.
- Semua kegagalan jaringan **tidak boleh** mengganggu playback audio.

## 6.3 Backend — Cloudflare Worker

### 6.3.1 Endpoint

```http
GET /v1/resolve?youtubeId=<id>&title=<title>&artist=<artist>&durationMs=<ms>&songKey=<key>
Header: X-Ian-Key: <shared-app-key>
```

Response sukses (canvas ada):

```json
{
  "ok": true,
  "matched": true,
  "confidence": 0.93,
  "spotifyId": "4PTG3Z6ehGkBF3zI7YkR6C",
  "canvasUrl": "https://canvaz.scdn.co/linear/v1/m/.../video.mp4",
  "canvasType": "VIDEO",
  "artistName": "The Weeknd",
  "urlExpiresAt": 1770000000000,
  "source": "youtube-odesli"
}
```

Response sukses (tidak ada canvas):

```json
{
  "ok": true,
  "matched": true,
  "confidence": 0.88,
  "spotifyId": "4PTG3Z6ehGkBF3zI7YkR6C",
  "canvasUrl": null,
  "source": "spotify-search"
}
```

Response tidak match:

```json
{
  "ok": true,
  "matched": false,
  "confidence": 0.0,
  "canvasUrl": null
}
```

Response error (worker/Spotify down):

```json
{ "ok": false, "error": "upstream_unavailable", "retryAfterMs": 60000 }
```

### 6.3.2 Token Manager (anonymous, tanpa login)

- Ambil token dari:
  `https://open.spotify.com/get_access_token?reason=transport&productType=web_player`
- Header yang dipakai: `User-Agent` browser modern + `Accept: application/json`.
- Response berisi `accessToken`, `accessTokenExpirationTimestampMs`, `isAnonymous`.
- Simpan token di memory isolate + KV (`spotify_token`), refresh kalau sisa umur < 60 detik.
- Kalau token request gagal: jangan retry berlebihan; pakai exponential backoff dan kembalikan
  `upstream_unavailable` supaya app langsung fallback ke tampilan biasa.
- **Jangan pernah** mengirim token ini ke client.

### 6.3.3 Resolusi Spotify Track ID

Urutan strategi (berhenti di yang pertama confident):

1. **Stream song — exact match via YouTube ID**
   - Panggil Odesli: `https://api.song.link/v1-alpha.1/links?url=https://www.youtube.com/watch?v=<id>`
   - Ambil entity Spotify track dari response.
   - Ini paling akurat karena match per-video, bukan per-teks.
   - Odesli rate limit ketat → wajib cache hasilnya (positif dan negatif).

2. **Local song — Spotify Search**
   - Normalisasi judul/artis: lowercase, buang `(feat. ...)`, `(remaster)`, `(live)`,
     `official video`, `lyrics`, dan tanda baca berlebih.
   - `GET https://api.spotify.com/v1/search?q=track:<title> artist:<artist>&type=track&limit=10`
     dengan anonymous token.
   - Skor kandidat:
     - durasi: `abs(candidate.duration_ms - durationMs) <= 5000` (wajib kalau durasi diketahui)
     - judul: normalized similarity ≥ 0.80
     - artis: normalized similarity ≥ 0.70
   - Pilih skor tertinggi. Kalau di bawah threshold → `matched: false`.
   - Jangan pakai hasil yang durasinya beda jauh (remix/live/cover sering salah match).

3. **Fallback teks untuk stream song**
   - Kalau Odesli gagal/timeout, pakai jalur Spotify Search yang sama seperti local.

### 6.3.4 Canvas Fetcher

- Endpoint internal: `POST https://spclient.wg.spotify.com/canvaz-cache/v0/canvases`
- Header:
  - `Authorization: Bearer <anonymous-token>`
  - `Content-Type: application/x-protobuf`
  - `Accept: application/x-protobuf`
- Payload protobuf:

```protobuf
message CanvasRequest {
  message Track {
    bytes track_uri = 1; // UTF-8 dari "spotify:track:<spotifyId>"
  }
  repeated Track tracks = 1;
}
```

- Parse response, ambil URL `canvaz.scdn.co`.
- Kalau response kosong / tidak ada canvas → simpan hasil negatif.
- Kalau `401/403` → invalidate token sekali, retry sekali. Kalau masih gagal → error ke client.
- Catatan: detail protobuf bisa berubah. Worker harus punya unit test + alert sederhana
  (mis. log harian jumlah `matched: false` vs `error`).

### 6.3.5 Cache & Rate Limit (KV)

| Key | Value | TTL |
|---|---|---|
| `map:yt:<youtubeId>` | spotifyId + confidence | 30 hari |
| `map:text:<normalizedTitle>|<normalizedArtist>` | spotifyId + confidence | 30 hari |
| `canvas:<spotifyId>` | canvasUrl + type + artistName | 12 jam |
| `miss:<songKey>` | matched=false | 7 hari |
| `token:web` | accessToken + expiry | sampai expiry - 60s |

Aturan:
- Semua request `resolve` dicek ke KV dulu. Cache hit → tidak menyentuh Spotify/Odesli.
- Negative cache mencegah app nembak terus untuk lagu yang memang tidak punya canvas.
- Rate limit per IP + per `X-Ian-Key` (mis. 60 request/menit) untuk melindungi token.
- `X-Ian-Key` bukan keamanan sejati (bisa diekstrak dari APK), hanya pengurangan abuse.

## 6.4 Client Android

### 6.4.1 Modul Baru

```text
data/canvas/spotify/
  SpotifyCanvasRepository.kt   // orchestrator: cache → network → state
  SpotifyCanvasApi.kt          // HTTP client ke worker
  CanvasCacheEntity.kt         // Room entity
  CanvasCacheDao.kt            // Room DAO
  CanvasMatch.kt               // model hasil resolve
```

Tanggung jawab:
- `SpotifyCanvasRepository.resolve(song)`:
  1. Cek Room berdasarkan `songKey` (`yt:<remoteId>` atau `local:<hash(title|artist|duration)>`).
  2. Kalau cache valid → emit langsung.
  3. Kalau tidak → panggil worker, simpan hasil (positif/negatif), emit.
- Tidak ada download `.mp4`. Yang disimpan hanya `canvasUrl` + metadata.
- URL dianggap valid sampai `urlExpiresAt`; kalau ExoPlayer dapat `403`, repository
  invalidate cache URL dan retry resolve **sekali**.

### 6.4.2 State di ViewModel

```kotlin
data class CanvasState(
    val songKey: String? = null,
    val status: Status = Status.IDLE, // IDLE, LOADING, READY, NONE, ERROR
    val url: String? = null,
    val spotifyId: String? = null
)
```

- `MusicViewModel` observe `currentSong`.
- Saat song berubah: cancel job lama, set `LOADING`, resolve, lalu `READY`/`NONE`/`ERROR`.
- **Prefetch opsional:** saat song berjalan > 50%, resolve song berikutnya di queue
  (hanya mapping + URL, bukan video).
- `READY` hanya boleh emit kalau `songKey` masih sama dengan song aktif (hindari canvas
  nyangkut dari lagu sebelumnya).

### 6.4.3 Integrasi ke UI yang Ada

Saat ini:
- `PlayerUiState.canvasVideoSource` hanya dari `CanvasVideoSource` (file lokal).
- `PlayerBackdrop.canvasVideoPath` juga hanya file lokal.
- Tombol canvas di `NowPlayingScreen` masih dikomentari.

Perubahan yang dibutuhkan:
- `PlayerUiState.VisualSlice` tambah `remoteCanvasUrl: String?` + `canvasStatus`.
- `rememberPlayerBackdrop` menerima `remoteCanvasUrl`; prioritas path:
  1. local canvas file (kalau user pernah menaruh file)
  2. `remoteCanvasUrl` dari Spotify
  3. tidak ada → tampilan Now Playing existing
- `PlayerCanvasVideoSurface` sudah bisa menerima URL HTTP (ExoPlayer), jadi tidak perlu
  komponen video baru. Pastikan `MediaItem.fromUri(url)` + `RESIZE_MODE_ZOOM`.
- ExoPlayer canvas: muted, `REPEAT_MODE_ALL`, `playWhenReady = isPlaying`, release saat dispose.
- Jangan buat instance ExoPlayer baru tiap recompose; key by `songKey` + URL.

## 6.5 UI/UX Now Playing (Final)

### 6.5.1 Tanpa Canvas

- Tidak ada perubahan visual sama sekali.
- Tidak ada placeholder, tidak ada ruang kosong, tidak ada spinner.
- Gradient/album art/controls/lyrics tetap seperti versi sekarang.

### 6.5.2 Dengan Canvas

Layout dari atas ke bawah:

```text
┌─────────────────────────────────────────────┐
│                                             │
│          CANVAS VIDEO (background)          │  ← area atas, full width
│          muted • loop • zoom crop           │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  scrim gelap tipis (biar teks kebaca) │  │
│  └───────────────────────────────────────┘  │
│                                             │
│         ▲ gradient fade ke bawah ▲          │  ← transisi ke warna album art
├─────────────────────────────────────────────┤
│  Title / Artist / Controls / Lyrics         │  ← konten existing
│  background: gradient dari album art        │
└─────────────────────────────────────────────┘
```

Aturan visual:
- Canvas mengisi **area atas** Now Playing (sekitar 52–60% tinggi layar; final tuning saat implementasi).
- Di bawah canvas ada **gradient vertikal**:
  - atas: transparan / warna canvas
  - bawah: `ambientColor` dari album art (warna dominan)
- Tambahkan scrim tipis di atas video supaya judul/kontrol tetap terbaca di semua frame.
- Konten yang sudah ada (album art hero, judul, seek bar, controls, lyrics/upnext) tetap
  berada di atas layer canvas, bukan digeser.
- Transisi saat canvas siap: crossfade 300–400 ms. Tidak boleh ada layout jump.
- Kalau resolve gagal setelah canvas sempat tampil (mis. URL 403): crossfade balik ke
  tampilan biasa, tanpa toast/error ke user.
- Hemat baterai: pause ExoPlayer canvas saat app di background atau Now Playing ditutup.

### 6.5.3 Tombol Canvas

- **Hapus** tombol/toggle canvas dari Now Playing (termasuk blok yang sekarang dikomentari).
- Tidak ada setting user untuk memilih mode canvas.
- Canvas murni otomatis berdasarkan ketersediaan lagu.
- `CanvasMode` / generative / Ken Burns yang lama boleh dibiarkan dormant untuk saat ini;
  jangan dihapus sebelum canvas Spotify stabil.

## 6.6 Alur Runtime

1. User play lagu.
2. `MusicViewModel` set `CanvasState(songKey, LOADING)`.
3. Repository cek Room:
   - hit valid → `READY(url)` / `NONE`
   - miss → request worker
4. Worker resolve Spotify ID → fetch canvas URL → response.
5. App simpan cache, emit `READY` atau `NONE`.
6. UI:
   - `READY` → tampilkan canvas + gradient
   - `NONE` / `ERROR` → tampilan existing
7. Ganti lagu → cancel request lama, reset state, ulangi.

Target latency: cache hit < 50 ms; network pertama 0.5–2 detik. Selama menunggu, UI existing
tetap tampil (tanpa spinner).

## 6.7 Error Handling

| Kondisi | Perilaku |
|---|---|
| Worker down / timeout 5s | `ERROR` → tampilan existing, retry saat lagu diputar lagi |
| Spotify token expired | Worker refresh token, retry sekali |
| Canvas 404 / tidak ada | `NONE`, simpan negative cache 7 hari |
| Canvas URL 403 | Invalidate URL cache, resolve ulang sekali, lalu fallback |
| Matching confidence rendah | `NONE`, jangan tampilkan canvas yang salah |
| Offline | Pakai cache URL kalau masih valid; kalau tidak → tampilan existing |

Tidak ada dialog/toast untuk kegagalan canvas. Canvas adalah enhancement, bukan fitur kritis.

## 6.8 Rate Limit & Beban

- Resolve hanya saat song berubah, bukan saat recompose/seek.
- Prefetch maksimal 1 lagu ke depan.
- Negative cache 7 hari.
- Worker cache 12 jam untuk URL, 30 hari untuk mapping.
- Kalau user skip lagu cepat berkali-kali, debounce 400–600 ms sebelum request.

## 6.9 Yang Harus Dihapus / Diubah

- Hapus tombol canvas di `NowPlayingScreen` (blok `IanGlassButton` + `canvasIsOn` +
  `preferredLiveMode`).
- `NowPlayingScreen` tidak lagi menghitung `preferredLiveMode` dari local file saja.
- `PlayerUiState` / `PlayerBackdrop` menerima sumber canvas dari repository, bukan hanya
  `CanvasVideoSource`.
- `CanvasVideoSource` tetap dipakai sebagai prioritas lokal, bukan satu-satunya sumber.
- Tidak ada perubahan pada audio playback, queue, atau media session.

## 6.10 Rencana Tes

1. Lagu stream yang punya canvas (contoh track id doc: `4PTG3Z6ehGkBF3zI7YkR6C`) → canvas muncul.
2. Lagu stream tanpa canvas → tampilan existing, tidak ada ruang kosong.
3. File lokal dengan judul/artis sama → canvas muncul via Spotify Search.
4. File lokal yang tidak ada di Spotify → `NONE`, tampilan existing.
5. Matikan internet → tidak crash, tidak ada spinner nyangkut.
6. Paksa URL canvas expired/403 → fallback mulus, lalu recover saat lagu diputar ulang.
7. Skip lagu cepat 10x → tidak ada request storm, tidak ada canvas lagu lama nyangkut.
8. Pause/play, buka/tutup Now Playing, background app → video ikut pause dan release.
9. Rotasi/config change → canvas tidak restart dari awal kalau tidak perlu.

## 6.11 Risiko

- Endpoint `canvaz-cache` dan anonymous token adalah internal Spotify; bisa berubah kapan saja.
- Odesli punya rate limit dan bisa down; cache + fallback search mengurangi dampaknya.
- Matching local song bisa salah untuk remix/live/cover; threshold confidence wajib ketat.
- Konten canvas milik artis/Spotify; cocok untuk personal use, bukan jaminan untuk Play Store.
- Biaya worker: gratis untuk volume normal, tapi KV + request tetap perlu dipantau.

## 6.12 Pertanyaan Terbuka (kecil, bisa diputuskan saat implementasi)

1. Tinggi area canvas: 52% atau 60% dari tinggi layar?
2. Perlu tombol "clear canvas cache" di settings, atau cukup otomatis?
3. Worker diberi fallback ke public proxy kalau self-hosted down, atau strict self-hosted?
4. Prefetch lagu berikutnya langsung aktif, atau tunggu fase 2?

---

# 7. REALITY CHECK 2026 + Implementasi Aktual (v2.1)

Bagian ini mengoreksi Bagian 1–6 berdasarkan verifikasi jaringan langsung per hari ini.
Skema §6 (self-hosted worker + Odesli + protobuf canvaz-cache) **sudah tidak bisa jalan
apa adanya** — endpoint yang direferensikan sebagian besar sudah mati atau dikunci.

## 7.1 Hasil verifikasi endpoint (dieksekusi langsung)

| Endpoint | Status | Bukti |
|---|---|---|
| `canvas.canvasapi.workers.dev/api/canvas/{id}` (public proxy lama) | **MATI** | 404 `error code: 1042` |
| `api.song.link/v1-alpha.1/links` (Odesli tanpa key) | **DEPRECATED** | 401 `PUBLIC_API_ACCESS_DEPRECATED` — sekarang butuh API key berbayar |
| `open.spotify.com/get_access_token?...` (anonymous token lama, §6.3.2) | **DIBLOKIR** | 403 "URL Blocked" (Varnish) tanpa/TOTP |
| `open.spotify.com/api/token` + TOTP **tanpa sp_dc** | **HIDUP** | 200 + `accessToken`, `isAnonymous:true` |
| `api.spotify.com/v1/search` pakai token anonymous | **429** `QUOTA_EXCEEDED` — anonymous tidak dapat kuota Web API | |
| `api-partner.spotify.com/pathfinder/v1/query` (GraphQL) pakai token anonymous | **HIDUP** | `searchTracks` mengembalikan uri/id/judul/artis/durasi + `playability` |
| GraphQL `canvas` (trackUnion, hash `575138ab...`) pakai token anonymous | **NULL** | canvas selalu null untuk token anonymous |
| `spclient.wg.spotify.com/canvaz-cache/v0/canvases` (protobuf) pakai token anonymous | **400** | "Could not parse request payload" — butuh token bawa sp_dc |
| `spotify-canva.vercel.app/api/canvas?trackId={id}` | **HIDUP** | 200 JSON `{"canvasesList":[{"canvasUrl":"https://canvaz.scdn.co/...","artist":{...}}]}` |
| MusicBrainz WS/2 (ISRC) | **HIDUP** tetapi tidak dipakai: `url-rels` Spotify jarang terisi | |

Kesimpulan: jalur anonymous-token → **api-partner search** (Odesli dibuang sepenuhnya;
judul/artis dari metadata YouTube atau MediaStore menutup kasus stream & lokal
sekaligus), lalu hasil match → **canvaz REST proxy** (`spotify-canva.vercel.app`,
deploy dari open-source `github.com/punisher-303/Spotify-Canva`) untuk mendapatkan
URL MP4. Protobuf `canvaz-cache` hanya jalan dengan `sp_dc` → porsi itu dinaikkan ke
proxy, bukan di app.

## 7.2 Bug yang nyata menyebabkan canvas tidak pernah muncul (sebelum perbaikan)

1. **`SpotifyCanvasRepository.resolve()` dead-code** — tidak pernah dipanggil dari
   ViewModel/UI mana pun. Dengan backend sehidup mati pun tidak akan tampil.
2. **Salah makna ID**: `Song.remoteId` untuk stream = **YouTube video ID**, tapi
   proxy Spotify mengharuskan **22-char Spotify track ID**. YT ID selalu 404/not-found.
3. **Lagu lokal langsung berhenti**: tidak punya `remoteId` → resolve mengembalikan
   `matched:false` tanpa mencoba apa pun, meskipun backend masih hidup.
4. **Semua upstream di Penurunan §6 mati**: worker proxy publik mati + Odesli diperketat.

## 7.3 Sepekan implementasi (files)

```
data/canvas/
  SpotifyTokenManager.kt     // anonymous token via TOTP (secret dict GitHub, HMAC-SHA1 6 digit)
  SpotifyCanvasApi.kt        // (1) GraphQL searchTracks  (2) canvaz REST proxy
  SpotifyCanvasRepository.kt // resolve: Room cache → text match → score → canvas fetch
ui/player/PlayerUiState.kt            // VisualSlice + remoteCanvasUrl
ui/player/components/PlayerBackdrop.kt // prioritas: file lokal > remote > tidak ada
ui/NowPlayingScreen.kt                // pass remoteCanvasUrl ke backdrop
viewmodel/MusicViewModel.kt           // observe currentSong, debounce 600 ms, cancel per key
```

Catatan penting implementasi:

- **SongKey**: `yt:<remoteId>` atau `local:<judul|artis|durasi>` — negative cache 7 hari,
  mapping positif 30 hari, URL canvas 12 jam.
- **Skoring kandidat**: Levenshtein + Jaccard; wajib judul ≥0.72, artis ≥0.55,
  durasi ±5 s = bonus utama, > 15 s = tolak; total ≥ 0.60.
- **TOTP**: XOR-map nilai rahasia `((i % 33) + 9)` → **join desimal** (bukan char!),
  UTF-8 → hex → secret bytes; counter = `floor(ms/30 000)`; `totpServer` pakai
  `floor(serverMs/30)` semantik (mirroring implementasi ref).
- **Debounce**: skip cepat di-tunda 600 ms; hasil resolve diabaikan bila songKey sudah
  berubah saat resolve selesai (tidak ada canvas nyangkut dari lagu sebelumnya).
- **Canvas video surface**: `PlayerCanvasVideoSurface` sudah bisa menerima URL https
  langsung (`MediaItem.fromUri`), muted + REPEAT_MODE_ALL + RESIZE_MODE_ZOOM — tidak
  perlu komponen baru.

## 7.4 Risiko sisa & mitigasi

- Instans proxy `spotify-canva.vercel.app` itu milik komunitas — bisa mati; kalau itu
  terjadi, deploy sendiri (butuh `SP_DC`) dan ganti konstanta `CANVAS_PROXY_BASE`
  di `SpotifyCanvasApi`.
- `SEARCH_TRACKS_SHA` diambil dari bundle web-player publik hari ini; kalau Spotify
  merotasi hash, search degradasi mulus (tidak ada kandidat → tidak ada canvas,
  bukan crash).
- Secret dict TOTP (`xyloflake/spot-secrets-go`) di-refresh tiap ~1 jam; snapshot
  terakhir di-cache sebagai fallback kalau GitHub tidak tersedia.
- Token anonymous tinggal ~1 jam dan dibuat per-proses; tidak pernah disimpan/dikirim
  ke pihak lain.




## 8. SP_DC requirement (Oct 2026)

All public canvas REST proxies are dead or dying:
- canvas.canvasapi.workers.dev ? 404
- spotify-canva.vercel.app/api/canvas ? 500 `{"error":"Failed to fetch canvas data"}` for every id
- spotify-canvas.vercel.app ? 404 route gone
- api.paxsenix.org/api/canvas ? 401 (now requires API key)
- spclient canvaz-cache with ANONYMOUS TOTP token ? 200 but empty (no canvases)

Only working primary path: spclient `spclient.wg.spotify.com/canvaz-cache/v0/canvases`
POST protobuf `[0x0A, len, [0x0A, uriLen, entity_uri, 0x10, 0x04]]` with a token minted
from the USER'S sp_dc cookie (open.spotify.com/api/token TOTP flow + Cookie: sp_dc=...).
Response parsed by scanning printable ASCII for `https://...*.mp4` canvas URLs.

App wiring: Settings ? "Spotify Canvas (SP_DC)" stores `spotify_sp_dc`
(SettingsStore.spotifySpDc); SpotifyTokenManager.getAccessToken(spDc) caches per
credential scope; searchTrack(query, spDc) / fetchCanvas(trackId, spDc). Without
sp_dc the VAL pipeline short-circuits: verdict `no_sp_dc` cached 1h.

## 8. SP_DC requirement (Oct 2026)

All public canvas REST proxies are dead or dying:
- canvas.canvasapi.workers.dev -> 404
- spotify-canva.vercel.app/api/canvas -> 500 '{error:Failed to fetch canvas data}' every id
- spotify-canvas.vercel.app -> 404 route gone
- api.paxsenix.org/api/canvas -> 401 (requires API key now)
- spclient canvaz-cache with ANONYMOUS TOTP token -> 200 but empty

Only working primary path: POST spclient.wg.spotify.com/canvaz-cache/v0/canvases protobuf [0x0A len [0x0A uriLen entity_uri 0x10 0x04]] with token minted from USER sp_dc cookie (open.spotify.com/api/token TOTP flow + Cookie sp_dc). Response parsed by scanning printable ASCII for https://...*.mp4 canvas URLs.

App wiring: Settings -> 'Spotify Canvas (SP_DC)' stores spotify_sp_dc (SettingsStore.spotifySpDc); SpotifyTokenManager.getAccessToken(spDc) caches per credential scope; searchTrack(query, spDc) / fetchCanvas(trackId, spDc). Without sp_dc the resolve pipeline short-circuits: verdict no_sp_dc cached 1h.
