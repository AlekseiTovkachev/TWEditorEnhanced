package app.tweditor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SaveSeamSupport {

  static final String FIXTURE_RESOURCE = "/saves/000007 - Территория Каэр Морхен-000.TheWitcherSave";
  static final String EXPECTED_SAVE_NAME = "000007 - Территория Каэр Морхен-000";

  private SaveSeamSupport() {
  }

  static AppEnvironment createEnvironment() {
    AppEnvironment environment = new AppEnvironment();
    environment.setFileSeparator(System.getProperty("file.separator"));
    environment.setLanguageID(3);
    return environment;
  }

  static File copyFixtureTo(Path directory) throws Exception {
    URL url = SaveSeamSupport.class.getResource(FIXTURE_RESOURCE);
    assertNotNull(url, "fixture not on classpath: " + FIXTURE_RESOURCE);
    Path target = directory.resolve(FIXTURE_RESOURCE.substring(FIXTURE_RESOURCE.lastIndexOf('/') + 1));
    try (InputStream in = SaveSeamSupport.class.getResourceAsStream(FIXTURE_RESOURCE)) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target.toFile();
  }

  static Loaded load(AppEnvironment environment, File saveFile, Path workDir) throws Exception {
    Loaded loaded = new Loaded(workDir, environment);
    loaded.saveDatabase = new SaveDatabase(environment, saveFile);
    loaded.saveDatabase.load();
    String saveName = loaded.saveDatabase.getName();
    loaded.saveDatabase.setSavePrefix(saveName + environment.getFileSeparator());
    loaded.smmName = "save_" + saveName.substring(0, 6) + ".smm";

    loaded.smmFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".smm").toFile();
    extract(loaded.saveDatabase.getEntry(loaded.smmName), loaded.smmFile);
    loaded.smmDatabase = new Database(environment, loaded.smmFile);
    loaded.smmDatabase.load();
    DBList smmList = (DBList) loaded.smmDatabase.getTopLevelStruct().getValue();
    String startingMod = smmList.getString("StartingMod");
    DBList questBaseList = (DBList) smmList.getElement("QuestBase_list").getValue();
    DBList questBaseFields = (DBList) questBaseList.getElement(0).getValue();
    loaded.questDBName = questBaseFields.getString("QuestBase");

    loaded.modName = startingMod + ".sav";
    loaded.modFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".sav").toFile();
    extract(loaded.saveDatabase.getEntry(loaded.modName), loaded.modFile);
    loaded.modDatabase = new ResourceDatabase(loaded.modFile);
    loaded.modDatabase.load();

    loaded.ifoFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".ifo").toFile();
    try (InputStream in = loaded.modDatabase.getEntry("module.ifo").getInputStream()) {
      Files.copy(in, loaded.ifoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    loaded.ifoDatabase = new Database(environment, loaded.ifoFile);
    loaded.ifoDatabase.load();
    DBList ifoList = (DBList) loaded.ifoDatabase.getTopLevelStruct().getValue();
    DBList playerList = (DBList) ifoList.getElement("Mod_PlayerList").getValue();
    loaded.player = (DBList) playerList.getElement(0).getValue();

    SaveEntry qdbEntry = loaded.saveDatabase.getEntry(loaded.questDBName + ".qdb");
    Database questDatabase = new Database(environment);
    try (InputStream in = qdbEntry.getInputStream()) {
      questDatabase.load(in);
    }
    DBList questDBList = (DBList) questDatabase.getTopLevelStruct().getValue();
    loaded.questCount = ((DBList) questDBList.getElement("Quests").getValue()).getElementCount();

    loaded.playerFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".utc").toFile();
    extract(loaded.saveDatabase.getEntry("player.utc"), loaded.playerFile);
    loaded.playerDatabase = new Database(environment, loaded.playerFile);
    loaded.playerDatabase.load();

    loaded.session.setSaveDatabase(loaded.saveDatabase);
    loaded.session.setDatabase(loaded.ifoDatabase);
    loaded.session.setModDatabase(loaded.modDatabase);
    loaded.session.setPlayerDatabase(loaded.playerDatabase);
    loaded.session.setSmmDatabase(loaded.smmDatabase);
    loaded.session.setSmmName(loaded.smmName);
    loaded.session.setModName(loaded.modName);

    return loaded;
  }

  static Map<String, Quest> questRecords(Loaded loaded) throws Exception {
    SaveEntry qdbEntry = loaded.saveDatabase.getEntry(loaded.questDBName + ".qdb");
    Database questDatabase = new Database(loaded.environment);
    try (InputStream in = qdbEntry.getInputStream()) {
      questDatabase.load(in);
    }
    DBList questDBList = (DBList) questDatabase.getTopLevelStruct().getValue();
    DBList quests = (DBList) questDBList.getElement("Quests").getValue();
    Map<String, Quest> records = new LinkedHashMap<>();
    for (int i = 0; i < quests.getElementCount(); i++) {
      DBList fields = (DBList) quests.getElement(i).getValue();
      String resourceName = fields.getString("File");
      SaveEntry qstEntry = loaded.saveDatabase.getEntry(resourceName + ".qst");
      Database qstDatabase = new Database(loaded.environment);
      try (InputStream in = qstEntry.getInputStream()) {
        qstDatabase.load(in);
      }
      records.put(resourceName, new Quest(resourceName, qstDatabase.getTopLevelStruct()));
    }
    return records;
  }

  static void save(Loaded loaded) throws Exception {
    loaded.ifoDatabase.save();
    ResourceEntry resourceEntry = new ResourceEntry("module.ifo", loaded.ifoFile);
    loaded.modDatabase.addEntry(resourceEntry);
    loaded.modDatabase.save();

    ResourceDatabase modDatabase = new ResourceDatabase(loaded.modDatabase.getPath());
    modDatabase.load();
    loaded.modDatabase = modDatabase;

    loaded.saveDatabase.addEntry(loaded.modName, loaded.modFile);
    loaded.playerDatabase.save();
    loaded.saveDatabase.addEntry("player.utc", loaded.playerFile);
    loaded.smmDatabase.save();
    loaded.saveDatabase.addEntry(loaded.smmName, loaded.smmFile);
    loaded.saveDatabase.save();

    SaveDatabase saveDatabase = new SaveDatabase(loaded.environment, loaded.saveDatabase.getPath());
    saveDatabase.load();
    loaded.saveDatabase = saveDatabase;
    loaded.session.setSaveDatabase(saveDatabase);
  }

  static java.util.Set<String> changedEntries(Map<String, Long> before, Map<String, Long> after) {
    java.util.Set<String> changed = new java.util.HashSet<>();
    for (String name : before.keySet()) {
      if (!before.get(name).equals(after.get(name))) {
        changed.add(name);
      }
    }
    return changed;
  }

  static Map<String, Long> entryDigests(SaveDatabase saveDatabase) throws Exception {
    Map<String, Long> digests = new LinkedHashMap<>();
    CRC32 crc = new CRC32();
    for (SaveEntry entry : saveDatabase.getEntries()) {
      crc.reset();
      try (InputStream in = entry.getInputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) > 0) {
          crc.update(buffer, 0, count);
        }
      }
      digests.put(entry.getResourceName(), crc.getValue());
    }
    return digests;
  }

  private static void extract(SaveEntry entry, File target) throws Exception {
    try (InputStream in = entry.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
      }
    }
  }

  static final class Loaded {
    final AppEnvironment environment;
    final GameSession session;
    SaveDatabase saveDatabase;
    Database smmDatabase;
    ResourceDatabase modDatabase;
    Database ifoDatabase;
    Database playerDatabase;
    DBList player;
    int questCount;
    String questDBName;
    String smmName;
    String modName;
    File smmFile;
    File modFile;
    File ifoFile;
    File playerFile;

    Loaded(Path workDir, AppEnvironment environment) {
      this.environment = environment;
      this.session = new GameSession(workDir.toFile());
    }
  }
}
