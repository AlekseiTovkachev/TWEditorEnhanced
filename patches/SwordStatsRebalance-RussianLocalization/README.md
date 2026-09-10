# Sword Stats Rebalance — Russian Localization patch

Russian descriptions for the [Sword Stats Rebalance 1.3](https://www.nexusmods.com/witcher/mods/1101)
mod for *The Witcher: Enhanced Edition*. The mod's author could not ship Russian text
(D'jinni corrupted the script), so the 39 item templates this mod overrides had either
`?`-destroyed Russian text or stale pre-rebalance descriptions. This patch fills in the
Russian `Description` field of all 39 templates.

The text is built from the **official base-game Russian localization**: each description body
is the official wording from the same template inside the game archives (`templates00.bif`),
and only the mod's changed stat lines are re-expressed, using official terminology and the
same TLK string references the mod's own English text uses (`<STRREF:…>`), so every displayed
number matches the actual rebalance.

## Requirements

- *The Witcher: Enhanced Edition* (the game itself provides the Russian text, fonts, and
  `dialog_14.tlk`; no other files are needed).
- **Sword Stats Rebalance 1.3 installed** — this patch overwrites that mod's templates in
  place. It does **not** install a second copy: the mod author warns that a second loose
  `.uti` with the same name under `Data` silently shadows one or the other. `install.ps1`
  refuses to run if it detects one.

## Install

```powershell
pwsh -File install.ps1                       # default game root: C:\Games\The Witcher Enhanced Edition
pwsh -File install.ps1 -GameRoot "D:\Games\The Witcher EE"   # custom install
```

What it does, in order:

1. Prerequisite checks (fail before touching anything): the base mod's `weapon_abl.lua`
   must exist, all 39 target `.uti` files must exist, and no duplicate loose `.uti` with a
   patch resref may exist anywhere under `Data`.
2. Copies every replaced original into
   `<GameRoot>\Mod Conflict Backups\Swords Stats Rebalance\<timestamp>-russian-localization\`
   (same subdirectory layout), never overwriting an existing backup.
3. Copies the 39 localized templates over the mod's files and verifies each installed file
   byte-digests identical to the patch copy.

Idempotent: re-running on a patched install reports "nothing to do".

`weapon_abl.lua` (the gameplay numbers) is never modified.

## Uninstall

```powershell
pwsh -File uninstall.ps1                     # uses the first *-russian-localization backup found
pwsh -File uninstall.ps1 -BackupDir "<path>" # explicit backup directory
```

Restores the 39 originals from the backup (digest-verified each file) and keeps the backup.

## Files included (39 templates)

- `Steel swords\` — it_stlswd_001, 008–016 (incl. Harvall, Gwalhir, D'yaebl, G'valchir's template)
- `Silver swords\` — it_svswd_001, 005 (Aerondight), 006 (Moonblade)
- `Meteorite swords\` — it_stlswd_bbb … it_stlswd_yyy (10 combinations)
- `Runic swords\` — it_svswd_eee … it_svswd_sss (10 combinations)
- `Meteorites and runestones\` — it_upgrcomp_001–006 (3 meteorite colors, 3 runes)

Only the Russian `Description` substring changed in each file; abilities, prices, model
fields, all other languages' text, and template resource names are untouched.

## Verification history

- Every patched file was re-parsed with a GFF reader after a real write/reload and matched
  the expected Russian text; the whole GFF tree was compared structurally against the
  original (identical outside the Russian description substring).
- One loose `.uti` winner per affected resref and exactly one `weapon_abl.lua` confirmed.

## Notes / known quirks

- The mod renames a few swords in English (e.g. "Mahakaman rune sihill" → "G'valchir").
  Russian names were left at the official wording: a literal translation would collide with
  the separate sword `it_stlswd_015` ("Гвихир", Gwalhir).
- Upstream quirk, not corrected here: the Moon rune's English text claims Damage "+10%" while
  the mod's own Lua grants +5%; all texts mirror the mod's displayed strrefs.
- Save games created before installing the patch may have copied item descriptions into the
  save; newly obtained/re-forged swords show the localized text immediately.
