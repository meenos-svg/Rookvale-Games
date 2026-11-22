package com.example.game.multiplayer

import com.example.BuildConfig
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Configuration for Supabase Realtime WebSockets (Presence + Broadcast).
 * Credentials are injected via BuildConfig from AI Studio Secrets (.env / .env.example).
 * If either credential is blank or still a placeholder, the game falls back seamlessly to Offline Solo mode.
 */
object MultiplayerConfig {
    val SUPABASE_URL: String
        get() = BuildConfig.SUPABASE_URL.trim()

    val SUPABASE_ANON_KEY: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    const val WORLD_CHANNEL_TOPIC = "realtime:rookvale:world"

    fun floorChannelTopic(floor: Int): String = "realtime:rookvale:floor:$floor"

    fun isConfigured(
        url: String = SUPABASE_URL,
        anonKey: String = SUPABASE_ANON_KEY
    ): Boolean {
        if (url.isBlank() || anonKey.isBlank()) return false
        if (isPlaceholder(url) || isPlaceholder(anonKey)) return false
        return parseValidHttpsHost(url) != null
    }

    fun realtimeWebSocketUrl(
        url: String = SUPABASE_URL,
        anonKey: String = SUPABASE_ANON_KEY
    ): String? {
        if (!isConfigured(url, anonKey)) return null
        val host = parseValidHttpsHost(url) ?: return null
        val encodedKey = URLEncoder.encode(anonKey, StandardCharsets.UTF_8.name())
        return "wss://$host/realtime/v1/websocket?apikey=$encodedKey&vsn=1.0.0"
    }

    private fun isPlaceholder(value: String): Boolean {
        val upper = value.trim().uppercase()
        return upper.startsWith("YOUR_") ||
            upper.startsWith("MY_") ||
            upper.contains("PLACEHOLDER") ||
            upper.contains("REPLACE_ME")
    }

    private fun parseValidHttpsHost(rawUrl: String): String? {
        return runCatching {
            val uri = URI(rawUrl.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (scheme != "https" || host.isBlank()) return null
            if (host == "localhost" || host == "127.0.0.1") return null
            if (uri.port != -1 && uri.port != 443) "$host:${uri.port}" else host
        }.getOrNull()
    }
}
