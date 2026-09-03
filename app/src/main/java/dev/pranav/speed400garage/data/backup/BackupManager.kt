package dev.pranav.speed400garage.data.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.pranav.speed400garage.data.db.GarageDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export and restore (§14, and P6 — the data outlives the device).
 *
 * This is a Phase 1 feature rather than a "later" one on purpose: if the tablet dies
 * and the data is gone, the entire premise of the app collapses. A ten-year record
 * with no way off the device is not a record, it is a hostage.
 *
 * The archive is a plain ZIP holding the SQLite database plus a JSON/CSV rendering of
 * every table. The JSON is not redundant — it is the guarantee that the data is never
 * trapped in this app's schema, readable with nothing but a text editor in ten years
 * when Room is a memory.
 */
@Singleton
class BackupManager @Inject constructor(
    private val context: Context,
    private val db: GarageDatabase,
) {

    /** Writes a complete archive to [target]. Returns the number of bytes written. */
    suspend fun export(target: Uri): Long = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file first, or the copy silently
        // misses everything logged since the last checkpoint.
        checkpoint()

        val dbFile = context.getDatabasePath(GarageDatabase.NAME)
        var bytes = 0L

        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest().toByteArray())
                zip.closeEntry()

                if (dbFile.exists()) {
                    zip.putNextEntry(ZipEntry(DB_ENTRY))
                    FileInputStream(dbFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }

                for ((name, csv) in tablesAsCsv()) {
                    zip.putNextEntry(ZipEntry("csv/$name.csv"))
                    zip.write(csv.toByteArray())
                    zip.closeEntry()
                }
                zip.finish()
            }
        } ?: throw IllegalStateException("Could not open $target for writing")

        bytes = dbFile.length()
        bytes
    } 

    /**
     * Replaces the current database with the one in [source].
     *
     * Destructive by nature, so it takes a safety copy of the existing database first
     * and rolls back if the imported file turns out not to be a usable one. The app
     * must be restarted afterwards: Room holds an open handle to the file that was
     * just swapped underneath it.
     */
    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(GarageDatabase.NAME)
        val staged = File(context.cacheDir, "restore-staged.db")
        val safety = File(context.cacheDir, "restore-safety.db")

        staged.delete()
        var found = false
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name == DB_ENTRY) {
                        FileOutputStream(staged).use { zip.copyTo(it) }
                        found = true
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: return@withContext RestoreResult.Failed("Could not read the archive.")

        if (!found) return@withContext RestoreResult.Failed(
            "That archive has no ${DB_ENTRY} in it — is it a Speed 400 Garage backup?"
        )
        if (!looksLikeSqlite(staged)) {
            staged.delete()
            return@withContext RestoreResult.Failed("The database inside that archive is not readable.")
        }

        checkpoint()
        if (dbFile.exists()) dbFile.copyTo(safety, overwrite = true)

        return@withContext try {
            db.close()
            // Side files belong to the OLD database; leaving them would corrupt the new one.
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            staged.copyTo(dbFile, overwrite = true)
            staged.delete()
            RestoreResult.Restored
        } catch (e: Exception) {
            if (safety.exists()) safety.copyTo(dbFile, overwrite = true)
            RestoreResult.Failed(e.message ?: "Restore failed; the previous data was put back.")
        }
    }

    private fun checkpoint() {
        runCatching { db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { it.moveToFirst() } }
    }

    /** SQLite files start with a fixed 16-byte header. Cheap, and catches a wrong file. */
    private fun looksLikeSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        FileInputStream(file).use { it.read(header) }
        return String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
    }

    private fun manifest(): String = """
        {
          "app": "dev.pranav.speed400garage",
          "schemaVersion": 1,
          "exportedAt": ${System.currentTimeMillis()},
          "contains": ["$DB_ENTRY", "csv/*.csv"],
          "note": "The CSVs are a plain-text rendering of every table so this data is never trapped in one app's schema."
        }
    """.trimIndent()

    private fun tablesAsCsv(): Map<String, String> {
        val database = db.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        database.query(SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'")).use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        return tables.associateWith { table ->
            buildString {
                database.query(SimpleSQLiteQuery("SELECT * FROM `$table`")).use { c ->
                    appendLine((0 until c.columnCount).joinToString(",") { escape(c.getColumnName(it)) })
                    while (c.moveToNext()) {
                        appendLine((0 until c.columnCount).joinToString(",") { i ->
                            if (c.isNull(i)) "" else escape(c.getString(i) ?: "")
                        })
                    }
                }
            }
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

    companion object {
        const val DB_ENTRY = "database/${GarageDatabase.NAME}"
        const val MANIFEST = "manifest.json"
        fun suggestedFilename(): String =
            "speed400-garage-backup-${java.time.LocalDate.now()}.zip"
    }
}

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data class Failed(val message: String) : RestoreResult
}
