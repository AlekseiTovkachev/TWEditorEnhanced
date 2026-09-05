TWEditor - Version 4.1.0-SNAPSHOT
------------------------

Overview
========

TWEditor allows you to modify save games created by The Witcher.  You can modify the attributes and abilities of the player character (Geralt), the items he carries, wears and stores, and the journal knowledge he has collected.  Edits stay in memory until you save; the Apply/Revert commands let you commit or discard the current edits without touching the file, Save As writes a copy under a new name, and a backup of the save is taken before the first write of each session.

The 'Stats' tab allows you to modify selected fields in the save game such as experience, orens and talents.  The modified values will be written when the file is saved.  Whether or not the changes are accepted when the save is loaded depends on the game engine.

The 'Attributes' tab allows you to modify Strength, Dexterity, Stamina and Intelligence selections.

The 'Signs' tab allows you to modify Aard, Igni, Quen, Axii and Yrden selections.

The 'Styles' tab allows you to modify Steel Sword and Silver Sword selections.

The 'Equipment' tab shows the paperdoll of equipped items, grouped by weapon slot: add items from a template tree into matching slots, move or remove them, and edit them in place.

The 'Inventory' tab allows you to modify Geralt's inventory.

The 'Storage' tab reads the innkeeper storage chest shared by every innkeeper in the save: store items from a template tree, remove, sort, examine and edit them.

The 'Quests' tab shows the game quests (Started, Completed, Failed and Not Started).  The 'Examine' button will display a description of the current quest stage (if the stage has a description).

The 'Knowledge' tab shows the journal knowledge the save holds (bestiary, characters, places, recipes, ingredients, glossary); ticked entries are written into the save on the next save.

The 'Statistics' tab is a read-only record of what Geralt has been doing: kills and top opponents, quests touched per act, and the journal activity timeline in in-game time.

The 'Difficulty' tab allows you to modify difficulty level.

Installation
============

The easiest way to run the editor is the self-contained Windows build: unzip `TWEditor-win-<version>.zip` (built with `gradlew packageWindowsAppImage`) and double-click `TWEditor.exe`.  No Java installation is required — a module-trimmed Java 25 runtime is bundled with the app.

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

The Java runtime will sometimes throws a null pointer exception when adding the shell folders to the file chooser dialog (JFileChooser).  If this happens, you can disable the shell folders by specifying `-DUseShellFolder=0` on the java command line.

Development
===========

Build and test with `gradlew build` (Gradle 9, Kotlin DSL, version catalog in `gradle/libs.versions.toml`).  A Java 25 toolchain (Temurin) is downloaded automatically on the first build via the Foojay resolver, so no specific JDK needs to be installed.

The test suite contains golden-file tests around the save-database layer.  The primary fixture is a real tutorial save committed under `src/test/resources/saves/`.  Additional local saves are picked up from the gitignored `.local-saves/` directory in the project root: drop any number of `*.TheWitcherSave` files there and the suite round-trips them (files are loaded, re-saved, and compared; they are never modified in place beyond the round-trip and are never committed).  When that directory is absent or empty, the local-save tests are skipped so fresh clones and CI stay green.

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
FlatLaf theming with follow-OS light/dark mode.
Save browser with embedded screenshots, level and save info.
Item/ability icons resolved from the game archives (TGA/DDS).
Per-instance item editing: weapon ability lists, appearance, quality, price.
Equipment paperdoll by weapon slot and the innkeeper storage chest tab.
Knowledge/Journal panel: bestiary, books/lore, alchemy knowledge, with journal entry editing.
Read-only Statistics tab: kills, quests per act, journal timeline.
Draft workflow: Apply commits the current edits, Revert discards them back to the last applied/saved state, Save As writes a renamed copy, and validation gates run at Apply/Save time.
A backup of the save is taken before its first write each session (File > Restore Backup).
