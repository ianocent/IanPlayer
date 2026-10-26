#pragma once

#include <QObject>
#include <QString>
#include <QList>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <functional>
#include <QJsonObject>

struct InnertubeSong
{
    QString title;
    QString artist;
    QString album;
    qint64 durationMs = 0;
    QString videoId;
    QString thumbnailUrl;
    bool isStream = true;
};

// InnerTube client (M3.2): POST JSON ke youtubei/v1/*, client WEB_REMIX.
// Pure helpers (noise filter, genre/mood lists) testable tanpa network.
class InnertubeClient : public QObject
{
    Q_OBJECT
public:
    struct Category { QString name; QString query; bool isMood = false; };

    explicit InnertubeClient(QObject *parent = nullptr);

    static QList<Category> genres();
    static QList<Category> moods();
    static bool isNoise(const QString &title, const QString &artist);
    static bool isIndianContent(const QString &title, const QString &artist);

    // API key dari property file / env; kosong kalau tidak ada (request mungkin 403).
    static QString apiKey();

    // network (async via signal)
    void search(const QString &query, int limit = 20);
    void browseHome();
    void fetchRelated(const QString &videoId);

signals:
    void searchFinished(const QList<InnertubeSong> &songs, const QString &error);
    void browseFinished(const QList<InnertubeSong> &songs, const QString &error);
    void relatedFinished(const QList<InnertubeSong> &songs, const QString &error);

private:
    void post(const QString &endpoint, const QJsonObject &body,
              std::function<void(const QJsonObject &, const QString &)> cb);
    QList<InnertubeSong> parseSearch(const QJsonObject &root);
    QNetworkAccessManager m_nam;
    QString m_apiKey;
};
