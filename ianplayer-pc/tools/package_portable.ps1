# Package IanPlayer PC as portable zip (M6.3).
# Run from ianplayer-pc directory in VS dev shell or with cmake on PATH.
param(
    [string]$QtDir = "C:\Qt\6.8.3\msvc2022_64",
    [string]$MpvDir = "",
    [string]$Version = "6.3.4"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $root
Set-Location $root

if (-not $MpvDir) {
    $MpvDir = Join-Path $root "third_party\mpv"
}

Write-Host "Configuring release preset..."
cmake --preset release

Write-Host "Building release preset..."
cmake --build --preset release

$build = Join-Path $root "build\release\Release"
if (-not (Test-Path $build)) {
    # Fallback if single-config or alternate build folder
    $build = Join-Path $root "build\release"
}

$dist = Join-Path $root "dist\IanPlayer-Portable"
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

Write-Host "Copying core application files..."
Copy-Item (Join-Path $build "ianplayer.exe") $dist
if (Test-Path (Join-Path $build "tag.dll")) { Copy-Item (Join-Path $build "tag.dll") $dist }
if (Test-Path (Join-Path $build "zd.dll")) { Copy-Item (Join-Path $build "zd.dll") $dist }

# Qt deploy
$windeploy = Join-Path $QtDir "bin\windeployqt.exe"
if (-not (Test-Path $windeploy)) {
    throw "windeployqt.exe not found at $windeploy"
}

Write-Host "Running windeployqt..."
& $windeploy --release --no-translations --qmldir (Join-Path $root "src\ui") (Join-Path $dist "ianplayer.exe")

# mpv
$mpvDll = Join-Path $build "libmpv-2.dll"
if (-not (Test-Path $mpvDll)) {
    $mpvDll = Join-Path $MpvDir "lib\x86_64\libmpv-2.dll"
}
if (Test-Path $mpvDll) {
    Copy-Item $mpvDll $dist
} else {
    throw "libmpv-2.dll not found in build directory ($build) or third_party ($MpvDir)"
}

# yt-dlp
$ytdlp = Join-Path $build "yt-dlp.exe"
if (-not (Test-Path $ytdlp)) {
    $ytdlp = Join-Path $root "third_party\ytdlp\yt-dlp.exe"
}
if (Test-Path $ytdlp) {
    Copy-Item $ytdlp $dist
} else {
    throw "yt-dlp.exe not found"
}

# Create README-PORTABLE.txt
$readmeContent = @"
==================================================
  IanPlayer PC v$Version — Portable Release
==================================================

Prinsip Portable:
- Aplikasi ini bersifat PORTABLE (tanpa instalasi).
- Seluruh data aplikasi (Database SQLite, cache thumbnail, cache lirik, dan pengaturannya) disimpan secara aman di folder per-user Windows:
  %LOCALAPPDATA%\IanPlayer\

Petunjuk Penggunaan:
1. Rekomendasi Lokasi Folder:
   Ekstrak folder IanPlayer-Portable ini di tempat yang memiliki hak akses tulis, seperti:
   - C:\Users\<Username>\Desktop\
   - C:\Users\<Username>\Downloads\
   - D:\IanPlayer\
   Catatan: Hindari menaruh folder ini di C:\Program Files karena Windows memerlukan hak akses Administrator untuk update otomatis yt-dlp / aplikasi.

2. Cara Menjalankan:
   Dobel klik `ianplayer.exe` untuk menjalankan aplikasi.

3. Fitur Integrasi Windows (Opsional):
   - Asosiasi File (.mp3, .flac, .m4a, .ogg, .opus, .wav) dan Protokol URL (ianplayer://) dapat diaktifkan melalui menu Pengaturan (⚙ Settings) di dalam aplikasi tanpa perlu installer.

4. Dependensi Komponen:
   - libmpv-2.dll (Audio playback engine)
   - yt-dlp.exe (YouTube stream extractor)
   - TagLib / ZLib DLL (Metadata tagging)
   Jangan menghapus file DLL atau exe yang ada di folder ini agar aplikasi dapat berjalan secara normal.

Terima kasih telah menggunakan IanPlayer PC!
==================================================
"@

$readmeContent | Out-File -FilePath (Join-Path $dist "README-PORTABLE.txt") -Encoding utf8

# Verification check
$essentialFiles = @("ianplayer.exe", "libmpv-2.dll", "yt-dlp.exe", "tag.dll", "zd.dll", "README-PORTABLE.txt")
foreach ($file in $essentialFiles) {
    $targetPath = Join-Path $dist $file
    if (-not (Test-Path $targetPath)) {
        Write-Warning "Missing optional or essential file in portable dist: $file"
    } else {
        Write-Host "Verified: $file present."
    }
}

# Create Zip package
$distParent = Join-Path $root "dist"
$zip = Join-Path $distParent "IanPlayer-Portable-$Version.zip"
if (Test-Path $zip) { Remove-Item $zip }

Write-Host "Compressing archive to $zip ..."
Compress-Archive -Path (Join-Path $dist "*") -DestinationPath $zip

Write-Host "Portable package successfully created at: $zip"
