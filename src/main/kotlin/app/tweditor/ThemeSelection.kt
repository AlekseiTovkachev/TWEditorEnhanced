package app.tweditor

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.NoSuchElementException
import java.util.Scanner
import javax.swing.LookAndFeel
import javax.swing.UIManager

object ThemeSelection {
    const val ACCENT_COLOR = "#B45309"

    enum class Preference {
        LIGHT, DARK, UNKNOWN
    }

    fun globalExtraDefaults(): Map<String, String> = mapOf("@accentColor" to ACCENT_COLOR)

    fun install() {
        install(detectOsPreference())
    }

    fun install(preference: Preference) {
        FlatLaf.setGlobalExtraDefaults(globalExtraDefaults())
        try {
            UIManager.setLookAndFeel(lookAndFeel(preference))
        } catch (exc: Exception) {
            throw RuntimeException("Unable to install the FlatLaf look-and-feel", exc)
        }
    }

    fun lookAndFeel(preference: Preference): LookAndFeel {
        return if (preference == Preference.DARK) FlatDarkLaf() else FlatLightLaf()
    }

    fun lookAndFeelForOs(): LookAndFeel = lookAndFeel(detectOsPreference())

    fun detectOsPreference(): Preference {
        val os = System.getProperty("os.name", "").lowercase()
        return try {
            when {
                os.startsWith("windows") -> windowsAppsUseLightTheme()
                os.startsWith("mac") -> macInterfaceStyle()
                os.startsWith("linux") -> linuxColorScheme()
                else -> Preference.UNKNOWN
            }
        } catch (exc: Exception) {
            Preference.UNKNOWN
        }
    }

    private fun windowsAppsUseLightTheme(): Preference {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme"
        ).start()
        process.waitFor()
        Scanner(process.inputStream).use { scanner ->
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                if (line.contains("AppsUseLightTheme")) {
                    return fromWindowsRegistryValue(line)
                }
            }
        }
        return Preference.UNKNOWN
    }

    fun fromWindowsRegistryValue(line: String): Preference {
        return try {
            Scanner(line.trim()).use { tokens ->
                tokens.skip("\\s*(?:AppsUseLightTheme\\s+REG_DWORD\\s+)?")
                val value = Integer.decode(tokens.next())
                if (value == 0) Preference.DARK else Preference.LIGHT
            }
        } catch (exc: NoSuchElementException) {
            Preference.UNKNOWN
        } catch (exc: NumberFormatException) {
            Preference.UNKNOWN
        }
    }

    private fun macInterfaceStyle(): Preference {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start()
        val exitCode = process.waitFor()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            return fromMacDefaultsOutput(reader.readLine(), exitCode)
        }
    }

    fun fromMacDefaultsOutput(line: String?, exitCode: Int): Preference {
        if (exitCode != 0) {
            return Preference.LIGHT
        }
        return if ("Dark".equals(line?.trim() ?: "", ignoreCase = true)) Preference.DARK else Preference.UNKNOWN
    }

    private fun linuxColorScheme(): Preference {
        val process = ProcessBuilder("gsettings", "get", "org.freedesktop.appearance", "color-scheme").start()
        process.waitFor()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            return fromGsettingsOutput(reader.readLine())
        }
    }

    fun fromGsettingsOutput(line: String?): Preference {
        if (line == null) {
            return Preference.UNKNOWN
        }
        return when (line.trim().replace("'", "")) {
            "prefer-dark" -> Preference.DARK
            "prefer-light", "default" -> Preference.LIGHT
            else -> Preference.UNKNOWN
        }
    }
}
