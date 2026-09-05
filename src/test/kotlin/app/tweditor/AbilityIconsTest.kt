package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AbilityIconsTest {

    @Test
    fun mapsSignAbilityLabelsToUiTextures() {
        assertEquals("ui_ab_aar1", AbilityIcons.iconResref("Aard1"))
        assertEquals("ui_ab_aar5", AbilityIcons.iconResref("Aard5"))
        assertEquals("ui_ab_ign3", AbilityIcons.iconResref("Igni3"))
        assertEquals("ui_ab_que4", AbilityIcons.iconResref("Quen4"))
        assertEquals("ui_ab_axi2", AbilityIcons.iconResref("Axii2"))
        assertEquals("ui_ab_yrd5", AbilityIcons.iconResref("Yrden5"))
    }

    @Test
    fun mapsSignPowerupsAndUpgrades() {
        assertEquals("ui_ab_aar1p", AbilityIcons.iconResref("Aard1 Powerup"))
        assertEquals("ui_ab_aar5p", AbilityIcons.iconResref("Aard5 Powerup"))
        assertEquals("ui_ab_que4p", AbilityIcons.iconResref("Quen4 Powerup"))
        assertEquals("ui_ab_aar1u1", AbilityIcons.iconResref("Aard1 Upgrade1"))
        assertEquals("ui_ab_aar2u2", AbilityIcons.iconResref("Aard2 Upgrade2"))
        assertEquals("ui_ab_yrd5u1", AbilityIcons.iconResref("Yrden5 Upgrade1"))
        assertEquals("ui_ab_axi2u2", AbilityIcons.iconResref("Axii2 Upgrade2"))
    }

    @Test
    fun mapsStyleAbilityLabelsToUiTextures() {
        assertEquals("ui_ab_sts1", AbilityIcons.iconResref("StyleSteelStrong1"))
        assertEquals("ui_ab_sts5", AbilityIcons.iconResref("StyleSteelStrong5"))
        assertEquals("ui_ab_stf2", AbilityIcons.iconResref("StyleSteelFast2"))
        assertEquals("ui_ab_stg3", AbilityIcons.iconResref("StyleSteelGroup3"))
        assertEquals("ui_ab_svs4", AbilityIcons.iconResref("StyleSilverStrong4"))
        assertEquals("ui_ab_svf1", AbilityIcons.iconResref("StyleSilverFast1"))
        assertEquals("ui_ab_svg5", AbilityIcons.iconResref("StyleSilverGroup5"))
    }

    @Test
    fun mapsStyleUpgrades() {
        assertEquals("ui_ab_sts1u1", AbilityIcons.iconResref("StyleSteelStrong1 Upgrade1"))
        assertEquals("ui_ab_sts2u3", AbilityIcons.iconResref("StyleSteelStrong2 Upgrade3"))
        assertEquals("ui_ab_stf2u2", AbilityIcons.iconResref("StyleSteelFast2 Upgrade2"))
        assertEquals("ui_ab_svg3u3", AbilityIcons.iconResref("StyleSilverGroup3 Upgrade3"))
        assertEquals("ui_ab_svs1u1", AbilityIcons.iconResref("StyleSilverStrong1 Upgrade1"))
    }

    @Test
    fun rejectsLabelsOutsideTheAbilityVocabulary() {
        assertNull(AbilityIcons.iconResref(""))
        assertNull(AbilityIcons.iconResref("Aard"))
        assertNull(AbilityIcons.iconResref("Aard12"))
        assertNull(AbilityIcons.iconResref("Aard1 Upgrades"))
        assertNull(AbilityIcons.iconResref("StyleSteelStrong"))
        assertNull(AbilityIcons.iconResref(" meteorite_red1_self "))
    }
}
