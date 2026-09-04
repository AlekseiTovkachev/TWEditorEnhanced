package app.tweditor;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Scanner;
import javax.swing.LookAndFeel;

/**
 * Decides which FlatLaf theme to use from the OS light/dark preference.
 * The decision (preference to look-and-feel) is a pure function; the
 * per-platform detection only extracts the preference.
 */
final class ThemeSelection {

  enum Preference {
    LIGHT, DARK, UNKNOWN
  }

  private ThemeSelection() {
  }

  static LookAndFeel lookAndFeel(Preference preference) {
    return preference == Preference.DARK ? new FlatDarkLaf() : new FlatLightLaf();
  }

  static LookAndFeel lookAndFeelForOs() {
    return lookAndFeel(detectOsPreference());
  }

  static Preference detectOsPreference() {
    String os = System.getProperty("os.name", "").toLowerCase();
    try {
      if (os.startsWith("windows")) {
        return windowsAppsUseLightTheme();
      } else if (os.startsWith("mac")) {
        return macInterfaceStyle();
      } else if (os.startsWith("linux")) {
        return linuxColorScheme();
      }
    } catch (Exception exc) {
      // fall through: unknown preference means the light default
    }
    return Preference.UNKNOWN;
  }

  private static Preference windowsAppsUseLightTheme() throws Exception {
    Process process = new ProcessBuilder("reg", "query",
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "/v", "AppsUseLightTheme").start();
    process.waitFor();
    Scanner scanner = new Scanner(process.getInputStream());
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      if (line.contains("AppsUseLightTheme")) {
        return fromWindowsRegistryValue(line);
      }
    }
    return Preference.UNKNOWN;
  }

  static Preference fromWindowsRegistryValue(String line) {
    try (Scanner tokens = new Scanner(line.trim())) {
      tokens.skip("\\s*(?:AppsUseLightTheme\\s+REG_DWORD\\s+)?");
      int value = Integer.decode(tokens.next());
      return value == 0 ? Preference.DARK : Preference.LIGHT;
    } catch (NoSuchElementException | NumberFormatException exc) {
      return Preference.UNKNOWN;
    }
  }

  private static Preference macInterfaceStyle() throws Exception {
    Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
    int exitCode = process.waitFor();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      return fromMacDefaultsOutput(reader.readLine(), exitCode);
    }
  }

  static Preference fromMacDefaultsOutput(String line, int exitCode) {
    if (exitCode != 0) {
      return Preference.LIGHT;
    }
    return "Dark".equalsIgnoreCase(line == null ? "" : line.trim()) ? Preference.DARK : Preference.UNKNOWN;
  }

  private static Preference linuxColorScheme() throws Exception {
    Process process = new ProcessBuilder("gsettings", "get", "org.freedesktop.appearance", "color-scheme").start();
    process.waitFor();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      return fromGsettingsOutput(reader.readLine());
    }
  }

  static Preference fromGsettingsOutput(String line) {
    if (line == null) {
      return Preference.UNKNOWN;
    }
    String value = line.trim().replace("'", "");
    if (value.equals("prefer-dark")) {
      return Preference.DARK;
    } else if (value.equals("prefer-light") || value.equals("default")) {
      return Preference.LIGHT;
    }
    return Preference.UNKNOWN;
  }
}
