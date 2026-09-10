@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.tweditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage

/**
 * Destinations use the game's own HUD button art (`ui_hud_buttons` atlas,
 * decoded vertically flipped like the game draws it, so cell origins are in
 * flipped space: raw rows Options0/Equipment64/Rest128/Log192/Hero256/Map320/
 * System384/Updated448 become 448/384/320/256/192/128/64/0 from the top).
 * Hero = the character-sheet face button, Journal = the log quill button,
 * Inventory = the equipment ring with the game's satchel emblem overlay.
 */
/**
 * Destinations draw the game's own round tab medallions from the inventory
 * screen: the character face, the journal, and the satchel; the selected
 * documentation keeps the lit coin variant for the active tab. The art always
 * comes from the base archives so user UI mods cannot break the shell.
 */
/**
 * Destinations draw the game's own round tab medallions from the inventory
 * screen: the character face, the journal, and the satchel. The art always
 * comes from the base archives so user UI mods cannot break the shell.
 */
enum class ComposeDestination(val labelKey: String, val fallback: String, val icon: String) {
    HERO("nav.hero", "H", "ui_chr_ti01"),
    JOURNAL("nav.journal", "J", "ui_jrn_ti01"),
    INVENTORY("nav.inventory", "I", "ui_inv_ti01")
}

data class MainWindowDimensions(val width: Int, val height: Int)

fun mainWindowDimensions(saved: String?): MainWindowDimensions {
    val savedWidth = saved?.substringBefore(',')?.toIntOrNull()
    val savedHeight = saved?.substringAfter(',', "")?.toIntOrNull()
    return MainWindowDimensions(
        width = savedWidth?.coerceAtLeast(1440) ?: 1440,
        height = savedHeight?.coerceAtLeast(900) ?: 900
    )
}

fun showComposeMainWindow(environment: AppEnvironment) = application {
    val dimensions = mainWindowDimensions(environment.properties.getProperty("window.main.size"))
    val windowState = rememberWindowState(width = dimensions.width.dp, height = dimensions.height.dp)
    val workflow = remember { ComposeFileWorkflow(environment) }
    var closeRequested by remember { mutableStateOf(false) }

    var languageId by remember { mutableIntStateOf(environment.languageID) }
    val availableLanguages = remember {
        LanguageCatalog.discover(environment).filter { EditorMessages.isAvailable(it.id) }
    }
    fun switchLanguage(language: EditorLanguage) {
        if (language.id == languageId) {
            return
        }
        try {
            environment.setLanguage(language)
            languageId = language.id
        } catch (exc: Throwable) {
            workflow.fail(
                listOf(
                    LocalizedText(
                        "language.switch.failed",
                        exc.message ?: exc.javaClass.simpleName
                    )
                )
            )
        }
    }

    Window(
        onCloseRequest = { closeRequested = true },
        title = EditorMessages.forLanguage(languageId).get("window.title"),
        state = windowState
    ) {
        ComposeEditorApp(
            environment = environment,
            workflow = workflow,
            languageId = languageId,
            availableLanguages = availableLanguages,
            onSelectLanguage = ::switchLanguage,
            closeRequested = closeRequested,
            clearCloseRequest = { closeRequested = false },
            exitApplication = ::exitApplication
        )
    }
}

@Composable
private fun ComposeEditorApp(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    languageId: Int,
    availableLanguages: List<EditorLanguage>,
    onSelectLanguage: (EditorLanguage) -> Unit,
    closeRequested: Boolean,
    clearCloseRequest: () -> Unit,
    exitApplication: () -> Unit
) {
    var destination by remember { mutableStateOf(ComposeDestination.HERO) }
    var fileState by remember { mutableStateOf(workflow.snapshot()) }
    var aboutOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var savePickerOpen by remember { mutableStateOf(false) }

    DisposableEffect(workflow) {
        val subscription = workflow.addListener {
            fileState = workflow.snapshot()
        }
        onDispose { subscription.close() }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { LoadTemplates.loadItemTemplates(environment) }
        }
        workflow.markTemplatesReady()
    }

    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            clearCloseRequest()
            if (fileState.dataModified || fileState.draftDirty) {
                pendingAction = PendingAction.CLOSE
            } else {
                workflow.close()
                exitApplication()
            }
        }
    }

    fun request(action: PendingAction) {
        if (fileState.dataModified || fileState.draftDirty) {
            pendingAction = action
        } else {
            execute(action, { savePickerOpen = true }, workflow, exitApplication)
        }
    }

    CompositionLocalProvider(LocalEditorMessages provides EditorMessages.forLanguage(languageId)) {
    val messages = LocalEditorMessages.current
    ReadableTypography {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            secondary = Verdigris,
            background = Ink,
            surface = Coal,
            surfaceVariant = Iron,
            onPrimary = Ink,
            onBackground = Parchment,
            onSurface = Parchment,
            outline = Line,
            error = Blood
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    ) {
        Column(Modifier.fillMaxSize().background(Ink)) {
            Row(Modifier.fillMaxSize()) {
                ComposeNavigationRail(environment, destination) { destination = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ComposeCommandBar(
                        state = fileState,
                        currentLanguage = LanguageCatalog.currentLanguage(environment, languageId),
                        availableLanguages = availableLanguages,
                        onSelectLanguage = onSelectLanguage,
                        onOpen = { request(PendingAction.OPEN) },
                        onUndo = workflow::undo,
                        onApply = workflow::apply,
                        onRevert = workflow::revert,
                        onSave = { workflow.save() },
                        onSaveAs = { workflow.saveAsInteractive() },
                        onRestore = { request(PendingAction.RESTORE) },
                        onAbout = { aboutOpen = true }
                    )
                    if (fileState.busy) {
                        OperationStrip(fileState)
                    }
                    if (fileState.errorMessage.isNotEmpty()) {
                        ErrorStrip(fileState.errorMessage)
                    }
                    fileState.statusMessage?.let { StatusStrip(it) }
                    if (fileState.pendingChanges.isNotEmpty()) {
                        PendingChangesStrip(fileState.pendingChanges)
                    }
                    when (destination) {
                        ComposeDestination.HERO -> HeroWorkspace(environment, workflow, fileState)
                        ComposeDestination.JOURNAL -> JournalWorkspace(environment, workflow, fileState, languageId)
                        ComposeDestination.INVENTORY -> InventoryWorkspace(environment, workflow, fileState, languageId)
                    }
                }
            }
        }

        // Every dialog renders inside the dark theme; M3 dialogs otherwise
        // fall back to the light scheme and pop up blinding white.
        if (pendingAction != null) {
        val action = pendingAction!!
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    messages.get(
                        if (action == PendingAction.CLOSE) "dialog.close.title" else "dialog.discard.title"
                    )
                )
            },
            text = {
                Text(
                    messages.get(
                        if (action == PendingAction.CLOSE) "dialog.close.text" else "dialog.discard.text"
                    )
                )
            },
            confirmButton = {
                if (action == PendingAction.CLOSE) {
                    Button(onClick = {
                        pendingAction = null
                        workflow.save { success ->
                            if (success) {
                                workflow.close()
                                exitApplication()
                            }
                        }
                    }) { Text(messages.get("dialog.close.saveAndClose")) }
                } else {
                    Button(onClick = {
                        pendingAction = null
                        workflow.close()
                        execute(action, { savePickerOpen = true }, workflow, exitApplication)
                    }) { Text(messages.get("dialog.discard.continue")) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (action == PendingAction.CLOSE) {
                        pendingAction = null
                        workflow.close()
                        exitApplication()
                    } else {
                        pendingAction = null
                        execute(action, { savePickerOpen = true }, workflow, exitApplication)
                    }
                }) {
                    Text(messages.get(if (action == PendingAction.CLOSE) "dialog.close.discard" else "common.cancel"))
                }
            }
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text(messages.get("dialog.about.title")) },
            text = { Text(workflow.aboutLines().joinToString("\n") { messages.render(it) }) },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text(messages.get("common.close")) } }
        )
    }

    if (savePickerOpen) {
        SavePickerDialog(
            environment = environment,
            onDismiss = { savePickerOpen = false },
            onSelected = { file ->
                savePickerOpen = false
                if (!fileState.busy) {
                    environment.properties.setProperty("current.directory", file.parentFile?.absolutePath ?: "")
                    workflow.open(file)
                }
            }
        )
    }

    fileState.overwriteTarget?.let { targetName ->
        AlertDialog(
            onDismissRequest = workflow::cancelOverwrite,
            title = { Text(messages.get("dialog.overwrite.title")) },
            text = { Text(messages.get("dialog.overwrite.text", targetName)) },
            confirmButton = {
                Button(onClick = workflow::confirmOverwrite) { Text(messages.get("dialog.overwrite.confirm")) }
            },
            dismissButton = {
                TextButton(onClick = workflow::cancelOverwrite) { Text(messages.get("common.cancel")) }
            }
        )
    }
    }
    }
}
}

private val LocalReadableTypographyApplied = compositionLocalOf { false }

/**
 * The active shell translation, provided at the window root so a language
 * switch recomposes every visible string. Defaults to the English bundle.
 */
internal val LocalEditorMessages = compositionLocalOf { EditorMessages.english() }

@Composable
private fun ReadableTypography(content: @Composable () -> Unit) {
    val alreadyApplied = LocalReadableTypographyApplied.current
    val base = LocalDensity.current
    val density = if (alreadyApplied) base else readableDensity(base)
    CompositionLocalProvider(
        LocalReadableTypographyApplied provides true,
        LocalDensity provides density,
        content = content
    )
}

private sealed class PendingAction {
    object OPEN : PendingAction()
    object RESTORE : PendingAction()
    object CLOSE : PendingAction()
}

private fun execute(
    action: PendingAction,
    openSavePicker: () -> Unit,
    workflow: ComposeFileWorkflow,
    exitApplication: () -> Unit
) {
    when (action) {
        PendingAction.OPEN -> openSavePicker()
        PendingAction.RESTORE -> workflow.restoreBackup()
        PendingAction.CLOSE -> {
            workflow.close()
            exitApplication()
        }
    }
}

@Composable
internal fun ComposeNavigationRail(
    environment: AppEnvironment,
    active: ComposeDestination,
    onSelect: (ComposeDestination) -> Unit
) {
    val messages = LocalEditorMessages.current
    Column(
        Modifier.width(112.dp).fillMaxHeight().background(Coal).border(0.5.dp, Line),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            Text(messages.get("nav.brand"), color = Amber, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
        ComposeDestination.entries.forEach { entry ->
            val selected = entry == active
            val label = messages.get(entry.labelKey)
            Column(
                Modifier.fillMaxWidth().height(92.dp)
                    .background(if (selected) Iron else Color.Transparent)
                    .semantics { contentDescription = messages.get("nav.destination.desc", label) }
                    .then(Modifier.clickableWithoutRipple { onSelect(entry) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GameAtlasGlyph(
                        environment = environment,
                        resref = entry.icon,
                        fallback = entry.fallback,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(label, color = if (selected) Parchment else Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ComposeCommandBar(
    state: ComposeFileState,
    currentLanguage: EditorLanguage,
    availableLanguages: List<EditorLanguage>,
    onSelectLanguage: (EditorLanguage) -> Unit,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    onApply: () -> Unit,
    onRevert: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onRestore: () -> Unit,
    onAbout: () -> Unit
) {
    var languageMenuOpen by remember { mutableStateOf(false) }
    val messages = LocalEditorMessages.current
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(Coal).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.widthIn(min = 165.dp, max = 320.dp)) {
            Text(messages.get("nav.commandBar.brand"), color = Amber, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(
                state.fileName ?: messages.get("nav.commandBar.noSaveOpen"),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShellTextButton(messages.get("command.open"), onOpen, enabled = !state.busy)
            ShellTextButton(messages.get("command.undo"), onUndo, enabled = state.canUndo)
            ShellTextButton(messages.get("common.apply"), onApply, enabled = state.canApply)
            ShellTextButton(messages.get("command.revert"), onRevert, enabled = state.canRevert)
            ShellTextButton(messages.get("command.saveAs"), onSaveAs, enabled = state.canSaveAs)
            ShellTextButton(messages.get("command.restore"), onRestore, enabled = state.canRestoreBackup)
            Button(
                onClick = onSave,
                enabled = state.fileName != null && !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)
            ) { Text(messages.get("command.save"), fontSize = 12.sp) }
            ShellTextButton(messages.get("command.about"), onAbout, enabled = !state.busy)
            Box {
                ShellTextButton(currentLanguage.displayName, { languageMenuOpen = true }, enabled = !state.busy)
                DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                    availableLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.displayName) },
                            onClick = {
                                languageMenuOpen = false
                                onSelectLanguage(language)
                            },
                            enabled = language.id != currentLanguage.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellTextButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp)) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun OperationStrip(state: ComposeFileState) {
    val messages = LocalEditorMessages.current
    Column(Modifier.fillMaxWidth().background(Iron).padding(horizontal = 20.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(messages.render(state.busyMessage ?: LocalizedText("workflow.busy.fallback")), color = Parchment, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("${state.progress}%", color = Amber, fontSize = 10.sp)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Line)) {
            Box(Modifier.fillMaxWidth(state.progress / 100f).height(3.dp).background(Amber))
        }
    }
}

@Composable
private fun ErrorStrip(messages: List<LocalizedText>) {
    val editorMessages = LocalEditorMessages.current
    Text(
        editorMessages.renderErrorStrip(messages),
        color = Blood,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF261615)).padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun StatusStrip(message: LocalizedText) {
    val editorMessages = LocalEditorMessages.current
    Text(
        editorMessages.render(message),
        color = Verdigris,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF14221E)).padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun PendingChangesStrip(changes: List<PendingChange>) {
    val messages = LocalEditorMessages.current
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF211D14)).padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(messages.get("workflow.pending.label"), color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            changes.joinToString(" · ") { messages.render(it.description) },
            color = Parchment,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HeroWorkspace(environment: AppEnvironment, workflow: ComposeFileWorkflow, state: ComposeFileState) {
    val messages = LocalEditorMessages.current
    var section by remember { mutableStateOf("Summary") }
    val sections = listOf(
        "Summary" to messages.get("hero.section.summary"),
        "Attributes" to messages.get("hero.section.attributes"),
        "Signs" to messages.get("hero.section.signs"),
        "Combat Styles" to messages.get("hero.section.combatStyles")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp)) {
        SectionHeading(messages.get("hero.title"), messages.get("hero.subtitle"))
        Spacer(Modifier.height(16.dp))
        SecondaryTabs(sections, section) { section = it }
        Spacer(Modifier.height(18.dp))
        when (section) {
            "Summary" -> if (state.fileName == null) NoSaveHint() else HeroSummaryWorkspace(workflow, state)
            "Attributes" -> AbilityWorkspace(environment, workflow, state, AbilityFamily.ATTRIBUTES)
            "Signs" -> AbilityWorkspace(environment, workflow, state, AbilityFamily.SIGNS)
            "Combat Styles" -> AbilityWorkspace(environment, workflow, state, AbilityFamily.COMBAT_STYLES)
        }
    }
}

@Composable
private fun NoSaveHint() {
    Text(LocalEditorMessages.current.get("hero.noSave"), color = Muted, fontSize = 13.sp)
}

@Composable
private fun HeroSummaryWorkspace(workflow: ComposeFileWorkflow, state: ComposeFileState) {
    val messages = LocalEditorMessages.current
    val summary = HeroData.summary(workflow.session)
    var level by remember(summary) { mutableStateOf(summary.level.toString()) }
    var experience by remember(summary) { mutableStateOf(summary.experience.toString()) }
    var vitality by remember(summary) { mutableStateOf(summary.vitality.toString()) }
    var endurance by remember(summary) { mutableStateOf(summary.endurance.toString()) }
    var toxicity by remember(summary) { mutableStateOf(summary.toxicity.toString()) }
    var orens by remember(summary) { mutableStateOf(summary.orens.toString()) }
    var bronze by remember(summary) { mutableStateOf(summary.bronzeTalents.toString()) }
    var silver by remember(summary) { mutableStateOf(summary.silverTalents.toString()) }
    var gold by remember(summary) { mutableStateOf(summary.goldTalents.toString()) }
    val parsed = listOf(level, experience, vitality, endurance, toxicity, orens, bronze, silver, gold).map { it.toIntOrNull() }
    val valid = parsed.all { it != null && it >= 0 }
    val changed = valid && parsed != listOf(
        summary.level, summary.experience, summary.vitality, summary.endurance, summary.toxicity,
        summary.orens, summary.bronzeTalents, summary.silverTalents, summary.goldTalents
    )

    ShellPanel(messages.get("hero.summary.panel"), messages.get("hero.summary.subtitle"), Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroValueField(messages.get("hero.field.level"), level, { level = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.vitality"), vitality, { vitality = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.bronzeTalents"), bronze, { bronze = it }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroValueField(messages.get("hero.field.experience"), experience, { experience = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.endurance"), endurance, { endurance = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.silverTalents"), silver, { silver = it }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroValueField(messages.get("hero.field.orens"), orens, { orens = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.toxicity"), toxicity, { toxicity = it }, Modifier.weight(1f))
                HeroValueField(messages.get("hero.field.goldTalents"), gold, { gold = it }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (valid) messages.get("hero.summary.staged") else messages.get("hero.summary.invalid"),
                color = if (valid) Muted else Blood,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    workflow.dispatch(SetHeroSummaryCommand(summary.copy(
                        level = parsed[0]!!, experience = parsed[1]!!,
                        vitality = parsed[2]!!, endurance = parsed[3]!!,
                        toxicity = parsed[4]!!, orens = parsed[5]!!,
                        bronzeTalents = parsed[6]!!, silverTalents = parsed[7]!!,
                        goldTalents = parsed[8]!!
                    )))
                },
                enabled = changed && !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Coal)
            ) { Text(messages.get("hero.summary.apply"), fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(messages.get("hero.summary.difficulty"), color = Parchment, fontSize = 13.sp)
                Text(messages.get("hero.summary.difficultyHint"), color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                summary.difficulty?.let { messages.get(it.messageKey) } ?: messages.get("hero.summary.unknownDifficulty"),
                color = Amber,
                fontSize = 12.sp
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Difficulty.entries.forEach { difficulty ->
                TextButton(
                    onClick = { workflow.dispatch(SetDifficultyCommand(difficulty)) },
                    enabled = state.fileName != null && difficulty != summary.difficulty && !state.busy,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                ) { Text(messages.get(difficulty.messageKey), fontSize = 10.sp) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ShellPanel(
        messages.get("hero.pending.panel"),
        messages.get("hero.pending.count", state.pendingChanges.size),
        Modifier.fillMaxWidth()
    ) {
        if (state.pendingChanges.isEmpty()) {
            Text(messages.get("hero.pending.empty"), color = Muted, fontSize = 12.sp)
        } else {
            state.pendingChanges.forEach { change ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(messages.render(change.description), color = Parchment, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(messages.get(change.evidence.messageKey), color = Verdigris, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroValueField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    val valueDescription = LocalEditorMessages.current.get("hero.value.desc", label)
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Parchment,
            unfocusedTextColor = Parchment,
            focusedLabelColor = Amber,
            unfocusedLabelColor = Muted,
            cursorColor = Amber,
            focusedBorderColor = Amber,
            unfocusedBorderColor = Line
        ),
        modifier = modifier.semantics { contentDescription = valueDescription }
    )
}

@Composable
private fun AbilityWorkspace(environment: AppEnvironment, workflow: ComposeFileWorkflow, state: ComposeFileState, family: AbilityFamily) {
    val messages = LocalEditorMessages.current
    val groups = HeroAbilityCatalog.groups(family)
    var selectedGroupKey by remember(family) { mutableStateOf(groups.firstOrNull()?.key.orEmpty()) }
    val acquired = HeroData.acquiredAbilities(workflow.session)
    val tree = HeroAbilityCatalog.tree(family, selectedGroupKey)

    ShellPanel(messages.get(family.messageKey), messages.get("hero.talent.overline"), Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            groups.forEach { group ->
                TextButton(onClick = { selectedGroupKey = group.key }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(messages.get(group.messageKey), color = if (group.key == selectedGroupKey) Amber else Muted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tree.forEach { progression ->
                val tierColor = when (progression.tier) {
                    TalentTier.BRONZE -> Color(0xFFC58B55)
                    TalentTier.SILVER -> Color(0xFFC5CBD2)
                    TalentTier.GOLD -> Color(0xFFE1B94A)
                }
                Column(Modifier.width(174.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.fillMaxWidth().background(Iron, RoundedCornerShape(5.dp)).padding(8.dp)) {
                        Text(messages.get("hero.level.label", romanLevel(progression.level)), color = tierColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(messages.get("hero.talent.tier", messages.get(progression.tier.messageKey)), color = Muted, fontSize = 9.sp)
                    }
                    progression.abilities.forEach { choice ->
                        val selected = choice.databaseLabel in acquired
                        Row(
                            Modifier.fillMaxWidth().height(82.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) Color(0xFF28352E) else Coal)
                                .border(1.dp, if (selected) tierColor else Line, RoundedCornerShape(3.dp))
                                .clickable(enabled = state.fileName != null && !state.busy) {
                                    workflow.dispatch(ToggleAbilityCommand(family, choice.databaseLabel, !selected))
                                }
                                .semantics { contentDescription = messages.get("hero.ability.toggle.desc", choice.databaseLabel) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(50.dp).background(Iron, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AbilityIcon(
                                    environment,
                                    choice.databaseLabel,
                                    Modifier.size(44.dp).alpha(abilityIconAlpha(selected))
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    AbilityNames.displayName(environment, choice.databaseLabel),
                                    color = if (selected) Parchment else Muted,
                                    fontSize = 10.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selected) Text(messages.get("hero.ability.acquired"), color = tierColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun romanLevel(level: Int): String = listOf("I", "II", "III", "IV", "V").getOrElse(level - 1) { level.toString() }

internal fun abilityIconAlpha(acquired: Boolean): Float = if (acquired) 1f else 0.38f

/** The talent-tree atlas icon for a database ability label. */
@Composable
private fun AbilityIcon(environment: AppEnvironment, databaseLabel: String, modifier: Modifier) {
    val revision = remember { mutableIntStateOf(0) }
    DisposableEffect(environment.icons) {
        val listener = Runnable { revision.intValue++ }
        environment.icons.addLateIconListener(listener)
        onDispose { }
    }
    val resref = AbilityResrefs.resref(databaseLabel)
    val bitmap = remember(resref, revision.intValue) {
        resref?.takeIf { it.isNotEmpty() }?.let {
            environment.icons.imageByResrefBaseArchive(it)?.toComposeImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = databaseLabel, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier.clip(RoundedCornerShape(4.dp)).background(Line))
    }
}

@Composable
internal fun JournalWorkspace(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState,
    languageId: Int = LanguageCatalog.ENGLISH_LANGUAGE_ID
) {
    val messages = LocalEditorMessages.current
    // Same approach as the inventory: only rebuild with the save/edit state.
    // The language id keys the remember so game-content names re-resolve
    // through the newly selected content TLK.
    val journal = remember(state.filePath, state.draftDirty, state.dataModified, state.busy, languageId) {
        JournalViewState.from(workflow.session, environment)
    }
    var section by remember(state.filePath) { mutableStateOf(JournalSection.QUESTS) }
    var warningOpen by remember { mutableStateOf(false) }
    val sections = JournalSection.entries.map { it.messageKey to messages.get(it.messageKey) }
    val activeSection = JournalSection.entries.firstOrNull { it.displayName == section.displayName } ?: JournalSection.QUESTS

    Column(Modifier.fillMaxSize().padding(26.dp)) {
        // The advanced toggle is pinned above the scrolling workspace so it is
        // reachable without scrolling back to the top.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeading(
                messages.get("journal.title"),
                messages.get("journal.subtitle"),
                Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.advancedJournalEditing,
                    onCheckedChange = { enabled ->
                        if (enabled) warningOpen = true else workflow.setAdvancedJournalEditing(false)
                    },
                    enabled = state.fileName != null && !state.busy,
                    modifier = Modifier.semantics { contentDescription = messages.get("journal.advanced.desc") }
                )
                Text(
                    if (state.advancedJournalEditing) messages.get("journal.advanced.on") else messages.get("journal.advanced.off"),
                    color = if (state.advancedJournalEditing) Amber else Muted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SecondaryTabs(sections, activeSection.messageKey) { key ->
                section = JournalSection.entries.first { it.messageKey == key }
            }
            Spacer(Modifier.height(10.dp))
        ShellPanel(
            messages.get(activeSection.messageKey),
            messages.get("journal.section.overline", journal.storyPhase.ifEmpty { messages.get("journal.section.noSaveLoaded") }),
            Modifier.fillMaxWidth()
        ) {
            if (state.fileName == null) {
                Text(messages.get("journal.noSave"), color = Muted, fontSize = 12.sp)
            } else {
                if (journal.storyPhase.isNotEmpty()) {
                    Text(messages.get("journal.storyPhase", journal.storyPhase), color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(7.dp))
                }
                if (activeSection == JournalSection.QUESTS) {
                    JournalQuestList(journal.quests, workflow, state)
                } else if (activeSection == JournalSection.MONSTERS) {
                    MonsterKnowledgeList(journal.monsterTargets, workflow, state)
                } else if (activeSection == JournalSection.FORMULA) {
                    FormulaKnowledgeList(journal.formulaTargets, workflow, state)
                } else {
                    JournalEntryList(activeSection, journal.entries(activeSection), journal.catalog(activeSection), workflow, state)
                }
            }
        }
        if (state.fileName != null && journal.unresolvedEntries.isNotEmpty()) {
            ShellPanel(
                messages.get("journal.unresolved.title"),
                messages.get("journal.unresolved.subtitle"),
                Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 360.dp)
            ) {
                Text(
                    messages.get("journal.unresolved.explainer"),
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(7.dp))
                LazyColumn(Modifier.heightIn(max = 245.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(journal.unresolvedEntries, key = { it.rawReference }) { entry ->
                        JournalEntryRow(entry, advanced = state.advancedJournalEditing, unresolved = true)
                    }
                }
            }
        }
        }
    }

    if (warningOpen) {
        AlertDialog(
            onDismissRequest = { warningOpen = false },
            title = { Text(messages.get("journal.advanced.dialog.title")) },
            text = {
                Text(messages.get("journal.advanced.dialog.text"))
            },
            confirmButton = {
                Button(onClick = {
                    warningOpen = false
                    workflow.setAdvancedJournalEditing(true)
                }) { Text(messages.get("journal.advanced.dialog.enable")) }
            },
            dismissButton = { TextButton(onClick = { warningOpen = false }) { Text(messages.get("common.cancel")) } }
        )
    }
}

@Composable
private fun JournalQuestList(
    quests: List<JournalQuestView>,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState
) {
    val messages = LocalEditorMessages.current
    var phasePicker by remember(state.filePath) { mutableStateOf<JournalQuestView?>(null) }
    val advanced = state.advancedJournalEditing
    if (quests.isEmpty()) {
        Text(messages.get("journal.quest.empty"), color = Muted, fontSize = 12.sp)
    } else {
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(quests, key = { it.resourceName }) { quest ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Raised).padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(quest.name, color = Parchment, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (advanced) {
                            Text(quest.resourceName, color = Muted, fontSize = 9.sp)
                            Text(
                                messages.get("journal.quest.rawPhase", quest.currentPhase, quest.rootPhases.size),
                                color = Muted,
                                fontSize = 9.sp
                            )
                        } else {
                            Text(quest.resourceName, color = Muted, fontSize = 9.sp)
                        }
                    }
                    Text(
                        messages.get(quest.stateKey),
                        color = if (quest.stateKey == "journal.quest.state.failed") Blood else Amber,
                        fontSize = 10.sp
                    )
                    if (quest.tracked) {
                        Spacer(Modifier.width(8.dp))
                        Text(messages.get("journal.quest.tracked"), color = Verdigris, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { phasePicker = quest },
                        enabled = advanced && state.fileName != null && !state.busy && quest.rootPhases.isNotEmpty()
                    ) {
                        Text(
                            if (advanced) messages.get("journal.quest.override") else messages.get("journal.quest.overrideAdvanced"),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
    Text(
        if (advanced) {
            messages.get("journal.quest.dangerous")
        } else {
            messages.get("journal.quest.enableAdvancedHint")
        },
        color = Muted,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 7.dp)
    )

    phasePicker?.let { quest ->
        AlertDialog(
            onDismissRequest = { phasePicker = null },
            title = { Text(messages.get("journal.quest.overrideTitle", quest.name)) },
            text = {
                Column(Modifier.widthIn(min = 480.dp)) {
                    Text(
                        messages.get("journal.quest.overrideText", quest.resourceName),
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(quest.rootPhases, key = { it.phaseId }) { phase ->
                            TextButton(
                                onClick = {
                                    val result = workflow.dispatch(
                                        SetQuestPhaseCommand(
                                            questResourceName = quest.resourceName,
                                            phaseId = phase.phaseId,
                                            advancedJournalEditing = true
                                        )
                                    )
                                    if (result is EditorCommandResult.Applied) phasePicker = null
                                },
                                enabled = !phase.current && advanced && !state.busy
                            ) {
                                Text(
                                    if (phase.current) {
                                        messages.get("journal.quest.phaseCurrent", phase.phaseId, phase.phaseName)
                                    } else {
                                        messages.get("journal.quest.phase", phase.phaseId, phase.phaseName)
                                    },
                                    color = if (phase.current) Amber else Parchment,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { phasePicker = null }) { Text(messages.get("common.cancel")) } }
        )
    }
}

@Composable
private fun MonsterKnowledgeList(
    targets: List<MonsterKnowledgeTarget>,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState
) {
    val messages = LocalEditorMessages.current
    if (targets.isEmpty()) {
        Text(
            messages.get("journal.monster.empty"),
            color = Muted,
            fontSize = 12.sp
        )
        Text(
            messages.get("journal.monster.grantHint"),
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    } else {
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(targets, key = { it.monsterId }) { target ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Raised).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(target.name, color = Parchment, fontSize = 12.sp)
                        Text(
                            if (target.known) {
                                messages.get("journal.monster.variants", target.variants.size, target.variants.joinToString())
                            } else {
                                messages.get("journal.monster.notPresent")
                            },
                            color = if (target.known) Verdigris else Muted,
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val command = if (target.known) {
                                RemoveMonsterKnowledgeCommand(target.monsterId)
                            } else {
                                GrantMonsterKnowledgeCommand(target.monsterId)
                            }
                            workflow.dispatch(command)
                        },
                        enabled = state.fileName != null && !state.busy
                    ) {
                        Text(if (target.known) messages.get("common.remove") else messages.get("journal.action.grant"), fontSize = 10.sp)
                    }
                }
            }
        }
    }
    Text(
        messages.get("journal.monster.unverifiedHint"),
        color = Muted,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 7.dp)
    )
}

@Composable
private fun FormulaKnowledgeList(
    targets: List<FormulaKnowledgeTarget>,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState
) {
    val messages = LocalEditorMessages.current
    if (targets.isEmpty()) {
        Text(
            messages.get("journal.formula.empty"),
            color = Muted,
            fontSize = 12.sp
        )
        Text(
            messages.get("journal.formula.grantHint"),
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    } else {
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(targets, key = { it.category + ":" + it.formulaId }) { target ->
                val canGrant = !target.present && state.fileName != null && !state.busy
                val canRemove = target.complete && state.advancedJournalEditing && state.fileName != null && !state.busy
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Raised).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(target.name, color = Parchment, fontSize = 12.sp)
                        Text(
                            "${messages.get(target.kind.messageKey)} · ${target.formulaId}",
                            color = Muted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                target.complete -> messages.get("journal.formula.complete")
                                target.present -> messages.get("journal.formula.incomplete")
                                else -> messages.get("journal.monster.notPresent")
                            },
                            color = when {
                                target.complete -> Verdigris
                                target.present -> Amber
                                else -> Muted
                            },
                            fontSize = 9.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (!target.present) {
                                workflow.dispatch(GrantFormulaCommand(target.category, target.formulaId))
                            } else if (target.complete && state.advancedJournalEditing) {
                                workflow.dispatch(
                                    RemoveFormulaCommand(
                                        target.category,
                                        target.formulaId,
                                        advancedJournalEditing = true
                                    )
                                )
                            }
                        },
                        enabled = canGrant || canRemove
                    ) {
                        Text(
                            when {
                                canGrant -> messages.get("journal.action.grant")
                                target.complete && state.advancedJournalEditing -> messages.get("common.remove")
                                target.complete -> messages.get("journal.formula.removeAdvanced")
                                target.present -> messages.get("journal.formula.incompleteLabel")
                                else -> messages.get("journal.action.grant")
                            },
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
    Text(
        messages.get("journal.formula.syncHint"),
        color = Muted,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 7.dp)
    )
}

private data class AdvancedJournalDialogState(
    val category: String,
    val existing: JournalEntryView?
)

@Composable
private fun JournalEntryList(
    section: JournalSection,
    entries: List<JournalEntryView>,
    catalog: List<JournalEntryView>,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState
) {
    val messages = LocalEditorMessages.current
    var dialog by remember(section, state.filePath) { mutableStateOf<AdvancedJournalDialogState?>(null) }
    val advanced = state.advancedJournalEditing
    val canMutate = state.fileName != null && !state.busy && advanced
    val addCategory = entries.firstOrNull { it.advancedEditable }?.category
        ?: catalog.firstOrNull { it.advancedEditable }?.category

    // Present entries first, then the installed game's catalog entries this
    // Save does not have yet, each offered for addition.
    val knownKeys = entries.map { it.rawReference.lowercase() }.toHashSet()
    val locked = catalog.filter { !knownKeys.contains(it.rawReference.lowercase()) }

    if (entries.isEmpty()) {
        Text(messages.get("journal.entry.empty", messages.get(section.messageKey).lowercase()), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
    if (locked.isEmpty() && entries.isEmpty()) {
        if (catalog.isEmpty()) {
            Text(
                messages.get("journal.entry.catalogUnavailable", messages.get(section.messageKey).lowercase()),
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            JournalEntryRow(
                entry = entry,
                advanced = advanced,
                unresolved = false,
                onEdit = { dialog = AdvancedJournalDialogState(entry.category, entry) },
                onRemove = {
                    workflow.dispatch(
                        RemoveAdvancedJournalEntryCommand(
                            entry.category,
                            entry.entryId,
                            advancedJournalEditing = true
                        )
                    )
                }
            )
        }
        locked.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Iron).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.rawReference, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(messages.get("journal.entry.notInSave"), color = Amber, fontSize = 10.sp)
                Spacer(Modifier.width(7.dp))
                TextButton(
                    onClick = {
                        workflow.dispatch(
                            AddAdvancedJournalEntryCommand(
                                category = entry.category,
                                entryId = entry.entryId,
                                read = false,
                                timeOfDay = AdvancedJournalAccess.currentGameTime(workflow.session),
                                advancedJournalEditing = true
                            )
                        )
                    },
                    enabled = canMutate && entry.advancedEditable,
                    modifier = Modifier.semantics { contentDescription = messages.get("journal.entry.add.desc", entry.rawReference) }
                ) { Text(messages.get("common.add"), fontSize = 10.sp) }
            }
        }
    }
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = { dialog = AdvancedJournalDialogState(addCategory!!, null) },
        enabled = canMutate && addCategory != null && AdvancedJournalAccess.canAdd(workflow.session, addCategory),
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
    ) {
        Text(if (advanced) messages.get("journal.entry.add") else messages.get("journal.entry.advancedEdit"), fontSize = 10.sp)
    }

    dialog?.let { current ->
        AdvancedJournalEntryDialog(
            dialog = current,
            workflow = workflow,
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun JournalEntryRow(
    entry: JournalEntryView,
    advanced: Boolean,
    unresolved: Boolean,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val messages = LocalEditorMessages.current
    val actionLabel = when {
        unresolved -> messages.get("journal.entry.unresolved")
        !advanced -> messages.get("journal.entry.advancedEdit")
        !entry.advancedEditable -> messages.get("journal.entry.preserved")
        else -> messages.get("journal.entry.edit")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Raised).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.displayName, color = Parchment, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.rawReference, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (entry.read) messages.get("journal.entry.read") else messages.get("journal.entry.unread"),
            color = if (entry.read) Muted else Amber,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(7.dp))
        if (entry.advancedEditable && advanced && !unresolved) {
            TextButton(onClick = { onEdit?.invoke() }) { Text(actionLabel, fontSize = 10.sp) }
            TextButton(onClick = { onRemove?.invoke() }) { Text(messages.get("common.remove"), fontSize = 10.sp) }
        } else {
            TextButton(onClick = {}, enabled = false) { Text(actionLabel, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun AdvancedJournalEntryDialog(
    dialog: AdvancedJournalDialogState,
    workflow: ComposeFileWorkflow,
    onDismiss: () -> Unit
) {
    val messages = LocalEditorMessages.current
    val existing = dialog.existing
    var entryId by remember(dialog.category, existing?.rawReference) { mutableStateOf(existing?.entryId.orEmpty()) }
    var read by remember(dialog.category, existing?.rawReference) { mutableStateOf(existing?.read ?: false) }
    var timeOfDay by remember(dialog.category, existing?.rawReference) {
        mutableStateOf((existing?.timeOfDay ?: AdvancedJournalAccess.currentGameTime(workflow.session).toLong()).toString())
    }
    var error by remember(dialog.category, existing?.rawReference) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) messages.get("journal.entry.dialog.add") else messages.get("journal.entry.dialog.edit"))
        },
        text = {
            Column(
                Modifier.widthIn(min = 430.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    messages.get(
                        "journal.entry.dialog.header",
                        AdvancedJournalCategories.messageKey(dialog.category)?.let { messages.get(it) }
                            ?: (AdvancedJournalCategories.displayName(dialog.category) ?: dialog.category),
                        dialog.category
                    ),
                    color = Muted,
                    fontSize = 10.sp
                )
                OutlinedTextField(
                    value = entryId,
                    onValueChange = { entryId = it },
                    label = { Text(messages.get("journal.entry.dialog.entryId")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeOfDay,
                    onValueChange = { timeOfDay = it },
                    label = { Text(messages.get("journal.entry.dialog.entryTOD")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = read, onCheckedChange = { read = it })
                    Text(messages.get("journal.entry.dialog.markRead"), color = Parchment, fontSize = 12.sp)
                }
                Text(
                    messages.get("journal.entry.dialog.preserveHint"),
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                error?.let { Text(it, color = Blood, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedTime = timeOfDay.trim().toIntOrNull()
                if (parsedTime == null || parsedTime < 0) {
                    error = messages.get("journal.entry.dialog.invalidTOD")
                } else {
                    val result = if (existing == null) {
                        workflow.dispatch(
                            AddAdvancedJournalEntryCommand(
                                category = dialog.category,
                                entryId = entryId,
                                read = read,
                                timeOfDay = parsedTime,
                                advancedJournalEditing = true
                            )
                        )
                    } else {
                        workflow.dispatch(
                            EditAdvancedJournalEntryCommand(
                                category = dialog.category,
                                currentEntryId = existing.entryId,
                                values = AdvancedJournalEntryValues(entryId, read, parsedTime),
                                advancedJournalEditing = true
                            )
                        )
                    }
                    if (result is EditorCommandResult.Applied) onDismiss()
                    else if (result is EditorCommandResult.Rejected) error = result.problems.joinToString(" ") { messages.render(it) }
                }
            }) { Text(if (existing == null) messages.get("common.add") else messages.get("common.apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(messages.get("common.cancel")) } }
    )
}

@Composable
internal fun InventoryWorkspace(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    state: ComposeFileState,
    languageId: Int = LanguageCatalog.ENGLISH_LANGUAGE_ID
) {
    val messages = LocalEditorMessages.current
    // Reading the whole inventory out of the session is expensive; rebuild it
    // only when the open save, its edit state, or the display language changes,
    // not on tab switches. The language id keys the remember so item names
    // re-resolve through the newly selected content TLK.
    val view = remember(state.filePath, state.draftDirty, state.dataModified, state.pendingChanges, state.busy, languageId) {
        InventoryViewState.from(workflow.session, messages)
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var narrowPage by remember { mutableStateOf("storage") }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var pickerDestination by remember { mutableStateOf<InventoryDestination?>(null) }
    var itemWindowId by remember { mutableStateOf<String?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoveredSlot by remember { mutableStateOf<Int?>(null) }
    var removedEquipmentIds by remember(state.filePath) { mutableStateOf(emptySet<String>()) }
    val draggingItem = view.allItems.firstOrNull { it.id == draggingId }
    val storageReady = EquipmentAccess.storageList(workflow.session) != null
    val saveOpen = workflow.session.saveDatabase != null
    fun selectEquipment(destination: EquipmentDestinationView) {
        val item = destination.item
        if (item == null || (selectedSlot == destination.slot && selectedId == item.id)) {
            pickerSlot = destination.slot
        } else {
            selectedSlot = destination.slot
            selectedId = item.id
        }
    }

    fun removeEquipment(destination: EquipmentDestinationView) {
        val item = destination.item ?: return
        if (item.id in removedEquipmentIds) return
        removedEquipmentIds = removedEquipmentIds + item.id
        val result = workflow.dispatch(RemoveEquipmentCommand(item.id))
        if (result is EditorCommandResult.Rejected) {
            removedEquipmentIds = removedEquipmentIds - item.id
            return
        }
        if (selectedId == item.id) {
            selectedId = null
            selectedSlot = null
        }
    }

    fun selectInventory(item: InventoryItemView) {
        selectedSlot = null
        selectedId = item.id
    }

    fun dropOnEquipment(sourceId: String, targetSlot: Int): Boolean {
        val item = view.allItems.firstOrNull { it.id == sourceId } ?: return false
        val compatibility = EquipmentCatalog.compatibility(environment, item, targetSlot)
        if (compatibility.status == EquipmentCompatibilityStatus.INCOMPATIBLE) {
            workflow.dispatch(EquipItemCommand(environment, sourceId, targetSlot))
            return false
        }
        val result = workflow.dispatch(
            EquipItemCommand(
                environment = environment,
                sourceId = sourceId,
                targetSlot = targetSlot,
                confirmUncertain = compatibility.status == EquipmentCompatibilityStatus.UNCERTAIN
            )
        )
        return if (result is EditorCommandResult.Applied) {
            selectedId = null
            selectedSlot = targetSlot
            true
        } else {
            false
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        SectionHeading(messages.get("inventory.title"), messages.get("inventory.subtitle"))
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val narrow = inventoryLayoutFor(maxWidth.value.toInt()) == InventoryLayout.NARROW
            if (narrow) {
                Column(Modifier.fillMaxSize()) {
                    SecondaryTabs(
                        listOf(
                            "storage" to messages.get("inventory.tab.storage"),
                            "equipment" to messages.get("inventory.tab.equipment"),
                            "carried" to messages.get("inventory.tab.carried")
                        ),
                        narrowPage
                    ) { narrowPage = it }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .semantics { contentDescription = messages.get("inventory.narrow.page.desc") },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (narrowPage) {
                            "storage" -> StorageCard(
                            environment,
                            view.storage,
                                ::selectInventory,
                                onEdit = { itemWindowId = it.id },
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                onAdd = { pickerDestination = InventoryDestination.STORAGE },
                                canAdd = saveOpen && storageReady,
                                onSort = { workflow.dispatch(SortStorageCommand()) },
                                onDragState = { id -> draggingId = id }
                            )
                            "equipment" -> EquipmentCard(
                                items = view.equipment,
                                onSelect = ::selectEquipment,
                                selectedId = selectedId,
                                onRemove = ::removeEquipment,
                                onDrop = ::dropOnEquipment,
                                draggingId = draggingId,
                                draggingItem = draggingItem,
                                hoveredSlot = hoveredSlot,
                                environment = environment,
                                onOpen = { itemWindowId = it.id },
                                modifier = Modifier.fillMaxWidth(),
                                scrollable = false,
                                onDragState = { id, slot -> draggingId = id; hoveredSlot = slot }
                            )
                            else -> CarriedItemsCard(
                                view,
                                ::selectInventory,
                                environment,
                                Modifier.fillMaxWidth(),
                                scrollable = false,
                                canAdd = saveOpen,
                                onAdd = { destination -> pickerDestination = destination },
                                onEdit = { itemWindowId = it.id },
                                onDragState = { id -> draggingId = id }
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StorageCard(
                            environment,
                            view.storage,
                            ::selectInventory,
                            onEdit = { itemWindowId = it.id },
                            modifier = Modifier.weight(1.05f).fillMaxHeight(),
                            onAdd = { pickerDestination = InventoryDestination.STORAGE },
                            canAdd = saveOpen && storageReady,
                            onSort = { workflow.dispatch(SortStorageCommand()) },
                            onDragState = { id -> draggingId = id }
                        )
                        EquipmentCard(
                            items = view.equipment,
                            onSelect = ::selectEquipment,
                            selectedId = selectedId,
                            onRemove = ::removeEquipment,
                            onDrop = ::dropOnEquipment,
                            draggingId = draggingId,
                            draggingItem = draggingItem,
                            hoveredSlot = hoveredSlot,
                            environment = environment,
                            onOpen = { itemWindowId = it.id },
                            modifier = Modifier.weight(1.05f).fillMaxHeight(),
                            onDragState = { id, slot -> draggingId = id; hoveredSlot = slot }
                        )
                        CarriedItemsCard(
                            view,
                            ::selectInventory,
                            environment,
                            Modifier.fillMaxHeight().width(CarriedPanelWidth),
                            canAdd = saveOpen,
                            onAdd = { destination -> pickerDestination = destination },
                            onEdit = { itemWindowId = it.id },
                            onDragState = { id -> draggingId = id }
                        )
                    }
                }
            }
        }
    }

    if (pickerSlot != null) {
        val targetSlot = pickerSlot!!
        val destinationOccupied = view.equipment.firstOrNull { it.slot == targetSlot }?.item != null
        EquipmentPickerDialog(
            environment = environment,
            workflow = workflow,
            targetSlot = targetSlot,
            destinationOccupied = destinationOccupied,
            onDismiss = {
                pickerSlot = null
                selectedId = null
                selectedSlot = null
            }
        )
    }
    if (pickerDestination != null) {
        InventoryPickerDialog(
            environment = environment,
            workflow = workflow,
            destination = pickerDestination!!,
            onDismiss = { pickerDestination = null }
        )
    }
    if (itemWindowId != null) {
        val windowItem = view.allItems.firstOrNull { it.id == itemWindowId }
        ItemWindow(
            environment = environment,
            workflow = workflow,
            sourceId = itemWindowId!!,
            item = windowItem,
            equipment = itemWindowId!!.startsWith("equipment-"),
            languageId = languageId,
            onDismiss = { itemWindowId = null }
        )
    }
}

/*
 * The old read-only call sites are intentionally kept below this point as
 * small, shared surfaces. The mutating callbacks above are the only route by
 * which the Compose workspace changes inventory state.
 */

@Composable
private fun StorageCard(
    environment: AppEnvironment,
    items: List<InventoryItemView>,
    onSelect: (InventoryItemView) -> Unit,
    modifier: Modifier,
    onEdit: (InventoryItemView) -> Unit = {},
    onAdd: () -> Unit = {},
    canAdd: Boolean = false,
    onSort: () -> Unit = {},
    onDragState: (String?) -> Unit = {}
) {
    val messages = LocalEditorMessages.current
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val visibleItems = items.filter { item ->
        normalizedQuery.isEmpty() ||
            item.name.lowercase().contains(normalizedQuery) ||
            item.templateResRef.lowercase().contains(normalizedQuery)
    }

    ShellPanel(messages.get("storage.title"), messages.get("storage.subtitle"), modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (items.isEmpty()) messages.get("storage.empty") else messages.get("storage.count", items.size),
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { searchOpen = true },
                enabled = items.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = messages.get("storage.search.desc") }
            ) { Text(messages.get("storage.search"), fontSize = 10.sp) }
        }
        if (items.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(visibleItems, key = { it.id }) { item -> InventoryItemRow(environment, item, onSelect, onDragState, onEdit) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSort, enabled = items.size > 1, modifier = Modifier.weight(1f)) { Text(messages.get("storage.sort")) }
            OutlinedButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.weight(1f)) { Text(messages.get("storage.add")) }
        }
        if (!canAdd) {
            Text(
                messages.get("storage.readonly.hint"),
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }

    if (searchOpen) {
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text(messages.get("storage.search.title")) },
            text = {
                Column(Modifier.widthIn(min = 420.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(messages.get("storage.search.field")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (query.isBlank()) messages.get("storage.search.all", items.size) else messages.get("storage.search.matches", visibleItems.size),
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { searchOpen = false }) { Text(messages.get("common.close")) } }
        )
    }
}

private data class EquipmentSlotLayout(
    val slot: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * A compact version of the game's equipment paperdoll. The asymmetric sword,
 * armor, quick-item, ring, trophy and heavy-weapon footprints intentionally
 * follow the proportions of the in-game inventory instead of a uniform grid.
 */
private val equipmentLayout = listOf(
    EquipmentSlotLayout(WeaponSlots.ELIXIR_1, 172, 16, 48, 56),
    EquipmentSlotLayout(WeaponSlots.ELIXIR_2, 226, 16, 48, 56),
    EquipmentSlotLayout(WeaponSlots.ELIXIR_3, 280, 16, 48, 56),
    EquipmentSlotLayout(WeaponSlots.BACK_SILVER, 8, 96, 68, 184),
    EquipmentSlotLayout(WeaponSlots.ARMOR, 113, 88, 114, 132),
    EquipmentSlotLayout(WeaponSlots.BACK_NORMAL, 264, 80, 68, 200),
    EquipmentSlotLayout(WeaponSlots.SHORT_1, 82, 228, 52, 58),
    EquipmentSlotLayout(WeaponSlots.SHORT_2, 206, 228, 52, 58),
    EquipmentSlotLayout(WeaponSlots.FOREARM_RIGHT, 24, 292, 52, 58),
    EquipmentSlotLayout(WeaponSlots.FOREARM_LEFT, 264, 292, 52, 58),
    EquipmentSlotLayout(WeaponSlots.TROPHY, 42, 348, 76, 108),
    EquipmentSlotLayout(WeaponSlots.BIG_WEAPON, 129, 314, 82, 142)
)

private val EquipmentPaperdollWidth = 340.dp
private val EquipmentPaperdollHeight = 466.dp

@Composable
private fun EquipmentCard(
    items: List<EquipmentDestinationView>,
    onSelect: (EquipmentDestinationView) -> Unit,
    selectedId: String?,
    onRemove: (EquipmentDestinationView) -> Unit,
    onDrop: (String, Int) -> Boolean,
    draggingId: String?,
    draggingItem: InventoryItemView?,
    hoveredSlot: Int?,
    environment: AppEnvironment,
    onOpen: (InventoryItemView) -> Unit,
    modifier: Modifier,
    scrollable: Boolean = true,
    onDragState: (String?, Int?) -> Unit = { _, _ -> }
) {
    val bySlot = items.associateBy { it.slot }
    val messages = LocalEditorMessages.current
    ShellPanel(messages.get("equipment.title"), messages.get("equipment.subtitle"), modifier) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            contentAlignment = Alignment.TopCenter
        ) {
            val scale = (maxWidth.value / EquipmentPaperdollWidth.value).coerceAtMost(1f)
            Box(
                Modifier.width(EquipmentPaperdollWidth * scale).height(EquipmentPaperdollHeight * scale)
                    .background(Color(0xFF111713), RoundedCornerShape(8.dp))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = messages.get("equipment.paperdoll.desc") }
            ) {
                // A quiet body silhouette keeps the same visual reading as the
                // game screen while letting actual item icons remain dominant.
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = 76.dp * scale)
                        .width(90.dp * scale).height(294.dp * scale)
                        .background(Iron.copy(alpha = 0.42f), RoundedCornerShape(46.dp))
                )
                equipmentLayout.forEach { layout ->
                    EquipmentDestinationCard(
                        destination = bySlot.getValue(layout.slot),
                        selected = bySlot.getValue(layout.slot).item?.id == selectedId,
                        onSelect = onSelect,
                        onRemove = onRemove,
                        onDrop = onDrop,
                        draggingId = draggingId,
                        draggingItem = draggingItem,
                        hoveredSlot = hoveredSlot,
                        environment = environment,
                        onOpen = onOpen,
                        compact = layout.width <= 52,
                        modifier = Modifier.offset(layout.x.dp * scale, layout.y.dp * scale)
                            .size(layout.width.dp * scale, layout.height.dp * scale),
                        onDragState = onDragState
                    )
                }
            }
        }
    }
}

@Composable
internal fun EquipmentDestinationCard(
    destination: EquipmentDestinationView,
    selected: Boolean,
    onSelect: (EquipmentDestinationView) -> Unit,
    onRemove: (EquipmentDestinationView) -> Unit,
    onDrop: (String, Int) -> Boolean,
    draggingId: String?,
    draggingItem: InventoryItemView?,
    hoveredSlot: Int?,
    environment: AppEnvironment,
    onOpen: (InventoryItemView) -> Unit,
    compact: Boolean,
    modifier: Modifier,
    onDragState: (String?, Int?) -> Unit
) {
    val item = destination.item
    val messages = LocalEditorMessages.current
    val compatibility = draggingItem?.let { EquipmentCatalog.compatibility(environment, it, destination.slot) }
    val targetColor = if (hoveredSlot == destination.slot && compatibility != null) {
        when (compatibility.status) {
            EquipmentCompatibilityStatus.COMPATIBLE -> Verdigris
            EquipmentCompatibilityStatus.UNCERTAIN -> Amber
            EquipmentCompatibilityStatus.INCOMPATIBLE -> Blood
        }
    } else Line
    val currentDraggingId = rememberUpdatedState(draggingId)
    val currentDrop = rememberUpdatedState(onDrop)
    val currentDragState = rememberUpdatedState(onDragState)
    val target = remember(destination.slot) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                currentDraggingId.value?.let { currentDragState.value(it, destination.slot) }
            }

            override fun onEntered(event: DragAndDropEvent) {
                currentDraggingId.value?.let { currentDragState.value(it, destination.slot) }
            }

            override fun onExited(event: DragAndDropEvent) {
                currentDragState.value(null, null)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentDragState.value(null, null)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val sourceId = currentDraggingId.value ?: return false
                val accepted = currentDrop.value(sourceId, destination.slot)
                currentDragState.value(null, null)
                return accepted
            }
        }
    }
    val currentTargetState = Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = target
    )
    Box(
        modifier.then(currentTargetState).clip(RoundedCornerShape(5.dp))
            .background(if (item != null) Color(0xFF26352E) else Raised)
            .border(
                if (destination.warning != null || selected) 1.5.dp else 1.dp,
                when {
                    destination.warning != null -> Blood
                    hoveredSlot == destination.slot -> targetColor
                    selected -> Amber
                    else -> Line
                },
                RoundedCornerShape(5.dp)
            )
            .semantics {
                contentDescription = buildString {
                    append(
                        messages.get(
                            "equipment.slot.desc",
                            destination.name,
                            item?.name ?: messages.get("common.empty")
                        )
                    )
                    if (destination.items.size > 1) append(messages.get("equipment.slot.count", destination.items.size))
                }
            }
            .combinedClickable(
                onClick = { onSelect(destination) },
                onDoubleClick = { item?.let(onOpen) }
            )
    ) {
        if (item != null) {
            ItemIcon(
                environment,
                item,
                Modifier.fillMaxSize().padding(
                    start = if (compact) 3.dp else 7.dp,
                    top = if (compact) 3.dp else 7.dp,
                    end = if (compact) 3.dp else 7.dp,
                    bottom = if (compact) 3.dp else 25.dp
                ),
                highResolution = true
            )
        } else {
            Text(
                compactSlotLabel(destination),
                color = Muted,
                fontSize = if (compact) 10.sp else 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (!compact) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Coal.copy(alpha = 0.88f)).padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item?.let {
                    Text(it.name, color = Parchment, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(destination.name, color = Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (hoveredSlot == destination.slot && draggingId != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Coal.copy(alpha = 0.92f)).padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (compatibility?.status) {
                        EquipmentCompatibilityStatus.COMPATIBLE -> messages.get("equipment.drop")
                        EquipmentCompatibilityStatus.UNCERTAIN -> messages.get("equipment.warned.drop")
                        EquipmentCompatibilityStatus.INCOMPATIBLE -> messages.get("equipment.invalid")
                        null -> messages.get("equipment.drop")
                    },
                    color = targetColor,
                    fontSize = 7.sp
                )
            }
        }
        destination.warning?.let {
            Text("⚠", color = Blood, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }
        if (destination.items.size > 1) {
            Text(
                "×${destination.items.size}",
                color = Amber,
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomStart).background(Coal.copy(alpha = 0.9f)).padding(2.dp)
            )
        }
        if (item != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp)
                    .clip(RoundedCornerShape(9.dp)).background(Coal.copy(alpha = 0.94f))
                    .border(1.dp, Blood, RoundedCornerShape(9.dp))
                    .semantics { contentDescription = messages.get("equipment.remove.desc", item.name, destination.name) }
                    .clickable { onRemove(destination) },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
                    val stroke = 1.5.dp.toPx()
                    drawLine(Parchment, Offset.Zero, Offset(size.width, size.height), strokeWidth = stroke)
                    drawLine(Parchment, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = stroke)
                }
            }
        }
    }
}

private fun compactSlotLabel(destination: EquipmentDestinationView): String = when (destination.slot) {
    WeaponSlots.ELIXIR_1 -> "E1"
    WeaponSlots.ELIXIR_2 -> "E2"
    WeaponSlots.ELIXIR_3 -> "E3"
    WeaponSlots.SHORT_1 -> "S1"
    WeaponSlots.SHORT_2 -> "S2"
    WeaponSlots.FOREARM_RIGHT -> "R"
    WeaponSlots.FOREARM_LEFT -> "L"
    else -> destination.name.take(1)
}

internal fun slotDisplayName(messages: EditorMessages, slot: Int): String {
    val key = InventoryViewState.slotNameKey(slot) ?: return WeaponSlots.name(slot)
    return messages.get(key)
}

// Satchel/alchemy grid geometry: seven fixed-size cells per row with gaps.
private val SatchelCellSize = 44.dp
private val SatchelCellGap = 5.dp
private val SatchelGridWidth = SatchelCellSize * 7 + SatchelCellGap * 6

// The carried-items shell hugs the grid instead of stretching with the
// window: grid + container padding (9*2) + shell padding (15*2).
private val CarriedPanelWidth = SatchelGridWidth + 9.dp * 2 + 15.dp * 2

@Composable
private fun CarriedItemsCard(
    view: InventoryViewState,
    onSelect: (InventoryItemView) -> Unit,
    environment: AppEnvironment,
    modifier: Modifier,
    scrollable: Boolean = true,
    canAdd: Boolean = true,
    onAdd: (InventoryDestination) -> Unit = {},
    onEdit: (InventoryItemView) -> Unit = {},
    onDragState: (String?) -> Unit = {}
) {
    val messages = LocalEditorMessages.current
    ShellPanel(messages.get("carried.title"), messages.get("carried.subtitle"), modifier) {
        Column(
            Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quest items sit on top, matching the in-game inventory screen.
            QuestItemsStrip(view.questItems, environment, onSelect, onEdit, { onAdd(InventoryDestination.QUEST_ITEMS) }, canAdd, onDragState)
            ContainerGrid(view.satchel, CarriedContainerKind.SATCHEL, environment, onSelect, onEdit, { onAdd(InventoryDestination.SATCHEL) }, canAdd, onDragState)
            ContainerGrid(view.alchemy, CarriedContainerKind.ALCHEMY, environment, onSelect, onEdit, { onAdd(InventoryDestination.ALCHEMY) }, canAdd, onDragState)
            view.satchel.overflow.orEmpty().plus(view.alchemy.overflow).forEach { item ->
                InventoryItemRow(environment, item, onSelect, onDragState, onEdit)
            }
        }
    }
}

private enum class CarriedContainerKind(val messageKey: String) {
    QUEST("carried.container.quest.desc"),
    SATCHEL("carried.container.satchel.desc"),
    ALCHEMY("carried.container.alchemy.desc")
}

/** Small purpose-drawn glyphs remain recognizable where full game textures do not. */
@Composable
private fun CarriedContainerGlyph(kind: CarriedContainerKind, modifier: Modifier) {
    val messages = LocalEditorMessages.current
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = messages.get(kind.messageKey) }) {
        val stroke = 1.6.dp.toPx()
        val style = Stroke(stroke)
        val ink = Amber
        when (kind) {
            CarriedContainerKind.QUEST -> {
                drawRoundRect(
                    ink,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.08f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.84f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
                    style = style
                )
                drawLine(ink, Offset(size.width * 0.32f, size.height * 0.38f), Offset(size.width * 0.68f, size.height * 0.38f), stroke)
                drawLine(ink, Offset(size.width * 0.32f, size.height * 0.56f), Offset(size.width * 0.68f, size.height * 0.56f), stroke)
                drawLine(ink, Offset(size.width * 0.32f, size.height * 0.74f), Offset(size.width * 0.58f, size.height * 0.74f), stroke)
            }
            CarriedContainerKind.SATCHEL -> {
                drawRoundRect(
                    ink,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.34f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.80f, size.height * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f, size.width * 0.10f),
                    style = style
                )
                val handle = Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.36f)
                    cubicTo(
                        size.width * 0.30f, size.height * 0.06f,
                        size.width * 0.70f, size.height * 0.06f,
                        size.width * 0.70f, size.height * 0.36f
                    )
                }
                drawPath(handle, ink, style = style)
                drawLine(ink, Offset(size.width * 0.12f, size.height * 0.57f), Offset(size.width * 0.88f, size.height * 0.57f), stroke)
                drawCircle(ink, radius = stroke, center = Offset(size.width * 0.5f, size.height * 0.68f))
            }
            CarriedContainerKind.ALCHEMY -> {
                val flask = Path().apply {
                    moveTo(size.width * 0.37f, size.height * 0.08f)
                    lineTo(size.width * 0.63f, size.height * 0.08f)
                    lineTo(size.width * 0.60f, size.height * 0.40f)
                    lineTo(size.width * 0.84f, size.height * 0.82f)
                    quadraticTo(size.width * 0.88f, size.height * 0.92f, size.width * 0.74f, size.height * 0.92f)
                    lineTo(size.width * 0.26f, size.height * 0.92f)
                    quadraticTo(size.width * 0.12f, size.height * 0.92f, size.width * 0.16f, size.height * 0.82f)
                    lineTo(size.width * 0.40f, size.height * 0.40f)
                    close()
                }
                drawPath(flask, ink, style = style)
                drawLine(ink, Offset(size.width * 0.25f, size.height * 0.72f), Offset(size.width * 0.75f, size.height * 0.72f), stroke)
            }
        }
    }
}

@Composable
private fun ContainerGrid(
    container: InventoryContainerView,
    kind: CarriedContainerKind,
    environment: AppEnvironment,
    onSelect: (InventoryItemView) -> Unit,
    onEdit: (InventoryItemView) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
    onDragState: (String?) -> Unit
) {
    // Fixed slot geometry: see SatchelCellSize/SatchelGridWidth above. Both
    // the grid and its container wrap to that width and stay centered in the
    // panel instead of stretching when the window grows.
    val messages = LocalEditorMessages.current
    val gridWidth = SatchelGridWidth
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.background(Iron, RoundedCornerShape(6.dp)).padding(9.dp)) {
            Row(Modifier.width(gridWidth), verticalAlignment = Alignment.CenterVertically) {
                CarriedContainerGlyph(kind, Modifier.size(22.dp))
                Spacer(Modifier.width(7.dp))
                Text(container.title, color = Parchment, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdd, enabled = canAdd) { Text(messages.get("common.add"), fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                container.cells.asSequence().chunked(14).forEachIndexed { index, blockCells ->
                    if (index > 0) {
                        Spacer(Modifier.height(9.dp))
                        HorizontalDivider(Modifier.width(gridWidth), color = Line)
                        Spacer(Modifier.height(9.dp))
                    }
                    blockCells.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(SatchelCellGap)) {
                            row.forEach { item ->
                                Box(
                                    Modifier.size(SatchelCellSize).clip(RoundedCornerShape(4.dp)).background(if (item != null) Color(0xFF3C493F) else Raised)
                                        .border(if (item != null) 1.dp else 0.5.dp, if (item != null) Amber else Line, RoundedCornerShape(4.dp))
                                        .then(item?.let { dragSourceModifier(it.id, onDragState) } ?: Modifier)
                                        .combinedClickable(
                                            enabled = item != null,
                                            onClick = { item?.let(onSelect) },
                                            onDoubleClick = { item?.let(onEdit) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ItemIcon(environment, item, Modifier.fillMaxSize().padding(3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestItemsStrip(
    items: List<InventoryItemView>,
    environment: AppEnvironment,
    onSelect: (InventoryItemView) -> Unit,
    onEdit: (InventoryItemView) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
    onDragState: (String?) -> Unit
) {
    val messages = LocalEditorMessages.current
    Column(Modifier.fillMaxWidth().background(Iron, RoundedCornerShape(6.dp)).padding(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarriedContainerGlyph(CarriedContainerKind.QUEST, Modifier.size(22.dp))
            Spacer(Modifier.width(7.dp))
            Text(messages.get("quest.title"), color = Parchment, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd, enabled = canAdd) { Text(messages.get("common.add"), fontSize = 11.sp) }
        }
        if (items.isEmpty()) {
            Text(messages.get("quest.empty"), color = Muted, fontSize = 11.sp)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.forEach { item ->
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(Raised)
                            .then(dragSourceModifier(item.id, onDragState))
                            .combinedClickable(onClick = { onSelect(item) }, onDoubleClick = { onEdit(item) }),
                        contentAlignment = Alignment.Center
                    ) {
                        ItemIcon(environment, item, Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryItemRow(
    environment: AppEnvironment,
    item: InventoryItemView,
    onSelect: (InventoryItemView) -> Unit,
    onDragState: (String?) -> Unit = {},
    onEdit: (InventoryItemView) -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Raised)
            .then(dragSourceModifier(item.id, onDragState))
            .combinedClickable(onClick = { onSelect(item) }, onDoubleClick = { onEdit(item) }).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(29.dp).background(Iron, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            ItemIcon(environment, item, Modifier.size(27.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Parchment, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.category + if (item.templateResRef.isNotEmpty()) " · ${item.templateResRef}" else "", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.count > 1) Text("×${item.count}", color = Amber, fontSize = 10.sp)
        if (item.warning != null) Text("⚠", color = Blood, fontSize = 10.sp)
    }
}

@Composable
private fun dragSourceModifier(itemId: String, onDragState: (String?) -> Unit): Modifier =
    Modifier.dragAndDropSource { _ ->
        onDragState(itemId)
        DragAndDropTransferData(
            transferable = DragAndDropTransferable(StringSelection("tweditor:$itemId")),
            supportedActions = listOf(DragAndDropTransferAction.Move),
            dragDecorationOffset = Offset.Zero,
            onTransferCompleted = { onDragState(null) }
        )
    }

/**
 * One picker-grid cell block: an item template with its game footprint. The
 * key doubles as the template resource name the commands dispatch with.
 */
private data class TemplateGridEntry(
    val key: String,
    val name: String,
    val iconResref: String?,
    val baseItem: Int,
    val warning: String? = null
)

private class TemplatePlacement(val index: Int, val col: Int, val row: Int, val width: Int, val height: Int)

/** Picker grids reuse the satchel geometry: seven fixed cells per row. */
private val PickerColumns = 7
private val PickerCellStep = SatchelCellSize + SatchelCellGap

/**
 * First-fit packing like the game's own grid: big items (weapons, armor)
 * claim their full InvSlotWidth x InvSlotHeight footprint of small cells,
 * small items (rings, elixirs) fill single cells around them.
 */
private fun packTemplateGrid(
    environment: AppEnvironment,
    entries: List<TemplateGridEntry>,
    columns: Int
): Pair<List<TemplatePlacement>, Int> {
    val placements = ArrayList<TemplatePlacement>(entries.size)
    val occupied = ArrayList<BooleanArray>()
    fun isFree(row: Int, col: Int): Boolean = occupied.getOrNull(row)?.get(col)?.not() ?: true
    fun occupy(row: Int, col: Int) {
        while (occupied.size <= row) occupied.add(BooleanArray(columns))
        occupied[row][col] = true
    }
    entries.forEachIndexed { index, entry ->
        val (footprintWidth, footprintHeight) = environment.icons.itemFootprint(entry.baseItem)
        val width = footprintWidth.coerceAtMost(columns)
        var placedAt: TemplatePlacement? = null
        var row = 0
        while (placedAt == null) {
            scan@ for (col in 0..columns - width) {
                for (dy in 0 until footprintHeight) {
                    for (dx in 0 until width) {
                        if (!isFree(row + dy, col + dx)) continue@scan
                    }
                }
                for (dy in 0 until footprintHeight) {
                    for (dx in 0 until width) occupy(row + dy, col + dx)
                }
                placedAt = TemplatePlacement(index, col, row, width, footprintHeight)
                break
            }
            if (placedAt == null) row++
        }
        placements.add(placedAt)
    }
    return placements to occupied.size
}

/** A template's real icon (async decoded, footprint sized) for the picker grids. */
@Composable
private fun TemplateIcon(environment: AppEnvironment, iconResref: String?, baseItem: Int, name: String, modifier: Modifier) {
    val revision = remember { mutableIntStateOf(0) }
    DisposableEffect(environment.icons) {
        environment.icons.addLateIconListener(Runnable { revision.intValue++ })
        onDispose { }
    }
    val bitmap = remember(iconResref, baseItem, revision.intValue) {
        iconResref?.let { environment.icons.itemIcon(it, baseItem)?.toComposeImageBitmap() }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = name, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Parchment, fontSize = 11.sp)
        }
    }
}

/**
 * The game-style template grid shared by both add-item pickers: each entry
 * occupies its real inventory footprint, hovering a block names it, clicking
 * it picks it.
 */
@Composable
private fun TemplatePickerGrid(
    environment: AppEnvironment,
    entries: List<TemplateGridEntry>,
    modifier: Modifier = Modifier,
    onPick: (TemplateGridEntry) -> Unit
) {
    var hoveredKey by remember(entries) { mutableStateOf<String?>(null) }
    val (placements, rows) = remember(entries) { packTemplateGrid(environment, entries, PickerColumns) }
    Column(modifier) {
        Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.width(SatchelGridWidth).height((PickerCellStep * rows - SatchelCellGap).coerceAtLeast(SatchelCellSize))) {
                placements.forEach { placement ->
                    val entry = entries[placement.index]
                    Box(
                        Modifier
                            .offset(x = PickerCellStep * placement.col, y = PickerCellStep * placement.row)
                            .size(
                                width = SatchelCellSize * placement.width + SatchelCellGap * (placement.width - 1),
                                height = SatchelCellSize * placement.height + SatchelCellGap * (placement.height - 1)
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (hoveredKey == entry.key) Color(0xFF46564B) else Color(0xFF3C493F))
                            .border(1.dp, if (entry.warning == null) Amber else Amber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .pointerInput(entry.key) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Enter -> hoveredKey = entry.key
                                            PointerEventType.Exit -> if (hoveredKey == entry.key) hoveredKey = null
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                            .clickable { onPick(entry) }
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TemplateIcon(environment, entry.iconResref, entry.baseItem, entry.name, Modifier.fillMaxSize())
                    }
                }
            }
        }
        val hovered = entries.firstOrNull { it.key == hoveredKey }
        val messages = LocalEditorMessages.current
        Text(
            hovered?.let { entry -> entry.warning?.let { "${entry.name} · $it" } ?: entry.name }
                ?: messages.get("template.grid.count", entries.size),
            color = if (hovered?.warning != null) Amber else Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun EquipmentPickerDialog(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    targetSlot: Int,
    destinationOccupied: Boolean,
    onDismiss: () -> Unit
) {
    var query by remember(targetSlot) { mutableStateOf("") }
    val messages = LocalEditorMessages.current
    val entries = EquipmentCatalog.pickerItems(environment, targetSlot, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(messages.get("equipment.picker.title", slotDisplayName(messages, targetSlot))) },
    text = {
        Column(Modifier.widthIn(min = 380.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(messages.get("inventory.picker.search")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (destinationOccupied) {
                Text(
                    messages.get("equipment.picker.replace.hint"),
                    color = Amber,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
            if (entries.isEmpty()) {
                Text(
                    messages.get("equipment.picker.empty"),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 7.dp)
                )
                Text(
                    messages.get("equipment.picker.empty.hint"),
                    color = Muted,
                    fontSize = 11.sp
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 390.dp).padding(top = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries, key = { it.resourceName }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Raised)
                                .border(1.dp, if (entry.warning == null) Line else Amber, RoundedCornerShape(5.dp))
                                .clickable {
                                    val result = workflow.dispatch(
                                        EquipTemplateCommand(
                                            environment = environment,
                                            resourceName = entry.resourceName,
                                            targetSlot = targetSlot,
                                            replaceExisting = destinationOccupied,
                                            confirmUncertain = entry.warning != null
                                        )
                                    )
                                    if (result is EditorCommandResult.Applied) onDismiss()
                                }
                                .padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TemplateIcon(
                                environment,
                                entry.iconResref,
                                entry.baseItem,
                                entry.name,
                                Modifier.size(42.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, color = Parchment, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.resourceName, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                entry.warning?.let { Text(messages.render(it), color = Amber, fontSize = 9.sp, maxLines = 2) }
                            }
                        }
                    }
                }
            }
        }
    },
        confirmButton = { TextButton(onClick = onDismiss) { Text(messages.get("common.cancel")) } }
    )
}

@Composable
private fun InventoryPickerDialog(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    destination: InventoryDestination,
    onDismiss: () -> Unit
) {
    var query by remember(destination) { mutableStateOf("") }
    var category by remember(destination) { mutableStateOf(InventoryPickerCategory.ALL) }
    val messages = LocalEditorMessages.current
    val entries = InventoryCatalog.pickerItems(environment, destination, query, category)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(messages.get("inventory.picker.title", messages.get(destination.messageKey))) },
    text = {
        Column(Modifier.widthIn(min = 380.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(messages.get("inventory.picker.search")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                InventoryPickerCategory.entries.forEach { option ->
                    TextButton(onClick = { category = option }) {
                        Text(messages.get(option.messageKey), color = if (category == option) Amber else Muted, fontSize = 10.sp)
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(messages.get("inventory.picker.empty"), color = Muted, fontSize = 11.sp)
            } else {
                TemplatePickerGrid(
                    environment,
                    entries.map {
                        TemplateGridEntry(it.resourceName, it.name, it.iconResref, it.baseItem)
                    },
                    Modifier.padding(top = 7.dp)
                ) { entry ->
                    val result = workflow.dispatch(
                        AddInventoryItemCommand(environment, destination, entry.key)
                    )
                    if (result is EditorCommandResult.Applied) onDismiss()
                }
            }
        }
    },
        confirmButton = { TextButton(onClick = onDismiss) { Text(messages.get("common.cancel")) } }
    )
}

@Composable
private fun ItemWindow(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    sourceId: String,
    item: InventoryItemView?,
    equipment: Boolean,
    languageId: Int,
    onDismiss: () -> Unit
) {
    val messages = LocalEditorMessages.current
    val initial = remember(sourceId, equipment) {
        if (equipment) readEquipmentEditValues(workflow.session, sourceId)
        else readInventoryEditValues(workflow.session, sourceId)
    }
    // The language id keys the remember so the description and effect names
    // re-resolve through the newly selected content TLK.
    val details = remember(sourceId, languageId) { readItemDetails(environment, workflow.session, sourceId) }
    val windowTitle = item?.name?.ifEmpty { null } ?: messages.get("item.fallback")
    val windowState = rememberWindowState(width = 760.dp, height = 820.dp)

    Window(
        onCloseRequest = onDismiss,
        title = messages.get("item.window.title", windowTitle),
        state = windowState
    ) {
        ItemWindowBody(environment, workflow, sourceId, item, equipment, initial, details, onDismiss)
    }
}

@Composable
internal fun ItemWindowBody(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    sourceId: String,
    item: InventoryItemView?,
    equipment: Boolean,
    initial: EquipmentEditValues?,
    details: ItemDetailsView?,
    onDismiss: () -> Unit
) {
    val messages = LocalEditorMessages.current
    ReadableTypography {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            secondary = Verdigris,
            background = Ink,
            surface = Coal,
            surfaceVariant = Iron,
            onPrimary = Ink,
            onBackground = Parchment,
            onSurface = Parchment,
            outline = Line,
            error = Blood
        )
    ) {
        Surface(
            Modifier.fillMaxSize().semantics { contentDescription = messages.get("item.window.desc") },
            color = Ink
        ) {
            if (initial == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(item?.name ?: messages.get("item.fallback"), color = Parchment, style = MaterialTheme.typography.headlineMedium)
                    Text(messages.get("item.gone"), color = Muted)
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Parchment)
                    ) { Text(messages.get("common.close")) }
                }
            } else {
                ItemWindowContent(environment, workflow, sourceId, item, equipment, initial, details, onDismiss)
            }
        }
    }
    }
}

internal fun readableDensity(base: Density): Density =
    Density(density = base.density, fontScale = base.fontScale * 1.2f)

@Composable
private fun ItemWindowContent(
    environment: AppEnvironment,
    workflow: ComposeFileWorkflow,
    sourceId: String,
    item: InventoryItemView?,
    equipment: Boolean,
    initial: EquipmentEditValues,
    details: ItemDetailsView?,
    onDismiss: () -> Unit
) {
    var modelPart1 by remember(sourceId) { mutableStateOf(initial.modelPart1.toString()) }
    var quality by remember(sourceId) { mutableStateOf(initial.quality.toString()) }
    var customCost by remember(sourceId) { mutableStateOf(initial.customCost.toString()) }
    var weaponType by remember(sourceId) { mutableStateOf(initial.weaponType) }
    var selfAbilities by remember(sourceId) { mutableStateOf(initial.selfAbilities.joinToString(", ") { it.name }) }
    var opponentAbilities by remember(sourceId) { mutableStateOf(initial.opponentAbilities.joinToString(", ") { it.name }) }
    var error by remember(sourceId) { mutableStateOf<String?>(null) }
    val messages = LocalEditorMessages.current

    fun parseAbilities(text: String, originals: List<WeaponAbility>): List<WeaponAbility> = text.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { name -> WeaponAbility(name, originals.firstOrNull { it.name == name }?.stack ?: 1) }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).background(Iron, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                ItemIcon(environment, item, Modifier.size(54.dp), highResolution = equipment)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item?.name ?: messages.get("item.fallback"), color = Parchment, style = MaterialTheme.typography.headlineMedium)
                Text(
                    listOfNotNull(item?.category, item?.templateResRef?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
        HorizontalDivider(color = Line)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(messages.get("item.details.title"), color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (details == null || !details.hasContent) {
                Text(messages.get("item.details.empty"), color = Muted, fontSize = 12.sp)
            } else {
                if (details.description.isNotBlank()) {
                    Text(messages.get("item.details.description"), color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    details.description.split("\n\n").forEach { Text(it, color = Parchment, fontSize = 12.sp) }
                }
                details.effects.groupBy { it.groupKey }.forEach { (groupKey, effects) ->
                    Text(messages.get("item.effects.$groupKey"), color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    effects.forEach { effect ->
                        val stack = if (effect.stack > 1) " ×${effect.stack}" else ""
                        Text("• ${effect.name}$stack", color = Parchment, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            HorizontalDivider(color = Line)
            Text(messages.get("item.edit.title"), color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            OutlinedTextField(modelPart1, { modelPart1 = it }, label = { Text(messages.get("item.edit.modelPart1")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(quality, { quality = it }, label = { Text(messages.get("item.edit.quality")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(customCost, { customCost = it }, label = { Text(messages.get("item.edit.customCost")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weaponType, { weaponType = it }, label = { Text(messages.get("item.edit.weaponType")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(selfAbilities, { selfAbilities = it }, label = { Text(messages.get("item.edit.selfAbilities")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(opponentAbilities, { opponentAbilities = it }, label = { Text(messages.get("item.edit.opponentAbilities")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Blood, fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (!equipment) {
                OutlinedButton(
                    onClick = {
                        if (workflow.dispatch(RemoveInventoryItemCommand(sourceId)) is EditorCommandResult.Applied) onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blood)
                ) { Text(messages.get("common.remove")) }
            }
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Parchment)
            ) { Text(messages.get("common.close")) }
            Button(
                onClick = {
                    val values = EquipmentEditValues(
                        selfAbilities = parseAbilities(selfAbilities, initial.selfAbilities),
                        opponentAbilities = parseAbilities(opponentAbilities, initial.opponentAbilities),
                        modelPart1 = modelPart1.toIntOrNull() ?: -1,
                        quality = quality.toIntOrNull() ?: -1,
                        customCost = customCost.toIntOrNull() ?: -1,
                        weaponType = weaponType.trim()
                    )
                    val result = if (equipment) {
                        workflow.dispatch(EditEquipmentCommand(environment, sourceId, values))
                    } else {
                        workflow.dispatch(EditInventoryItemCommand(environment, sourceId, values))
                    }
                    if (result is EditorCommandResult.Applied) onDismiss()
                    else if (result is EditorCommandResult.Rejected) error = result.problems.joinToString("\n") { messages.render(it) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
            ) { Text(messages.get("common.apply")) }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = Parchment, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SecondaryTabs(tabs: List<Pair<String, String>>, active: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        tabs.forEach { (key, label) ->
            TextButton(onClick = { onSelect(key) }, enabled = true) {
                Text(label, color = if (active == key) Amber else Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Iron, RoundedCornerShape(7.dp)).padding(13.dp)) {
        Text(label, color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Parchment, fontSize = 17.sp)
    }
}

@Composable
private fun ShellPanel(title: String, overline: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Coal, RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(15.dp)) {
        Text(overline.uppercase(), color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(title, color = Parchment, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A game texture drawn whole; the shell decodes from the base archives. */
@Composable
private fun GameAtlasGlyph(
    environment: AppEnvironment,
    resref: String,
    fallback: String,
    modifier: Modifier
) {
    GameTextureGlyph(
        environment = environment,
        resref = resref,
        fallback = fallback,
        modifier = modifier
    )
}

/**
 * A game texture drawn whole (transparent margins trimmed so emblems fill
 * their box); the shell decodes from the base archives.
 */
@Composable
private fun GameTextureGlyph(
    environment: AppEnvironment,
    resref: String,
    fallback: String,
    modifier: Modifier
) {
    val revision = remember { mutableIntStateOf(0) }
    DisposableEffect(environment.icons) {
        val listener = Runnable { revision.intValue++ }
        environment.icons.addLateIconListener(listener)
        onDispose { }
    }
    val bitmap = remember(resref, revision.intValue) {
        environment.icons.imageByResrefBaseArchive(resref)?.let {
            trimTransparent(it).toComposeImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = fallback, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(fallback, color = Parchment, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

private fun trimTransparent(image: BufferedImage): BufferedImage {
    val width = image.width
    val height = image.height
    val argb = image.getRGB(0, 0, width, height, null, 0, width)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (argb[row + x] ushr 24 > 16) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) {
        return image
    }
    minX = (minX - 1).coerceAtLeast(0)
    minY = (minY - 1).coerceAtLeast(0)
    maxX = (maxX + 1).coerceAtMost(width - 1)
    maxY = (maxY + 1).coerceAtMost(height - 1)
    return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
}

private fun cropImage(image: BufferedImage, crop: IntArray): BufferedImage? = runCatching {
    require(crop.size == 4)
    require(crop[0] >= 0 && crop[1] >= 0 && crop[2] > 0 && crop[3] > 0)
    require(crop[0] + crop[2] <= image.width && crop[1] + crop[3] <= image.height)
    image.getSubimage(crop[0], crop[1], crop[2], crop[3])
}.getOrNull()

/**
 * A real game item icon resolved from the record's BaseItem / ModelPart1 /
 * TemplateResRef. Dense grids use footprint-sized thumbnails; large Equipment
 * cells opt into the trimmed source texture so Compose never upscales a 48 px
 * intermediary.
 */
@Composable
internal fun ItemIcon(
    environment: AppEnvironment,
    item: InventoryItemView?,
    modifier: Modifier,
    highResolution: Boolean = false
) {
    val revision = remember { mutableIntStateOf(0) }
    DisposableEffect(environment.icons) {
        val listener = Runnable { revision.intValue++ }
        environment.icons.addLateIconListener(listener)
        onDispose { }
    }
    val bitmap = remember(item, highResolution, revision.intValue) {
        item?.let {
            val image = if (highResolution) {
                environment.icons.itemViewIconFullResolution(it.baseItem, it.modelPart, it.templateResRef)
            } else {
                environment.icons.itemViewIcon(it.baseItem, it.modelPart, it.templateResRef)
            }
            image?.toComposeImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = item?.name, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                item?.name?.take(1)?.uppercase() ?: "",
                color = if (item?.warning == null) Parchment else Blood,
                fontSize = 11.sp
            )
        }
    }
}

private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick)




