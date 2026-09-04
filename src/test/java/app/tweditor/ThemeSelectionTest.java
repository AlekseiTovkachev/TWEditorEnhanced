package app.tweditor;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ThemeSelectionTest {

  @Test
  void darkPreferencePicksTheDarkTheme() {
    assertInstanceOf(FlatDarkLaf.class, ThemeSelection.lookAndFeel(ThemeSelection.Preference.DARK));
  }

  @Test
  void lightAndUnknownPreferencesPickTheLightTheme() {
    assertInstanceOf(FlatLightLaf.class, ThemeSelection.lookAndFeel(ThemeSelection.Preference.LIGHT));
    assertInstanceOf(FlatLightLaf.class, ThemeSelection.lookAndFeel(ThemeSelection.Preference.UNKNOWN));
  }

  @Test
  void windowsRegistryLightValueParsesToLight() {
    assertEquals(ThemeSelection.Preference.LIGHT,
        ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_DWORD    0x1"));
  }

  @Test
  void windowsRegistryDarkValueParsesToDark() {
    assertEquals(ThemeSelection.Preference.DARK,
        ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_DWORD    0x0"));
  }

  @Test
  void windowsRegistryBareValueParsesWithoutPrefix() {
    assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromWindowsRegistryValue("0x0"));
    assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromWindowsRegistryValue("0x1"));
  }

  @Test
  void garbageWindowsRegistryValueIsUnknown() {
    assertEquals(ThemeSelection.Preference.UNKNOWN,
        ThemeSelection.fromWindowsRegistryValue("    AppsUseLightTheme    REG_SZ    yes"));
    assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromWindowsRegistryValue(""));
  }

  @Test
  void macDefaultsMissingKeyMeansLight() {
    assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromMacDefaultsOutput(null, 1));
    assertEquals(ThemeSelection.Preference.LIGHT,
        ThemeSelection.fromMacDefaultsOutput("The domain/default pair of (kCFPreferencesAnyApplication, AppleInterfaceStyle) does not exist", 1));
  }

  @Test
  void macDefaultsDarkMeansDark() {
    assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromMacDefaultsOutput("Dark", 0));
  }

  @Test
  void gsettingsPreferDarkMeansDark() {
    assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromGsettingsOutput("'prefer-dark'"));
    assertEquals(ThemeSelection.Preference.DARK, ThemeSelection.fromGsettingsOutput("prefer-dark"));
  }

  @Test
  void gsettingsLightSchemesMeansLight() {
    assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromGsettingsOutput("'default'"));
    assertEquals(ThemeSelection.Preference.LIGHT, ThemeSelection.fromGsettingsOutput("'prefer-light'"));
  }

  @Test
  void gsettingsMissingOutputIsUnknown() {
    assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromGsettingsOutput(null));
    assertEquals(ThemeSelection.Preference.UNKNOWN, ThemeSelection.fromGsettingsOutput("'something-else'"));
  }
}
