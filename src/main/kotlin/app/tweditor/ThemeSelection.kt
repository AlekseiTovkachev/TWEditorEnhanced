package app.tweditor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.NoSuchElementException
import java.util.Scanner

/**
 * Reads the operating-system theme preference for the Compose color scheme.
 * Presentation is owned by Compose; this object deliberately has no
 * look-and-feel or toolkit installation side effects.
 */
object ThemeSelection {
    enum class Preference {
        LIGHT, DARK, UNKNOWN
    }

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
