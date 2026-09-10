# Sword Stats Rebalance 1.3 — Russian localization: work summary

**Date:** 2026-09-10 · **Scope:** Russian localization of the installed mod, per the handoff
(`tweditor-sword-rebalance-russian-localization-handoff.md`). No save files were touched.

## What was wrong

The mod rebalances 39 sword/rune/meteorite `.uti` templates and translates the item descriptions
for every language except Russian (the author's D'jinni pipeline could not display the script).
The installed templates had two kinds of Russian text damage, both discovered by inventorying
all 39 files against the official base-game templates (`templates00.bif` via `main.key`):

- **Destroyed (3 templates):** `it_stlswd_001`, `it_stlswd_009`, `it_svswd_001` — the Russian
  substring existed but every non-ASCII character had been written as a literal `?` (0x3F).
- **Stale (36 templates):** the Russian description was still the untouched official base-game
  text with the *old* stat numbers/lines, while the mod's English (and German/Polish/etc.)
  versions carry the new rebalance lines — so a Russian install would show wrong stats.

Russian **names** were fine in all 39 templates (byte-identical to the official base Russian
names), so names were left untouched.

## Verified format facts (these made the fix mechanical)

- TW1 GFF localized substrings encode `ID = language * 2 + gender`; Russian = language 14 →
  substring ID **28** (male slot). Confirmed empirically against the mod's own files.
- Localized text in TW1 data (TLK and GFF substrings alike) is **UTF-8**, not the Windows
  code page from `languages.2da` (cp1251 is only the font/charset setting). `dialog_14.tlk`
  decodes as UTF-8 (`D0 A1 D0 B8 D0 BB D0 B0` = "Сила").
- The mod's added stat lines reference **TLK strrefs** (`<STRREF:2143>` = "+10%", `2124` =
  "+40%", `2087` = "-20%", …). Those strings are identical in the English and Russian TLKs, so
  copying the mod's own strrefs renders correct localized numbers.

## What was written

For **all 39 templates**, the Russian `Description` substring (language 14, gender 0) was
replaced. Each new text is:

- the **official base-game Russian description body verbatim** (copied from the same-resref
  `.uti` inside `templates00.bif`, no loose overrides), plus
- the mod's **new stat lines re-expressed in official terminology**, strrefs copied from the
  mod's English text so the numbers match the actual rebalance (`weapon_abl.lua` values were
  cross-checked: e.g. `it_stlswd_001` = +10% damage/+10% Bleeding → two `<STRREF:2143>`; the
  `-20%`/`-10%` downgrades use the negative strrefs the mod itself references).

Official vocabulary used (all attested in the official Russian templates/TLK):
`Оружие`, `Обновление оружия`, `Повреждения`, `Атака`, `Вероятность критического
Кровотечения/Боли/Оглушения/Ослепления/Воспламенения/Нокдауна`, `Вероятность Обезоруживания`,
`Точного Удара`, `Увеличивает восприимчивость противника к серебру`, `Пробивает любую броню
противника.` One genuinely new sentence was translated (it exists in the mod's English and
Polish but not in official Russian — the runestones' italic note): «Добавление более одной
одинаковой руны на серебряный меч дает дополнительные улучшения.»

The three similarly named Meteorite/Rune sword variants keep their distinct effect lines
(e.g. 3×red = Bleeding +30%/Stun +15% vs 2red+blue = Bleeding +20%/Precise Hit +10%).

## Files changed (installed)

39 templates under `C:\Games\The Witcher Enhanced Edition\Data\Override\Swords Stats Rebalance\`:

- `Steel swords\`: it_stlswd_001, 008, 009, 010, 011, 012, 013, 014, 015, 016
- `Silver swords\`: it_svswd_001, 005, 006
- `Meteorite swords\`: it_stlswd_bbb, bby, byy, rbb, rby, rrb, rrr, yrr, yyr, yyy
- `Runic swords\`: it_svswd_eee, ees, ess, mee, mme, mmm, sme, smm, ssm, sss
- `Meteorites and runestones\`: it_upgrcomp_001…006

Only the Russian `Description` substring changed in each file; every other field
(`WpnAbilitySelf`, `WpnAbilityOpp`, prices, model fields, all other languages' substrings,
`TemplateResRef`) is structurally identical, proven by a full-tree comparison of the parsed
original vs. the re-parsed written file. `weapon_abl.lua` was not touched.

## Backup

`C:\Games\The Witcher Enhanced Edition\Mod Conflict Backups\Swords Stats Rebalance\20260910-225224-russian-localization\`
— all 39 original `.uti` files, same subdirectory layout. (The earlier 20260910-220425 backup
of the DM/Scabbard conflicts is untouched.)

## Verification performed

- Parse-back: every edited file was re-read through the project's GFF parser
  (`Database`), asserting the exact expected Russian text after a real write/reload —
  never from in-memory state.
- Structural equality: entire GFF tree (types, ids, labels, all substring slots, VOID
  payloads) matches the original except the replaced Russian description substring;
  substring order/position preserved.
- Post-install: all 39 installed files re-parsed and matched again (gated test,
  `verifyInstalledRussianDescriptions`).
- No duplicates: exactly one loose `.uti` winner per affected resref under `Data`, and
  exactly one `weapon_abl.lua` (the same-basename `.dds` textures of the texture-upscale
  pack and `.mdb` models of z_zSwords_DM are different resource types, not `.uti` conflicts).
- Repo state: only two new test files were added; the existing worktree changes are
  untouched.

## Tooling added (repo)

- `src/test/kotlin/app/tweditor/ModRussianLocalizationData.kt` — the 39 Russian texts with
  their mod subdirectories (documented data table).
- `src/test/kotlin/app/tweditor/ModRussianLocalizationTest.kt` — `@Tag("local")` gated test:
  `applyRussianDescriptionsToSwordRebalanceTemplates` (idempotent: backs up, edits working
  copy, proves via write/reload, installs, re-verifies) and
  `verifyInstalledRussianDescriptions` (read-only re-verification).
  Run with: `.\gradlew.bat localSaveTest --tests "app.tweditor.ModRussianLocalizationTest"`

## Distributable patch package

`patches\SwordStatsRebalance-RussianLocalization\` — a self-contained, re-installable
version of this fix (39 localized `.uti` files in the mod's layout + `install.ps1` /
`uninstall.ps1` / `README.md`). The installer: fails first on missing prerequisites (base
mod absent, missing targets) and on any duplicate loose `.uti` winner under `Data`, backs
up originals into `Mod Conflict Backups\<ts>-russian-localization`, then copies and
digest-verifies (SHA256) every installed file; re-runs are no-ops. Proven end-to-end:
no-op install → uninstall restore (39 verified) → re-apply (39 verified) → duplicate-guard
refusal test → repo's `verifyInstalledRussianDescriptions` test passed on the final state.

## Decisions & residual notes

- **English renames were not propagated to Russian.** The mod renames a few swords in English
  (e.g. `it_stlswd_012` "Mahakaman rune sihill" → "G'valchir"). The official Russian names were
  kept: translating "G'valchir" would collide with the *different* sword `it_stlswd_015`
  "Гвихир" (Gwalhir), and the handoff says to include names only where Russian is missing — it
  isn't.
- **English typos not mirrored:** the mod's English has a stray period ("Pain +20%. Bleeding")
  in `it_stlswd_yyr` and broken `</c>` tag placements in a few lines; the Russian texts use
  the intended comma/tag structure instead.
- **Upstream quirk (not ours to fix):** for `it_upgrcomp_006` the mod's English claims Damage
  "+10%" (`<STRREF:2143>`) while its Lua gives Moon rune Damage_Mult 1.05 (+5%). All Russian
  texts mirror the mod's own displayed strrefs; the numbers are the mod author's.
- **Not verified in-game:** the handoff's visual check (launch the game under Russian,
  inspect tooltips) needs the game itself; structural + parse-back proof is complete. To
  check: set the game language to Russian, obtain/hold any affected sword (e.g. the starting
  witcher steel sword `it_stlswd_001` or forged `it_stlswd_rrr`) and read the tooltip —
  the name/description now come from substring ID 28.
