package com.company.vehiclevoice.update

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseUpdateClient(private val context: Context) {
    fun fetchReleases(): List<GitHubReleaseVersion> = parseReleaseList(httpGetText(RELEASES_URL))
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
        const val RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases"
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

fun parseReleaseList(json: String): List<GitHubReleaseVersion> = topLevelObjects(json).map { releaseJson ->
    val assets = jsonArrayForField(releaseJson, "assets")
        ?.let(::topLevelObjects)
        ?.mapNotNull { assetJson ->
            val name = jsonStringField(assetJson, "name")
            val downloadUrl = jsonStringField(assetJson, "browser_download_url")
            if (name != null && downloadUrl != null) GitHubReleaseAsset(name, downloadUrl) else null
        }
        .orEmpty()
    GitHubReleaseVersion(
        tagName = jsonStringField(releaseJson, "tag_name").orEmpty(),
        name = jsonStringField(releaseJson, "name").orEmpty(),
        htmlUrl = jsonStringField(releaseJson, "html_url").orEmpty(),
        draft = jsonBooleanField(releaseJson, "draft") ?: false,
        prerelease = jsonBooleanField(releaseJson, "prerelease") ?: false,
        assets = assets
    )
}.filter { release -> release.tagName.isNotBlank() }

private fun jsonStringField(json: String, field: String): String? {
    val match = Regex("\\\"${Regex.escape(field)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(json) ?: return null
    return match.groupValues[1]
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
}

private fun jsonBooleanField(json: String, field: String): Boolean? {
    val match = Regex("\\\"${Regex.escape(field)}\\\"\\s*:\\s*(true|false)").find(json) ?: return null
    return match.groupValues[1].toBooleanStrictOrNull()
}

private fun jsonArrayForField(json: String, field: String): String? {
    val fieldIndex = json.indexOf("\"$field\"")
    if (fieldIndex < 0) return null
    val start = json.indexOf('[', fieldIndex)
    if (start < 0) return null
    val end = matchingBracket(json, start, '[', ']')
    if (end <= start) return null
    return json.substring(start, end + 1)
}

private fun topLevelObjects(json: String): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < json.length) {
        val start = json.indexOf('{', index)
        if (start < 0) break
        val end = matchingBracket(json, start, '{', '}')
        if (end < 0) break
        result += json.substring(start, end + 1)
        index = end + 1
    }
    return result
}

private fun matchingBracket(json: String, start: Int, open: Char, close: Char): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until json.length) {
        val char = json[index]
        if (escaped) {
            escaped = false
            continue
        }
        if (char == '\\') {
            escaped = inString
            continue
        }
        if (char == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        when (char) {
            open -> depth += 1
            close -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return -1
}
