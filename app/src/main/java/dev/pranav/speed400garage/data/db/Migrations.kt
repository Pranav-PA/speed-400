package dev.pranav.speed400garage.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, as plain SQL.
 *
 * The statements live here as strings rather than inline in [Migration] bodies for one
 * reason: strings can be run against an ordinary SQLite on the JVM, so
 * `MigrationSqlTest` can actually execute a v1 database forward and compare the result
 * against Room's exported schema. Inline in a Migration they could only be read.
 *
 * That is not hypothetical tidiness. The first version of both of these declared their
 * FTS columns nullable where Room expects NOT NULL. Room validates the schema when it
 * opens the database, so the app would have crashed on launch for anyone updating with
 * real data in it — a bug no amount of "the build succeeded" would ever have caught,
 * on the one database §3 P6 says has to outlive the device.
 *
 * Rule for anything added here: the SQL must match `app/schemas/<version>.json`
 * exactly, and the test enforces it.
 */
object Migrations {

    /** Adds the full-text index over events, and backfills it from existing rows. */
    val V1_TO_V2: List<String> = listOf(
        "CREATE VIRTUAL TABLE IF NOT EXISTS `event_fts` USING FTS4(" +
            "`title` TEXT NOT NULL, `notes` TEXT, content=`event`)",
        // Rebuild populates the index from the content table, so an upgrade keeps its
        // history searchable instead of starting empty.
        "INSERT INTO `event_fts`(`event_fts`) VALUES('rebuild')",
    )

    /** Adds the handbook corpus and its full-text index. */
    val V2_TO_V3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `handbook_chunk` (" +
            "`id` TEXT NOT NULL, `page` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, " +
            "`text` TEXT NOT NULL, `section` TEXT, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_handbook_chunk_page` ON `handbook_chunk` (`page`)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `handbook_chunk_fts` USING FTS4(" +
            "`text` TEXT NOT NULL, `section` TEXT, content=`handbook_chunk`)",
    )

    val MIGRATION_1_2 = migration(1, 2, V1_TO_V2)
    val MIGRATION_2_3 = migration(2, 3, V2_TO_V3)

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

    private fun migration(from: Int, to: Int, statements: List<String>) =
        object : Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) {
                statements.forEach(db::execSQL)
            }
        }
}
