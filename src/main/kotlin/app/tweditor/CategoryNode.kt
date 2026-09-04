package app.tweditor

import javax.swing.tree.DefaultMutableTreeNode

class CategoryNode(category: String) : DefaultMutableTreeNode(category) {
    fun insert(childNode: InventoryNode): Int {
        val count = childCount
        val itemName = (childNode.userObject as ItemTemplate).itemName
        var index = 0
        while (index < count) {
            val node = getChildAt(index) as InventoryNode
            val item = node.userObject as ItemTemplate
            if (itemName < item.itemName) {
                insert(childNode, index)
                break
            }
            index++
        }

        if (index == count) {
            add(childNode)
        }
        return index
    }
}
