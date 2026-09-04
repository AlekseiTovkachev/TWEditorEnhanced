package app.tweditor

class Quest @Throws(DBException::class) constructor(private val resourceName: String, private val questElement: DBElement) {
    val questName: String
    val questState: Int
    private var questModified = false

    init {
        if (questElement.getType() != 14) {
            throw DBException("Top-level quest element is not a structure")
        }
        var fieldList = questElement.getValue() as DBList

        this.questName = fieldList.getString("QuestLocName").trim()

        val mainPhase = fieldList.getElement("MainPhase")
        if (mainPhase == null || mainPhase.getType() != 15) {
            throw DBException("MainPhase not found for quest " + resourceName)
        }
        val questList = mainPhase.getValue() as DBList
        if (questList.getElementCount() == 0) {
            throw DBException("No quest list for quest " + resourceName)
        }
        fieldList = questList.getElement(0).getValue() as DBList
        this.questState = when {
            fieldList.getInteger("QuestBegan") == 0 -> 0
            fieldList.getInteger("Completed") == 1 -> 2
            fieldList.getInteger("Failed") == 1 -> 3
            fieldList.getInteger("NewQuestInfoSent") == 1 -> 1
            else -> 0
        }
    }

    fun getResourceName(): String = resourceName

    fun getQuestElement(): DBElement = questElement

    fun isModified(): Boolean = questModified

    fun setModified(modified: Boolean) {
        this.questModified = modified
    }

    override fun toString(): String = questName
}
