\# Batch Issues — IanPlayer \& Repo Private



Format per issue:

```

\### REPO: owner/repo-name

TITLE: judul issue

LABELS: label1,label2

\---

Body issue (boleh multiline)

\---

```



Isi list di bawah ini, terus jalanin `create-issues.sh` buat auto-create semua via `gh` CLI.



\---



\### REPO: ianocent/IanPlayer

TITLE: Fix lyric sync delay on slow/low-end devices

LABELS: bug,performance

\---

Lyric animation kadang telat sync sama audio playback di device low-end.

Kemungkinan root cause di coroutine lifecycle / ptsUs timing calculation.



\*\*Steps to reproduce:\*\*

1\. Buka lagu yang punya synced lyrics

2\. Play di device low-end / emulator low spec

3\. Lyric keliatan delay \~300-500ms dari audio



\*\*Expected:\*\* lyric muncul tepat sesuai timestamp audio.

\---



\### REPO: ianocent/IanPlayer

TITLE: WaveRecordSheet screen recording notification not compatible on Android 14+

LABELS: bug,android

\---

Notification compat buat WaveRecordSheet feature error di Android 14 ke atas.

Perlu cek `notification channel` \& foreground service type declaration.

\---



\### REPO: ianocent/IanPlayer

TITLE: Room DB cache stream URL expired handling

LABELS: enhancement

\---

Cached stream URL dari YouTube Music (InnerTube) kadang expired tapi masih dipake dari Room DB cache, hasilnya playback gagal.



\*\*Proposal:\*\* tambahin TTL / expiry check sebelum pake cached URL, auto-refetch kalo expired.

\---



\### REPO: ianocent/IanPlayer

TITLE: RecapCardSheet monthly recap — add share as image feature

LABELS: enhancement,feature

\---

Recap card sekarang cuma bisa dilihat di app. Tambahin fitur export/share sebagai image (PNG) biar bisa di-share ke social media, mirip Spotify Wrapped.

\---



\### REPO: ianocent/IanPlayer

TITLE: Animated gradient background causes battery drain on long sessions

LABELS: bug,performance

\---

Animated linear gradient background di background player kemungkinan jalan terus-terusan walau app di-minimize, perlu pause pas app di background/lifecycle onPause.

\---



<!-- ============================================= -->

<!-- REPO PRIVATE — ganti nama repo sesuai punya lu -->

<!-- ============================================= -->



\### REPO: ianocent/hmsFrontend

TITLE: DraggableTableView — reservation drag-drop occasionally drops on wrong room row

LABELS: bug

\---

Drag-drop reservation di `DraggableTableView` kadang nge-drop ke row room yang salah kalo scroll position lagi di tengah-tengah table.



\*\*Steps to reproduce:\*\*

1\. Scroll table ke tengah

2\. Drag reservation card

3\. Drop ke row target



\*\*Expected:\*\* drop selalu akurat ke row yang di-hover.

\---



\### REPO: ianocent/hmsFrontend

TITLE: ListView search field loses value on route change

LABELS: bug

\---

Search field di `ListView.tsx` reset value-nya kalo user pindah route terus balik lagi ke halaman yang sama. Perlu persist search state (context/query param).

\---



\### REPO: ianocent/hmsBackend

TITLE: STAAH webhook — property payload "Array to string conversion" error on multi-room push

LABELS: bug,staah

\---

`StaahWebhookService` throw error "Array to string conversion" kalo payload property punya lebih dari 1 room type dalam satu push. Perlu audit format payload sebelum diproses ke `StaahRoomMapping`.

\---



\### REPO: ianocent/hmsBackend

TITLE: PDF report — RoomTypeRevenue raw SQL CTE not respecting property scope on edge case

LABELS: bug,report

\---

`RoomTypeRevenue` report controller pake raw SQL CTE, kadang gak ikut `is\_physical`/`onlyActive()` scope replication dengan bener di edge case multi-property. Perlu unit test tambahan buat property isolation.

\---



\### REPO: ianocent/suzuki-bit

TITLE: Promo popup component overflow on small viewport

LABELS: bug,ui

\---

Promo popup component resize gak proporsional di viewport < 375px, bikin tombol close ketutup.

