package app.tweditor

/**
 * Database ability labels ("Strength2 Upgrade1", "Aard2 Upgrade1",
 * "StyleSteelGroup3 Upgrade2") -> base-game TLK strrefs that carry the talent
 * tooltips the in-game character screen shows. The tooltip text opens with a
 * `<cHEADER>` heading holding the node's display name ("Position", "Buzz",
 * "Cut at the Jugular II", "STRENGTH (level 1)"); the strrefs are
 * language-independent, so the names localize through the install's strings
 * database. Labels without a table entry fall back to the synthetic label.
 *
 * Every mapping was verified against the decompiled ability definitions
 * (`witcher_atr_abl.luc` / `witcher_sgn_abl.luc` / `witcher_cs_abl.luc`) and
 * the real English TLK: the heading text matches the ability's own effects.
 */
object AbilityNames {
    private const val STYLE_TAB = 1713

    /** The curated TLK reference for a database label, or null when not tabled. */
    internal fun strref(databaseLabel: String): Int? = TalentNameRefs.strref(databaseLabel)

    fun displayName(environment: AppEnvironment, databaseLabel: String): String {
        val strref = TalentNameRefs.strref(databaseLabel) ?: return fallbackLabel(databaseLabel)
        val heading = heading(environment, strref)
        if (heading.isEmpty()) return fallbackLabel(databaseLabel)
        return readable(heading)
    }

    /** The character-screen tab name for an ability family group. */
    fun groupName(environment: AppEnvironment, databaseLabel: String): String {
        val prefix = databaseLabel.takeWhile { !it.isDigit() }
        val strref = when (prefix) {
            "StyleSteelStrong" -> STYLE_TAB
            "StyleSteelFast" -> STYLE_TAB + 1
            "StyleSteelGroup" -> STYLE_TAB + 2
            "StyleSilverStrong" -> STYLE_TAB + 3
            "StyleSilverFast" -> STYLE_TAB + 4
            "StyleSilverGroup" -> STYLE_TAB + 5
            else -> return prefix
        }
        val heading = heading(environment, strref)
        return if (heading.isEmpty()) syntheticGroup(prefix) else readable(heading)
    }

    private fun heading(environment: AppEnvironment, strref: Int): String {
        val database = environment.stringsDatabase ?: return ""
        return runCatching { database.getHeading(strref).trim() }.getOrDefault("")
    }

    /** "Strength (level 2)" keeps its level annotation; other headings title-case. */
    internal fun readable(heading: String): String {
        val levelMatch = Regex("^(.*) [(]level (\\d+)[)]$", RegexOption.IGNORE_CASE).find(heading.trim())
        if (levelMatch != null) {
            return titleWords(levelMatch.groupValues[1]) + " (level " + levelMatch.groupValues[2] + ")"
        }
        return titleWords(heading)
    }

    private fun titleWords(text: String): String {
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        return words.mapIndexed { index, word ->
            val lowered = word.lowercase()
            when {
                lowered in ROMAN_NUMERALS -> lowered.uppercase()
                index > 0 && lowered in SMALL_WORDS -> lowered
                else -> lowered.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    /** The synthetic label the editor showed before the game's names were wired in. */
    fun fallbackLabel(databaseLabel: String): String {
        val style = Regex("Style(Steel|Silver)(Strong|Fast|Group)(\\d+)(?: Upgrade(\\d+))?$").find(databaseLabel)
        val core = Regex("^([A-Za-z]+)(\\d+)(.*)$").find(databaseLabel)
        return when {
            style != null -> {
                val upgrade = style.groupValues[4]
                val tail = if (upgrade.isEmpty()) "" else " · Upgrade $upgrade"
                "Style ${style.groupValues[1]} ${style.groupValues[2].lowercase()} ${style.groupValues[3]}$tail"
            }
            core == null -> databaseLabel
            else -> {
                val name = core.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
                val suffix = core.groupValues[3]
                val tail = when {
                    suffix.startsWith(" Powerup") -> " · Powerup"
                    suffix.startsWith(" Upgrade") -> " · Upgrade " + suffix.removePrefix(" Upgrade")
                    else -> ""
                }
                "$name ${core.groupValues[2]}$tail"
            }
        }
    }

    private fun syntheticGroup(prefix: String): String = when {
        prefix.startsWith("StyleSteel") -> prefix.removePrefix("Style") + " · Steel"
        prefix.startsWith("StyleSilver") -> prefix.removePrefix("Style") + " · Silver"
        else -> prefix
    }

    private val ROMAN_NUMERALS = setOf("i", "ii", "iii", "iv", "v")
    private val SMALL_WORDS = setOf("at", "the", "of", "a", "an", "and")
}

/**
 * The curated table. Each sign has 5 level tooltips followed by per-level
 * Powerup/Upgrade tooltips; each style has 5 level tooltips then 9 upgrade
 * tooltips. The TLK scatters some trees (Dexterity, Stamina, Group Steel,
 * Strong Silver) around unrelated strings, so those use explicit references;
 * every entry was validated against the ability's own decompiled effects.
 */
private object TalentNameRefs {
    private val refs: Map<String, Int> = buildMap {
        put("Strength1", 1243); put("Strength2", 1244); put("Strength3", 1245); put("Strength4", 1246); put("Strength5", 1277)
        put("Strength1 Upgrade1", 1278); put("Strength1 Upgrade2", 1279)
        put("Strength2 Upgrade1", 1280); put("Strength2 Upgrade2", 1281); put("Strength2 Upgrade3", 1282)
        put("Strength3 Upgrade1", 1283); put("Strength3 Upgrade2", 1284); put("Strength3 Upgrade3", 1285)
        put("Strength4 Upgrade1", 1286); put("Strength4 Upgrade2", 1287); put("Strength4 Upgrade3", 1288)
        put("Strength5 Upgrade1", 1289); put("Strength5 Upgrade2", 1290)

        put("Dexterity1", 1291); put("Dexterity2", 1292); put("Dexterity3", 1293); put("Dexterity4", 1294); put("Dexterity5", 1295)
        put("Dexterity1 Upgrade1", 2077); put("Dexterity1 Upgrade2", 1247)
        put("Dexterity2 Upgrade1", 1297); put("Dexterity2 Upgrade2", 1298); put("Dexterity2 Upgrade3", 1501)
        put("Dexterity3 Upgrade1", 1502); put("Dexterity3 Upgrade2", 1503); put("Dexterity3 Upgrade3", 1504)
        put("Dexterity4 Upgrade1", 1303); put("Dexterity4 Upgrade2", 1304); put("Dexterity4 Upgrade3", 1305)
        put("Dexterity5 Upgrade1", 1306); put("Dexterity5 Upgrade2", 1307)

        put("Endurance1", 1308); put("Endurance2", 1309); put("Endurance3", 1310); put("Endurance4", 1311); put("Endurance5", 1312)
        put("Endurance1 Upgrade1", 1428); put("Endurance1 Upgrade2", 1429)
        put("Endurance2 Upgrade1", 1315); put("Endurance2 Upgrade2", 1316); put("Endurance2 Upgrade3", 1317)
        put("Endurance3 Upgrade1", 1318); put("Endurance3 Upgrade2", 1319); put("Endurance3 Upgrade3", 1320)
        put("Endurance4 Upgrade1", 1321); put("Endurance4 Upgrade2", 1322); put("Endurance4 Upgrade3", 1323)
        put("Endurance5 Upgrade1", 1324); put("Endurance5 Upgrade2", 1325)

        put("Intelligence1", 1326); put("Intelligence2", 1327); put("Intelligence3", 1328); put("Intelligence4", 1329); put("Intelligence5", 1330)
        put("Intelligence1 Upgrade1", 1331); put("Intelligence1 Upgrade2", 1332)
        put("Intelligence2 Upgrade1", 1333); put("Intelligence2 Upgrade2", 1334); put("Intelligence2 Upgrade3", 1335)
        put("Intelligence3 Upgrade1", 1336); put("Intelligence3 Upgrade2", 1337); put("Intelligence3 Upgrade3", 1338)
        put("Intelligence4 Upgrade1", 1505); put("Intelligence4 Upgrade2", 1340); put("Intelligence4 Upgrade3", 1341)
        put("Intelligence5 Upgrade1", 1342); put("Intelligence5 Upgrade2", 1343)

        signTree("Aard", 1506)
        // the save's RnAbName labels spell the sign "Axi" (the character screen says "AXII")
        signTree("Axi", 1524)
        signTree("Igni", 1542)
        signTree("Quen", 1560)
        signTree("Yrden", 1578)

        styleTree("StyleSteelStrong", 1344)
        styleTree("StyleSteelFast", 1358)
        styleTree("StyleSteelGroup", 1372, upgrades = listOf(1377, 1378, 1379, 1439, 1440, 1441, 1442, 1443, 1444))
        styleTree("StyleSilverStrong", 1430, upgrades = listOf(1435, 1436, 1437, 1438, 1395, 1396, 1397, 1398, 1399))
        styleTree("StyleSilverFast", 1400)
        styleTree("StyleSilverGroup", 1414)
    }

    private fun MutableMap<String, Int>.signTree(prefix: String, levelFirst: Int) {
        for (level in 1..5) put("$prefix$level", levelFirst + level - 1)
        // 13 upgrade tooltips: level 1 (Powerup, Upgrade1), levels 2-4 (Powerup,
        // Upgrade1, Upgrade2), level 5 (Powerup, Upgrade1), contiguous.
        val upgradeFirst = levelFirst + 5
        put("${prefix}1 Powerup", upgradeFirst)
        put("${prefix}1 Upgrade1", upgradeFirst + 1)
        for (level in 2..4) {
            val base = upgradeFirst + 2 + (level - 2) * 3
            put("$prefix$level Powerup", base)
            put("$prefix$level Upgrade1", base + 1)
            put("$prefix$level Upgrade2", base + 2)
        }
        put("${prefix}5 Powerup", upgradeFirst + 11)
        put("${prefix}5 Upgrade1", upgradeFirst + 12)
    }

    private fun MutableMap<String, Int>.styleTree(
        prefix: String,
        levelFirst: Int,
        upgrades: List<Int> = emptyList()
    ) {
        for (level in 1..5) put("$prefix$level", levelFirst + level - 1)
        val list = upgrades.ifEmpty {
            // contiguous: level 1..3 × (Upgrade1, Upgrade2, Upgrade3)
            (0 until 9).map { levelFirst + 5 + it }
        }
        list.forEachIndexed { index, ref ->
            val level = index / 3 + 1
            val upgrade = index % 3 + 1
            put("$prefix$level Upgrade$upgrade", ref)
        }
    }

    fun strref(databaseLabel: String): Int? = refs[databaseLabel]
}
