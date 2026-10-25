package com.aarush.cpm.data.sync

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/** [SyncSql] over Room's underlying database connection. */
class RoomSyncSql(private val db: SupportSQLiteDatabase) : SyncSql {

    private fun bind(args: List<Any?>): Array<Any?> = args.map { v ->
        when (v) {
            is Boolean -> if (v) 1L else 0L
            is Int -> v.toLong()
            is Float -> v.toDouble()
            else -> v
        }
    }.toTypedArray()

    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            // PRAGMA / DDL with no arguments; queries that return rows go through query().
            db.execSQL(sql)
        } else {
            db.execSQL(sql, bind(args))
        }
    }

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> =
        db.query(SimpleSQLiteQuery(sql, bind(args))).use { c ->
            val out = ArrayList<Map<String, Any?>>()
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                }
                out += row
            }
            out
        }

    override fun insert(sql: String, args: List<Any?>): Long {
        db.execSQL(sql, bind(args))
        return db.query("SELECT last_insert_rowid()").use { c -> c.moveToFirst(); c.getLong(0) }
    }
}
