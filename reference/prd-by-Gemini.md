# Product Requirements Document (PRD): IanPlayer UI/UX & Engine Optimization

## 1. Tujuan Produk (Objective)
Mengoptimalkan aplikasi **IanPlayer** dengan mengadopsi elemen premium dari proyek **Convx** (Liquid Glass UI). Tujuan utamanya adalah menghadirkan pengalaman visual yang jauh lebih *smooth*, responsif (animasi berbasis *spring*/pegas ala iOS), memiliki efek *frosted glass* yang membiaskan cahaya secara realistis, serta menghadirkan fleksibilitas antarmuka lewat sistem *Multi-Layout*.

## 2. Fitur Utama (Key Features)
- **Liquid Glass UI & Canvas Refraction:** 
  - Mengubah transparansi layar yang *flat* menjadi refraksi objek (*Backdrop Blur*).
  - Elemen UI (*Nav Bar*, *Floating Buttons*, *Bottom Sheets*) akan membiaskan objek di bawahnya secara *real-time*.
- **Dynamic Layout Engine:** 
  - Sistem pengaturan baru di mana *user* bisa mengubah desain UI secara keseluruhan. 
  - **Opsi Layout:**
    1. **YT Music Layout:** Fokus pada komponen *Material You*, daftar navigasi yang ringkas, dan sistem *tabs* yang efisien.
    2. **Spotify Layout:** Menonjolkan *Cover Art* besar, dominasi *background* warna gelap, dan gradien berbasis ekstraksi warna sampul album.
    3. **Apple Music Layout:** Mengadaptasi gaya Convx (V17 Player). Fokus pada efek *Liquid Glass*, *bouncy animations*, dan *background* yang sepenuhnya ter-blur (*fullscreen blur*).
    4. **Base IanPlayer Layout:** Desain orisinal aplikasi dengan peningkatan animasi transisi (*smooth upgrade*).
- **Smooth Bouncy Transitions:** Menggantikan pergerakan kaku dengan *rubber-band overscroll* dan *blurred page transitions*.
- **Enhanced Synced Lyrics:** Sistem lirik ala Karaoke dengan transisi warna/pembesaran per baris atau kata yang presisi.

## 3. Spesifikasi Teknis (Technical Canvas Details dari Convx)
Untuk mencapai efek Convx di IanPlayer, pengembangan sisi Canvas akan fokus pada:
- **API Custom Backdrop:** Menggunakan `rememberLayerBackdrop()` untuk menangkap *pixel* aktual yang ter-render di UI.
- **Modifier Adaption:** Menerapkan `Modifier.layerBackdrop(...)` ke *subtree* yang relevan, terutama di bagian *Now Playing* dan navigasi.
- **Draw Phase:** Penggambaran akhir dilakukan menggunakan fungsi kustom `drawBackdrop(...)` di dalam *Canvas* untuk memproses efek *blur* berat tanpa mengganggu *framerate* (60-120fps).