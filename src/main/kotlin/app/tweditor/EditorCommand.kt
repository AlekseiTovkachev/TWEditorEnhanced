package app.tweditor

enum class EvidenceLevel(val messageKey: String) {
    VERIFIED_IN_GAME("evidence.verifiedInGame"),
    STRUCTURALLY_VERIFIED("evidence.structurallyVerified"),
    UNVERIFIED("evidence.unverified"),
    DELIBERATELY_DANGEROUS("evidence.deliberatelyDangerous")
}

data class PendingChange(
    val description: LocalizedText,
    val evidence: EvidenceLevel
)

/** Immutable state intended for presentation layers such as Compose. */
data class EditorPresentationState(
    val difficulty: Difficulty?,
    val pendingChanges: List<PendingChange>,
    val canUndo: Boolean,
    val validationMessages: List<LocalizedText>
)

interface EditorCommand {
    fun validate(session: GameSession): List<LocalizedText>

    fun apply(session: GameSession): AppliedEditorCommand
}

data class AppliedEditorCommand(
    val pendingChange: PendingChange,
    val undo: () -> Unit
)

sealed interface EditorCommandResult {
    val state: EditorPresentationState

    data class Applied(
        val change: PendingChange,
        override val state: EditorPresentationState
    ) : EditorCommandResult

    data class Rejected(
        val problems: List<LocalizedText>,
        override val state: EditorPresentationState
    ) : EditorCommandResult
}

data class EditorWorkflowResult(
    val completed: Boolean,
    val problems: List<LocalizedText>,
    val state: EditorPresentationState
)

/**
 * The presentation-facing command seam. Commands own validation, semantic
 * mutation, evidence, and undo; the controller owns history and the draft
 * workflow. Presentation code never needs to traverse a DBList.
 */
class EditorCommandController(private val session: GameSession) {
    private data class HistoryEntry(
        val command: AppliedEditorCommand,
        val dataModifiedBefore: Boolean,
        val draftDirtyBefore: Boolean
    )

    private val history = java.util.ArrayDeque<HistoryEntry>()
    private val pendingChanges = ArrayList<PendingChange>()
    private var appliedBoundary = 0
    private var validationMessages: List<LocalizedText> = emptyList()

    fun state(): EditorPresentationState = EditorPresentationState(
        difficulty = DifficultyAccess.readOrNull(session),
        pendingChanges = pendingChanges.toList(),
        canUndo = !history.isEmpty(),
        validationMessages = validationMessages.toList()
    )

    fun dispatch(command: EditorCommand): EditorCommandResult {
        val problems = command.validate(session)
        if (problems.isNotEmpty()) {
            validationMessages = problems.toList()
            return EditorCommandResult.Rejected(problems.toList(), state())
        }

        val dataModifiedBefore = session.isDataModified()
        val draftDirtyBefore = session.isDraftDirty()
        val historyEntry = HistoryEntry(
            command = command.apply(session),
            dataModifiedBefore = dataModifiedBefore,
            draftDirtyBefore = draftDirtyBefore
        )
        session.setDataModified(true)
        history.addLast(historyEntry)
        pendingChanges.add(historyEntry.command.pendingChange)
        validationMessages = emptyList()
        return EditorCommandResult.Applied(historyEntry.command.pendingChange, state())
    }

    fun undo(): EditorWorkflowResult {
        if (history.isEmpty()) {
            return EditorWorkflowResult(false, listOf(LocalizedText("command.undo.empty")), state())
        }

        val entry = history.removeLast()
        entry.command.undo()
        session.restoreEditState(entry.dataModifiedBefore, entry.draftDirtyBefore)
        pendingChanges.removeAt(pendingChanges.lastIndex)
        validationMessages = emptyList()
        return EditorWorkflowResult(true, emptyList(), state())
    }

    fun apply(): EditorWorkflowResult {
        if (!session.isDraftDirty()) {
            return EditorWorkflowResult(false, emptyList(), state())
        }

        val problems = session.runValidation()
        if (problems.isNotEmpty()) {
            validationMessages = problems.toList()
            return EditorWorkflowResult(false, problems.toList(), state())
        }

        session.applyDraft()
        history.clear()
        appliedBoundary = pendingChanges.size
        validationMessages = emptyList()
        return EditorWorkflowResult(true, emptyList(), state())
    }

    fun revert(): EditorWorkflowResult {
        if (!session.isDraftDirty()) {
            return EditorWorkflowResult(false, emptyList(), state())
        }
        if (!session.revertToBaseline()) {
            val problems = listOf(LocalizedText("command.revert.noBaseline"))
            validationMessages = problems
            return EditorWorkflowResult(false, problems, state())
        }

        history.clear()
        while (pendingChanges.size > appliedBoundary) {
            pendingChanges.removeAt(pendingChanges.lastIndex)
        }
        validationMessages = emptyList()
        return EditorWorkflowResult(true, emptyList(), state())
    }

    /** Clears the semantic review list after a successful Save. */
    fun markSaved() {
        pendingChanges.clear()
        appliedBoundary = 0
        history.clear()
        validationMessages = emptyList()
        session.setDataModified(false)
        session.createBaseline()
    }
}
