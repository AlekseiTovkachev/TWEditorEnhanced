package app.tweditor;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialSaveGoldenTest {

  @BeforeAll
  static void init() {
    SaveSeamSupport.initSeam();
  }

  @Test
  void fixtureIsRecognizedAsAValidSaveArchive(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);

    assertEquals(SaveSeamSupport.EXPECTED_SAVE_NAME, loaded.saveDatabase.getName());
    assertTrue(loaded.saveDatabase.getName().matches("\\d{6} .*"),
        "save name must start with six digits and a space for the editor to accept it");
    assertEquals(143, loaded.saveDatabase.getEntries().size());

    SaveEntry modEntry = loaded.saveDatabase.getEntry(loaded.modName);
    SaveEntry smmEntry = loaded.saveDatabase.getEntry(loaded.smmName);
    assertNotNull(modEntry, "module save entry missing");
    assertNotNull(smmEntry, "smm save entry missing");
    assertTrue(modEntry.isCompressed(), "module .sav entries are zlib-compressed");
    assertNotNull(loaded.saveDatabase.getEntry("player.utc"));
    assertNotNull(loaded.saveDatabase.getEntry("savenfo.txt"));
    assertNotNull(loaded.saveDatabase.getEntry(loaded.questDBName + ".qdb"));
    assertNotNull(loaded.saveDatabase.getEntry("q0001.qst"));
    assertEquals("kaer_morhen.sav", loaded.modName);
    assertEquals("save_000007.smm", loaded.smmName);
  }

  @Test
  void parsedPlayerFactsMatchTheFixture(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);

    assertEquals(0, loaded.player.getInteger("ExpLevel"));
    assertEquals(30, loaded.player.getInteger("Experience"));
    assertEquals(0, loaded.player.getInteger("Gold"));
    assertEquals(248, loaded.player.getInteger("CurrentHitPoints"));
    assertEquals(25, loaded.player.getInteger("CurrentEndurance"));
    assertEquals(0, loaded.player.getInteger("CurrentToxicity"));
    assertEquals(0, loaded.player.getInteger("TalentBronze"));
    assertEquals(0, loaded.player.getInteger("TalentSilver"));
    assertEquals(0, loaded.player.getInteger("TalentGold"));
    assertEquals(0, ((DBList) loaded.player.getElement("ItemList").getValue()).getElementCount());

    DBList playerTop = (DBList) loaded.playerDatabase.getTopLevelStruct().getValue();
    assertEquals("Wiedzmin", playerTop.getString("Tag"));
    assertEquals(30, playerTop.getInteger("Experience"));
    assertEquals(0, playerTop.getInteger("Gold"));
  }

  @Test
  void parsedQuestFactsMatchTheFixture(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);
    Map<String, Quest> records = SaveSeamSupport.questRecords(loaded);

    assertEquals(137, loaded.questCount);
    assertEquals(137, records.size());
    assertEquals("Defending Kaer Morhen", records.get("q0001").getQuestName());
    assertEquals(1, records.get("q0001").getQuestState());
    assertEquals(2, records.get("p_init").getQuestState());
    assertEquals("A Potion for Triss", records.get("q0002").getQuestName());
    assertEquals(0, records.get("q0002").getQuestState());
  }

  @Test
  void roundTripLeavesUntouchedEntriesByteIdentical(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveDatabase pristine = new SaveDatabase(save);
    pristine.load();
    Map<String, Long> before = SaveSeamSupport.entryDigests(pristine);

    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);
    SaveSeamSupport.save(loaded);

    SaveDatabase repacked = new SaveDatabase(save);
    repacked.load();
    Map<String, Long> after = SaveSeamSupport.entryDigests(repacked);

    assertEquals(before.keySet(), after.keySet());
    Set<String> rewritten = SaveSeamSupport.changedEntries(before, after);
    assertEquals(Set.of(loaded.modName, "player.utc", loaded.smmName), rewritten,
        "only the module .sav container, player.utc and the .smm file are rewritten by a save; every other entry must be byte-identical");
  }

  @Test
  void roundTripReparsesWithSameFacts(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);
    SaveSeamSupport.save(loaded);

    SaveSeamSupport.Loaded reloaded = SaveSeamSupport.load(save, tempDir);
    assertEquals(0, reloaded.player.getInteger("ExpLevel"));
    assertEquals(30, reloaded.player.getInteger("Experience"));
    assertEquals(0, reloaded.player.getInteger("Gold"));
    assertEquals(248, reloaded.player.getInteger("CurrentHitPoints"));
    assertEquals(25, reloaded.player.getInteger("CurrentEndurance"));
    assertEquals(137, reloaded.questCount);

    Map<String, Quest> records = SaveSeamSupport.questRecords(reloaded);
    assertEquals("Defending Kaer Morhen", records.get("q0001").getQuestName());
    assertEquals(1, records.get("q0001").getQuestState());
    assertEquals(2, records.get("p_init").getQuestState());
    assertEquals("A Potion for Triss", records.get("q0002").getQuestName());
  }

  @Test
  void editedGoldPersistsThroughRoundTrip(@TempDir Path tempDir) throws Exception {
    File save = SaveSeamSupport.copyFixtureTo(tempDir);
    SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(save, tempDir);

    loaded.player.setInteger("Gold", 500);
    SaveSeamSupport.save(loaded);

    SaveSeamSupport.Loaded reloaded = SaveSeamSupport.load(save, tempDir);
    assertEquals(500, reloaded.player.getInteger("Gold"));
    assertEquals(30, reloaded.player.getInteger("Experience"));
    assertEquals(248, reloaded.player.getInteger("CurrentHitPoints"));
    assertEquals(137, reloaded.questCount);
  }
}
