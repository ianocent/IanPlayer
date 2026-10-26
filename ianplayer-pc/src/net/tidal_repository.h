#pragma once

#include <QObject>
#include <QString>
#include <QList>
#include <QNetworkAccessManager>

struct TidalTrack
{
    qint64 id = 0;
    QString title;
    QString artist;
    QString album;
    qint64 durationMs = 0;
    QString artUrl;
    bool isStream = true;
};

class TidalRepository : public QObject
{
    Q_OBJECT
public:
    explicit TidalRepository(QObject *parent = nullptr);

    void searchSongs(const QString &query);

signals:
    void searchFinished(const QList<TidalTrack> &tracks, const QString &error);

private:
    QNetworkAccessManager m_nam;
};
