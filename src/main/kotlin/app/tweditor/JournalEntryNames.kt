package app.tweditor

class JournalEntryNames {
    companion object {
        private val PREFIX_NAMES = HashMap<String, String>()

        init {
            put("jaskier", "Dandelion")
            put("zygfryd", "Siegfried")
            put("leuvaarden", "Declan Leuvaarden")
            put("rayla", "White Rayla")
            put("raymond", "Raymond Maarloeve")
            put("blueeyed", "Blue-eyed lass")
            put("candf", "Codringher and Fenn")
            put("celina1", "Celina")
            put("vamp1", "Vampiress")
            put("wett", "Roderick de Wett")
            put("wildhunt", "King of the Wild Hunt")
            put("magister", "The Magister")

            put("bargh", "Barghest")
            put("drown1", "Drowner")
            put("drown2", "Drowned dead")
            put("ghoul", "Ghoul")
            put("alghul", "Alghoul")
            put("alp", "Alp")
            put("arch", "Archespore")
            put("basil", "Basilisk")
            put("bloed", "Bloedzuiger")
            put("bruxa", "Bruxa")
            put("cment", "Cemetaur")
            put("cocat", "Cockatrice")
            put("dog", "Dog")
            put("echin", "Echinops")
            put("fled", "Fleder")
            put("gark", "Garkain")
            put("grav", "Graveir")
            put("hag", "Devourer")
            put("hellh", "Hellhound")
            put("ifryt", "Ifrit")
            put("kkqeen", "Kikimore queen")
            put("kkwar", "Kikimore warrior")
            put("kkwor", "Kikimore worker")
            put("kosh", "Koshchey")
            put("polud1", "Midday bride")
            put("polud2", "Noonwraith")
            put("scolo", "Giant centipede")
            put("skolop", "Giant centipede")
            put("striga", "Striga")
            put("vodprst", "Vodyanoi priest")
            put("vodwar", "Vodyanoi warrior")
            put("werew", "Werewolf")
            put("wiver1", "Wyvern")
            put("wiver2", "Royal wyvern")
            put("wolf", "Wolf")
            put("wraith", "Wraith")
            put("zeugl", "Zeugl")
            put("golem", "Golem")
            put("dagon", "Dagon")
            put("ordhound", "Armored hound")
            put("fright", "Frightener")
            put("q0001_frightener", "Frightener")
            put("q0001_frigh", "Frightener")
            put("spectr", "Specter")
            put("boneh", "Bonehead")
            put("bride", "Midday bride")
            put("vetal", "Vetala")
            put("vetala", "Vetala")
            put("dwimeryt", "Dwimeryt")
            put("cannibal", "The Cannibal")

            put("wizards", "Mages")
            put("medicine", "Medical Science")
            put("sfere", "Conjunction of the Spheres")
            put("flame", "Cult of the Eternal Fire")
            put("melitele", "Cult of Melitele")
            put("lodge", "Lodge of Sorceresses")
            put("scoiatael", "Scoia'tael")
            put("fisstech", "Fisstech")
            put("catriona", "Catriona")
            put("dwarves", "Dwarves")
            put("gnomes", "Gnomes")
            put("elves", "Elves")
            put("redania", "Redania")
            put("temeria", "Temerian History")
            put("witchers", "Witchers")
            put("dimeritium", "Dimeritium")
            put("gifts", "Gifts")
            put("destiny", "Destiny")
            put("magic", "Magic")
            put("history", "Temerian History")
            put("nilfgaard", "Nilfgaard")

            put("kaer", "Kaer Morhen")
            put("kaerlab", "Laboratory")
            put("wyzim", "Vizima")
            put("wyzimold", "Old Vizima")
            put("wyzimrich", "Trade Quarter")
            put("wyzimpoor", "The Outskirts")
            put("wyzemb", "Temple Quarter")
            put("wyznar", "The New Narakort")
            put("wyzhosp", "St. Lebioda's Hospital")
            put("wyzbank", "Vivaldi Bank")
            put("wyzbroth", "House of the Queen of the Night")
            put("wyzcastle", "Foltest's castle")
            put("wyzguard", "Guardhouse")
            put("wyzjail", "Dungeon")
            put("wyzshani", "Shani's house")
            put("wyztriss", "Triss' house")
            put("wyzraymond", "Detective's house")
            put("outskirts", "The Outskirts")
            put("marshes", "Swamp")
            put("marshtower", "Mage's tower")
            put("oldmanor", "Old Manor")
            put("outinn", "Country Inn")
            put("outcrypt", "Crypt in the outskirts")
            put("island", "Black Tern Island")
            put("fields", "Fields")
            put("zerrikania", "Zerrikania")
            put("canals", "Vizima sewers")
            put("villageinn", "Inn")
            put("shore", "Lakeside")
            put("iceplains", "Ice plains")
            put("order", "The Cloister of the Order")
        }

        private fun put(prefix: String, name: String) {
            PREFIX_NAMES[prefix] = name
        }

        fun displayName(entryId: String): String {
            val sep = entryId.indexOf('/')
            val prefix = if (sep > 0) entryId.substring(0, sep) else entryId
            val mapped = PREFIX_NAMES[prefix.lowercase()]
            if (mapped != null) {
                return mapped
            }

            val tutorial = Regex("tutorial(\\d+)", RegexOption.IGNORE_CASE).find(prefix)
            if (tutorial != null) {
                return "Tutorial " + tutorial.groupValues[1]
            }
            val unique = Regex("unique(\\d+)", RegexOption.IGNORE_CASE).find(prefix)
            if (unique != null) {
                return "Unique " + unique.groupValues[1]
            }
            val substance = Regex("(hydragenum|vermilion|rebis|quebrith|aether|vitriol)(\\d+)", RegexOption.IGNORE_CASE).find(prefix)
            if (substance != null) {
                return capitalize(substance.groupValues[1]) + " " + roman(substance.groupValues[2].toInt())
            }

            return if (prefix.isEmpty()) entryId else capitalize(prefix)
        }

        private fun capitalize(prefix: String): String {
            return prefix.substring(0, 1).uppercase() + prefix.substring(1)
        }

        private fun roman(value: Int): String {
            val numerals = LinkedHashMap<Int, String>()
            numerals[10] = "X"
            numerals[9] = "IX"
            numerals[5] = "V"
            numerals[4] = "IV"
            numerals[1] = "I"
            var remaining = value
            val result = StringBuilder()
            for ((number, numeral) in numerals) {
                while (remaining >= number) {
                    result.append(numeral)
                    remaining -= number
                }
            }
            return result.toString()
        }
    }
}
