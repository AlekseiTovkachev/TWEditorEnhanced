package app.tweditor

/** Database labels displayed by the Compose Hero ability editor. */
object HeroAbilityLabels {
    val attributes: List<String> = listOf("Strength", "Dexterity", "Endurance", "Intelligence").flatMap { group ->
        buildList {
            addAll((1..5).map { "$group$it" })
            addAll((1..5).map { "$group$it Upgrade1" })
            addAll((1..5).map { "$group$it Upgrade2" })
            addAll((2..4).map { "$group$it Upgrade3" })
        }
    }

    val signs: List<String> = listOf("Aard", "Igni", "Quen", "Axi", "Yrden").flatMap { group ->
        buildList {
            addAll((1..5).map { "$group$it" })
            addAll((1..5).map { "$group$it Powerup" })
            addAll((1..5).map { "$group$it Upgrade1" })
            addAll((2..4).map { "$group$it Upgrade2" })
        }
    }

    val combatStyles: List<String> = listOf(
        "StyleSteelStrong", "StyleSteelFast", "StyleSteelGroup",
        "StyleSilverStrong", "StyleSilverFast", "StyleSilverGroup"
    ).flatMap { group ->
        buildList {
            addAll((1..5).map { "$group$it" })
            addAll((1..3).map { "$group$it Upgrade1" })
            addAll((1..3).map { "$group$it Upgrade2" })
            addAll((1..3).map { "$group$it Upgrade3" })
        }
    }
}
