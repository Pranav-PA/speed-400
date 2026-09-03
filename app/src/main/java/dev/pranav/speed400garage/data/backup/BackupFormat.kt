package dev.pranav.speed400garage.data.backup

/**
 * The parts of the backup format that are pure enough to test.
 *
 * Split out of [BackupManager] for the same reason the migration SQL was split out of
 * the database class: file surgery on the only copy of a ten-year record deserves more
 * evidence than "it compiled". Escaping and file sniffing are ordinary text logic and
 * need no Android device to be verified.
 */
object BackupFormat {

    const val DB_ENTRY = "database/speed400_garage.db"
    const val MANIFEST = "manifest.json"
    const val CSV_PREFIX = "csv/"

    /** Every SQLite file begins with this, followed by a NUL. */
    private const val SQLITE_MAGIC = "SQLite format 3"

    /**
     * Cheap check that a file really is a database before it is allowed to replace the
     * live one. Catches the picked-the-wrong-file case, which is the likeliest way a
     * restore goes wrong.
     */
    fun looksLikeSqlite(header: ByteArray): Boolean {
        if (header.size < SQLITE_MAGIC.length) return false
        return String(header, Charsets.US_ASCII).startsWith(SQLITE_MAGIC)
    }

    /**
     * RFC 4180 escaping.
     *
     * This matters more than it looks. Notes are free text: "rattle, left side" has a
     * comma in it, a workshop name can carry a quote, and a multi-line note has
     * newlines. Any of those left unescaped shifts every column after it — and the CSV
     * is the copy that exists so this data is never trapped in one app's schema. A
     * silently misaligned export is worse than no export, because you find out only
     * when you finally need it.
     */
    fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** A null cell is written as empty, which round-trips back to null rather than "null". */
    fun csvRow(values: List<String?>): String =
        values.joinToString(",") { if (it == null) "" else escapeCsv(it) }

    fun csvEntryName(table: String) = "$CSV_PREFIX$table.csv"

    fun manifest(exportedAtMillis: Long, schemaVersion: Int): String = """
        {
          "app": "dev.pranav.speed400garage",
          "schemaVersion": $schemaVersion,
          "exportedAt": $exportedAtMillis,
          "contains": ["$DB_ENTRY", "$CSV_PREFIX*.csv"],
          "note": "The CSVs are a plain-text rendering of every table so this data is never trapped in one app's schema."
        }
    """.trimIndent()

    fun suggestedFilename(today: String) = "speed400-garage-backup-$today.zip"
}
