package app.tweditor

/** The eight game-facing Journal sections, kept in the game's display order. */
enum class JournalSection(val displayName: String, val messageKey: String) {
    QUESTS("Quests", "journal.section.quests"),
    CHARACTERS("Characters", "journal.category.characters"),
    LOCATIONS("Locations", "journal.category.locations"),
    MONSTERS("Monsters", "journal.section.monsters"),
    FORMULA("Formula", "journal.section.formula"),
    INGREDIENTS("Ingredients", "journal.category.ingredients"),
    GLOSSARY("Glossary", "journal.category.glossary"),
    TUTORIALS("Tutorials", "journal.category.tutorials")
}

data class JournalEntryView(
    val category: String,
    val entryId: String,
    val displayName: String,
    val read: Boolean,
    val timeOfDay: Long,
    val section: JournalSection?,
    val advancedEditable: Boolean = false,
    val advancedEditReason: LocalizedText? = null
) {
    val rawReference: String get() = "$category:$entryId"
}

data class JournalQuestView(
    val resourceName: String,
    val name: String,
    val stateKey: String,
    val tracked: Boolean,
    val currentPhase: Int,
    val rootPhases: List<QuestPhaseTarget>
)

/** Immutable facts used by the Compose Journal workspace. */
data class JournalViewState(
    val storyPhase: String,
    val trackedQuestNames: List<String>,
    val quests: List<JournalQuestView>,
    val entriesBySection: Map<JournalSection, List<JournalEntryView>>,
    val unresolvedEntries: List<JournalEntryView>,
    val monsterTargets: List<MonsterKnowledgeTarget>,
    val formulaTargets: List<FormulaKnowledgeTarget>,
    private val catalogBySection: Map<JournalSection, List<JournalEntryView>> = emptyMap()
) {
    fun entries(section: JournalSection): List<JournalEntryView> = entriesBySection[section].orEmpty()

    /** Sections whose catalog is known, so locked entries can be offered. */
    val catalogableSections: Set<JournalSection> =
        setOf(JournalSection.CHARACTERS, JournalSection.LOCATIONS, JournalSection.INGREDIENTS, JournalSection.GLOSSARY, JournalSection.TUTORIALS)

    fun catalog(section: JournalSection): List<JournalEntryView> = catalogBySection[section].orEmpty()

    companion object {
        private val ingredientCategories = setOf(
            "hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol"
        )
        private val formulaCategories = setOf("recipe", "recipe_oil", "recipe_bomb")
        private val tutorialCategories = setOf("tutorial", "alchemy")

        fun from(session: GameSession): JournalViewState = from(session, null)

        fun from(session: GameSession, environment: AppEnvironment?): JournalViewState {
            val journal = session.getJournalData()
            val tracked = journal?.trackedQuests?.toList().orEmpty()
            val phaseTargetsByQuest = QuestPhaseAccess.targets(session).groupBy { it.questResourceName.lowercase() }
            val quests = session.getQuests().orEmpty()
                .map { quest ->
                    val rootPhases = phaseTargetsByQuest[quest.getResourceName().lowercase()].orEmpty()
                    JournalQuestView(
                        resourceName = quest.getResourceName(),
                        name = quest.questName.ifEmpty { quest.getResourceName() },
                        stateKey = questStateKey(quest.questState),
                        tracked = tracked.any { it.equals(quest.questName, ignoreCase = true) },
                        currentPhase = rootPhases.firstOrNull { it.current }?.phaseId
                            ?: QuestPhaseAccess.shape(quest)?.currentPhase
                            ?: -1,
                        rootPhases = rootPhases
                    )
                }
                .sortedWith(compareBy<JournalQuestView, String>(String.CASE_INSENSITIVE_ORDER) { it.stateKey }.thenBy { it.name.lowercase() })

            val grouped = JournalSection.entries.associateWith { ArrayList<JournalEntryView>() }
            val unresolved = ArrayList<JournalEntryView>()
            val existingKeys = HashSet<String>()
            val catalogRows = JournalCatalog.catalogEntries(environment)
            val catalogPictures = catalogRows.associate {
                it.category.lowercase() + ":" + it.entryId.lowercase() to it.picture
            }
            for (entry in journal?.entries.orEmpty()) {
                existingKeys.add(entry.category.lowercase() + ":" + entry.entryId.lowercase())
                val view = entry.toView(session, environment, catalogPictures)
                val section = sectionFor(entry.category)
                if (section == null) unresolved.add(view) else grouped.getValue(section).add(view.copy(section = section))
            }

            // The installed game's catalog, so sections can offer entries the
            // Save does not have yet (locked knowledge to add to the character).
            val catalogBySection = HashMap<JournalSection, List<JournalEntryView>>()
            catalogRows
                .groupBy { sectionForCatalog(it.category, it.picture) }
                .filterKeys { it != null }
                .forEach { (section, rows) ->
                    val views = rows.filter { existingKeys.contains(it.category.lowercase() + ":" + it.entryId.lowercase()).not() }
                        .map { row ->
                            JournalEntryView(
                                category = row.category,
                                entryId = row.entryId,
                                displayName = JournalCatalog.rowLabel(environment, row.category, row.entryId, row.picture),
                                read = false,
                                timeOfDay = 0,
                                section = section,
                                advancedEditable = AdvancedJournalCategories.supports(row.category)
                            )
                        }
                        .sortedWith(compareBy<JournalEntryView, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }.thenBy { it.rawReference })
                    if (section != null && views.isNotEmpty()) {
                        catalogBySection[section] = views
                    }
                }

            val immutableGroups = grouped.mapValues { (_, values) ->
                values.sortedWith(compareBy<JournalEntryView, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }.thenBy { it.rawReference })
            }
            return JournalViewState(
                storyPhase = journal?.storyPhase.orEmpty(),
                trackedQuestNames = tracked,
                quests = quests,
                entriesBySection = immutableGroups,
                unresolvedEntries = unresolved.sortedWith(
                    compareBy<JournalEntryView, String>(String.CASE_INSENSITIVE_ORDER) { it.category }.thenBy { it.entryId }
                ),
                monsterTargets = JournalCatalog.monsterTargets(environment, journal?.entries.orEmpty()),
                formulaTargets = JournalCatalog.formulaTargets(environment, session),
                catalogBySection = catalogBySection
            )
        }

        private fun JournalEntry.toView(
            session: GameSession,
            environment: AppEnvironment?,
            catalogPictures: Map<String, String>
        ): JournalEntryView {
            val editability = AdvancedJournalAccess.editability(session, category, entryId)
            val picture = catalogPictures[category.lowercase() + ":" + entryId.lowercase()].orEmpty()
            return JournalEntryView(
                category = category,
                entryId = entryId,
                displayName = JournalCatalog.rowLabel(environment, category, entryId, picture),
                read = isRead,
                timeOfDay = timeOfDay,
                section = sectionFor(category),
                advancedEditable = editability.supported,
                advancedEditReason = editability.reason
            )
        }

        private fun sectionFor(category: String): JournalSection? = when (category.lowercase()) {
            "character" -> JournalSection.CHARACTERS
            "place" -> JournalSection.LOCATIONS
            "bestiary" -> JournalSection.MONSTERS
            in formulaCategories -> JournalSection.FORMULA
            in ingredientCategories -> JournalSection.INGREDIENTS
            "info" -> JournalSection.GLOSSARY
            in tutorialCategories -> JournalSection.TUTORIALS
            // `unique` is catalog-dependent: without its journal.2da row we
            // must show it as unresolved rather than guess a destination.
            else -> null
        }

        /**
         * Catalog rows route by the row itself, so the game's `unique`
         * category can land in Ingredients or Glossary by its picture, the
         * way the old Knowledge workspace sorted them.
         */
        private fun sectionForCatalog(category: String, picture: String): JournalSection? =
            when (category.lowercase()) {
                "unique" -> if (picture.startsWith("je_ingr", ignoreCase = true)) JournalSection.INGREDIENTS else JournalSection.GLOSSARY
                else -> sectionFor(category)
            }

        private fun questStateKey(state: Int): String = when (state) {
            1 -> "journal.quest.state.started"
            2 -> "journal.quest.state.completed"
            3 -> "journal.quest.state.failed"
            else -> "journal.quest.state.notStarted"
        }
    }
}
