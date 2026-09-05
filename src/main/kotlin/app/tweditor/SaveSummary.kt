package app.tweditor

import java.io.File

/**
 * The facts the save browser shows for one Save on disk, read in a single cheap pass
 * (archive directory table plus the savenfo, screenshot and player entries).
 */
class SaveSummary(
    val file: File,
    val saveName: String,
    val level: Int,
    val screenshot: TgaImage?,
    val lastModified: Long
)
