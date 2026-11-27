package com.example.game.update

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class UpdateCheckState(
    val currentVersionLabel: String = "v${BuildConfig.VERSION_NAME}",
    val buildCode: Int = BuildConfig.VERSION_CODE,
    val updateAvailable: Boolean = false,
    val releaseTimestampIso: String? = null,
    val releaseDateLabel: String? = null,
    val releaseName: String = "Rookvale Latest Android Build",
    val downloadUrl: String = GitHubUpdateChecker.RELEASES_PAGE_URL
)

data class ParsedReleaseInfo(
    val latestTimestampIso: String,
    val latestTimestampMs: Long,
    val releaseName: String,
    val downloadUrl: String,
    /** Build number of the published APK, read from the release notes ("versionCode N"). */
    val versionCode: Int? = null
)

/**
 * Lightweight, silent-on-offline GitHub Release update checker for Rookvale's rolling `latest` tag.
 * Compares the remote release/asset timestamp against max(BuildConfig.BUILD_TIME_UTC, lastDismissedReleaseMs)
 * with a 2-minute CI clock skew grace window so newly installed APKs never falsely prompt for themselves.
 */
object GitHubUpdateChecker {
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/meenos-svg/Rookvale-Games/releases/tags/latest"
    const val RELEASES_PAGE_URL =
        "https://github.com/meenos-svg/Rookvale-Games/releases"
    private const val ALLOWED_URL_PREFIX =
        "https://github.com/meenos-svg/Rookvale-Games/releases"

    private const val PREFS_NAME = "rookvale_update_prefs"
    private const val KEY_DISMISSED_RELEASE_TS = "last_seen_release_timestamp_iso"
    private const val CI_CLOCK_SKEW_GRACE_MS = 120_000L
    private val VERSION_CODE_REGEX = Regex("versionCode\\s+(\\d+)")

    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(
        context: Context,
        installedBuildTimeIso: String = BuildConfig.BUILD_TIME_UTC
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val baseState = UpdateCheckState(
            currentVersionLabel = "v${BuildConfig.VERSION_NAME}",
            buildCode = BuildConfig.VERSION_CODE
        )
        val dismissedIso = loadDismissedReleaseTimestamp(context)
        val rawJson = fetchLatestReleaseJson() ?: return@withContext baseState
        evaluateUpdateFromJson(
            rawJson = rawJson,
            installedBuildTimeIso = installedBuildTimeIso,
            dismissedReleaseIso = dismissedIso,
            baseState = baseState
        )
    }

    fun dismissRelease(context: Context, releaseTimestampIso: String?) {
        if (releaseTimestampIso.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISMISSED_RELEASE_TS, releaseTimestampIso).apply()
    }

    fun loadDismissedReleaseTimestamp(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISMISSED_RELEASE_TS, null)
    }

    internal fun evaluateUpdateFromJson(
        rawJson: String,
        installedBuildTimeIso: String,
        dismissedReleaseIso: String?,
        baseState: UpdateCheckState = UpdateCheckState()
    ): UpdateCheckState {
        val parsed = parseReleaseJson(rawJson) ?: return baseState
        val dismissedMsForCode = parseIsoUtcMillis(dismissedReleaseIso) ?: 0L

        // Preferred: compare build numbers. The release asset is uploaded minutes after the APK's
        // BUILD_TIME_UTC is stamped, so timestamps alone made the newest APK nag about itself.
        val remoteCode = parsed.versionCode
        if (remoteCode != null && baseState.buildCode > 0) {
            val newer = remoteCode > baseState.buildCode && parsed.latestTimestampMs > dismissedMsForCode
            return if (!newer) baseState else baseState.copy(
                updateAvailable = true,
                releaseTimestampIso = parsed.latestTimestampIso,
                releaseDateLabel = formatFriendlyDate(parsed.latestTimestampMs),
                releaseName = parsed.releaseName,
                downloadUrl = parsed.downloadUrl
            )
        }

        val installedMs = parseIsoUtcMillis(installedBuildTimeIso) ?: 0L
        val dismissedMs = parseIsoUtcMillis(dismissedReleaseIso) ?: 0L

        // Compare against both the APK compile timestamp (+ 2m CI skew buffer) and last dismissed timestamp
        val buildThresholdMs = if (installedMs > 0L) installedMs + CI_CLOCK_SKEW_GRACE_MS else 0L
        val isNewerThanInstalled = parsed.latestTimestampMs > buildThresholdMs
        val isNewerThanDismissed = parsed.latestTimestampMs > dismissedMs

        if (!isNewerThanInstalled || !isNewerThanDismissed) {
            return baseState
        }

        return baseState.copy(
            updateAvailable = true,
            releaseTimestampIso = parsed.latestTimestampIso,
            releaseDateLabel = formatFriendlyDate(parsed.latestTimestampMs),
            releaseName = parsed.releaseName,
            downloadUrl = parsed.downloadUrl
        )
    }

    internal fun parseReleaseJson(rawJson: String): ParsedReleaseInfo? {
        val root = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val candidates = mutableListOf<Pair<String, Long>>()

        fun considerTimestamp(iso: String?) {
            if (iso.isNullOrBlank()) return
            val ms = parseIsoUtcMillis(iso) ?: return
            candidates += (iso to ms)
        }

        considerTimestamp(root["published_at"] as? String)
        considerTimestamp(root["updated_at"] as? String)

        val assets = root["assets"] as? List<*>
        assets?.forEach { asset ->
            @Suppress("UNCHECKED_CAST")
            val assetMap = asset as? Map<String, Any?> ?: return@forEach
            considerTimestamp(assetMap["updated_at"] as? String)
        }

        val latest = candidates.maxByOrNull { it.second } ?: return null
        val rawName = (root["name"] as? String)
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.trim()
            ?.take(60)
            .orEmpty()
            .ifEmpty { "Rookvale Latest Android Build" }

        val safeUrl = validateReleaseUrl(root["html_url"] as? String)
        val code = (root["body"] as? String)
            ?.let { VERSION_CODE_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return ParsedReleaseInfo(
            latestTimestampIso = latest.first,
            latestTimestampMs = latest.second,
            releaseName = rawName,
            downloadUrl = safeUrl,
            versionCode = code
        )
    }

    internal fun validateReleaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        return if (trimmed.startsWith(ALLOWED_URL_PREFIX) && !trimmed.contains(" ")) {
            trimmed
        } else {
            RELEASES_PAGE_URL
        }
    }

    internal fun parseIsoUtcMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(iso.trim())?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun formatFriendlyDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, HH:mm 'UTC'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    private fun fetchLatestReleaseJson(): String? {
        return runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Rookvale-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull()
    }
}
