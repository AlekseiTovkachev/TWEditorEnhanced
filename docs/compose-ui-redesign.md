# Compose Desktop UI Redesign

This brief records the agreed product shape for the post-4.1 presentation-layer replacement. It does not authorize changes to Save semantics: the GUI-free Seam remains the source of truth.

## Goals and boundaries

- Replace Swing and FlatLaf with Compose Desktop in one linear cutover.
- Make the Windows application cohesive, professional, responsive, and reliable from 100–200% display scaling.
- Use Material 3 primitives under a custom dark, warm, restrained Witcher-inspired design system.
- Load recognizable game icons from the user's installation and provide built-in fallback icons; do not commit game artwork.
- Keep file and Save commands conventional and unambiguous, without filling the interface with keyboard-shortcut hints.
- Preserve every existing editing capability except that Statistics is hidden from navigation for now.
- Do not broaden the rewrite into the Save parser/writer or reinterpret unknown Save data.

## Navigation

A vertical rail provides three primary destinations. Its icons are the exact face, scroll, and hand glyphs used by the game's `pb_but_char`, `pb_but_map`, and `pb_but_inv` controls, resolved at runtime from the installed `gui_panel3` texture atlas. Journal's secondary tabs are text-only.

1. **Hero** — a compact level, experience, vitality, orens, Bronze/Silver/Gold talent, and Difficulty summary that does not compete with the main editor; clearly visible secondary pages for Attributes, Signs, and Combat Styles.
2. **Journal** — Quests, Characters, Locations, Monsters, Formula, Ingredients, Glossary, and Tutorials in the game's order.
3. **Inventory** — Equipment, Satchel, Alchemy Sack, Quest Items, and Storage.

The rail uses game-resolved icons with text labels. Secondary navigation is visually quieter. Major regions reflow before local scrolling is introduced; controls and item cells do not shrink below usable sizes.

## Inventory workspace

At comfortable widths, the workspace follows the game's item-screen composition: a long Storage list on the left, Equipment in the center, and Quest Items, Satchel, and Alchemy Sack stacked on the right. At narrow widths, these regions become secondary pages.

- **Equipment** reproduces the game's spatial arrangement of the 12 modeled Geralt slots: steel sword, silver sword, two short-weapon slots, big weapon, armor, trophy, two ring slots, and three elixir slots. It does not invent additional destinations or place the slots on an unrelated skeleton graphic.
- **Satchel** is a 14×3 grid. Its 42-cell limit remains enforced but is not repeated as a capacity counter in the interface.
- **Alchemy Sack** is a separate 14×3 grid. Its 42-cell limit remains enforced but is not repeated as a capacity counter in the interface.
- **Quest Items** are outside both capacity grids and show no invented maximum.
- **Storage** is a long, virtualized ordered icon list, not a spatial grid, and has no invented capacity. Search is a secondary action opened from a magnifying-glass icon rather than a permanently visible field. When the Save has no initialized Storage record, it explains that the player must visit an innkeeper in-game and disables Add.

Clicking an occupied item selects it and opens details; double-click edits it. Each container exposes one `+` action that opens its destination-aware picker; empty cells are not individually rendered as Add buttons. Items can be dragged from carried containers or Storage into compatible Equipment slots. Compatible and incompatible targets are visibly distinguished during the drag, and an invalid drop leaves the source and destination unchanged. Removal is explicit and remains undoable until Save.

The picker supplies search, category filters, icons, names, stack amount where applicable, and a details preview. Known-illegal destinations are blocked. Unverified templates remain selectable after a warning because the user may understand a Mod better than the editor. Existing unknown or invalid state is displayed with a warning and preserved unless explicitly changed.

## Journal workspace

Quests remain readable in normal mode. Monster knowledge and Formula grants are normally editable; other knowledge categories and raw Quest-phase overrides require **Advanced Journal Editing**.

Advanced Journal Editing:

- is visible but off whenever a Save is opened;
- enables disabled mutation controls rather than hiding content;
- shows a concise warning when enabled;
- uses the normal Apply, Save, and Save As behavior without steering toward Save As;
- records every pending change for review and Undo.

A Monster grant writes the observed book-style `bestiary:<id>/s/1` Journal entry. Removing a Monster removes its knowledge variants as an explicit editor operation.

A Formula grant for a potion, oil, or bomb updates the Journal and every corresponding Player alchemy-knowledge collection. It does not add a fake `READBOOK_lst` source. The new Journal record uses `EntryCD = 0`, is unread, and uses the Save's current game-time for `EntryTOD`; the latter must be confirmed by a focused before/after sample. Formula removal is advanced because the game has no observed forget operation. Oils and bombs use the potion model but remain marked unverified until checked in-game.

Unknown Mod Journal categories appear as unresolved data and are never guessed into Monsters. Advanced users may edit understood categories, but unfamiliar records are preserved by default.

The Advanced Quest editor may select only an existing root Quest phase. It changes the raw current-phase value and does not replay scripts or synchronize nested conditions, linked quests, StoryPhase, NPCs, dialogue, rewards, inventory, or other game state. The UI must state this limitation and show the exact raw phase change. Generic completion/failure controls and arbitrary invented phase IDs are not offered.

## Modded Saves

Both vanilla and Modded Saves are in scope. Unknown entries and fields are preserved. Loose and packed Mod resources, localized strings, icons, and module awareness will be derived from representative Mods installed by the owner rather than guessed in advance. Findings should be reduced into a minimal synthetic committed fixture where licensing permits; third-party Mod assets are not committed.

## Safety and verification

Before the UI cutover:

- replace delete-then-rename saving with candidate validation and atomic replacement;
- centralize every mutation behind an editor-command boundary;
- harden archive-integrity assertions to compare entry key sets and every untouched digest;
- provide deterministic fixtures for every supported mutation family so CI cannot pass by skipping them;
- preserve the in-memory draft and original Save on every failed write stage.

Verification is layered:

1. Parser/writer tests for valid and malformed archives.
2. Command-level write/reload tests with semantic and archive-integrity assertions.
3. Compose semantic tests for displayed state and command dispatch.
4. Packaged-app smoke tests for Open, edit, Apply/Revert, Save, and reload.
5. Representative in-game working-copy checks for each mutation family.

Every mutation carries an evidence level: **verified in-game**, **structurally verified**, **unverified**, or **deliberately dangerous**. Kover reports coverage initially without an arbitrary global threshold. Curated screenshots of the dark theme may support review, but pixel-perfect snapshots are not the primary correctness gate.
