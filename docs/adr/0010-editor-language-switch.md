# One editor-language switch for shell and game content

The editor previously showed every game-content string (item/quest/journal names) in one language fixed at startup from the Windows registry (or `-DTW.language`), and the Compose shell's own labels were hardcoded English. We decided the whole editor — shell labels and game-content strings — switches display language at runtime from a main-window header dropdown, and the dropdown lists only languages the editor can *fully* display; translations land one language at a time, starting with Russian. The choice persists in `TWEditor.properties`, `-DTW.language` keeps its override role, and the registry value remains the first-run default. Game-content text resolves from the install's `dialog_<id>.tlk` (a loose override wins, per selected language, at switch time); when a string lacks the selected language it falls back to English for display only — fallback text is never written into a Save, and string edits write the active language's substring slot (WYSIWYG).

**Considered options**:

- **Content-only switch (shell stays English)** — rejected: the owner reads Russian and verifies mod localization work in the editor; a half-translated editor serves neither use well.
- **Compose Multiplatform string resources** — rejected: a new library dependency for one feature; `java.util.ResourceBundle` is the mainstream JVM answer, needs no dependency, and reads UTF-8 `.properties` natively (Java 9+).
- **Pick-at-startup language instead of live switch** — rejected: the primary use is flipping to Russian to inspect what a Modded Save reads like; a restart requirement kills that.

**Consequences**:

- Command-layer user-facing messages must move from built English strings to message keys + args (the Seam stays GUI-free — keys are data), which is the bulk of the refactor cost.
- Hundreds of shell strings get extracted to `Messages.properties` / `Messages_ru.properties`; the Russian wording reuses the official base-game vocabulary established by the Sword Stats Rebalance localization work.
- Adding a later language (Polish, German, …) is a new `Messages_xx.properties` plus vetting — no code change.
