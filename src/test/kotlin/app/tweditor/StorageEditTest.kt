package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Storage mutations are exercised through the same command seam used by Compose. */
@Timeout(300)
class StorageEditTest {
    @Test
    fun storageItemsAreReadFromTheSmm(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val record = StorageAccess.findStorageRecord(
            loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        ) ?: error("the committed storage fixture must contain an initialized storage record")

        val itemList = record.getElement("ItemList")!!.getValue() as DBList
        assertTrue(itemList.getElementCount() > 0, "the storage fixture must contain stored items")
    }

    @Test
    fun sortingTheChestPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val itemList = requireNotNull(StorageAccess.itemList(loaded.session))
        val beforeSequence = sequence(itemList)
        assertTrue(beforeSequence.size >= 2, "the storage fixture needs at least two items to sort")

        val controller = EditorCommandController(loaded.session)
        val result = controller.dispatch(SortStorageCommand())
        assertTrue(result is EditorCommandResult.Applied)
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(repacked),
            setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        )

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val afterList = requireNotNull(StorageAccess.itemList(reloaded.session))
        assertEquals(beforeSequence.sorted(), sequence(afterList).sorted(), "sorting must not add or lose items")
        assertEquals(
            StorageAccess.sortedElements(afterList).map(::itemKey),
            afterList.toList().map(::itemKey),
            "the persisted order must match the storage command's ordering"
        )
    }

    private fun sequence(items: DBList): List<String> = items.toList().map(::itemKey)

    private fun itemKey(element: DBElement): String =
        StorageAccess.resref(element) + "#" + StorageAccess.stackSize(element)
}
