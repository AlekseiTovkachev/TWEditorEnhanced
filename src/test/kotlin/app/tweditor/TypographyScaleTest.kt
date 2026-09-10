package app.tweditor

import androidx.compose.ui.unit.Density
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypographyScaleTest {
    @Test
    fun readableDensityRaisesOnlyTheFontScale() {
        val result = readableDensity(Density(density = 1.5f, fontScale = 1f))

        assertEquals(1.5f, result.density)
        assertEquals(1.2f, result.fontScale)
    }

    @Test
    fun readableDensityRespectsTheUsersExistingAccessibilityScale() {
        val result = readableDensity(Density(density = 1f, fontScale = 1.25f))

        assertEquals(1.5f, result.fontScale)
    }
}
