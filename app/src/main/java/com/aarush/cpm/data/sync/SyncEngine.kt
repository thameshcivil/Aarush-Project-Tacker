package com.aarush.cpm.data.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64
import java.util.UUID

/** The few SQL calls the engine needs. On the phone this wraps Room's database; in tests a
 *  plain SQLite (JDBC) connection, so the whole sync logic is tested without a device. */
interface SyncSql {
    fun exec(sql: String, args: List<Any?> = emptyList())
    /** Rows as column → value (Long, Double, String or null), in column order. */
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>
    /** Runs an INSERT and returns the new row's id. */
    fun insert(sql: String, args: List<Any?>): Long
}

/**
 * One row as stored in the cloud. [data] holds every column (links as uids) and is null only
 * for a delete that was never uploaded alive. [deleted] marks a deleted row (tombstone).
 */
data class CloudRow(
    val table: String,
    val uid: String,
    val data: Map<String, Any?>?,
    val deleted: Boolean,
    val by: String = "",
    val updatedAt: Long = 0
)

/** A pending local change: which row, and the change counter used to clear it safely. */
data class OutKey(val table: String, val rk: String, val seq: Long)

data class ApplyResult(val applied: Int, val skipped: Int, val parked: Int)

/**
 * Cloud sync logic, independent of Firebase:
 *  - [ensureSchema] adds the bookkeeping tables and the change-tracking triggers. Every
 *    insert, update or delete in a synced table records the row in sync_outbox, unless the
 *    change is being applied from the cloud (sync_flag.applying = 1).
 *  - [collectOutgoing] turns pending local changes into [CloudRow]s (links → uids).
 *  - [applyIncoming] writes cloud rows into this phone (uids → local ids). A row whose
 *    parent isn't here yet is "parked" and retried on later passes.
 * Conflicts: the latest write to the cloud wins. A row changed on this phone and not yet
 * uploaded is not overwritten by an older cloud copy; the local change uploads next.
 */
class SyncEngine(private val sql: SyncSql, private val newUid: () -> String = { UUID.randomUUID().toString() }) {

    // ------------------------------------------------------------------ schema

    fun ensureSchema() {
        sql.exec("CREATE TABLE IF NOT EXISTS sync_flag (id INTEGER PRIMARY KEY NOT NULL, applying INTEGER NOT NULL)")
        sql.exec("INSERT OR IGNORE INTO sync_flag (id, applying) VALUES (1, 0)")
        sql.exec("UPDATE sync_flag SET applying = 0")
        sql.exec("CREATE TABLE IF NOT EXISTS sync_outbox (tbl TEXT NOT NULL, rk TEXT NOT NULL, seq INTEGER NOT NULL, PRIMARY KEY (tbl, rk))")
        sql.exec("CREATE TABLE IF NOT EXISTS sync_ids (tbl TEXT NOT NULL, localId INTEGER NOT NULL, uid TEXT NOT NULL, PRIMARY KEY (tbl, localId))")
        sql.exec("CREATE UNIQUE INDEX IF NOT EXISTS sync_ids_uid ON sync_ids (tbl, uid)")
        sql.exec("CREATE TABLE IF NOT EXISTS sync_cursor (tbl TEXT PRIMARY KEY NOT NULL, u INTEGER NOT NULL)")
        sql.exec("CREATE TABLE IF NOT EXISTS sync_parked (tbl TEXT NOT NULL, uid TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (tbl, uid))")
        SyncSchema.tables.forEach { t ->
            oldTriggerDrops(t).forEach(sql::exec)
            triggerSql(t).forEach(sql::exec)
        }
    }

    /** Row-key expression for a trigger, using NEW or OLD. */
    private fun rkExpr(t: SyncTable, row: String) = when (val k = t.key) {
        RowKey.Id -> "CAST($row.id AS TEXT)"
        is RowKey.Natural -> "$row.`${k.column}`"
        RowKey.ProjectMember -> "CAST($row.projectId AS TEXT) || '|' || $row.deviceId"
    }

    /**
     * Change-tracking triggers (version 2).
     *
     * Each records "this row changed" by deleting any older note for the row and inserting a
     * new one. Version 1 used INSERT OR REPLACE, but SQLite lets the statement that fired a
     * trigger override the trigger's conflict handling, and Room writes with UPDATE OR ABORT /
     * INSERT OR ABORT, so a second note for the same row aborted the save and crashed the app
     * (e.g. editing an expense). DELETE + INSERT never conflicts, whatever the outer statement.
     */
    internal fun triggerSql(t: SyncTable): List<String> {
        fun note(row: String) = "DELETE FROM sync_outbox WHERE tbl = '${t.name}' AND rk = ${rkExpr(t, row)}; " +
            "INSERT INTO sync_outbox (tbl, rk, seq) VALUES ('${t.name}', ${rkExpr(t, row)}, " +
            "(SELECT COALESCE(MAX(seq), 0) + 1 FROM sync_outbox));"
        val gate = "WHEN (SELECT applying FROM sync_flag WHERE id = 1) = 0"
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS sync2_i_${t.name} AFTER INSERT ON `${t.name}` $gate BEGIN ${note("NEW")} END",
            // An update can change a natural key, so both the old and the new key are recorded.
            "CREATE TRIGGER IF NOT EXISTS sync2_u_${t.name} AFTER UPDATE ON `${t.name}` $gate BEGIN ${note("OLD")} ${note("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS sync2_d_${t.name} AFTER DELETE ON `${t.name}` $gate BEGIN ${note("OLD")} END"
        )
    }

    /** Version 1 triggers (see [triggerSql]), removed from databases that still have them. */
    internal fun oldTriggerDrops(t: SyncTable): List<String> =
        listOf("i", "u", "d").map { "DROP TRIGGER IF EXISTS sync_${it}_${t.name}" }

    private val infoCache = mutableMapOf<String, List<Map<String, Any?>>>()
    private fun tableInfo(table: String) = infoCache.getOrPut(table) { sql.query("PRAGMA table_info(`$table`)") }
    private fun columns(table: String): List<String> = tableInfo(table).map { it["name"] as String }

    /** A row from a phone on an older app version may lack a column added since (e.g.
     *  projects.profitPercent). Required columns it doesn't send get 0 / 0.0 / "". */
    private fun fillMissingRequired(table: String, values: LinkedHashMap<String, Any?>) {
        tableInfo(table).forEach { c ->
            val name = c["name"] as String
            if (name in values || (c["pk"] as Long) != 0L || (c["notnull"] as Long) == 0L || c["dflt_value"] != null) return@forEach
            val type = (c["type"] as String).uppercase()
            values[name] = when { type.contains("INT") -> 0L; type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> 0.0; else -> "" }
        }
    }

    // ------------------------------------------------------------------ ids

    fun localIdFor(table: String, uid: String): Long? =
        sql.query("SELECT localId FROM sync_ids WHERE tbl = ? AND uid = ?", listOf(table, uid)).firstOrNull()?.get("localId") as Long?

    private fun existingUid(table: String, localId: Long): String? =
        sql.query("SELECT uid FROM sync_ids WHERE tbl = ? AND localId = ?", listOf(table, localId)).firstOrNull()?.get("uid") as String?

    /** The row's uid, created on first use (also for a parent referenced before it uploads). */
    fun uidFor(table: String, localId: Long): String = existingUid(table, localId) ?: newUid().also {
        sql.exec("INSERT INTO sync_ids (tbl, localId, uid) VALUES (?, ?, ?)", listOf(table, localId, it))
    }

    private fun map(table: String, localId: Long, uid: String) =
        sql.exec("INSERT OR REPLACE INTO sync_ids (tbl, localId, uid) VALUES (?, ?, ?)", listOf(table, localId, uid))

    // ------------------------------------------------------------------ outgoing

    fun pendingCount(): Long = sql.query("SELECT COUNT(*) AS n FROM sync_outbox").first()["n"] as Long

    /** Queues every row of every synced table (first upload from the Owner's phone). */
    fun enqueueAll() {
        SyncSchema.tables.forEach { t ->
            sql.exec("INSERT OR REPLACE INTO sync_outbox (tbl, rk, seq) SELECT '${t.name}', ${rkExpr(t, "`${t.name}`")}, 0 FROM `${t.name}`")
        }
    }

    /** Link value → cloud value: uid string, null stays null, 0 ("none") stays 0. */
    private fun linkOut(target: String, v: Any?): Any? = when (v) {
        null -> null
        is Long -> if (v == 0L) 0L else uidFor(target, v)
        else -> v
    }

    fun collectOutgoing(limit: Int = 400): List<Pair<OutKey, CloudRow?>> {
        val pending = sql.query("SELECT tbl, rk, seq FROM sync_outbox ORDER BY seq LIMIT ?", listOf(limit.toLong()))
        return pending.map { p ->
            val key = OutKey(p["tbl"] as String, p["rk"] as String, p["seq"] as Long)
            key to runCatching { outgoingRow(key) }.getOrNull()
        }
    }

    /** Null = nothing to send (e.g. created and deleted before it was ever uploaded). */
    private fun outgoingRow(k: OutKey): CloudRow? {
        val t = SyncSchema.byName[k.table] ?: return null
        val (row, uid) = when (val key = t.key) {
            RowKey.Id -> {
                val id = k.rk.toLong()
                val r = sql.query("SELECT * FROM `${t.name}` WHERE id = ?", listOf(id)).firstOrNull()
                r to (if (r != null) uidFor(t.name, id) else existingUid(t.name, id) ?: return null)
            }
            is RowKey.Natural -> {
                val r = sql.query("SELECT * FROM `${t.name}` WHERE `${key.column}` = ?", listOf(k.rk)).firstOrNull()
                val cloudKey = if (t.name == "app_settings") SyncSchema.settingKeyToCloud(k.rk) { uidFor("projects", it) } else k.rk
                r to cloudKey
            }
            RowKey.ProjectMember -> {
                val (pid, dev) = k.rk.split("|", limit = 2).let { it[0].toLong() to it[1] }
                val r = sql.query("SELECT * FROM project_members WHERE projectId = ? AND deviceId = ?", listOf(pid, dev)).firstOrNull()
                r to "${uidFor("projects", pid)}|$dev"
            }
        }
        if (row == null) return CloudRow(t.name, uid, null, deleted = true)
        val data = LinkedHashMap<String, Any?>()
        row.forEach { (c, v) ->
            if (t.key == RowKey.Id && c == "id") return@forEach
            data[c] = t.links[c]?.let { linkOut(it, v) } ?: v
        }
        if (t.name == "app_settings") data["key"] = uid
        return CloudRow(t.name, uid, data, deleted = false)
    }

    /** Clears changes that reached the cloud. A row changed again since it was collected has
     *  a newer seq, so it stays queued. */
    fun markSent(keys: List<OutKey>) = keys.forEach {
        sql.exec("DELETE FROM sync_outbox WHERE tbl = ? AND rk = ? AND seq = ?", listOf(it.table, it.rk, it.seq))
    }

    // ------------------------------------------------------------------ incoming

    fun cursor(table: String): Long = sql.query("SELECT u FROM sync_cursor WHERE tbl = ?", listOf(table)).firstOrNull()?.get("u") as Long? ?: 0L
    fun setCursor(table: String, u: Long) = sql.exec("INSERT OR REPLACE INTO sync_cursor (tbl, u) VALUES (?, ?)", listOf(table, u))

    private fun setApplying(on: Boolean) = sql.exec("UPDATE sync_flag SET applying = ? WHERE id = 1", listOf(if (on) 1L else 0L))

    private fun isPending(table: String, rk: String) =
        sql.query("SELECT 1 AS x FROM sync_outbox WHERE tbl = ? AND rk = ?", listOf(table, rk)).isNotEmpty()

    private class Unresolved : Exception()

    /** Cloud value → local link id. Throws [Unresolved] when the parent isn't here yet;
     *  [lenient] gives 0 instead (used only for rows that are deleted straight away). */
    private fun linkIn(target: String, v: Any?, lenient: Boolean): Any? = when (v) {
        null -> null
        is String -> localIdFor(target, v) ?: if (lenient) 0L else throw Unresolved()
        is Number -> v.toLong()
        else -> v
    }

    /** Applies rows from the cloud. Call inside one database transaction. */
    fun applyIncoming(rows: List<CloudRow>): ApplyResult {
        var applied = 0; var skipped = 0; var parked = 0
        val order = SyncSchema.tables.withIndex().associate { it.value.name to it.index }
        setApplying(true)
        try {
            rows.sortedBy { order[it.table] ?: Int.MAX_VALUE }.forEach {
                when (applyOne(it)) { Outcome.APPLIED -> applied++; Outcome.SKIPPED -> skipped++; Outcome.PARKED -> parked++ }
            }
            // Parked rows whose parents may have just arrived, parents first.
            SyncSchema.tables.forEach { t ->
                sql.query("SELECT uid, payload FROM sync_parked WHERE tbl = ?", listOf(t.name)).forEach { p ->
                    sql.exec("DELETE FROM sync_parked WHERE tbl = ? AND uid = ?", listOf(t.name, p["uid"]))
                    if (applyOne(decode(p["payload"] as String)) == Outcome.APPLIED) { applied++; parked-- }
                }
            }
        } finally {
            setApplying(false)
        }
        return ApplyResult(applied, skipped, parked.coerceAtLeast(0))
    }

    fun parkedCount(): Long = sql.query("SELECT COUNT(*) AS n FROM sync_parked").first()["n"] as Long

    private enum class Outcome { APPLIED, SKIPPED, PARKED }

    private fun park(r: CloudRow): Outcome {
        sql.exec("INSERT OR REPLACE INTO sync_parked (tbl, uid, payload) VALUES (?, ?, ?)", listOf(r.table, r.uid, encode(r)))
        return Outcome.PARKED
    }

    private fun applyOne(r: CloudRow): Outcome {
        val t = SyncSchema.byName[r.table] ?: return Outcome.SKIPPED
        return try {
            when (val key = t.key) {
                RowKey.Id -> applyIdRow(t, r)
                is RowKey.Natural -> applyNaturalRow(t, key.column, r)
                RowKey.ProjectMember -> applyMemberRow(t, r)
            }
        } catch (e: Unresolved) {
            park(r)
        } catch (e: Exception) {
            // e.g. a row from a newer app version with columns this phone doesn't have.
            park(r)
        }
    }

    /** Local values for a cloud row: known columns only, links translated. */
    private fun localValues(t: SyncTable, data: Map<String, Any?>, lenient: Boolean): LinkedHashMap<String, Any?> {
        val cols = columns(t.name).toSet()
        val out = LinkedHashMap<String, Any?>()
        data.forEach { (c, v) ->
            if (c !in cols || (t.key == RowKey.Id && c == "id")) return@forEach
            out[c] = t.links[c]?.let { linkIn(it, v, lenient) } ?: when (v) { is Boolean -> if (v) 1L else 0L; else -> v }
        }
        return out
    }

    private fun insertSql(table: String, cols: Collection<String>, orReplace: Boolean = false) =
        "INSERT ${if (orReplace) "OR REPLACE " else ""}INTO `$table` (${cols.joinToString { "`$it`" }}) VALUES (${cols.joinToString { "?" }})"

    private fun applyIdRow(t: SyncTable, r: CloudRow): Outcome {
        val local = localIdFor(t.name, r.uid)
        if (r.deleted) {
            if (local != null) {
                sql.exec("DELETE FROM `${t.name}` WHERE id = ?", listOf(local))
                sql.exec("DELETE FROM sync_outbox WHERE tbl = ? AND rk = ?", listOf(t.name, local.toString()))
                return Outcome.APPLIED
            }
            // Never seen here. Reserve an id for it (insert, then delete) so rows that still
            // point at it (e.g. expenses of a removed vendor) link up exactly as on the
            // phone that deleted it.
            val data = r.data ?: return Outcome.SKIPPED
            val values = localValues(t, data, lenient = true)
            fillMissingRequired(t.name, values)
            val id = sql.insert(insertSql(t.name, values.keys), values.values.toList())
            sql.exec("DELETE FROM `${t.name}` WHERE id = ?", listOf(id))
            map(t.name, id, r.uid)
            return Outcome.APPLIED
        }
        val data = r.data ?: return Outcome.SKIPPED
        if (local != null && isPending(t.name, local.toString())) return Outcome.SKIPPED
        val values = localValues(t, data, lenient = false)
        fillMissingRequired(t.name, values)
        if (local == null) {
            val id = sql.insert(insertSql(t.name, values.keys), values.values.toList())
            map(t.name, id, r.uid)
        } else if (sql.query("SELECT 1 AS x FROM `${t.name}` WHERE id = ?", listOf(local)).isNotEmpty()) {
            sql.exec("UPDATE `${t.name}` SET ${values.keys.joinToString { "`$it` = ?" }} WHERE id = ?", values.values.toList() + local)
        } else {
            val withId = LinkedHashMap<String, Any?>().apply { put("id", local); putAll(values) }
            sql.exec(insertSql(t.name, withId.keys), withId.values.toList())
        }
        return Outcome.APPLIED
    }

    private fun applyNaturalRow(t: SyncTable, column: String, r: CloudRow): Outcome {
        val localKey = if (t.name == "app_settings") {
            SyncSchema.settingKeyToLocal(r.uid) { localIdFor("projects", it) } ?: return if (r.deleted) Outcome.SKIPPED else park(r)
        } else r.uid
        if (isPending(t.name, localKey)) return Outcome.SKIPPED
        if (r.deleted) {
            sql.exec("DELETE FROM `${t.name}` WHERE `$column` = ?", listOf(localKey))
            return Outcome.APPLIED
        }
        val values = localValues(t, r.data ?: return Outcome.SKIPPED, lenient = false)
        values[column] = localKey
        fillMissingRequired(t.name, values)
        sql.exec(insertSql(t.name, values.keys, orReplace = true), values.values.toList())
        return Outcome.APPLIED
    }

    private fun applyMemberRow(t: SyncTable, r: CloudRow): Outcome {
        val (projUid, dev) = r.uid.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val pid = localIdFor("projects", projUid) ?: return if (r.deleted) Outcome.SKIPPED else park(r)
        if (isPending(t.name, "$pid|$dev")) return Outcome.SKIPPED
        if (r.deleted) {
            sql.exec("DELETE FROM project_members WHERE projectId = ? AND deviceId = ?", listOf(pid, dev))
            return Outcome.APPLIED
        }
        val values = localValues(t, r.data ?: return Outcome.SKIPPED, lenient = false)
        values["projectId"] = pid; values["deviceId"] = dev
        fillMissingRequired(t.name, values)
        sql.exec(insertSql(t.name, values.keys, orReplace = true), values.values.toList())
        return Outcome.APPLIED
    }

    // ------------------------------------------------------------------ reset

    /** Empties every synced table (and this phone's notifications) before taking the cloud
     *  copy. Login accounts are kept. Call inside a transaction. */
    fun wipeLocal() {
        setApplying(true)
        try {
            SyncSchema.tables.asReversed().forEach { sql.exec("DELETE FROM `${it.name}`") }
            sql.exec("DELETE FROM notifications")
            listOf("sync_ids", "sync_outbox", "sync_cursor", "sync_parked").forEach { sql.exec("DELETE FROM $it") }
        } finally {
            setApplying(false)
        }
    }

    // ------------------------------------------------------------------ parked encoding

    private fun encode(r: CloudRow): String {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use {
            it.writeObject(arrayListOf(r.table, r.uid, r.data?.let { d -> LinkedHashMap(d) }, r.deleted, r.by, r.updatedAt))
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(s: String): CloudRow {
        val l = ObjectInputStream(ByteArrayInputStream(Base64.getDecoder().decode(s))).use { it.readObject() as ArrayList<Any?> }
        return CloudRow(l[0] as String, l[1] as String, l[2] as Map<String, Any?>?, l[3] as Boolean, l[4] as String, l[5] as Long)
    }
}
