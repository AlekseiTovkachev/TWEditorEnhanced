package app.tweditor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Owns the process-wide facts of a running editor: platform separators and
 * temp directory, the detected game install and user data paths, application
 * properties, the localized strings database and language identifier, the
 * resource index, and the item templates built from it.
 */
public class AppEnvironment
{
  private String fileSeparator;
  private String lineSeparator;
  private String tmpDir;
  private boolean useShellFolder = true;
  private String installPath;
  private String installDataPath;
  private String gamePath;
  private File propFile;
  private Properties properties;
  private StringsDatabase stringsDatabase;
  private int languageID;
  private Map<String, Object> resourceFiles;
  private List<ItemTemplate> itemTemplates;

  public String getFileSeparator()
  {
    return this.fileSeparator;
  }

  public void setFileSeparator(String fileSeparator)
  {
    this.fileSeparator = fileSeparator;
  }

  public String getLineSeparator()
  {
    return this.lineSeparator;
  }

  public void setLineSeparator(String lineSeparator)
  {
    this.lineSeparator = lineSeparator;
  }

  public String getTmpDir()
  {
    return this.tmpDir;
  }

  public void setTmpDir(String tmpDir)
  {
    this.tmpDir = tmpDir;
  }

  public boolean isUseShellFolder()
  {
    return this.useShellFolder;
  }

  public void setUseShellFolder(boolean useShellFolder)
  {
    this.useShellFolder = useShellFolder;
  }

  public String getInstallPath()
  {
    return this.installPath;
  }

  public void setInstallPath(String installPath)
  {
    this.installPath = installPath;
  }

  public String getInstallDataPath()
  {
    return this.installDataPath;
  }

  public void setInstallDataPath(String installDataPath)
  {
    this.installDataPath = installDataPath;
  }

  public String getGamePath()
  {
    return this.gamePath;
  }

  public void setGamePath(String gamePath)
  {
    this.gamePath = gamePath;
  }

  public File getPropFile()
  {
    return this.propFile;
  }

  public void setPropFile(File propFile)
  {
    this.propFile = propFile;
  }

  public Properties getProperties()
  {
    return this.properties;
  }

  public void setProperties(Properties properties)
  {
    this.properties = properties;
  }

  public StringsDatabase getStringsDatabase()
  {
    return this.stringsDatabase;
  }

  public void setStringsDatabase(StringsDatabase stringsDatabase)
  {
    this.stringsDatabase = stringsDatabase;
  }

  public int getLanguageID()
  {
    return this.languageID;
  }

  public void setLanguageID(int languageID)
  {
    this.languageID = languageID;
  }

  public Map<String, Object> getResourceFiles()
  {
    return this.resourceFiles;
  }

  public void setResourceFiles(Map<String, Object> resourceFiles)
  {
    this.resourceFiles = resourceFiles;
  }

  public List<ItemTemplate> getItemTemplates()
  {
    return this.itemTemplates;
  }

  public void setItemTemplates(List<ItemTemplate> itemTemplates)
  {
    this.itemTemplates = itemTemplates;
  }

  public String getString(int stringRef)
  {
    return this.stringsDatabase.getString(stringRef);
  }

  public String getLabel(int stringRef)
  {
    return this.stringsDatabase.getLabel(stringRef);
  }

  public String getHeading(int stringRef)
  {
    return this.stringsDatabase.getHeading(stringRef);
  }

  public void saveProperties()
  {
    try
    {
      FileOutputStream out = new FileOutputStream(this.propFile);
      this.properties.store(out, "TWEditor Properties");
      out.close();
    } catch (Throwable exc) {
      Main.logException("Exception while saving application properties", exc);
    }
  }
}
