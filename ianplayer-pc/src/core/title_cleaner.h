#pragma once

#include <QString>
#include <QPair>

// Port 1:1 SongTags.kt (Android) bagian title cleaning + resolve (parity #30,
// target M1.1 + M4.3). Pure & testable — hanya QString/QRegularExpression,
// tidak menyentuh DB maupun TagLib.
namespace SongTags {

/** Strip akhiran promo "(Official Video)", "[Lyrics]", "- Official Audio",
 *  "| Music Video", dsb. dari judul. Kalau hasil kosong -> kembali ke raw. */
QString cleanTitle(const QString &rawTitle);

/** Artist junk ("", "Unknown ...", "YouTube", "Various ...") */
bool isJunkArtist(const QString &artist);

/** Split judul gabungan "Artist - Title" + bersihkan nama channel
 *  ("Ed Sheeran - Topic" / "EdSheeranVEVO"). Kembalikan (title, artist).
 *  Kalau artist terpercaya sudah diketahui, urutan "Song - Artist" dari
 *  re-upload YouTube ("Without Me - Eminem") diresolve dengan mencabut
 *  " - Artist" suffix (atau "Artist - " prefix) dari judul — tidak menebak. */
QPair<QString, QString> resolve(const QString &title, const QString &artist);

} // namespace SongTags
