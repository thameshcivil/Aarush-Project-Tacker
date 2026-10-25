package com.aarush.cpm.data.sync

/*
 * What cloud sync copies, and how the tables point at each other (Phase 2).
 *
 * Every phone numbers its own rows 1, 2, 3…, so a row's local id means nothing on another
 * phone. In the cloud each row is known by a uid instead, and every column that holds
 * another row's id (a "link") is stored as that row's uid. Each phone translates uids back
 * to its own ids when it applies a row. The order below is parents first, so a row's links
 * normally resolve when it arrives.
 *
 * Not synced: users (each phone's own login accounts and password hashes) and notifications
 * (worked out on each phone).
 */

/** How a table's rows are identified. */
sealed class RowKey {
    /** Room autoGenerate Long `id`: mapped to a cloud uid. */
    object Id : RowKey()
    /** Natural text key, the same on every phone (e.g. a device id). */
    data class Natural(val column: String) : RowKey()
    /** project_members: (projectId, deviceId). The project part is translated like a link. */
    object ProjectMember : RowKey()
}

data class SyncTable(
    val name: String,
    val key: RowKey = RowKey.Id,
    /** Link columns → the table they point at. */
    val links: Map<String, String> = emptyMap()
)

object SyncSchema {
    private fun p(name: String, vararg more: Pair<String, String>) =
        SyncTable(name, RowKey.Id, mapOf("projectId" to "projects") + more)

    /** Parents before children. */
    val tables: List<SyncTable> = listOf(
        SyncTable("team_devices", RowKey.Natural("deviceId")),
        SyncTable("projects"),
        SyncTable("app_settings", RowKey.Natural("key")),
        SyncTable("project_members", RowKey.ProjectMember, mapOf("projectId" to "projects")),
        p("cost_allocations"),
        p("project_area_components"),
        p("vendors"),
        p("vendor_works", "vendorId" to "vendors"),
        p("boq_notations"),
        p("boq_items"),
        p("materials"),
        p("material_coefficients"),
        p("material_rates", "linkedVendorId" to "vendors"),
        p("labour_rates"),
        p("schedule_activities", "vendorId" to "vendors"),
        SyncTable("schedule_dependencies", RowKey.Id,
            mapOf("activityId" to "schedule_activities", "dependsOnActivityId" to "schedule_activities")),
        p("activity_progress_logs", "activityId" to "schedule_activities"),
        p("labour_entries", "scheduleActivityId" to "schedule_activities"),
        p("project_progress"),
        p("expenses", "vendorId" to "vendors"),
        p("material_purchases", "expenseId" to "expenses", "vendorId" to "vendors"),
        p("material_usages", "progressLogId" to "activity_progress_logs"),
        p("client_payments")
    )

    /** Tables deliberately kept on each phone only. */
    val localOnly = setOf("users", "notifications")

    val byName: Map<String, SyncTable> = tables.associateBy { it.name }

    // ------------------------------------------------------------- project-scoped settings

    /** app_settings keys that end in a local project id, e.g. "schedule.sundayOff.12". */
    private val projectScopedPrefixes = listOf("schedule.sundayOff.")

    /** Local key → cloud key ("schedule.sundayOff.12" → "schedule.sundayOff.@<projectUid>").
     *  [projectUid] maps a local project id to its uid. Other keys are unchanged. */
    fun settingKeyToCloud(key: String, projectUid: (Long) -> String): String {
        val prefix = projectScopedPrefixes.firstOrNull { key.startsWith(it) } ?: return key
        val id = key.removePrefix(prefix).toLongOrNull() ?: return key
        return prefix + "@" + projectUid(id)
    }

    /** Cloud key → local key; null when the project isn't on this phone yet. */
    fun settingKeyToLocal(key: String, projectId: (String) -> Long?): String? {
        val prefix = projectScopedPrefixes.firstOrNull { key.startsWith("$it@") } ?: return key
        val local = projectId(key.removePrefix("$prefix@")) ?: return null
        return prefix + local
    }
}
