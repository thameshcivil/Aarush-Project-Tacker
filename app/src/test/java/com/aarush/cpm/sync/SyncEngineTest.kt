package com.aarush.cpm.sync

import com.aarush.cpm.data.sync.CloudRow
import com.aarush.cpm.data.sync.RowKey
import com.aarush.cpm.data.sync.SyncEngine
import com.aarush.cpm.data.sync.SyncSchema
import com.aarush.cpm.data.sync.SyncSql
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/*
 * Cloud sync, tested with simulated phones: each phone is an in-memory SQLite database with
 * the app's real tables (from Room's generated schema on CI), and the cloud is a map.
 */

/** [SyncSql] over JDBC (sqlite-jdbc), the test stand-in for Room's database. */
class JdbcSql(private val conn: Connection) : SyncSql {
    private fun norm(v: Any?): Any? = when (v) { is Int -> v.toLong(); is Float -> v.toDouble(); is Boolean -> if (v) 1L else 0L; else -> v }

    override fun exec(sql: String, args: List<Any?>) {
        conn.prepareStatement(sql).use { st -> args.forEachIndexed { i, a -> st.setObject(i + 1, norm(a)) }; st.execute() }
    }

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> =
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, norm(a)) }
            st.executeQuery().use { rs ->
                val md = rs.metaData
                val out = ArrayList<Map<String, Any?>>()
                while (rs.next()) {
                    out += LinkedHashMap<String, Any?>().apply {
                        for (i in 1..md.columnCount) put(md.getColumnLabel(i), norm(rs.getObject(i)))
                    }
                }
                out
            }
        }

    override fun insert(sql: String, args: List<Any?>): Long {
        exec(sql, args)
        return query("SELECT last_insert_rowid() AS id").first()["id"] as Long
    }
}

/** The app's CREATE TABLE statements: Room's schema JSON when the build generated it (always on
 *  CI), otherwise a synthetic schema with the same keys and link columns (for local runs). */
object TestSchema {
    /** The newest generated schema (the current database version). */
    val roomJson: File? = listOf("schemas", "app/schemas")
        .map { File(it, "com.aarush.cpm.data.database.AppDatabase") }
        .firstOrNull { it.isDirectory }
        ?.listFiles { f -> f.name.endsWith(".json") }
        ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }

    fun createStatements(): List<String> {
        val json = roomJson?.readText()
        if (json != null) {
            return Regex("\"tableName\": \"([^\"]+)\"[\\s\\S]*?\"createSql\": \"([^\"]*)\"").findAll(json)
                .map { it.groupValues[2].replace("\${TABLE_NAME}", it.groupValues[1]).replace("\\\"", "\"") }.toList()
        }
        val tables = SyncSchema.tables.map { t ->
            when (val k = t.key) {
                RowKey.Id -> "CREATE TABLE `${t.name}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', " +
                    "`amount` REAL NOT NULL DEFAULT 0" + t.links.keys.joinToString("") { ", `$it` INTEGER" } + ")"
                is RowKey.Natural -> "CREATE TABLE `${t.name}` (`${k.column}` TEXT NOT NULL, `value` TEXT NOT NULL DEFAULT '', " +
                    "`name` TEXT NOT NULL DEFAULT '', `role` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`${k.column}`))"
                RowKey.ProjectMember -> "CREATE TABLE `project_members` (`projectId` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, " +
                    "`isLead` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`projectId`, `deviceId`))"
            }
        }
        return tables + listOf(
            "CREATE TABLE `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `email` TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL DEFAULT 0)"
        )
    }
}

/** A fake Firestore: last write wins per row; a delete keeps the row's last data. */
class FakeCloud {
    private var clock = 0L
    val rows = LinkedHashMap<Pair<String, String>, CloudRow>()

    fun push(r: CloudRow, by: String) {
        val key = r.table to r.uid
        val old = rows[key]
        rows[key] = r.copy(data = r.data ?: old?.data, by = by, updatedAt = ++clock)
    }

    fun since(table: String, u: Long) = rows.values.filter { it.table == table && it.updatedAt >= u }.sortedBy { it.updatedAt }
}

class Phone(val deviceId: String) {
    val conn: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    val sql = JdbcSql(conn)
    val engine = SyncEngine(sql)

    init {
        TestSchema.createStatements().forEach { sql.exec(it) }
        engine.ensureSchema()
    }

    /** Inserts a row, filling any NOT NULL column not given with 0 / 0.0 / "". */
    fun insert(table: String, values: Map<String, Any?>): Long {
        val info = sql.query("PRAGMA table_info(`$table`)")
        val all = LinkedHashMap<String, Any?>(values)
        info.forEach { c ->
            val name = c["name"] as String
            if (name !in all && (c["notnull"] as Long) == 1L && (c["pk"] as Long) == 0L && c["dflt_value"] == null) {
                val type = (c["type"] as String).uppercase()
                all[name] = when { type.contains("INT") -> 0L; type.contains("REAL") -> 0.0; else -> "" }
            }
        }
        return sql.insert("INSERT INTO `$table` (${all.keys.joinToString { "`$it`" }}) VALUES (${all.keys.joinToString { "?" }})", all.values.toList())
    }

    fun rows(table: String) = sql.query("SELECT * FROM `$table`")
    fun one(table: String, where: String, vararg args: Any?) = sql.query("SELECT * FROM `$table` WHERE $where", args.toList()).single()

    fun push(cloud: FakeCloud) {
        while (true) {
            val batch = engine.collectOutgoing(50)
            if (batch.isEmpty()) return
            batch.forEach { (_, row) -> row?.let { cloud.push(it, deviceId) } }
            engine.markSent(batch.map { it.first })
        }
    }

    fun pull(cloud: FakeCloud) {
        SyncSchema.tables.forEach { t ->
            val rows = cloud.since(t.name, engine.cursor(t.name))
            if (rows.isNotEmpty()) {
                engine.applyIncoming(rows)
                engine.setCursor(t.name, rows.maxOf { it.updatedAt })
            }
        }
    }

    fun sync(cloud: FakeCloud) { push(cloud); pull(cloud) }
}

class SyncEngineTest {

    private val realSchema get() = TestSchema.roomJson != null

    @Test fun schemaCoversEveryRoomTable() {
        if (System.getenv("CI") == "true") assertThat(TestSchema.roomJson).isNotNull()
        assumeTrue(realSchema)
        val phone = Phone("A")
        val tables = phone.sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").map { it["name"] as String }
            .filter { !it.startsWith("sqlite_") && !it.startsWith("sync_") && it != "room_master_table" }
        // Every app table is either synced or deliberately local.
        assertThat(tables.toSet()).isEqualTo(SyncSchema.tables.map { it.name }.toSet() + SyncSchema.localOnly)
        // Every link and key column really exists.
        SyncSchema.tables.forEach { t ->
            val cols = phone.sql.query("PRAGMA table_info(`${t.name}`)").map { it["name"] as String }
            t.links.keys.forEach { assertThat(cols).contains(it) }
            when (val k = t.key) {
                RowKey.Id -> assertThat(cols).contains("id")
                is RowKey.Natural -> assertThat(cols).contains(k.column)
                RowKey.ProjectMember -> assertThat(cols).containsAtLeast("projectId", "deviceId")
            }
        }
    }

    @Test fun triggersRecordLocalChangesButNotCloudOnes() {
        val p = Phone("A")
        val id = p.insert("projects", mapOf("name" to "Kumar"))
        p.sql.exec("UPDATE projects SET name = 'Kumar Residence' WHERE id = ?", listOf(id))
        assertThat(p.engine.pendingCount()).isEqualTo(1)
        p.engine.markSent(p.engine.collectOutgoing().map { it.first })
        assertThat(p.engine.pendingCount()).isEqualTo(0)
        // A row applied from the cloud is not queued again.
        p.engine.applyIncoming(listOf(CloudRow("projects", "u-1", mapOf("name" to "From cloud"), false)))
        assertThat(p.engine.pendingCount()).isEqualTo(0)
        // Changed again after it was collected: stays queued.
        val batch = p.engine.collectOutgoing()
        p.sql.exec("DELETE FROM projects WHERE id = ?", listOf(id))
        p.engine.markSent(batch.map { it.first })
        assertThat(p.engine.pendingCount()).isEqualTo(1)
    }

    @Test fun rowsAndLinksReachTheOtherPhoneWithItsOwnIds() {
        val cloud = FakeCloud()
        val a = Phone("A")
        val b = Phone("B")
        // Phone B's ids are already ahead, so the same rows get different ids there.
        repeat(3) { b.insert("projects", mapOf("name" to "old $it")) }
        b.sql.exec("DELETE FROM projects"); b.engine.markSent(b.engine.collectOutgoing().map { it.first })

        val proj = a.insert("projects", mapOf("name" to "Kumar"))
        val vendor = a.insert("vendors", mapOf("projectId" to proj, "name" to "Murugan"))
        val exp = a.insert("expenses", mapOf("projectId" to proj, "vendorId" to vendor, "itemOrMaterialName" to "Cement").filterKeys { realSchema || it != "itemOrMaterialName" })
        a.insert("material_purchases", mapOf("projectId" to proj, "expenseId" to exp, "vendorId" to vendor))
        a.sync(cloud); b.sync(cloud)

        val bProj = b.one("projects", "name = ?", "Kumar")["id"] as Long
        assertThat(bProj).isNotEqualTo(proj)
        val bVendor = b.one("vendors", "projectId = ?", bProj)
        val bExp = b.one("expenses", "projectId = ?", bProj)
        assertThat(bExp["vendorId"]).isEqualTo(bVendor["id"])
        assertThat(b.one("material_purchases", "projectId = ?", bProj)["expenseId"]).isEqualTo(bExp["id"])
        assertThat(b.engine.pendingCount()).isEqualTo(0)

        // Edits on B come back to A on A's ids.
        b.sql.exec("UPDATE vendors SET name = 'Sri Murugan' WHERE id = ?", listOf(bVendor["id"]))
        b.insert("expenses", mapOf("projectId" to bProj, "vendorId" to bVendor["id"]))
        b.sync(cloud); a.sync(cloud)
        assertThat(a.one("vendors", "id = ?", vendor)["name"]).isEqualTo("Sri Murugan")
        assertThat(a.rows("expenses").map { it["vendorId"] }).containsExactly(vendor, vendor)
        assertThat(a.rows("projects")).hasSize(1)
    }

    @Test fun deletesPropagateAndRemovedParentsStillLinkForNewPhones() {
        val cloud = FakeCloud()
        val a = Phone("A")
        val proj = a.insert("projects", mapOf("name" to "Kumar"))
        val vendor = a.insert("vendors", mapOf("projectId" to proj, "name" to "Old vendor"))
        a.insert("expenses", mapOf("projectId" to proj, "vendorId" to vendor))
        a.sync(cloud)
        val b = Phone("B"); b.sync(cloud)
        // Vendor removed on A; its payments are kept (as in the app).
        a.sql.exec("DELETE FROM vendors WHERE id = ?", listOf(vendor))
        a.sync(cloud); b.sync(cloud)
        assertThat(b.rows("vendors")).isEmpty()
        assertThat(b.rows("expenses")).hasSize(1)

        // A phone joining later still gets the expense, linked to a (gone) vendor id.
        val c = Phone("C"); c.sync(cloud)
        assertThat(c.rows("vendors")).isEmpty()
        val cExp = c.rows("expenses").single()
        assertThat(cExp["vendorId"] as Long).isGreaterThan(0L)
        assertThat(c.engine.parkedCount()).isEqualTo(0)
    }

    @Test fun childBeforeParentIsParkedThenApplied() {
        val p = Phone("B")
        p.engine.applyIncoming(listOf(CloudRow("vendors", "v-1", mapOf("projectId" to "p-1", "name" to "V"), false)))
        assertThat(p.rows("vendors")).isEmpty()
        assertThat(p.engine.parkedCount()).isEqualTo(1)
        p.engine.applyIncoming(listOf(CloudRow("projects", "p-1", mapOf("name" to "P"), false)))
        assertThat(p.engine.parkedCount()).isEqualTo(0)
        val pid = p.rows("projects").single()["id"]
        assertThat(p.rows("vendors").single()["projectId"]).isEqualTo(pid)
    }

    @Test fun unsentLocalEditIsNotOverwrittenByTheCloud() {
        val cloud = FakeCloud()
        val a = Phone("A"); val b = Phone("B")
        val proj = a.insert("projects", mapOf("name" to "One"))
        a.sync(cloud); b.sync(cloud)
        val bProj = b.rows("projects").single()["id"]
        b.sql.exec("UPDATE projects SET name = 'B edit' WHERE id = ?", listOf(bProj))   // not pushed yet
        a.sql.exec("UPDATE projects SET name = 'A edit' WHERE id = ?", listOf(proj))
        a.sync(cloud)
        b.pull(cloud)
        assertThat(b.rows("projects").single()["name"]).isEqualTo("B edit")
        b.push(cloud); a.sync(cloud)
        assertThat(a.rows("projects").single()["name"]).isEqualTo("B edit")
    }

    @Test fun projectSettingKeysMembersAndDevicesTranslate() {
        val cloud = FakeCloud()
        val a = Phone("A"); val b = Phone("B")
        repeat(2) { b.insert("projects", mapOf("name" to "x")) }
        b.sql.exec("DELETE FROM projects"); b.engine.markSent(b.engine.collectOutgoing().map { it.first })

        val proj = a.insert("projects", mapOf("name" to "Kumar"))
        a.insert("app_settings", mapOf("key" to "schedule.sundayOff.$proj", "value" to "false"))
        a.insert("app_settings", mapOf("key" to "owner_pin_hash", "value" to "h"))
        a.insert("team_devices", mapOf("deviceId" to "dev-ravi", "name" to "Ravi", "role" to "COORDINATOR"))
        a.insert("project_members", mapOf("projectId" to proj, "deviceId" to "dev-ravi", "isLead" to 1L))
        a.sync(cloud); b.sync(cloud)

        val bProj = b.rows("projects").single()["id"] as Long
        assertThat(bProj).isNotEqualTo(proj)
        assertThat(b.rows("app_settings").map { it["key"] }).containsExactly("schedule.sundayOff.$bProj", "owner_pin_hash")
        assertThat(cloud.rows.keys.map { it.second }.none { it == "schedule.sundayOff.$proj" }).isTrue()
        val m = b.rows("project_members").single()
        assertThat(m["projectId"]).isEqualTo(bProj); assertThat(m["deviceId"]).isEqualTo("dev-ravi"); assertThat(m["isLead"]).isEqualTo(1L)
        assertThat(b.rows("team_devices").single()["name"]).isEqualTo("Ravi")

        // Removing the member on B removes it on A.
        b.sql.exec("DELETE FROM project_members")
        b.sync(cloud); a.sync(cloud)
        assertThat(a.rows("project_members")).isEmpty()
    }

    @Test fun firstUploadSendsEverythingAndWipeEmptiesSyncedTables() {
        val cloud = FakeCloud()
        val a = Phone("A")
        val proj = a.insert("projects", mapOf("name" to "Kumar"))
        a.insert("vendors", mapOf("projectId" to proj))
        a.engine.markSent(a.engine.collectOutgoing().map { it.first })   // as if sync was off
        a.engine.enqueueAll()
        a.push(cloud)
        assertThat(cloud.rows.keys.map { it.first }).containsAtLeast("projects", "vendors")

        val b = Phone("B")
        b.insert("projects", mapOf("name" to "Junk on new phone"))
        b.insert("users", mapOf("email" to "me@x"))
        b.sql.exec("BEGIN"); b.engine.wipeLocal(); b.sql.exec("COMMIT")
        assertThat(b.rows("projects")).isEmpty()
        assertThat(b.rows("users")).hasSize(1)
        assertThat(b.engine.pendingCount()).isEqualTo(0)
        b.sync(cloud)
        assertThat(b.rows("projects").single()["name"]).isEqualTo("Kumar")
    }
}
