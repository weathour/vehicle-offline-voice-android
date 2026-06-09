package com.company.vehiclevoice.update

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseUpdateClient(private val context: Context) {
    fun fetchReleases(): List<GitHubReleaseVersion> = parseReleaseAtom(httpGetText(RELEASES_ATOM_URL))
        .filter { release -> !release.draft && release.apkAssetUrl() != null }

    fun downloadApk(downloadUrl: String): File {
        val updatesDir = File(context.cacheDir, UpdateApkProvider.UPDATE_CACHE_DIR).apply { mkdirs() }
        val outFile = File(updatesDir, UpdateApkProvider.UPDATE_APK_FILE_NAME)
        val connection = openConnection(downloadUrl)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("GitHub APK download HTTP $code")
            connection.inputStream.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (outFile.length() <= 0L) throw IOException("Downloaded APK is empty")
        return outFile
    }

    private fun httpGetText(url: String): String {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                throw IOException(
                    "GitHub releases not found. Private repositories require an authenticated update service; " +
                        "do not embed a GitHub token in the APK."
                )
            }
            if (code !in 200..299) throw IOException("GitHub releases HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 25_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "VehicleOfflineVoiceAndroid")
    }

    companion object {
        const val REPOSITORY = "weathour/vehicle-offline-voice-android"
        const val RELEASES_ATOM_URL = "https://github.com/$REPOSITORY/releases.atom"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

data class GitHubReleaseVersion(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>
) {
    fun title(): String = buildString {
        append(tagName)
        if (name.isNotBlank() && name != tagName) append("  ").append(name)
        if (prerelease) append("  预发布")
    }

    fun apkAssetUrl(): String? = assets.firstOrNull { asset ->
        asset.name.endsWith(".apk", ignoreCase = true)
    }?.downloadUrl ?: assets.firstOrNull { asset ->
        asset.downloadUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)
    }?.downloadUrl
}

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String
)

fun parseReleaseAtom(atom: String): List<GitHubReleaseVersion> = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    .findAll(atom)
    .mapNotNull { match ->
        val entry = match.groupValues[1]
        val tagName = Regex("/releases/tag/([^\"<]+)").find(entry)?.groupValues?.get(1)
            ?: Regex("Repository/\\d+/([^<]+)").find(entry)?.groupValues?.get(1)
            ?: return@mapNotNull null
        val title = xmlUnescape(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(entry)?.groupValues?.get(1)).ifBlank { tagName }
        GitHubReleaseVersion(
            tagName = tagName,
            name = title,
            htmlUrl = "https://github.com/${GitHubReleaseUpdateClient.REPOSITORY}/releases/tag/$tagName",
            draft = false,
            prerelease = false,
            assets = listOf(
                GitHubReleaseAsset(
                    name = apkFileName(tagName),
                    downloadUrl = "https://github.com/${GitHubReleaseUpdateClient.REPOSITORY}/releases/download/$tagName/${apkFileName(tagName)}"
                )
            )
        )
    }
    .toList()

fun apkFileName(tagName: String): String = "vehicle-offline-voice-android-$tagName-debug.apk"

private fun xmlUnescape(value: String?): String = value.orEmpty()
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
