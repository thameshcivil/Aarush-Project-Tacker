package com.aarush.cpm.ui.common

import com.aarush.cpm.domain.schedule.WorkCalendar
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inrFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun formatCurrency(amount: Double): String = inrFormat.format(amount)

/** ₹12.4 L / ₹1.25 Cr style short amounts for tight stat tiles. */
fun formatCurrencyShort(amount: Double): String {
    val abs = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        abs >= 1_00_00_000 -> "$sign₹%.2f Cr".format(abs / 1_00_00_000)
        abs >= 1_00_000 -> "$sign₹%.2f L".format(abs / 1_00_000)
        abs >= 1_000 -> "$sign₹%.1f K".format(abs / 1_000)
        else -> "$sign₹%.0f".format(abs)
    }
}

fun formatQuantity(qty: Double, unit: String): String {
    val trimmed = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)
    return if (unit.isBlank()) trimmed else "$trimmed $unit"
}

fun formatPercent(value: Double): String = "%.1f%%".format(value)

private val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
private val localDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
private val shortDateFormat = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

fun formatDate(epochMillis: Long): String = dateFormat.format(java.util.Date(epochMillis))

private val dateTimeFormat = java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", java.util.Locale.getDefault())
/** "26 Oct 2026, 9:14 AM": when a record was entered. */
fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(java.util.Date(epochMillis))
fun formatDate(date: LocalDate): String = date.format(localDateFormat)
fun formatShortDate(date: LocalDate): String = date.format(shortDateFormat)

/** The person's local calendar day for an epoch timestamp — use for grouping by day
 *  (dividing millis by 86,400,000 groups by UTC day, which splits IST mornings wrongly). */
fun localDayOf(epochMillis: Long): LocalDate = WorkCalendar.toLocalDate(epochMillis)

/**
 * Material3's DatePicker works in UTC-midnight millis. These convert to/from the app's
 * stored "local noon" date values so a picked date never shifts by a day in any timezone.
 */
fun pickerMillisFromStored(epochMillis: Long): Long =
    localDayOf(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun storedMillisFromPicker(pickerMillis: Long): Long =
    WorkCalendar.toEpochMillis(Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate(), ZoneId.systemDefault())

fun pickerMillisFromDate(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun dateFromPickerMillis(pickerMillis: Long): LocalDate = Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** Shown where a figure has no value yet (no schedule, no index). A plain hyphen: the app's
 *  copy rule avoids em-dashes. */
const val NO_VALUE = "-"
