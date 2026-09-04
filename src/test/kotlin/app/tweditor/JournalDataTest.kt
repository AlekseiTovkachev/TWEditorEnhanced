package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class JournalDataTest {
    @Test
    fun journalDataIsParsedFromTheQuestDatabase(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val journalData = loaded.session.getJournalData()

        assertTrue(journalData != null, "the session must carry the parsed journal data")
        assertEquals("Prolog", journalData!!.storyPhase)
        assertEquals(13, journalData.entries.size)
        assertEquals(listOf("q0001"), journalData.trackedQuests)
    }

    @Test
    fun journalCategoriesGroupCaseInsensitively(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val journalData = loaded.session.getJournalData()!!

        assertEquals(3, journalData.entriesInCategory("tutorial").size,
            "tutorial01, tutorial03 and Tutorial35 must land in one category despite case")
        assertEquals(1, journalData.entriesInCategory("character").size)
        assertEquals(1, journalData.entriesInCategory("place").size)
        assertEquals(1, journalData.entriesInCategory("unique").size)
        assertEquals(1, journalData.entriesInCategory("hidden").size)
        assertEquals(6, journalData.entries.count { SUBSTANCE_CATEGORIES.contains(it.category) })
        assertTrue(journalData.entries.all { !it.isRead }, "every fixture journal entry is unread")
    }

    @Test
    fun sessionQuestsIncludeTheBestiaryRecords(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val quests = loaded.session.getQuests()
        assertTrue(quests != null, "the session must carry the quest records")
        assertEquals(133, quests!!.size,
            "137 quest records exist; 4 (p_init, the q-tech subquests) carry no localized name and are dropped, matching LoadFile")
        val bestiary = quests.filter { it.getResourceName().startsWith("q9") }
        assertEquals(36, bestiary.size)
        assertTrue(bestiary.all { it.questName.isNotEmpty() })
    }

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }

        private val SUBSTANCE_CATEGORIES = setOf("hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol")
    }
}
