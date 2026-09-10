TWEditor - Version 4.1.0-SNAPSHOT
------------------------

Overview
========

TWEditor allows you to modify save games created by The Witcher. You can edit Geralt's hero state, equipment, carried and stored items, and journal knowledge. Edits stay in memory until you save; Apply/Revert let you commit or discard the current draft without touching the file, Save As writes a copy under a new name, and a backup is taken before the first write of each session.

The shipped desktop UI has three Compose workspaces:

- **Hero** edits the level summary, difficulty, attributes, Signs, and combat styles.
- **Journal** shows quests, bestiary, characters, locations, formulas, ingredients, glossary, and tutorials. Advanced Journal Editing makes the intentionally risky raw edits explicit.
- **Inventory** combines the 12 supported equipment destinations, Satchel, Alchemy Sack, Quest Items, and the shared non-spatial Storage chest. Add, edit, remove, sort, selection, and compatible drag/drop actions use the same command and Save workflow.

Statistics remains implemented as a read-only data calculation but is intentionally hidden from navigation until it has a dedicated presentation.

Installation
============

The easiest way to run the editor is the self-contained Windows app image: run `gradlew packageWindowsAppImage`, then double-click `build/app-image/TWEditor/TWEditor.exe`. No Java installation is required — the Compose Desktop distribution bundles its runtime.

Alternatively, the cross-platform JAR build works on any platform as described below.

This version of the save game editor assumes you have installed the Enhanced Edition of The Witcher.  Using this version of the editor with the original version of The Witcher can result in inventory errors.

To install this utility, place the TWEditorEnhanced-4.1.0-SNAPSHOT.jar file into a directory of your choice.  To run the utility, create a program shortcut and specify 

  `javaw -Xmx256m -jar TWEditorEnhanced-4.1.0-SNAPSHOT.jar`

as the program to run.  Set the Start Directory to the directory where you extracted the jar file.  A sample program shortcut is included.  The `-Xmx256m` argument specifies the maximum heap size in megabytes (the example specifies a heap of 256Mb).  You can increase the size if you run out of space processing very large saves.  Note that Windows will start swapping if the Java heap size exceeds the amount of available storage and this will significantly impact performance.  The java virtual machine will fail to start if the requested heap size is too large.

The plain cross-platform JAR requires a modern Java runtime: **Java 25 or newer**.  You can download a current JRE from https://adoptium.net.  If you are unsure what version of Java is installed on your system, open a command prompt window and enter `java -version`.  (The self-contained Windows build above bundles its own runtime and needs no Java installation.)

The game install directory is located by scanning the Windows registry.  If this scan fails or if the game files are located in a different directory, you can specify the game install directory when starting the editor.  This is done by specifying -DTW.install.path="<path>" on the java command line where <path> is the directory containing dialog.tlk.  For example, if the game files are located in C:\Games\The Witcher and the editor is installed in C:\Games, the shortcut would look like this:

  `javaw -DTW.install.path="C:\Games\The Witcher" -jar TWEditorEnhanced-4.1.0-SNAPSHOT.jar`

Don't forget to put double quotes around the path name.

The language identifier is determined by scanning the windows registry.  If this scan fails or you want to use a different language, you can specify the language identifier when starting the editor.  This is done by specifying -DTW.language=n on the java command line where 'n' is the language identifier for the associated .tlk file.  For example, US English would be specified as:

  `javaw -DTW.language=3 -jar TWEditorEnhanced-4.1.0-SNAPSHOT.jar`

The game data directory is assumed to be `The Witcher` in the user documents folder (*My Documents* on an English-language system).  If the save games are located in another directory, you can specify the game data directory when starting the editor.  This is done by specifying `-DTW.data.path="<path>"` on the java command line where <path> is directory containing the game data.  For example, if the user login is `Ronald Hoffman`, the normal game data directory would be `C:\Documents and Settings\Ronald Hoffman\My Documents\The Witcher`.

Open and Save As use the operating system's native file dialog. The Compose overwrite confirmation remains inside the application so file replacement is explicit.

Development
===========

Build and test with `gradlew build` (Gradle 9, Kotlin DSL, version catalog in `gradle/libs.versions.toml`). The UI is Compose Desktop with Material 3; a Java 25 toolchain (Temurin) is downloaded automatically on the first build via the Foojay resolver, so no specific JDK needs to be installed. `gradlew coverageReport` writes report-only Kover HTML/XML coverage; it has no arbitrary global threshold.

The release-gating test suite contains golden-file tests around the save-database layer.  Representative early-game, storage, and equipment fixtures are committed under `src/test/resources/saves/`; tests load working copies and never mutate those resources.  Broader probes against owner-installed game resources and the gitignored `.local-saves/` directory are tagged `local` and are deliberately excluded from `gradlew test`; run them explicitly with `gradlew localSaveTest` when those prerequisites are available.

ScripterRon - Ronald.Hoffman6@gmail.com

--------------------------------------------------

Version 1.0:
============
Initial release.


Version 1.1:
============
Add inventory support (add/remove/examine)


Version 1.2:
============
An open input stream was causing the save to intermittently fail.

The game mnemonic for the Axii sign is 'Axi' and not 'Axii'.  This caused failures when editing the Axii sign.


Version 1.3:
============
Open saves created on a Russian system.

Add 'Quests' tab.


Version 1.4:
============
Use the maximum stack size when adding an item to the inventory.


Version 1.5:
============
Fix null pointer exception when modifying a sign and no signs have been learned yet.


Version 2.0:
============
Support multiple installed languages.

Add the ability to repack a save file.

Support for the expanded inventory management scheme implemented in the Enhanced Edition.


Version 2.1:
============
Support equipped items.

Version 2.2:
============
Difficulty support.

Version 3.0.1
=============
Provide a JAR and DMG file

Version 4.0.0
=============
The JAR can now be started with `java -jar` / `javaw -jar` (Main-Class manifest attribute).
Build modernized to Gradle 9; the Windows launcher (launch4j) and Mac DMG packaging are removed and will be replaced by jpackage.

Version 4.1.0
=============
Self-contained Windows build via jpackage (module-trimmed Java 25 runtime, no Java installation required).
Compose Desktop presentation with a custom dark, warm color system.
Save browser with embedded screenshots, level and save info.
Item/ability icons resolved from the game archives (TGA/DDS).
Per-instance item editing: weapon ability lists, appearance, quality, price.
Three Compose workspaces: Hero, Journal, and Inventory, with the accepted equipment paperdoll and innkeeper storage model.
Journal knowledge and quest editing with an explicit Advanced Journal Editing boundary.
Read-only Statistics computation retained but hidden from navigation.
Draft workflow: Apply commits the current edits, Revert discards them back to the last applied/saved state, Save As writes a renamed copy, and validation gates run at Apply/Save time.
A backup of the save is taken before its first write each session (File > Restore Backup).
