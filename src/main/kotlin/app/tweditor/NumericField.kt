package app.tweditor

import javax.swing.JTextField

class NumericField : JTextField {
    constructor() : this("", 5)

    constructor(string: String) : this(string, string.length.coerceAtLeast(5))

    constructor(columns: Int) : this("", columns)

    constructor(string: String, columns: Int) : super(NumericDocument(), string, columns)

    fun getValue(): Int {
        val text = text
        return if (text.isNotEmpty()) text.toInt() else 0
    }

    fun setValue(value: Int) {
        text = value.toString()
    }
}
