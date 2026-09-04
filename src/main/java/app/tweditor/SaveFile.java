package app.tweditor;

import javax.swing.*;
import java.io.IOException;

public class SaveFile extends Thread
{
  private final ProgressDialog progressDialog;
  private final GameSession session;
  private final AppEnvironment environment;
  private boolean saveSuccessful = false;

  public SaveFile(ProgressDialog dialog, GameSession session, AppEnvironment environment)
  {
    this.progressDialog = dialog;
    this.session = session;
    this.environment = environment;
  }

  public void run()
  {
    try
    {
      this.session.getDatabase().save();
      this.progressDialog.updateProgress(15);

      ResourceEntry resourceEntry = new ResourceEntry("module.ifo", this.session.getDatabaseFile());
      this.session.getModDatabase().addEntry(resourceEntry);
      this.session.getModDatabase().save();
      this.progressDialog.updateProgress(30);

      ResourceDatabase modDatabase = new ResourceDatabase(this.session.getModDatabase().getPath());
      modDatabase.load();
      this.session.setModDatabase(modDatabase);
      this.progressDialog.updateProgress(45);

      this.session.getSaveDatabase().addEntry(this.session.getModName(), this.session.getModFile());
      this.progressDialog.updateProgress(60);

      this.session.getPlayerDatabase().save();
      this.session.getSaveDatabase().addEntry(this.session.getPlayerName(), this.session.getPlayerFile());
      this.progressDialog.updateProgress(70);

      this.session.getSmmDatabase().save();
      this.session.getSaveDatabase().addEntry(this.session.getSmmName(), this.session.getSmmFile());
      this.progressDialog.updateProgress(80);

      this.session.getSaveDatabase().save();
      this.progressDialog.updateProgress(90);

      SaveDatabase saveDatabase = new SaveDatabase(this.environment, this.session.getSaveDatabase().getPath());
      saveDatabase.load();
      this.session.setSaveDatabase(saveDatabase);

      this.progressDialog.updateProgress(100);

      this.saveSuccessful = true;
    } catch (DBException exc) {
      Main.logException("Unable to update save database", exc);
    } catch (IOException exc) {
      Main.logException("Unable to save file", exc);
    } catch (Throwable exc) {
      Main.logException("Exception while saving file", exc);
    }

    SwingUtilities.invokeLater(() ->
            SaveFile.this.progressDialog.closeDialog(SaveFile.this.saveSuccessful));
  }
}
