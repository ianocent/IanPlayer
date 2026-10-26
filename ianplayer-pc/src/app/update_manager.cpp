#include "app/update_manager.h"

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcess>
#include <QTextStream>
#include <QDir>
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

void UpdateManager::downloadAndApplyUpdate(const QString &zipUrl)
{
    QNetworkRequest req(zipUrl);
    req.setRawHeader("User-Agent", "IanPlayer");
    QNetworkReply *reply = m_nam.get(req);
    const QString zipPath = QDir::tempPath() + QStringLiteral("/IanPlayer-update.zip");
    QFile *file = new QFile(zipPath, this);
    if (!file->open(QIODevice::WriteOnly)) {
        emit ytDlpUpdateFinished(QStringLiteral("cannot open temp file"));
        reply->abort();
        reply->deleteLater();
        file->deleteLater();
        return;
    }
    QObject::connect(reply, &QNetworkReply::readyRead, this, [reply, file]() { file->write(reply->readAll()); });
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, file, zipPath]() {
        file->write(reply->readAll());
        file->close();
        const bool ok = (reply->error() == QNetworkReply::NoError);
        const QString err = reply->errorString();
        reply->deleteLater();
        file->deleteLater();
        if (!ok) {
            emit ytDlpUpdateFinished(QStringLiteral("download failed: ") + err);
            return;
        }

        const QString appDir = QCoreApplication::applicationDirPath();
        const QString appName = QFileInfo(QCoreApplication::applicationFilePath()).fileName();
        const QString batPath = QDir::tempPath() + QStringLiteral("/ianplayer_update.bat");
        QFile bat(batPath);
        if (bat.open(QIODevice::WriteOnly | QIODevice::Text)) {
            QTextStream ts(&bat);
            ts << "@echo off\r\n";
            ts << ":wait\r\n";
            ts << "tasklist /FI \"IMAGENAME eq " << appName << "\" 2>NUL | find /I \"" << appName << "\" >NUL\r\n";
            ts << "if not errorlevel 1 ( timeout /t 1 /nobreak >NUL & goto wait )\r\n";
            ts << "set APPDIR=" << appDir << "\r\n";
            ts << "set ZIP=" << zipPath << "\r\n";
            ts << "set NEWDIR=%TEMP%\\IanPlayer-new\r\n";
            ts << "if exist \"%NEWDIR%\" rmdir /s /q \"%NEWDIR%\"\r\n";
            ts << "mkdir \"%NEWDIR%\"\r\n";
            ts << "powershell -NoProfile -Command \"Expand-Archive -Path '%ZIP%' -DestinationPath '%NEWDIR%' -Force\"\r\n";
            ts << "ren \"%APPDIR%\" \"_old\"\r\n";
            ts << "ren \"%NEWDIR%\" \"" << QFileInfo(appDir).fileName() << "\"\r\n";
            ts << "move /Y \"%NEWDIR%\" \"%APPDIR%\" >NUL 2>&1\r\n";
            ts << "start \"\" \"%APPDIR%\\" << appName << "\"\r\n";
            ts << "timeout /t 2 /nobreak >NUL\r\n";
            ts << "rmdir /s /q \"%APPDIR%_old\" 2>NUL\r\n";
            ts << "del \"%ZIP%\" 2>NUL\r\n";
            ts << "del \"%~f0\" 2>NUL\r\n";
            bat.close();
        }

        QProcess::startDetached(QStringLiteral("cmd.exe"), {QStringLiteral("/c"), batPath});
        QCoreApplication::quit();
    });
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
