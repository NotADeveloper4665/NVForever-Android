package com.notadeveloper4665.nvforever

import android.content.Context
import android.os.StatFs
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies the user's selected Data tree into app-private storage.
 *
 * Why this exists:
 * - NVForever's current VFS uses ordinary filesystem paths; SAF content:// URIs are not
 *   POSIX paths and cannot safely be passed to Rust as one.
 * - This creates a stable, private, read-only-at-runtime filesystem view for the future
 *   native engine. The source installation is only opened for reading.
 *
 * Future optimization point:
 * Replace this staging copy with a SAF-backed VFS provider only after the Rust VFS has
 * been refactored around a file-provider abstraction. Do not pass guessed /proc paths or
 * URI strings to the current path-based VFS.
 */
object GameDataImporter {
    data class Progress(
        val bytesCopied: Long,
        val totalBytes: Long,
        val filesCopied: Int,
        val totalFiles: Int
    ) {
        val percent: Int
            get() = if (totalBytes > 0L) {
                ((bytesCopied * 100L / totalBytes).coerceIn(0L, 100L)).toInt()
            } else if (totalFiles > 0) {
                (filesCopied * 100 / totalFiles).coerceIn(0, 100)
            } else 0
    }

    class ImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * Performs an atomic import into filesDir/game-data/Data.
     * Call from a worker thread. Cancellation is checked between chunks and files.
     */
    fun import(
        context: Context,
        selectedTree: Uri,
        cancelled: AtomicBoolean,
        onProgress: (Progress) -> Unit
    ): File {
        val selected = DocumentFile.fromTreeUri(context, selectedTree)
            ?: throw ImportException("The selected folder is no longer accessible")
        if (!selected.canRead()) throw ImportException("Android no longer grants read access to this folder")

        // Users may choose either the Fallout New Vegas root or its Data folder directly.
        val dataTree = selected.findFile("Data")?.takeIf { it.isDirectory } ?: selected
        val master = dataTree.findFile("FalloutNV.esm")
        if (master?.isFile != true) {
            throw ImportException("FalloutNV.esm was not found. Select the game root or its Data folder.")
        }

        val entries = enumerate(dataTree, cancelled)
        val totalBytes = entries.sumOf { it.length.coerceAtLeast(0L) }
        val totalFiles = entries.count { !it.isDirectory }
        val staging = File(context.filesDir, "game-data.staging")
        val destination = File(context.filesDir, "game-data")
        val backup = File(context.filesDir, "game-data.previous")

        // Fail before copying when the provider reports sizes and the app volume is too small.
        // Add a configurable safety margin because SAF metadata and filesystem allocation differ.
        val safetyMargin = 512L * 1024L * 1024L
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        if (totalBytes > 0L && available < totalBytes + safetyMargin) {
            throw ImportException(
                "Not enough app storage. Need approximately ${formatBytes(totalBytes + safetyMargin)}, " +
                    "but only ${formatBytes(available)} is available."
            )
        }

        deleteTree(staging)
        deleteTree(backup)
        if (!staging.mkdirs() && !staging.isDirectory) {
            throw ImportException("Could not create the temporary import directory")
        }

        var copiedBytes = 0L
        var copiedFiles = 0
        try {
            copyDirectory(
                context = context,
                source = dataTree,
                target = staging,
                relative = "",
                cancelled = cancelled,
                onFile = { fileBytes ->
                    copiedBytes += fileBytes
                    copiedFiles++
                    onProgress(Progress(copiedBytes, totalBytes, copiedFiles, totalFiles))
                },
                onChunk = { chunkBytes ->
                    copiedBytes += chunkBytes
                    onProgress(Progress(copiedBytes, totalBytes, copiedFiles, totalFiles))
                }
            )

            // Validate the copied master before swapping directories.
            if (!File(staging, "FalloutNV.esm").isFile) {
                throw ImportException("Import finished without FalloutNV.esm; the source may have changed.")
            }
            val archiveCount = staging.listFiles()?.count {
                it.isFile && it.extension.equals("bsa", ignoreCase = true)
            } ?: 0
            if (archiveCount == 0) {
                throw ImportException("No BSA archives were copied. The selected Data folder is incomplete.")
            }

            // Atomic directory swap on the same app-private filesystem. Restore the old
            // import if the final rename fails, so an interrupted update never destroys it.
            if (destination.exists() && !destination.renameTo(backup)) {
                throw ImportException("Could not preserve the previous game-data import")
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw ImportException("Could not activate the imported game data")
            }
            deleteTree(backup)
            return File(destination, "Data")
        } catch (t: Throwable) {
            deleteTree(staging)
            if (t is ImportException) throw t
            if (t is InterruptedException || cancelled.get()) {
                throw ImportException("Game-data import cancelled")
            }
            throw ImportException("Could not import game data: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    private data class Entry(val document: DocumentFile, val relative: String, val isDirectory: Boolean, val length: Long)

    private fun enumerate(root: DocumentFile, cancelled: AtomicBoolean): List<Entry> {
        val out = ArrayList<Entry>()
        fun visit(directory: DocumentFile, prefix: String) {
            checkCancelled(cancelled)
            for (child in directory.listFiles()) {
                checkCancelled(cancelled)
                val name = child.name ?: throw ImportException("A game-data entry has no filename")
                if (name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
                    throw ImportException("Unsafe filename encountered in the selected folder")
                }
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    out += Entry(child, relative, true, 0L)
                    visit(child, relative)
                } else if (child.isFile) {
                    out += Entry(child, relative, false, child.length())
                }
            }
        }
        visit(root, "")
        return out
    }

    private fun copyDirectory(
        context: Context,
        source: DocumentFile,
        target: File,
        relative: String,
        cancelled: AtomicBoolean,
        onFile: (Long) -> Unit,
        onChunk: (Long) -> Unit
    ) {
        checkCancelled(cancelled)
        if (!target.exists() && !target.mkdirs()) throw ImportException("Could not create ${target.name}")
        for (child in source.listFiles()) {
            checkCancelled(cancelled)
            val name = child.name ?: throw ImportException("A game-data entry has no filename")
            if (name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
                throw ImportException("Unsafe filename encountered in the selected folder")
            }
            val output = File(target, name)
            if (child.isDirectory) {
                copyDirectory(context, child, output, if (relative.isEmpty()) name else "$relative/$name", cancelled, onFile, onChunk)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        var fileBytes = 0L
                        while (true) {
                            checkCancelled(cancelled)
                            val read = input.read(buffer)
                            if (read < 0) break
                            stream.write(buffer, 0, read)
                            fileBytes += read
                            onChunk(read.toLong())
                        }
                        stream.fd.sync()
                        onFile(0L)
                    }
                } ?: throw ImportException("Android could not open ${child.name}")
            }
        }
    }

    private fun checkCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw ImportException("Game-data import cancelled")
        }
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        if (!file.delete() && file.exists()) throw ImportException("Could not remove temporary file ${file.name}")
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gib >= 1.0) String.format("%.1f GiB", gib)
        else String.format("%.0f MiB", bytes / (1024.0 * 1024.0))
    }
}
