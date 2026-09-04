package app.tweditor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public class LoadFile extends Thread
{
  private final ProgressDialog progressDialog;
  private final GameSession session;
  private final AppEnvironment environment;
  private File file;
  private boolean loadSuccessful = false;

  public LoadFile(ProgressDialog dialog, GameSession session, AppEnvironment environment, File file)
  {
    this.progressDialog = dialog;
    this.session = session;
    this.environment = environment;
    this.file = file;
  }

  public void run()
  {
    InputStream in = null;
    FileOutputStream out = null;
    try
    {
      SaveDatabase saveDatabase = new SaveDatabase(this.environment, this.file);
      saveDatabase.load();
      this.progressDialog.updateProgress(25);
      String saveName = saveDatabase.getName();
      saveDatabase.setSavePrefix(saveName + this.environment.getFileSeparator());

      int sep = saveName.indexOf(' ');
      if ((sep != 6) || (!Character.isDigit(saveName.charAt(0)))) {
        throw new DBException("Save name is not formatted correctly");
      }
      this.session.setSmmName("save_" + saveName.substring(0, 6) + ".smm");
      SaveEntry saveEntry = saveDatabase.getEntry(this.session.getSmmName());
      if (saveEntry == null) {
        throw new DBException("Save does not contain " + this.session.getSmmName());
      }
      in = saveEntry.getInputStream();
      if (this.session.getSmmFile().exists()) {
        this.session.getSmmFile().delete();
      }
      byte[] buffer = new byte[4096];
      out = new FileOutputStream(this.session.getSmmFile());
      int count;
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
      }
      in.close();
      in = null;
      out.close();
      out = null;
      Database smmDatabase = new Database(this.environment, this.session.getSmmFile());
      smmDatabase.load();
      this.progressDialog.updateProgress(35);

      DBList list = (DBList)smmDatabase.getTopLevelStruct().getValue();
      String startingMod = list.getString("StartingMod");
      if (startingMod.length() == 0) {
        throw new DBException("StartingMod not found in SMM database");
      }
      DBElement element = list.getElement("QuestBase_list");
      if ((element == null) || (element.getType() != 15)) {
        throw new DBException("QuestBaseList not found in SMM database");
      }
      DBList questList = (DBList)element.getValue();
      if (questList.getElementCount() == 0) {
        throw new DBException("No quest list found in SMM database");
      }
      DBList fieldList = (DBList)questList.getElement(0).getValue();
      String questDBName = fieldList.getString("QuestBase");
      if (questDBName.length() == 0) {
        throw new DBException("No quest database name found in SMM database");
      }

      this.session.setModName(startingMod + ".sav");
      saveEntry = saveDatabase.getEntry(this.session.getModName());
      if (saveEntry == null) {
        throw new DBException("Save does not contain " + this.session.getModName());
      }
      in = saveEntry.getInputStream();
      if (this.session.getModFile().exists()) {
        this.session.getModFile().delete();
      }

      buffer = new byte[4096];
      out = new FileOutputStream(this.session.getModFile());
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
      }
      in.close();
      in = null;
      out.close();
      out = null;
      this.progressDialog.updateProgress(50);

      ResourceDatabase modDatabase = new ResourceDatabase(this.session.getModFile());
      modDatabase.load();
      this.progressDialog.updateProgress(60);

      ResourceEntry resourceEntry = modDatabase.getEntry("module.ifo");
      if (resourceEntry == null) {
        throw new DBException("Save does not contain module.ifo");
      }
      in = resourceEntry.getInputStream();
      if (this.session.getDatabaseFile().exists()) {
        this.session.getDatabaseFile().delete();
      }
      out = new FileOutputStream(this.session.getDatabaseFile());
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
      }
      in.close();
      in = null;
      out.close();
      out = null;
      this.progressDialog.updateProgress(75);

      Database database = new Database(this.environment, this.session.getDatabaseFile());
      database.load();
      list = (DBList)database.getTopLevelStruct().getValue();
      element = list.getElement("Mod_PlayerList");
      if ((element == null) || (element.getType() != 15)) {
        throw new DBException("module.ifo does not contain Mod_PlayerList");
      }
      list = (DBList)element.getValue();
      if (list.getElementCount() == 0) {
        throw new DBException("Mod_PlayerList is empty");
      }
      this.progressDialog.updateProgress(80);

      String fileName = questDBName + ".qdb";
      saveEntry = saveDatabase.getEntry(fileName);
      if (saveEntry == null) {
        throw new DBException("Save does not contain " + fileName);
      }
      in = saveEntry.getInputStream();
      Database questDatabase = new Database(this.environment);
      questDatabase.load(in);
      in.close();
      in = null;
      list = (DBList)questDatabase.getTopLevelStruct().getValue();
      element = list.getElement("Quests");
      if ((element == null) || (element.getType() != 15)) {
        throw new DBException("Quests not found in quest database");
      }
      questList = (DBList)element.getValue();
      this.progressDialog.updateProgress(85);

      count = questList.getElementCount();
      List<Quest> quests = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        fieldList = (DBList)questList.getElement(i).getValue();
        String resourceName = fieldList.getString("File");
        fileName = resourceName + ".qst";
        saveEntry = saveDatabase.getEntry(fileName);
        if (saveEntry == null) {
          throw new DBException("Save does not contain " + fileName);
        }
        in = saveEntry.getInputStream();
        questDatabase = new Database(this.environment);
        questDatabase.load(in);
        in.close();
        in = null;
        Quest quest = new Quest(resourceName, questDatabase.getTopLevelStruct());
        if (quest.getQuestName().length() > 0) {
          quests.add(quest);
        }
      }
      this.session.setQuests(quests);

      this.session.setPlayerName("player.utc");
      saveEntry = saveDatabase.getEntry(this.session.getPlayerName());
      if (saveEntry == null) {
        throw new DBException("Save does not contain " + this.session.getPlayerName());
      }
      in = saveEntry.getInputStream();
      if (this.session.getPlayerFile().exists()) {
        this.session.getPlayerFile().delete();
      }
      out = new FileOutputStream(this.session.getPlayerFile());
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
      }
      in.close();
      in = null;
      out.close();
      out = null;

      Database playerDatabase = new Database(this.environment, this.session.getPlayerFile());
      playerDatabase.load();

      this.progressDialog.updateProgress(100);

      this.session.setSaveDatabase(saveDatabase); //.TheWitcherSave
      this.session.setModDatabase(modDatabase); //.sav
      this.session.setDatabase(database); //.sav -> 'module.ifo'
      this.session.setPlayerDatabase(playerDatabase); //player.utc
      this.session.setSmmDatabase(smmDatabase); //.smm
      this.loadSuccessful = true;
    } catch (DBException exc) {
      Main.logException("Save file structure is not valid", exc);
    } catch (IOException exc) {
      Main.logException("Unable to read save file", exc);
    } catch (Throwable exc) {
      Main.logException("Exception while opening save file", exc);
    }

    try
    {
      if (in != null) {
        in.close();
      }
      if (out != null) {
        out.close();
      }

    }
    catch (IOException exc)
    {
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        LoadFile.this.progressDialog.closeDialog(LoadFile.this.loadSuccessful);
      }
    });
  }
}
