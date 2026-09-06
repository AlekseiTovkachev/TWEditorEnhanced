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
The in-save container describing the current game session — the session's state, the player list, and the quest database reference. The editor's Stats/Attributes/Signs/Styles/Equipment/Inventory/Difficulty tabs all read from the player record inside it; Quests and Knowledge read the quest database it points at; Storage reads the shared innkeeper chest in the .smm meta database; Statistics reads across all of them.
_Avoid_: mod

**Player record**:
The parsed player state within the Module: level, experience, gold, vitality, talents, styles, equipment, inventory.
_Avoid_: character, hero

**Quest record**:
A quest inside the Save: its name, state (not started / started / completed / failed), and its phase list.
_Avoid_: journal entry

**Journal entry**:
A non-quest knowledge record in the Save's journal, such as a learned monster, recipe, character, location, ingredient, glossary topic, or tutorial. Its category and entry ID refer to the game's journal catalog.
_Avoid_: quest, Quest record

**Formula**:
Learned alchemy knowledge for a potion, oil, or bomb. A Formula is represented consistently across its Journal entry and the Player record's alchemy-knowledge collections.
_Avoid_: recipe (except when referring to the raw `recipe*` category IDs)

**Quest phase**:
One existing stage in a Quest record's progression tree. Selecting a Quest phase directly does not replay scripts or synchronize the related story, NPC, reward, dialogue, or linked-quest state.
_Avoid_: quest status, quest stage

**Journal workspace**:
The editor area that mirrors the game's journal sections: Quests, Characters, Locations, Monsters, Formula, Ingredients, Glossary, and Tutorials.
_Avoid_: Knowledge tab

**Hero workspace**:
The editor area for Geralt's statistics, difficulty, attributes, Signs, and combat styles.
_Avoid_: Stats tab, character tab

**Advanced Journal Editing**:
A session-scoped mode that permits editing Journal-entry categories beyond Formula and Monsters and making a warned raw quest-phase override. It is off whenever a Save is opened because those changes can produce game-inconsistent progression state.
_Avoid_: expert mode, unsafe mode

**Game resource**:
A data file from the game installation (item templates, localized strings) that the editor reads to interpret a Save. Lives in the game directory, never inside a Save.
_Avoid_: entry

**Mod**:
An installed game modification that may add or override game resources used by a Save. Distinct from a Module, which is part of the Save's internal structure.
_Avoid_: module

**Modded Save**:
A Save created or played with one or more Mods active, and which may reference resources or preserve fields not present in the base game.
_Avoid_: mod save, custom Save

**Strings database**:
The game's localized text table, referenced from saves by numeric ID. The editor needs it to display item and quest names.
_Avoid_: tlk, translation file

### Item ownership

**Inventory workspace**:
The editor area for everything Geralt equips, carries, or keeps in innkeeper Storage. It contains Equipment, Satchel, Alchemy Sack, Quest Items, and Storage rather than denoting any one of them.
_Avoid_: inventory (when referring only to carried items), item list

**Equipment**:
Items assigned to Geralt's wearable and weapon slots. Each item class permits a specific set of slots.
_Avoid_: equipped inventory, paperdoll

**Satchel**:
Geralt's ordinary carried-item grid, containing 42 stack cells.
_Avoid_: inventory, backpack

**Alchemy Sack**:
Geralt's ingredient grid, containing 42 stack cells separate from the Satchel.
_Avoid_: ingredients inventory, alchemy inventory

**Quest Items**:
Geralt's carried quest-specific items, which do not occupy Satchel or Alchemy Sack cells and have no known capacity limit.
_Avoid_: quest inventory

**Storage**:
The Save's global innkeeper chest: an ordered item collection with no spatial positions or known capacity. It may be absent until initialized by the game.
_Avoid_: stash, storage grid

**Unverified item**:
An item template whose destination legality is not understood, usually because it comes from a Mod or an unfamiliar base-item class. It may be added after a warning; it is distinct from an item known to be illegal in that destination.
_Avoid_: illegal item, unsupported item

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

**UI redesign**:
Replacement of the editor's presentation layer to create a cohesive, accessible Windows desktop application. It may reorganize navigation, layouts, and interactions, but preserves the existing editing capabilities and Save semantics.
_Avoid_: UI polish, reskin, modernization

**Kotlin conversion**:
The planned migration of all production code to Kotlin, in its own phase after Modernization: incremental, one file at a time, tests green every commit. Modernization is its prerequisite, not its passenger.
_Avoid_: port, rewrite, rewrite-in-Kotlin
