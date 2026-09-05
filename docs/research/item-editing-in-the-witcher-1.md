# Per-instance item editing in The Witcher 1 (Enhanced Edition) — research findings

**Date:** 2026-09-05
**Scope:** How TW1 resolves item tooltips, which item fields are dynamic at runtime, where the ability database lives, prior art in save editors/cheats, damage math, and whether item descriptions can be made dynamic.
**Evidence classes used throughout:**
- **[A] Verified first-party** — official D'jinni editor documentation (mirrored on the D'jinni wiki), the game's own script API (`nwscriptdefn.nss`), the official game manual, official CDPR pages, or game files documented in the official witcher-games wiki file-format articles.
- **[B] Community-primary** — modder forum posts, mod readmes/changelogs on Nexus/ModDB/GOG, wiki mini-mod pages with hands-on steps.
- **[C] Our own probe** — results from probing a real save / our fork's source code (TWEditorEnhanced), stated as such.
- **[D] Speculation / unresolved** — explicitly marked. No verified source found.

---

## Executive summary

The in-game item tooltip does **not** change when you edit per-instance fields in the save because nearly everything visible in the tooltip is **template-driven static data**: the item name and description text are localized-string references stored on the `.uti` template (the D'jinni manual documents `Name` and `Description` as template attributes, and `TemplateResRef` as the pointer back to that template), and the colored "stat lines" (`Damage +40%`, `Pain +50%`, …) are rendered from the **ability definitions** in the game's compiled-Lua ability database — the definitions themselves carry their own localized display strings and icons. Save item instances carry an `RnAbName`-referencing `WpnAbilitySelf`/`WpnAbilityOpp` list plus `ModelPart1`/`CustomCost`, but the *text* shown for each ability is not stored on the instance at all — it is looked up by ability name from the static database inside `scripts00.bif` (`witcher_atr_abl.luc` and friends, compiled Lua `DefAbility({...})` tables). So a per-instance edit changes *which abilities/looks/price the instance has*, and should change *combat behavior*, but cannot change tooltip *text* — there is no per-instance text the tooltip reads, except possibly an inline `LocalizedName` substring (unverified, see Q6). The strongest single pieces of evidence: (1) the D'jinni manual's item-editor field list (name/description/name-color/desc-color live on the template) [A]; (2) the game's own `nwscriptdefn.nss` showing abilities are attached/removed/queried **by name** on item instances at runtime (`AddAbility`, `AddGreaseAbility` — oils!, `AddWeaponEffect`, `RemoveAbility`, `HasAbility`) [A]; (3) the witcher-games wiki DIY "Difficulty settings mod" showing the ability database is decompiled Lua (`witcher_atr_abl.luc`) whose `DefAbility` entries carry names, multipliers and display strings (`AbilityName`/`AbilityDescription` strrefs, `Icon`) [A/B].

---

## 0. Background: engine lineage and where things live

The Witcher 1 runs on CDPR's heavily modified BioWare Aurora derivative. Save games are "Rich Saved Game" (`RGMH`) containers holding GFF resources, same container philosophy as NWN/KotOR; 2DA files are identical in format to NWN/KotOR ("The Witcher did not introduce any change here") [A].

- Save container layout: [witcher.fandom — TheWitcherSave format](https://witcher.fandom.com/wiki/TheWitcherSave_format) [A/B].
- 2DA/GFF/BIF/KEY/LUA/LUC format articles: [witcher.fandom — File format](https://witcher.fandom.com/wiki/File_format), [KEY/BIF v1.1](https://witcher.fandom.com/wiki/KEY_BIF_V1.1_format), [witcher-games.fandom — LUC format](https://witcher-games.fandom.com/wiki/LUC_format) (compiled Lua, decompiled with LuaDec 0.6; TW1 uses Lua **v5.0.2**) [A/B].
- The game loads `\DATA` recursively after archives, so loose files and `\Data\Override` override BIF content — the basis of all TW1 mini-mods [B]: [witcher.fandom — Modding](https://witcher.fandom.com/wiki/Modding).

---

## 1. Tooltip / description resolution

### 1.1 What the D'jinni manual says about item fields (template-level) [A]

The community-mirrored official D'jinni manual ([djinni.fandom — Module and area creation](https://djinni.fandom.com/wiki/Module_and_area_creation)) documents the item editor's attribute windows. For an **item template/instance in the editor**:

- `Name` — "Contains the name of the item."
- `Tag` — item identification tag.
- `Description` — "**Contains the description of the item displayed in the player's equipment.**"
- `TemplateResRef` — "Contains the template for the item" (points into `Data\Templates\Items`, i.e., the `.uti`).
- `Name Color (hex)` — "The name of the item in the player's equipment will be displayed in this color."
- `Desc Color (hex)` — description color.
- `Base type name` — base group (torch, steelsword, silversword, …), opening `basicitems.2da`.
- `Appearance` / `Model appearance` — icon / 3D model appearance indices.
- `Quality` — Very Low / Low / Normal / High / Very High.
- `Abilities` — "Contains item abilities."
- Economy: `Market Category` (→ `marketcat.2da`), `Custom Price` ("custom price of an item… quoted in orens").
- Weapon section: `Weapon Type` (WitcherSteelSword, WitcherSilverSword, Fistfight, Monster, Bow, Crossbow, TwoHander, ShortWeapon, Shield, Pole, Dagger, …), `Automatic Weapon Selection`, `Unarmed Combat Weapon`, `Weapon Ability Self` ("Determines a weapon's abilities"), `Weapon Ability Opponent` ("Determines the abilities an opponent has during combat when using this item"), `Weapon Slot`, `Override Attack Distance`.

These field names map 1:1 to the GFF labels we see in saves and `.uti` files (`WpnAbilitySelf`, `WpnAbilityOpp`, `WeaponType`, `Quality`, `CustomCost`, `ModelPart1`). Note the manual explicitly says the **displayed name and description come from the template** and are shown "in the player's equipment", with per-item **colors** also template attributes.

### 1.2 Where the visible stat lines come from [A/B/C]

The stat lines in TW1 item tooltips ("Damage +30%", "Pain +50%", "Disarm +25%", …) are exactly the bonuses that rune/meteorite swords and named swords carry via their **weapon abilities**:

- Community stat tables transcribed from the game ([nightsolo.net — Witcher 1 weapons](https://www.nightsolo.net/games/witcher1/weapons.html), [witcher.fandom — Rune swords](https://witcher.fandom.com/wiki/Rune_swords)) list per-sword bonuses identical to the ability-driven bonuses (3× red meteorite = Damage +40% / Bleeding +30%, G'valchir = Damage +100 / Armor Penetration +100, …).
- **Abilities carry their own display text and icons.** In the Lua ability DB, `DefAbility` entries include `Icon`, `AbilityName`, and `AbilityDescription` (strrefs into `dialog.tlk`) — e.g., the `FoodRegenerationEP` ability: `Icon = 'icb_cop01', AbilityName = '2287', AbilityDescription = '2288'` (Russian mini-mod guide, [witcher-world.ucoz.net](https://witcher-world.ucoz.net/publ/the_witcher/sozdanie_modov/sozdanie_modov_bez_redaktora/6-1-0-38) [B]; the same DB schema is documented in English on [witcher-games.fandom — Difficulty settings mod](https://witcher-games.fandom.com/wiki/Difficulty_settings_mod) [A/B]). This is the mechanism by which the tooltip renders a line per ability: the game looks up the ability **by name** and takes name/description/icon from the definition.
- Our probe [C]: save item structs carry `WpnAbilitySelf`/`WpnAbilityOpp` as lists of `STRUCT 0xBABE` entries with `RnAbName` (string) + `RnAbStk` (int) — pure **name references** into that static DB. No text lives on the instance.

**Conclusion:** tooltip *text* = template (`LocalizedName`/`Description` strrefs + colors) for the name/description, plus ability-definition display strings for each ability line. Nothing textual lives on the instance except `LocalizedName`/`Description` LSTRINGs themselves.

### 1.3 Does the tooltip read the instance ability list or the template's? **[D] unresolved**

The user's observation [C] — editing instance fields produced no tooltip change — is consistent with the tooltip being generated from the template's ability list (or the game re-initializing tooltip data from the template). We found **no source that settles whether the in-inventory tooltip renders lines from the instance `WpnAbilitySelf` list or from the `.uti`'s list**. Both hypotheses explain the observation:

1. Tooltip lines are built from the template's `WpnAbilitySelf` (static per template).
2. The game rebuilds the instance's runtime ability set from the template on load (see Q2).

An in-game test would settle it: copy `WpnAbilitySelf` from a strong sword `.uti` into a rusty-sword instance (we just shipped this) and compare the tooltip vs. combat behavior — see "Implications".

---

## 2. What is dynamic at runtime, per field

| Field | Where it lives | Runtime role | Evidence |
|---|---|---|---|
| `WpnAbilitySelf` | `.uti` template **and** save instance [C] | **Live combat state.** The game's script API adds/removes/queries abilities **on item instances by name** at runtime: `AddAbility(sAbility, oObject, nMinutes, nStackable)` — "Adds ability to object (character or item)"; `AddGreaseAbility(sAbility, oItem, nMinutes)` — "Adds current grease ability to item" (this is how blade oils work); `AddWeaponEffect(sEffect, nEffectLevel, sMedium, fIntensivity, oWeapon)` — "adds critical effect to weapon"; plus `RemoveAbility`, `HasAbility`. All return 1/0 on success/error | [A] [nwscriptdefn.nss (game's own API)](https://web.archive.org/web/20080502225556id_/http://www.witchermod.com:80/djinni/nwscriptdefn.nss), [AddGreaseAbility wiki](https://djinni.fandom.com/wiki/AddGreaseAbility), [AddWeaponEffect wiki](https://djinni.fandom.com/wiki/AddWeaponEffect) |
| `WpnAbilityOpp` | `.uti` template and save instance [C] | Abilities applied against the wielder's opponent (D'jinni: "the abilities an opponent has during combat when using this item") | [A] [D'jinni manual](https://djinni.fandom.com/wiki/Module_and_area_creation) |
| `ModelPart1` | save instance [C]; `Appearance`/`Model appearance` on templates [A] | **Cosmetic.** Runtime API `SetItemModelPart(nPart, nIconNumber, nModelNumber, oItem)` — "Sets appearance of item oItem" (icon #, model #). Icon/model swap per instance, not text | [A] [SetItemModelPart wiki](https://djinni.fandom.com/wiki/SetItemModelPart) |
| `CustomCost` | save instance [C]; D'jinni `Custom Price` (Economy, orens) [A] | Likely the item's value (price). No source documents its use for inventory tooltips or equipped items | [A] manual + [C] probe; **runtime use for player items unresolved** |
| `Quality` | `.uti` template only [A]; **absent from save item structs** [C probe] | D'jinni lists 5 levels (Very Low…Very High) but documents **no runtime effect**. No community evidence of it driving name color in TW1 (that is a TW2/3 mechanic). | [A] manual; **unresolved** |
| `BaseItem` | save instance (index into `basicitems.2da` row) [A/C] | Base type drives slots/stacking/animation set (NWN-family semantics; TW1 `baseitems.2da` confirmed by stacking mods). Changing it on an instance would change base behavior — **no community precedent found; unresolved** | [A/B] [item stacking mod](https://witcher-games.fandom.com/wiki/Item_stacking_mod), [Item Stacking & Equipment Slots (Nexus)](https://www.nexusmods.com/witcher/mods/259) |
| `WeaponType` | `.uti` template only [A]; **absent from save item structs** [C probe] | Chooses wield animation/style class (WitcherSteelSword, Monster, Dagger, …). Template-only in practice | [A] D'jinni Weapon Section |

**Known mods that change these on existing saves:** none found. Every community mechanism works **outside** the save:

- Forge-rune/meteorite upgrades **swap the whole item** — the blacksmith hands Geralt a *new template* (`it_stlswd_rrr` "Meteorite Sword (3× red)", `it_svswd_eee` "Rune Sword (Earth×3)", …). "Existing Meteorite swords are replaced when a new one is forged" ([nightsolo.net](https://www.nightsolo.net/games/witcher1/weapons.html) [B]; full template ID list in [djinni.fandom — Items](https://djinni.fandom.com/wiki/Items) [A]).
- Stat rebalance mods (FCR and derivatives) ship **new/edited `.uti` templates, edited Lua abilities, and scripts** — e.g. FCR Hard Overhauled's changelog: "Slightly increased damage of higher end steel swords", "Made every oil and runestone increase damage by flat value", "Restored monsters' abilities: Pain to wraiths, …", "Updated descriptions" ([Nexus 811](https://www.nexusmods.com/witcher/mods/811) [B]).
- Oil application at runtime adds a grease ability **to the item instance** via `AddGreaseAbility` [A] — the only mechanism that visibly mutates an existing item's abilities, and it is a runtime-script effect, not a save edit.

---

## 3. The ability system

### 3.1 Where abilities live: compiled Lua in `scripts00.bif` [A/B], not a 2DA

- The official-wiki DIY "Difficulty settings mod" states the difficulty settings are governed by **`\DATA\SCRIPTS00.BIF\witcher_atr_abl.luc`**, a **compiled Lua script** defining abilities via `DefAbility({...})` — with `Name`, `AttrsMod`, `Damage_Mult`, `PointRegen.VP_Mod`, `EffectResistance.*_Mult`, `Armor_Mult` ([witcher-games.fandom](https://witcher-games.fandom.com/wiki/Difficulty_settings_mod)) [A/B]. To mod it you decompile the `.luc` (LuaDec) and drop the edited `witcher_atr_abl.lua` into `\Data\Override` [A/B].
- The Russian "modding without the editor" guide confirms: ~170 `.luc` files ship in the BIFs; decompile with LuaDec; edited files go to `\Data\Override` as `.lua` ([witcher-world.ucoz.net](https://witcher-world.ucoz.net/publ/the_witcher/sozdanie_modov/sozdanie_modov_bez_redaktora/6-1-0-38)) [B]. It also shows the `HeroStartingAbility` dump — `Attack_Mod = 100`, `Defence = { Dodge_Mod = 45, Parry_Mod = 45 }`, `EffectImmunity`, `SpellIntensity_Mod`, etc.
- **This matches our probe [C]:** the binary resource inside `scripts00.bif` containing ability names + floats (`meteorite_red1_self`, labels `Damage_Mod`, `Silver_Mult`, `Parry_Mult`) is these compiled-Lua `DefAbility` tables. There is **no `abilities.2da`** in `main.key`/`2da00.bif` [C] — correctly, because the DB is Lua, not 2DA. (A separate `diffsettings.2da` exists for immortality/intro settings [B, same Russian guide].)

So: **TW1 abilities = Lua-table definitions (`DefAbility`) in compiled `.luc` resources, referenced by name from templates (`WpnAbilitySelf`), instances (save), and scripts (`AddAbility`).** D'jinni's editor exposes the same "Abilities" concept on character and item templates ([djinni.fandom — Character templates](https://djinni.fandom.com/wiki/Character_templates), [Module and area creation](https://djinni.fandom.com/wiki/Module_and_area_creation)) [A].

### 3.2 How mods add custom abilities to weapons [B]

- **Rune/meteorite mods and vanilla forging** do not edit instances: they reference the pre-built combination templates listed in the D'jinni item table (10 meteorite + 10 rune sword `.uti`s) [A]. Sword-stat rebalance mods (e.g. [Sword Stats Rebalance, Nexus 1101](https://www.nexusmods.com/witcher/mods/1101)) edit **templates and their descriptions** [B].
- **FCR (Full Combat Rebalance) by Andrzej "Flash" Kwiatkowski** — officially endorsed by CDPR ([thewitcher.com news](https://www.thewitcher.com/en/news/636/full-combat-rebalance-mod-for-the-first-witcher-game) [A]). Scale: 2,175 files, 182 of them scripts (Flash Mod: 913 files / 17 scripts) ([ModDB](https://www.moddb.com/mods/full-combat-rebalance1), [witcher-games.fandom](https://witcher-games.fandom.com/wiki/Full_Combat_Rebalance), [vgtimes summary](https://vgtimes.com/games/the-witcher/files/24122-complete-rebalancing-of-the-combat-system.html)) [B]. Its manual states "damage fully dependent on weapons as in most cRPG games", a full armor system, and per-armor-type special swords ([FCR manual, Scribd](https://www.scribd.com/document/201802553/Fcr-1-6-Manual-En) [A for FCR]). Given the Lua-override mechanism above, FCR's damage rebalance was implemented by shipping edited Lua ability definitions + edited `.uti` templates + scripts — consistent with its file composition and with derivative mods' changelogs [B]. No source we found describes FCR editing save instances (and it doesn't need to — static data suffices).

### 3.3 `RnAbStk` [D] unresolved

The per-ability struct in the save is `RnAbName` + `RnAbStk` [C]. The closest first-party hook is the script API's `AddAbility(sAbility, oObject, nMinutes, nStackable)` — "nStackable — czy ability moze sie nakladac na inne instancje tego samego ability" (whether the ability may stack onto other instances of the same ability) [A]. `RnAbStk` is therefore plausibly a **stack/level counter** for stackable ability instances (or the ability level, since TW1 encodes levels in names like `meteorite_red1/red2/red3`). No source confirms; treat as opaque and preserve it.

### 3.4 Missing ability names [D] unresolved

- At the **script API level**, invalid ability names are rejected: `AddAbility` "Returns 1 on success, 0 on error (invalid object/ability)" [A].
- What the **save loader** does with an unknown `RnAbName` is undocumented anywhere we looked. Community crash reports around `baseitems.2da` edits exist (changing weapon stacking "may crash your game" [B]), but no report of unknown ability names causing a crash or being silently ignored. **Recommendation:** treat unknown names as no-op + surface a warning in the editor; verify in-game before shipping a "copy power" from an ability DB entry not present in the user's install (e.g., copies between differently-modded installs).

---

## 4. Prior art — save editors and cheats

### 4.1 ScripterRon's TWEditor / TWEditorEnhanced [A]

The original read-me is the primary source for scope ([TWEditor_ReadMe.txt mirror](https://docs6.chomikuj.pl/1409990907,PL,0,0,TWEditor_ReadMe.txt); also [boazy/TWEditorEnhanced](https://github.com/boazy/TWEditorEnhanced), [cloudskytian/TWEditorEnhancedCN](https://github.com/cloudskytian/TWEditorEnhancedCN), [gamepressure entry](https://www.gamepressure.com/download/the-witcher-enhanced-edition-tweditorenhanced-savegame-editor-v3/z1137ea)):

- Tabs: Stats / Attributes / Signs / Styles / Equipment / Inventory / Quests (+Difficulty in forks). Inventory = **add/remove/examine whole items** (from the template list) and stack sizes; Equipment = swap equipped items/trophy. No per-instance stat, ability, model, or price editing.
- Key caveat from the author himself: "Whether or not the changes are accepted when the save is loaded **depends on the game engine**."
- Wrong-edition note: original-game editor vs EE editor ("expanded inventory system") — inventory errors if mismatched [A].
- Community usage is overwhelmingly **add/remove items and fix quest bugs** (dice-box bug, missing quest items), not stat surgery ([Steam guide](https://steamcommunity.com/sharedfiles/filedetails/?id=340782606) [B]).
- **What TWEditor never exposed (and reported nothing about):** any per-instance field. Its item *names* come from resolving the **template's** `LocalizedName` through `dialog.tlk` — our fork's `LoadTemplates.kt`/`ItemTemplate.kt` keep that design [C].

Our fork's new per-instance editor (`ItemEditDialog.kt`, `ItemEdit.kt`) is, as far as we could find, the **first tool to edit item instance fields in TW1 saves** — no predecessor surfaced in any search [C + absence of evidence in searches].

### 4.2 Other tools [B]

- **Trainers:** Cheat Happens "+10/+14" trainers (Unlimited Health/Endurance, talents, gold, **"255 Items"** — stack size/pickup hack) ([cheathappens.com](https://www.cheathappens.com/14344-PC-Witcher_The_(Enhanced)_cheats)); Cheat Engine table for EE 1.4.5 (GM, endurance, talents, orens) ([fearlessrevolution](https://fearlessrevolution.com/viewtopic.php?t=1432)); a +14 trainer that even toggles a "debug/console mode" and wireframe ([vgtimes](https://vgtimes.com/games/the-witcher/files/51449-trainer-14-1.5.0.1304-steamgog.html)) — indicating debug features exist in the exe but are not publicly documented for TW1. All memory-poking; none touch item structs.
- **Script-based item-granting mods** (the dominant "cheat" approach): Genie Wish Mod, Many Items, Convenient Shopkeeper (all by Corylea) grant existing items via `def_arealoaded.ncs` + `custom_script.ncs` calling `CreateItemOnObject` on templates ([Nexus 246](https://www.nexusmods.com/witcher/mods/246), [Nexus 340](https://www.nexusmods.com/witcher/mods/340), [Nexus 364](https://www.nexusmods.com/witcher/mods/364)) [B].
- **2DA mini-mods:** `baseitems.2da` stacking/slot unlocks (Flash's Mod, Sayne's, [Nexus 259](https://www.nexusmods.com/witcher/mods/259), [Greater Item Stacking, Nexus 736](https://www.nexusmods.com/witcher/mods/736)); movement speed via `creaturespeed.2da`/`moverates.2da` ([wiki](https://witcher-games.fandom.com/wiki/Movement_speed_mod)) [B].
- **Lua ability mini-mods:** difficulty/food-regen/starting-stats via `witcher_atr_abl.luc`→`.lua` in Override ([wiki](https://witcher-games.fandom.com/wiki/Difficulty_settings_mod), [Russian guide](https://witcher-world.ucoz.net/publ/the_witcher/sozdanie_modov/sozdanie_modov_bez_redaktora/6-1-0-38)) [A/B].
- **TW1 console/debug cheats:** unlike TW3, TW1 has no publicly documented in-game console/`additem` system [D-unresolved]; cheating is done with the above tools instead.

---

## 5. Damage math

- **Style abilities own the per-hit damage ranges.** The Strong Steel style tree carries `Damage 20–30` (level 1) → `40–80` → `80–160` → `130–260` (The Reaper special) → `160–320`, plus per-style armor reduction and effect chances ([GameBanshee — Strong Steel](https://www.gamebanshee.com/thewitcher/combatstyles/strongsteel.php); [witcher.fandom — Strong Steel](https://witcher.fandom.com/wiki/Strong_Steel)) [B]. These numbers live in the same static ability data (style abilities), not on items.
- **The weapon contributes bonuses via its abilities:** % damage, bleed/pain/stun/disarm/blind/knockdown/incineration, armor penetration, silver-sensitivity ([nightsolo.net weapons table](https://www.nightsolo.net/games/witcher1/weapons.html), [witcher.fandom — Rune swords](https://witcher.fandom.com/wiki/Rune_swords), [Meteorite swords](https://witcher-games.fandom.com/wiki/Meteorite_swords)) [B]. Meteorite/rune damage bonuses stack with style talents ([Steam discussion](https://steamcommunity.com/app/20900/discussions/0/1738841319802440234/) [B]).
- **Oils/whetstones/diamond dust** add % damage or armor penetration for the fight — implemented as runtime-added grease abilities ([FCR manual table: Specter Oil +20% dmg / 40% armor pen, etc.](https://www.scribd.com/document/201802553/Fcr-1-6-Manual-En) [A for FCR]; API `AddGreaseAbility` [A]).
- **Difficulty multiplies damage globally** via ability modifiers (`Difficulty_easy: Damage_Mult = 2`; `Difficulty_normal: 1.5`; hard = none) [A/B wiki].
- **The engine's damage effect takes abilities as input:** `EffectDamage(fDmgMin, fDmgMax, fPercentageDmg, sMedium, bCombatDamage, sAbilities="")` — "sAbilities: comma-separated names of additional abilities used in calculation" (comment signed by CDPR dev Maciej Sinilo) [A, nwscriptdefn.nss].
- **FCR's own formula (community-posted by an FCR player/modder, GOG forum):** "Strong is weapon, plus character bonuses, plus oils, plus variable damage (increasing with skill), less the beast's toughness, or reduced by its armour %. Fast is … plus 1 damage. … Group is … less 6 damage" ([GOG forum — Bug in FCR?](https://www.gog.com/forum/the_witcher/bug_in_fcr)) [B — describes FCR, not vanilla].

**Net:** what changes weapon damage is the **static ability definitions** (style levels, per-weapon bonuses, difficulty multipliers, oils) *combined* with *which* abilities are attached to the weapon instance. Nothing in the per-instance struct numerically scales damage except the ability-reference list itself (and `CustomCost` for price, not damage).

---

## 6. Can descriptions be made dynamic?

1. **Swap `TemplateResRef` on the instance.** The save item struct carries `TemplateResRef` [C], and the D'jinni manual shows the editor exposing it as a changeable pointer to any `.uti` in `Data\Templates\Items` [A]. Pointing an instance at a different template should change name, description, icon, abilities and stats wholesale — the strongest candidate for "dynamic" text. **No community precedent found; unresolved** [D]. Worth testing: change the rusty sword's `TemplateResRef` to `it_stlswd_014` (Harvall) and observe name/tooltip/stats in game.
2. **Inline text on the instance `LocalizedName` (LSTRING type 12, strref −1 + substring).** The format supports inline substrings when the string ref is −1 (NWN CExoLocString semantics; our own reader implements exactly this: substrings take precedence, else resolve strref ≥ 0 from `dialog.tlk`, else empty — `DBList.kt:108-132` [C]). **Notable probe result [C]:** the save's rusty sword has `LocalizedName = strref −1, empty` — yet the game displays "Rusty Sword" in game, which implies the engine **falls back to the template's name when the instance LSTRING is −1/empty**. Conversely, other save items evidently carry inline localized strings (our inventory panel reads and shows them directly, `InventoryPanel.kt:407` [C]). **No source documents whether the game prefers the instance string when it is non-empty** — plausible but unverified [D]. If it does, writing an inline substring is the only way to give a save item a custom tooltip name. The description (`Description` field) would need the same treatment. Community evidence: none found; nobody appears to have documented renaming items via save edits.
3. **Editing `.uti` templates + `dialog.tlk`/override text** is the robust, community-proven route for changed text (all item mods ship templates; `LoadTemplates.kt` override pattern) [B].

---

## Implications for TWEditorEnhanced

1. **Do not promise tooltip changes from per-instance edits.** Name/description/colors come from the `.uti` template; stat-line text comes from the Lua ability DB. This is expected behavior, not a bug. Consider labeling per-instance edits in the UI as "affects gameplay/looks/price — tooltip text is static".
2. **`WpnAbilitySelf`/`WpnAbilityOpp` instance edits are the real lever** — the game's own runtime API mutates exactly this state on item instances (oils via `AddGreaseAbility`; crit effects via `AddWeaponEffect`). Expected visible result of a "copy power" edit: the copied bonuses (e.g. 3× red meteorite = +40% damage, +30% bleeding) apply in **combat**, even though the tooltip won't show them. **Verify in-game once** (apply meteorite abilities to the rusty sword → hit a human → check damage/bleed); if it works, also test whether the tooltip lines update after re-equip/reload (settles Q1.3).
3. **Validate ability names against the ability DB** before writing them: the script API rejects invalid ability names (`AddAbility` returns 0), so the loader may ignore or choke on them. Ship a name list (extracted from the `scripts00.bif` Lua resource we already parse) and warn on unknown entries; treat cross-install copies (modded vs vanilla) as unsafe.
4. **Preserve `RnAbStk`** verbatim (likely stack/level; semantics unresolved). Round-trip tests should assert byte-equality of unrelated fields (already in `ItemEditTest.kt`).
5. **`ModelPart1`** is a safe cosmetic edit (runtime API `SetItemModelPart` proves per-instance icon/model is a supported concept) — changing it should swap the inventory icon/3D model, nothing else. Label it "appearance".
6. **`CustomCost`** — expose as "price (orens)"; no evidence it feeds tooltips. Note in UI that merchant sell-price behavior should be verified in-game.
7. **`Quality` and `WeaponType` are template-only** (absent from save structs per our probe) — do not attempt per-instance edits; if users want them changed, that requires `.uti`/D'jinni work outside the save editor.
8. **Most promising future feature:** a "re-skin" / "swap template" edit that rewrites the instance's `TemplateResRef` to another existing `.uti` (e.g., rusty sword → Harvall). Should change name, description, icon, abilities and stats in one move; must be tested in-game first (Q6.1), with the same round-trip + CRC safety we use elsewhere.
9. **Editing text (name/description)** for a single instance is speculative: format supports inline LSTRING substrings (strref −1 + text), and the −1/empty rusty-sword probe proves the game falls back to the template when the instance string is empty — but whether a non-empty inline string is *displayed* is unverified. Prototype behind an "experimental" flag, or leave to template edits.

---

## Innkeeper storage (the `StoreRef = "storage"` system)

The inn storage is **not** a `.utm` merchant store. Per the wiki's storage-mods page, shopkeepers and storage keepers share the creature field `StoreRef`: a shopkeeper's `StoreRef` names a `.utm` file, while a **storage keeper's `StoreRef` is the literal string `storage`** — an engine-managed, global store (which is why the stash is shared across every inn). "Stores and storage cannot co-exist in a single character template."

Save-side anatomy (probed on real saves, 15 files, all Kaer Morhen):

- Save archive entries: `player.utc`, one module `.sav` (RFILE holding `module.ifo` + area `.are`/`.git` pairs + triggers), `save_XXXXXX.smm`, `.qst`/`.qdb`, screenshot `.tga`. **No store/stash resource** (also zero resources matching inn/stor/stash/karcz/depozyt/schowek terms anywhere in the KEY-indexed game archives).
- The player's bags live in `player.utc`: `ItemList` (4 sub-bags) + `Equip_ItemList` — what the editor edits today.
- **Creature records inside area `.git` files carry the same inventory shape**: vesemir's record in `l08.git` has `ItemList`, `Equip_ItemList`, plus `StoreRef`/`StoreOID`. So any innkeeper instantiated in a saved area would carry its items in-record.
- The smm carries the 13 game modules (`Meta_Mod_list`: outskirts_1, wyzimpoor_1, sewers, marshland, wyzimrich_3, wyzimpoor_3, marshland_3, village_4, oldmanor_5, wyzimold_5, wyzimburn, ice_plains, kaer_morhen) plus persistent `StoryNPC_list`/`MonsterNPCs` records — **no inventory fields** on those records.
- All 15 local saves are from the `kaer_morhen` module, **which contains no inn** — the storage has nowhere to be in them, so the exact persistence point of the *contents* (innkeeper's in-area creature record vs a floating store object) is still unverified.
- The KEY/BIF split matters: `main.key` indexes only DDS/UTI/2DA; creature `.utc` templates, `.dlg`, `.ncs`, and area files live in **BIF-internal variable tables the editor never reads** (BIF V1.1: 20-byte entries = id/offset/length/type at header offsets 8/16). Any deeper stash work means parsing BIF-internal tables.

**Follow-up RESOLVED — the stash IS in the save (owner was right)**: with the inn save and the owner's exact item list (Barghest skull ×4, Temerian iron dagger, book, axe), a resref-accurate grep finds the chest: the stash lives in the save's meta database as **`smm/StoreList/<record>`** — the store object itself (`ObjectId` matching the innkeeper's `StoreOID`, `Template="storage"`, `IsStorage=1`) carrying an `ItemList` of plain item structs. The smm is per-save global state, which is exactly why the chest is shared by every innkeeper and persists with each save. (The first probe missed it: the grep terms were wrong — TW1 has no `it_axe` (it's `it_laxe`/`it_saxe`), the skull is `it_other_018` (other-class, not an ingredient), and the dagger is `it_dgr_001`.) **The storage chest is therefore editable**: `StoragePanel` (Storage tab) reads/edits that `ItemList` — add from the full template tree, remove, examine, per-instance edit — with a golden write/reload round-trip test (`StorageEditTest`) that also asserts every other archive entry stays unchanged. Saves that never used storage have no `StoreList` yet; the panel reports that and asks the player to use the chest once in-game first (synthesizing the store record is not attempted because the engine's ObjectId allocation is not understood).

---

## Equipment slots (the `WeaponSlot` field and the two 2DA tables)

Each equipped item struct in `Mod_PlayerList[0]/Equip_ItemList` carries **`WeaponSlot` = a row index into the game's `weaponslots.2da`** (28 rows), NOT a per-item-type enum. Verified empirically across the local saves (steel sword→1, silver→2, dagger→3, long axe→10, fists pseudo-item→11, armor→27) and confirmed by the table itself:

| Row | Slot | Flag | Notes |
|-----|------|------|-------|
| 0 | Hide | — | unequipped/pseudo |
| 1 | Back_Normal | 0x8000 | steel sword, right back |
| 2 | Back_Silver | 0x4000 | silver sword, left back |
| 3 | Short_1 | 0x10000 | sidearm position 1 |
| 4 | Short_2 | 0x20000 | sidearm position 2 |
| 5 | Trophy | 0x80000 | |
| 6 | Medalion | 0x100000 | the witcher medallion |
| 7–9 | Elixir_1..3 | 0x200000/0x400000/0x800000 | drunk potions |
| 10 | Big_Weapon | 0x40000 | long axes/hammers; **the steel sword's second position** |
| 11 | Fists | 0x1000000 | the fists pseudo-item (BaseItem 36, hidden by the editor) |
| 12–25 | belt/limb/head misc | various | Belt_Chest_front, Leg_Right, Forearm_R/L, Belt_Chest_back, Neck, Belt, Belt_*_Front, Belt_L/R, Arm_L/R, Head |
| 26 | Left_Hand | 0x20 | **development leftover** (owner: the torch actually takes a sidearm slot; the editor does not offer this slot) |
| 27 | Armor | 0x8000000 | |

**Which slots an item may occupy = the `EquipableSlots` bitmask column in `baseitems.2da`** (same flags): steel sword `0x48030` → back + big-weapon + left hand (the two visible steel positions the owner sees in-game are rows 1 and 10); silver `0x4030` → back + left hand; dagger/short weapons `0x30010` → Short_1/Short_2 (the extra `0x10` bit has **no** weaponslots row — ignore it); potions and bombs `0xE00000` → Elixir_1..3; armor `0x8000000`; trophy `0x80000`; rings `0x88` → **Forearm_Right(14)/Forearm_Left(16)** (the paperdoll's wrist slots); necklaces `0x0` → **not equippable at all** in TW1; the medallion item class `0x100000`. The player struct also carries `WeapLastSlot` (the currently drawn weapon).

**CORRECTION (owner, overriding the earlier socket speculation): nothing in the game is upgradeable** — armor has **no** upgrade sockets, and no item does; runes and "upgrade components" either provide a temporary bonus or serve as crafting ingredients. No socket model is needed; the baseitem classes (`upgrade_component` 34, rune armors `it_ran_*`) are ordinary items, not socket payloads.

**Editor support**: `WeaponSlots.kt` embeds the row table (names + flags) and reads `EquipableSlots` from the game's `baseitems.2da`; EquipPanel shows the game's paperdoll as 12 slot rows (steel, silver, short ×2, big weapon, armor, trophy, rings ×2, elixirs ×3 — no Left_Hand) with each row populated or `(empty)`, duplicates grouped into one row (`Big weapon (x6)`, Remove peels one per click), *Move to Slot* (combo filtered by the item's mask and the paperdoll rows), *Remove*, and Add-into-the-selected-empty-slot (the template's mask must accept that slot; potions/bombs go in elixir slots carrying `StackSize = MaxStack`, rings/amulets in the forearm slots — one item per slot, enforced). Golden round-trip tests (`EquipSlotEditTest`, gated on the owner's save 30): the slot view's populated/empty rows, a slot move, and a second steel sword in the big-weapon position persist through write/reload with all other archive entries byte-identical. The storage chest displays in plain list order (all structs sit at `Repos 0,0` — no grid), so StoragePanel's **Sort Chest** rewrites the struct order by type (the storage tab order), then name/resref/stack, verified by `StorageEditTest`'s round trip.

---

## Source register


First-party / official:
- D'jinni editor manual (official docs, community-mirrored): item editor fields — https://djinni.fandom.com/wiki/Module_and_area_creation
- Game script API (`nwscriptdefn.nss`, archived): https://web.archive.org/web/20080502225556id_/http://www.witchermod.com:80/djinni/nwscriptdefn.nss (pages: [AddGreaseAbility](https://djinni.fandom.com/wiki/AddGreaseAbility), [AddWeaponEffect](https://djinni.fandom.com/wiki/AddWeaponEffect), [SetItemModelPart](https://djinni.fandom.com/wiki/SetItemModelPart), [Function reference index](https://djinni.fandom.com/wiki/D%27Jinni_Function_Reference))
- Official game manual (combat/styles): https://ftpmirror.your.org/pub/misc/ftp.atari.com/manuals/pc/witcher/WitcherManual.pdf
- CDPR official news on FCR: https://www.thewitcher.com/en/news/636/full-combat-rebalance-mod-for-the-first-witcher-game
- File formats (official wiki): https://witcher.fandom.com/wiki/File_format , https://witcher-games.fandom.com/wiki/LUC_format , https://witcher.fandom.com/wiki/TheWitcherSave_format
- FCR 1.6 manual (FCR first-party): https://www.scribd.com/document/201802553/Fcr-1-6-Manual-En

Community-primary:
- Ability DB override procedure: https://witcher-games.fandom.com/wiki/Difficulty_settings_mod ; https://witcher-world.ucoz.net/publ/the_witcher/sozdanie_modov/sozdanie_modov_bez_redaktora/6-1-0-38
- ScripterRon TWEditor readme + forks: https://docs6.chomikuj.pl/1409990907,PL,0,0,TWEditor_ReadMe.txt ; https://github.com/boazy/TWEditorEnhanced ; https://github.com/cloudskytian/TWEditorEnhancedCN ; https://www.gamepressure.com/download/the-witcher-enhanced-edition-tweditorenhanced-savegame-editor-v3/z1137ea
- Save-editing usage guide: https://steamcommunity.com/sharedfiles/filedetails/?id=340782606
- Weapon stat tables: https://www.nightsolo.net/games/witcher1/weapons.html ; https://witcher.fandom.com/wiki/Rune_swords ; https://witcher-games.fandom.com/wiki/Meteorite_swords ; https://witcher.fandom.com/wiki/Strong_Steel ; https://www.gamebanshee.com/thewitcher/combatstyles/strongsteel.php
- FCR description/community formula: https://www.moddb.com/mods/full-combat-rebalance1 ; https://witcher-games.fandom.com/wiki/Full_Combat_Rebalance ; https://www.gog.com/forum/the_witcher/bug_in_fcr ; https://www.nexusmods.com/witcher/mods/811 (FCR Hard Overhauled changelog) ; https://www.nexusmods.com/witcher/mods/1101 (Sword Stats Rebalance)
- Item/cheat mods: https://www.nexusmods.com/witcher/mods/246 (Genie Wish) ; https://www.nexusmods.com/witcher/mods/340 (Many Items) ; https://www.nexusmods.com/witcher/mods/364 (Convenient Shopkeeper) ; https://www.nexusmods.com/witcher/mods/259 and https://witcher-games.fandom.com/wiki/Item_stacking_mod (baseitems.2da) ; https://www.cheathappens.com/14344-PC-Witcher_The_(Enhanced)_cheats ; https://fearlessrevolution.com/viewtopic.php?t=1432
- Meteorite/style stacking discussion: https://steamcommunity.com/app/20900/discussions/0/1738841319802440234/
- D'jinni item list (all sword template IDs): https://djinni.fandom.com/wiki/Items
- TW1 forum (template editing workflow): https://forums.cdprojektred.com/index.php?threads/tw1-editing-utc-files-in-the-djinni-editor-inventory-contents.7456830/

Our own probes (TWEditorEnhanced codebase / real saves):
- Save item structs: `WpnAbilitySelf`/`WpnAbilityOpp` (`STRUCT 0xBABE`: `RnAbName`+`RnAbStk`), `ModelPart1`, `CustomCost` present; `Quality`/`WeaponType` absent; rusty sword `LocalizedName` = strref −1/empty. Ability DB binary (names + floats, labels `Damage_Mod`, `Silver_Mult`, `Parry_Mult`, e.g. `meteorite_red1_self`) inside `scripts00.bif`; no `abilities.2da` in `main.key`/`2da00.bif`.
- Innkeeper storage probes (2026-09): 15 local saves scanned (all `kaer_morhen` module) — no store/stash resource or object tags; `l08.git` vesemir record carries `ItemList`/`Equip_ItemList`/`StoreRef`/`StoreOID`; smm `Meta_Mod_list` = 13 module names; `main.key` indexes only DDS(8826)/UTI(1100)/2DA(223). Mechanism per https://witcher.fandom.com/wiki/Storage_mods (`StoreRef="storage"`).
- Editor behavior: `src/main/kotlin/app/tweditor/ItemEdit.kt`, `ItemEditDialog.kt`, `DBList.kt` (LSTRING precedence), `InventoryPanel.kt`, `LoadTemplates.kt`, tests `src/test/kotlin/app/tweditor/ItemEditTest.kt`.

Explicitly unresolved:
- Whether the in-game tooltip renders ability lines from the instance list or the template list (Q1.3).
- Runtime effect of `Quality` (and whether it ever drove name color in TW1).
- Use of instance `CustomCost` for equipped/player items.
- `RnAbStk` semantics.
- Loader behavior for unknown ability names in save lists.
- Whether the game displays a non-empty inline `LocalizedName`/`Description` substring from the instance (and whether anyone has tried `TemplateResRef` swapping on an instance).
- Existence/extent of a TW1 built-in debug console (trainers imply debug modes exist; no public command list found).
