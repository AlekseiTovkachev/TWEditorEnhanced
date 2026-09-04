package app.tweditor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

public class Main
{
  public static JFrame mainWindow;

  private static String deferredText;
  private static Throwable deferredException;

  public static void main(String[] args)
  {
    try
    {
      AppEnvironment environment = new AppEnvironment();
      String osName = System.getProperty("os.name").toLowerCase();
      boolean osMac = osName.startsWith("mac");
      boolean osLinux = osName.startsWith("linux");
      boolean osWin = osName.startsWith("windows");
      environment.setFileSeparator(System.getProperty("file.separator"));
      environment.setLineSeparator(System.getProperty("line.separator"));
      String tmpDir = System.getProperty("java.io.tmpdir");
      if(osLinux) {
          tmpDir = tmpDir + "/";
      }
      environment.setTmpDir(tmpDir);

      String option = System.getProperty("UseShellFolder");
      if ((option != null) && (option.equals("0"))) {
        environment.setUseShellFolder(false);
      }

      String installPath = System.getProperty("TW.install.path");
      String languageString = System.getProperty("TW.language");
      int languageID = -1;
      if (languageString != null)
        languageID = Integer.parseInt(languageString);
      if ((installPath == null) || (languageID == -1)) {
        if (osMac) {
            installPath = "/Applications/The Witcher.app/Contents/Resources/drive_c/Program Files/The Witcher";
            languageID = 3;
        } else if (osLinux) {
            String locateString = "locate dialog_3.tlk | grep \"Witcher.*Data\" | sed -e \"s|/Data/dialog_3.tlk||\"";
            String[] cmd = {
                "/bin/sh",
                "-c",
                locateString
            };
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            installPath = reader.readLine();
            reader.close();

            languageID = 3;
        } else if (osWin) {
            String regString = "reg query \"HKLM\\Software\\CD Projekt Red\\The Witcher\" /reg:32";
            Process process = Runtime.getRuntime().exec(regString);
            StreamReader streamReader = new StreamReader(process.getInputStream(), environment.getLineSeparator());
            streamReader.start();
            process.waitFor();
            streamReader.join();

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\s*(\\S*)\\s*(\\S*)\\s*(.*)");
            String line;
            while ((line = streamReader.getLine()) != null) {
                java.util.regex.Matcher m = p.matcher(line);
                if ((m.matches()) && (m.groupCount() == 3) && (m.group(2).equals("REG_SZ"))) {
                    String keyName = m.group(1);
                    if ((keyName.equals("InstallFolder")) && (installPath == null))
                        installPath = m.group(3);
                    else if ((keyName.equals("Language")) && (languageID == -1)) {
                        languageID = Integer.parseInt(m.group(3));
                    }
                }
            }
        }


        if (installPath == null) {
          throw new IOException("Unable to locate The Witcher installation directory");
        }
        if (languageID == -1) {
          throw new IOException("Unable to determine the installed language");
        }

      }

      environment.setInstallPath(installPath);
      environment.setLanguageID(languageID);
      String installDataPath = new StringBuilder().append(installPath).append(environment.getFileSeparator()).append("Data").toString();
      environment.setInstallDataPath(installDataPath);
      File dirFile = new File(installDataPath);
      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }

      String gamePath = System.getProperty("TW.data.path");
      if (gamePath == null) {
        File defaultDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        String userSubPath = osMac ? "com.cdprojektred.TheWitcher/The Witcher" : "The Witcher";
        gamePath = new StringBuilder().append(defaultDir).append(environment.getFileSeparator()).append(userSubPath).toString();
      }
      environment.setGamePath(gamePath);

      dirFile = new File(new StringBuilder().append(gamePath).append(environment.getFileSeparator()).append("saves").toString());
      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }

      File stringsFile = new File(new StringBuilder().append(installDataPath).append(environment.getFileSeparator()).append("dialog_").append(languageID).append(".tlk").toString());
      if (!stringsFile.exists()) {
        throw new IOException(new StringBuilder().append("Localized strings database ").append(stringsFile.getPath()).append(" does not exist").toString());
      }
      environment.setStringsDatabase(new StringsDatabase(stringsFile));

      KeyDatabase keyDatabase = new KeyDatabase(environment, new StringBuilder().append(installDataPath).append(environment.getFileSeparator()).append("main.key").toString());
      List keyEntries = keyDatabase.getEntries();
      HashMap<String, Object> resourceFiles = new HashMap(keyEntries.size());
      for (Object keyEntryObj : keyEntries) {
        KeyEntry keyEntry = (KeyEntry)keyEntryObj;
        String name = keyEntry.getFileName().toLowerCase();
        int sep = name.lastIndexOf('.');
        if (sep > 0) {
          String ext = name.substring(sep);
          if ((ext.equals(".2da")) || (ext.equals(".uti"))) {
            resourceFiles.put(name, keyEntry);
          }

        }

      }
      environment.setResourceFiles(resourceFiles);

      processOverrides(environment, new File(installDataPath));

      dirFile = new File(new StringBuilder().append(System.getProperty("user.home")).append(environment.getFileSeparator()).append("Application Data").append(environment.getFileSeparator()).append("ScripterRon").toString());

      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }
      File propFile = new File(new StringBuilder().append(dirFile.getPath()).append(environment.getFileSeparator()).append("TWEditor.properties").toString());
      environment.setPropFile(propFile);
      Properties properties = new Properties();
      if (propFile.exists()) {
        FileInputStream in = new FileInputStream(propFile);
        properties.load(in);
        in.close();
      }
      environment.setProperties(properties);

      properties.setProperty("app.version", BuildInfo.VERSION);
      properties.setProperty("java.version", System.getProperty("java.version"));
      properties.setProperty("java.home", System.getProperty("java.home"));
      properties.setProperty("os.name", System.getProperty("os.name"));
      properties.setProperty("sun.os.patch.level", System.getProperty("sun.os.patch.level"));
      properties.setProperty("user.name", System.getProperty("user.name"));
      properties.setProperty("user.home", System.getProperty("user.home"));
      properties.setProperty("install.path", installPath);
      properties.setProperty("game.path", gamePath);
      properties.setProperty("temp.path", tmpDir);

      ThemeSelection.install();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Main.createAndShowGUI(environment);
        } } );
    }
    catch (Throwable exc) {
      logException("Exception during program initialization", exc);
    }
  }

  private static void processOverrides(AppEnvironment environment, File dirFile)
  {
    File[] files = dirFile.listFiles();
    for (File file : files)
      if (file.isDirectory()) {
        processOverrides(environment, file);
      } else {
        String name = file.getName().toLowerCase();
        int sep = name.lastIndexOf('.');
        if (sep > 0) {
          String ext = name.substring(sep);
          if ((ext.equals(".2da")) || (ext.equals(".uti")))
            environment.getResourceFiles().put(name, file);
        }
      }
  }

  public static void createAndShowGUI(AppEnvironment environment)
  {
    try
    {
      JFrame.setDefaultLookAndFeelDecorated(true);

      mainWindow = new MainWindow(environment);
      mainWindow.pack();
      mainWindow.setVisible(true);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Main.buildTemplates(environment);
        } } );
    }
    catch (Throwable exc) {
      logException("Exception while initializing application window", exc);
    }
  }

  public static void buildTemplates(AppEnvironment environment)
  {
    ProgressDialog dialog = new ProgressDialog(mainWindow, "Loading item templates");
    LoadTemplates task = new LoadTemplates(dialog, environment);
    task.start();
    dialog.showDialog();
  }

  public static void logException(String text, Throwable exc)
  {
    System.runFinalization();
    System.gc();

    if (SwingUtilities.isEventDispatchThread()) {
      StringBuilder string = new StringBuilder(512);

      string.append("<html><b>");
      string.append(text);
      string.append("</b><br><br>");

      string.append("<b>");
      string.append(exc.toString());
      string.append("</b><br><br>");

      StackTraceElement[] trace = exc.getStackTrace();
      int count = 0;
      for (StackTraceElement elem : trace) {
        string.append(elem.toString());
        string.append("<br>");
        count++; if (count == 25) {
          break;
        }
      }
      string.append("</html>");
      JOptionPane.showMessageDialog(mainWindow, string, "Error", 0);
    } else if (deferredException == null) {
      deferredText = text;
      deferredException = exc;
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            Main.logException(Main.deferredText, Main.deferredException);
          } } );
      }
      catch (Throwable swingException) {
        deferredException = null;
        deferredText = null;
      }
    }
  }

  public static void dumpData(String text, byte[] data, int offset, int length)
  {
    System.out.println(text);

    for (int i = 0; i < length; i++) {
      if (i % 32 == 0)
        System.out.print(String.format(" %14X  ", new Object[] { Integer.valueOf(i) }));
      else if (i % 4 == 0) {
        System.out.print(" ");
      }
      System.out.print(String.format("%02X", new Object[] { Byte.valueOf(data[(offset + i)]) }));

      if (i % 32 == 31) {
        System.out.println();
      }
    }
    if (length % 32 != 0)
      System.out.println();
  }
}
