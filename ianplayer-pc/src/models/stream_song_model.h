#pragma once

#include <QAbstractListModel>
#include <QVector>
#include <QString>

struct StreamSong
{
    QString title;
    QString artist;
    QString album;
    qint64 durationMs = 0;
    QString videoId;
    QString thumbnailUrl;
    QString source; // "yt" | "tidal"
};

class StreamSongModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ rowCount NOTIFY countChanged)
public:
    enum Roles {
        TitleRole = Qt::UserRole + 1,
        ArtistRole,
        AlbumRole,
        DurationMsRole,
        VideoIdRole,
        ThumbnailUrlRole,
        SourceRole,
    };
    explicit StreamSongModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    void setSongs(const QVector<StreamSong> &songs);
    void append(const QVector<StreamSong> &songs);
    void clear();

    const QVector<StreamSong> &songs() const { return m_songs; }

signals:
    void countChanged();

private:
    QVector<StreamSong> m_songs;
};
