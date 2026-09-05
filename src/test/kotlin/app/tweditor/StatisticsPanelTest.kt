package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Timeout(600)
class StatisticsPanelTest {
    @Test
    fun panelBuildsTheReadonlyTablesFromTheFixtureSave(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val panel = StatisticsPanel(loaded.session)
        panel.setFields(loaded.player!!)

        val data = panel.statistics()
        assertNotNull(data)
        assertEquals(6, data!!.totalKills)

        val kills = panel.killsTableModel()
        assertEquals(2, kills.rowCount)
        assertEquals("q0001_band01", kills.getValueAt(0, 0))
        assertEquals(5, kills.getValueAt(0, 1))

        val acts = panel.actsTableModel()
        assertTrue(acts.rowCount > 0)
        assertEquals("Prologue", acts.getValueAt(0, 0))
        assertEquals(4, acts.getValueAt(0, 1))
        val actRows = (0 until acts.rowCount).map { acts.getValueAt(it, 0) }
        assertTrue(actRows.contains("Act I"), "act rows: " + actRows)
    }

    @Test
    fun theKillTableIsCappedAtTenRows(@TempDir tempDir: Path) {
        val saves = SaveSeamSupport.localSaves()
        assumeTrue(saves.isNotEmpty(), "no local saves available")
        val work = SaveSeamSupport.tempCopy(saves.first())
        val loaded = SaveSeamSupport.load(environment, work, tempDir)

        val panel = StatisticsPanel(loaded.session)
        panel.setFields(loaded.player!!)

        val data = panel.statistics()!!
        assertTrue(data.totalKills > 0, "local save must hold kills")
        assertTrue(panel.killsTableModel().rowCount <= 10)
    }

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
