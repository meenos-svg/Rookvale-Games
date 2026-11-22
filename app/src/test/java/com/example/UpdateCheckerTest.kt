package com.example

import com.example.game.model.Vocation
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.update.GitHubUpdateChecker
import com.example.game.update.UpdateCheckState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseReleaseJson_picksNewestAcrossPublishedUpdatedAndAssetTimestamps() {
        val json = """
            {
              "name": "Rookvale Build #42",
              "html_url": "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
              "published_at": "2026-04-10T10:00:00Z",
              "updated_at": "2026-04-10T10:05:00Z",
              "assets": [
                {
                  "name": "rookvale-debug.apk",
                  "updated_at": "2026-04-10T10:12:30Z"
                }
              ]
            }
        """.trimIndent()

        val parsed = GitHubUpdateChecker.parseReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("2026-04-10T10:12:30Z", parsed!!.latestTimestampIso)
        assertEquals("Rookvale Build #42", parsed.releaseName)
        assertEquals(
            "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
            parsed.downloadUrl
        )
    }

    @Test
    fun evaluateUpdate_suppressesFalsePositiveWithinTwoMinuteClockSkewWindow() {
        val json = """
            {
              "name": "Rookvale Latest",
              "html_url": "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
              "published_at": "2026-04-10T10:01:30Z"
            }
        """.trimIndent()

        val result = GitHubUpdateChecker.evaluateUpdateFromJson(
            rawJson = json,
            installedBuildTimeIso = "2026-04-10T10:00:00Z",
            dismissedReleaseIso = null,
            baseState = UpdateCheckState(currentVersionLabel = "v1.2", buildCode = 2)
        )

        assertFalse(result.updateAvailable)
        assertNull(result.releaseTimestampIso)
    }

    @Test
    fun evaluateUpdate_triggersWhenRemoteReleaseIsNewerThanInstalledAndDismissed() {
        val json = """
            {
              "name": "Rookvale Co-op Update",
              "html_url": "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
              "published_at": "2026-04-10T12:30:00Z"
            }
        """.trimIndent()

        val result = GitHubUpdateChecker.evaluateUpdateFromJson(
            rawJson = json,
            installedBuildTimeIso = "2026-04-10T10:00:00Z",
            dismissedReleaseIso = "2026-04-10T11:00:00Z",
            baseState = UpdateCheckState(currentVersionLabel = "v1.2", buildCode = 2)
        )

        assertTrue(result.updateAvailable)
        assertEquals("2026-04-10T12:30:00Z", result.releaseTimestampIso)
        assertEquals("Rookvale Co-op Update", result.releaseName)
        assertNotNull(result.releaseDateLabel)
    }

    @Test
    fun evaluateUpdate_respectsDismissedTimestampAndRetriggersOnNewerBuild() {
        val release1Json = """
            {
              "name": "Build 1",
              "html_url": "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
              "published_at": "2026-04-10T12:30:00Z"
            }
        """.trimIndent()

        val afterDismiss = GitHubUpdateChecker.evaluateUpdateFromJson(
            rawJson = release1Json,
            installedBuildTimeIso = "2026-04-10T10:00:00Z",
            dismissedReleaseIso = "2026-04-10T12:30:00Z"
        )
        assertFalse(afterDismiss.updateAvailable)

        val release2Json = """
            {
              "name": "Build 2",
              "html_url": "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
              "published_at": "2026-04-11T08:00:00Z"
            }
        """.trimIndent()

        val newerRelease = GitHubUpdateChecker.evaluateUpdateFromJson(
            rawJson = release2Json,
            installedBuildTimeIso = "2026-04-10T10:00:00Z",
            dismissedReleaseIso = "2026-04-10T12:30:00Z"
        )
        assertTrue(newerRelease.updateAvailable)
        assertEquals("2026-04-11T08:00:00Z", newerRelease.releaseTimestampIso)
    }

    @Test
    fun validateReleaseUrl_rejectsUntrustedSchemesAndForeignDomains() {
        assertEquals(
            "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build",
            GitHubUpdateChecker.validateReleaseUrl(
                "https://github.com/meenos-svg/Rookvale-Games/releases/tag/latest-build"
            )
        )
        assertEquals(
            GitHubUpdateChecker.RELEASES_PAGE_URL,
            GitHubUpdateChecker.validateReleaseUrl("https://evil.example.com/rookvale.apk")
        )
        assertEquals(
            GitHubUpdateChecker.RELEASES_PAGE_URL,
            GitHubUpdateChecker.validateReleaseUrl("javascript:alert(1)")
        )
        assertEquals(
            GitHubUpdateChecker.RELEASES_PAGE_URL,
            GitHubUpdateChecker.validateReleaseUrl("http://github.com/meenos-svg/Rookvale-Games/releases")
        )
        assertEquals(
            GitHubUpdateChecker.RELEASES_PAGE_URL,
            GitHubUpdateChecker.validateReleaseUrl(null)
        )
    }

    @Test
    fun ensureConnectedOrRetry_handlesConfiguredAndUnconfiguredBuildsCleanly() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val manager = RealtimeMultiplayerManager(scope = scope)
            val payload = LocalPlayerPresencePayload(
                playerId = "hero_retry_test",
                name = "Aldric",
                level = 5,
                vocation = Vocation.ADVENTURER,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 48,
                y = 48,
                floor = 0,
                hp = 180,
                maxHp = 180,
                joinedAt = 1000L
            )

            val result = manager.ensureConnectedOrRetry(payload)
            if (!MultiplayerConfig.isConfigured()) {
                assertEquals(RealtimeMultiplayerManager.RetryResult.MISSING_CREDENTIALS, result)
                assertEquals(ConnectionStatus.OFFLINE_SOLO, manager.uiState.value.status)
            } else {
                assertEquals(RealtimeMultiplayerManager.RetryResult.CONNECTING, result)
                manager.disconnect()
                assertEquals(ConnectionStatus.OFFLINE_SOLO, manager.uiState.value.status)
            }
        } finally {
            scope.cancel()
        }
    }
}
