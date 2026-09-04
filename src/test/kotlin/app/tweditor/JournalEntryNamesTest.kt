package app.tweditor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class JournalEntryNamesTest {
    @Test
    fun curatedNamesWinOverFallbacks() {
        assertEquals("Dandelion", JournalEntryNames.displayName("jaskier/info"))
        assertEquals("Drowner", JournalEntryNames.displayName("drown1/w/1"))
        assertEquals("Mages", JournalEntryNames.displayName("wizards/info"))
        assertEquals("Kaer Morhen", JournalEntryNames.displayName("kaer/basic"))
    }

    @Test
    fun numberedEntriesGetFormattedNames() {
        assertEquals("Tutorial 35", JournalEntryNames.displayName("tutorial35"))
        assertEquals("Unique 1", JournalEntryNames.displayName("unique1"))
        assertEquals("Hydragenum2", JournalEntryNames.displayName("hydragenum2"),
            "ingredient names come from item templates, not a substance-level guess")
    }

    @Test
    fun unknownPrefixesFallBackToCapitalizedId() {
        assertEquals("Vesemir", JournalEntryNames.displayName("vesemir/info"))
        assertEquals("Mysterything", JournalEntryNames.displayName("mysterything/variant"))
    }
}
