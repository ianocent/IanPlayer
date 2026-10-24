#include "core/library_scanner.h"

#include "core/title_cleaner.h"

#include <QCryptographicHash>
#include <QDateTime>
#include <QDir>
#include <QFileInfo>
#include <QImage>
#include <QStack>
#include <QStandardPaths>

#include <QtConcurrent/QtConcurrent>
#include <taglib/mpegfile.h>
#include <taglib/attachedpictureframe.h>
#include <taglib/fileref.h>
#include <taglib/flacfile.h>
#include <taglib/flacpicture.h>
#include <taglib/id3v2tag.h>
#include <taglib/mp4file.h>
#include <taglib/mp4item.h>

#include <atomic>

namespace {

const QStringList kSystemFolderBlacklist = {
    QStringLiteral("System Volume Information"),
    QStringLiteral("$RECYCLE.BIN"),
    QStringLiteral("$Recycle.Bin"),
    QStringLiteral("$MSOCache"),
    QStringLiteral("Windows"),
    QStringLiteral("Program Files"),
    QStringLiteral("Program Files (x86)"),
    QStringLiteral("AppData"),
    QStringLiteral("Intel"),
    QStringLiteral("PerfLogs"),
    QStringLiteral("Recovery"),
    QStringLiteral("MSOCache"),
};

const QStringList kMusicExtensions = {
    QStringLiteral("mp3"), QStringLiteral("flac"), QStringLiteral("m4a"),
    QStringLiteral("aac"), QStringLiteral("wav"), QStringLiteral("ogg"),
    QStringLiteral("opus"),
};

QString tagTag(const TagLib::String &s)
{
    return s.isEmpty() ? QString() : QString::fromUtf8(s.toCString(true));
}

} // namespace

// ==== util ====

QSet<QString> LibraryScanner::supportedExtensions()
{
    return { kMusicExtensions.begin(), kMusicExtensions.end() };
}

QString LibraryScanner::thumbnailsDir()
{
    return QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation)
        + QStringLiteral("/thumbnails");
}

bool LibraryScanner::isExcludedDir(const QString &dirName)
{
    if (dirName.startsWith(QLatin1Char('.')))
        return true;
    return kSystemFolderBlacklist.contains(dirName, Qt::CaseInsensitive);
}

// ==== testable ====

std::optional<QPair<QString, QString>>
LibraryScanner::parseTaglessFileName(const QString &baseName)
{
    const int dashIndex = baseName.indexOf(QStringLiteral(" - "));
    if (dashIndex <= 0)
        return std::nullopt;

    const QString artist = baseName.left(dashIndex).trimmed();
    const QString title = baseName.mid(dashIndex + 3).trimmed();
    if (artist.isEmpty() || title.isEmpty())
        return std::nullopt;

    return QPair<QString, QString>{ artist, title };
}

// ==== TagLib ====

QImage LibraryScanner::embeddedArt(const QString &filePath, const QString &codec)
{
    const std::wstring widePath = filePath.toStdWString();

    if (codec == QLatin1String("mp3") || codec == QLatin1String("wav")) {
        TagLib::MPEG::File file(widePath.c_str());
        if (!file.isValid())
            return {};
        TagLib::ID3v2::Tag *tag = file.ID3v2Tag();
        if (!tag)
            return {};
        const TagLib::ID3v2::FrameList apic = tag->frameList("APIC");
        if (apic.isEmpty())
            return {};
        const auto *pic =
            static_cast<const TagLib::ID3v2::AttachedPictureFrame *>(apic.front());
        const TagLib::ByteVector &bytes = pic->picture();
        if (bytes.isEmpty())
            return {};
        QImage img = QImage::fromData(
            reinterpret_cast<const uchar *>(bytes.data()), int(bytes.size()));
        return img;
    } else if (codec == QLatin1String("flac")) {
        TagLib::FLAC::File file(widePath.c_str());
        if (!file.isValid())
            return {};
        const TagLib::List<TagLib::FLAC::Picture *> &pictures = file.pictureList();
        if (pictures.isEmpty())
            return {};
        const TagLib::ByteVector &bytes = pictures.front()->data();
        return QImage::fromData(
            reinterpret_cast<const uchar *>(bytes.data()), int(bytes.size()));
    } else if (codec == QLatin1String("m4a") || codec == QLatin1String("aac")) {
        TagLib::MP4::File file(widePath.c_str());
        if (!file.isValid() || !file.tag())
            return {};
        const TagLib::MP4::Item covr = file.tag()->item("covr");
        const TagLib::MP4::CoverArtList covers = covr.toCoverArtList();
        if (covers.isEmpty())
            return {};
        const TagLib::ByteVector &bytes = covers.front().data();
        return QImage::fromData(
            reinterpret_cast<const uchar *>(bytes.data()), int(bytes.size()));
    }
    return {};
}

QPair<QString, QString> LibraryScanner::saveThumbnail(const QString &albumKey,
                                                     const QImage &image,
                                                     const QString &thumbsDir)
{
    const QString hashHex = QString::fromLatin1(
        QCryptographicHash::hash(albumKey.toUtf8(), QCryptographicHash::Sha1).toHex());
    QDir().mkpath(thumbsDir);

    const QString path256 = thumbsDir + QStringLiteral("/%1_256.jpg").arg(hashHex);
    const QString path64 = thumbsDir + QStringLiteral("/%1_64.jpg").arg(hashHex);

    const QImage art256 = image.scaled(256, 256, Qt::KeepAspectRatio, Qt::SmoothTransformation);
    const QImage art64 = image.scaled(64, 64, Qt::KeepAspectRatio, Qt::SmoothTransformation);
    if (!art256.save(path256, "JPEG", 85) || !art64.save(path64, "JPEG", 85))
        return {};

    return { path256, path64 };
}

SongRecord LibraryScanner::readTagLib(const QString &filePath, const QString &codec)
{
    SongRecord rec;
    rec.path = filePath;
    rec.codec = codec;

    const QString baseName = QFileInfo(filePath).completeBaseName();
    rec.title = baseName;

    TagLib::FileRef ref(filePath.toStdWString().c_str());
    if (!ref.isNull()) {
        if (auto *ap = ref.audioProperties()) {
            rec.durationMs = ap->lengthInMilliseconds();
            rec.bitrate = ap->bitrate();
        }
        if (auto *tag = ref.tag()) {
            const QString title = tagTag(tag->title());
            const QString artist = tagTag(tag->artist());
            const QString album = tagTag(tag->album());
            if (!title.isEmpty())
                rec.title = title;
            if (!artist.isEmpty())
                rec.artist = artist;
            if (!album.isEmpty())
                rec.album = album;
            rec.trackNo = int(tag->track());
            rec.year = int(tag->year());
        }
    }

    // fallback port MusicRepository.kt 1:1: artist dari "Artist - Title"
    // hanya jika TagLib kosong; kalau tak ada dash valid -> Unknown Artist
    if (rec.artist.isEmpty()) {
        const auto parsed = parseTaglessFileName(baseName);
        if (parsed) {
            rec.artist = parsed->first;
            rec.title = parsed->second;   // nama file dipercaya: dash belakang
        } else {
            rec.artist = QStringLiteral("Unknown Artist");
        }
    }
    if (rec.album.isEmpty())
        rec.album = QStringLiteral("Unknown Album");

    // bersihkan akhiran promo "(Official Video)", "[Lyrics]", dsb. dari judul
    // hasil tag ATAU nama file (parity #30 target M1.1 — port SongTags.kt)
    rec.title = SongTags::cleanTitle(rec.title);

    return rec;
}

// ==== scan ====

LibraryScanner::ScanResult LibraryScanner::scanSync(
    const QStringList &roots,
    const QHash<QString, qint64> &knownModified,
    const QString &thumbsDir,
    const std::function<bool()> &cancelled,
    const std::function<void(int, int)> &progress)
{
    ScanResult result;

    // ---- 1) traverse & gunakan stack dir: hitung total supaya progress stabil
    QStack<QString> dirs;
    for (const QString &root : roots)
        dirs.push(QDir::fromNativeSeparators(root));

    QStringList alive;
    while (!dirs.isEmpty()) {
        if (cancelled()) {
            result.cancelled = true;
            return result;
        }
        const QString dirPath = dirs.pop();
        QDir dir(dirPath);
        if (!dir.exists())
            continue;
        const QFileInfoList items =
            dir.entryInfoList(QDir::Files | QDir::Dirs | QDir::NoDotAndDotDot | QDir::Readable);
        for (const QFileInfo &info : items) {
            if (info.isDir()) {
                if (!isExcludedDir(info.fileName()))
                    dirs.push(info.absoluteFilePath());
            } else if (kMusicExtensions.contains(info.suffix().toLower())) {
                alive.append(info.absoluteFilePath());
            }
        }
    }

    result.totalTracked = alive.size();
    result.alivePaths = alive;
    progress(0, result.totalTracked);

    // ---- 2) TagLib per file (incremental skip kalau modified masih fresh) ----
    int processed = 0;
    for (const QString &filePath : alive) {
        if (cancelled()) {
            result.cancelled = true;
            return result;
        }

        const QFileInfo info(filePath);
        const QString codec = info.suffix().toLower();
        const qint64 modifiedMs = info.lastModified().toMSecsSinceEpoch();

        const auto known = knownModified.constFind(filePath);
        if (known != knownModified.constEnd() && *known == modifiedMs) {
            ++processed;
            continue;
        }

        SongRecord rec = readTagLib(filePath, codec);
        rec.modifiedTs = modifiedMs;
        rec.addedTs = QDateTime::currentMSecsSinceEpoch();

        const QImage art = embeddedArt(filePath, codec);
        if (!art.isNull()) {
            const QString albumKey = QStringLiteral("%1|%2").arg(rec.album, rec.artist);
            const auto thumbs = saveThumbnail(albumKey, art, thumbsDir);
            if (!thumbs.first.isEmpty()) {
                rec.art256 = thumbs.first;
                rec.art64 = thumbs.second;
            }
        }
        result.songs.append(rec);

        ++processed;
        progress(processed, result.totalTracked);
    }

    // songs = hanya yang perlu di-upsert; totalTracked = semua yang hidup.
    result.newOrUpdated = result.songs.size();
    return result;
}

// ==== async wrapper ====

LibraryScanner::LibraryScanner(QObject *parent)
    : QObject(parent)
{
    connect(&m_watcher, &QFutureWatcher<ScanResult>::finished, this, [this] {
        m_scanning = false;
        const ScanResult result = m_watcher.result();
        if (result.cancelled) {
            emit scanCancelled();
            return;
        }
        emit scanCompleted(result.songs, result.alivePaths, result.totalTracked);
    });
}

void LibraryScanner::cancelScan()
{
    m_cancelRequested.store(true);
}

void LibraryScanner::scanAsync(const QStringList &roots,
                               const QHash<QString, qint64> &knownModified)
{
    if (m_scanning || roots.isEmpty())
        return;
    m_scanning = true;
    m_cancelRequested.store(false);
    emit scanningStarted();

    const QString thumbsDir = thumbnailsDir();

    auto cancelled = [this]() -> bool { return m_cancelRequested.load(); };
    auto progress = [this](int current, int total) {
        emit progressChanged(current, total);
    };

    const QFuture<ScanResult> future = QtConcurrent::run([=]() -> ScanResult {
        return scanSync(roots, knownModified, thumbsDir, cancelled, progress);
    });
    m_watcher.setFuture(future);
}
