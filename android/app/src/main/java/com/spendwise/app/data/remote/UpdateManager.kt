package com.spendwise.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.spendwise.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppUpdateInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    val changelog: String? = null,
    @SerialName("is_mandatory") val isMandatory: Boolean = false
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float) : UpdateDownloadState()
    object ReadyToInstall : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseProvider: SupabaseClientProvider
) {
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks if a newer version of Spendwise is available.
     * Prioritizes Supabase app_updates table, with automatic GitHub Releases fallback.
     */
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        // 1. Try Supabase app_updates table
        if (supabaseProvider.isConfigured) {
            try {
                val client = supabaseProvider.getClient()
                val updates = client.from("app_updates").select {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }.decodeList<AppUpdateInfo>()

                val latest = updates.firstOrNull()
                if (latest != null && latest.versionCode > BuildConfig.VERSION_CODE) {
                    Log.i("UpdateManager", "Found update in Supabase: v${latest.versionName} (${latest.versionCode})")
                    return@withContext latest
                }
            } catch (e: Exception) {
                Log.w("UpdateManager", "Supabase update check skipped: ${e.message}")
            }
        }

        // 2. Direct GitHub version.json fallback (High reliability, zero database setup needed)
        try {
            val rawUrl = URL("https://raw.githubusercontent.com/Mathidevdevil/Expancetraker/main/version.json")
            val conn = (rawUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Spendwise-Android")
            }
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val update = json.decodeFromString<AppUpdateInfo>(jsonText)
                if (update.versionCode > BuildConfig.VERSION_CODE) {
                    Log.i("UpdateManager", "Found update in version.json: v${update.versionName} (${update.versionCode})")
                    return@withContext update
                }
            }
        } catch (e: Exception) {
            Log.w("UpdateManager", "GitHub version.json check skipped: ${e.message}")
        }

        // 3. Fallback to GitHub Releases API
        try {
            val url = URL("https://api.github.com/repos/Mathidevdevil/Expancetraker/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Spendwise-Android")
            }
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(responseText).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
                val body = root["body"]?.jsonPrimitive?.content ?: "Performance enhancements & bug fixes."
                
                // Parse version from tag like "v1.0.1" or "1.0.1"
                val cleanTag = tagName.removePrefix("v").trim()
                val parts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
                val remoteCode = if (parts.size >= 2) parts[0] * 100 + parts[1] * 10 + (parts.getOrNull(2) ?: 0) else 0

                val assets = root["assets"]?.toString() ?: ""
                val apkAssetUrl = if (assets.contains("browser_download_url")) {
                    val regex = """"browser_download_url":\s*"([^"]+\.apk)"""".toRegex()
                    regex.find(responseText)?.groupValues?.get(1)
                } else null

                if (apkAssetUrl != null && remoteCode > BuildConfig.VERSION_CODE) {
                    Log.i("UpdateManager", "Found update on GitHub Releases: $cleanTag ($remoteCode)")
                    return@withContext AppUpdateInfo(
                        versionCode = remoteCode,
                        versionName = cleanTag,
                        apkUrl = apkAssetUrl,
                        changelog = body,
                        isMandatory = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("UpdateManager", "GitHub Releases update check skipped: ${e.message}")
        }

        return@withContext null
    }

    private val allowedOtaHosts = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    internal fun isTrustedOtaUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val parsed = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        if (!parsed.protocol.equals("https", ignoreCase = true)) return false
        val host = parsed.host.lowercase()
        return allowedOtaHosts.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Downloads the APK file over HTTPS from trusted release hosts and triggers the installer.
     */
    suspend fun downloadAndInstallApk(apkUrl: String) = withContext(Dispatchers.IO) {
        _downloadState.value = UpdateDownloadState.Downloading(0f)
        var connection: HttpURLConnection? = null
        try {
            if (!isTrustedOtaUrl(apkUrl)) {
                _downloadState.value = UpdateDownloadState.Error("Untrusted or non-HTTPS update URL.")
                return@withContext
            }
            var activeConn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
            }
            connection = activeConn

            // Handle redirect safely, verifying HTTPS and trusted hosts on every hop
            var code = activeConn.responseCode
            var hops = 0
            while ((code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) && hops < 4) {
                val newUrl = activeConn.getHeaderField("Location")
                activeConn.disconnect()
                if (!isTrustedOtaUrl(newUrl)) {
                    _downloadState.value = UpdateDownloadState.Error("Blocked untrusted redirect URL.")
                    return@withContext
                }
                activeConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                }
                connection = activeConn
                code = activeConn.responseCode
                hops++
            }

            val fileLength = activeConn.contentLength
            val destinationFile = File(context.cacheDir, "spendwise_update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            activeConn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = total.toFloat() / fileLength.toFloat()
                            _downloadState.value = UpdateDownloadState.Downloading(progress)
                        }
                    }
                }
            }

            _downloadState.value = UpdateDownloadState.ReadyToInstall
            withContext(Dispatchers.Main) {
                launchInstaller(destinationFile)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to download APK: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error(e.message ?: "Download failed")
        } finally {
            connection?.disconnect()
        }
    }

    fun launchInstaller(apkFile: File) {
        try {
            // Check Android 8+ unknown app sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error launching APK installer: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error("Could not launch installer: ${e.message}")
        }
    }

    fun resetState() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}
