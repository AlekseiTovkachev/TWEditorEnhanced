# Editor language switch (Russian first)

## Goal

A single header-dropdown switch controls the language of everything the editor displays: the Compose shell's own labels and the game-content strings (item/quest/journal names) resolved for the open Save. v1 ships **English + Russian**; later install languages land one at a time as shell translations arrive.

Motivation: the Sword Stats Rebalance localization work (see `RUSSIAN-LOCALIZATION-SUMMARY.md`) gave the mod Russian text that was verified structurally but never seen rendered. Switching the editor to Russian lets the owner read those names/descriptions in lists and tooltips without launching the game.

## Decisions (settled in grill)

1. **Whole-editor language** — one switch covers shell labels *and* game-content strings; the shell is no longer hardcoded English.
2. **Live switch** — no restart; open workspaces re-render immediately.
3. **Header dropdown** in the main window; lists only languages the editor can fully display (shell translation exists **and** a content `dialog_<id>.tlk` exists).
4. **English fallback for display** — a game-content string missing in the selected language renders the English (id 3) value, so gaps stay readable; fallback is never written into a Save.
5. **WYSIWYG writes** — string edits (e.g. item rename) write the *active display language's* substring slot; switching the display switches the write slot.
6. **Plumbing** — the choice persists in `TWEditor.properties`; `-DTW.language=n` keeps its override role; the registry value (`HKLM\Software\CD Projekt Red\The Witcher\Language`, currently 3) remains the first-run default. Module TLK fallback chain (preferred module → base → any module) is untouched.
7. **Loose-override TLKs** — a loose `Data\dialog_<id>.tlk` wins for the selected language at switch time, not only at startup.
8. **Translation storage** — `java.util.ResourceBundle` + `Messages.properties` (English, fallback) / `Messages_ru.properties` (UTF-8); JDK built-in, no new dependencies.
9. **Command messages** — command-layer user-facing messages (validation warnings, "Unknown ability" errors) become message keys + typed args; the shell renders them localized. The Seam stays GUI-free (keys are data).
10. **Authoring** — the agent produces the complete Russian draft; the owner corrects wording in the review build, reusing the official vocabulary from the mod-localization thread (Повреждения, Атака, Вероятность критического Кровотечения, …).
11. Log/diagnostic messages stay English (not user-facing).

## Current mechanism (facts)

- Startup language detection: `-DTW.language` → registry `Language` → fail (`Main.kt:32-83`). Loads `Data\dialog_<id>.tlk` (`Main.kt:107`); hard fail if missing.
- Display read: `DBList.kt:118` selects the `environment.languageID`, gender 0 substring; **empty when missing** (no fallback today). Write: `DBList.kt:152` adds/replaces the substring in the `environment.languageID` slot.
- Loose override: `Data\dialog_<languageID>.tlk` replaces the startup TLK only when the id matches (`Main.kt:272`).
- Module TLKs: every `.tlk` inside packed modules registers regardless of language (`Main.kt:229`); `AppEnvironment.getString` chains preferred module → base → any module (`AppEnvironment.kt:47`).
- Owner's install: 10 language TLKs present (3=English, 5=Polish, 10=German, 11=French, 12=Spanish, 13=Italian, 14=Russian, 15=Czech, 16=Hungarian, 21=Chinese Traditional), named by the loose `Data\languages.2da`; registry default is English (3). TLK and GFF substring text is UTF-8; `ID = language * 2 + gender` (Russian male slot = 28).
- Translatable surface: ~94 `Text("` literals in `ComposeMainWindow.kt` alone, plus `ComposeSavePicker.kt`, `ItemDetails.kt`, and command-layer messages (`EquipmentCommand`, `HeroCommand`, `InventoryCommand`, `AdvancedJournalCommand`, `ComposeFileWorkflow`).

## Implementation slices

### Slice 1 — switch seam + tracer bullet

Seam (GUI-free, all testable without GUI):

- `EditorLanguage(id: Int, displayName: String)` value type.
- `LanguageCatalog`: discovers available languages from the install (`Data\dialog_*.tlk`); display names from `languages.2da` (id 3's row is "FinalEnglish_Short" — show "English"), falling back to `dialog_<n>` labels when the 2da is absent. Synthetic minimal `languages.2da` fixture committed; no game data committed.
- `AppEnvironment.setLanguage(EditorLanguage)`: resolves the content TLK for the new id (loose file wins, else archive entry if present; optionally validate the TLK header's own language id), swaps `stringsDatabase`, updates `languageID`.
- Display fallback at the read layer: selected-language substring, else English (id 3) substring, else strref resolution, else empty. Write path stays slot-exact — fallback never persisted.
- i18n runtime: `ResourceBundle` with parent chaining (`Messages_ru` → `Messages`) for missing-key English fallback; the shell exposes the active bundle via a `CompositionLocal` provided at the window root, so a language change recomposes every visible string.
- Header dropdown: English / Русский.
- Tracer bullet: the **Inventory workspace** fully localized in both languages; a language switch re-queries its displayed items so names re-resolve through the new TLK.

Tests:

- `LanguageCatalog` discovery against a temp install layout (synthetic fixtures).
- Switch seam: after `setLanguage(14)`, `getString` resolves Russian text.
- Fallback: an item carrying only an English substring renders English while Russian is selected.
- Rename under Russian: write/reload round-trip proves the edit lands in slot 28 (language 14, gender 0) and untouched archive entries stay byte-identical (save-edit verification standard).

### Slice 2 — full shell extraction sweep

- Extract every hardcoded user-visible string across `ComposeMainWindow` (headings, buttons, dialogs, tooltips), `ComposeSavePicker`, `ItemDetails` → keys (convention `workspace.element`).
- Command-layer messages → keys + args: `EquipmentCommand` (warnings, "No display name or template reference", "Unknown … ability"), `HeroCommand`, `InventoryCommand`, `AdvancedJournalCommand`, `ComposeFileWorkflow` (save-info lines, dialogs).
- Complete English `Messages.properties`; complete `Messages_ru.properties` draft.
- Every workspace re-renders under the switch (language change re-dispatches the open workspaces' read commands).

### Slice 3 — polish + owner review

- Save-info "Language: N" shows the language display name instead of the raw id (`ComposeFileWorkflow.kt:353`).
- Edge cases: switch with no save open; switch with an unsaved draft (Draft/Apply/Revert state is structured — untouched; validation messages re-localize on next render).
- Review build for the owner (`packageWindowsAppImage` → `build/app-image/TWEditor/TWEditor.exe`); terminology corrections folded back into `Messages_ru.properties`.

## Later languages (out of scope for v1)

Polish, German, French, Spanish, Italian, Czech, Hungarian, Chinese Traditional: each needs a new `Messages_<locale>.properties` and a vetting pass; the content TLKs already ship in the install. Locale mapping for future bundles: 5→pl, 10→de, 11→fr, 12→es, 13→it, 15→cs, 16→hu, 21→zh-TW.

## References

- ADR: `docs/adr/0010-editor-language-switch.md`
- Prior localization work: `RUSSIAN-LOCALIZATION-SUMMARY.md`, `patches/SwordStatsRebalance-RussianLocalization/README.md`, `docs/research/item-editing-in-the-witcher-1.md`
