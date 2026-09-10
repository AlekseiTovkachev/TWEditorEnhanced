package app.tweditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The game-style save browser: lists the *.TheWitcherSave files of a directory
 * with their embedded screenshot, in-game save name and player level, read one
 * save at a time in the background. The stock file dialog stays reachable
 * through "Browse..." for saves stored anywhere else.
 */
@Composable
fun SavePickerDialog(
    environment: AppEnvironment,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit
) {
    val directory = resolveSavesDirectory(environment)
    val messages = LocalEditorMessages.current
    val files = remember(directory) {
        directory?.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
    val summaries = remember(directory) { mutableStateMapOf<File, SaveSummary?>() }
    var selected by remember(directory) { mutableStateOf<File?>(files.firstOrNull()) }
    var scanning by remember(directory) { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cache = SaveSummaryCache(environment)
            for (file in files) {
                if (!scanning) break
                summaries[file] = runCatching { cache.get(file) }.getOrNull()
            }
        }
        scanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(messages.get("picker.title")) },
        text = {
            Column(Modifier.widthIn(min = 640.dp).heightIn(max = 470.dp)) {
                if (files.isEmpty()) {
                    Text(messages.get("picker.empty", directory?.path ?: messages.get("picker.empty.fallback")))
                    Spacer(Modifier.height(8.dp))
                    Text(messages.get("picker.browse.hint"), fontSize = 11.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(files) { file ->
                            SaveRow(
                                file = file,
                                summary = summaries[file],
                                selected = file == selected,
                                onSelect = { selected = file },
                                onOpen = {
                                    selected = file
                                    onSelected(file)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (scanning) {
                            messages.get("picker.scanning", files.size)
                        } else {
                            messages.get(
                                "picker.scanned",
                                summaries.values.count { it != null },
                                files.size,
                                directory?.path ?: ""
                            )
                        },
                        fontSize = 10.sp,
                        color = Muted
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let(onSelected) },
                enabled = selected != null,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
            ) { Text(messages.get("command.open")) }
        },
        dismissButton = {
            TextButton(onClick = {
                val browsed = browseForSave(messages.get("picker.browse.dialog"), initialDirectory = directory, mode = FileDialog.LOAD)
                if (browsed != null) {
                    environment.properties.setProperty("current.directory", browsed.parentFile?.absolutePath ?: "")
                    onSelected(browsed)
                }
            }) { Text(messages.get("picker.browse")) }
        }
    )
}

@Composable
private fun SaveRow(
    file: File,
    summary: SaveSummary?,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    val messages = LocalEditorMessages.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color(0xFF26352E) else Raised, RoundedCornerShape(5.dp))
            .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(5.dp))
            .combinedClickable(onClick = { onSelect() }, onDoubleClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SaveScreenshot(summary, Modifier.size(width = 86.dp, height = 58.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary?.saveName ?: file.name,
                color = Parchment,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (summary) {
                    null -> file.name
                    else -> messages.get(
                        "picker.row.subtitle",
                        if (summary.level >= 0) summary.level.toString() else "?",
                        formattedDate(summary.lastModified),
                        file.name
                    )
                },
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SaveScreenshot(summary: SaveSummary?, modifier: Modifier) {
    if (summary?.screenshot == null) {
        Box(
            modifier.background(Iron, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(LocalEditorMessages.current.get("picker.noImage"), color = Muted, fontSize = 9.sp)
        }
        return
    }
    val tga = summary.screenshot
    val contentHeight = tga.contentHeight(TgaImage.SCREENSHOT_PADDING)
    val bitmap = remember(summary.file) {
        BufferedImage(tga.width, contentHeight, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, tga.width, contentHeight, tga.argb, 0, tga.width)
        }.toComposeImageBitmap()
    }
    Image(bitmap, contentDescription = summary.saveName, modifier = modifier, contentScale = ContentScale.FillBounds)
}

private fun formattedDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

internal fun resolveSavesDirectory(environment: AppEnvironment): File? {
    environment.properties.getProperty("current.directory")?.let { remembered ->
        File(remembered).takeIf { it.isDirectory }?.let { return it }
    }
    return environment.gamePath?.let { File(it, "saves") }?.takeIf { it.isDirectory }
}

internal fun browseForSave(title: String, initialDirectory: File?, defaultName: String? = null, mode: Int): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    initialDirectory?.takeIf { it.isDirectory }?.let { dialog.directory = it.absolutePath }
    defaultName?.let { dialog.file = it }
    dialog.isVisible = true
    val selected = dialog.file ?: return null
    return File(dialog.directory ?: initialDirectory?.absolutePath ?: File(".").absolutePath, selected)
}

