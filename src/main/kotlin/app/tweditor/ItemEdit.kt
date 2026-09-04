package app.tweditor

class WeaponAbility(val name: String, val stack: Int) {
    override fun equals(other: Any?): Boolean = other is WeaponAbility && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name
}

class ItemEdit(private val environment: AppEnvironment, private val fields: DBList) {
    val weaponAbilitiesSelf: List<WeaponAbility>
        get() = readAbilities(fields, "WpnAbilitySelf")

    val weaponAbilitiesOpp: List<WeaponAbility>
        get() = readAbilities(fields, "WpnAbilityOpp")

    val modelPart1: Int
        get() = fields.getInteger("ModelPart1")

    val quality: Int
        get() = fields.getInteger("Quality")

    val customCost: Int
        get() = fields.getInteger("CustomCost")

    val weaponType: String
        get() = fields.getString("WeaponType")

    fun setWeaponAbilities(self: List<WeaponAbility>, opp: List<WeaponAbility>) {
        writeAbilities("WpnAbilitySelf", self)
        writeAbilities("WpnAbilityOpp", opp)
    }

    fun copyWeaponPowerFrom(templateList: DBList) {
        setWeaponAbilities(readAbilities(templateList, "WpnAbilitySelf"), readAbilities(templateList, "WpnAbilityOpp"))
    }

    fun setModelPart1(value: Int) {
        fields.setInteger("ModelPart1", value, DBElement.BYTE)
    }

    fun setQuality(value: Int) {
        fields.setInteger("Quality", value, DBElement.BYTE)
    }

    fun setCustomCost(value: Int) {
        fields.setInteger("CustomCost", value, DBElement.DWORD)
    }

    fun setWeaponType(value: String) {
        fields.setString("WeaponType", value)
    }

    private fun writeAbilities(label: String, abilities: List<WeaponAbility>) {
        val element = fields.getElement(label)
        val abilityList: DBList
        if (element != null && element.getType() == DBElement.LIST) {
            abilityList = element.getValue() as DBList
            while (abilityList.getElementCount() > 0) {
                abilityList.removeElement(0)
            }
        } else {
            abilityList = DBList(environment, abilities.size)
            fields.addElement(DBElement(DBElement.LIST, 0, label, abilityList))
        }

        for (ability in abilities) {
            val entryFields = DBList(environment, 2)
            entryFields.addElement(DBElement(DBElement.STRING, 0, "RnAbName", ability.name))
            entryFields.addElement(DBElement(DBElement.BYTE, 0, "RnAbStk", ability.stack))
            abilityList.addElement(DBElement(DBElement.STRUCT, WEAPON_ABILITY_STRUCT_ID, "", entryFields))
        }
    }

    companion object {
        private const val WEAPON_ABILITY_STRUCT_ID = 47806

        fun readAbilities(list: DBList, label: String): List<WeaponAbility> {
            val element = list.getElement(label) ?: return emptyList()
            if (element.getType() != DBElement.LIST) {
                return emptyList()
            }

            val abilities = ArrayList<WeaponAbility>()
            val abilityList = element.getValue() as DBList
            for (entryElement in abilityList) {
                val entryFields = entryElement.getValue() as DBList
                val name = entryFields.getString("RnAbName")
                if (name.isNotEmpty()) {
                    val stackField = entryFields.getElement("RnAbStk")
                    val stack = if (stackField != null) entryFields.getInteger("RnAbStk") else 0
                    abilities.add(WeaponAbility(name, stack))
                }
            }
            return abilities
        }
    }
}
