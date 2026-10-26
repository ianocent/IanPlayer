# Package IanPlayer PC as portable zip (M6.3).
# Run from ianplayer-pc directory in VS dev shell or with cmake on PATH.
param(
    [string]$QtDir = "C:\Qt\6.8.3\msvc2022_64",
    [string]$MpvDir = $env:MPV_DIR
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $root
Set-Location $root

if (-not $MpvDir) { $MpvDir = "C:\mpv" }

Write-Host "Configuring release..."
cmake --preset release

Write-Host "Building release..."
cmake --build --preset release

$build = Join-Path $root "build\release\Release"
$dist = Join-Path $root "dist\IanPlayer-Portable"
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

Copy-Item (Join-Path $build "ianplayer.exe") $dist
Copy-Item (Join-Path $build "tag.dll") $dist -ErrorAction SilentlyContinue
Copy-Item (Join-Path $build "zd.dll") $dist -ErrorAction SilentlyContinue

# Qt deploy
$windeploy = Join-Path $QtDir "bin\windeployqt.exe"
& $windeploy --release --no-translations --qmldir (Join-Path $root "src\ui") (Join-Path $dist "ianplayer.exe")

# mpv
if (Test-Path (Join-Path $MpvDir "libmpv-2.dll")) {
    Copy-Item (Join-Path $MpvDir "libmpv-2.dll") $dist
} elseif (Test-Path (Join-Path $MpvDir "mpv-2.dll")) {
    Copy-Item (Join-Path $MpvDir "mpv-2.dll") $dist
}
if (Test-Path (Join-Path $MpvDir "mpv.exe")) {
    # mpv binary not required; ensure dll present
}

# yt-dlp
$ytdlp = Join-Path $root "third_party\ytdlp\yt-dlp.exe"
if (Test-Path $ytdlp) { Copy-Item $ytdlp $dist }

# zip
$ver = "0.1.0"
$zip = Join-Path $root "dist\IanPlayer-Portable-$ver.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path (Join-Path $dist "*") -DestinationPath $zip

Write-Host "Portable package ready: $zip"
