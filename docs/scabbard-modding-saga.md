# The Witcher EE Sword/Scabbard Modding Saga — Handoff Document

**Purpose:** complete working state, findings, and open problem, written so another agent can resume without re-deriving anything.
**Date:** 2026-09-11.
**Game:** The Witcher Enhanced Edition, GOG install at `C:\Games\The Witcher Enhanced Edition`.

---

## 1. The mod stack (what the user runs)

Installed into `...\The Witcher Enhanced Edition\Data\`, manual copies only (Vortex breaks mod 1188):

```
Data\
  Override\
    ClassicUI\                     <- Vizima UI (1040), pure UI retextures, no conflicts
    HanGivesHan\, LoreMap\, ProjectMersey\, SuspectFix\, Swords Stats Rebalance\,
    ModernTextureUpscaleAIO\, MorennStorageRomance\, sound/texture overhauls
  z_scabbard\                      <- 1. base: Scabbard Mod v1.04 (ModDB installer) — COMPLETE (49 uti templates)
  z_zUWACaI\                       <- 2. armors: UWACaI 1.01 NoRotWW (1177) + merged appearance.2da
  z_zSwords_DM\                    <- 3. everything sword-related (steps 2-7 + 9 of the install guide):
    gui_new\icons\                 <- CSO icons <- ZIcons (1188) overwrote <- + Rusty's icon
    zzz_scabbards\                 <- 1188's sub-priority layer (must load late):
      templates\NPC\, templates\Swords\   <- 27 marker-tagged sword templates (was 4 — INCOMPLETE INSTALL, now fixed)
      steel\Witcher\ph_stl_001.* etc.     <- scabbard models
      wfx\                                <- placement files (fx_stl001_pa/pl1-3, fx_stl004_*, fx_stl006_*, fx_slv001/005_*)
      scripts\                            <- 1188's + 1191's scripts (10406-B armor-aware it_arm*_on.ncs, custom_script.ncs of 19 Jun = the "Zzz Scabbards Fix")
    zz_scabbard\                   <- 1191 compat layer (UWACaI <-> scabbards)
    steel_swrds\..., silver_swrds\..., Grease_fix\, envmaps\, NPC\
```

The mod authors use `z_ < zz_ < zzz_` naming to influence load order, but do **not** assume a separately added alphabetically later root wins every same-resref collision. A Process Monitor trace below proved that assumption false for `ph_stl_001.mdb` in this installation.

The 1191 patch IS attached (verified): its scripts live merged in `z_zSwords_DM\zzz_scabbards\scripts\`, sizes match the archive byte-for-byte.

## 2. Verified-healthy facts (via the save editor in this workspace)

From the newest local save (`000185 - …-182.TheWitcherSave`, dumped via `EquipmentDumpTest`):

- Carried meteorite sword: `it_stlswd_rrr`, tag `steelsword;RRR;upgraded;stl_up;miecz_nowy` — the `miecz_nowy` marker **persists** (the one-time migration re-bound the instance and the user's re-save wrote it back).
- Worn body item: `it_witcharm_002`, tag `it_witcharm_002;pancerz_nowy`.
- Stored Witcher steel sword: `it_stlswd_001`, tag `WitcherSword;steelsword;it_stlswd_001` (no marker — expected; it never got the equip-time migration while in storage).
- Storage and satchel contain no rogue sword items. The save side is **provably healthy**.

**Behavioral outcome during this investigation:** completing the templates and saving once made the remove/re-add pulse behave like the mod's intended one-time migration. Afterward, in a separate thread, the owner intentionally turned off the sword re-adding behavior entirely. That later choice supersedes the earlier “one blink on cold-start load is expected” conclusion.

## 3. What actually changed vs. the start of this thread (and stuck)

1. **27 marker-tagged templates added** to `Data\z_zSwords_DM\zzz_scabbards\templates\Swords\` (from the 1188 main archive; the user's install had only 4 of 31). **ROOT-CAUSE FIX for the migration pulse.**
2. **Base mod templates restored to 49 files** in `Data\z_scabbard\uti\` (a 10 Sep "SSR install" pass had stripped 33 hooked templates; source = the user's own `Data\mod_backup.zip`).
3. **`Data\z_zUWACaI\appearance.2da` merged:** UWACaI original + scabbard-mod rows applied (Geralt rows 7,8,9,12,13,15,16,18,21 blanked to `****`, matching the scabbard mod's intent) + rows 723-996 appended (completeness). UWACaI's original file is preserved in the backups (see location below).
4. **Data-tree hygiene:** all diagnostic `_backup` folders moved OUT of `Data\` (they were being scanned as resource roots and shadowed live files — see findings). They were copied, with all 34 file hashes verified, to the stable ignored workspace directory **`mods\scabbard-backups-2026-09-11\`**. The older `%TEMP%\opencode\tweditor_backups_out\` copy remains.

**Separate-thread sword re-add change:** the owner reports that the automatic sword re-adding behavior was turned off in another thread. This repository does not contain that external change. A final filesystem audit here found `Data\z_scabbard\scripts\sword_load.ncs` still present, SHA-256 `D95DC8CA36030E1C4FB99F72E38F466CAA50336C3719FF9241AA72B78C5AAD6E`, byte-identical to the unpacked Scabbard Mod 1.04 copy. Therefore the behavior was not disabled by deleting or replacing that loose file; its exact other-thread mechanism must be consulted before reproducing or reversing it. Earlier in this thread the file was briefly renamed to `.off` for a probe and then restored.

**Cleaned probe junk:** `z_zSwords_DM\Templates\Fx\` and `z_scabbard\Templates\Fx\` probe dirs deleted; `fx_stl001_pa1-3.wfx` restored pristine (x=-0.003, z=-0.528, scale 1.01) in both mod folders; `Data\Templates\Fx\fx_stl001_pa1.wfx` probe deleted; `ph_stl_001.mdb` restored in both locations.

## 4. Established facts / engine rules learned

1. **The scabbard attach chain (disassembled with `ncsdis --witcher`, source `it_scab_stlrrr.ncs`, 2415 B, 389 instructions):**
   - `main()`: `if (GetLocalInt(pc, "pochwy_start") == 0) → DelayCommand(1.0, { SetLocalInt(pc,"pochwy_start",1) })` else attach immediately; every load re-runs the attach (`sub_00000090`).
   - `sub_00000090()`: `GetItemPossessedBy(pc,"RRR")` → `GetItemInSlot(pc,15)` → on match: `ExecuteScript("it_stlscab_dstr")` then `CreateVisualEffectAtObject(item, node, effectResref)`.
   - **Belt attach:** node `swd_r` (right hip), effect `fx_stl001_pl1-3` per armor state.
   - **Back attach:** node `swd_s`, effect `fx_stl001_pa1/2/3` — selected by `GetCreatureAppearance(pc)` == 0/1/2/3 (none/light/medium/heavy). The `it_arm1/2/3_on.ncs` family (10406 B, called via `custom_script`) carries the full sword-tag map (`fx_stl001-018`, `fx_slv001/005/006`).
   - The `wfx` XML files (WootEffect format) contain the transforms: belt `pl1` = Position(-0.600,0.162,-0.150) Rotation(0,-2,66.75) Scale(1.01); back `pa1` = Position(-0.003,0,-0.528) Rotation(-0.3,180,310) Scale(1.01). Base-mod and 1188 back files are byte-identical (1188 never retuned the back placement).

2. **The exact live resources are now traced.** A clean restart/load captured with Process Monitor showed `witcher.exe` opening `Data\z_zSwords_DM\zzz_scabbards\steel\Witcher\ph_stl_001.mdb` (151,368 bytes) and the back-placement file `Data\z_zSwords_DM\zzz_scabbards\wfx\fx_stl001_pa1.wfx`. The earlier giant-transform probes missed because they were not consistently applied to the winning resource and the tree contained competing diagnostic copies. The WFX does resolve as a loose resource.

3. **Correction: the rendered sheath is not welded into the body model.** Binary parsing found no sheath mesh in `cm_witch2.mdb`; it contains the body/armor meshes and the `swd_r/l/s` mount nodes. More decisively, UWACaI's active `02WornHeavy\cm_witch2.mdb` is byte-identical to the vanilla `cm_witch2.mdb` extracted from `meshes00.bif` except for four ASCII bytes that change two diffuse/normal texture references from `cr_witch3_c1` to `cr_witch2_c1`. Geometry and the entire `swd_s` transform are identical. The earlier rename probe missed a third loose resource with the same resref: three distinct files named `ph_stl_001.mdb` were present.

4. **The exact active armor target is established.** Equipped `it_witcharm_002.uti` has `ModelPart1=2`/`MdlOverr=2`, resolving to `z_zUWACaI\Armors\02WornHeavy\cm_witch2.mdb`. Its `swd_s` node is at MDB file offset `0x38182`, position `(0.002,-0.003,-0.525)`, quaternion `(0,0.4226184,-0.0000004,0.9063077)`. The same transform occurs in the compared UWACaI worn/light/medium bodies.

5. **Mod 1188's intended pair is separate sword and sheath geometry.** Its readme says one model serves the Witcher steel and all meteorite swords. The pair is `steel_swrds\Witcher\it_stlswd_002.mdb` plus `zzz_scabbards\steel\Witcher\ph_stl_001.mdb`; the WFX names that sheath model in `ModelFile=ph_stl_001`. The sheath has three visual LOD meshes (`shadow`, `cutscene`, `cutscene.001`) aligned along local Z.

6. **The crash cause was isolated and a safe rigid-rotation build is installed.** The progression before it remains useful: a six-percent length trial hid the tip but only masked the centerline divergence; WFX changes produced no clear response; local-X geometry moved sideways; and an isolated `+0.03` local-Y shear established the correct outward depth axis/sign but overshot and looked skewed. The first rigid `-1.5°` implementation rotated vertices plus normals/tangents/binormals. Local audit then showed the MDB tangent and binormal ranges overlap (packed/interleaved storage), so treating both as independent float-vector arrays corrupted the file even though simple hash/idempotence tests passed. The crash-capable build was removed immediately, the verified originals were restored, and the owner confirmed several saves loaded normally again. The corrected rigid build writes only vertex positions and mesh bounding boxes—the same MDB regions proven loadable by the length/shear trials. A byte-range audit found 9,708 changed bytes and zero changes outside those allowed regions. The owner confirmed the safe `-1.5°` build was almost perfect; successive visual tuning settled on a current trial of `-1.25°` at original longitudinal scale plus a uniform `-0.005` model-local X centering offset. Live model SHA-256: `6F4A2814C2D266EB39F9540634DC9EAFA2D7AC85AD68B984DA19B6D614CB7966`; WFX SHA-256 remains `42AB8E529B7D8BC2D0A301637CCBAD98FE8C8A9EDE2C8BFBAEA3C7FA6540B8AF`.

7. **Load-order gotchas discovered the hard way:** any folder inside `Data\` is scanned as a resource root — our own backup folders (`_wfx_steel_backup` etc., holding pristine `.wfx`/`.uti` copies) were shadowing/competing with live files during the probe era, which poisoned several experiments before being moved out. **Never keep backups inside `Data\`.** A stale giant-transform probe was also found at `Data\Templates\Fx\fx_stl001_pa1.wfx`; it was archived to `mods\scabbard-backups-2026-09-11\wfx_tuning_out\unrecorded-live-probe\` and removed from `Data`. Process Monitor also recorded handles for an old `Data\_wfx_tuning\` tree during shutdown; a subsequent filesystem check confirmed that directory is no longer present.

## 5. Final scabbard fit

**Accepted correction:** align the sword and scabbard centerlines when worn on the back with the UWACaI jacket without changing the scabbard's length or its WFX.

The hash-verified original model/WFX and the safe vertex-only builds loaded normally. Visual iteration established the final chosen values: `TiltDegrees=-1.25`, `CenterOffsetX=-0.005`, and `Stretch=1.0`. The patch rotates the three sheath LOD vertex arrays rigidly about their mouth and translates them five millimeters left; it updates only vertex positions and bounding boxes. The original WFX remains byte-identical.

- Installer defaults are the accepted values; running `install.ps1` with no tuning arguments reproduces them.
- `uninstall.ps1` restores the digest-verified original model and WFX while retaining the backups.
- Do not rotate tangent/binormal buffers: their ranges overlap in this MDB's packed representation and the resulting corruption crashes save loading.

**Escalation path (owner approved but deferred):** a post to Andy0167 on mod 1188/1191 with the full evidence (point 5.4 summary + the save-tag facts). Draft text exists in this thread and is ready to use verbatim.

## 6. Tools in place

- `ncsdis.exe --witcher` (bytecode disassembler, Witcher engine tables): `%TEMP%\opencode\ncsdis\xoreos-tools-0.0.6-win64\ncsdis.exe`. Full listings already generated: `it_scab_stlrrr.ncs` → `%TEMP%\opencode\stlrrr.lst`, `it_arm1_on.ncs` → `%TEMP%\opencode\arm1.lst`.
- **Save dump test:** `src/test/kotlin/app/tweditor/EquipmentDumpTest.kt` (read-only, `@Tag("local")`), run via `.\gradlew localSaveTest --tests "app.tweditor.EquipmentDumpTest"`, output to `build/equipment-dump.txt`. Reads the newest save dropped into `.local-saves\` (property `tweditor.localSaves`).
- The user's save game: a copy of the newest `.TheWitcherSave` lives in `<workspace>\.local-saves\`.

## 7. Backups of every change we made

Stable copies are in `<workspace>\mods\scabbard-backups-2026-09-11\` (ignored by Git). The original `%TEMP%\opencode\tweditor_backups_out\` tree also remains:
- `_appearance2da_backup\appearance.2da.uwacai` — UWACaI's original appearance.2da
- `_swords_templates_backup\` — the 4 pre-fix template files
- `_z_scabbard_uti_backup\` — the 16-file (stripped) base uti state
- `_m0_sword_backup\` — the 11 sword-scoped loose m0 templates (still also in place in Data)
- `wfx_tuning_out\scab_pristine\` — pristine `fx_stl001_pa1-3.wfx`
- `deleted_live_probe\` — the six placement files from the deletion experiment

These backups are already in a persistent workspace location; no relocation is required.

## 8. Gotchas for the next agent

- The ModDB scabbard-mod installer has no variant options in v1.04 (single Geralt-only build); Steam path needs `\common\`.
- Never use Vortex for these mods.
- `fx_stl001_pa1` is shared by the Witcher steel sword AND all meteorite swords (1188: "one model for the Witcher steel and meteorite swords").
- The three in-game weight classes map to `GetCreatureAppearance` 1/2/3 and to the pa1/2/3 (back) and pl1/2/3 (belt) placement variants; all three shipped byte-identical in both mod eras, so no existing variant fits modded bodies.
- The user plays from scratch with this mod stack (no old saves to upgrade). The automatic sword re-adding behavior was intentionally turned off in a separate thread; see the verified caveat in section 3 before attempting to reproduce or undo it.
- The exact traced model currently has the audited vertex-only rigid `-1.25°` rotation and `-0.005` local-X centering offset; `fx_stl001_pa1.wfx` remains original. Their verified backups remain outside `Data` under `Mod Conflict Backups\Scabbard Fit Fix`.
