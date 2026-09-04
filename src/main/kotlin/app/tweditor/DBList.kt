package app.tweditor

class DBList(val environment: AppEnvironment, capacity: Int) : DBElementValue(), Iterable<DBElement> {
    var elementList: MutableList<DBElement> = ArrayList(capacity)
    var labelMap: MutableMap<String, DBElement> = HashMap(capacity)

    fun addElement(element: DBElement): Boolean {
        val label = element.getLabel()
        if (label.isEmpty()) {
            elementList.add(element)
            return true
        }

        if (labelMap[label] != null) {
            return false
        }
        elementList.add(element)
        labelMap[label] = element
        return true
    }

    fun insertElement(index: Int, element: DBElement): Boolean {
        val label = element.getLabel()
        if (label.isEmpty()) {
            elementList.add(index, element)
            return true
        }

        if (labelMap[label] != null) {
            return false
        }
        elementList.add(index, element)
        labelMap[label] = element
        return true
    }

    fun removeElement(index: Int): DBElement {
        val element = elementList.removeAt(index)
        val label = element.getLabel()
        if (label.isNotEmpty()) {
            labelMap.remove(label)
        }
        return element
    }

    fun removeElement(label: String?): Boolean {
        if (label == null || label.isEmpty()) {
            throw IllegalArgumentException("No database element label supplied")
        }
        val element = labelMap[label] ?: return false
        val removed = elementList.remove(element)
        if (removed) {
            labelMap.remove(label)
        }
        return removed
    }

    fun removeElement(element: DBElement): Boolean {
        val removed = elementList.remove(element)
        if (removed) {
            val label = element.getLabel()
            if (label.isNotEmpty()) {
                labelMap.remove(label)
            }
        }
        return removed
    }

    fun getElement(label: String?): DBElement? {
        if (label == null || label.isEmpty()) {
            throw IllegalArgumentException("No database element label supplied")
        }
        return labelMap[label]
    }

    fun setElement(label: String?, element: DBElement) {
        if (label == null || label.isEmpty()) {
            throw IllegalArgumentException("No database element label supplied")
        }
        val oldElement = labelMap[label]
        if (oldElement != null) {
            elementList[elementList.indexOf(oldElement)] = element
        } else {
            elementList.add(element)
        }

        labelMap[label] = element
    }

    fun setElement(index: Int, element: DBElement) {
        val oldElement = elementList[index]
        val oldLabel = oldElement.getLabel()
        val label = element.getLabel()
        if (label != oldLabel) {
            throw IllegalArgumentException("New label is not the same as old label")
        }
        elementList[index] = element
        labelMap[label] = element
    }

    fun getElement(index: Int): DBElement = elementList[index]

    fun getElementCount(): Int = elementList.size

    override fun iterator(): Iterator<DBElement> = elementList.iterator()

    @Throws(DBException::class)
    fun getString(label: String): String {
        val element = getElement(label)
        val value: String
        if (element != null) {
            val fieldType = element.getType()
            value = when (fieldType) {
                10, 11 -> element.getValue() as String
                12 -> {
                    val string = element.getValue() as LocalizedString
                    if (string.getSubstringCount() > 0) {
                        val substring = string.getSubstring(environment.languageID, 0)
                        if (substring != null) {
                            substring.string
                        } else {
                            string.getSubstring(0).string
                        }
                    } else {
                        val refid = string.stringReference
                        if (refid >= 0) {
                            environment.getString(refid)
                        } else {
                            ""
                        }
                    }
                }
                else -> throw DBException("Field " + label + " is not a string")
            }
        } else {
            value = ""
        }

        return value
    }

    @Throws(DBException::class)
    fun setString(label: String, value: String) {
        val element = getElement(label)
        if (element != null) {
            val fieldType = element.getType()
            when (fieldType) {
                10 -> element.setValue(value)
                11 -> element.setValue(value)
                12 -> {
                    val string = element.getValue() as LocalizedString
                    string.addSubstring(LocalizedSubstring(value, environment.languageID, 0))
                }
                else -> throw DBException("Field " + label + " is not a string")
            }
        } else {
            addElement(DBElement(10, 0, label, value))
        }
    }

    @Throws(DBException::class)
    fun getInteger(label: String): Int {
        val element = getElement(label)
        val value: Int
        if (element != null) {
            val fieldType = element.getType()
            value = when (fieldType) {
                0, 2, 3, 5 -> element.getValue() as Int
                6, 7, 4 -> (element.getValue() as Long).toInt()
                1 -> (element.getValue() as Char).code
                8 -> (element.getValue() as Float).toInt()
                9 -> (element.getValue() as Double).toInt()
                else -> throw DBException("Field " + label + " is not numeric")
            }
        } else {
            value = 0
        }

        return value
    }

    @Throws(DBException::class)
    fun setInteger(label: String, value: Int) {
        setInteger(label, value, 5)
    }

    @Throws(DBException::class)
    fun setInteger(label: String, value: Int, type: Int) {
        val element = getElement(label)
        if (element != null) {
            val fieldType = element.getType()
            when (fieldType) {
                0 -> element.setValue(value and 0xFF)
                2 -> element.setValue(value and 0xFFFF)
                3 -> {
                    var shortValue = value and 0xFFFF
                    if (shortValue > 32767) {
                        shortValue = shortValue or -65536
                    }
                    element.setValue(shortValue)
                }
                5 -> element.setValue(value)
                4 -> element.setValue(value.toLong())
                6, 7 -> element.setValue(value.toLong())
                1 -> element.setValue(value.toChar())
                8 -> element.setValue(value.toFloat())
                9 -> element.setValue(value.toDouble())
                else -> throw DBException("Field " + label + " is not numeric")
            }
        } else {
            addElement(DBElement(type, 0, label, value))
        }
    }

    @Throws(DBException::class)
    fun getFloat(label: String): Float {
        val element = getElement(label)
        val value: Float
        if (element != null) {
            val fieldType = element.getType()
            if (fieldType == 8) {
                value = element.getValue() as Float
            } else {
                throw DBException("Field " + label + " is not floating-point")
            }
        } else {
            value = 0.0f
        }

        return value
    }

    @Throws(DBException::class)
    fun setFloat(label: String, value: Float) {
        val element = getElement(label)
        if (element != null) {
            val fieldType = element.getType()
            if (fieldType == 8) {
                element.setValue(value)
            } else {
                throw DBException("Field " + label + " is not floating-point")
            }
        } else {
            addElement(DBElement(8, 0, label, value))
        }
    }

    public override fun clone(): DBList {
        val clonedList = super.clone() as DBList

        clonedList.elementList = ArrayList(elementList.size)
        clonedList.labelMap = HashMap(elementList.size)
        for (element in elementList) {
            clonedList.addElement(element.clone())
        }
        return clonedList
    }
}
