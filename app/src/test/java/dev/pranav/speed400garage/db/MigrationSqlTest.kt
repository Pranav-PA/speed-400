package dev.pranav.speed400garage.db

import dev.pranav.speed400garage.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs the migrations for real, against an ordinary SQLite, and compares the result
 * against the schema Room exported.
 *
 * Room validates the schema every time it opens the database and throws if a migration
 * left it different from what the entities describe. That check runs on the device, at
 * launch, on a database holding ten years of records — which is the worst possible
 * place to discover a typo. This moves that discovery to the build.
 *
 * It caught a real one immediately: both FTS tables were being created with nullable
 * columns where Room expects NOT NULL, so the first launch after updating would have
 * crashed. Compiling proved nothing about that.
 */
class MigrationSqlTest {

    private val schemaDir = File("schemas/dev.pranav.speed400garage.data.db.GarageDatabase")

    @Test
    fun `a v1 database migrates all the way to v3 and matches the exported schema`() {
        val db = openMemoryDb()
        db.use {
            createSchema(it, version = 1)
            Migrations.V1_TO_V2.forEach { sql -> it.exec(sql) }
            Migrations.V2_TO_V3.forEach { sql -> it.exec(sql) }

            val expected = tablesOf(version = 3)
            val actual = it.tableSql()

            expected.forEach { (table, expectedSql) ->
                val actualSql = actual[table]
                assertTrue("migrations never created `$table`", actualSql != null)
                assertEquals(
                    "`$table` does not match the schema Room expects — the app would " +
                        "throw on open with a real database",
                    normalise(expectedSql),
                    normalise(actualSql!!),
                )
            }
        }
    }

    @Test
    fun `every statement is idempotent, so a half-applied migration can be re-run`() {
        // A migration interrupted by a crash or a kill leaves the database part-way.
        // Re-running has to be safe, or the recovery path is "reinstall and lose it all".
        val db = openMemoryDb()
        db.use {
            createSchema(it, version = 1)
            repeat(2) { _ ->
                Migrations.V1_TO_V2.forEach { sql -> it.exec(sql) }
                Migrations.V2_TO_V3.forEach { sql -> it.exec(sql) }
            }
            assertTrue(it.tableSql().containsKey("handbook_chunk_fts"))
        }
    }

    @Test
    fun `the event index is backfilled, not left empty`() {
        // An upgrade must not silently lose the searchability of everything logged
        // before it. FTS 'rebuild' is what populates the index from existing rows.
        val db = openMemoryDb()
        db.use {
            createSchema(it, version = 1)
            it.exec(
                "INSERT INTO event (id, bikeId, type, occurredAt, hasTimeOfDay, title, " +
                    "createdAt, updatedAt) VALUES ('e1', 'b1', 'service', 1, 0, " +
                    "'Front brake pads replaced', 1, 1)"
            )
            Migrations.V1_TO_V2.forEach { sql -> it.exec(sql) }

            val hits = it.createStatement()
                .executeQuery("SELECT COUNT(*) FROM event_fts WHERE event_fts MATCH 'brake'")
            hits.next()
            assertEquals("a pre-existing event must be findable after the upgrade", 1, hits.getInt(1))
        }
    }

    @Test
    fun `every migrated version has an exported schema to check against`() {
        // Room only exports a schema when the version is bumped. A migration with no
        // exported schema is one nothing can verify.
        (1..3).forEach { v ->
            assertTrue("schemas/$v.json is missing", File(schemaDir, "$v.json").exists())
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun openMemoryDb(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:")

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    /** Builds a database at [version] straight from Room's own exported statements. */
    private fun createSchema(connection: Connection, version: Int) {
        entitiesOf(version).forEach { (_, statements) ->
            statements.forEach { connection.exec(it) }
        }
    }

    /** Expected CREATE TABLE per table name, at [version]. */
    private fun tablesOf(version: Int): Map<String, String> =
        entitiesOf(version).mapValues { (_, statements) -> statements.first() }

    /**
     * Reads the exported schema properly rather than by regex.
     *
     * The table name lives in a sibling field of createSql, so a regex over createSql
     * alone loses the association — which is exactly how the first version of this
     * test managed to create an empty database and still look like it was working.
     *
     * @return table name to its CREATE statement followed by its index statements.
     */
    private fun entitiesOf(version: Int): Map<String, List<String>> {
        val root = Json.parseToJsonElement(File(schemaDir, "$version.json").readText())
        val entities = root.jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
        return entities.associate { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            fun resolve(sql: String) = sql.replace("\${TABLE_NAME}", table)
            val create = resolve(entity.getValue("createSql").jsonPrimitive.content)
            val indices = entity["indices"]?.jsonArray.orEmpty()
                .map { resolve(it.jsonObject.getValue("createSql").jsonPrimitive.content) }
            table to (listOf(create) + indices)
        }
    }

    private fun Connection.tableSql(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        createStatement().executeQuery(
            "SELECT name, sql FROM sqlite_master WHERE type='table' AND sql IS NOT NULL"
        ).use { rs -> while (rs.next()) out[rs.getString(1)] = rs.getString(2) }
        return out
    }

    /** SQLite stores what it was given; only whitespace and IF NOT EXISTS differ. */
    private fun normalise(sql: String): String = sql
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
