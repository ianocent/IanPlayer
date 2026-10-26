#include "net/tidal_repository.h"

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QUrl>
#include <QUrlQuery>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>

static const QString kTidalToken = QStringLiteral("uXSp7m8679Yn9W9o");

TidalRepository::TidalRepository(QObject *parent) : QObject(parent) {}

void TidalRepository::searchSongs(const QString &query)
{
    QUrl url(QStringLiteral("https://api.tidal.com/v1/search/tracks"));
    QUrlQuery q;
    q.addQueryItem(QStringLiteral("query"), query);
    q.addQueryItem(QStringLiteral("limit"), QStringLiteral("20"));
    q.addQueryItem(QStringLiteral("countryCode"), QStringLiteral("ID"));
    url.setQuery(q);

    QNetworkRequest req(url);
    req.setRawHeader("x-tidal-token", kTidalToken.toUtf8());

    QNetworkReply *reply = m_nam.get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() != QNetworkReply::NoError) {
            emit searchFinished({}, reply->errorString());
            reply->deleteLater();
            return;
        }
        QList<TidalTrack> out;
        QJsonParseError pe{};
        const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
        if (pe.error == QJsonParseError::NoError && doc.isObject()) {
            const QJsonArray items = doc.object().value(QStringLiteral("items")).toArray();
            for (const QJsonValue &v : items) {
                const QJsonObject item = v.toObject();
                TidalTrack t;
                t.id = item.value(QStringLiteral("id")).toVariant().toLongLong();
                t.title = item.value(QStringLiteral("title")).toString();
                const QJsonArray artists = item.value(QStringLiteral("artists")).toArray();
                if (!artists.isEmpty())
                    t.artist = artists.at(0).toObject().value(QStringLiteral("name")).toString();
                if (t.artist.isEmpty())
                    t.artist = QStringLiteral("Unknown Artist");
                const QJsonObject album = item.value(QStringLiteral("album")).toObject();
                t.album = album.value(QStringLiteral("title")).toString();
                if (t.album.isEmpty())
                    t.album = QStringLiteral("Unknown Album");
                t.durationMs = item.value(QStringLiteral("duration")).toVariant().toLongLong() * 1000;
                QString cover = album.value(QStringLiteral("cover")).toString();
                if (!cover.isEmpty())
                    t.artUrl = QStringLiteral("https://resources.tidal.com/images/%1/1280x1280.jpg")
                                   .arg(cover.replace(QStringLiteral("-"), QStringLiteral("/")));
                out.append(t);
            }
        }
        emit searchFinished(out, {});
        reply->deleteLater();
    });
}
