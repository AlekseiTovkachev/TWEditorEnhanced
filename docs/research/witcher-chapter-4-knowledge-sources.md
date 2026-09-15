# The Witcher EE: Chapter IV knowledge gaps in Save 000917

## Scope and evidence

This report applies to `000917 - Деревня-917.TheWitcherSave` (`StoryPhase=Act_4`). It compares that Save's Journal against the installed game's effective `journal.2da` catalog, then separates real, player-facing gaps from duplicate, internal, later-chapter, and non-Journal records.

The Save/catalog facts come from the repository's read-only `ChapterFourKnowledgeProbeTest` and the installed game resources at `C:\Games\The Witcher Enhanced Edition\Data`. Acquisition routes are cross-checked against the [Witcher Wiki dialogue-only table](https://witcher.fandom.com/wiki/Guide_for_illiterate_witchers), individual entry/source pages, the [Mike's RPG Center book table](https://mikesrpgcenter.com/witcher/items/books.html), and the [Great Book of Minerals entry](https://witcher.fandom.com/wiki/The_Great_Book_of_Minerals).

## Recommendation

1. **Buy and read *The Great Book of Minerals* (600 orens) from the Hermit in the Fields.** It teaches every real, currently obtainable ingredient entry missing from this Save: **Ducal Water, Fifth Essence, Lunar Shards, Optima Mater, Phosphorus, Powdered Pearl, Pyrite, Quicksilver Solution, Sulfur, and Wine Stone**. The same book is also listed at the Elder Druid in the Swamp Cemetery, but the Chapter IV Hermit is the immediate source. It is explicitly more economical than the small mineral book because it contains that book's entire set plus the advanced minerals ([contents and locations](https://witcher.fandom.com/wiki/The_Great_Book_of_Minerals)).
2. **Talk to the female innkeeper in the Murky Waters Country Inn about the vodyanoi.** This free Chapter IV conversation teaches **Vodyanoi Warrior** and its harvest knowledge ([Vodyanoi Warrior sources](https://witcher.fandom.com/wiki/Vodyanoi_warrior)). If that dialogue is exhausted or unavailable, buy/read ***A Description of the Vodyanoi or the Fishpeople*** from the Hermit for 600 orens. *Old Habits Die Hard* could award the same book, but this Save has already completed that quest without taking/retaining it, so it is no longer the actionable free route ([quest reward choice](https://www.gamebanshee.com/thewitcher/walkthrough/oldhabitsdiehard.php)).
3. **Optional completion only:** buy/read ***The Last Wish*** from the Hermit for 800 orens to add **Ifrit**. Ifrit is a genuine bestiary entry, but there is no Chapter IV contract or unique harvest part that makes it urgent; Pyrite is already covered by the Great Book. The next free dialogue source is not until Chapter V ([Ifrit sources](https://witcher.fandom.com/wiki/Ifrit)).

No currently carried book addresses these gaps: the Save carries only *Ithlinne's Prophecy*, *Ballads*, and *Fairytales and Stories*.

## Missing monster rows classified

| Catalog row | Classification | What to do in Chapter IV |
|---|---|---|
| `vodwar` — Vodyanoi Warrior | Real, actionable harvest knowledge | Female Country Inn innkeeper dialogue for free; otherwise *A Description of the Vodyanoi* from the Hermit |
| `ifryt` — Ifrit | Real, but not useful in Chapter IV | Optional *The Last Wish* purchase from the Hermit; safe to defer |
| `bride` — Midday Bride | Real named story/boss row, but no remaining practical acquisition need | *The Heat of the Day* is already complete. The Midday Bride is tied to that quest and supplies no separate unique harvest ingredient; do not buy anything for it ([associated quest](https://witcher.fandom.com/wiki/Midday_bride)) |
| `vetal` — Vetala | Misleading category comparison | The Save already has the normal story-facing `character:vetala/info` record. Do not treat the absent raw bestiary row as missing usable knowledge and do not buy anything |
| `ordhound` — Armored Hound | Later mutant content | Chapter V/later; not actionable now |
| `exfast`, `exstrong`, `mnfast`, `mnstrong`, `skintest` | Internal/test catalog rows | Not player-facing knowledge; ignore |

The Chapter IV dialogue-only reference also lists Alp, Bruxa, Cemetaur, Devourer, Garkain, Noonwraith, Vodyanoi Warrior, and Vodyanoi Priest as learnable through conversations. This Save already knows all except Vodyanoi Warrior, so buying *Vampires: Facts and Myths* or necrophage books would be redundant. See the [Chapter IV dialogue table](https://witcher.fandom.com/wiki/Guide_for_illiterate_witchers).

## Missing ingredient rows classified

### Real gaps filled by one book

The Great Book of Minerals resolves these raw rows:

| Display name | Raw Journal/catalog identity |
|---|---|
| Ducal Water | `quebrith:quebrith10` |
| Fifth Essence | `aether:aether9` |
| Lunar Shards | `rebis:rebis11` |
| Optima Mater | `quebrith:quebrith14` |
| Phosphorus | `vermilion:vermilion4` |
| Powdered Pearl | `aether:aether12` |
| Pyrite | `vermilion:vermilion12` |
| Quicksilver Solution | `aether:aether14` and `hydragenum:hydragenum4` |
| Sulfur | `quebrith:quebrith3` |
| Wine Stone | `rebis:rebis3` |

Quicksilver Solution appears twice because the catalog represents the same named material under two alchemical-substance categories. It is **one semantic ingredient and one book purchase**, not two missing objects.

### Not real current ingredient gaps

- `unique13` **Dog Tallow**: this is an oil/grease base, not a normal learned alchemical ingredient entry. The Save already knows `bestiary:dog/s/1`, which is the practical prerequisite for harvesting it. Do not buy *Book of Animals* for this raw `unique` row. The game guide's Chapter I note confirms that dog tallow drops after learning Dogs from *Book of Animals*; the knowledge gate is the dog bestiary entry, which this Save already has ([drop prerequisite](https://witcher.fandom.com/wiki/The_Witcher_Shopping_List/Chapter_I)).
- `unique4` duplicate **Kikimore Claw**: the Save already contains the real ingredient record `quebrith:quebrith12` and knows Kikimore Worker, Warrior, and Queen. `unique4` resolves to the same display name and is not evidence that harvesting is missing. Do not buy *Wonderful World of Insectoids* again.
- `hydragenum3` **Mutagen** and `hydragenum10` **Pituitary Gland**: these belong to later mutant enemies/books (*Experiment Notes* and *Greater Brothers*). They are Chapter V knowledge, so they are outside the user's current Chapter IV scope and cannot justify a current purchase.

The Save already contains `unique7` Dagon Secretions and `unique8` Koshchey Heart, so the completed *Reaping Time* and the current *Ripples* progression have not left those as knowledge gaps. The active *Temptation* quest can still award *Plants of Barren Lands*, but the Save has no missing plant entries from that book; choose it only for collection/completion, not knowledge efficiency.

## Why raw catalog subtraction over-reports missing knowledge

The game's Save does not use one Boolean per displayed ingredient or monster. Journal records are category-qualified strings, and the catalog can contain multiple rows that resolve to one display concept. The editor therefore needs semantic interpretation:

- Monster variants are grouped by the monster prefix before `/`; see [`JournalCommand.kt`](../../src/main/kotlin/app/tweditor/JournalCommand.kt) and [`MonsterKnowledgeCommandTest.kt`](../../src/test/kotlin/app/tweditor/MonsterKnowledgeCommandTest.kt).
- Ingredient rows live under six substance categories plus catalog-dependent `unique` rows, and names can resolve through item templates; see [`JournalViewModel.kt`](../../src/main/kotlin/app/tweditor/JournalViewModel.kt) and [`JournalCommand.kt`](../../src/main/kotlin/app/tweditor/JournalCommand.kt).
- Character knowledge and bestiary knowledge are separate categories. Thus `character:vetala/info` is meaningful even when a raw `bestiary:vetal/...` catalog row is absent.
- Book provenance (`READBOOK_lst`) is separate again from Journal knowledge. A missing read-book record does not prove missing usable knowledge, and dialogue can create knowledge without book provenance; see [`0007-journal-editing-safety-boundary.md`](../adr/0007-journal-editing-safety-boundary.md).

For this Save, the reliable shopping result is therefore **Great Book of Minerals**, not every title associated with every raw catalog difference.
