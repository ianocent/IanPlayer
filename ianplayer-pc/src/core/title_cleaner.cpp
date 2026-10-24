#include "core/title_cleaner.h"

#include <QRegularExpression>
#include <QVector>

#include <algorithm>

namespace SongTags {
namespace {

// TITLE_JUNK Kotlin: akhiran promo yang biasa nempel di judul YouTube.
const QVector<QRegularExpression> &titleJunk()
{
    static const QVector<QRegularExpression> junk = {
        QRegularExpression(QStringLiteral(R"(\s*[\(\[](official\s+)?(music\s+)?video[\)\]])"),
                           QRegularExpression::CaseInsensitiveOption),
        QRegularExpression(QStringLiteral(R"(\s*[\(\[](official\s+)?(lyric[s]?\s+)?video[\)\]])"),
                           QRegularExpression::CaseInsensitiveOption),
        QRegularExpression(QStringLiteral(R"(\s*[\(\[]official\s+(music\s+)?(audio|visualizer|mv)[\)\]])"),
                           QRegularExpression::CaseInsensitiveOption),
        QRegularExpression(QStringLiteral(R"(\s*[\(\[](lyrics?|audio|hd|hq|4k|m\/v)[\)\]])"),
                           QRegularExpression::CaseInsensitiveOption),
        QRegularExpression(QStringLiteral(R"(\s*-\s*(official\s+)?(music\s+)?(video|audio|lyric[s]?)(\s+video)?$)"),
                           QRegularExpression::CaseInsensitiveOption),
        QRegularExpression(QStringLiteral(R"(\s*\|\s*(official\s+)?(music\s+)?video$)"),
                           QRegularExpression::CaseInsensitiveOption),
    };
    return junk;
}

bool endsWithIgnoreCase(const QString &s, const QString &suffix)
{
    return s.size() > suffix.size()
        && s.right(suffix.size()).compare(suffix, Qt::CaseInsensitive) == 0;
}

bool startsWithIgnoreCase(const QString &s, const QString &prefix)
{
    return s.size() > prefix.size()
        && s.left(prefix.size()).compare(prefix, Qt::CaseInsensitive) == 0;
}

// trimEnd('-', '|', '(', '[') Kotlin
QString trimEndOf(const QString &s, const QString &chars)
{
    int end = s.size();
    while (end > 0 && chars.contains(s.at(end - 1)))
        --end;
    return s.left(end);
}

} // namespace

QString cleanTitle(const QString &rawTitle)
{
    // Kotlin: ulangi replace sampai tidak berubah lagi (junk bertumpuk)
    QString t = rawTitle.trimmed();
    QString before;
    do {
        before = t;
        for (const QRegularExpression &rx : titleJunk())
            t.remove(rx);
    } while (t != before);

    t = trimEndOf(t.trimmed(), QStringLiteral("-|(["));
    t = t.trimmed();
    return t.isEmpty() ? rawTitle.trimmed() : t;
}

bool isJunkArtist(const QString &artist)
{
    return artist.isEmpty()
        || artist.startsWith(QStringLiteral("Unknown"), Qt::CaseInsensitive)
        || artist.compare(QStringLiteral("YouTube"), Qt::CaseInsensitive) == 0
        || artist.contains(QStringLiteral("Various"), Qt::CaseInsensitive);
}

QPair<QString, QString> resolve(const QString &title, const QString &artist)
{
    QString t = cleanTitle(title);
    QString a = artist.trimmed();

    // Channel-name cleanup: "Ed Sheeran - Topic" / "EdSheeranVEVO"
    if (a.endsWith(QStringLiteral(" - Topic"), Qt::CaseInsensitive))
        a = a.left(a.size() - QString(QStringLiteral(" - Topic")).size()).trimmed();
    if (a.endsWith(QStringLiteral("VEVO"), Qt::CaseInsensitive))
        a = a.left(a.size() - QString(QStringLiteral("VEVO")).size()).trimmed();

    // Artist terpercaya muncul verbatim di judul: cabut supaya judul cuma
    // menyisakan nama lagu, di sisi mana pun artist berada.
    if (!a.isEmpty() && !isJunkArtist(a)) {
        const QString suffix = QStringLiteral(" - ") + a;
        const QString prefix = a + QStringLiteral(" - ");
        if (endsWithIgnoreCase(t, suffix)) {
            t = t.left(t.size() - suffix.size()).trimmed();
        } else {
            if (startsWithIgnoreCase(t, prefix))
                t = t.mid(prefix.size()).trimmed();
        }
    }

    const int dash = t.indexOf(QStringLiteral(" - "));
    if (dash > 0) {
        const QString leftCandidate = t.left(dash).trimmed();
        const QString rightCandidate = t.mid(dash + 3).trimmed();
        const bool artistIsJunk =
            isJunkArtist(a) || a.endsWith(QStringLiteral(" - Topic"), Qt::CaseInsensitive);
        const bool leftLooksLikeArtist =
            leftCandidate.size() <= 60 && !leftCandidate.contains(QLatin1Char('('));
        if (artistIsJunk && !rightCandidate.isEmpty() && leftLooksLikeArtist)
            return { cleanTitle(rightCandidate), leftCandidate };
    }

    if (a.isEmpty())
        a = artist.trimmed().isEmpty() ? QStringLiteral("Unknown Artist") : artist.trimmed();
    return { t, a };
}

} // namespace SongTags
