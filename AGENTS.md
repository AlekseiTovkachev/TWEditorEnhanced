# AGENTS.md

## Agent skills

### Issue tracker

GitHub Issues (gh CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.

## Technology preferences

The owner is not deeply familiar with the JVM library/plugin/vendor landscape and wants to learn transferable, mainstream skills from this project.

- **Prefer popular, modern, mainstream technologies.** If a choice is niche, propose the mainstream equivalent instead.
- **When proposing any library, vendor, plugin, or tool, briefly explain what it is and why it fits** (one or two lines), especially if it isn't already referenced in `docs/adr/`.
- New dependencies must be justified against the mainstream default for that job.

## Save-edit verification standard

Any code path that mutates save data must ship with a write/reload edit test, following the `TutorialSaveGoldenTest` pattern (`SaveSeamSupport` harness):

1. Load a real save (fixture from `src/test/resources/saves`, or a `.local-saves` save with an explicit prerequisite check that fails before mutating when the save doesn't match).
2. Apply the edit through the session layer.
3. Save and reload the file through the parser.
4. Assert the edit persisted **and** untouched archive entries remain byte-identical (CRC digests, per `entryDigests`).

Never claim an edit works from in-memory state alone - the round-trip through the real file is the proof. Working copies only; never mutate a save outside a temp directory.

## Swing tests

The gated GUI tests (`tweditor.screenshots`) create real windows. Two rules keep them from hanging the build (a hung test JVM stalls gradle with no output and no test report):

1. **Never call `SwingUtilities.invokeAndWait { modalDialog.isVisible = true }`.** A modal dialog shown this way blocks the dispatch until it closes, so the call never returns and the test JVM hangs forever. Show the dialog with `invokeLater`, wait, then close it with `isVisible = false` (see `SavePickerDialogTest.capture`).
2. **Annotate gated UI test classes with `@Timeout`** so a stuck EDT fails that test instead of hanging the whole run.

Also: interrupting or timing out a gradle invocation does not stop the daemon - it keeps building in the background and a later rerun can report stale `UP-TO-DATE`. After a hung or killed run, check `jps -l` for stray `GradleDaemon`/test-worker JVMs and kill them before rerunning.
