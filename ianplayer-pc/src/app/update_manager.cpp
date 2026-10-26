#include "app/update_manager.h"

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcess>
#include <QStandardPaths>
#include <QDir>
#include <QCoreApplication>
#include <QSettings>

UpdateManager::UpdateManager(QSettings *settings, QObject *parent)
    : QObject(parent), m_settings(settings) {}

static QString currentVersion()
{
    return QCoreApplication::applicationVersion();
}

static bool isNewer(const QString &remote, const QString &local)
{
    // simple semver compare (major.minor.patch)
    const QStringList r = remote.split('.');
    const QStringList l = local.split('.');
    for (int i = 0; i < 3; ++i) {
        const int rv = i < r.size() ? r.at(i).toInt() : 0;
        const int lv = i < l.size() ? l.at(i).toInt() : 0;
        if (rv != lv)
            return rv > lv;
    }
    return false;
}

void UpdateManager::checkForUpdates()
{
    QNetworkRequest req(QUrl(QStringLiteral("https://api.github.com/repos/ianocent/IanPlayer/releases/latest")));
    req.setRawHeader("User-Agent", "IanPlayer");
    QNetworkReply *reply = m_nam.get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() != QNetworkReply::NoError) {
            reply->deleteLater();
            emit noUpdate();
            return;
        }
        QJsonParseError pe{};
        const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
        reply->deleteLater();
        if (pe.error != QJsonParseError::NoError || !doc.isObject()) {
            emit noUpdate();
            return;
        }
        const QJsonObject o = doc.object();
        QString tag = o.value(QStringLiteral("tag_name")).toString();
        tag.remove('v');
        const QString url = o.value(QStringLiteral("html_url")).toString();
        const QString notes = o.value(QStringLiteral("body")).toString();

        if (m_settings) {
            const QString ignored = m_settings->value(QStringLiteral("update_ignored_version")).toString();
            if (ignored == tag) {
                emit noUpdate();
                return;
            }
        }

        if (isNewer(tag, currentVersion())) {
            emit updateAvailable(tag, url, notes);
        } else {
            emit noUpdate();
        }
    });
}

void UpdateManager::setDontShowAgain(const QString &version)
{
    if (m_settings) {
        m_settings->setValue(QStringLiteral("update_ignored_version"), version);
        m_settings->sync();
    }
}

void UpdateManager::updateYtDlp()
{
    QProcess proc;
    // find yt-dlp
    const QString exe = QCoreApplication::applicationDirPath() + QStringLiteral("/yt-dlp.exe");
    proc.setProgram(QFileInfo::exists(exe) ? exe : QStringLiteral("yt-dlp"));
    proc.setArguments({QStringLiteral("-U")});
    proc.start();
    if (proc.waitForFinished(60000)) {
        emit ytDlpUpdateFinished(QString::fromUtf8(proc.readAllStandardOutput()) + QString::fromUtf8(proc.readAllStandardError()));
    } else {
        proc.kill();
        emit ytDlpUpdateFinished(QStringLiteral("timeout"));
    }
}
