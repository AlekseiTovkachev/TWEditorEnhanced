package app.tweditor

import java.io.File
import java.io.FileOutputStream

class Quest @Throws(DBException::class) constructor(
    private val resourceName: String,
    private var questElement: DBElement,
    private val sourceDatabase: Database? = null
) {
    val questName: String
    val questState: Int
    val motherDb: String
    private var questModified = false

    init {
        if (questElement.getType() != 14) {
            throw DBException("Top-level quest element is not a structure")
        }
        var fieldList = questElement.getValue() as DBList

        this.questName = fieldList.getString("QuestLocName").trim()
        this.motherDb = fieldList.getString("MotherDB").lowercase()

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

    internal fun snapshot(): QuestSnapshot = QuestSnapshot(resourceName, questElement.clone(), questModified)

    internal fun restore(snapshot: QuestSnapshot) {
        questElement = snapshot.topLevel.clone()
        sourceDatabase?.setTopLevelStruct(questElement)
        questModified = snapshot.modified
    }

    internal fun saveTo(file: File) {
        val database = requireNotNull(sourceDatabase) { "Quest $resourceName has no loaded database backing it" }
        database.setTopLevelStruct(questElement)
        FileOutputStream(file).use { output -> database.save(output) }
    }

    override fun toString(): String = questName
}

internal data class QuestSnapshot(
    val resourceName: String,
    val topLevel: DBElement,
    val modified: Boolean
)
