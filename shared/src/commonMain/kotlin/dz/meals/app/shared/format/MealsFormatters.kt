package dz.meals.app.shared.format

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val ArabicWeekdays = listOf(
    "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"
)
private val ArabicMonths = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

fun Int.formatDzd(): String {
    val value = toLong()
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString("\u202F").reversed()
    return (if (value < 0L) "-" else "") + grouped + " دج"
}

fun Double.prettyQuantity(): String {
    if (!isFinite()) return "—"
    val roundedTenths = (this * 10.0).roundToLong()
    if (roundedTenths % 10L == 0L) return (roundedTenths / 10L).toString()
    val whole = roundedTenths / 10L
    val fraction = abs(roundedTenths % 10L)
    return "$whole.$fraction"
}

fun epochMillisToLocalDate(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

fun localDateToEpochMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

fun formatDateDmy(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"

fun formatArabicDate(date: LocalDate): String {
    val weekday = ArabicWeekdays[date.dayOfWeek.ordinal]
    val month = ArabicMonths[date.month.ordinal]
    return "$weekday ${date.day} $month"
}

fun formatArabicWeekRange(start: LocalDate, end: LocalDate): String {
    val startLabel = "${start.day} ${ArabicMonths[start.month.ordinal]}"
    val endLabel = "${end.day} ${ArabicMonths[end.month.ordinal]} ${end.year}"
    return "$startLabel - $endLabel"
}
