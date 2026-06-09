package com.company.vehiclevoice.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseUpdateClientTest {
    @Test
    fun parseReleaseAtom_extractsReleaseTagsAndDerivedApkUrls() {
        val releases = parseReleaseAtom(
            """
            <feed>
              <entry>
                <id>tag:github.com,2008:Repository/1262716404/v0.3.1-version-selector</id>
                <link rel="alternate" type="text/html" href="https://github.com/weathour/vehicle-offline-voice-android/releases/tag/v0.3.1-version-selector"/>
                <title>v0.3.1 Version Selector (debug)</title>
              </entry>
            </feed>
            """.trimIndent()
        )

        assertEquals(1, releases.size)
        assertEquals("v0.3.1-version-selector", releases.first().tagName)
        assertEquals(
            "https://github.com/weathour/vehicle-offline-voice-android/releases/download/v0.3.1-version-selector/vehicle-offline-voice-android-v0.3.1-version-selector-debug.apk",
            releases.first().apkAssetUrl()
        )
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
