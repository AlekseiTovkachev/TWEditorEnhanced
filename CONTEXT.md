# TWEditorEnhanced

A Windows save-game editor for The Witcher (Enhanced Edition). Loads the game's save files, parses them into editable structures, and writes them back without corruption.

## Language

### Save domain

**Save**:
A `.TheWitcherSave` file produced by the game — the thing the editor loads, parses, edits, and rewrites. One Save holds everything from one moment of gameplay.
_Avoid_: savegame, save file

**Save archive**:
The outer container format of a Save: a header, named entries, and a directory table pointing at each entry's bytes. What the editor reads and rewrites byte-safely.
_Avoid_: RGMH file, container

**Entry**:
A named file stored inside a Save archive (module data, player blueprint, quest files, screenshot, notes). Entries may be compressed individually.
_Avoid_: resource (reserved for game installation data), item

**Module**:
The in-save container describing the current game session — the session's state, the player list, and the quest database reference. The editor's Stats/Attributes/Signs/Styles/Equipment/Inventory/Difficulty tabs all read from the player record inside it.
_Avoid_: mod

**Player record**:
The parsed player state within the Module: level, experience, gold, vitality, talents, styles, equipment, inventory.
_Avoid_: character, hero

**Quest record**:
A quest inside the Save: its name, state (not started / started / completed / failed), and its phase list.
_Avoid_: journal entry

**Game resource**:
A data file from the game installation (item templates, localized strings) that the editor reads to interpret a Save. Lives in the game directory, never inside a Save.
_Avoid_: entry

**Strings database**:
The game's localized text table, referenced from saves by numeric ID. The editor needs it to display item and quest names.
_Avoid_: tlk, translation file

### Editor architecture

**Seam**:
The save-database layer — reading/writing Save archives plus the parsed structures beneath them. The single place automated tests attach; deliberately GUI-free.
_Avoid_: core, backend, engine

### Work streams

**Modernization**:
Work on the code itself: removing the shared static application state, raising the Java language level, improving idioms. Behavior-preserving by definition; the Seam tests are the gate.
_Avoid_: refactor (too vague), rewrite

**UI polish**:
Work on how the editor looks and feels: theme depth, dark mode, spacing, small UX fixes. No panel restructuring unless a decision says otherwise.
_Avoid_: modernization (reserved for code work), reskin

**Kotlin conversion**:
The planned migration of all production code to Kotlin, in its own phase after Modernization: incremental, one file at a time, tests green every commit. Modernization is its prerequisite, not its passenger.
_Avoid_: port, rewrite, rewrite-in-Kotlin
