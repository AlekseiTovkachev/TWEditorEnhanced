package app.tweditor;

import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Captures light/dark screenshots of the main window for theme tickets.
 * Gated behind -Dtweditor.screenshots=true so the suite never opens windows by default:
 *   gradlew test --tests "app.tweditor.ThemeScreenshotTest" -Dtweditor.screenshots=true
 */
class ThemeScreenshotTest {

  @Test
  void captureLightAndDarkMainWindow() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("tweditor.screenshots"));

    Path outDir = Path.of("docs", "screenshots");
    Files.createDirectories(outDir);
    capture(ThemeSelection.Preference.LIGHT, outDir.resolve("theme-light.png"));
    capture(ThemeSelection.Preference.DARK, outDir.resolve("theme-dark.png"));
  }

  private static void capture(ThemeSelection.Preference preference, Path target) throws Exception {
    AppEnvironment environment = createEnvironment();

    SwingUtilities.invokeAndWait(() -> ThemeSelection.install(preference));

    MainWindow[] holder = new MainWindow[1];
    SwingUtilities.invokeAndWait(() -> holder[0] = new MainWindow(environment));
    MainWindow window = holder[0];
    SwingUtilities.invokeAndWait(() -> {
      window.setAlwaysOnTop(true);
      window.setLocationRelativeTo(null);
      window.pack();
      window.setVisible(true);
      window.toFront();
    });

    loadFixture(environment, window);

    SwingUtilities.invokeAndWait(() -> {
      try {
        window.session.setDataChanging(true);
        DBList list = (DBList) window.session.getDatabase().getTopLevelStruct().getValue();
        list = (DBList) list.getElement("Mod_PlayerList").getValue();
        list = (DBList) list.getElement(0).getValue();

        window.statsPanel.setFields(list);
        window.attributesPanel.setFields(list);
        window.signsPanel.setFields(list);
        window.stylesPanel.setFields(list);
        window.questsPanel.setFields(list);
        window.difficultyPanel.setFields(list);

        window.tabbedPane.setSelectedIndex(0);
        window.tabbedPane.setVisible(true);
        window.session.setDataChanging(false);
        window.session.setDataModified(false);
      } catch (Exception exc) {
        throw new RuntimeException(exc);
      }
    });

    Thread.sleep(1000);

    Robot robot = new Robot(window.getGraphicsConfiguration().getDevice());
    BufferedImage shot = robot.createScreenCapture(window.getBounds());
    ImageIO.write(shot, "png", target.toFile());

    SwingUtilities.invokeAndWait(window::dispose);
  }

  private static AppEnvironment createEnvironment() throws Exception {
    AppEnvironment environment = new AppEnvironment();
    environment.setFileSeparator(System.getProperty("file.separator"));
    environment.setLineSeparator(System.getProperty("line.separator"));
    environment.setTmpDir(System.getProperty("java.io.tmpdir"));
    environment.setProperties(new Properties());
    environment.setLanguageID(3);
    environment.setResourceFiles(new HashMap<>());
    environment.setItemTemplates(new ArrayList<>());
    environment.setStringsDatabase(new StringsDatabase(fakeTlk().getPath()));
    return environment;
  }

  private static void loadFixture(AppEnvironment environment, MainWindow window) throws Exception {
    File saveFile = SaveSeamSupport.copyFixtureTo(Files.createTempDirectory("theme-shots"));
    LoadFile task = new LoadFile(new ProgressDialog(window, "Loading"), window.session, environment, saveFile);
    task.run();
    assertNotNull(window.session.getSaveDatabase(), "fixture save failed to load");
  }

  /**
   * A minimal but valid TLK so localized-string lookups resolve to an empty
   * string instead of hitting a missing strings database.
   */
  private static File fakeTlk() {
    try {
      File file = Files.createTempFile("theme-shots", ".tlk").toFile();
      file.deleteOnExit();
      try (FileOutputStream out = new FileOutputStream(file)) {
        out.write(new byte[]{'T', 'L', 'K', ' ', 'V', '3', '.', '0'});
        out.write(leInt(3));  // language id
        out.write(leInt(1));  // string count
        out.write(leInt(20 + 40));  // string data offset
        byte[] entry = new byte[40];
        entry[0] = 0x01;  // present
        entry[28] = 0x00;  // data offset
        entry[32] = 0x00;  // data length
        out.write(entry);
        out.write(new byte[0]);
      }
      return file;
    } catch (IOException exc) {
      throw new RuntimeException(exc);
    }
  }

  private static byte[] leInt(int value) {
    return new byte[]{
        (byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)
    };
  }
}
