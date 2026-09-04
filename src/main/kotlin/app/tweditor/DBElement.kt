package app.tweditor

class DBElement(type: Int, id: Int, label: String?, value: Any?) : Cloneable {
    companion object {
        const val BYTE = 0
        const val CHAR = 1
        const val WORD = 2
        const val SHORT = 3
        const val DWORD = 4
        const val INT = 5
        const val DWORD64 = 6
        const val INT64 = 7
        const val FLOAT = 8
        const val DOUBLE = 9
        const val STRING = 10
        const val RESOURCE = 11
        const val LSTRING = 12
        const val VOID = 13
        const val STRUCT = 14
        const val LIST = 15
    }

    private var type: Int = type
    private var id: Int = id
    private var label: String? = label ?: ""
    private var value: Any? = value

    fun getType(): Int = type

    fun setType(type: Int) {
        this.type = type
    }

    fun getID(): Int = id

    fun setID(id: Int) {
        this.id = id
    }

    fun getLabel(): String = label ?: ""

    fun setLabel(label: String?) {
        this.label = label ?: ""
    }

    fun getValue(): Any? = value

    fun setValue(value: Any?) {
        if (this.value == null) {
            throw IllegalArgumentException("No value provided")
        }
        this.value = value
    }

    public override fun clone(): DBElement {
        return try {
            val cloned = super.clone() as DBElement
            if (cloned.type == LIST || cloned.type == STRUCT || cloned.type == LSTRING) {
                cloned.value = (cloned.value as DBElementValue).clone()
            }
            cloned
        } catch (exc: CloneNotSupportedException) {
            throw UnsupportedOperationException("Unable to clone database element", exc)
        }
    }
}