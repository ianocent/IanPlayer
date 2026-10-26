#pragma once

#include <QObject>
#include <QString>
#include <QNetworkAccessManager>

class QSettings;

class UpdateManager : public QObject
{
    Q_OBJECT
public:
    explicit UpdateManager(QSettings *settings, QObject *parent = nullptr);

    Q_INVOKABLE void checkForUpdates();
    Q_INVOKABLE void updateYtDlp();
    Q_INVOKABLE void downloadAndApplyUpdate(const QString &zipUrl);
    Q_INVOKABLE void setDontShowAgain(const QString &version);

signals:
    void updateAvailable(const QString &version, const QString &downloadUrl, const QString &notes);
    void noUpdate();
    void ytDlpUpdateFinished(const QString &output);

private:
    QSettings *m_settings = nullptr;
    QNetworkAccessManager m_nam;
};
