package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class StatisticsDataTest {
    @Test
    fun fixtureKillListFeedsTotalsAndTopOpponents(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val data = StatisticsData.compute(killTags(loaded), questsOf(loaded), journalOf(loaded))

        assertEquals(6, data.totalKills)
        assertEquals(2, data.distinctOpponents)
        assertEquals(listOf(KillCount("q0001_band01", 5), KillCount("q0001_band01ab;q0001_band01", 1)),
            data.topKills)
    }

    @Test
    fun fixtureQuestsSortIntoTheirActs(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val data = StatisticsData.compute(killTags(loaded), questsOf(loaded), journalOf(loaded))

        assertTrue(data.acts.isNotEmpty())
        assertEquals(133, data.acts.sumOf { it.known },
            "every session quest must land in an act bucket (4 of the fixture's 137 qdb quests have empty names and are not in the session)")
        assertEquals("prologue1", data.acts.first().act)
        val acts = data.acts.associateBy { it.act }
        assertEquals(4, acts["prologue1"]!!.known)
        assertEquals(14, acts["act1"]!!.known)
        assertEquals(44, acts["act2"]!!.known)
        assertEquals(31, acts["act3"]!!.known)
        assertEquals(18, acts["act4"]!!.known)
        assertEquals(19, acts["act5"]!!.known)
        assertEquals(3, acts["epilogue"]!!.known)
        assertTrue(acts.keys.none { it == "other" })
    }

    @Test
    fun fixtureJournalBracketsInGameTime(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val data = StatisticsData.compute(killTags(loaded), questsOf(loaded), journalOf(loaded))

        assertTrue(data.journalEntries > 0)
        assertTrue(data.firstEntryTOD > 0, "fixture journal entries carry a game-written EntryTOD")
        assertTrue(data.lastEntryTOD >= data.firstEntryTOD)
        assertEquals(data.journalEntries, data.days.sumOf { it.count },
            "every timed entry lands in exactly one day bucket")
        assertTrue(data.days.first().day <= data.days.last().day)
    }

    @Test
    fun computeSurvivesAnEmptySession() {
        val data = StatisticsData.compute(emptyList(), emptyList(), emptyList())

        assertEquals(0, data.totalKills)
        assertEquals(0, data.journalEntries)
        assertTrue(data.topKills.isEmpty())
        assertTrue(data.acts.isEmpty())
        assertTrue(data.days.isEmpty())
    }

    @Test
    fun dayBucketsGroupEntriesByInGameDay() {
        val journal = listOf(
            JournalEntry("quest", "a", true, 100L),
            JournalEntry("quest", "b", true, 86_399L),
            JournalEntry("quest", "c", true, 86_400L),
            JournalEntry("quest", "d", true, 172_800L))
        val data = StatisticsData.compute(emptyList(), emptyList(), journal)

        assertEquals(3, data.days.size)
        val days = data.days.associateBy { it.day }
        assertEquals(2, days[0]!!.count)
        assertEquals(100L, days[0]!!.firstTOD)
        assertEquals(86_399L, days[0]!!.lastTOD)
        assertEquals(1, days[1]!!.count)
        assertEquals(1, days[2]!!.count)
    }

    @Test
    fun localSaveStatisticsStayConsistent(@TempDir tempDir: Path) {
        val saves = SaveSeamSupport.localSaves()
        assumeTrue(saves.isNotEmpty(),
            "no local saves in '" + System.getProperty("tweditor.localSaves", ".local-saves") + "' - drop *.TheWitcherSave files there to exercise them")

        for (save in saves) {
            val work = SaveSeamSupport.tempCopy(save)
            val loaded = SaveSeamSupport.load(environment, work, tempDir)
            val data = StatisticsData.compute(killTags(loaded), questsOf(loaded), journalOf(loaded))

            val expected = StatisticExpectations.from(loaded)
            assertEquals(expected.killCount, data.totalKills, save.getName())
            assertEquals(expected.distinctTags, data.distinctOpponents, save.getName())
            assertEquals(expected.questCount, data.acts.sumOf { it.known }, save.getName())
            assertTrue(data.topKills.firstOrNull()?.count == data.topKills.maxOfOrNull { it.count },
                save.getName() + ": top kills are sorted by count")
            assertTrue(data.journalEntries > 0, save.getName())
            assertTrue(data.firstEntryTOD >= 0 && data.lastEntryTOD >= data.firstEntryTOD, save.getName())
        }
    }

    private class StatisticExpectations private constructor(
        val killCount: Int, val distinctTags: Int, val questCount: Int) {
        companion object {
            fun from(loaded: SaveSeamSupport.Loaded): StatisticExpectations {
                val playerTop = loaded.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList
                val kills = playerTop.getElement("KillList")?.getValue() as? DBList
                val tags = HashSet<String>()
                var count = 0
                if (kills != null) {
                    for (kill in kills) {
                        count++
                        tags.add(((kill.getValue() as DBList).getString("OppTag")).lowercase())
                    }
                }
                return StatisticExpectations(count, tags.size, loaded.session.getQuests()!!.size)
            }
        }
    }

    private fun killTags(loaded: SaveSeamSupport.Loaded): List<String> {
        val playerTop = loaded.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        val kills = playerTop.getElement("KillList")?.getValue() as? DBList ?: return emptyList()
        val tags = ArrayList<String>()
        for (kill in kills) {
            val fields = kill.getValue() as? DBList ?: continue
            val tag = fields.getString("OppTag")
            if (tag.isNotEmpty()) {
                tags.add(tag)
            }
        }
        return tags
    }

    private fun questsOf(loaded: SaveSeamSupport.Loaded): List<Quest> = loaded.session.getQuests()!!

    private fun journalOf(loaded: SaveSeamSupport.Loaded): List<JournalEntry> =
        loaded.session.getJournalData()!!.entries

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
