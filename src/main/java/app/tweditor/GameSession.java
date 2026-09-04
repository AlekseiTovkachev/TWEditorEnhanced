package app.tweditor;

import java.io.File;
import java.util.List;

/**
 * Owns all state for one loaded save archive: the save database itself, the
 * structures extracted from it, the quest records, entry names, and the
 * modified flags. Created empty by the GUI, populated by LoadFile, consumed
 * by SaveFile and the panels.
 */
public class GameSession
{
  private final File smmFile;
  private final File databaseFile;
  private final File modFile;
  private final File playerFile;

  private SaveDatabase saveDatabase;
  private Database database;
  private ResourceDatabase modDatabase;
  private Database playerDatabase;
  private Database smmDatabase;
  private String smmName;
  private String modName;
  private String playerName;
  private List<Quest> quests;
  private boolean dataModified;
  private boolean dataChanging;

  public GameSession(File tmpDir)
  {
    this.smmFile = new File(tmpDir, "TWEditor.smm");
    this.databaseFile = new File(tmpDir, "TWEditor.ifo");
    this.modFile = new File(tmpDir, "TWEditor.mod");
    this.playerFile = new File(tmpDir, "TWEditor.player");
  }

  public SaveDatabase getSaveDatabase()
  {
    return this.saveDatabase;
  }

  public void setSaveDatabase(SaveDatabase saveDatabase)
  {
    this.saveDatabase = saveDatabase;
  }

  public Database getDatabase()
  {
    return this.database;
  }

  public void setDatabase(Database database)
  {
    this.database = database;
  }

  public ResourceDatabase getModDatabase()
  {
    return this.modDatabase;
  }

  public void setModDatabase(ResourceDatabase modDatabase)
  {
    this.modDatabase = modDatabase;
  }

  public Database getPlayerDatabase()
  {
    return this.playerDatabase;
  }

  public void setPlayerDatabase(Database playerDatabase)
  {
    this.playerDatabase = playerDatabase;
  }

  public Database getSmmDatabase()
  {
    return this.smmDatabase;
  }

  public void setSmmDatabase(Database smmDatabase)
  {
    this.smmDatabase = smmDatabase;
  }

  public String getSmmName()
  {
    return this.smmName;
  }

  public void setSmmName(String smmName)
  {
    this.smmName = smmName;
  }

  public String getModName()
  {
    return this.modName;
  }

  public void setModName(String modName)
  {
    this.modName = modName;
  }

  public String getPlayerName()
  {
    return this.playerName;
  }

  public void setPlayerName(String playerName)
  {
    this.playerName = playerName;
  }

  public File getSmmFile()
  {
    return this.smmFile;
  }

  public File getDatabaseFile()
  {
    return this.databaseFile;
  }

  public File getModFile()
  {
    return this.modFile;
  }

  public File getPlayerFile()
  {
    return this.playerFile;
  }

  public List<Quest> getQuests()
  {
    return this.quests;
  }

  public void setQuests(List<Quest> quests)
  {
    this.quests = quests;
  }

  public boolean isDataModified()
  {
    return this.dataModified;
  }

  public void setDataModified(boolean dataModified)
  {
    this.dataModified = dataModified;
  }

  public boolean isDataChanging()
  {
    return this.dataChanging;
  }

  public void setDataChanging(boolean dataChanging)
  {
    this.dataChanging = dataChanging;
  }

  public void close()
  {
    this.database = null;
    this.modDatabase = null;
    this.saveDatabase = null;
    this.dataModified = false;
  }
}
