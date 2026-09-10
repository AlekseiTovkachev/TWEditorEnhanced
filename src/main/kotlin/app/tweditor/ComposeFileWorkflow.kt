package app.tweditor

import java.io.File
import java.awt.FileDialog
import java.awt.Frame
import java.util.concurrent.CopyOnWriteArrayList

/**
 * File/session actions exposed to the Compose shell.
 *
 * The workflow deliberately owns no UI widgets.  Long-running load and save work
 * is delegated to the existing loader/saver threads, while this class publishes a
 * small immutable state snapshot for the declarative UI. User-facing text is
 * carried as message keys ([LocalizedText]); the shell renders it localized.
 */
class ComposeFileWorkflow(
    val environment: AppEnvironment,
    val session: GameSession = GameSession(File(environment.tmpDir))
) {
    val editorCommands = EditorCommandController(session)

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val operationLock = Any()

    @Volatile
    private var busy = false
    @Volatile
    private var progress = 0
    @Volatile
    private var busyMessage: LocalizedText? = null
    @Volatile
    private var statusMessage: LocalizedText? = null
    @Volatile
    private var errorMessage: List<LocalizedText> = emptyList()
    @Volatile
    private var templatesReady = false
    @Volatile
    private var advancedJournalEditing = false
    private var pendingOperationError: String? = null
    private var completion: ((Boolean) -> Unit)? = null
    private var pendingSaveAsTarget: File? = null
    private var pendingSaveAsCompletion: ((Boolean) -> Unit)? = null

    fun snapshot(): ComposeFileState {
        val save = session.saveDatabase
        val commandState = editorCommands.state()
        return ComposeFileState(
            fileName = save?.getName(),
            filePath = save?.getFile()?.absolutePath,
            dataModified = session.isDataModified(),
            draftDirty = session.isDraftDirty(),
            canSaveAs = save != null && !busy,
            canApply = save != null && session.isDraftDirty() && !busy,
            canRevert = save != null && session.isDraftDirty() && !busy,
            canUndo = save != null && commandState.canUndo && !busy,
            canRestoreBackup = save != null && session.hasSaveBackup() && !busy,
            busy = busy,
            progress = progress,
            busyMessage = busyMessage,
            statusMessage = statusMessage,
            errorMessage = errorMessage,
            overwriteTarget = pendingSaveAsTarget?.name,
            templatesReady = templatesReady,
            advancedJournalEditing = advancedJournalEditing,
            pendingChanges = commandState.pendingChanges
        )
    }

    fun setAdvancedJournalEditing(enabled: Boolean) {
        if (enabled && session.saveDatabase == null) {
            return
        }
        advancedJournalEditing = enabled
        publish()
    }

    fun addListener(listener: () -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun markTemplatesReady() {
        templatesReady = true
        publish()
    }

    fun clearMessages() {
        statusMessage = null
        errorMessage = emptyList()
        publish()
    }

    fun open(file: File, onComplete: (Boolean) -> Unit = {}) {
        if (busy) {
            return
        }
        if (file.name.endsWith(SaveBackup.BACKUP_SUFFIX)) {
            fail(listOf(LocalizedText("workflow.open.backupNotEditable")))
            onComplete(false)
            return
        }
        if (!file.isFile) {
            fail(listOf(LocalizedText("workflow.open.fileMissing", file.absolutePath)))
            onComplete(false)
            return
        }

        session.close()
        advancedJournalEditing = false
        editorCommands.markSaved()
        startOperation(LocalizedText("workflow.open.loading", file.name), onComplete)
        LoadFile(
            session,
            environment,
            file,
            onProgress = { updateProgress(it) },
            onComplete = { success ->
                if (success) {
                    editorCommands.markSaved()
                    finishOperation(true, LocalizedText("workflow.open.opened", file.name))
                } else {
                    session.close()
                    advancedJournalEditing = false
                    editorCommands.markSaved()
                    finishOperation(
                        false,
                        pendingOperationError?.let { LocalizedText("workflow.open.failed", it) }
                            ?: LocalizedText("workflow.open.failed", file.name)
                    )
                }
            },
            onError = { text, exc ->
                pendingOperationError = "$text${exc.message?.let { ": $it" } ?: ""}"
            }
        ).start()
    }

    fun save(onComplete: (Boolean) -> Unit = {}) {
        if (busy) {
            return
        }
        if (session.saveDatabase == null) {
            fail(listOf(LocalizedText("workflow.save.noOpen")))
            onComplete(false)
            return
        }
        val problems = session.runValidation()
        if (problems.isNotEmpty()) {
            fail(validationMessages(problems))
            onComplete(false)
            return
        }
        startOperation(LocalizedText("workflow.save.saving", session.saveDatabase!!.getName()), onComplete)
        SaveFile(
            session,
            environment,
            targetFile = null,
            onProgress = { updateProgress(it) },
            onComplete = { success ->
                if (success) {
                    editorCommands.markSaved()
                    finishOperation(true, LocalizedText("workflow.save.saved", session.saveDatabase!!.getName()))
                } else {
                    finishOperation(
                        false,
                        pendingOperationError?.let { LocalizedText("workflow.save.failedDetail", it) }
                            ?: LocalizedText("workflow.save.failed")
                    )
                }
            },
            onError = { text, exc ->
                pendingOperationError = "$text${exc.message?.let { ": $it" } ?: ""}"
            }
        ).start()
    }

    fun saveAsInteractive(onComplete: (Boolean) -> Unit = {}) {
        if (busy || session.saveDatabase == null) {
            return
        }
        val currentFile = session.saveDatabase!!.getFile()
        var target = browseForSave(
            title = "Save As",
            initialDirectory = currentFile.parentFile,
            defaultName = currentFile.nameWithoutExtension + " copy.TheWitcherSave",
            mode = FileDialog.SAVE
        ) ?: return
        if (!target.name.endsWith(".TheWitcherSave")) {
            target = File(target.parentFile, target.name + ".TheWitcherSave")
        }
        if (!Regex("\\d{6} .+").matches(target.name.removeSuffix(".TheWitcherSave"))) {
            fail(listOf(LocalizedText("workflow.saveAs.nameInvalid")))
            onComplete(false)
            return
        }
        if (target.absoluteFile == currentFile.absoluteFile) {
            save(onComplete)
            return
        }
        if (target.isFile) {
            pendingSaveAsTarget = target
            pendingSaveAsCompletion = onComplete
            publish()
            return
        }
        saveAs(target, onComplete)
    }

    /** Called by the Compose overwrite confirmation dialog. */
    fun confirmOverwrite() {
        val target = pendingSaveAsTarget ?: return
        val callback = pendingSaveAsCompletion ?: {}
        pendingSaveAsTarget = null
        pendingSaveAsCompletion = null
        saveAs(target, callback)
    }

    fun cancelOverwrite() {
        pendingSaveAsTarget = null
        pendingSaveAsCompletion = null
        publish()
    }

    fun saveAs(target: File, onComplete: (Boolean) -> Unit = {}) {
        if (busy || session.saveDatabase == null) {
            return
        }
        val problems = session.runValidation()
        if (problems.isNotEmpty()) {
            fail(validationMessages(problems))
            onComplete(false)
            return
        }
        startOperation(LocalizedText("workflow.saveAs.saving", target.name), onComplete)
        SaveFile(
            session,
            environment,
            targetFile = target,
            onProgress = { updateProgress(it) },
            onComplete = { success ->
                if (success) {
                    editorCommands.markSaved()
                    finishOperation(true, LocalizedText("workflow.saveAs.saved", target.name))
                } else {
                    finishOperation(
                        false,
                        pendingOperationError?.let { LocalizedText("workflow.saveAs.failed", it) }
                            ?: LocalizedText("workflow.saveAs.failed", target.name)
                    )
                }
            },
            onError = { text, exc ->
                pendingOperationError = "$text${exc.message?.let { ": $it" } ?: ""}"
            }
        ).start()
    }

    fun apply() {
        if (busy || session.saveDatabase == null) {
            return
        }
        val result = editorCommands.apply()
        if (result.completed) {
            statusMessage = LocalizedText("workflow.apply.done")
            errorMessage = emptyList()
        } else {
            errorMessage = result.problems.ifEmpty { listOf(LocalizedText("workflow.apply.failed")) }
            statusMessage = null
        }
        publish()
    }

    fun dispatch(command: EditorCommand): EditorCommandResult {
        if (busy || session.saveDatabase == null) {
            val state = editorCommands.state()
            return EditorCommandResult.Rejected(listOf(LocalizedText("workflow.dispatch.noEditableSave")), state)
        }
        val result = editorCommands.dispatch(command)
        if (result is EditorCommandResult.Rejected) {
            errorMessage = result.problems
            statusMessage = null
        } else {
            errorMessage = emptyList()
            statusMessage = null
        }
        publish()
        return result
    }

    fun undo() {
        if (busy || session.saveDatabase == null) return
        val result = editorCommands.undo()
        if (result.completed) {
            statusMessage = LocalizedText("workflow.undo.done")
            errorMessage = emptyList()
        } else {
            errorMessage = result.problems.ifEmpty { listOf(LocalizedText("workflow.undo.empty")) }
            statusMessage = null
        }
        publish()
    }

    fun revert() {
        if (busy || session.saveDatabase == null) {
            return
        }
        val result = editorCommands.revert()
        if (result.completed) {
            statusMessage = LocalizedText("workflow.revert.done")
            errorMessage = emptyList()
        } else {
            errorMessage = result.problems.ifEmpty { listOf(LocalizedText("workflow.revert.empty")) }
            statusMessage = null
        }
        publish()
    }

    fun restoreBackup(onComplete: (Boolean) -> Unit = {}) {
        if (busy) {
            return
        }
        val target = session.saveDatabase?.getFile()
        if (target == null || !session.hasSaveBackup()) {
            fail(listOf(LocalizedText("workflow.restore.none")))
            onComplete(false)
            return
        }
        // Notify the caller only after the restored archive has been parsed again,
        // not after the sibling .bak copy alone has been written.
        startOperation(LocalizedText("workflow.restore.restoring", target.name), {})
        Thread {
            try {
                session.restoreSaveBackup()
                session.close()
                advancedJournalEditing = false
                editorCommands.markSaved()
                finishOperation(true, LocalizedText("workflow.restore.reloading", target.name))
                open(target, onComplete)
            } catch (exc: Throwable) {
                finishOperation(
                    false,
                    LocalizedText(
                        "workflow.restore.failed",
                        exc.message ?: exc.javaClass.simpleName
                    )
                )
                onComplete(false)
            }
        }.apply {
            name = "save-backup-restore"
            isDaemon = true
        }.start()
    }

    fun close() {
        if (busy) {
            return
        }
        session.close()
        advancedJournalEditing = false
        editorCommands.markSaved()
        statusMessage = LocalizedText("workflow.close.done")
        errorMessage = emptyList()
        publish()
    }

    fun aboutLines(): List<LocalizedText> = buildList {
        add(LocalizedText("about.title", BuildInfo.VERSION))
        add(LocalizedText("about.java", System.getProperty("java.version")))
        add(LocalizedText("about.os", System.getProperty("os.name") + " " + System.getProperty("os.version")))
        add(
            environment.installPath?.let { LocalizedText("about.installPath", it) }
                ?: LocalizedText("about.installPath.unset")
        )
        add(
            environment.gamePath?.let { LocalizedText("about.dataPath", it) }
                ?: LocalizedText("about.dataPath.unset")
        )
        add(LocalizedText("about.language", LanguageCatalog.currentLanguage(environment, environment.languageID).displayName))
        add(LocalizedText("about.packedModules", environment.packedModules.size))
        val ownership = session.getModuleOwnership()
        add(
            ownership.startingModule?.let { LocalizedText("about.saveModule", it) }
                ?: LocalizedText("about.saveModule.unknown")
        )
        if (ownership.modules.isNotEmpty()) {
            add(LocalizedText("about.saveModuleSet", ownership.modules.joinToString(", ")))
        }
    }

    private fun startOperation(message: LocalizedText, onComplete: (Boolean) -> Unit) {
        synchronized(operationLock) {
            if (busy) {
                return
            }
            busy = true
            progress = 0
            busyMessage = message
            statusMessage = null
            errorMessage = emptyList()
            pendingOperationError = null
            completion = onComplete
        }
        publish()
    }

    private fun updateProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        publish()
    }

    private fun finishOperation(success: Boolean, message: LocalizedText) {
        val callback: ((Boolean) -> Unit)?
        synchronized(operationLock) {
            busy = false
            progress = if (success) 100 else progress
            busyMessage = null
            if (success) {
                statusMessage = message
                errorMessage = emptyList()
            } else {
                statusMessage = null
                errorMessage = listOf(message)
            }
            callback = completion
            completion = null
        }
        publish()
        callback?.invoke(success)
    }

    internal fun fail(messages: List<LocalizedText>) {
        errorMessage = messages
        statusMessage = null
        publish()
    }

    private fun validationMessages(problems: List<LocalizedText>): List<LocalizedText> =
        listOf(LocalizedText("workflow.validation.failed")) + problems

    private fun publish() {
        for (listener in listeners) {
            listener()
        }
    }
}

data class ComposeFileState(
    val fileName: String?,
    val filePath: String?,
    val dataModified: Boolean,
    val draftDirty: Boolean,
    val canSaveAs: Boolean,
    val canApply: Boolean,
    val canRevert: Boolean,
    val canUndo: Boolean,
    val canRestoreBackup: Boolean,
    val busy: Boolean,
    val progress: Int,
    val busyMessage: LocalizedText?,
    val statusMessage: LocalizedText?,
    val errorMessage: List<LocalizedText>,
    val overwriteTarget: String?,
    val templatesReady: Boolean,
    val advancedJournalEditing: Boolean,
    val pendingChanges: List<PendingChange>
)

private fun chooseFile(title: String, directory: File?, defaultName: String?, mode: Int): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    directory?.takeIf { it.isDirectory }?.let { dialog.directory = it.absolutePath }
    defaultName?.let { dialog.file = it }
    dialog.isVisible = true
    val selected = dialog.file ?: return null
    return File(dialog.directory ?: directory?.absolutePath ?: File(".").absolutePath, selected)
}
