package com.ianocent.musicplayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ianocent.musicplayer.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val GITHUB_API =
        "https://api.github.com/repos/ianocent/IanPlayer/releases/latest"

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
            if (assets.length() == 0) return@withContext null

            val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")

            UpdateInfo(
                versionCode = 0, // not used — semver comparison via versionName
                versionName = versionName,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes
            )
        } catch (e: Exception) {
            null
        }
    }

    fun startDownload(context: Context, update: UpdateInfo): Long {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (file.exists()) file.delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("IanPlayer Update")
            .setDescription("Downloading v${update.versionName}...")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun installApk(context: Context) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (!file.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }

    fun registerDownloadReceiver(
        context: Context,
        downloadId: Long,
        onComplete: () -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    onComplete()
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }
}
