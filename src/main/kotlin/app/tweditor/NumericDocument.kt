package app.tweditor

import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.PlainDocument

class NumericDocument : PlainDocument() {
    @Throws(BadLocationException::class)
    override fun insertString(offset: Int, string: String?, attributes: AttributeSet?) {
        if (string != null) {
            var stringLength = string.length
            val initialChar: Char = if (length > 0) getText(0, 1)[0] else ' '
            if (stringLength == 0) {
                super.insertString(offset, string, attributes)
            } else if (stringLength == 1) {
                val c = string[0]
                if (Character.isDigit(c)) {
                    if (offset != 0 || initialChar != '-') {
                        super.insertString(offset, string, attributes)
                    }
                } else if (c == '-' && offset == 0 && initialChar != '-') {
                    super.insertString(offset, string, attributes)
                }
            } else {
                val buffer = StringBuilder(string)
                var index = 0
                while (index < stringLength) {
                    if (offset == 0 && index == 0) {
                        val c = buffer[0]
                        if (Character.isDigit(c)) {
                            if (initialChar == '-') {
                                buffer.deleteCharAt(index)
                                stringLength--
                            } else {
                                index++
                            }
                        } else if (c == '-') {
                            if (initialChar != '-') {
                                index++
                            } else {
                                buffer.deleteCharAt(index)
                                stringLength--
                            }
                        } else {
                            buffer.deleteCharAt(index)
                            stringLength--
                        }
                    } else if (Character.isDigit(buffer[index])) {
                        index++
                    } else {
                        buffer.deleteCharAt(index)
                        stringLength--
                    }
                }

                super.insertString(offset, buffer.toString(), attributes)
            }
        }
    }
}
