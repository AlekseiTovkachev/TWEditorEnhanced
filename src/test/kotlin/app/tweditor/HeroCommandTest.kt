package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class HeroCommandTest {
    @Test
    fun summaryReadsTheCompactHeroFacts(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(environment, SaveSeamSupport.copyFixtureTo(tempDir), tempDir)
        val summary = HeroData.summary(loaded.session)

        assertEquals(loaded.player!!.getInteger("ExpLevel"), summary.level)
        assertEquals(loaded.player!!.getInteger("Experience"), summary.experience)
        assertEquals(loaded.player!!.getInteger("CurrentHitPoints"), summary.vitality)
        assertEquals(loaded.player!!.getInteger("CurrentEndurance"), summary.endurance)
        assertEquals(loaded.player!!.getInteger("CurrentToxicity"), summary.toxicity)
        assertEquals(loaded.player!!.getInteger("Gold"), summary.orens)
        assertEquals(loaded.player!!.getInteger("TalentBronze"), summary.bronzeTalents)
        assertEquals(loaded.player!!.getInteger("TalentSilver"), summary.silverTalents)
        assertEquals(loaded.player!!.getInteger("TalentGold"), summary.goldTalents)
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun heroSummaryEditPersistsThroughCommandSaveAndReload(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
        val original = HeroData.summary(loaded.session)
        val edited = original.copy(
            level = original.level + 1,
            experience = original.experience + 123,
            vitality = original.vitality + 10,
            endurance = original.endurance + 11,
            toxicity = original.toxicity + 12,
            orens = original.orens + 13,
            bronzeTalents = original.bronzeTalents + 2,
            silverTalents = original.silverTalents + 3,
            goldTalents = original.goldTalents + 4
        )

        val result = EditorCommandController(loaded.session).dispatch(SetHeroSummaryCommand(edited))
        assertTrue(result is EditorCommandResult.Applied)
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(edited, HeroData.summary(reloaded.session))
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(reloaded.saveDatabase!!),
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        )
    }

    @Test
    fun abilityTreesPreserveGameRowsWithinFiveProgressionColumns() {
        val strength = HeroAbilityCatalog.tree(AbilityFamily.ATTRIBUTES, "Strength")

        assertEquals((1..5).toList(), strength.map { it.level })
        assertEquals(
            listOf("Strength2", "Strength2 Upgrade1", "Strength2 Upgrade2", "Strength2 Upgrade3"),
            strength[1].abilities.map { it.databaseLabel }
        )
        assertEquals(listOf("Strength1", "Strength1 Upgrade1", "Strength1 Upgrade2"), strength[0].abilities.map { it.databaseLabel })
        assertEquals(listOf("Strength5", "Strength5 Upgrade1", "Strength5 Upgrade2"), strength[4].abilities.map { it.databaseLabel })
    }

    @Test
    fun abilityGroupsUseTheGameFacingOrderAndStaminaName() {
        assertEquals(
            listOf(
                "hero.group.strength", "hero.group.dexterity", "hero.group.stamina", "hero.group.intelligence"
            ),
            HeroAbilityCatalog.groups(AbilityFamily.ATTRIBUTES).map { it.messageKey }
        )
        assertEquals(
            listOf(
                "hero.group.strongSteel", "hero.group.fastSteel", "hero.group.groupSteel",
                "hero.group.strongSilver", "hero.group.fastSilver", "hero.group.groupSilver"
            ),
            HeroAbilityCatalog.groups(AbilityFamily.COMBAT_STYLES).map { it.messageKey }
        )
        val messages = EditorMessages.english()
        assertEquals(
            listOf("Strength", "Dexterity", "Stamina", "Intelligence"),
            HeroAbilityCatalog.groups(AbilityFamily.ATTRIBUTES).map { messages.get(it.messageKey) }
        )
    }

    @Test
    fun inactiveSkillIconsAreDimmedUntilAcquired() {
        assertEquals(0.38f, abilityIconAlpha(acquired = false))
        assertEquals(1f, abilityIconAlpha(acquired = true))
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun representativeHeroAbilityPersistsThroughCommandSaveAndReload(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val controller = EditorCommandController(loaded.session)
        val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
        val talentsBefore = HeroData.summary(loaded.session).let {
            Triple(it.bronzeTalents, it.silverTalents, it.goldTalents)
        }
        val acquired = HeroData.acquiredAbilities(loaded.session)
        val choice = HeroAbilityCatalog.attributes.first { it.databaseLabel.endsWith("1") && it.databaseLabel !in acquired }

        val applied = controller.dispatch(ToggleAbilityCommand(choice.family, choice.databaseLabel, true))
        assertTrue(applied is EditorCommandResult.Applied)
        assertTrue(choice.databaseLabel in HeroData.acquiredAbilities(loaded.session))
        assertTrue(choice.databaseLabel in controller.state().pendingChanges.single().description.args)

        assertTrue(controller.undo().completed)
        assertTrue(choice.databaseLabel !in HeroData.acquiredAbilities(loaded.session))
        controller.dispatch(ToggleAbilityCommand(choice.family, choice.databaseLabel, true))
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertTrue(choice.databaseLabel in HeroData.acquiredAbilities(reloaded.session))
        val talentsAfter = HeroData.summary(reloaded.session).let {
            Triple(it.bronzeTalents, it.silverTalents, it.goldTalents)
        }
        assertEquals(talentsBefore, talentsAfter, "Skill editing must not spend talent points")
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(reloaded.saveDatabase!!),
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        )
    }

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
