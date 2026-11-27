package com.example.game.model

/** How health, mana and XP are shown in the HUD. */
enum class VitalsStyle(val label: String) { ORB("Orb"), BARS("Classic bars") }

/** HUD pieces the player can move (and some can hide). */
enum class HudElement(val label: String, val hideable: Boolean) {
    VITALS("Health & minimap", false),
    BATTLE("Battle list", true),
    RAIL("Menu buttons", false),
    TRACKER("Tracker", true),
    TARGET("Target frame", true),
    HOTBAR("Hotbar", false),
    CHAT("Chat & log", true)
}

/**
 * Device-local HUD customization: vitals style, hotbar size, where each piece sits (offset in dp
 * from its default spot) and which optional pieces are hidden.
 */
data class HudLayout(
    val vitalsStyle: VitalsStyle = VitalsStyle.ORB,
    val hotbarSize: Int = 7,
    val offsets: Map<HudElement, Pair<Float, Float>> = emptyMap(),
    val hidden: Set<HudElement> = emptySet()
) {
    fun offsetOf(e: HudElement): Pair<Float, Float> = offsets[e] ?: (0f to 0f)
    fun isVisible(e: HudElement): Boolean = !(e.hideable && e in hidden)

    fun withOffset(e: HudElement, x: Float, y: Float): HudLayout =
        copy(offsets = offsets + (e to (x.coerceIn(-MAX_SHIFT, MAX_SHIFT) to y.coerceIn(-MAX_SHIFT, MAX_SHIFT))))

    fun toggled(e: HudElement): HudLayout =
        if (!e.hideable) this else copy(hidden = if (e in hidden) hidden - e else hidden + e)

    fun encodeOffsets(): String = offsets.entries.joinToString(";") { (e, p) -> "${e.name}:${p.first},${p.second}" }
    fun encodeHidden(): String = hidden.joinToString(",") { it.name }

    companion object {
        const val MAX_SHIFT = 1200f
        val HOTBAR_SIZES = listOf(5, 7, 9)

        fun decodeOffsets(raw: String?): Map<HudElement, Pair<Float, Float>> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(';').mapNotNull { part ->
                val name = part.substringBefore(':', "")
                val xy = part.substringAfter(':', "").split(',')
                val e = HudElement.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
                val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                e to (x.coerceIn(-MAX_SHIFT, MAX_SHIFT) to y.coerceIn(-MAX_SHIFT, MAX_SHIFT))
            }.toMap()
        }

        fun decodeHidden(raw: String?): Set<HudElement> =
            raw.orEmpty().split(',').mapNotNull { n -> HudElement.entries.firstOrNull { it.name == n && it.hideable } }.toSet()
    }
}

/** Hotbar slot actions (anything that isn't a specific item). */
object HotbarActions {
    const val PREFIX = "action:"
    const val HEALTH_POTION = "action:hp"
    const val MANA_POTION = "action:mp"
    const val HEAL_RUNE = "action:heal_rune"
    const val ATTACK_RUNE = "action:attack_rune"
    const val ROPE = "action:rope"
    const val EAT_FOOD = "action:eat"
    const val OPEN_BAG = "action:bag"
    const val OPEN_GEAR = "action:gear"
    const val OPEN_MAP = "action:map"
    const val OPEN_SKILLS = "action:skills"

    const val MAX_SLOTS = 9

    /** Default bar: the classic five plus empty slots. */
    val DEFAULT_SLOTS: List<String?> = listOf(HEALTH_POTION, MANA_POTION, HEAL_RUNE, ATTACK_RUNE, ROPE, null, null, null, null)

    val labels: Map<String, String> = linkedMapOf(
        HEALTH_POTION to "Health Potion",
        MANA_POTION to "Mana Potion",
        HEAL_RUNE to "Healing Rune (bound)",
        ATTACK_RUNE to "Attack Rune (bound)",
        ROPE to "Rope",
        EAT_FOOD to "Eat best food",
        OPEN_BAG to "Open Bag",
        OPEN_GEAR to "Open Gear",
        OPEN_MAP to "Open Map",
        OPEN_SKILLS to "Open Skills"
    )

    fun isAction(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    /** Old saves stored only the two extra slots; put them after the classic five. */
    fun migrate(saved: List<String?>): List<String?> {
        val base = if (saved.size <= 2) DEFAULT_SLOTS.take(5) + saved + List(2 - saved.size) { null } else saved
        return List(MAX_SLOTS) { idx -> base.getOrNull(idx) }
    }
}
