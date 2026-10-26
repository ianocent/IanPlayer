#include "net/innertube_client.h"

#include <QNetworkRequest>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QUrl>
#include <QUrlQuery>
#include <QFile>
#include <QDir>
#include <QStandardPaths>
#include <QRegularExpression>
#include <QDate>
#include <QSet>
#include <functional>

namespace {

QString findApiKey()
{
    // 1) env
    const QByteArray env = qgetenv("INNERTUBE_API_KEY");
    if (!env.isEmpty())
        return QString::fromUtf8(env);

    // 2) keys.properties di AppLocalDataLocation / cwd
    QStringList candidates;
    candidates << (QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + QStringLiteral("/keys.properties"));
    candidates << (QDir::currentPath() + QStringLiteral("/keys.properties"));
    for (const QString &p : candidates) {
        QFile f(p);
        if (!f.open(QIODevice::ReadOnly | QIODevice::Text))
            continue;
        while (!f.atEnd()) {
            const QString line = QString::fromUtf8(f.readLine()).trimmed();
            if (line.startsWith(QStringLiteral("innertube_api_key=")) ||
                line.startsWith(QStringLiteral("youtube_api_key="))) {
                return line.section('=', 1).trimmed();
            }
        }
    }
    return {};
}

QJsonObject baseContext()
{
    QJsonObject client;
    client.insert(QStringLiteral("clientName"), QStringLiteral("WEB_REMIX"));
    client.insert(QStringLiteral("clientVersion"), QStringLiteral("1.20240101.01.00"));
    client.insert(QStringLiteral("hl"), QStringLiteral("en"));
    client.insert(QStringLiteral("gl"), QStringLiteral("US"));
    QJsonObject ctx;
    ctx.insert(QStringLiteral("client"), client);
    return ctx;
}

} // namespace

InnertubeClient::InnertubeClient(QObject *parent)
    : QObject(parent)
    , m_apiKey(findApiKey())
{
}

QList<InnertubeClient::Category> InnertubeClient::genres()
{
    const int year = QDate::currentDate().year();
    QList<Category> out;
    const QList<QPair<QString, QString>> data = {
        {QStringLiteral("Pop"), QStringLiteral("popular pop songs %1 hits").arg(year)},
        {QStringLiteral("Rock"), QStringLiteral("rock music hits modern classic")},
        {QStringLiteral("Hip Hop"), QStringLiteral("hip hop rap songs hits")},
        {QStringLiteral("R&B"), QStringLiteral("r&b soul music songs")},
        {QStringLiteral("Electronic"), QStringLiteral("edm dance electronic music")},
        {QStringLiteral("Jazz"), QStringLiteral("jazz music classics")},
        {QStringLiteral("Indie"), QStringLiteral("indie alternative rock songs")},
        {QStringLiteral("Metal"), QStringLiteral("heavy metal hard rock songs")},
        {QStringLiteral("K-Pop"), QStringLiteral("kpop korean pop songs hits")},
        {QStringLiteral("Reggae"), QStringLiteral("reggae music chill vibes")},
        {QStringLiteral("Country"), QStringLiteral("country music hits popular")},
        {QStringLiteral("Lofi"), QStringLiteral("lofi hip hop beats relaxing")},
    };
    for (const auto &d : data) {
        Category c; c.name = d.first; c.query = d.second; c.isMood = false; out.append(c);
    }
    return out;
}

QList<InnertubeClient::Category> InnertubeClient::moods()
{
    const QList<QPair<QString, QString>> data = {
        {QStringLiteral("Sad"), QStringLiteral("sad emotional pop rock songs hits")},
        {QStringLiteral("Energetic"), QStringLiteral("upbeat energy workout songs")},
        {QStringLiteral("Chill"), QStringLiteral("chill lofi relaxing music")},
        {QStringLiteral("Happy"), QStringLiteral("happy feel good songs")},
        {QStringLiteral("Romantic"), QStringLiteral("romantic love songs hits")},
        {QStringLiteral("Dark"), QStringLiteral("dark moody aesthetic songs")},
        {QStringLiteral("Calm"), QStringLiteral("calm acoustic peaceful music")},
        {QStringLiteral("Focus"), QStringLiteral("focus study instrumental music")},
        {QStringLiteral("Party"), QStringLiteral("party dance hits banger songs")},
        {QStringLiteral("Nostalgia"), QStringLiteral("nostalgic throwback classic hits")},
        {QStringLiteral("Heartbreak"), QStringLiteral("heartbreak breakup sad songs")},
        {QStringLiteral("Confident"), QStringLiteral("confident boss energy songs")},
    };
    QList<Category> out;
    for (const auto &d : data) {
        Category c; c.name = d.first; c.query = d.second; c.isMood = true; out.append(c);
    }
    return out;
}

bool InnertubeClient::isIndianContent(const QString &title, const QString &artist)
{
    static const QRegularExpression re(
        QStringLiteral("\\b(hindi|tamil|telugu|punjabi|bollywood|desi)\\b"),
        QRegularExpression::CaseInsensitiveOption);
    return re.match(title + QStringLiteral(" ") + artist).hasMatch();
}

bool InnertubeClient::isNoise(const QString &title, const QString &artist)
{
    // noise filter: compilation/karaoke + konten India
    static const QRegularExpression noiseRe(
        QStringLiteral("compilation|karaoke|8d audio|slowed|reverb|nightcore"),
        QRegularExpression::CaseInsensitiveOption);
    if (noiseRe.match(title).hasMatch())
        return true;
    if (isIndianContent(title, artist))
        return true;
    // empty title guard
    if (title.trimmed().isEmpty())
        return true;
    return false;
}

QString InnertubeClient::apiKey()
{
    // static-ish: create temp client
    return findApiKey();
}

void InnertubeClient::search(const QString &query, int limit)
{
    QJsonObject body;
    body.insert(QStringLiteral("context"), baseContext());
    body.insert(QStringLiteral("query"), query);
    body.insert(QStringLiteral("params"), QStringLiteral("EgIQAQ%3D%3D")); // songs filter

    post(QStringLiteral("search"), body, [this, limit](const QJsonObject &root, const QString &err) {
        if (!err.isEmpty()) {
            emit searchFinished({}, err);
            return;
        }
        auto songs = parseSearch(root);
        if (songs.size() > limit)
            songs = songs.mid(0, limit);
        emit searchFinished(songs, {});
    });
}

void InnertubeClient::browseHome()
{
    QJsonObject body;
    body.insert(QStringLiteral("context"), baseContext());
    body.insert(QStringLiteral("browseId"), QStringLiteral("FEmusic_trending"));

    post(QStringLiteral("browse"), body, [this](const QJsonObject &root, const QString &err) {
        if (!err.isEmpty()) {
            emit browseFinished({}, err);
            return;
        }
        emit browseFinished(parseSearch(root), {});
    });
}

void InnertubeClient::fetchRelated(const QString &videoId)
{
    QJsonObject body;
    body.insert(QStringLiteral("context"), baseContext());
    body.insert(QStringLiteral("videoId"), videoId);

    post(QStringLiteral("next"), body, [this](const QJsonObject &root, const QString &err) {
        if (!err.isEmpty()) {
            emit relatedFinished({}, err);
            return;
        }
        emit relatedFinished(parseSearch(root), {});
    });
}

void InnertubeClient::post(const QString &endpoint, const QJsonObject &body,
                          std::function<void(const QJsonObject &, const QString &)> cb)
{
    QUrl url(QStringLiteral("https://music.youtube.com/youtubei/v1/%1").arg(endpoint));
    QUrlQuery q;
    if (!m_apiKey.isEmpty())
        q.addQueryItem(QStringLiteral("key"), m_apiKey);
    url.setQuery(q);

    QNetworkRequest req(url);
    req.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    req.setRawHeader("X-Goog-Api-Format-Version", "1");
    req.setRawHeader("Origin", "https://music.youtube.com");

    QNetworkReply *reply = m_nam.post(req, QJsonDocument(body).toJson(QJsonDocument::Compact));
    QObject::connect(reply, &QNetworkReply::finished, this, [reply, cb]() {
        if (reply->error() != QNetworkReply::NoError) {
            cb({}, reply->errorString());
            reply->deleteLater();
            return;
        }
        QJsonParseError pe{};
        const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
        if (pe.error != QJsonParseError::NoError || !doc.isObject()) {
            cb({}, QStringLiteral("invalid json"));
            reply->deleteLater();
            return;
        }
        cb(doc.object(), {});
        reply->deleteLater();
    });
}

QList<InnertubeSong> InnertubeClient::parseSearch(const QJsonObject &root)
{
    QList<InnertubeSong> out;
    // Walk known containers: musicShelfRenderer / musicCardShelfRenderer contents
    std::function<void(const QJsonValue &)> walk = [&](const QJsonValue &v) {
        if (v.isObject()) {
            const QJsonObject o = v.toObject();
            // musicResponsiveListItemRenderer
            if (o.contains(QStringLiteral("musicResponsiveListItemRenderer"))) {
                const QJsonObject item = o.value(QStringLiteral("musicResponsiveListItemRenderer")).toObject();
                InnertubeSong s;
                // title
                const QJsonArray flex = item.value(QStringLiteral("flexColumns")).toArray();
                if (flex.size() > 0) {
                    const QJsonObject col0 = flex.at(0).toObject()
                        .value(QStringLiteral("musicResponsiveListItemFlexColumnRenderer")).toObject();
                    const QJsonArray runs = col0.value(QStringLiteral("text")).toObject()
                        .value(QStringLiteral("runs")).toArray();
                    if (!runs.isEmpty())
                        s.title = runs.at(0).toObject().value(QStringLiteral("text")).toString();
                }
                if (flex.size() > 1) {
                    const QJsonObject col1 = flex.at(1).toObject()
                        .value(QStringLiteral("musicResponsiveListItemFlexColumnRenderer")).toObject();
                    const QJsonArray runs = col1.value(QStringLiteral("text")).toObject()
                        .value(QStringLiteral("runs")).toArray();
                    QStringList parts;
                    for (const QJsonValue &r : runs)
                        parts << r.toObject().value(QStringLiteral("text")).toString();
                    s.artist = parts.join(QString()).trimmed();
                }
                // videoId
                const QJsonObject nav = item.value(QStringLiteral("navigationEndpoint")).toObject();
                const QJsonObject watch = nav.value(QStringLiteral("watchEndpoint")).toObject();
                s.videoId = watch.value(QStringLiteral("videoId")).toString();
                if (s.videoId.isEmpty()) {
                    const QJsonObject pe = item.value(QStringLiteral("playlistItemData")).toObject();
                    s.videoId = pe.value(QStringLiteral("videoId")).toString();
                }
                // thumbnail
                const QJsonObject thumb = item.value(QStringLiteral("thumbnail")).toObject()
                    .value(QStringLiteral("musicThumbnailRenderer")).toObject()
                    .value(QStringLiteral("thumbnail")).toObject();
                const QJsonArray thumbs = thumb.value(QStringLiteral("thumbnails")).toArray();
                if (!thumbs.isEmpty())
                    s.thumbnailUrl = thumbs.last().toObject().value(QStringLiteral("url")).toString();
                // duration (fixedColumns)
                const QJsonArray fixed = item.value(QStringLiteral("fixedColumns")).toArray();
                if (!fixed.isEmpty()) {
                    const QJsonObject fc = fixed.at(0).toObject()
                        .value(QStringLiteral("musicResponsiveListItemFixedColumnRenderer")).toObject();
                    const QJsonArray truns = fc.value(QStringLiteral("text")).toObject()
                        .value(QStringLiteral("runs")).toArray();
                    if (!truns.isEmpty()) {
                        const QString dur = truns.at(0).toObject().value(QStringLiteral("text")).toString();
                        // parse mm:ss
                        const QStringList mmss = dur.split(':');
                        if (mmss.size() == 2) {
                            s.durationMs = (mmss.at(0).toInt() * 60 + mmss.at(1).toInt()) * 1000LL;
                        }
                    }
                }

                if (!s.videoId.isEmpty() && !isNoise(s.title, s.artist)) {
                    s.album = QStringLiteral("");
                    out.append(s);
                }
            }
            for (auto it = o.begin(); it != o.end(); ++it)
                walk(it.value());
        } else if (v.isArray()) {
            for (const QJsonValue &e : v.toArray())
                walk(e);
        }
    };
    walk(root);
    // dedup by videoId
    QSet<QString> seen;
    QList<InnertubeSong> dedup;
    for (const auto &s : out) {
        if (seen.contains(s.videoId))
            continue;
        seen.insert(s.videoId);
        dedup.append(s);
    }
    return dedup;
}
