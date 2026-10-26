#pragma once

#include <QObject>
#include <QString>
#include <QSettings>

// SettingsStore paritas Android (M5.1). Semua key dipetakan 1:1.
class SettingsService : public QObject
{
    Q_OBJECT
    Q_PROPERTY(double motionScale READ motionScale WRITE setMotionScale NOTIFY settingsChanged)
    Q_PROPERTY(int glassBlur READ glassBlur WRITE setGlassBlur NOTIFY settingsChanged)
    Q_PROPERTY(double glassVibrancy READ glassVibrancy WRITE setGlassVibrancy NOTIFY settingsChanged)
    Q_PROPERTY(int seekBarStyle READ seekBarStyle WRITE setSeekBarStyle NOTIFY settingsChanged)
    Q_PROPERTY(int miniLayoutIndex READ miniLayoutIndex WRITE setMiniLayoutIndex NOTIFY settingsChanged)
    Q_PROPERTY(bool isPillAtBottom READ isPillAtBottom WRITE setIsPillAtBottom NOTIFY settingsChanged)
    Q_PROPERTY(QString userName READ userName WRITE setUserName NOTIFY settingsChanged)
    Q_PROPERTY(QString avatarPath READ avatarPath WRITE setAvatarPath NOTIFY settingsChanged)
    Q_PROPERTY(QString bannerPath READ bannerPath WRITE setBannerPath NOTIFY settingsChanged)
    Q_PROPERTY(QString spotifySpDc READ spotifySpDc WRITE setSpotifySpDc NOTIFY settingsChanged)
    Q_PROPERTY(QString reduceMotion READ reduceMotion CONSTANT)

public:
    explicit SettingsService(QSettings *settings, QObject *parent = nullptr);

    double motionScale() const;
    void setMotionScale(double v);
    int glassBlur() const;
    void setGlassBlur(int v);
    double glassVibrancy() const;
    void setGlassVibrancy(double v);
    int seekBarStyle() const;
    void setSeekBarStyle(int v);
    int miniLayoutIndex() const;
    void setMiniLayoutIndex(int v);
    bool isPillAtBottom() const;
    void setIsPillAtBottom(bool v);
    QString userName() const;
    void setUserName(const QString &v);
    QString avatarPath() const;
    void setAvatarPath(const QString &v);
    QString bannerPath() const;
    void setBannerPath(const QString &v);
    QString spotifySpDc() const;
    void setSpotifySpDc(const QString &v);
    QString reduceMotion() const { return QStringLiteral("system"); }

signals:
    void settingsChanged();

private:
    QSettings *m_settings = nullptr;
    template <typename T>
    T get(const QString &key, const T &def) const;
    template <typename T>
    void set(const QString &key, const T &value);
};
