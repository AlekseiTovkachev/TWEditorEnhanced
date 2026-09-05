# Item and ability icons in The Witcher 1 (Enhanced Edition) — probe findings

**Date:** 2026-09-05
**Scope:** How inventory item icons and Signs/Styles ability icons are stored and named in the game install, so the editor can display them without shipping any extracted art.
**Method:** one-off probe run against the real install at `C:\Games\The Witcher Enhanced Edition` using this codebase's `KeyDatabase`/`TextDatabase`/`Database` classes (probe deleted after use).

## Formats

- The install's `main.key` indexes **34,109 resources; 8,827 are DDS and 0 are TGA** (58 BMP, 44 JPG). Everything icon-shaped is **DDS**. `TgaDecoder` stays for save screenshots only; the icon path decodes DDS.
- All 1,378 icon-candidate textures (`iit_*`, `ui_ab_*`) are **DXT5** (the DX9 fourcc), dimensions 16x32, 32x32, 32x128, 64x32, 64x64, 64x128, 64x256, 128x128, with mipmaps. `question_mark.dds` (the engine's placeholder icon) is 32x32 DXT1, 824 bytes.
- The sword world texture `it_stlswd_001.dds` is 512x256 DXT1 — same-name `it_*` textures are world-model diffuse maps, not icons; the DXT1 support is still worth having for the direct-resref fallback.

## Item icons: the `iit_` composition rule

There is **no icon column in `baseitems.2da`** (columns: Label, InvSlotWidth, InvSlotHeight, InvMinY, InvMaxY, EquipableSlots, WeaponMatType, JoinTarget, JoinSource, CanRotateIcon, ModelType, ItemClass, DefaultModel, Container, WeaponWield, WeaponType, RangedWeapon, MinAttackDist, MaxAttackDist, MinRange, MaxRange, Stacking, InvSoundType, RotateOnGround, AmmunitionType, WeaponSize, ReadyAnim, UsableType, DefaultPicture, CanParry, IsPickable, SortingOrder) and **no icon field on `.uti` templates** (`DescPicture` is the description-dialog picture and is empty on swords). `DefaultPicture` is the same placeholder for every row (`_unknown_item`).

The inventory icon is a texture named after the item's **base-item class and appearance index**, not the template resref:

- **`iit_<ItemClass minus its `it_` prefix>_<ModelPart1, zero-padded to 3 digits>`** resolves for **956 of 1,100** `.uti` templates. Examples: `it_stlswd_001` (BaseItem 1, ModelPart1 1) → `iit_stlswd_001`; rune sword `it_stlswd_rrr` (ModelPart1 2) → `iit_stlswd_002`; dice `dice_adv_001` (BaseItem 49, ModelPart1 1) → `iit_dice_001`; keys (BaseItem 40, ModelPart1 231/232) → `iit_quest_231/232`.
- Fallback `<TemplateResRef>.dds` direct covers amulet/necklace-style textures whose class textures don't exist (`it_amulet_001.dds` etc.; 147 templates total, 31 not already covered by the composition rule).
- Fallback `iit_<DefaultModel minus `it_`>` catches odd ModelPart values.
- The remainder (~110 of 1,100) are NPC prop tools (`w_h_*`, BaseItem 43/53), quest potion variants, and `it_v*` misc — never normal player inventory content. They fall back to `question_mark`, the engine's own placeholder.

## Ability icons: `witcher_sgn_tree.luc` / `witcher_cs_trees.luc`

There is **no `abilities.2da`**; ability definitions are compiled Lua (`.luc` in `scripts00.bif`). The sign/style **trees** — the per-node Icon mapping the character screen uses — live in two dedicated LUC files whose string constants spell the mapping verbatim:

- `witcher_sgn_tree.luc`: `Aard1 → ui_ab_aar1`, `Aard1 Powerup → ui_ab_aar1p`, `Aard1 Upgrade1 → ui_ab_aar1u1`, `Aard2 Upgrade2 → ui_ab_aar2u2`, …
- `witcher_cs_trees.luc`: `StyleSteelStrong1 → ui_ab_sts1`, `StyleSteelStrong1 Upgrade1 → ui_ab_sts1u1`, …

The DDS texture set is complete and regular, so the mapping needs no LUC parsing — it is a deterministic transform of the ability labels the Signs/Styles panels already carry (`databaseLabels`):

- Signs: `Aard/Igni/Quen/Axii/Yrden` → `aar/ign/que/axi/yrd`; `N` → `N`, `N Powerup` → `Np`, `N UpgradeK` → `NuK`; icon = `ui_ab_<prefix><transform>`.
- Styles: `StyleSteelStrong/Fast/Group` → `sts/stf/stg`, `StyleSilverStrong/Fast/Group` → `svs/svf/svg`; `N` → `N`, `N UpgradeK` → `NuK`; icon = `ui_ab_<prefix><transform>`.
- Every `ui_ab_` variant exists in three renditions: plain, `_i`, `_x` (learned/inactive state art, exact semantics unverified — the editor shows the plain rendition and leaves the learned state to the checkbox itself).

Each texture set is fully present for the editor's label grid: signs `aar/ign/que/axi/yrd` × levels 1–5 × {plain, p, u1, u2}; styles `sts/stf/stg/svs/svf/svg` × levels 1–5 × {plain, u1, u2, u3}. Verified by a real-install test that resolves every label in both panels.

## Implications for the editor

1. Decode DDS (DXT5 required, DXT1/DXT3/uncompressed for safety) in pure Kotlin; no new dependencies.
2. Extend the resource scan to keep `.dds` (and `.tga` for modded installs) alongside `.2da`/`.uti`.
3. Resolve item icons via the `iit_` chain (class+ModelPart1 → template resref → DefaultModel → `question_mark`); resolve ability icons via the label transform.
4. Decode off the EDT into a per-resref cache; renderers read the cache and repaint when late decodes land (icons pop in progressively, text shows until then).
5. Icons stay in the install at runtime; nothing extracted is committed.
