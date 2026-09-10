package app.tweditor

data class QuestPhaseTarget(
    val questResourceName: String,
    val questName: String,
    val phaseId: Int,
    val phaseName: String,
    val current: Boolean
) {
    val rawReference: String get() = "$questResourceName:CurrPhase=$phaseId"
}

internal data class QuestPhaseShape(
    val rootFields: DBList,
    val phases: DBList,
    val currentPhase: Int
)

internal object QuestPhaseAccess {
    fun targets(session: GameSession): List<QuestPhaseTarget> = session.getQuests().orEmpty()
        .flatMap { quest ->
            val shape = shape(quest) ?: return@flatMap emptyList()
            (0 until shape.phases.getElementCount()).mapNotNull { index ->
                val phaseFields = shape.phases.getElement(index).getValue() as? DBList ?: return@mapNotNull null
                val phaseName = runCatching { phaseFields.getString("Name") }
                    .getOrDefault("")
                    .trim()
                    .ifEmpty { "Root phase $index" }
                QuestPhaseTarget(
                    questResourceName = quest.getResourceName(),
                    questName = quest.questName,
                    phaseId = index + 1,
                    phaseName = phaseName,
                    current = shape.currentPhase == index + 1
                )
            }
        }

    fun quest(session: GameSession, resourceName: String): Quest? = session.getQuests().orEmpty()
        .firstOrNull { it.getResourceName().equals(resourceName, ignoreCase = true) }

    fun shape(quest: Quest): QuestPhaseShape? {
        val topFields = quest.getQuestElement().getValue() as? DBList ?: return null
        val mainPhase = topFields.getElement("MainPhase")?.takeIf { it.getType() == DBElement.LIST }
            ?.getValue() as? DBList ?: return null
        val rootFields = mainPhase.getElement(0).getValue() as? DBList ?: return null
        val phaseElement = rootFields.getElement("Phases")?.takeIf { it.getType() == DBElement.LIST }
            ?: return null
        val phases = phaseElement.getValue() as? DBList ?: return null
        val current = runCatching { rootFields.getInteger("CurrPhase") }.getOrNull() ?: return null
        return QuestPhaseShape(rootFields, phases, current)
    }

    fun phaseName(quest: Quest, phaseId: Int): String = shape(quest)?.let { shape ->
        val fields = shape.phases.getElement(phaseId - 1).getValue() as? DBList
        fields?.let { runCatching { it.getString("Name") }.getOrDefault("").trim() }
            ?.takeIf { it.isNotEmpty() }
    } ?: "Root phase $phaseId"
}

private data class QuestPhaseCommandSnapshot(
    val quest: Quest,
    val questSnapshot: QuestSnapshot
)

private fun questPhaseSnapshot(session: GameSession, resourceName: String): QuestPhaseCommandSnapshot {
    val quest = requireNotNull(QuestPhaseAccess.quest(session, resourceName)) { "Quest $resourceName is not open" }
    return QuestPhaseCommandSnapshot(quest, quest.snapshot())
}

private fun restoreQuestPhaseSnapshot(snapshot: QuestPhaseCommandSnapshot) {
    snapshot.quest.restore(snapshot.questSnapshot)
}

class SetQuestPhaseCommand(
    private val questResourceName: String,
    private val phaseId: Int,
    private val advancedJournalEditing: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        if (!advancedJournalEditing) {
            return listOf(LocalizedText("journal.command.advancedOffQuest"))
        }
        if (questResourceName.trim().isEmpty()) return listOf(LocalizedText("quest.command.chooseQuest"))
        if (phaseId <= 0) return listOf(LocalizedText("quest.command.chooseRootPhase"))
        val quest = QuestPhaseAccess.quest(session, questResourceName)
            ?: return listOf(LocalizedText("quest.command.notInSave"))
        val shape = QuestPhaseAccess.shape(quest)
            ?: return listOf(LocalizedText("quest.command.unfamiliarShape"))
        if (phaseId > shape.phases.getElementCount()) {
            return listOf(LocalizedText("quest.command.arbitraryPhaseBlocked"))
        }
        if (shape.currentPhase == phaseId) {
            return listOf(LocalizedText("quest.command.alreadyAtPhase", phaseId))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = questPhaseSnapshot(session, questResourceName)
        val quest = snapshot.quest
        val shape = requireNotNull(QuestPhaseAccess.shape(quest))
        val oldPhase = shape.currentPhase
        shape.rootFields.setInteger("CurrPhase", phaseId)
        quest.setModified(true)
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(
                    "quest.pending.override",
                    quest.getResourceName(),
                    oldPhase,
                    phaseId,
                    QuestPhaseAccess.phaseName(quest, phaseId)
                ),
                EvidenceLevel.DELIBERATELY_DANGEROUS
            )
        ) {
            restoreQuestPhaseSnapshot(snapshot)
        }
    }
}
