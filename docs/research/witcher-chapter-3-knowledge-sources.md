# The Witcher EE: monster and ingredient knowledge available by Chapter III

## Scope and confidence

This is an acquisition map for the original *The Witcher: Enhanced Edition*, limited to material obtainable no later than Chapter III. It is intended to be joined against the newest Save's missing Journal entries; it does not claim that every listed monster is required during Chapter III.

The installed game's `journal.2da`, item templates, English TLK, and Save structures are the primary evidence for entry IDs/names and storage. Vendor inventories, free placements, and dialogue alternatives are cross-checked against two longstanding community reference tables: [Mike's RPG Center book list](https://mikesrpgcenter.com/witcher/items/books.html), [Gamepressure books/scrolls part 1](https://www.gamepressure.com/thewitcher/books-and-scrolls-p-1/z7f10), [part 2](https://www.gamepressure.com/thewitcher/books-and-scrolls-p-2/z8f11), and the [Witcher Wiki dialogue-only guide](https://witcher.fandom.com/wiki/Guide_for_illiterate_witchers). The installed manual confirms the core mechanic: right-clicking a tome that advertises alchemical information teaches its formulas ([official manual mirror](https://ftpmirror.your.org/pub/misc/ftp.atari.com/manuals/pc/witcher/WitcherManual.pdf)).

## Best Chapter III move: get *Physiologus*

After the bank robbery, find the townsman walking around John Natalis Square / the roads leading into it, generally after sleeping until about 14:00. In Enhanced Edition he gives Geralt **Physiologus for free** (the pre-EE surprise 1,000-orens charge was removed). It is a comprehensive bestiary and supplies the monster/harvest knowledge that would otherwise require many specialist monster books. The Royal Huntsman also alludes to such an all-monsters family book, but that conversation is lore, not the acquisition trigger. See the [Physiologus location and EE note](https://witcher-games.fandom.com/wiki/Physiologus).

Practical consequence: if that event is already available, do it before buying specialist monster books. It does **not** replace the plant/mineral books below.

### Applied to the current newest Save

The probed Save is `000746 - Храмовый квартал-743.TheWitcherSave`, `StoryPhase=Act_3`. It **already carries Physiologus** and has `bestiary:witchers/phis`; do not acquire another copy. Right-click/read the carried copy if it is still usable/unread before spending money, then re-check the Journal. Its catalog comparison leaves these named gaps:

- Currently useful/obtainable: **Ifrit** (*The Last Wish*, 800; optional because Pyrite overlaps the mineral book), **Koshchey** (*The Road of No Return*, 150), and **Vodyanoi Warrior** (*A Description of the Vodyanoi*, 600). The raw bestiary catalog also reports `vetal` absent, but the Save already has `character:vetala/info` and has completed *Six Feet Under*. The in-game Characters entry is the normal story-facing Vetala record in this timeline; do not treat the absent raw bestiary row as missing practical knowledge.
- Not useful to buy for Chapter III: **Dagon** and **Bride** are Ch. IV content; **Armored Hound** is later mutant content. `exfast`, `exstrong`, `mnfast`, `mnstrong`, and `skintest` look like internal/raw catalog rows, not normal shopping targets.

Its efficient ingredient shopping list is:

1. **Great Book of Minerals** — fills Sulfur, Wine Stone, Phosphorus, Powdered Pearl, Pyrite, Optima Mater, Fifth Essence, Ducal Water, Lunar Shards, and both catalog identities for Quicksilver Solution (plus other overlaps).
2. **Ritual Plants** — fills Allspice, Ergot, Wolfsbane, Mandrake, Han, Hop, and Mistletoe.
3. **Plants of Barren Lands** — fills Bryonia, Honeysuckle, and Ginatia (the Save already knows its other entries).
4. **A Description of the Vodyanoi / Fishpeople** and **The Road of No Return** — only if Vodyanoi Warrior/Koshchey and their harvest entries remain missing after reading Physiologus.
5. Do **not** buy **Book of Animals** for the catalog's `unique13` Dog Tallow row. Dog tallow is a grease base rather than a normal alchemical ingredient, and the Save already has `bestiary:dog/s/1`, the knowledge that controls dog harvesting ([Chapter I shopping note](https://witcher.fandom.com/wiki/The_Witcher_Shopping_List/Chapter_I)).
6. Do **not** buy **Wonderful World of Insectoids** for `unique4`. The Save already has Kikimore Worker/Warrior/Queen and `quebrith:quebrith12` (Kikimore Claw). The second catalog row resolves to the same display name and is not evidence of missing practical harvesting knowledge.

Do **not** buy *The Last Wish* merely for Pyrite: the Great Book supplies it. Buy it only if the Ifrit entry itself matters now. Dagon Secretions are tied to *Hymns of Madness and Despair*, Vaska's **Chapter III** *Reaping Time* reward; this Save has not started that quest, so it remains obtainable now. Mutagen and Pituitary Gland come with later mutant material (*Experiment Notes* / *Greater Brothers*) and are not realistic Chapter III targets.

## Specialist monster books (all purchasable by Chapter III)

| Book | Knowledge supplied | Chapter III source (or earlier free source) |
|---|---|---|
| *Barghests* | Barghest; Beast Fangs, Ectoplasm, Death Dust | Earlier: Outskirts antiquary or Abigail, 50 |
| *Book of Animals* | Dog, Wolf; Beast Fangs, Beast Liver | Earlier: Outskirts antiquary/Abigail or Temple Quarter antiquary, 100 |
| *Tome of Fear and Loathing, Vol. I* | Ghoul, Graveir; White Vinegar, Ghoul Blood, Cadaverine, Abomination Lymph, Graveir Bones | Free earlier by outdrinking the Outskirts Inn drunkard; otherwise antiquary, 250 |
| *Tome of Fear and Loathing, Vol. II* | Alghoul, Cemetaur, Devourer; their unique parts plus common necrophage substances | Temple Quarter antiquary; also Bookseller/Kalkstein by Chapter III, 500 |
| *Swamp Monsters* | Drowner, Drowned Dead, Bloedzuiger; their unique parts plus common necrophage substances | Free earlier in an Outskirts hut; another copy in Kalkstein's cellar in Ch. III; vendors, 200 |
| *Ornithosaurs* | Cockatrice, Basilisk, Wyvern, Royal Wyvern; Cockatrice Feather/Eye, Toxin, Venom Glands, Wing Membrane | Temple Quarter antiquary/Kalkstein, Trade Quarter Bookseller, Ch. III tower Kalkstein, 400 |
| *Specters, Wraiths and the Damned* | Wraith, Noonwraith, Nightwraith, Wild Hunt; Ectoplasm, Death Dust, Shimmering Dust, Shadow Dust | Free in Order Guardhouse chest; otherwise antiquary/Kalkstein/Bookseller, 400 |
| *Curses and the Cursed/Damned* | Echinops, Archespore, Werewolf, Striga; unique parts and Spores | Temple Quarter antiquary or Trade Quarter Bookseller, 500; free only at end-Ch. III Salamandra Base |
| *Vampires: Facts and Myths* | Fleder, Garkain, Alp, Bruxa; unique parts plus Abomination Lymph, Wing Membrane, Naezan Salts | Trade Quarter Bookseller, 600 (the free copy is Ch. IV, too late) |
| *Wonderful World of Insectoids* | Kikimore Worker/Warrior/Queen, Giant Centipede; Tracheae, Kikimore Claw, Queen Nerve, Toxin, Venom Glands | Trade Quarter Bookseller, 600 |
| *Animating the Inanimate* | Golem; Golem's Obsidian Heart | Temple Quarter antiquary or Trade Quarter Bookseller, 150 |
| *The Last Wish* | Ifrit; Pyrite, Ectoplasm | Trade Quarter Bookseller, 800 (free copy is Ch. V, too late) |
| *A Description of the Vodyanoi / Fishpeople* | Vodyanoi Warrior/Priest; Scales, Bladder, Stones of Ys, Tendons | Trade Quarter Bookseller, 600 (free reward is Ch. IV, too late) |
| *The Road of No Return* | Koshchey; Koshchey Heart | Temple Quarter antiquary or Trade Quarter Bookseller, 150 |

Two one-off early sources matter if the corresponding entries are absent: Vesemir supplies Frightener knowledge in the Prologue (and *The Frightener* is in an upstairs Kaer Morhen chest); Abigail gives **Berengar's Notes on the Beast** near the end of *Of Monsters and Men*, teaching Hellhound and Trace of the Beyond. The item/effect/source table is corroborated in the references above; the Chapter III monster roster is summarized by [Monsters by Chapter](https://witcher.fandom.com/wiki/The_Witcher_monsters).

## Plant and mineral ingredient knowledge

Buy only the volumes that cover entries still missing; overlaps are substantial.

| Book | Ingredient entries | Available by Chapter III |
|---|---|---|
| *Field Plants* | White Myrtle, Hellebore, Celandine, Balisse, Crow's Eye, Berbercane, Sewant | Many Ch. I–III herbalists/antiquaries; 200 |
| *Subterranean Plants* | Sewant Mushroom, Green Mold | Ch. I herbalist; Temple Quarter antiquary/herbalist; Trade Quarter Bookseller; 300 |
| *Swamp Plants* | Celandine, Beggartick, Fool's Parsley | Free dialogue with a Brickmaker in the swamp, or Temple/Trade vendor; 400 |
| *Ritual Plants* | Allspice, Ergot, Wolfsbane, Mandrake, Han, Hop Umbels, Mistletoe | Temple antiquary/Elder Druid/Trade Bookseller; 400 |
| *The Druid's Herbarium* | Hellebore, Allspice, Wolf's Aloe, Verbena, Mistletoe, Ginatia | Elder Druid, Trade Bookseller/Zerrikanian Trader; 600 |
| *Plants of Barren Lands* | Wolf's Aloe, Bryonia, Verbena, Honeysuckle, Ginatia | Trade Bookseller/Zerrikanian Trader; 600 (free copy is Ch. IV, too late) |
| *Small Book of Minerals* | Sulfur, Ginatz's Acid, Wine Stone, Naezan Salts, Calcium Equum, Phosphorus, White Vinegar | Temple/Trade merchants and Kalkstein; 400 |
| *Great Book of Minerals* | Everything in the small volume plus Powdered Pearl, Pyrite, Optima Mater, Fifth Essence, Ducal Water, Albar's Crystals, Lunar Shards, Quicksilver Solution | Trade Quarter Alchemist; 600 |
| *Feainnewedd* | Feainnewedd | Temple Quarter antiquary or swamp Elder Druid; 300 |

If both mineral books are unread, the **Great Book** is the efficient Chapter III purchase because it subsumes the Small Book's mineral set (community tables disagree only on whether White Vinegar is described with the small volume; White Vinegar is also supplied by necrophage books). Plant tomes do not unlock the six alchemical substance categories themselves—Vitriol, Rebis, Aether, Quebrith, Hydragenum, Vermilion are how ingredient entries are grouped in the Save/catalog, not six books to seek.

Mineral knowledge does not unlock a harvesting node: these minerals are bought or looted, not gathered like herbs or cut from monster corpses. Reading the Great Book identifies the listed materials and the alchemical substances they contain, making them intelligible/selectable as alchemy ingredients. Its value is therefore flexibility and substitution in brewing, not increased drops or a new gathering action.

## Dialogue, talents, and other no-purchase routes

- Generic NPC dialogue and gift-giving can teach knowledge. By the end of Ch. I it can cover Balisse, Beggartick, Berbercane, Hellebore, Sewant, Verbena, White Myrtle, Wolf's Aloe and Drowner/Ghoul/Graveir/Echinops/Barghest; Ch. II adds Crow's Eye, Fool's Parsley and Cockatrice plus one of Drowned Dead/Fleder; Ch. III dialogue adds Archespore, Basilisk, Kikimore Worker, Kikimore Warrior and Wyvern. The dialogue guide warns that Drowned Dead versus Fleder may be mutually exclusive: [chapter-by-chapter dialogue-only table](https://witcher.fandom.com/wiki/Guide_for_illiterate_witchers).
- The swamp Brickmaker explicitly teaches all three *Swamp Plants* entries, so do that before buying the book ([Gamepressure part 2](https://www.gamepressure.com/thewitcher/books-and-scrolls-p-2/z8f11)).
- The bronze **Monster Lore** talent under Intelligence directly grants selected not-yet-encountered monster knowledge; it is not merely a loot bonus. Early reports specifically identify Barghest, Ghoul, Graveir, and Drowner, with more becoming available as progression advances ([attribute description](https://www.gamebanshee.com/thewitcher/attributes/intelligence.php), [Ghoul acquisition note](https://witcher.fandom.com/wiki/Ghoul)).
- Quest/event rewards also teach or hand over knowledge: Vesemir (Frightener), Abigail (Hellhound), Vaska's Ch. III *Reaping Time* reward (*Hymns of Madness and Despair*, Dagon knowledge), and the end-Ch. III boss loot. The latter arrive too late to improve most Chapter III harvesting, so they should not displace timely books/dialogue.

## What “saved differently” means

There is no single `knownIngredients` or `knownMonsters` flag.

- Ordinary Journal knowledge lives in the Save's `Journal` list as records with an `Entry` string plus `EntryCD`, `EntryTOD`, and `EntryRead`. The editor parser splits strings such as `bestiary:ghoul/s/1` into category `bestiary` and entry ID `ghoul/s/1`; see [`JournalData.kt`](../../src/main/kotlin/app/tweditor/JournalData.kt) and [`GameSession.kt`](../../src/main/kotlin/app/tweditor/GameSession.kt).
- Monster rows can occur in several catalog variants (`<monster>/s/1`, `/w/1`, `/b/1`). The editor deliberately groups all variants by the prefix before `/` when deciding whether the monster is known. Its observed grant shape is the book-style `bestiary:<id>/s/1`; see [`JournalCommand.kt`](../../src/main/kotlin/app/tweditor/JournalCommand.kt) and the round-trip test [`MonsterKnowledgeCommandTest.kt`](../../src/test/kotlin/app/tweditor/MonsterKnowledgeCommandTest.kt). The suffix is therefore part of the journal-page identity, not a safe standalone “acquisition method” flag.
- Ingredient Journal records are catalog entries grouped under six substance categories (`hydragenum`, `vermilion`, `rebis`, `quebrith`, `aether`, `vitriol`) plus catalog-dependent `unique` rows. Their display names often resolve through the corresponding item template rather than the raw category/ID; see [`JournalViewModel.kt`](../../src/main/kotlin/app/tweditor/JournalViewModel.kt) and [`JournalCommand.kt`](../../src/main/kotlin/app/tweditor/JournalCommand.kt).
- Reading/acquiring a book has separate provenance/state (`READBOOK_lst`) from the Journal rows that the book teaches. That is why two Saves can show the same usable knowledge but differ in read-book history, timestamps, read/unread flags, or page variants. The project intentionally does **not** fabricate a `READBOOK_lst` record when it grants knowledge; see [`0007-journal-editing-safety-boundary.md`](../adr/0007-journal-editing-safety-boundary.md) and [`FormulaCommandTest.kt`](../../src/test/kotlin/app/tweditor/FormulaCommandTest.kt).

For purchase advice, compare semantic knowledge (monster prefix; ingredient catalog identity), not `READBOOK_lst` alone. A book can be marked read while some Journal state is unusual, and knowledge can exist from talent/dialogue without that book ever appearing in read history.
