package com.aarush.cpm.data

import com.aarush.cpm.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the v8 → v9 migration SQL against the schema Room generates from the entities
 * (app/schemas/.../9.json, written by the Room compiler during the build). Room validates the
 * migrated tables against exactly this on first open; a mismatch would crash the app for
 * every existing user. Skipped if the schema file has not been generated in this build.
 */
class MigrationSchemaTest {
    private val schemaFile = listOf("schemas", "app/schemas")
        .map { File(it, "com.aarush.cpm.data.database.AppDatabase/${AppDatabase.VERSION}.json") }
        .firstOrNull { it.exists() }

    /** On CI the schema must exist, so the check can never be silently skipped there. */
    private fun requireSchema() {
        if (System.getenv("CI") == "true") assertTrue("Room schema ${AppDatabase.VERSION}.json not found (cwd ${File(".").absolutePath})", schemaFile != null)
        assumeTrue("Room schema not generated", schemaFile != null)
    }

    private fun norm(sql: String, table: String = "vendor_works") = sql.replace("`", "").replace("\${TABLE_NAME}", table)
        .replace(Regex("\\s+"), " ").replace("( ", "(").replace(" )", ")").trim()

    /** The "createSql" of one entity in the Room schema JSON. */
    private fun createSql(json: String, table: String): String {
        val start = json.indexOf("\"tableName\": \"$table\"")
        assertTrue("table $table missing from Room schema", start >= 0)
        val m = Regex("\"createSql\": \"([^\"]*)\"").find(json, start)!!
        return m.groupValues[1]
    }

    @Test fun vendorWorksTableMatchesRoom() {
        requireSchema()
        val json = schemaFile!!.readText()
        assertEquals(norm(createSql(json, "vendor_works")), norm(AppDatabase.SQL_8_9_CREATE_VENDOR_WORKS))
    }

    @Test fun areaComponentColumnsMatchRoom() {
        requireSchema()
        val expected = norm(createSql(schemaFile!!.readText(), "project_area_components"))
        // Room's table must end with the two new columns exactly as the migration adds them
        // (DB default 0 is fine: the entity declares no default, so Room does not compare it).
        assertTrue(expected, expected.contains("isCompoundWall INTEGER NOT NULL, isLumpSum INTEGER NOT NULL"))
        AppDatabase.SQL_8_9_ADD_AREA_COLUMNS.forEach { sql ->
            val col = Regex("ADD COLUMN (\\w+) (\\w+ NOT NULL)").find(sql)!!.groupValues
            assertTrue("${col[1]} ${col[2]}", expected.contains("${col[1]} ${col[2]}"))
        }
    }

    // ---------------------------------------------------------------- v9 → v10 (team roles)

    @Test fun teamDevicesTableMatchesRoom() {
        requireSchema()
        val json = schemaFile!!.readText()
        assertEquals(norm(createSql(json, "team_devices"), "team_devices"), norm(AppDatabase.SQL_9_10_CREATE_TEAM_DEVICES, "team_devices"))
    }

    @Test fun projectMembersTableMatchesRoom() {
        requireSchema()
        val json = schemaFile!!.readText()
        assertEquals(norm(createSql(json, "project_members"), "project_members"), norm(AppDatabase.SQL_9_10_CREATE_PROJECT_MEMBERS, "project_members"))
    }

    @Test fun enteredByColumnsMatchRoom() {
        requireSchema()
        val json = schemaFile!!.readText()
        AppDatabase.SQL_9_10_ADD_ENTERED_BY.forEach { sql ->
            val m = Regex("ALTER TABLE (\\w+) ADD COLUMN (\\w+ TEXT)").find(sql)!!.groupValues
            val table = norm(createSql(json, m[1]), m[1])
            // Nullable in Room (no NOT NULL), exactly like the migration adds it.
            assertTrue(table, table.contains("${m[2]},") || table.contains("${m[2]})"))
            assertTrue(table, !table.contains("${m[2]} NOT NULL"))
        }
    }

    // ---------------------------------------------------------------- v10 → v11 (profit %)

    @Test fun profitColumnMatchesRoom() {
        requireSchema()
        val table = norm(createSql(schemaFile!!.readText(), "projects"), "projects")
        val col = Regex("ADD COLUMN (\\w+) (\\w+ NOT NULL)").find(AppDatabase.SQL_10_11_ADD_PROFIT)!!.groupValues
        assertTrue(table, table.contains("${col[1]} ${col[2]}"))
    }
}
