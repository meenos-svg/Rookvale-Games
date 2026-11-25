package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.game.model.AdventurerPalette
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.Vocation
import com.example.game.multiplayer.RemotePlayerState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class ScreenFacing {
    FRONT,
    BACK,
    LEFT,
    RIGHT
}

/**
 * True 8-directional camera-relative isometric viewing octants (`entityFacing - cameraYaw`).
 */
enum class ScreenOctant(val label: String) {
    FRONT("Front"),
    FRONT_RIGHT("Front-Right"),
    RIGHT("Right"),
    BACK_RIGHT("Back-Right"),
    BACK("Back"),
    BACK_LEFT("Back-Left"),
    LEFT("Left"),
    FRONT_LEFT("Front-Left");

    val isRear: Boolean
        get() = this == BACK || this == BACK_LEFT || this == BACK_RIGHT
}

/**
 * Continuous 2.5D/3D camera-relative orientation basis for an entity.
 *
 * Coordinate convention (right-handed entity-local 3D space):
 * - `+localR`: entity's OWN right side (weapon hand). At FRONT (`relativeAngleRad == 0`), points screen-left (`-X`).
 * - `+localF`: entity's forward facing (nose/chest). At FRONT (`relativeAngleRad == 0`), points toward viewer (`depth = +1`).
 * - `+localUp`: vertical height above ground plane (foreshortened by ~38° isometric pitch).
 */
data class ResolvedScreenFacing(
    val quadrant: ScreenFacing,
    val screenDirX: Float,
    val screenDirY: Float,
    val octant: ScreenOctant = ScreenOctant.FRONT,
    val relativeAngleRad: Float = 0f,
    val lightScreenX: Float = -0.72f,
    val lightScreenY: Float = -0.62f
) {
    /** Horizontal screen-X component of entity's forward axis (`+1` = screen-right). */
    val forwardScreenX: Float get() = sin(relativeAngleRad)

    /** Depth component of entity's forward axis (`+1` = facing viewer, `-1` = facing away). */
    val forwardDepth: Float get() = cos(relativeAngleRad)

    /** Horizontal screen-X component of entity's OWN right-shoulder axis (`-1` when facing viewer). */
    val rightScreenX: Float get() = -cos(relativeAngleRad)

    /** Depth component of entity's OWN right-shoulder axis (`+1` when turned right, `-1` when turned left). */
    val rightDepth: Float get() = sin(relativeAngleRad)

    /** True whenever the entity's back is angled toward the camera (`depth < -0.18`). */
    val isBackFacing: Boolean get() = forwardDepth < -0.18f

    /** Depth of a 3D entity-local point (larger = closer to viewer = drawn in front). */
    fun depthOf(localR: Float, localF: Float): Float =
        localR * rightDepth + localF * forwardDepth

    /**
     * Projects an entity-local 3D offset `(localR, localF, localUp)` in logical units `u`
     * into 2.5D isometric screen coordinates matching Rookvale's ~38° pitch.
     */
    fun project(
        originX: Float,
        originY: Float,
        u: Float,
        localR: Float,
        localF: Float,
        localUp: Float
    ): Offset {
        val sx = originX + (localR * rightScreenX + localF * forwardScreenX) * u
        val depth = depthOf(localR, localF)
        val sy = originY + depth * ISO_DEPTH_PITCH * u - localUp * ISO_UP_SCALE * u
        return Offset(sx, sy)
    }

    companion object {
        /** Isometric depth foreshortening factor (`sin(~38°) * 0.72 ≈ 0.44`). */
        const val ISO_DEPTH_PITCH = 0.44f
        /** Vertical foreshortening factor (`cos(~24°) ≈ 0.92`) for solid 2.5D proportions. */
        const val ISO_UP_SCALE = 0.92f
    }
}

/**
 * Resolves a character's or monster's continuous 3D camera-relative orientation (`entityFacing - cameraYaw`)
 * and 8-directional octant, plus smooth angular interpolation and screen-space light direction.
 */
object ScreenFacingResolver {

    fun directionToWorldRadians(direction: Direction): Float =
        atan2(direction.dy.toDouble(), direction.dx.toDouble()).toFloat()

    fun vectorToWorldRadians(dx: Float, dy: Float, fallback: Direction): Float =
        if (hypot(dx, dy) > 0.05f) atan2(dy.toDouble(), dx.toDouble()).toFloat()
        else directionToWorldRadians(fallback)

    /** Shortest-arc smooth angular interpolation in radians (handles `±PI` wrap cleanly). */
    fun shortestAngleLerp(currentRad: Float, targetRad: Float, alpha: Float): Float {
        var diff = (targetRad - currentRad) % (2f * PI.toFloat())
        if (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
        if (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
        return currentRad + diff * alpha.coerceIn(0f, 1f)
    }

    fun resolve(
        worldFacing: Direction,
        cameraYawDeg: Float,
        targetDeltaX: Float = 0f,
        targetDeltaY: Float = 0f
    ): ResolvedScreenFacing {
        val worldRad = vectorToWorldRadians(targetDeltaX, targetDeltaY, worldFacing)
        return resolveFromRadians(worldRad, cameraYawDeg)
    }

    fun resolveFromRadians(
        worldFacingRad: Float,
        cameraYawDeg: Float
    ): ResolvedScreenFacing {
        val wx = cos(worldFacingRad)
        val wy = sin(worldFacingRad)

        val camRad = IsometricCamera.normalizeYawDeg(cameraYawDeg) * (PI / 180.0)
        val cosY = cos(camRad).toFloat()
        val sinY = sin(camRad).toFloat()

        // Match IsometricCamera projection axes: +sx is screen-right, +sy is screen-down (toward viewer)
        val rawSx = wx * cosY - wy * sinY
        val rawSy = wx * sinY + wy * cosY
        val len = hypot(rawSx, rawSy).coerceAtLeast(0.001f)
        val sx = rawSx / len
        val sy = rawSy / len

        // Continuous camera-relative yaw where 0 rad = facing directly toward camera (+sy, sx=0)
        val relRad = atan2(sx.toDouble(), sy.toDouble()).toFloat()
        val relDeg = (relRad * (180.0 / PI)).toFloat()

        val octant = octantFromRelativeDeg(relDeg)

        // Preserve legacy 4-way quadrant mapping (at default 45° yaw: SOUTH->FRONT, EAST->RIGHT, NORTH->BACK, WEST->LEFT)
        val screenAngleDeg = (atan2(sy.toDouble(), sx.toDouble()) * (180.0 / PI)).toFloat()
        val quadrant = when {
            screenAngleDeg in 55f..160f -> ScreenFacing.FRONT
            screenAngleDeg in -25f..55f -> ScreenFacing.RIGHT
            screenAngleDeg in -125f..-25f -> ScreenFacing.BACK
            else -> ScreenFacing.LEFT
        }

        // Project canonical world light vector into screen space, gently stabilized to keep warm upper-left readability
        val rawLx = IsometricLightingEngine.LIGHT_DIR_X * cosY - IsometricLightingEngine.LIGHT_DIR_Y * sinY
        val rawLy = IsometricLightingEngine.LIGHT_DIR_X * sinY + IsometricLightingEngine.LIGHT_DIR_Y * cosY
        val blendedLx = (rawLx * 0.55f - 0.55f).coerceIn(-0.92f, 0.92f)
        val blendedLy = (rawLy * 0.35f - 0.65f).coerceIn(-0.95f, -0.25f)

        return ResolvedScreenFacing(
            quadrant = quadrant,
            screenDirX = sx,
            screenDirY = sy,
            octant = octant,
            relativeAngleRad = relRad,
            lightScreenX = blendedLx,
            lightScreenY = blendedLy
        )
    }

    /**
     * Resolves a direct camera-relative angle in degrees (`0° = FRONT`, `45° = FRONT_RIGHT`, `90° = RIGHT`,
     * `135° = BACK_RIGHT`, `180° = BACK`, `-135° = BACK_LEFT`, `-90° = LEFT`, `-45° = FRONT_LEFT`).
     * Used by interactive 8-directional paper-doll preview stages.
     */
    fun resolveForRelativeAngleDeg(relativeDeg: Float): ResolvedScreenFacing {
        var norm = relativeDeg % 360f
        if (norm > 180f) norm -= 360f
        if (norm <= -180f) norm += 360f
        val relRad = (norm * PI / 180.0).toFloat()
        val sx = sin(relRad)
        val sy = cos(relRad)
        val octant = octantFromRelativeDeg(norm)
        val quadrant = when {
            abs(norm) <= 45f -> ScreenFacing.FRONT
            norm in 45f..135f -> ScreenFacing.RIGHT
            norm in -135f..-45f -> ScreenFacing.LEFT
            else -> ScreenFacing.BACK
        }
        return ResolvedScreenFacing(
            quadrant = quadrant,
            screenDirX = sx,
            screenDirY = sy,
            octant = octant,
            relativeAngleRad = relRad,
            lightScreenX = -0.72f,
            lightScreenY = -0.62f
        )
    }

    fun octantFromRelativeDeg(relDeg: Float): ScreenOctant {
        var norm = relDeg % 360f
        if (norm > 180f) norm -= 360f
        if (norm <= -180f) norm += 360f
        return when {
            norm in -22.5f..22.5f -> ScreenOctant.FRONT
            norm in 22.5f..67.5f -> ScreenOctant.FRONT_RIGHT
            norm in 67.5f..112.5f -> ScreenOctant.RIGHT
            norm in 112.5f..157.5f -> ScreenOctant.BACK_RIGHT
            norm in -67.5f..-22.5f -> ScreenOctant.FRONT_LEFT
            norm in -112.5f..-67.5f -> ScreenOctant.LEFT
            norm in -157.5f..-112.5f -> ScreenOctant.BACK_LEFT
            else -> ScreenOctant.BACK
        }
    }
}

data class MaterialPalette(
    val primary: Color,
    val highlight: Color,
    val shadow: Color,
    val accent: Color = Color(0xFFE6B44C),
    val glow: Color? = null
)

enum class WeaponVisualKind {
    NONE,
    DAGGER,
    SWORD,
    FALCHION,
    RAPIER,
    CLAYMORE,
    AXE,
    GLAIVE,
    CLUB,
    BOW,
    ARBALEST,
    JAVELIN,
    WAND,
    STAFF
}

enum class OffhandVisualKind {
    NONE,
    ROUND_SHIELD,
    KITE_SHIELD,
    DRAGON_SHIELD,
    ARCANE_ORB
}

enum class ArmorVisualKind {
    TUNIC,
    LEATHER,
    ROBE,
    PLATE,
    WYRMSCALE
}

enum class HelmetVisualKind {
    NONE,
    LEATHER_CAP,
    VIKING_HELM,
    MAGE_HOOD,
    WIZARD_HAT
}

enum class BootsVisualKind {
    DEFAULT,
    TRAVELER,
    PLATE
}

data class PaperDollAppearance(
    val vocation: Vocation,
    val tunicPalette: MaterialPalette,
    val hairPalette: MaterialPalette,
    val skinPrimary: Color = Color(0xFFEBC29B),
    val skinHighlight: Color = Color(0xFFF7D7B7),
    val skinShadow: Color = Color(0xFFC5936A),
    val weaponKind: WeaponVisualKind = WeaponVisualKind.NONE,
    val weaponPalette: MaterialPalette = MaterialPalettes.IRON,
    val isTwoHanded: Boolean = false,
    val offhandKind: OffhandVisualKind = OffhandVisualKind.NONE,
    val offhandPalette: MaterialPalette = MaterialPalettes.OAK_WOOD,
    val armorKind: ArmorVisualKind = ArmorVisualKind.TUNIC,
    val armorPalette: MaterialPalette = tunicPalette,
    val helmetKind: HelmetVisualKind = HelmetVisualKind.NONE,
    val helmetPalette: MaterialPalette = MaterialPalettes.LEATHER,
    val legsPalette: MaterialPalette = MaterialPalettes.DEFAULT_TROUSERS,
    val bootsKind: BootsVisualKind = BootsVisualKind.DEFAULT,
    val bootsPalette: MaterialPalette = MaterialPalettes.DEFAULT_BOOTS,
    val hasAmulet: Boolean = false
) {
    companion object {
        fun fromEquipment(
            vocation: Vocation,
            tunicColorIndex: Int,
            hairColorIndex: Int,
            equipment: EquippedGear
        ): PaperDollAppearance = fromItemIds(
            vocation = vocation,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex,
            weaponId = equipment.weaponId,
            offhandId = equipment.shieldId,
            armorId = equipment.armorId,
            helmetId = equipment.helmetId,
            legsId = equipment.legsId,
            bootsId = equipment.bootsId,
            amuletId = equipment.amuletId
        )

        fun fromRemotePlayer(rp: RemotePlayerState): PaperDollAppearance = fromItemIds(
            vocation = rp.vocation,
            tunicColorIndex = rp.tunicColorIndex,
            hairColorIndex = rp.hairColorIndex,
            weaponId = rp.weaponId,
            offhandId = rp.offhandId,
            armorId = rp.armorId,
            helmetId = rp.helmetId,
            legsId = null,
            bootsId = rp.bootsId,
            amuletId = null
        )

        fun fromItemIds(
            vocation: Vocation,
            tunicColorIndex: Int,
            hairColorIndex: Int,
            weaponId: String?,
            offhandId: String?,
            armorId: String?,
            helmetId: String?,
            legsId: String? = null,
            bootsId: String? = null,
            amuletId: String? = null
        ): PaperDollAppearance {
            val baseTunicSwatch = AdventurerPalette.tunicAt(tunicColorIndex)
            val baseHairSwatch = AdventurerPalette.hairAt(hairColorIndex)

            val tunicPalette = when (vocation) {
                Vocation.ADVENTURER -> paletteFromArgb(baseTunicSwatch.primaryArgb, baseTunicSwatch.highlightArgb, Color(0xFFE6B44C))
                Vocation.KNIGHT -> MaterialPalettes.KNIGHT_SURCOAT
                Vocation.PALADIN -> MaterialPalettes.PALADIN_CLOAK
                Vocation.MAGE -> MaterialPalettes.MAGE_ROBE
            }
            val hairPalette = paletteFromArgb(baseHairSwatch.primaryArgb, baseHairSwatch.highlightArgb, Color(0xFF2B1C14))

            val weaponDef = weaponId?.takeIf { it.isNotBlank() }?.let { ItemCatalog.all[it] }
            val (weaponKind, weaponPalette) = resolveWeapon(weaponId)
            val is2H = weaponDef?.isTwoHanded == true || weaponKind in setOf(
                WeaponVisualKind.BOW,
                WeaponVisualKind.ARBALEST,
                WeaponVisualKind.CLAYMORE,
                WeaponVisualKind.GLAIVE,
                WeaponVisualKind.STAFF
            )

            val (offhandKind, offhandPalette) = when {
                is2H -> OffhandVisualKind.NONE to MaterialPalettes.OAK_WOOD
                !offhandId.isNullOrBlank() -> resolveOffhand(offhandId)
                weaponKind == WeaponVisualKind.WAND -> OffhandVisualKind.ARCANE_ORB to MaterialPalettes.ARCANE_FOCUS
                else -> OffhandVisualKind.NONE to MaterialPalettes.OAK_WOOD
            }

            val (armorKind, armorPalette) = resolveArmor(armorId, vocation, tunicPalette)
            val (helmetKind, helmetPalette) = resolveHelmet(helmetId, vocation)
            val legsPalette = resolveLegs(legsId, armorKind)
            val (bootsKind, bootsPalette) = resolveBoots(bootsId, armorKind)

            return PaperDollAppearance(
                vocation = vocation,
                tunicPalette = tunicPalette,
                hairPalette = hairPalette,
                weaponKind = weaponKind,
                weaponPalette = weaponPalette,
                isTwoHanded = is2H,
                offhandKind = offhandKind,
                offhandPalette = offhandPalette,
                armorKind = armorKind,
                armorPalette = armorPalette,
                helmetKind = helmetKind,
                helmetPalette = helmetPalette,
                legsPalette = legsPalette,
                bootsKind = bootsKind,
                bootsPalette = bootsPalette,
                hasAmulet = !amuletId.isNullOrBlank()
            )
        }

        private fun resolveWeapon(weaponId: String?): Pair<WeaponVisualKind, MaterialPalette> {
            val id = weaponId?.lowercase()?.trim().orEmpty()
            if (id.isEmpty()) return WeaponVisualKind.NONE to MaterialPalettes.IRON
            val def = ItemCatalog.all[id]
            val palette = materialPaletteForId(id, def?.category)
            val kind = when {
                id.contains("club") || id.contains("mace") || id.contains("cudgel") ||
                    id.contains("warmaul") || id.contains("warhammer") || id.contains("worldbreaker") -> WeaponVisualKind.CLUB
                id.contains("dirk") || id.contains("dagger") -> WeaponVisualKind.DAGGER
                id.contains("claymore") || id.contains("greatsword") -> WeaponVisualKind.CLAYMORE
                id.contains("longblade") || id.contains("longsword") -> WeaponVisualKind.SWORD
                id.contains("rapier") -> WeaponVisualKind.RAPIER
                id.contains("falchion") || id.contains("sabre") -> WeaponVisualKind.FALCHION
                id.contains("glaive") || id.contains("halberd") -> WeaponVisualKind.GLAIVE
                id.contains("axe") || id.contains("cleaver") || id.contains("hatchet") -> WeaponVisualKind.AXE
                id.contains("arbalest") || id.contains("crossbow") || id.contains("siegebow") -> WeaponVisualKind.ARBALEST
                id.contains("bow") || id.contains("recurve") -> WeaponVisualKind.BOW
                id.contains("javelin") || id.contains("spear") -> WeaponVisualKind.JAVELIN
                id.contains("crosier") || id.contains("rod") || id.contains("staff") || id.contains("soulspire") -> WeaponVisualKind.STAFF
                id.contains("wand") || id.contains("scepter") || id.contains("conduit") || id.contains("spire") -> WeaponVisualKind.WAND
                def?.category == ItemCategory.WEAPON_WAND -> if (def.isTwoHanded) WeaponVisualKind.STAFF else WeaponVisualKind.WAND
                def?.category == ItemCategory.WEAPON_DISTANCE -> if (def.isTwoHanded) WeaponVisualKind.BOW else WeaponVisualKind.JAVELIN
                else -> WeaponVisualKind.SWORD
            }
            return kind to palette
        }

        private fun resolveOffhand(offhandId: String): Pair<OffhandVisualKind, MaterialPalette> {
            val id = offhandId.lowercase().trim()
            return when {
                id.contains("cinderite") || id.contains("bastion") -> OffhandVisualKind.DRAGON_SHIELD to MaterialPalettes.CINDERITE
                id.contains("rime_plate") || id.contains("frostpeak") -> OffhandVisualKind.KITE_SHIELD to MaterialPalettes.RIME_PLATE
                id.contains("vesperite") || id.contains("bulwark") -> OffhandVisualKind.KITE_SHIELD to MaterialPalettes.VESPERITE
                id.contains("bog_steel") || id.contains("mire_woven") -> OffhandVisualKind.KITE_SHIELD to MaterialPalettes.MIRE_WOVEN
                id.contains("dragon") || id.contains("wyrm") -> OffhandVisualKind.DRAGON_SHIELD to MaterialPalettes.WYRMSCALE
                id.contains("steel") || id.contains("dwarven") || id.contains("kite") || id.contains("heater") ->
                    OffhandVisualKind.KITE_SHIELD to MaterialPalettes.DWARVEN_STEEL
                id.contains("orb") || id.contains("tome") || id.contains("focus") ->
                    OffhandVisualKind.ARCANE_ORB to MaterialPalettes.ARCANE_FOCUS
                else -> OffhandVisualKind.ROUND_SHIELD to MaterialPalettes.OAK_WOOD
            }
        }

        private fun resolveArmor(
            armorId: String?,
            vocation: Vocation,
            tunicPalette: MaterialPalette
        ): Pair<ArmorVisualKind, MaterialPalette> {
            val id = armorId?.lowercase()?.trim().orEmpty()
            return when {
                id.contains("cinderite") || id.contains("warplate") -> ArmorVisualKind.WYRMSCALE to MaterialPalettes.CINDERITE
                id.contains("rime_plate") || id.contains("frostpeak") -> ArmorVisualKind.PLATE to MaterialPalettes.RIME_PLATE
                id.contains("vesperite") || id.contains("aegis") -> ArmorVisualKind.PLATE to MaterialPalettes.VESPERITE
                id.contains("mire_woven") || id.contains("bog_steel") -> ArmorVisualKind.LEATHER to MaterialPalettes.MIRE_WOVEN
                id.contains("dragon") || id.contains("wyrm") -> ArmorVisualKind.WYRMSCALE to MaterialPalettes.WYRMSCALE
                id.contains("brass") || id.contains("plate") || id.contains("mail") -> ArmorVisualKind.PLATE to MaterialPalettes.BRASS_PLATE
                id.contains("robe") || id.contains("mystic") -> ArmorVisualKind.ROBE to MaterialPalettes.MAGE_ROBE
                id.contains("leather") -> ArmorVisualKind.LEATHER to MaterialPalettes.LEATHER
                vocation == Vocation.MAGE -> ArmorVisualKind.ROBE to MaterialPalettes.MAGE_ROBE
                vocation == Vocation.KNIGHT -> ArmorVisualKind.PLATE to MaterialPalettes.KNIGHT_SURCOAT
                else -> ArmorVisualKind.TUNIC to tunicPalette
            }
        }

        private fun resolveHelmet(helmetId: String?, vocation: Vocation): Pair<HelmetVisualKind, MaterialPalette> {
            val id = helmetId?.lowercase()?.trim().orEmpty()
            return when {
                id.contains("viking") || id.contains("iron") || id.contains("steel") -> HelmetVisualKind.VIKING_HELM to MaterialPalettes.DWARVEN_STEEL
                id.contains("hat") -> HelmetVisualKind.WIZARD_HAT to MaterialPalettes.MAGE_ROBE
                id.contains("hood") -> HelmetVisualKind.MAGE_HOOD to MaterialPalettes.MAGE_ROBE
                id.contains("leather") || id.contains("cap") -> HelmetVisualKind.LEATHER_CAP to MaterialPalettes.LEATHER
                vocation == Vocation.MAGE -> HelmetVisualKind.MAGE_HOOD to MaterialPalettes.MAGE_ROBE
                else -> HelmetVisualKind.NONE to MaterialPalettes.LEATHER
            }
        }

        private fun resolveLegs(legsId: String?, armorKind: ArmorVisualKind): MaterialPalette {
            val id = legsId?.lowercase()?.trim().orEmpty()
            return when {
                id.contains("brass") -> MaterialPalettes.BRASS_PLATE
                id.contains("leather") -> MaterialPalettes.LEATHER
                armorKind == ArmorVisualKind.WYRMSCALE -> MaterialPalettes.WYRMSCALE
                armorKind == ArmorVisualKind.PLATE -> MaterialPalettes.DWARVEN_STEEL
                else -> MaterialPalettes.DEFAULT_TROUSERS
            }
        }

        private fun resolveBoots(bootsId: String?, armorKind: ArmorVisualKind): Pair<BootsVisualKind, MaterialPalette> {
            val id = bootsId?.lowercase()?.trim().orEmpty()
            return when {
                id.contains("plate") || id.contains("steel") || id.contains("brass") ->
                    BootsVisualKind.PLATE to MaterialPalettes.DWARVEN_STEEL
                id.contains("leather") || id.contains("traveler") -> BootsVisualKind.TRAVELER to MaterialPalettes.TRAVELER_BOOTS
                armorKind == ArmorVisualKind.PLATE || armorKind == ArmorVisualKind.WYRMSCALE ->
                    BootsVisualKind.PLATE to MaterialPalettes.DWARVEN_STEEL
                else -> BootsVisualKind.DEFAULT to MaterialPalettes.DEFAULT_BOOTS
            }
        }

        private fun materialPaletteForId(id: String, category: ItemCategory?): MaterialPalette = when {
            id.contains("cinderite") -> MaterialPalettes.CINDERITE
            id.contains("rime_plate") || id.contains("frostpeak") || id.contains("glacier") -> MaterialPalettes.RIME_PLATE
            id.contains("vesperite") -> MaterialPalettes.VESPERITE
            id.contains("mire_woven") || id.contains("bog_steel") -> MaterialPalettes.MIRE_WOVEN
            id.contains("moonforge") || id.contains("starlight") -> MaterialPalettes.MOONFORGE
            id.contains("wyrm") || id.contains("dragon") || id.contains("drake") -> MaterialPalettes.WYRMSCALE_BLADE
            id.contains("cinder") || id.contains("fire") || id.contains("inferno") -> MaterialPalettes.CINDER_FLAME
            id.contains("bile") || id.contains("venom") -> MaterialPalettes.BILE_VENOM
            id.contains("tallow") || id.contains("vortex") -> MaterialPalettes.ARCANE_FOCUS
            id.contains("quenchsteel") -> MaterialPalettes.QUENCHSTEEL
            id.contains("duskmetal") || id.contains("bog") || id.contains("colossus") -> MaterialPalettes.DUSKMETAL
            id.contains("sunbrass") || id.contains("brass") -> MaterialPalettes.SUNBRASS
            id.contains("wood") || id.contains("ashwood") || id.contains("hunting") || id.contains("brindle") -> MaterialPalettes.OAK_WOOD
            category == ItemCategory.WEAPON_WAND -> MaterialPalettes.ARCANE_FOCUS
            else -> MaterialPalettes.IRON
        }

        private fun paletteFromArgb(primaryArgb: Int, highlightArgb: Int, accent: Color): MaterialPalette {
            val p = Color(primaryArgb)
            val h = Color(highlightArgb)
            val s = Color(
                red = (p.red * 0.62f).coerceIn(0f, 1f),
                green = (p.green * 0.62f).coerceIn(0f, 1f),
                blue = (p.blue * 0.62f).coerceIn(0f, 1f),
                alpha = 1f
            )
            return MaterialPalette(primary = p, highlight = h, shadow = s, accent = accent)
        }
    }
}

object MaterialPalettes {
    val OAK_WOOD = MaterialPalette(
        primary = Color(0xFF8B5A2B),
        highlight = Color(0xFFB87F46),
        shadow = Color(0xFF543316),
        accent = Color(0xFFC8A165)
    )
    val LEATHER = MaterialPalette(
        primary = Color(0xFF7D4B28),
        highlight = Color(0xFFA86A3E),
        shadow = Color(0xFF4B2B15),
        accent = Color(0xFFD9A64C)
    )
    val DEFAULT_TROUSERS = MaterialPalette(
        primary = Color(0xFF4C3C30),
        highlight = Color(0xFF6B5647),
        shadow = Color(0xFF2D231B),
        accent = Color(0xFF8A725E)
    )
    val DEFAULT_BOOTS = MaterialPalette(
        primary = Color(0xFF432A19),
        highlight = Color(0xFF644129),
        shadow = Color(0xFF24150C),
        accent = Color(0xFF9C7A47)
    )
    val TRAVELER_BOOTS = MaterialPalette(
        primary = Color(0xFF5D3A21),
        highlight = Color(0xFF875835),
        shadow = Color(0xFF331E10),
        accent = Color(0xFFE0B45C)
    )
    val IRON = MaterialPalette(
        primary = Color(0xFFAAB4C2),
        highlight = Color(0xFFE0E8F2),
        shadow = Color(0xFF636E7D),
        accent = Color(0xFFD9A448)
    )
    val SUNBRASS = MaterialPalette(
        primary = Color(0xFFD29B44),
        highlight = Color(0xFFF6CC78),
        shadow = Color(0xFF8A5F1D),
        accent = Color(0xFFFFE59E),
        glow = Color(0xFFFFCF66)
    )
    val DUSKMETAL = MaterialPalette(
        primary = Color(0xFF5C6678),
        highlight = Color(0xFF8B99B0),
        shadow = Color(0xFF343B47),
        accent = Color(0xFFA8B5CC)
    )
    val QUENCHSTEEL = MaterialPalette(
        primary = Color(0xFF7D93B2),
        highlight = Color(0xFFC5DCFA),
        shadow = Color(0xFF43536B),
        accent = Color(0xFFFFB74D),
        glow = Color(0xFF84C6FF)
    )
    val MOONFORGE = MaterialPalette(
        primary = Color(0xFFBBD7F5),
        highlight = Color(0xFFF0F8FF),
        shadow = Color(0xFF6C8BB3),
        accent = Color(0xFF73F0FF),
        glow = Color(0xFF68E8FF)
    )
    /** Whispering Marsh Mire-Woven / Bog-Steel: mossy swamp-silver alloy with venomous emerald glow. */
    val MIRE_WOVEN = MaterialPalette(
        primary = Color(0xFF426B54),
        highlight = Color(0xFF8BE8B5),
        shadow = Color(0xFF1F382B),
        accent = Color(0xFF66F27E),
        glow = Color(0xFF4AE86E)
    )
    /** Tier 4 Vesperite: violet-silver crystalline forged metal with luminous amethyst sheen. */
    val VESPERITE = MaterialPalette(
        primary = Color(0xFF9C84E8),
        highlight = Color(0xFFEBE2FF),
        shadow = Color(0xFF47377A),
        accent = Color(0xFFD6C2FF),
        glow = Color(0xFFB58CFF)
    )
    /** Frostpeak Highlands Rime-Plate: glacial frost-iron steel inlaid with crystalline cyan quartz. */
    val RIME_PLATE = MaterialPalette(
        primary = Color(0xFF7CAFD4),
        highlight = Color(0xFFE0F8FF),
        shadow = Color(0xFF335573),
        accent = Color(0xFF6CE6FF),
        glow = Color(0xFF5AD8FF)
    )
    /** Tier 5 Cinderite: obsidian-forged metal inlaid with glowing volcanic-gold fissures. */
    val CINDERITE = MaterialPalette(
        primary = Color(0xFF3E2D36),
        highlight = Color(0xFFFFC95C),
        shadow = Color(0xFF1A1217),
        accent = Color(0xFFFF9E2B),
        glow = Color(0xFFFF7A1F)
    )
    val BRASS_PLATE = MaterialPalette(
        primary = Color(0xFFB58A3C),
        highlight = Color(0xFFE5BD6A),
        shadow = Color(0xFF6F511D),
        accent = Color(0xFFF6D98B)
    )
    val DWARVEN_STEEL = MaterialPalette(
        primary = Color(0xFF7A8699),
        highlight = Color(0xFFB9C6D9),
        shadow = Color(0xFF47505E),
        accent = Color(0xFFE2B454)
    )
    val WYRMSCALE = MaterialPalette(
        primary = Color(0xFF287D52),
        highlight = Color(0xFF49B87D),
        shadow = Color(0xFF14472D),
        accent = Color(0xFFEEC55D),
        glow = Color(0xFF5DF09E)
    )
    val WYRMSCALE_BLADE = MaterialPalette(
        primary = Color(0xFFD65E38),
        highlight = Color(0xFFFFA06A),
        shadow = Color(0xFF7A2814),
        accent = Color(0xFF49B87D),
        glow = Color(0xFFFF7A3B)
    )
    val CINDER_FLAME = MaterialPalette(
        primary = Color(0xFFD94A27),
        highlight = Color(0xFFFF9852),
        shadow = Color(0xFF781F0E),
        accent = Color(0xFFFFDF73),
        glow = Color(0xFFFF6A24)
    )
    val BILE_VENOM = MaterialPalette(
        primary = Color(0xFF4F8A3B),
        highlight = Color(0xFF88D45D),
        shadow = Color(0xFF2A4F1E),
        accent = Color(0xFFAEFF6E),
        glow = Color(0xFF78F050)
    )
    val ARCANE_FOCUS = MaterialPalette(
        primary = Color(0xFF5F49B8),
        highlight = Color(0xFF9C82FA),
        shadow = Color(0xFF342470),
        accent = Color(0xFF6CF0FF),
        glow = Color(0xFF6CE8FF)
    )
    val KNIGHT_SURCOAT = MaterialPalette(
        primary = Color(0xFF8C2A2A),
        highlight = Color(0xFFC24444),
        shadow = Color(0xFF521616),
        accent = Color(0xFFE5BC5C)
    )
    val PALADIN_CLOAK = MaterialPalette(
        primary = Color(0xFF2B6148),
        highlight = Color(0xFF45946F),
        shadow = Color(0xFF173828),
        accent = Color(0xFFE5BC5C)
    )
    val MAGE_ROBE = MaterialPalette(
        primary = Color(0xFF3B3587),
        highlight = Color(0xFF6059BD),
        shadow = Color(0xFF211C52),
        accent = Color(0xFFE6C25A),
        glow = Color(0xFF7B9AFF)
    )
}
