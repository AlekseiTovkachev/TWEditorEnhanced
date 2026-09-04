package app.tweditor

class LocalizedSubstring(var string: String, var language: Int, var gender: Int) : Cloneable {
    public override fun clone(): LocalizedSubstring {
        return try {
            super.clone() as LocalizedSubstring
        } catch (exc: CloneNotSupportedException) {
            throw UnsupportedOperationException("Unable to clone localized substring", exc)
        }
    }
}
