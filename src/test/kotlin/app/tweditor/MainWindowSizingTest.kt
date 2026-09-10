package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainWindowSizingTest {
    @Test
    fun defaultWindowKeepsTheInventoryWideAndUsable() {
        assertEquals(MainWindowDimensions(1440, 900), mainWindowDimensions(null))
        assertEquals(MainWindowDimensions(1440, 900), mainWindowDimensions("960,700"))
    }

    @Test
    fun aLargerSavedWindowSizeIsPreserved() {
        assertEquals(MainWindowDimensions(1680, 1050), mainWindowDimensions("1680,1050"))
    }
}
