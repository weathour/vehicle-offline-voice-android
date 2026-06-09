package com.company.vehiclevoice.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseUpdateClientTest {
    @Test
    fun parseReleaseList_extractsReleaseApkAssets() {
        val releases = parseReleaseList(
            """
            [
              {
                "tag_name": "v0.3.1-version-selector",
                "name": "Version selector",
                "html_url": "https://github.com/weathour/vehicle-offline-voice-android/releases/tag/v0.3.1-version-selector",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
                  {"name": "vehicle-offline-voice-v0.3.1-debug.apk", "browser_download_url": "https://example.com/app.apk"}
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, releases.size)
        assertEquals("v0.3.1-version-selector", releases.first().tagName)
        assertEquals("https://example.com/app.apk", releases.first().apkAssetUrl())
    }

    @Test
    fun apkAssetUrl_returnsNullWhenReleaseHasNoApk() {
        val release = GitHubReleaseVersion(
            tagName = "v0.3.0",
            name = "old",
            htmlUrl = "https://example.com",
            draft = false,
            prerelease = false,
            assets = listOf(GitHubReleaseAsset("readme.txt", "https://example.com/readme.txt"))
        )

        assertNull(release.apkAssetUrl())
    }
}
