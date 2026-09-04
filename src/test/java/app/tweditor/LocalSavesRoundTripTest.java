package app.tweditor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalSavesRoundTripTest {

  static AppEnvironment environment;

  @BeforeAll
  static void init() {
    environment = SaveSeamSupport.createEnvironment();
  }

  @Test
  void localSavesRoundTripWithIdenticalFacts(@TempDir Path tempDir) throws Exception {
    File savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile();
    File[] saves = savesDir.listFiles((dir, name) -> name.endsWith(".TheWitcherSave"));
    assumeTrue(saves != null && saves.length > 0,
        "no local saves in '" + savesDir + "' - drop *.TheWitcherSave files there to exercise them (they are gitignored and stay local)");

    for (int i = 0; i < saves.length; i++) {
      Path workDir = Files.createDirectory(tempDir.resolve("save-" + i));
      File save = Files.copy(saves[i].toPath(), workDir.resolve(saves[i].getName())).toFile();
      SaveSeamSupport.Loaded loaded = SaveSeamSupport.load(environment, save, workDir);
      int questCount = loaded.questCount;
      int experience = loaded.player.getInteger("Experience");
      int hitPoints = loaded.player.getInteger("CurrentHitPoints");

      Map<String, Long> before = SaveSeamSupport.entryDigests(loaded.saveDatabase);
      SaveSeamSupport.save(loaded);
      Map<String, Long> after = SaveSeamSupport.entryDigests(loaded.saveDatabase);

      assertEquals(before.keySet(), after.keySet(), save.getName());
      Set<String> rewritten = SaveSeamSupport.changedEntries(before, after);
      Set<String> allowedToChange = Set.of(loaded.modName, "player.utc", loaded.smmName);
      assertTrue(allowedToChange.containsAll(rewritten),
          save.getName() + ": entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten);

      SaveSeamSupport.Loaded reloaded = SaveSeamSupport.load(environment, save, workDir);
      assertEquals(questCount, reloaded.questCount, save.getName());
      assertEquals(experience, reloaded.player.getInteger("Experience"), save.getName());
      assertEquals(hitPoints, reloaded.player.getInteger("CurrentHitPoints"), save.getName());
      assertTrue(reloaded.player.getInteger("Gold") >= 0, save.getName());
    }
  }
}
