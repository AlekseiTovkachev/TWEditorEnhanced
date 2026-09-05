package app.tweditor

/**
 * The player's equipment slots. The row indexes, display names and flags are
 * the game's own weaponslots.2da table: the save stores the row index in each
 * equipped item's WeaponSlot field, and baseitems.2da's EquipableSlots column
 * is a bitmask of the same flags (so "which slots can this item go in" is the
 * mask, and "which slot is it in" is the row index).
 */
object WeaponSlots {
    const val HIDE = 0
    const val BACK_NORMAL = 1
    const val BACK_SILVER = 2
    const val SHORT_1 = 3
    const val SHORT_2 = 4
    const val TROPHY = 5
    const val MEDALION = 6
    const val ELIXIR_1 = 7
    const val ELIXIR_2 = 8
    const val ELIXIR_3 = 9
    const val BIG_WEAPON = 10
    const val FISTS = 11
    const val BELT_CHEST_FRONT = 12
    const val LEG_RIGHT = 13
    const val FOREARM_RIGHT = 14
    const val BELT_CHEST_BACK = 15
    const val FOREARM_LEFT = 16
    const val NECK = 17
    const val BELT = 18
    const val BELT_RIGHT_FRONT = 19
    const val BELT_LEFT_FRONT = 20
    const val BELT_LEFT = 21
    const val BELT_RIGHT = 22
    const val ARM_LEFT = 23
    const val ARM_RIGHT = 24
    const val HEAD = 25
    const val LEFT_HAND = 26
    const val ARMOR = 27

    /** Display name per weaponslots.2da row (index = row). */
    private val names = arrayOf(
        "Hidden", "Steel sword", "Silver sword", "Short weapon 1", "Short weapon 2", "Trophy", "Medallion",
        "Elixir 1", "Elixir 2", "Elixir 3", "Big weapon", "Fists", "Belt, chest front", "Leg, right",
        "Ring, right", "Belt, chest back", "Ring, left", "Neck", "Belt", "Belt, right front",
        "Belt, left front", "Belt, left", "Belt, right", "Arm, left", "Arm, right", "Head", "Left hand", "Armor"
    )

    /** The weaponslots.2da flag for each row (index = row). */
    private val flags = intArrayOf(
        0x0, 0x8000, 0x4000, 0x10000, 0x20000, 0x80000, 0x100000,
        0x200000, 0x400000, 0x800000, 0x40000, 0x1000000, 0x2, 0x4,
        0x8, 0x40, 0x80, 0x200, 0x400, 0x1000,
        0x2000, 0x2000000, 0x4000000, 0x10000000, 0x20000000, 0x1, 0x20, 0x8000000
    )

    fun name(slot: Int): String {
        return if (slot in names.indices) names[slot] else "Slot " + slot
    }

    /** The slots (weaponslots.2da rows) allowed by a baseitems.2da EquipableSlots mask. */
    fun slotsFor(mask: Int): List<Int> {
        val slots = ArrayList<Int>()
        for (row in names.indices) {
            if (row != HIDE && mask and flags[row] != 0) {
                slots.add(row)
            }
        }
        return slots
    }

    /**
     * The EquipableSlots mask per baseitems.2da row, read from the game
     * resource map (a File path or a KeyEntry, as in Main.resourceFilesFrom).
     */
    fun equipableSlots(environment: AppEnvironment): Map<Int, Int> {
        val masks = HashMap<Int, Int>(64)
        val resource = environment.resourceFiles["baseitems.2da"]
        val input = when (resource) {
            is java.io.File -> java.io.FileInputStream(resource)
            is KeyEntry -> resource.getInputStream()
            else -> null
        }
        if (input != null) {
            try {
                val table = TextDatabase(input)
                for (row in 0 until table.getResourceCount()) {
                    val mask = table.getInteger(row, "EquipableSlots")
                    if (mask != 0) {
                        masks[row] = mask
                    }
                }
            } finally {
                input.close()
            }
        }
        return masks
    }
}
