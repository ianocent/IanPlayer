#include "app/settings_service.h"

SettingsService::SettingsService(QSettings *settings, QObject *parent)
    : QObject(parent), m_settings(settings) {}

double SettingsService::motionScale() const { return m_settings ? m_settings->value("motion_scale", 1.0).toDouble() : 1.0; }
void SettingsService::setMotionScale(double v) { if (m_settings) { m_settings->setValue("motion_scale", v); m_settings->sync(); } emit settingsChanged(); }

int SettingsService::glassBlur() const { return m_settings ? m_settings->value("glass_blur", 16).toInt() : 16; }
void SettingsService::setGlassBlur(int v) { if (m_settings) { m_settings->setValue("glass_blur", v); m_settings->sync(); } emit settingsChanged(); }

double SettingsService::glassVibrancy() const { return m_settings ? m_settings->value("glass_vibrancy", 0.3).toDouble() : 0.3; }
void SettingsService::setGlassVibrancy(double v) { if (m_settings) { m_settings->setValue("glass_vibrancy", v); m_settings->sync(); } emit settingsChanged(); }

int SettingsService::seekBarStyle() const { return m_settings ? m_settings->value("seek_bar_style", 0).toInt() : 0; }
void SettingsService::setSeekBarStyle(int v) { if (m_settings) { m_settings->setValue("seek_bar_style", v); m_settings->sync(); } emit settingsChanged(); }

int SettingsService::miniLayoutIndex() const { return m_settings ? m_settings->value("mini_layout_index", 0).toInt() : 0; }
void SettingsService::setMiniLayoutIndex(int v) { if (m_settings) { m_settings->setValue("mini_layout_index", v); m_settings->sync(); } emit settingsChanged(); }

bool SettingsService::isPillAtBottom() const { return m_settings ? m_settings->value("is_pill_at_bottom", true).toBool() : true; }
void SettingsService::setIsPillAtBottom(bool v) { if (m_settings) { m_settings->setValue("is_pill_at_bottom", v); m_settings->sync(); } emit settingsChanged(); }

QString SettingsService::userName() const { return m_settings ? m_settings->value("user_name", QString()).toString() : QString(); }
void SettingsService::setUserName(const QString &v) { if (m_settings) { m_settings->setValue("user_name", v); m_settings->sync(); } emit settingsChanged(); }

QString SettingsService::avatarPath() const { return m_settings ? m_settings->value("avatar_path", QString()).toString() : QString(); }
void SettingsService::setAvatarPath(const QString &v) { if (m_settings) { m_settings->setValue("avatar_path", v); m_settings->sync(); } emit settingsChanged(); }

QString SettingsService::bannerPath() const { return m_settings ? m_settings->value("banner_path", QString()).toString() : QString(); }
void SettingsService::setBannerPath(const QString &v) { if (m_settings) { m_settings->setValue("banner_path", v); m_settings->sync(); } emit settingsChanged(); }

QString SettingsService::spotifySpDc() const { return m_settings ? m_settings->value("spotify_sp_dc", QString()).toString() : QString(); }
void SettingsService::setSpotifySpDc(const QString &v) { if (m_settings) { m_settings->setValue("spotify_sp_dc", v); m_settings->sync(); } emit settingsChanged(); }
