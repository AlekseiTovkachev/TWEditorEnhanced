package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The curated talent-name table must cover every label the Hero panels offer;
 * without this, a row silently falls back to the synthetic label.
 */
class AbilityNamesTest {
    @Test
    fun coversEveryCatalogLabel() {
        val catalog = HeroAbilityLabels.attributes + HeroAbilityLabels.signs + HeroAbilityLabels.combatStyles
        assertTrue(catalog.size >= 240, "expected the full ability grid, got " + catalog.size)
        val missing = catalog.filter { AbilityNames.strref(it) == null }
        assertEquals(emptyList<String>(), missing, "labels missing from the curated name table")
    }

    @Test
    fun levelHeadingsKeepTheLevelAnnotation() {
        assertEquals("Strength (level 2)", AbilityNames.readable("STRENGTH (level 2)"))
        assertEquals("Strength (level 1)", AbilityNames.readable("STRENGTH (LEVEL 1)"))
    }

    @Test
    fun upgradeHeadingsTitleCase() {
        assertEquals("Buzz", AbilityNames.readable("BUZZ"))
        assertEquals("Cut at the Jugular II", AbilityNames.readable("CUT AT THE JUGULAR II"))
        assertEquals("Knowledge of the Cleansing Ritual", AbilityNames.readable("KNOWLEDGE OF THE CLEANSING RITUAL"))
        assertEquals("Mental Endurance", AbilityNames.readable("MENTAL ENDURANCE"))
        assertEquals("Harm's Way I", AbilityNames.readable("HARM'S WAY I"))
    }

    @Test
    fun unknownLabelsFallBackToSyntheticOnes() {
        assertEquals("Strength 2 · Upgrade 1", AbilityNames.fallbackLabel("Strength2 Upgrade1"))
        assertEquals("Aard 2 · Powerup", AbilityNames.fallbackLabel("Aard2 Powerup"))
        assertEquals("Style Steel strong 3 · Upgrade 2", AbilityNames.fallbackLabel("StyleSteelStrong3 Upgrade2"))
        assertEquals("meteorite_red1_self", AbilityNames.fallbackLabel("meteorite_red1_self"))
    }
}
