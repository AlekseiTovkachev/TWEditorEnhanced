package app.tweditor

class LocalizedString(stringReference: Int) : DBElementValue() {
    var stringReference: Int = stringReference
    private var substringList: MutableList<LocalizedSubstring> = ArrayList(4)

    fun addSubstring(substring: LocalizedSubstring) {
        val existing = substringList.indexOfFirst { candidate ->
            candidate.language == substring.language && candidate.gender == substring.gender
        }
        if (existing >= 0) {
            substringList[existing] = substring
        } else {
            substringList.add(substring)
        }
    }

    fun getSubstringCount(): Int = substringList.size

    fun getSubstring(index: Int): LocalizedSubstring = substringList[index]

    fun getSubstrings(): List<LocalizedSubstring> = substringList

    fun getSubstring(language: Int, gender: Int): LocalizedSubstring? {
        return substringList.find { candidate ->
            candidate.language == language && candidate.gender == gender
        }
    }

    public override fun clone(): LocalizedString {
        val cloned = super.clone() as LocalizedString
        cloned.substringList = ArrayList(substringList.size)
        for (substring in substringList) {
            cloned.substringList.add(substring.clone())
        }
        return cloned
    }
}
