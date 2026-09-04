package app.tweditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DatabaseUpdateListener
  implements ActionListener, DocumentListener
{
  private final GameSession session;

  public DatabaseUpdateListener(GameSession session)
  {
    this.session = session;
  }

  public void actionPerformed(ActionEvent ae)
  {
    if ((this.session.getDatabase() != null) && (!this.session.isDataChanging())) {
      this.session.setDataModified(true);
      Main.mainWindow.setTitle(null);
    }
  }

  public void changedUpdate(DocumentEvent de)
  {
    if ((this.session.getDatabase() != null) && (!this.session.isDataChanging())) {
      this.session.setDataModified(true);
      Main.mainWindow.setTitle(null);
    }
  }

  public void insertUpdate(DocumentEvent de)
  {
    if ((this.session.getDatabase() != null) && (!this.session.isDataChanging())) {
      this.session.setDataModified(true);
      Main.mainWindow.setTitle(null);
    }
  }

  public void removeUpdate(DocumentEvent de)
  {
    if ((this.session.getDatabase() != null) && (!this.session.isDataChanging())) {
      this.session.setDataModified(true);
      Main.mainWindow.setTitle(null);
    }
  }
}
