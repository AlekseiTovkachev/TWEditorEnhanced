package app.tweditor

enum class Difficulty(
    val displayName: String,
    val messageKey: String,
    private val marker: String,
    private val setting: Int
) {
    EASY("Easy", "difficulty.easy", "Difficulty_easy", 0),
    MEDIUM("Medium", "difficulty.medium", "Difficulty_normal", 1),
    HARD("Hard", "difficulty.hard", "", 2);

    companion object {
        fun fromDisplayName(value: String): Difficulty? = values().firstOrNull { it.displayName == value }

        fun fromSetting(value: Int): Difficulty? = values().firstOrNull { it.setting == value }
    }

    internal fun markerName(): String = marker
    internal fun settingValue(): Int = setting
}

class SetDifficultyCommand(val target: Difficulty) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> = DifficultyAccess.validate(session, target)

    override fun apply(session: GameSession): AppliedEditorCommand {
        val before = DifficultyAccess.capture(session)
        val previous = DifficultyAccess.readOrNull(session)
            ?: throw IllegalStateException("Difficulty is not readable")
        DifficultyAccess.apply(session, target)
        val change = PendingChange(
            description = LocalizedText(
                "difficulty.pending.set",
                LocalizedText(previous.messageKey),
                LocalizedText(target.messageKey)
            ),
            evidence = EvidenceLevel.STRUCTURALLY_VERIFIED
        )
        return AppliedEditorCommand(change) { DifficultyAccess.restore(session, before) }
    }
}

internal object DifficultyAccess {
    private data class Lists(
        val modulePlayer: DBList,
        val player: DBList,
        val smm: DBList
    )

    data class Snapshot(
        val moduleAbilities: DBElement,
        val playerAbilities: DBElement,
        val gameDifficulty: DBElement
    )

    fun readOrNull(session: GameSession): Difficulty? {
        return try {
            val lists = lists(session)
            playerMarkerDifficulty(lists.modulePlayer) ?: Difficulty.fromSetting(
                lists.smm.getElement("GameDiffSetting")?.let { lists.smm.getInteger("GameDiffSetting") }
                    ?: return null
            )
        } catch (_: Exception) {
            null
        }
    }

    fun validate(session: GameSession, target: Difficulty): List<LocalizedText> {
        val problems = ArrayList<LocalizedText>()
        val lists = try {
            lists(session)
        } catch (exc: Exception) {
            problems.add(
                LocalizedText("difficulty.command.unavailable", exc.message ?: "required Save structures are missing")
            )
            return problems
        }

        val current = try {
            playerMarkerDifficulty(lists.modulePlayer) ?: lists.smm.getElement("GameDiffSetting")?.let {
                Difficulty.fromSetting(lists.smm.getInteger("GameDiffSetting"))
            }
        } catch (exc: Exception) {
            problems.add(LocalizedText("difficulty.command.unreadable", exc.message ?: "CharAbilities is malformed"))
            return problems
        }
        if (current == null) {
            problems.add(LocalizedText("difficulty.command.noRecognizedValue"))
        } else if (current == target) {
            problems.add(LocalizedText("difficulty.command.alreadySet", LocalizedText(target.messageKey)))
        }

        try {
            for ((nameKey, list) in listOf("difficulty.anchor.modulePlayer" to lists.modulePlayer, "difficulty.anchor.player" to lists.player)) {
                val abilities = abilities(list)
                if (target != Difficulty.HARD && abilitiesMarkerDifficulty(abilities) == null && difficultyAnchor(abilities) < 0) {
                    problems.add(LocalizedText("difficulty.command.noAnchor", LocalizedText(nameKey)))
                }
            }
        } catch (exc: Exception) {
            problems.add(LocalizedText("difficulty.command.malformed", exc.message ?: "unknown structure"))
        }
        return problems
    }

    fun capture(session: GameSession): Snapshot {
        val lists = lists(session)
        return Snapshot(
            moduleAbilities = lists.modulePlayer.getElement("CharAbilities")!!.clone(),
            playerAbilities = lists.player.getElement("CharAbilities")!!.clone(),
            gameDifficulty = lists.smm.getElement("GameDiffSetting")!!.clone()
        )
    }

    fun apply(session: GameSession, target: Difficulty) {
        val lists = lists(session)
        setCharAbilities(lists.modulePlayer, target)
        setCharAbilities(lists.player, target)
        lists.smm.setInteger("GameDiffSetting", target.settingValue())
    }

    fun restore(session: GameSession, snapshot: Snapshot) {
        val lists = lists(session)
        lists.modulePlayer.setElement("CharAbilities", snapshot.moduleAbilities.clone())
        lists.player.setElement("CharAbilities", snapshot.playerAbilities.clone())
        lists.smm.setElement("GameDiffSetting", snapshot.gameDifficulty.clone())
    }

    private fun lists(session: GameSession): Lists {
        val moduleTop = session.database?.getTopLevelStruct()?.getValue() as? DBList
            ?: throw IllegalStateException("Module is not loaded")
        val modulePlayers = moduleTop.getElement("Mod_PlayerList")?.getValue() as? DBList
            ?: throw IllegalStateException("Module player list is missing")
        val modulePlayer = modulePlayers.getElement(0).getValue() as? DBList
            ?: throw IllegalStateException("Module player record is missing")
        val player = session.playerDatabase?.getTopLevelStruct()?.getValue() as? DBList
            ?: throw IllegalStateException("Player blueprint is not loaded")
        val smm = session.smmDatabase?.getTopLevelStruct()?.getValue() as? DBList
            ?: throw IllegalStateException("Save metadata is not loaded")
        return Lists(modulePlayer, player, smm)
    }

    private fun abilities(player: DBList): DBList {
        return player.getElement("CharAbilities")?.getValue() as? DBList
            ?: throw IllegalStateException("CharAbilities is missing")
    }

    private fun playerMarkerDifficulty(player: DBList): Difficulty? {
        return abilitiesMarkerDifficulty(abilities(player))
    }

    private fun abilitiesMarkerDifficulty(abilities: DBList): Difficulty? {
        for (element in abilities) {
            val fields = element.getValue() as? DBList ?: continue
            when (abilityName(fields)) {
                Difficulty.EASY.markerName() -> return Difficulty.EASY
                Difficulty.MEDIUM.markerName() -> return Difficulty.MEDIUM
            }
        }
        return null
    }

    private fun difficultyAnchor(abilities: DBList): Int {
        for (i in 0 until abilities.getElementCount()) {
            val fields = abilities.getElement(i).getValue() as? DBList ?: continue
            if (abilityName(fields) == "StyleSilverGroup1") {
                return i
            }
        }
        return -1
    }

    private fun abilityName(fields: DBList): String {
        val named = fields.getElement("RnAbName")
        return named?.getValue()?.toString() ?: fields.getElement(0).getValue().toString()
    }

    private fun setCharAbilities(player: DBList, target: Difficulty) {
        val abilities = abilities(player)
        for (i in abilities.getElementCount() - 1 downTo 0) {
            val fields = abilities.getElement(i).getValue() as? DBList ?: continue
            if (abilityName(fields) == Difficulty.EASY.markerName() || abilityName(fields) == Difficulty.MEDIUM.markerName()) {
                abilities.removeElement(i)
            }
        }
        if (target == Difficulty.HARD) {
            return
        }

        val anchor = difficultyAnchor(abilities)
        if (anchor < 0) {
            throw IllegalStateException("CharAbilities has no difficulty anchor")
        }
        val fields = DBList(player.environment, 2)
        fields.addElement(DBElement(DBElement.STRING, 0, "RnAbName", target.markerName()))
        fields.addElement(DBElement(DBElement.BYTE, 0, "RnAbStk", 0))
        abilities.insertElement(anchor + 1, DBElement(DBElement.STRUCT, 48879, "", fields))
    }
}
