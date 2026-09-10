package app.tweditor

enum class AbilityFamily(val title: String, val messageKey: String) {
    ATTRIBUTES("Attributes", "hero.family.attributes"),
    SIGNS("Signs", "hero.family.signs"),
    COMBAT_STYLES("Combat Styles", "hero.family.combatStyles")
}

data class AbilityChoice(
    val family: AbilityFamily,
    val databaseLabel: String
) {
    private val style = Regex("Style(Steel|Silver)(Strong|Fast|Group)(\\d+)$").find(databaseLabel)
    private val core = Regex("([A-Za-z]+)(\\d+)(.*)$").find(databaseLabel)

    val displayLabel: String
        get() = when {
            style != null ->
                "Style ${style.groupValues[1]} ${style.groupValues[2].lowercase()} ${style.groupValues[3]}"
            core == null -> databaseLabel
            else -> {
                val name = core.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
                val tail = when {
                    core.groupValues[3].startsWith(" Powerup") -> " · Powerup"
                    core.groupValues[3].startsWith(" Upgrade") -> " · Upgrade " + core.groupValues[3].removePrefix(" Upgrade")
                    else -> ""
                }
                "$name ${core.groupValues[2]}$tail"
            }
        }
}

data class AbilityGroup(val key: String, val messageKey: String)

enum class TalentTier(val title: String, val messageKey: String) {
    BRONZE("Bronze", "hero.tier.bronze"),
    SILVER("Silver", "hero.tier.silver"),
    GOLD("Gold", "hero.tier.gold")
}

data class AbilityTreeLevel(
    val level: Int,
    val tier: TalentTier,
    val abilities: List<AbilityChoice>
)

object HeroAbilityCatalog {
    val attributes: List<AbilityChoice> by lazy {
        HeroAbilityLabels.attributes.map { AbilityChoice(AbilityFamily.ATTRIBUTES, it) }
    }
    val signs: List<AbilityChoice> by lazy {
        HeroAbilityLabels.signs.map { AbilityChoice(AbilityFamily.SIGNS, it) }
    }
    val combatStyles: List<AbilityChoice> by lazy {
        HeroAbilityLabels.combatStyles.map { AbilityChoice(AbilityFamily.COMBAT_STYLES, it) }
    }

    fun choices(family: AbilityFamily): List<AbilityChoice> = when (family) {
        AbilityFamily.ATTRIBUTES -> attributes
        AbilityFamily.SIGNS -> signs
        AbilityFamily.COMBAT_STYLES -> combatStyles
    }

    fun groups(family: AbilityFamily): List<AbilityGroup> = when (family) {
        AbilityFamily.ATTRIBUTES -> listOf(
            AbilityGroup("Strength", "hero.group.strength"),
            AbilityGroup("Dexterity", "hero.group.dexterity"),
            AbilityGroup("Endurance", "hero.group.stamina"),
            AbilityGroup("Intelligence", "hero.group.intelligence")
        )
        AbilityFamily.SIGNS -> listOf(
            AbilityGroup("Aard", "hero.group.aard"), AbilityGroup("Igni", "hero.group.igni"),
            AbilityGroup("Quen", "hero.group.quen"), AbilityGroup("Axi", "hero.group.axii"),
            AbilityGroup("Yrden", "hero.group.yrden")
        )
        AbilityFamily.COMBAT_STYLES -> listOf(
            AbilityGroup("StyleSteelStrong", "hero.group.strongSteel"),
            AbilityGroup("StyleSteelFast", "hero.group.fastSteel"),
            AbilityGroup("StyleSteelGroup", "hero.group.groupSteel"),
            AbilityGroup("StyleSilverStrong", "hero.group.strongSilver"),
            AbilityGroup("StyleSilverFast", "hero.group.fastSilver"),
            AbilityGroup("StyleSilverGroup", "hero.group.groupSilver")
        )
    }

    /** Five game-facing progression columns, with each level's related nodes below it. */
    fun tree(family: AbilityFamily, groupKey: String): List<AbilityTreeLevel> {
        val pattern = Regex("^${Regex.escape(groupKey)}(\\d+)(?: (Powerup|Upgrade)(\\d+)?)?$")
        val indexed = choices(family).mapNotNull { choice ->
            val match = pattern.matchEntire(choice.databaseLabel) ?: return@mapNotNull null
            val level = match.groupValues[1].toInt()
            val lane = when (match.groupValues[2]) {
                "Powerup" -> 1
                "Upgrade" -> 1 + (match.groupValues[3].toIntOrNull() ?: 1)
                else -> 0
            }
            Triple(level, lane, choice)
        }
        return (1..5).map { level ->
            AbilityTreeLevel(
                level = level,
                tier = when (level) {
                    1, 2 -> TalentTier.BRONZE
                    3, 4 -> TalentTier.SILVER
                    else -> TalentTier.GOLD
                },
                abilities = indexed.filter { it.first == level }.sortedBy { it.second }.map { it.third }
            )
        }
    }
}

data class HeroSummary(
    val level: Int,
    val experience: Int,
    val vitality: Int,
    val endurance: Int,
    val toxicity: Int,
    val orens: Int,
    val bronzeTalents: Int,
    val silverTalents: Int,
    val goldTalents: Int,
    val difficulty: Difficulty?
)

object HeroData {
    fun summary(session: GameSession): HeroSummary {
        val player = playerFields(session)
        return HeroSummary(
            level = player?.integerOrZero("ExpLevel") ?: 0,
            experience = player?.integerOrZero("Experience") ?: 0,
            vitality = player?.integerOrZero("CurrentHitPoints") ?: 0,
            endurance = player?.integerOrZero("CurrentEndurance") ?: 0,
            toxicity = player?.integerOrZero("CurrentToxicity") ?: 0,
            orens = player?.integerOrZero("Gold") ?: 0,
            bronzeTalents = player?.integerOrZero("TalentBronze") ?: 0,
            silverTalents = player?.integerOrZero("TalentSilver") ?: 0,
            goldTalents = player?.integerOrZero("TalentGold") ?: 0,
            difficulty = DifficultyAccess.readOrNull(session)
        )
    }

    fun acquiredAbilities(session: GameSession): Set<String> {
        val abilities = playerFields(session)?.getElement("CharAbilities")?.getValue() as? DBList ?: return emptySet()
        return abilities.mapNotNull { element ->
            (element.getValue() as? DBList)?.let { fields -> runCatching { fields.getString("RnAbName") }.getOrNull() }
        }.toSet()
    }

    internal fun playerFields(session: GameSession): DBList? {
        val top = session.database?.getTopLevelStruct()?.getValue() as? DBList ?: return null
        val modPlayers = top.getElement("Mod_PlayerList")?.getValue() as? DBList ?: return null
        if (modPlayers.getElementCount() == 0) return null
        return modPlayers.getElement(0).getValue() as? DBList
    }

    private fun DBList.integerOrZero(label: String): Int = runCatching { getInteger(label) }.getOrDefault(0)
}

class SetHeroSummaryCommand(private val summary: HeroSummary) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        if (HeroData.playerFields(session) == null) return listOf(LocalizedText("hero.command.noPlayerRecord"))
        val values = listOf(
            "hero.field.level" to summary.level,
            "hero.field.experience" to summary.experience,
            "hero.field.vitality" to summary.vitality,
            "hero.field.endurance" to summary.endurance,
            "hero.field.toxicity" to summary.toxicity,
            "hero.field.orens" to summary.orens,
            "hero.field.bronzeTalents" to summary.bronzeTalents,
            "hero.field.silverTalents" to summary.silverTalents,
            "hero.field.goldTalents" to summary.goldTalents
        )
        return values.filter { it.second < 0 }.map { LocalizedText("hero.command.negativeValue", LocalizedText(it.first)) }
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val database = requireNotNull(session.database) { "No module database is open" }
        val before = requireNotNull(database.getTopLevelStruct()).clone()
        val player = requireNotNull(HeroData.playerFields(session)) { "No player record is open" }
        mapOf(
            "ExpLevel" to summary.level,
            "Experience" to summary.experience,
            "CurrentHitPoints" to summary.vitality,
            "CurrentEndurance" to summary.endurance,
            "CurrentToxicity" to summary.toxicity,
            "Gold" to summary.orens,
            "TalentBronze" to summary.bronzeTalents,
            "TalentSilver" to summary.silverTalents,
            "TalentGold" to summary.goldTalents
        ).forEach { (label, value) -> player.setInteger(label, value) }
        return AppliedEditorCommand(
            PendingChange(LocalizedText("hero.pending.editSummary"), EvidenceLevel.STRUCTURALLY_VERIFIED)
        ) { database.setTopLevelStruct(before.clone()) }
    }
}

/**
 * Command-backed replacement for the legacy checkbox mutations in the Hero
 * panels.  It preserves the prerequisite rules and the sign spell mirror in
 * KnownList0 while keeping Compose unaware of DBList traversal.
 */
class ToggleAbilityCommand(
    private val family: AbilityFamily,
    private val databaseLabel: String,
    private val enabled: Boolean
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val choice = HeroAbilityCatalog.choices(family).firstOrNull { it.databaseLabel == databaseLabel }
            ?: return listOf(
                LocalizedText("hero.command.unknownAbility", LocalizedText(family.messageKey), databaseLabel)
            )
        val player = HeroData.playerFields(session)
            ?: return listOf(LocalizedText("hero.command.noPlayerRecord"))
        val abilities = player.getElement("CharAbilities")?.getValue() as? DBList
            ?: return listOf(LocalizedText("hero.command.noCharAbilities"))
        val names = abilityNames(abilities)
        val descriptor = parse(choice.databaseLabel)
            ?: return listOf(LocalizedText("hero.command.unsupportedLabel", databaseLabel))

        if (enabled) {
            if (names.contains(databaseLabel)) {
                return listOf(LocalizedText("hero.command.alreadyAcquired", databaseLabel))
            }
            val missing = prerequisites(descriptor).firstOrNull { it !in names }
            if (missing != null) {
                return listOf(LocalizedText("hero.command.acquireFirst", missing, databaseLabel))
            }
        } else {
            if (databaseLabel !in names) {
                return listOf(LocalizedText("hero.command.notAcquired", databaseLabel))
            }
            val dependent = names.asSequence()
                .mapNotNull(::parse)
                .firstOrNull { dependsOn(it, descriptor) }
            if (dependent != null) {
                return listOf(LocalizedText("hero.command.removeFirst", dependent.label, databaseLabel))
            }
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val database = requireNotNull(session.database) { "No module database is open" }
        val before = requireNotNull(database.getTopLevelStruct()).clone()
        val player = requireNotNull(HeroData.playerFields(session)) { "No player record is open" }
        val abilities = requireNotNull(player.getElement("CharAbilities")?.getValue() as? DBList) {
            "The player record has no CharAbilities list"
        }
        val descriptor = requireNotNull(parse(databaseLabel)) { "Unsupported ability label: $databaseLabel" }
        val namesBefore = abilityNames(abilities)

        if (enabled) {
            addAbility(abilities, databaseLabel, player.environment)
            associatedAttribute(databaseLabel)?.let { addAbilityIfMissing(abilities, it, player.environment) }
        } else {
            removeAbility(abilities, databaseLabel)
            associatedAttribute(databaseLabel)?.let { removeAbility(abilities, it) }
        }

        if (family == AbilityFamily.SIGNS && descriptor.kind <= 1) {
            updateSignSpell(player, descriptor, namesBefore, enabled)
        }

        val actionKey = if (enabled) "hero.pending.addAbility" else "hero.pending.removeAbility"
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(actionKey, choiceName(family, databaseLabel)),
                EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            database.setTopLevelStruct(before.clone())
        }
    }

    private fun choiceName(family: AbilityFamily, label: String): String =
        when (family) {
            AbilityFamily.COMBAT_STYLES -> label.removePrefix("Style").replace("Steel", "Steel ").replace("Silver", "Silver ")
            else -> label
        }

    private fun prerequisites(descriptor: AbilityDescriptor): List<String> = when (descriptor.kind) {
        0 -> if (descriptor.level > 1) listOf("${descriptor.group}${descriptor.level - 1}") else emptyList()
        1 -> buildList {
            add("${descriptor.group}${descriptor.level}")
            if (descriptor.level > 1) add("${descriptor.group}${descriptor.level - 1} Powerup")
        }
        else -> listOf("${descriptor.group}${descriptor.level}")
    }

    private fun dependsOn(candidate: AbilityDescriptor, removed: AbilityDescriptor): Boolean {
        if (candidate.group != removed.group) return false
        return when (removed.kind) {
            0 -> candidate.level > removed.level ||
                (candidate.level == removed.level && candidate.kind != 0)
            1 -> candidate.kind == 1 && candidate.level > removed.level
            else -> false
        }
    }

    private fun updateSignSpell(player: DBList, descriptor: AbilityDescriptor, namesBefore: Set<String>, adding: Boolean) {
        val spellFamily = SIGN_SPELL_FAMILIES[descriptor.group] ?: return
        val row = descriptor.kind
        val beforeHighest = highestLevel(namesBefore, descriptor.group, row)
        val namesAfter = if (adding) namesBefore + descriptor.label else namesBefore - descriptor.label
        val afterHighest = highestLevel(namesAfter, descriptor.group, row)
        if (beforeHighest == afterHighest) return

        val low = spellFamily * 10
        val knownListElement = player.getElement("KnownList0")
        var spellList = knownListElement?.getValue() as? DBList
        var matchingIndex = -1
        if (spellList != null) {
            for (index in 0 until spellList.getElementCount()) {
                val fields = spellList.getElement(index).getValue() as? DBList ?: continue
                val spell = runCatching { fields.getInteger("Spell") }.getOrDefault(-1)
                if (spell in low..(low + 9) && (spell and 1) == row) {
                    matchingIndex = index
                    break
                }
            }
        }

        if (afterHighest < 0) {
            if (spellList != null && matchingIndex >= 0) spellList.removeElement(matchingIndex)
            return
        }

        val spellValue = low + 2 * afterHighest + row
        if (spellList != null && matchingIndex >= 0) {
            val fields = spellList.getElement(matchingIndex).getValue() as DBList
            fields.setInteger("Spell", spellValue)
        } else {
            if (spellList == null) {
                spellList = DBList(player.environment, 1)
                player.addElement(DBElement(15, 0, "KnownList0", spellList))
            }
            val fields = DBList(player.environment, 1)
            fields.addElement(DBElement(2, 0, "Spell", spellValue))
            spellList.addElement(DBElement(14, 2, "", fields))
        }
    }

    private fun highestLevel(names: Set<String>, group: String, kind: Int): Int =
        names.mapNotNull(::parse).filter { it.group == group && it.kind == kind }.maxOfOrNull { it.level } ?: -1

    private fun abilityNames(abilities: DBList): Set<String> = abilities.mapNotNull { element ->
        (element.getValue() as? DBList)?.let { fields -> runCatching { fields.getString("RnAbName") }.getOrNull() }
    }.toSet()

    private fun addAbilityIfMissing(abilities: DBList, label: String, environment: AppEnvironment) {
        if (label !in abilityNames(abilities)) addAbility(abilities, label, environment)
    }

    private fun addAbility(abilities: DBList, label: String, environment: AppEnvironment) {
        val fields = DBList(environment, 2)
        fields.addElement(DBElement(10, 0, "RnAbName", label))
        fields.addElement(DBElement(0, 0, "RnAbStk", 0))
        abilities.addElement(DBElement(14, 48879, "", fields))
    }

    private fun removeAbility(abilities: DBList, label: String) {
        for (index in abilities.getElementCount() - 1 downTo 0) {
            val fields = abilities.getElement(index).getValue() as? DBList ?: continue
            if (runCatching { fields.getString("RnAbName") }.getOrNull() == label) {
                abilities.removeElement(index)
            }
        }
    }

    private fun associatedAttribute(label: String): String? = ATTRIBUTE_ASSOCIATED[label]

    private data class AbilityDescriptor(
        val label: String,
        val group: String,
        val level: Int,
        val kind: Int
    )

    private fun parse(label: String): AbilityDescriptor? {
        val match = ABILITY_PATTERN.matchEntire(label) ?: return null
        val suffix = match.groupValues[3]
        val kind = when {
            suffix == "Powerup" -> 1
            suffix == "Upgrade" -> 2
            else -> 0
        }
        return AbilityDescriptor(label, match.groupValues[1], match.groupValues[2].toInt(), kind)
    }

    companion object {
        private val ABILITY_PATTERN = Regex("^([A-Za-z]+)(\\d+)(?: (Powerup|Upgrade)\\d+)?$")
        private val ATTRIBUTE_ASSOCIATED = mapOf(
            "Dexterity1 Upgrade1" to "Skinning",
            "Intelligence2 Upgrade1" to "HerbGathering",
            "Intelligence2 Upgrade3" to "GreaseMaking",
            "Intelligence3 Upgrade1" to "RitualOfPurify",
            "Intelligence3 Upgrade2" to "Anatomy",
            "Intelligence3 Upgrade3" to "BombMaking",
            "Intelligence4 Upgrade2" to "RitualOfLife"
        )
        private val SIGN_SPELL_FAMILIES = mapOf("Aard" to 0, "Igni" to 3, "Quen" to 1, "Axi" to 4, "Yrden" to 2)
    }
}
