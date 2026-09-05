package app.tweditor

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JCheckBox

/**
 * A talent/ability checkbox whose game icon takes the check square's slot
 * (Swing replaces the square whenever a checkbox icon is set), so the acquired
 * state is drawn over the icon itself: dimmed while unacquired, full colour
 * with a green underline once acquired. The icon re-queries lazily on paint,
 * because the game textures decode on a background thread.
 */
class AbilityCheckBox(
    name: String,
    private val iconLabel: String,
    private val iconSize: Int,
    private val iconProvider: (String, Int) -> ImageIcon?
) : JCheckBox(name) {
    private var paintedIcon: AcquiredIcon? = null

    override fun paintComponent(graphics: Graphics) {
        if (paintedIcon == null) {
            val base = iconProvider(iconLabel, iconSize)
            if (base != null) {
                paintedIcon = AcquiredIcon(base, this)
                icon = paintedIcon
            }
        }
        super.paintComponent(graphics)
    }

    private class AcquiredIcon(private val base: ImageIcon, private val field: JCheckBox) : Icon {
        override fun getIconWidth(): Int = base.iconWidth
        override fun getIconHeight(): Int = base.iconHeight

        override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                if (!field.isSelected) {
                    g.composite = AlphaComposite.SrcOver.derive(0.35f)
                }
                base.paintIcon(component, g, x, y)
                if (field.isSelected) {
                    g.composite = AlphaComposite.SrcOver
                    g.color = Color(0x60D060)
                    g.fillRect(x, y + iconHeight - 3, iconWidth, 3)
                }
            } finally {
                g.dispose()
            }
        }
    }
}
