package com.example.game.world

import com.example.game.model.GraphicsQuality
import com.example.game.model.normalizeRegionId

/**
 * Ambient overland weather types across Rookvale's 1600x1200 multi-region continent.
 */
enum class WeatherType(
    val displayName: String,
    val shortLabel: String,
    val shaderModeId: Float,
    val windX: Float,
    val windY: Float
) {
    CLEAR(
        displayName = "Clear Skies",
        shortLabel = "Clear",
        shaderModeId = 0f,
        windX = 0f,
        windY = 0f
    ),
    LIGHT_RAIN(
        displayName = "Light Rain",
        shortLabel = "Rain",
        shaderModeId = 1f,
        windX = -0.34f,
        windY = 1.0f
    ),
    FALLING_LEAVES(
        displayName = "Falling Leaves",
        shortLabel = "Leaves",
        shaderModeId = 2f,
        windX = -0.56f,
        windY = 0.44f
    ),
    SNOW(
        displayName = "Snowfall",
        shortLabel = "Snow",
        shaderModeId = 3f,
        windX = -0.24f,
        windY = 0.42f
    ),
    VOLCANIC_ASH(
        displayName = "Volcanic Ash",
        shortLabel = "Ash",
        shaderModeId = 4f,
        windX = -0.28f,
        windY = 0.28f
    ),
    SWAMP_MIST(
        displayName = "Swamp Mist",
        shortLabel = "Mist",
        shaderModeId = 5f,
        windX = 0.24f,
        windY = -0.05f
    )
}

/**
 * Weighted weather occurrence probability (0..100) for a given overland biome.
 */
data class WeatherWeight(
    val weather: WeatherType,
    val weightPercent: Int
)

/**
 * Graphics-quality-scaled rendering budget for ambient weather particles and shaders.
 */
data class WeatherVisualProfile(
    val particleCount: Int,
    val enableGroundRipples: Boolean,
    val enableSubtleVeil: Boolean,
    val enableAtmosphericGradient: Boolean,
    val enableAgslShader: Boolean,
    val particleScale: Float
)

/**
 * Deterministic resolved weather state for a region at a specific point on the shared UTC clock.
 */
data class ResolvedWeatherState(
    val regionId: String,
    val activeWeather: WeatherType,
    val previousWeather: WeatherType,
    val transitionBlend: Float,
    val intensity: Float,
    val isAdminOverride: Boolean = false,
    val isSuppressedUnderground: Boolean = false
)

/**
 * Shared deterministic biome-based weather system for Rookvale's 12 overland regions.
 *
 * Uses UTC epoch windows ([WEATHER_EPOCH_MS]) combined with a deterministic hash of
 * `(epochIndex, regionId)` so all online players in the same biome simultaneously experience
 * identical weather without extra network traffic, with smooth crossfades across epoch boundaries.
 */
object BiomeWeatherSystem {
    /** Duration of each deterministic weather window across the world (4 minutes). */
    const val WEATHER_EPOCH_MS: Long = 240_000L

    /** Duration of the smooth fade-in transition at the start of each new weather window. */
    const val TRANSITION_WINDOW_MS: Long = 15_000L

    /**
     * Authentic biome-specific weather probability tables (each summing strictly to 100)
     * for all 12 overland sectors of the 1600x1200 continent.
     */
    val biomeFrequencies: Map<String, List<WeatherWeight>> = mapOf(
        WorldRegionMeta.ROOKVALE_VALLEY.regionId to listOf(
            WeatherWeight(WeatherType.CLEAR, 50),
            WeatherWeight(WeatherType.LIGHT_RAIN, 25),
            WeatherWeight(WeatherType.FALLING_LEAVES, 25)
        ),
        WorldRegionMeta.SPIREWOOD_WEST.regionId to listOf(
            WeatherWeight(WeatherType.FALLING_LEAVES, 45),
            WeatherWeight(WeatherType.CLEAR, 30),
            WeatherWeight(WeatherType.LIGHT_RAIN, 25)
        ),
        WorldRegionMeta.ALDER_SEA_PLAINS.regionId to listOf(
            WeatherWeight(WeatherType.CLEAR, 40),
            WeatherWeight(WeatherType.LIGHT_RAIN, 40),
            WeatherWeight(WeatherType.SWAMP_MIST, 20)
        ),
        WorldRegionMeta.FROSTCREST_HIGHLANDS.regionId to listOf(
            WeatherWeight(WeatherType.SNOW, 65),
            WeatherWeight(WeatherType.CLEAR, 25),
            WeatherWeight(WeatherType.LIGHT_RAIN, 10)
        ),
        WorldRegionMeta.FROSTBAY_STRAITS.regionId to listOf(
            WeatherWeight(WeatherType.SNOW, 50),
            WeatherWeight(WeatherType.CLEAR, 30),
            WeatherWeight(WeatherType.LIGHT_RAIN, 20)
        ),
        WorldRegionMeta.CALDERA_PEAKS.regionId to listOf(
            WeatherWeight(WeatherType.VOLCANIC_ASH, 55),
            WeatherWeight(WeatherType.CLEAR, 25),
            WeatherWeight(WeatherType.SNOW, 20)
        ),
        WorldRegionMeta.EMBER_ISLES.regionId to listOf(
            WeatherWeight(WeatherType.VOLCANIC_ASH, 65),
            WeatherWeight(WeatherType.CLEAR, 25),
            WeatherWeight(WeatherType.LIGHT_RAIN, 10)
        ),
        WorldRegionMeta.WRITHING_MARSHES.regionId to listOf(
            WeatherWeight(WeatherType.SWAMP_MIST, 55),
            WeatherWeight(WeatherType.LIGHT_RAIN, 30),
            WeatherWeight(WeatherType.CLEAR, 15)
        ),
        WorldRegionMeta.BLIGHTWOOD_REACH.regionId to listOf(
            WeatherWeight(WeatherType.SWAMP_MIST, 45),
            WeatherWeight(WeatherType.FALLING_LEAVES, 25),
            WeatherWeight(WeatherType.LIGHT_RAIN, 20),
            WeatherWeight(WeatherType.CLEAR, 10)
        ),
        WorldRegionMeta.EMERALD_JUNGLE_EAST.regionId to listOf(
            WeatherWeight(WeatherType.LIGHT_RAIN, 45),
            WeatherWeight(WeatherType.FALLING_LEAVES, 35),
            WeatherWeight(WeatherType.CLEAR, 20)
        ),
        WorldRegionMeta.MAELSTROM_JUNGLE_WEST.regionId to listOf(
            WeatherWeight(WeatherType.LIGHT_RAIN, 55),
            WeatherWeight(WeatherType.SWAMP_MIST, 20),
            WeatherWeight(WeatherType.FALLING_LEAVES, 15),
            WeatherWeight(WeatherType.CLEAR, 10)
        ),
        WorldRegionMeta.SUNSPIRE_DESERT.regionId to listOf(
            WeatherWeight(WeatherType.CLEAR, 70),
            WeatherWeight(WeatherType.VOLCANIC_ASH, 20),
            WeatherWeight(WeatherType.LIGHT_RAIN, 10)
        )
    )

    fun frequenciesForRegion(regionId: String): List<WeatherWeight> {
        val normalized = normalizeRegionId(regionId)
        return biomeFrequencies[normalized] ?: biomeFrequencies.getValue(WorldRegionMeta.ROOKVALE_VALLEY.regionId)
    }

    /**
     * Deterministically selects the weather for a given [regionId] and UTC [epochIndex].
     * Guarantees identical output across all devices for the same biome and time window.
     */
    fun weatherForEpoch(regionId: String, epochIndex: Long): WeatherType {
        val normalized = normalizeRegionId(regionId)
        val table = frequenciesForRegion(normalized)
        val bucket = deterministicPercentBucket(normalized, epochIndex)
        var cumulative = 0
        for (entry in table) {
            cumulative += entry.weightPercent
            if (bucket < cumulative) return entry.weather
        }
        return table.lastOrNull()?.weather ?: WeatherType.CLEAR
    }

    /**
     * Resolves the current weather state for a player at ([regionId], [floor]) and [utcMillis],
     * honoring underground suppression (`floor != 0`) and local Admin overrides.
     */
    fun resolveWeather(
        regionId: String,
        floor: Int,
        utcMillis: Long,
        adminOverride: WeatherType? = null
    ): ResolvedWeatherState {
        val normalized = normalizeRegionId(regionId)
        if (floor != 0) {
            return ResolvedWeatherState(
                regionId = normalized,
                activeWeather = WeatherType.CLEAR,
                previousWeather = WeatherType.CLEAR,
                transitionBlend = 1f,
                intensity = 0f,
                isAdminOverride = adminOverride != null,
                isSuppressedUnderground = true
            )
        }

        if (adminOverride != null) {
            return ResolvedWeatherState(
                regionId = normalized,
                activeWeather = adminOverride,
                previousWeather = adminOverride,
                transitionBlend = 1f,
                intensity = if (adminOverride == WeatherType.CLEAR) 0f else 1f,
                isAdminOverride = true,
                isSuppressedUnderground = false
            )
        }

        val safeMillis = utcMillis.coerceAtLeast(0L)
        val epochIndex = safeMillis / WEATHER_EPOCH_MS
        val elapsedInEpoch = safeMillis % WEATHER_EPOCH_MS
        val current = weatherForEpoch(normalized, epochIndex)
        val previous = weatherForEpoch(normalized, (epochIndex - 1L).coerceAtLeast(0L))

        val rawBlend = if (current == previous) {
            1f
        } else {
            (elapsedInEpoch.toFloat() / TRANSITION_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
        }
        val smoothBlend = smoothstep(rawBlend)
        val intensity = when {
            current == WeatherType.CLEAR && previous == WeatherType.CLEAR -> 0f
            current == WeatherType.CLEAR -> 1f - smoothBlend
            previous == WeatherType.CLEAR -> smoothBlend
            else -> 1f
        }

        return ResolvedWeatherState(
            regionId = normalized,
            activeWeather = current,
            previousWeather = previous,
            transitionBlend = smoothBlend,
            intensity = intensity.coerceIn(0f, 1f),
            isAdminOverride = false,
            isSuppressedUnderground = false
        )
    }

    /**
     * Scales weather particle counts, ground ripples, and atmospheric visual shaders to match
     * the player's active [GraphicsQuality] tier.
     */
    fun visualProfileFor(quality: GraphicsQuality): WeatherVisualProfile = when (quality) {
        GraphicsQuality.LOW -> WeatherVisualProfile(
            particleCount = 0,
            enableGroundRipples = false,
            enableSubtleVeil = true,
            enableAtmosphericGradient = false,
            enableAgslShader = false,
            particleScale = 0.85f
        )
        GraphicsQuality.MEDIUM -> WeatherVisualProfile(
            particleCount = 34,
            enableGroundRipples = false,
            enableSubtleVeil = true,
            enableAtmosphericGradient = false,
            enableAgslShader = false,
            particleScale = 1.0f
        )
        GraphicsQuality.HIGH -> WeatherVisualProfile(
            particleCount = 78,
            enableGroundRipples = true,
            enableSubtleVeil = true,
            enableAtmosphericGradient = true,
            enableAgslShader = false,
            particleScale = 1.06f
        )
        GraphicsQuality.HD -> WeatherVisualProfile(
            particleCount = 120,
            enableGroundRipples = true,
            enableSubtleVeil = true,
            enableAtmosphericGradient = true,
            enableAgslShader = true,
            particleScale = 1.12f
        )
    }

    internal fun deterministicPercentBucket(regionId: String, epochIndex: Long): Int {
        // Stable SplitMix64 mixing over region FNV-1a hash + epoch index (platform-independent)
        var regionHash = -3750763034362895579L
        for (ch in regionId) {
            regionHash = regionHash xor ch.code.toLong()
            regionHash *= 1099511628211L
        }
        var z = regionHash xor (epochIndex * -7046029254386353131L + 0x9E3779B97F4A7C15UL.toLong())
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        val unsignedMod = (z ushr 1) % 100L
        return unsignedMod.toInt().coerceIn(0, 99)
    }

    private fun smoothstep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
