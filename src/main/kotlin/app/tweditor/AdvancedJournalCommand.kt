package app.tweditor

import java.util.Locale

/** Raw Journal categories whose four-field entry shape is understood well enough for warned editing. */
object AdvancedJournalCategories {
    private val displayNames = linkedMapOf(
        "character" to "Characters",
        "place" to "Locations",
        "hydragenum" to "Ingredients",
        "vermilion" to "Ingredients",
        "rebis" to "Ingredients",
        "quebrith" to "Ingredients",
        "aether" to "Ingredients",
        "vitriol" to "Ingredients",
        "info" to "Glossary",
        "tutorial" to "Tutorials",
        "alchemy" to "Tutorials"
    )

    private val messageKeys = mapOf(
        "character" to "journal.category.characters",
        "place" to "journal.category.locations",
        "hydragenum" to "journal.category.ingredients",
        "vermilion" to "journal.category.ingredients",
        "rebis" to "journal.category.ingredients",
        "quebrith" to "journal.category.ingredients",
        "aether" to "journal.category.ingredients",
        "vitriol" to "journal.category.ingredients",
        "info" to "journal.category.glossary",
        "tutorial" to "journal.category.tutorials",
        "alchemy" to "journal.category.tutorials"
    )

    fun normalize(category: String): String = category.trim().lowercase(Locale.ROOT)

    fun displayName(category: String): String? = displayNames[normalize(category)]

    fun messageKey(category: String): String? = messageKeys[normalize(category)]

    fun supports(category: String): Boolean = displayName(category) != null
}

data class AdvancedJournalEntryValues(
    val entryId: String,
    val read: Boolean,
    val timeOfDay: Int
)

internal data class AdvancedJournalEditability(
    val supported: Boolean,
    val reason: LocalizedText? = null
)

internal data class AdvancedJournalRecord(
    val index: Int,
    val element: DBElement,
    val fields: DBList,
    val category: String,
    val entryId: String,
    val supportedShape: Boolean,
    val reason: LocalizedText?
)

/** Domain seam for the conservative, raw Journal shape used by Advanced Journal Editing. */
internal object AdvancedJournalAccess {
    private val expectedFields = setOf("Entry", "EntryCD", "EntryTOD", "EntryRead")

    fun editability(session: GameSession, category: String, entryId: String): AdvancedJournalEditability {
        val normalizedCategory = AdvancedJournalCategories.normalize(category)
        if (!AdvancedJournalCategories.supports(normalizedCategory)) {
            return AdvancedJournalEditability(false, LocalizedText("journal.edit.unresolvedCategory"))
        }

        val matches = records(session).filter {
            it.category == normalizedCategory && it.entryId.equals(entryId.trim(), ignoreCase = true)
        }
        if (matches.size != 1) {
            return AdvancedJournalEditability(
                false,
                if (matches.isEmpty()) {
                    LocalizedText("journal.edit.entryGone")
                } else {
                    LocalizedText("journal.edit.multipleVariants")
                }
            )
        }
        val match = matches.single()
        return if (match.supportedShape) {
            AdvancedJournalEditability(true)
        } else {
            AdvancedJournalEditability(false, match.reason)
        }
    }

    fun canAdd(session: GameSession, category: String): Boolean =
        AdvancedJournalCategories.supports(category) && records(session).any {
            it.category == AdvancedJournalCategories.normalize(category) && it.supportedShape
        }

    fun records(session: GameSession): List<AdvancedJournalRecord> {
        val topList = session.getQuestDatabase()?.getTopLevelStruct()?.getValue() as? DBList ?: return emptyList()
        return records(topList)
    }

    fun records(topList: DBList): List<AdvancedJournalRecord> {
        val journal = topList.getElement("Journal")?.takeIf { it.getType() == DBElement.LIST }
            ?.getValue() as? DBList ?: return emptyList()
        return journal.mapIndexedNotNull { index, element -> record(index, element) }
    }

    fun currentGameTime(session: GameSession): Int = session.getJournalData()?.entries.orEmpty()
        .map { it.timeOfDay }
        .filter { it > 0 }
        .maxOrNull()
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: 0

    fun supportedRecord(session: GameSession, category: String, entryId: String): AdvancedJournalRecord? =
        records(session).singleOrNull {
            it.category == AdvancedJournalCategories.normalize(category) &&
                it.entryId.equals(entryId.trim(), ignoreCase = true) && it.supportedShape
        }

    fun supportedTemplate(session: GameSession, category: String): DBElement? = records(session)
        .firstOrNull {
            it.category == AdvancedJournalCategories.normalize(category) && it.supportedShape
        }
        ?.element
        ?.clone()

    private fun record(index: Int, element: DBElement): AdvancedJournalRecord? {
        val fields = element.getValue() as? DBList ?: return null
        val rawEntry = runCatching { fields.getString("Entry") }.getOrDefault("")
        val separator = rawEntry.indexOf(':')
        val category = if (separator > 0) AdvancedJournalCategories.normalize(rawEntry.substring(0, separator)) else ""
        val entryId = if (separator > 0) rawEntry.substring(separator + 1).trim() else ""
        val shapeReason = when {
            category.isEmpty() || entryId.isEmpty() -> LocalizedText("journal.edit.incompleteIdentity")
            !AdvancedJournalCategories.supports(category) -> LocalizedText("journal.edit.unresolvedCategory")
            element.getType() != DBElement.STRUCT -> LocalizedText("journal.edit.notStandard")
            !hasExpectedShape(fields) -> LocalizedText("journal.edit.unfamiliarFields")
            else -> null
        }
        return AdvancedJournalRecord(
            index = index,
            element = element,
            fields = fields,
            category = category,
            entryId = entryId,
            supportedShape = shapeReason == null,
            reason = shapeReason
        )
    }

    private fun hasExpectedShape(fields: DBList): Boolean {
        if (fields.getElementCount() != expectedFields.size) return false
        if (fields.map { it.getLabel() }.toSet() != expectedFields) return false
        return fields.getElement("Entry")?.getType() == DBElement.STRING &&
            fields.getElement("EntryCD")?.getType() == DBElement.DWORD &&
            fields.getElement("EntryTOD")?.getType() == DBElement.DWORD &&
            fields.getElement("EntryRead")?.getType() == DBElement.BYTE
    }

    fun hasIdentity(session: GameSession, category: String, entryId: String): Boolean {
        val normalizedCategory = AdvancedJournalCategories.normalize(category)
        return records(session).any {
            it.category == normalizedCategory && it.entryId.equals(entryId.trim(), ignoreCase = true)
        }
    }
}

private data class AdvancedJournalSnapshot(
    val topLevel: DBElement,
    val journalDirty: Boolean
)

private fun advancedJournalSnapshot(session: GameSession): AdvancedJournalSnapshot = AdvancedJournalSnapshot(
    topLevel = requireNotNull(session.getQuestDatabase()?.getTopLevelStruct()) { "No quest database is open" }.clone(),
    journalDirty = session.isJournalDirty()
)

private fun restoreAdvancedJournalSnapshot(session: GameSession, snapshot: AdvancedJournalSnapshot) {
    session.restoreJournalSnapshot(snapshot.topLevel, snapshot.journalDirty)
}

private fun validateAdvancedJournalMode(enabled: Boolean): List<LocalizedText> =
    if (enabled) emptyList() else listOf(LocalizedText("journal.command.advancedOff"))

private fun validateAdvancedJournalCategory(category: String): List<LocalizedText> =
    if (AdvancedJournalCategories.supports(category)) emptyList()
    else listOf(LocalizedText("journal.command.unsupportedCategory", category))

private fun validateEntryId(entryId: String, actionKey: String): List<LocalizedText> {
    val id = entryId.trim()
    return when {
        id.isEmpty() -> listOf(LocalizedText("journal.command.emptyId", LocalizedText(actionKey)))
        ':' in id -> listOf(LocalizedText("journal.command.idNoColon"))
        else -> emptyList()
    }
}

class AddAdvancedJournalEntryCommand(
    private val category: String,
    private val entryId: String,
    private val read: Boolean = false,
    private val timeOfDay: Int? = null,
    private val advancedJournalEditing: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val problems = ArrayList<LocalizedText>()
        problems += validateAdvancedJournalMode(advancedJournalEditing)
        problems += validateAdvancedJournalCategory(category)
        problems += validateEntryId(entryId, "journal.action.adding")
        if (problems.isNotEmpty()) return problems
        if (session.getQuestDatabase() == null || session.getJournalData() == null) {
            return listOf(LocalizedText("journal.command.noJournalDatabase"))
        }
        if (timeOfDay != null && timeOfDay < 0) {
            problems += LocalizedText("journal.command.negativeTOD")
        }
        if (AdvancedJournalAccess.hasIdentity(session, category, entryId)) {
            problems += LocalizedText("journal.command.duplicateAdd")
        }
        if (AdvancedJournalAccess.supportedTemplate(session, category) == null) {
            problems += LocalizedText(
                "journal.command.noTemplate",
                AdvancedJournalCategories.displayName(category) ?: category
            )
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = advancedJournalSnapshot(session)
        val normalizedCategory = AdvancedJournalCategories.normalize(category)
        val normalizedId = entryId.trim()
        val acquisitionTime = timeOfDay ?: AdvancedJournalAccess.currentGameTime(session)
        val template = requireNotNull(AdvancedJournalAccess.supportedTemplate(session, normalizedCategory))
        session.mutateJournal { topList ->
            val journal = journalList(topList)
            val fields = template.getValue() as DBList
            fields.setString("Entry", "$normalizedCategory:$normalizedId")
            fields.setInteger("EntryCD", 0, DBElement.DWORD)
            fields.setInteger("EntryTOD", acquisitionTime, DBElement.DWORD)
            fields.setInteger("EntryRead", if (read) 1 else 0, DBElement.BYTE)
            journal.addElement(template)
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(
                    "journal.pending.add",
                    AdvancedJournalCategories.messageKey(normalizedCategory)?.let(::LocalizedText) ?: normalizedCategory,
                    "$normalizedCategory:$normalizedId"
                ),
                EvidenceLevel.DELIBERATELY_DANGEROUS
            )
        ) {
            restoreAdvancedJournalSnapshot(session, snapshot)
        }
    }
}

class EditAdvancedJournalEntryCommand(
    private val category: String,
    private val currentEntryId: String,
    private val values: AdvancedJournalEntryValues,
    private val advancedJournalEditing: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val problems = ArrayList<LocalizedText>()
        problems += validateAdvancedJournalMode(advancedJournalEditing)
        problems += validateAdvancedJournalCategory(category)
        problems += validateEntryId(currentEntryId, "journal.action.editing")
        problems += validateEntryId(values.entryId, "journal.action.editing")
        if (values.timeOfDay < 0) problems += LocalizedText("journal.command.negativeTOD")
        if (problems.isNotEmpty()) return problems
        val matches = AdvancedJournalAccess.records(session).filter {
            it.category == AdvancedJournalCategories.normalize(category) &&
                it.entryId.equals(currentEntryId.trim(), ignoreCase = true)
        }
        if (matches.size != 1) {
            problems += if (matches.isEmpty()) {
                LocalizedText("journal.edit.entryGone")
            } else {
                LocalizedText("journal.edit.multipleVariantsEdit")
            }
        } else if (!matches.single().supportedShape) {
            problems += matches.single().reason ?: LocalizedText("journal.edit.unfamiliarShape")
        }
        if (!values.entryId.trim().equals(currentEntryId.trim(), ignoreCase = true) &&
            AdvancedJournalAccess.hasIdentity(session, category, values.entryId)
        ) {
            problems += LocalizedText("journal.command.duplicateReplacement")
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = advancedJournalSnapshot(session)
        val normalizedCategory = AdvancedJournalCategories.normalize(category)
        val oldId = currentEntryId.trim()
        val newId = values.entryId.trim()
        session.mutateJournal { topList ->
            val record = AdvancedJournalAccess.records(topList).single {
                it.category == normalizedCategory && it.entryId.equals(oldId, ignoreCase = true)
            }
            record.fields.setString("Entry", "$normalizedCategory:$newId")
            record.fields.setInteger("EntryTOD", values.timeOfDay, DBElement.DWORD)
            record.fields.setInteger("EntryRead", if (values.read) 1 else 0, DBElement.BYTE)
        }
        val identity = if (oldId.equals(newId, ignoreCase = true)) {
            "$normalizedCategory:$oldId"
        } else {
            "$normalizedCategory:$oldId -> $normalizedCategory:$newId"
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(
                    "journal.pending.edit",
                    AdvancedJournalCategories.messageKey(normalizedCategory)?.let(::LocalizedText) ?: normalizedCategory,
                    identity
                ),
                EvidenceLevel.DELIBERATELY_DANGEROUS
            )
        ) {
            restoreAdvancedJournalSnapshot(session, snapshot)
        }
    }
}

class RemoveAdvancedJournalEntryCommand(
    private val category: String,
    private val entryId: String,
    private val advancedJournalEditing: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val problems = ArrayList<LocalizedText>()
        problems += validateAdvancedJournalMode(advancedJournalEditing)
        problems += validateAdvancedJournalCategory(category)
        problems += validateEntryId(entryId, "journal.action.removing")
        if (problems.isNotEmpty()) return problems
        val matches = AdvancedJournalAccess.records(session).filter {
            it.category == AdvancedJournalCategories.normalize(category) &&
                it.entryId.equals(entryId.trim(), ignoreCase = true)
        }
        if (matches.size != 1) {
            problems += if (matches.isEmpty()) {
                LocalizedText("journal.edit.entryGone")
            } else {
                LocalizedText("journal.edit.multipleVariantsRemove")
            }
        } else if (!matches.single().supportedShape) {
            problems += matches.single().reason ?: LocalizedText("journal.edit.unfamiliarShape")
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = advancedJournalSnapshot(session)
        val normalizedCategory = AdvancedJournalCategories.normalize(category)
        val normalizedId = entryId.trim()
        session.mutateJournal { topList ->
            val journal = journalList(topList)
            val record = AdvancedJournalAccess.records(topList).single {
                it.category == normalizedCategory && it.entryId.equals(normalizedId, ignoreCase = true)
            }
            journal.removeElement(record.index)
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(
                    "journal.pending.remove",
                    AdvancedJournalCategories.messageKey(normalizedCategory)?.let(::LocalizedText) ?: normalizedCategory,
                    "$normalizedCategory:$normalizedId"
                ),
                EvidenceLevel.DELIBERATELY_DANGEROUS
            )
        ) {
            restoreAdvancedJournalSnapshot(session, snapshot)
        }
    }
}

private fun journalList(topList: DBList): DBList {
    val journal = topList.getElement("Journal")
        ?: throw IllegalStateException("The open Save has no Journal list")
    if (journal.getType() != DBElement.LIST) {
        throw IllegalStateException("The open Save has an unfamiliar Journal shape")
    }
    return journal.getValue() as DBList
}
