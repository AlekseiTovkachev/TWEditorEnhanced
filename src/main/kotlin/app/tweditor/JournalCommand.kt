package app.tweditor

import java.io.File
import java.io.InputStream
import java.util.Locale

data class MonsterKnowledgeTarget(
    val monsterId: String,
    val name: String,
    val variants: List<String>
) {
    val known: Boolean get() = variants.isNotEmpty()
}

/** One journal.2da catalog row: the raw category, its entry ID, and its picture. */
data class JournalCatalogRow(val category: String, val entryId: String, val picture: String)

enum class FormulaKind(val category: String, val displayName: String, val messageKey: String) {
    POTION("recipe", "Potion", "journal.formula.potion"),
    OIL("recipe_oil", "Oil", "journal.formula.oil"),
    BOMB("recipe_bomb", "Bomb", "journal.formula.bomb");

    companion object {
        fun fromCategory(category: String): FormulaKind? = values().firstOrNull {
            it.category.equals(category.trim(), ignoreCase = true)
        }

        /**
         * Player alchemy lists do not carry the Journal category.  These
         * prefixes are only used for already-observed records, never to add a
         * catalog row that the installed game has not exposed.
         */
        fun inferFromObservedId(formulaId: String): FormulaKind? {
            val id = formulaId.lowercase(Locale.ROOT)
            return when {
                id.contains("grease") || id.contains("oil") -> OIL
                id.contains("bomb") -> BOMB
                id.contains("potion") -> POTION
                else -> null
            }
        }
    }
}

data class FormulaKnowledgeTarget(
    val category: String,
    val formulaId: String,
    val name: String,
    val presentInJournal: Boolean,
    val presentInKnowledge: Boolean,
    val presentInRandomRecipes: Boolean,
    val presentInIdentified: Boolean
) {
    val kind: FormulaKind get() = requireNotNull(FormulaKind.fromCategory(category))
    val complete: Boolean
        get() = presentInJournal && presentInKnowledge && presentInRandomRecipes && presentInIdentified
    val present: Boolean
        get() = presentInJournal || presentInKnowledge || presentInRandomRecipes || presentInIdentified
}

/**
 * Resolves the observed bestiary catalog when the installed game exposes it,
 * then merges in every existing bestiary variant from the Save. Existing
 * variants are therefore still visible when a Mod catalog row is unavailable.
 */
object JournalCatalog {
    fun monsterTargets(environment: AppEnvironment?, existing: Iterable<JournalEntry>): List<MonsterKnowledgeTarget> {
        val grouped = LinkedHashMap<String, LinkedHashSet<String>>()
        if (environment != null) {
            for (row in readBestiaryRows(environment)) {
                val monsterId = monsterId(row.entryId)
                if (monsterId.isNotEmpty()) grouped.getOrPut(monsterId) { LinkedHashSet() }
            }
        }
        for (entry in existing) {
            if (entry.category.equals("bestiary", ignoreCase = true)) {
                val id = monsterId(entry.entryId)
                if (id.isNotEmpty()) grouped.getOrPut(id) { LinkedHashSet() }.add(entry.entryId)
            }
        }
        return grouped.entries
            .map { (id, variants) ->
                MonsterKnowledgeTarget(id, JournalEntryNames.displayName(id), variants.toList().sorted())
            }
            .sortedWith(compareBy<MonsterKnowledgeTarget, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.monsterId })
    }

    fun formulaTargets(environment: AppEnvironment?, session: GameSession): List<FormulaKnowledgeTarget> {
        val definitions = LinkedHashMap<String, FormulaKind>()
        for (row in readJournalRows(environment).orEmpty()) {
            val kind = FormulaKind.fromCategory(row.category) ?: continue
            val id = row.entryId.trim()
            if (id.isNotEmpty()) definitions[formulaKey(kind.category, id)] = kind
        }

        val journalKindsById = HashMap<String, FormulaKind>()
        for (entry in session.getJournalData()?.entries.orEmpty()) {
            val kind = FormulaKind.fromCategory(entry.category) ?: continue
            val id = entry.entryId.trim()
            if (id.isNotEmpty()) {
                definitions[formulaKey(kind.category, id)] = kind
                journalKindsById[id.lowercase(Locale.ROOT)] = kind
            }
        }
        for (id in FormulaAccess.observedFormulaIds(session)) {
            val kind = journalKindsById[id.lowercase(Locale.ROOT)]
                ?: FormulaKind.inferFromObservedId(id)
                ?: continue
            definitions.putIfAbsent(formulaKey(kind.category, id), kind)
        }

        return definitions.entries
            .map { (key, kind) ->
                val id = key.substringAfter(':')
                val state = FormulaAccess.state(session, kind, id)
                FormulaKnowledgeTarget(
                    category = kind.category,
                    formulaId = id,
                    name = formulaName(environment, id),
                    presentInJournal = state.presentInJournal,
                    presentInKnowledge = state.presentInKnowledge,
                    presentInRandomRecipes = state.presentInRandomRecipes,
                    presentInIdentified = state.presentInIdentified
                )
            }
            .sortedWith(compareBy<FormulaKnowledgeTarget> { it.present }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.kind.displayName }
                .thenBy { it.formulaId })
    }

    private data class CatalogRow(val category: String, val entryId: String, val picture: String)

    private fun readBestiaryRows(environment: AppEnvironment): List<CatalogRow> {
        return readJournalRows(environment).orEmpty().filter {
            it.category.equals("bestiary", ignoreCase = true)
        }
    }

    /**
     * Every entry ID the installed game's journal.2da catalog defines, as
     * (category, entryId) pairs; the Journal workspace merges these with the
     * Save so missing entries can be offered for addition.
     */
    fun catalogEntries(environment: AppEnvironment?): List<JournalCatalogRow> {
        return readJournalRows(environment).orEmpty()
            .filter { it.entryId.isNotBlank() }
            .map { JournalCatalogRow(it.category, it.entryId, it.picture) }
    }

    /**
     * The display label for a catalog row: item-backed entry IDs resolve
     * through the item templates, ingredient rows resolve through their
     * `je_ingr_NNN` picture to the `it_ingr_NNN` template, and everything else
     * falls back to the curated prefix table. `x/y` entry IDs keep the variant
     * in a suffix, like the game's own journal shows them.
     */
    fun rowLabel(environment: AppEnvironment?, category: String, entryId: String, picture: String = ""): String {
        val trimmedId = entryId.trim()
        if (trimmedId.startsWith("it_", ignoreCase = true)) {
            val name = itemTemplateName(environment, trimmedId)
            if (name != null) {
                return withVariant(trimmedId, name)
            }
        }
        if (picture.startsWith("je_ingr", ignoreCase = true)) {
            val resref = "it_ingr_" + picture.substring("je_ingr_".length)
            val name = itemTemplateName(environment, resref)
            if (name != null) {
                return withVariant(trimmedId, name)
            }
            return withVariant(trimmedId, "Ingredient " + picture)
        }
        return withVariant(trimmedId, JournalEntryNames.displayName(trimmedId))
    }

    private fun itemTemplateName(environment: AppEnvironment?, resref: String): String? {
        if (environment == null) return null
        val key = resref.lowercase(Locale.ROOT)
        templateNames[key]?.let { return it }
        val fromScan = environment.itemTemplates.firstOrNull { it.resourceName.equals(resref, ignoreCase = true) }?.itemName
        val name = if (!fromScan.isNullOrBlank()) fromScan else readTemplateName(environment, resref)
        templateNames[key] = name
        return name
    }

    private fun readTemplateName(environment: AppEnvironment, resref: String): String? {
        val entry = environment.resourceFiles[resref.lowercase(Locale.ROOT) + ".uti"] ?: return null
        val input = ResourceAccess.open(entry) ?: return null
        return try {
            val database = Database(environment)
            database.load(input)
            val fields = database.getTopLevelStruct()!!.getValue() as DBList
            fields.getString("LocalizedName").ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }

    private val templateNames = HashMap<String, String?>()

    /** The `x/y` journal reference keeps its variant, like the game journal does. */
    private fun withVariant(entryId: String, label: String): String {
        val sep = entryId.indexOf('/')
        if (sep < 0) {
            return label
        }
        val variant = entryId.substring(sep + 1)
        if (variant.isEmpty() || variant == "info" || variant == "basic") {
            return label
        }
        return label + " (" + variant + ")"
    }

    /**
     * journal.2da rows as (category, entryId) pairs. The file is tab-separated
     * with a leading unnamed index column: the header line opens with a tab
     * (empty first cell) and each row opens with its row number, so the split
     * must NOT trim the line before splitting or every column shifts by one.
     */
    private fun readJournalRows(environment: AppEnvironment?): List<CatalogRow>? {
        if (environment == null) return emptyList()
        val resource = environment.resourceFiles["journal.2da"] ?: return emptyList()
        val input = open(resource) ?: return emptyList()
        return try {
            val lines = input.use { it.readBytes().toString(Charsets.ISO_8859_1).lineSequence().toList() }
            var header: List<String>? = null
            val rows = ArrayList<CatalogRow>()
            for (line in lines) {
                if (line.isBlank() || line.trimStart().startsWith("2DA")) continue
                val tokens = line.split('\t').map { it.trim() }
                if (header == null) {
                    header = tokens
                    continue
                }
                val category = value(header, tokens, "Category")
                val entryId = value(header, tokens, "EntryId")
                if (category.isNotEmpty() && entryId.isNotEmpty()) {
                    rows.add(CatalogRow(category, entryId, value(header, tokens, "Picture")))
                }
            }
            rows
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun open(resource: Any): InputStream? = try {
        ResourceAccess.open(resource)
    } catch (_: Throwable) {
        null
    }

    private fun value(header: List<String>, row: List<String>, name: String): String {
        val index = header.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (index < 0 || index >= row.size) return ""
        val value = row[index]
        return if (value == "****") "" else value
    }

    private fun monsterId(entryId: String): String = entryId.substringBefore('/').trim().lowercase()

    private fun formulaKey(category: String, id: String): String =
        category.lowercase(Locale.ROOT) + ":" + id.lowercase(Locale.ROOT)

    private fun formulaName(environment: AppEnvironment?, id: String): String =
        environment?.itemTemplates?.firstOrNull { it.resourceName.equals(id, ignoreCase = true) }
            ?.itemName?.takeIf { it.isNotBlank() }
            ?: JournalEntryNames.displayName(id)
}

internal data class FormulaRepresentationState(
    val presentInJournal: Boolean,
    val presentInKnowledge: Boolean,
    val presentInRandomRecipes: Boolean,
    val presentInIdentified: Boolean
) {
    val complete: Boolean
        get() = presentInJournal && presentInKnowledge && presentInRandomRecipes && presentInIdentified
}

internal object FormulaAccess {
    fun state(session: GameSession, kind: FormulaKind, formulaId: String): FormulaRepresentationState {
        val id = formulaId.lowercase(Locale.ROOT)
        val journal = session.getJournalData()?.entries.orEmpty().any {
            it.category.equals(kind.category, ignoreCase = true) && it.entryId.lowercase(Locale.ROOT) == id
        }
        val module = modulePlayer(session)
        val player = player(session)
        return FormulaRepresentationState(
            presentInJournal = journal,
            presentInKnowledge = containsFormula(module?.getElement("AlchKnowledge")?.getValue() as? DBList, id) &&
                containsFormula(player?.getElement("AlchKnowledge")?.getValue() as? DBList, id),
            presentInRandomRecipes = containsFormula(module?.getElement("AlchKnwnRandRec")?.getValue() as? DBList, id) &&
                containsFormula(player?.getElement("AlchKnwnRandRec")?.getValue() as? DBList, id),
            presentInIdentified = containsIdentified(module?.getElement("AlchIdent")?.getValue() as? DBList, id) &&
                containsIdentified(player?.getElement("AlchIdent")?.getValue() as? DBList, id)
        )
    }

    fun observedFormulaIds(session: GameSession): Set<String> {
        val ids = LinkedHashSet<String>()
        for (entry in session.getJournalData()?.entries.orEmpty()) {
            if (FormulaKind.fromCategory(entry.category) != null) ids.add(entry.entryId)
        }
        for (record in formulaRecords(modulePlayer(session), "AlchKnowledge") +
            formulaRecords(modulePlayer(session), "AlchKnwnRandRec") +
            formulaRecords(player(session), "AlchKnowledge") +
            formulaRecords(player(session), "AlchKnwnRandRec")) {
            formulaRecordId(record)?.let(ids::add)
        }
        for (record in identifiedRecords(modulePlayer(session)) + identifiedRecords(player(session))) {
            identifiedId(record)?.let { id ->
                if (FormulaKind.inferFromObservedId(id) != null) ids.add(id)
            }
        }
        return ids
    }

    fun currentGameTime(session: GameSession): Int = session.getJournalData()?.entries.orEmpty()
        .map { it.timeOfDay }
        .filter { it > 0 }
        .maxOrNull()
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: 0

    fun modulePlayer(session: GameSession): DBList? {
        val top = session.database?.getTopLevelStruct()?.getValue() as? DBList ?: return null
        val players = top.getElement("Mod_PlayerList")?.getValue() as? DBList ?: return null
        return players.getElement(0).getValue() as? DBList
    }

    fun player(session: GameSession): DBList? = session.playerDatabase?.getTopLevelStruct()?.getValue() as? DBList

    fun formulaRecords(player: DBList?, label: String): List<DBList> {
        val list = player?.getElement(label)?.getValue() as? DBList ?: return emptyList()
        return list.mapNotNull { it.getValue() as? DBList }
    }

    fun identifiedRecords(player: DBList?): List<DBList> {
        val list = player?.getElement("AlchIdent")?.getValue() as? DBList ?: return emptyList()
        return list.mapNotNull { it.getValue() as? DBList }
    }

    fun formulaRecordId(fields: DBList): String? = listOf("AlchRecipName", "AlchRecipTemp")
        .asSequence()
        .map { runCatching { fields.getString(it) }.getOrDefault("").trim() }
        .firstOrNull { it.isNotEmpty() }

    fun identifiedId(fields: DBList): String? = runCatching { fields.getString("AlchSubstance") }
        .getOrDefault("").trim().takeIf { it.isNotEmpty() }

    fun containsFormula(list: DBList?, id: String): Boolean {
        if (list == null) return false
        return list.any { element ->
            (element.getValue() as? DBList)?.let { formulaRecordId(it)?.lowercase(Locale.ROOT) == id } == true
        }
    }

    fun containsIdentified(list: DBList?, id: String): Boolean {
        if (list == null) return false
        return list.any { element ->
            (element.getValue() as? DBList)?.let { identifiedId(it)?.lowercase(Locale.ROOT) == id } == true
        }
    }

    fun anyRepresentation(session: GameSession, category: String, formulaId: String): Boolean {
        val kind = FormulaKind.fromCategory(category) ?: return false
        return state(session, kind, formulaId).let {
            it.presentInJournal || it.presentInKnowledge || it.presentInRandomRecipes || it.presentInIdentified
        }
    }
}

private data class JournalCommandSnapshot(
    val topLevel: DBElement,
    val journalDirty: Boolean
)

private fun journalCommandSnapshot(session: GameSession): JournalCommandSnapshot = JournalCommandSnapshot(
    topLevel = requireNotNull(session.getQuestDatabase()?.getTopLevelStruct()) { "No quest database is open" }.clone(),
    journalDirty = session.isJournalDirty()
)

private fun restoreJournalCommandSnapshot(session: GameSession, snapshot: JournalCommandSnapshot) {
    session.restoreJournalSnapshot(snapshot.topLevel, snapshot.journalDirty)
}

private fun normalizedMonsterId(value: String): String = value.trim().substringBefore('/').lowercase()

private fun monsterEntries(session: GameSession, monsterId: String): List<JournalEntry> =
    session.getJournalData()?.entries.orEmpty().filter {
        it.category.equals("bestiary", ignoreCase = true) && normalizedMonsterId(it.entryId) == monsterId
    }

class GrantMonsterKnowledgeCommand(
    private val monsterId: String,
    private val acquisitionTime: Int = 0
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val id = normalizedMonsterId(monsterId)
        if (id.isEmpty()) return listOf(LocalizedText("journal.command.chooseMonsterGrant"))
        if (session.getQuestDatabase() == null || session.getJournalData() == null) {
            return listOf(LocalizedText("journal.command.noJournalDatabase"))
        }
        if (monsterEntries(session, id).isNotEmpty()) {
            return listOf(
                LocalizedText("journal.command.duplicateGrant", JournalEntryNames.displayName(id))
            )
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = journalCommandSnapshot(session)
        val id = normalizedMonsterId(monsterId)
        session.addJournalEntry("bestiary", "$id/s/1", acquisitionTime)
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("journal.pending.grantMonster", JournalEntryNames.displayName(id)),
                EvidenceLevel.UNVERIFIED
            )
        ) {
            restoreJournalCommandSnapshot(session, snapshot)
        }
    }
}

class RemoveMonsterKnowledgeCommand(private val monsterId: String) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val id = normalizedMonsterId(monsterId)
        if (id.isEmpty()) return listOf(LocalizedText("journal.command.chooseMonsterRemove"))
        if (monsterEntries(session, id).isEmpty()) {
            return listOf(LocalizedText("journal.command.monsterNotPresent", JournalEntryNames.displayName(id)))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = journalCommandSnapshot(session)
        val id = normalizedMonsterId(monsterId)
        val entries = monsterEntries(session, id)
        session.removeJournalEntries(entries)
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("journal.pending.removeMonster", JournalEntryNames.displayName(id), entries.size),
                EvidenceLevel.UNVERIFIED
            )
        ) {
            restoreJournalCommandSnapshot(session, snapshot)
        }
    }
}

private data class FormulaCommandSnapshot(
    val moduleTop: DBElement,
    val playerTop: DBElement,
    val journalTop: DBElement,
    val journalDirty: Boolean
)

private fun formulaCommandSnapshot(session: GameSession): FormulaCommandSnapshot = FormulaCommandSnapshot(
    moduleTop = requireNotNull(session.database?.getTopLevelStruct()) { "No module database is open" }.clone(),
    playerTop = requireNotNull(session.playerDatabase?.getTopLevelStruct()) { "No player database is open" }.clone(),
    journalTop = requireNotNull(session.getQuestDatabase()?.getTopLevelStruct()) { "No quest database is open" }.clone(),
    journalDirty = session.isJournalDirty()
)

private fun restoreFormulaCommandSnapshot(session: GameSession, snapshot: FormulaCommandSnapshot) {
    requireNotNull(session.database) { "No module database is open" }.setTopLevelStruct(snapshot.moduleTop.clone())
    requireNotNull(session.playerDatabase) { "No player database is open" }.setTopLevelStruct(snapshot.playerTop.clone())
    session.restoreJournalSnapshot(snapshot.journalTop, snapshot.journalDirty)
}

private fun normalizedFormulaId(value: String): String = value.trim()

private fun formulaExistsAnywhere(session: GameSession, formulaId: String): Boolean {
    return FormulaKind.values().any { kind -> FormulaAccess.anyRepresentation(session, kind.category, formulaId) }
}

private fun formulaKind(category: String): FormulaKind? = FormulaKind.fromCategory(category)

/**
 * Grants one observed Formula without creating a source-book history record.
 * The duplicated module/player records are intentionally kept in lockstep;
 * Save files carry both copies and the game expects the same alchemy state in
 * each of them.
 */
class GrantFormulaCommand(
    private val category: String,
    private val formulaId: String,
    private val acquisitionTime: Int? = null
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val kind = formulaKind(category)
            ?: return listOf(LocalizedText("journal.command.unsupportedFormulaCategory", category))
        val id = normalizedFormulaId(formulaId)
        if (id.isEmpty()) return listOf(LocalizedText("journal.command.chooseFormulaGrant", LocalizedText(kind.messageKey)))
        if (session.getQuestDatabase() == null || session.getJournalData() == null) {
            return listOf(LocalizedText("journal.command.noJournalDatabase"))
        }
        if (FormulaAccess.modulePlayer(session) == null || FormulaAccess.player(session) == null) {
            return listOf(LocalizedText("journal.command.noPlayerRecords"))
        }
        if (formulaExistsAnywhere(session, id)) {
            return listOf(LocalizedText("journal.command.duplicateFormulaGrant", JournalEntryNames.displayName(id)))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = formulaCommandSnapshot(session)
        val kind = requireNotNull(formulaKind(category)) { "Unsupported Formula category: $category" }
        val id = normalizedFormulaId(formulaId)
        FormulaAccess.grant(session, kind, id, acquisitionTime ?: FormulaAccess.currentGameTime(session))
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("journal.pending.grantFormula", LocalizedText(kind.messageKey), JournalEntryNames.displayName(id)),
                EvidenceLevel.UNVERIFIED
            )
        ) {
            restoreFormulaCommandSnapshot(session, snapshot)
        }
    }
}

/**
 * Formula forgetting is deliberately advanced: no in-game forget operation
 * has been observed.  The constructor flag keeps direct command dispatches
 * behind the same explicit boundary as the Compose control.
 */
class RemoveFormulaCommand(
    private val category: String,
    private val formulaId: String,
    private val advancedJournalEditing: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val kind = formulaKind(category)
            ?: return listOf(LocalizedText("journal.command.unsupportedFormulaCategory", category))
        if (!advancedJournalEditing) {
            return listOf(LocalizedText("journal.command.removeRequiresAdvanced"))
        }
        val id = normalizedFormulaId(formulaId)
        if (id.isEmpty()) return listOf(LocalizedText("journal.command.chooseFormulaRemove"))
        if (!formulaExistsAnywhere(session, id)) {
            return listOf(LocalizedText("journal.command.formulaNotPresent", JournalEntryNames.displayName(id)))
        }
        if (session.getQuestDatabase() == null || FormulaAccess.modulePlayer(session) == null ||
            FormulaAccess.player(session) == null
        ) {
            return listOf(LocalizedText("journal.command.missingFormulaDatabases"))
        }
        if (kind.category.isEmpty()) return listOf(LocalizedText("journal.command.unsupportedFormulaCategory", category))
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = formulaCommandSnapshot(session)
        val kind = requireNotNull(formulaKind(category)) { "Unsupported Formula category: $category" }
        val id = normalizedFormulaId(formulaId)
        FormulaAccess.remove(session, kind, id)
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("journal.pending.removeFormula", LocalizedText(kind.messageKey), JournalEntryNames.displayName(id)),
                EvidenceLevel.DELIBERATELY_DANGEROUS
            )
        ) {
            restoreFormulaCommandSnapshot(session, snapshot)
        }
    }
}

private fun FormulaAccess.grant(session: GameSession, kind: FormulaKind, formulaId: String, acquisitionTime: Int) {
    val module = requireNotNull(modulePlayer(session)) { "No module Player record is open" }
    val player = requireNotNull(player(session)) { "No Player record is open" }
    val template = formulaRecordTemplate(module, player)
    addFormulaToPlayer(module, formulaId, template)
    addFormulaToPlayer(player, formulaId, template)
    session.addJournalEntry(kind.category, formulaId, acquisitionTime)
}

private fun FormulaAccess.remove(session: GameSession, kind: FormulaKind, formulaId: String) {
    for (player in listOfNotNull(modulePlayer(session), player(session))) {
        removeFormulaFromPlayer(player, formulaId)
    }
    val journalEntries = session.getJournalData()?.entries.orEmpty().filter {
        it.category.equals(kind.category, ignoreCase = true) && it.entryId.equals(formulaId, ignoreCase = true)
    }
    session.removeJournalEntries(journalEntries)
}

private fun formulaRecordTemplate(module: DBList, player: DBList): DBElement? {
    return sequenceOf(module, player)
        .flatMap { source ->
            sequenceOf("AlchKnowledge", "AlchKnwnRandRec").flatMap { label ->
                val list = source.getElement(label)?.getValue() as? DBList ?: return@flatMap emptySequence()
                list.asSequence()
            }
        }
        .firstOrNull { it.getValue() is DBList }
        ?.clone()
}

private fun addFormulaToPlayer(player: DBList, formulaId: String, template: DBElement?) {
    val identified = ensureAlchemyList(player, "AlchIdent")
    val randomRecipes = ensureAlchemyList(player, "AlchKnwnRandRec")
    val knowledge = ensureAlchemyList(player, "AlchKnowledge")

    val identifiedTemplate = identified.firstOrNull { it.getValue() is DBList }?.clone()
    identified.addElement((identifiedTemplate ?: canonicalIdentifiedRecord(player.environment)).also {
        val fields = it.getValue() as DBList
        fields.setString("AlchSubstance", formulaId)
    })

    fun addRecord(list: DBList) {
        val record = (template?.clone() ?: canonicalFormulaRecord(player.environment)).also {
            val fields = it.getValue() as DBList
            fields.setString("AlchBaseType", "E")
            fields.setInteger("Vitriol", fields.getInteger("Vitriol"), DBElement.WORD)
            fields.setInteger("Rebis", fields.getInteger("Rebis"), DBElement.WORD)
            fields.setInteger("Aether", fields.getInteger("Aether"), DBElement.WORD)
            fields.setInteger("Quebrith", fields.getInteger("Quebrith"), DBElement.WORD)
            fields.setInteger("Hydragenum", fields.getInteger("Hydragenum"), DBElement.WORD)
            fields.setInteger("Vermilion", fields.getInteger("Vermilion"), DBElement.WORD)
            fields.setInteger("Albedo", fields.getInteger("Albedo"), DBElement.WORD)
            fields.setInteger("Nigredo", fields.getInteger("Nigredo"), DBElement.WORD)
            fields.setInteger("Rubedo", fields.getInteger("Rubedo"), DBElement.WORD)
            fields.setString("AlchUniqueSubst", fields.getString("AlchUniqueSubst"))
            fields.setString("AlchRecipName", formulaId)
            fields.setString("AlchRecipTemp", formulaId)
            fields.setInteger("AlchRecIdx", fields.getInteger("AlchRecIdx"), DBElement.DWORD)
        }
        list.addElement(record)
    }
    addRecord(randomRecipes)
    addRecord(knowledge)
}

private fun removeFormulaFromPlayer(player: DBList, formulaId: String) {
    for (label in listOf("AlchIdent", "AlchKnwnRandRec", "AlchKnowledge")) {
        val list = player.getElement(label)?.getValue() as? DBList ?: continue
        for (index in list.getElementCount() - 1 downTo 0) {
            val fields = list.getElement(index).getValue() as? DBList ?: continue
            val id = if (label == "AlchIdent") FormulaAccess.identifiedId(fields) else FormulaAccess.formulaRecordId(fields)
            if (id?.equals(formulaId, ignoreCase = true) == true) list.removeElement(index)
        }
    }
}

private fun ensureAlchemyList(player: DBList, label: String): DBList {
    val existing = player.getElement(label)
    if (existing != null) {
        if (existing.getType() != DBElement.LIST) {
            throw IllegalStateException("Player field $label is not a list")
        }
        return existing.getValue() as DBList
    }
    val list = DBList(player.environment, 1)
    player.addElement(DBElement(DBElement.LIST, 0, label, list))
    return list
}

private fun canonicalIdentifiedRecord(environment: AppEnvironment): DBElement {
    val fields = DBList(environment, 1)
    fields.addElement(DBElement(DBElement.STRING, 0, "AlchSubstance", ""))
    return DBElement(DBElement.STRUCT, 0, "", fields)
}

private fun canonicalFormulaRecord(environment: AppEnvironment): DBElement {
    val fields = DBList(environment, 14)
    fields.addElement(DBElement(DBElement.STRING, 0, "AlchBaseType", "E"))
    for (label in listOf("Vitriol", "Rebis", "Aether", "Quebrith", "Hydragenum", "Vermilion", "Albedo", "Nigredo", "Rubedo")) {
        fields.addElement(DBElement(DBElement.WORD, 0, label, 0))
    }
    fields.addElement(DBElement(DBElement.STRING, 0, "AlchUniqueSubst", ""))
    fields.addElement(DBElement(DBElement.STRING, 0, "AlchRecipName", ""))
    fields.addElement(DBElement(DBElement.STRING, 0, "AlchRecipTemp", ""))
    fields.addElement(DBElement(DBElement.DWORD, 0, "AlchRecIdx", -1L))
    return DBElement(DBElement.STRUCT, 0, "", fields)
}
