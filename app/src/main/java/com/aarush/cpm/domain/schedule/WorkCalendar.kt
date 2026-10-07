package com.aarush.cpm.domain.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Working-day calendar for schedule maths. Indian residential sites usually work six days
 * a week with Sunday off, which is the default; set [sundayOff] = false for a 7-day site.
 * Everything in the schedule engine is done in "working-day indexes" relative to an
 * [origin] date, then converted back to real dates here.
 */
class WorkCalendar(val origin: LocalDate, val sundayOff: Boolean = true) {

    fun isWorkingDay(date: LocalDate): Boolean = !(sundayOff && date.dayOfWeek == DayOfWeek.SUNDAY)

    fun nextWorkingDay(date: LocalDate): LocalDate {
        var d = date
        while (!isWorkingDay(d)) d = d.plusDays(1)
        return d
    }

    /** Working-day index of [date]: 0 = first working day on/after origin. A non-working
     *  date maps to the next working day's index. Dates before origin give negative indexes. */
    fun indexOf(date: LocalDate): Int {
        val start = nextWorkingDay(origin)
        val target = nextWorkingDay(date)
        if (target == start) return 0
        return if (target.isAfter(start)) countWorking(start, target) else -countWorking(target, start)
    }

    /** Date of working-day index [index]. */
    fun dateOf(index: Int): LocalDate {
        var d = nextWorkingDay(origin)
        if (index >= 0) {
            var remaining = index
            // Jump whole weeks first so long schedules stay cheap.
            val perWeek = if (sundayOff) 6 else 7
            d = d.plusWeeks((remaining / perWeek).toLong()); remaining %= perWeek
            d = nextWorkingDay(d)
            while (remaining > 0) { d = nextWorkingDay(d.plusDays(1)); remaining-- }
        } else {
            var remaining = -index
            while (remaining > 0) {
                d = d.minusDays(1)
                while (!isWorkingDay(d)) d = d.minusDays(1)
                remaining--
            }
        }
        return d
    }

    /** Working days in [from, to) — from inclusive, to exclusive. */
    private fun countWorking(from: LocalDate, to: LocalDate): Int {
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()
        if (!sundayOff) return totalDays
        var count = (totalDays / 7) * 6
        var d = from.plusDays(((totalDays / 7) * 7).toLong())
        while (d.isBefore(to)) { if (isWorkingDay(d)) count++; d = d.plusDays(1) }
        return count
    }

    companion object {
        fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

        /** Local noon of [date] — a safe epoch value for "a date" that survives any
         *  timezone offset when converted back with [toLocalDate]. */
        fun toEpochMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
            date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
