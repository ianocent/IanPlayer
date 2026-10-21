package com.ianocent.musicplayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ianocent.musicplayer.data.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val GITHUB_API =
        "https://api.github.com/repos/ianocent/IanPlayer/releases/latest"

    /** Result of an install attempt, so the caller can explain failures instead of doing nothing. */
    enum class InstallResult { STARTED, NEEDS_PERMISSION, MISSING_FILE, FAILED }

    /**
     * True when [remote] is a strictly newer semver than [current].
     * Pure function; lives here so version policy has one home.
     */
    fun isNewer(remote: String, current: String): Boolean {
        val parts1 = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val diff = (parts1.getOrElse(i) { 0 }) - (parts2.getOrElse(i) { 0 })
            if (diff != 0) return diff > 0
        }
        return false
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")

            if (conn.responseCode != 200) return@withContext null

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)

            val tagName = obj.getString("tag_name") // "v5.0.0"
            val versionName = tagName.removePrefix("v")
            val releaseNotes = obj.optString("body", "")
            val assets = obj.getJSONArray("assets")

            // Prefer the APK asset; the first asset is not guaranteed to be it.
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null

            UpdateInfo(
                versionCode = 0, // not used — semver comparison via versionName
                versionName = versionName,
                downloadUrl = apk.getString("browser_download_url"),
                releaseNotes = releaseNotes
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun updateFile(context: Context): File =
        File(File(context.filesDir, "updates"), "update.apk")

    fun startDownload(context: Context, update: UpdateInfo): Long {
        val file = updateFile(context)
        if (file.exists()) file.delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // No setDestinationUri: DownloadManager owns the file under scoped storage.
        // We copy it into the app dir from [registerDownloadReceiver] once complete.
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("IanPlayer Update")
            .setDescription("Downloading v${update.versionName}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    /**
     * Launches the system package installer for the downloaded APK.
     * Returns a result instead of failing silently, so the UI can guide the
     * user through "Install unknown apps" when Android blocks it.
     */
    fun installApk(context: Context): InstallResult {
        val file = updateFile(context)
        if (!file.exists() || file.length() == 0L) {
            Timber.w("Update APK missing at ${file.absolutePath}")
            return InstallResult.MISSING_FILE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // Android 8+: the user must allow this app to install unknown apps.
            return try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                InstallResult.NEEDS_PERMISSION
            } catch (e: Exception) {
                Timber.e(e, "Cannot open unknown-sources settings")
                InstallResult.FAILED
            }
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                clipData = android.content.ClipData.newRawUri("IanPlayer update", apkUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            InstallResult.STARTED
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            InstallResult.FAILED
        }
    }

    /**
     * Listens for the download finishing, then copies DownloadManager's file
     * into the app's external Download dir (FileProvider shares it from there).
     * The callback reports real success/failure, not just "broadcast arrived".
     */
    fun registerDownloadReceiver(
        context: Context,
        downloadId: Long,
        onFinished: (Boolean) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                try {
                    ctx.unregisterReceiver(this)
                } catch (_: Exception) {}

                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val success = try {
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        var status = -1
                        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                status = cursor.getInt(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                                )
                            }
                        }

                        if (status != DownloadManager.STATUS_SUCCESSFUL) {
                            Timber.w("Update download $downloadId failed with status=$status")
                            false
                        } else {
                            val source = dm.getUriForDownloadedFile(downloadId)
                            val target = updateFile(ctx)
                            target.parentFile?.mkdirs()
                            val copied = source != null && runCatching {
                                ctx.contentResolver.openInputStream(source)?.use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                } != null
                            }.getOrDefault(false)
                            copied && target.length() > 0L
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Update download post-processing failed")
                        false
                    } finally {
                        pending.finish()
                    }

                    withContext(Dispatchers.Main) { onFinished(success) }
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }
}
