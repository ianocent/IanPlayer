#include "app/downloader_service.h"

#include "app/library_service.h"
#include "net/stream_url_resolver.h"

#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>
#include <QStandardPaths>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QUrl>
#include <QUrlQuery>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QRegularExpression>

#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <taglib/mpegfile.h>
#include <taglib/id3v2tag.h>
#include <taglib/attachedpictureframe.h>
#include <taglib/mp4file.h>
#include <taglib/mp4tag.h>
#include <taglib/mp4coverart.h>

DownloaderService::DownloaderService(LibraryService *library, StreamUrlResolver *resolver, QObject *parent)
    : QObject(parent)
    , m_library(library)
    , m_resolver(resolver)
{
}

static QString sanitizeFileName(const QString &name)
{
    QString out = name;
    out.replace(QRegularExpression(QStringLiteral("[\\\\/:*?\"<>|]")), QStringLiteral("_"));
    if (out.trimmed().isEmpty())
        out = QStringLiteral("track");
    return out.trimmed();
}

void DownloaderService::downloadStream(const QString &videoId, const QString &title, const QString &artist)
{
    if (!m_resolver) {
        emit downloadError(QStringLiteral("resolver tidak tersedia"));
        return;
    }
    auto resolved = m_resolver->getStreamUrl(QUrl(QStringLiteral("https://www.youtube.com/watch?v=%1").arg(videoId)));
    if (!resolved) {
        emit downloadError(QStringLiteral("gagal resolve stream URL"));
        return;
    }

    const QString dir = QStandardPaths::writableLocation(QStandardPaths::MusicLocation) + QStringLiteral("/IanPlayer");
    QDir().mkpath(dir);
    const QString path = dir + QStringLiteral("/") + sanitizeFileName(title) + QStringLiteral(".m4a");

    doDownload(resolved->url, path, title, artist);
}

void DownloaderService::doDownload(const QString &url, const QString &destPath, const QString &title, const QString &artist)
{
    QNetworkAccessManager *nam = new QNetworkAccessManager(this);
    QNetworkRequest req(url);
    QNetworkReply *reply = nam->get(req);

    QFile *file = new QFile(destPath, this);
    if (!file->open(QIODevice::WriteOnly)) {
        emit downloadError(QStringLiteral("tidak bisa buka file tujuan"));
        reply->abort();
        reply->deleteLater();
        nam->deleteLater();
        file->deleteLater();
        return;
    }

    QObject::connect(reply, &QNetworkReply::readyRead, this, [reply, file]() {
        file->write(reply->readAll());
    });
    QObject::connect(reply, &QNetworkReply::downloadProgress, this, &DownloaderService::downloadProgress);

    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, file, nam, destPath, title, artist]() {
        file->write(reply->readAll());
        file->close();
        const bool ok = (reply->error() == QNetworkReply::NoError);
        const QString err = reply->errorString();
        reply->deleteLater();
        nam->deleteLater();
        file->deleteLater();

        if (!ok) {
            QFile::remove(destPath);
            emit downloadError(err);
            return;
        }
        enrichAndTag(destPath, title, artist);
    });
}

void DownloaderService::enrichAndTag(const QString &path, const QString &title, const QString &artist)
{
    // Enrich via iTunes Search API (async), then write tags.
    QNetworkAccessManager *nam = new QNetworkAccessManager(this);
    QUrl url(QStringLiteral("https://itunes.apple.com/search"));
    QUrlQuery q;
    q.addQueryItem(QStringLiteral("term"), title + QStringLiteral(" ") + artist);
    q.addQueryItem(QStringLiteral("media"), QStringLiteral("music"));
    q.addQueryItem(QStringLiteral("limit"), QStringLiteral("1"));
    url.setQuery(q);

    QNetworkRequest req(url);
    QNetworkReply *reply = nam->get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, nam, path, title, artist]() {
        QString album = QStringLiteral("Unknown Album");
        int year = 0;
        int track = 0;
        QString coverUrl;

        if (reply->error() == QNetworkReply::NoError) {
            QJsonParseError pe{};
            const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
            if (pe.error == QJsonParseError::NoError && doc.isObject()) {
                const QJsonArray results = doc.object().value(QStringLiteral("results")).toArray();
                if (!results.isEmpty()) {
                    const QJsonObject r = results.at(0).toObject();
                    album = r.value(QStringLiteral("collectionName")).toString(album);
                    track = r.value(QStringLiteral("trackNumber")).toInt(track);
                    const QString release = r.value(QStringLiteral("releaseDate")).toString();
                    if (release.length() >= 4)
                        year = release.left(4).toInt();
                    coverUrl = r.value(QStringLiteral("artworkUrl100")).toString();
                    // upgrade to 600x600
                    coverUrl.replace(QStringLiteral("100x100bb"), QStringLiteral("600x600bb"));
                }
            }
        }
        reply->deleteLater();
        nam->deleteLater();

        // write tags
        {
            TagLib::FileRef f(path.toUtf8().constData());
            if (!f.isNull() && f.tag()) {
                TagLib::Tag *tag = f.tag();
                tag->setTitle(TagLib::String(title.toStdWString()));
                tag->setArtist(TagLib::String(artist.toStdWString()));
                tag->setAlbum(TagLib::String(album.toStdWString()));
                if (year > 0) tag->setYear(year);
                if (track > 0) tag->setTrack(track);
                f.save();
            }
        }

        // cover art
        if (!coverUrl.isEmpty()) {
            QNetworkAccessManager *nam2 = new QNetworkAccessManager(this);
            QNetworkReply *creply = nam2->get(QNetworkRequest(QUrl(coverUrl)));
            QObject::connect(creply, &QNetworkReply::finished, this, [this, creply, nam2, path]() {
                if (creply->error() == QNetworkReply::NoError) {
                    const QByteArray data = creply->readAll();
                    const bool isMp4 = path.endsWith(QStringLiteral(".m4a"), Qt::CaseInsensitive) ||
                                       path.endsWith(QStringLiteral(".mp4"), Qt::CaseInsensitive);
                    if (isMp4) {
                        TagLib::MP4::File f(path.toUtf8().constData());
                        if (f.isValid() && f.tag()) {
                            TagLib::MP4::CoverArtList covers;
                            covers.append(TagLib::MP4::CoverArt(TagLib::MP4::CoverArt::JPEG,
                                TagLib::ByteVector(data.constData(), data.size())));
                            f.tag()->setItem("covr", TagLib::MP4::Item(covers));
                            f.save();
                        }
                    } else {
                        TagLib::MPEG::File f(path.toUtf8().constData());
                        if (f.isValid()) {
                            TagLib::ID3v2::Tag *tag = f.ID3v2Tag(true);
                            if (tag) {
                                TagLib::ID3v2::AttachedPictureFrame *frame =
                                    new TagLib::ID3v2::AttachedPictureFrame;
                                frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
                                frame->setMimeType("image/jpeg");
                                frame->setPicture(TagLib::ByteVector(data.constData(), data.size()));
                                tag->addFrame(frame);
                                f.save();
                            }
                        }
                    }
                }
                creply->deleteLater();
                nam2->deleteLater();

                if (m_library) {
                    m_library->addRoot(QFileInfo(path).absolutePath());
                    m_library->rescanNow();
                }
                emit downloadFinished(path);
            });
        } else {
            if (m_library) {
                m_library->addRoot(QFileInfo(path).absolutePath());
                m_library->rescanNow();
            }
            emit downloadFinished(path);
        }
    });
}
