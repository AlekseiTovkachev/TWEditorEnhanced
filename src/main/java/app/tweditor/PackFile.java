package app.tweditor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import javax.swing.SwingUtilities;

public class PackFile extends Thread
{
  private final ProgressDialog progressDialog;
  private final GameSession session;
  private final File extractDirectory;
  private boolean saveSuccessful = false;

  public PackFile(ProgressDialog dialog, GameSession session, File dirFile)
  {
    this.progressDialog = dialog;
    this.session = session;
    this.extractDirectory = dirFile;
  }

  public void run()
  {
    FileInputStream in = null;
    OutputStream out = null;
    List<SaveEntry> entries = this.session.getSaveDatabase().getEntries();
    try
    {
      for (SaveEntry entry : entries) {
        File file = new File(this.extractDirectory.getPath() + Main.fileSeparator + entry.getResourceName());
        if ((!file.exists()) || (!file.isFile())) {
          throw new IOException("Resource '" + file.getPath() + "' not found");
        }
        if (entry.isCompressed()) {
          entry.setOnDisk(false);
          entry.readFromFile(file);
        } else {
          entry.setResourceFile(file, 0, (int)file.length());
        }

      }

      this.session.getSaveDatabase().save();

      this.saveSuccessful = true;
    } catch (IOException exc) {
      Main.logException("Unable to save file", exc);
    } catch (Throwable exc) {
      Main.logException("Exception while saving file", exc);
    }

    SwingUtilities.invokeLater(() ->
            PackFile.this.progressDialog.closeDialog(PackFile.this.saveSuccessful)
    );
  }
}

