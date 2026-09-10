package app.tweditor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ThemeSelectionTest {
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

}
