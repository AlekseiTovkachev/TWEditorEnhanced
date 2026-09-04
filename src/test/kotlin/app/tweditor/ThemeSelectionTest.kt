package app.tweditor

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import javax.swing.UIManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ThemeSelectionTest {

    @Test
    fun darkPreferencePicksTheDarkTheme() {
        assertTrue(ThemeSelection.lookAndFeel(ThemeSelection.Preference.DARK) is FlatDarkLaf)
    }

    @Test
    fun lightAndUnknownPreferencesPickTheLightTheme() {
        assertTrue(ThemeSelection.lookAndFeel(ThemeSelection.Preference.LIGHT) is FlatLightLaf)
        assertTrue(ThemeSelection.lookAndFeel(ThemeSelection.Preference.UNKNOWN) is FlatLightLaf)
    }

    @Test
    fun windowsRegistryLightValueParsesToLight() {
        assertEquals(ThemeSelection.Preference.LIGHT,
            ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_DWORD    0x1"))
    }

    @Test
    fun windowsRegistryDarkValueParsesToDark() {
        assertEquals(ThemeSelection.Preference.DARK,
            ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_DWORD    0x0"))
    }

    @Test
    fun windowsRegistryBareValueParsesWithoutPrefix() {
        assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromWindowsRegistryValue("0x0"))
        assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromWindowsRegistryValue("0x1"))
    }

    @Test
    fun garbageWindowsRegistryValueIsUnknown() {
        assertEquals(ThemeSelection.Preference.UNKNOWN,
            ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_SZ    yes"))
        assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromWindowsRegistryValue(""))
    }

    @Test
    fun macDefaultsMissingKeyMeansLight() {
        assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromMacDefaultsOutput(null, 1))
        assertEquals(ThemeSelection.Preference.LIGHT,
            ThemeSelection.fromMacDefaultsOutput("The domain/default pair of (kCFPreferencesAnyApplication, AppleInterfaceStyle) does not exist", 1))
    }

    @Test
    fun macDefaultsDarkMeansDark() {
        assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromMacDefaultsOutput("Dark", 0))
    }

    @Test
    fun gsettingsPreferDarkMeansDark() {
        assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromGsettingsOutput("'prefer-dark'"))
        assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromGsettingsOutput("prefer-dark"))
    }

    @Test
    fun gsettingsLightSchemesMeansLight() {
        assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromGsettingsOutput("'default'"))
        assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromGsettingsOutput("'prefer-light'"))
    }

    @Test
    fun gsettingsMissingOutputIsUnknown() {
        assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromGsettingsOutput(null))
        assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromGsettingsOutput("'something-else'"))
    }

    @Test
    fun globalDefaultsCarryTheAccentColor() {
        assertEquals("#B45309", ThemeSelection.globalExtraDefaults()["@accentColor"])
        assertEquals(1, ThemeSelection.globalExtraDefaults().size)
    }

    @Test
    fun installAppliesTheAccentAndAFollowOsTheme() {
        ThemeSelection.install()
        assertEquals("#B45309", FlatLaf.getGlobalExtraDefaults()["@accentColor"])
        val lafClass = UIManager.getLookAndFeel().javaClass.getName()
        assertTrue(lafClass == "com.formdev.flatlaf.FlatLightLaf" || lafClass == "com.formdev.flatlaf.FlatDarkLaf")
    }
}
