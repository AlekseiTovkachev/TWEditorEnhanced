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
