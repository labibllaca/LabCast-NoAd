package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LiveGitHubRelease(
    val tagName: String,
    val title: String,
    val changelog: String,
    val publishedDate: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val isPrerelease: Boolean,
    val hasDirectApk: Boolean
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: LiveGitHubRelease) : UpdateCheckResult()
    data class UpToDate(val latestTag: String, val message: String) : UpdateCheckResult()
    data class Error(val message: String, val statusCode: Int? = null) : UpdateCheckResult()
}

object GitHubUpdateManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Checks the GitHub REST API for real releases in the specified repository.
     */
    suspend fun checkReleases(
        repo: String,
        includePrereleases: Boolean,
        currentVersion: String
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix("/")
        if (cleanRepo.isBlank() || !cleanRepo.contains("/")) {
            return@withContext UpdateCheckResult.Error(
                "Ungültiges Repository-Format. Bitte verwende das Format 'Benutzername/Repository' (z. B. 'labibllaca/LabCast-NoAd')."
            )
        }

        val apiUrl = if (includePrereleases) {
            "https://api.github.com/repos/$cleanRepo/releases"
        } else {
            "https://api.github.com/repos/$cleanRepo/releases/latest"
        }

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "LabCast-Android-OTA")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string()

            if (code == 404) {
                return@withContext UpdateCheckResult.Error(
                    "Keine Releases im GitHub Repository '$cleanRepo' gefunden (HTTP 404 Not Found).\n\n" +
                    "Tipp: Erstelle auf GitHub einen Release / Tag für '$cleanRepo' und lade die compilierte .apk als Release-Asset hoch.",
                    statusCode = 404
                )
            } else if (code == 403) {
                return@withContext UpdateCheckResult.Error(
                    "GitHub API Rate-Limit erreicht (HTTP 403 Forbidden). Die anonyme API-Anfragegrenze von GitHub wurde vorübergehend erreicht. Bitte versuche es in wenigen Minuten erneut.",
                    statusCode = 403
                )
            } else if (!response.isSuccessful || body.isNullOrEmpty()) {
                return@withContext UpdateCheckResult.Error(
                    "GitHub API Fehler: HTTP $code (${response.message.ifEmpty { "Unbekannter Fehler" }}).",
                    statusCode = code
                )
            }

            // Parse response
            val releaseObj: JSONObject? = if (includePrereleases) {
                val array = JSONArray(body)
                if (array.length() > 0) array.getJSONObject(0) else null
            } else {
                JSONObject(body)
            }

            if (releaseObj == null) {
                return@withContext UpdateCheckResult.Error(
                    "Für '$cleanRepo' wurden auf GitHub noch keine Releases veröffentlicht.",
                    statusCode = 200
                )
            }

            val release = parseReleaseObject(cleanRepo, releaseObj)

            // Compare version tag with current version using incremental number logic
            val isNewer = isNewerVersion(release.tagName, currentVersion)

            if (!isNewer) {
                return@withContext UpdateCheckResult.UpToDate(
                    latestTag = release.tagName,
                    message = "App ist auf dem neuesten Stand ($currentVersion)."
                )
            }

            return@withContext UpdateCheckResult.UpdateAvailable(release)

        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error(
                "Verbindungsfehler beim Abrufen von GitHub: ${e.localizedMessage ?: e.message ?: "Keine Internetverbindung"}"
            )
        }
    }

    /**
     * Compares remote release tag with local current version incrementally.
     * Supports formats like v1.2.1, 1.3, v2.0.0-rc1, etc.
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = extractVersionNumbers(remoteTag)
        val currentParts = extractVersionNumbers(currentVersion)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        if (maxLen == 0) {
            // Fallback to strict string difference if no numbers parsed
            val normR = remoteTag.lowercase().removePrefix("v").trim()
            val normC = currentVersion.lowercase().removePrefix("v").trim()
            return normR != normC && normR.isNotBlank()
        }

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun extractVersionNumbers(versionStr: String): List<Int> {
        val clean = versionStr.lowercase()
            .removePrefix("v")
            .removePrefix("release-")
            .removePrefix("release_")
            .split("-")[0]
            .split("+")[0]
            .trim()
        return clean.split(".").mapNotNull { part ->
            part.filter { it.isDigit() }.toIntOrNull()
        }
    }

    private fun parseReleaseObject(repo: String, obj: JSONObject): LiveGitHubRelease {
        val tagName = obj.optString("tag_name", "v1.0.0")
        val title = obj.optString("name").ifEmpty { "Release $tagName" }
        val changelog = obj.optString("body").ifEmpty { "Keine Release-Notes vorhanden." }
        val publishedAt = obj.optString("published_at", "")
        val htmlUrl = obj.optString("html_url", "https://github.com/$repo")
        val isPrerelease = obj.optBoolean("prerelease", false)

        var downloadUrl = htmlUrl
        var assetName = "labcast-$tagName.apk"
        var assetSizeBytes = 0L
        var hasDirectApk = false

        // Search for APK file in assets
        val assets = obj.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    assetName = name
                    downloadUrl = asset.optString("browser_download_url", downloadUrl)
                    assetSizeBytes = asset.optLong("size", 0L)
                    hasDirectApk = true
                    break
                }
            }

            // Fallback to first asset if no .apk found
            if (!hasDirectApk && assets.length() > 0) {
                val firstAsset = assets.getJSONObject(0)
                assetName = firstAsset.optString("name", assetName)
                downloadUrl = firstAsset.optString("browser_download_url", downloadUrl)
                assetSizeBytes = firstAsset.optLong("size", 0L)
            }
        }

        val formattedDate = try {
            if (publishedAt.length >= 10) publishedAt.substring(0, 10) else "Aktuell"
        } catch (e: Exception) {
            "Aktuell"
        }

        return LiveGitHubRelease(
            tagName = tagName,
            title = title,
            changelog = changelog,
            publishedDate = formattedDate,
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
            assetName = assetName,
            assetSizeBytes = assetSizeBytes,
            isPrerelease = isPrerelease,
            hasDirectApk = hasDirectApk
        )
    }

    /**
     * Downloads the APK file to the app's cache directory while reporting real progress.
     */
    suspend fun downloadApkFile(
        context: Context,
        release: LiveGitHubRelease,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
            val destinationFile = File(updatesDir, release.assetName)

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "LabCast-Android-OTA")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Download fehlgeschlagen mit HTTP ${response.code}: ${response.message}")
                )
            }

            val responseBody = response.body ?: return@withContext Result.failure(
                Exception("Leere Server-Antwort beim Herunterladen der APK.")
            )

            val contentLength = responseBody.contentLength().takeIf { it > 0 } ?: release.assetSizeBytes.takeIf { it > 0 } ?: 25_000_000L

            responseBody.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                    output.flush()
                }
            }

            if (!destinationFile.exists() || destinationFile.length() < 1024) {
                return@withContext Result.failure(
                    Exception("Heruntergeladene Datei ist unvollständig oder ungültig (${destinationFile.length()} Bytes).")
                )
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers the Android Package Installer for the downloaded APK file.
     */
    fun startPackageInstall(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("APK-Installationsdatei existiert nicht."))
            }

            val authority = "${context.packageName}.provider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
