package com.app.check_inpets.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.app.check_inpets.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String
)

@Serializable
data class GitHubLatestRelease(
    @SerialName("tag_name")
    val tagName: String,
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

data class RemoteAppVersion(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = ""
)

data class CurrentAppVersion(
    val versionCode: Long,
    val versionName: String
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val current: CurrentAppVersion) : AppUpdateState
    data class UpdateAvailable(
        val current: CurrentAppVersion,
        val remote: RemoteAppVersion
    ) : AppUpdateState
    data class Downloading(
        val progressPercent: Int,
        val remote: RemoteAppVersion
    ) : AppUpdateState
    data class NeedsInstallPermission(
        val apkFile: File,
        val remote: RemoteAppVersion
    ) : AppUpdateState
    data class Installing(val remote: RemoteAppVersion) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

sealed interface InstallCallbackEvent {
    data class ConfirmRequired(val intent: Intent) : InstallCallbackEvent
    data class Failed(val status: Int, val message: String) : InstallCallbackEvent
    data object Succeeded : InstallCallbackEvent
}

object AppUpdateManager {

    const val GITHUB_OWNER = "fael1505"
    const val GITHUB_REPO = "CheckinPets"
    const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _foregroundUpdates = MutableSharedFlow<AppUpdateState.UpdateAvailable>(extraBufferCapacity = 1)
    val foregroundUpdates = _foregroundUpdates.asSharedFlow()

    private val _installEvents = MutableSharedFlow<InstallCallbackEvent>(extraBufferCapacity = 4)
    val installEvents = _installEvents.asSharedFlow()

    fun notifyForegroundUpdateAvailable(update: AppUpdateState.UpdateAvailable) {
        _foregroundUpdates.tryEmit(update)
    }

    fun emitInstallEvent(event: InstallCallbackEvent) {
        _installEvents.tryEmit(event)
    }

    fun getCurrentVersion(context: Context): CurrentAppVersion {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val name = pkgInfo.versionName ?: "1.0"
        return CurrentAppVersion(versionCode = code, versionName = name)
    }

    suspend fun checkForUpdate(context: Context): AppUpdateState = withContext(Dispatchers.IO) {
        val current = getCurrentVersion(context)
        runCatching {
            val conn = (URL(GITHUB_LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CheckinPets-Updater")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = conn.responseCode
                if (code == 404) {
                    return@withContext AppUpdateState.UpToDate(current = current)
                }
                if (code !in 200..299) {
                    error("Servidor retornou HTTP $code ao verificar atualização.")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubLatestRelease>(body)
                if (release.draft || release.prerelease) {
                    return@withContext AppUpdateState.UpToDate(current = current)
                }

                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext AppUpdateState.UpToDate(current = current)

                val remoteVersionName = release.tagName.trim().removePrefix("v").removePrefix("V")
                val isNewer = compareSemanticVersions(remoteVersionName, current.versionName) > 0

                if (isNewer) {
                    AppUpdateState.UpdateAvailable(
                        current = current,
                        remote = RemoteAppVersion(
                            versionCode = current.versionCode + 1,
                            versionName = remoteVersionName,
                            apkUrl = apkAsset.browserDownloadUrl,
                            releaseNotes = release.body.trim()
                        )
                    )
                } else {
                    AppUpdateState.UpToDate(current = current)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { err ->
            AppUpdateState.Error(err.message ?: "Não foi possível verificar atualizações.")
        }
    }

    internal fun compareSemanticVersions(remote: String, local: String): Int {
        val remoteParts = remote.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    suspend fun downloadApk(
        context: Context,
        remote: RemoteAppVersion,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "checkin-pets-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val conn = (URL(remote.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CheckinPets-Updater")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Falha no download do APK (HTTP $code).")
                }
                val totalBytes = conn.contentLengthLong
                var downloaded = 0L
                var lastReported = -1

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(100)
                apkFile
            } finally {
                conn.disconnect()
            }
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun installApk(context: Context, apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkFile.exists() && apkFile.length() > 0L) {
                "Arquivo de atualização inválido."
            }
            val appContext = context.applicationContext
            AppContainer.getInstance(appContext).preferencesDataStore.setPendingRestartAfterUpdate(true)

            val installer = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            session.use { s ->
                FileInputStream(apkFile).use { input ->
                    s.openWrite("checkin_pets_update", 0, apkFile.length()).use { out ->
                        input.copyTo(out)
                        s.fsync(out)
                    }
                }
                val callbackIntent = Intent(appContext, AppUpdateInstallReceiver::class.java).apply {
                    action = AppUpdateInstallReceiver.ACTION_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusIntent = PendingIntent.getBroadcast(appContext, sessionId, callbackIntent, flags)
                s.commit(statusIntent.intentSender)
            }
        }
    }
}
