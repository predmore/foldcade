package app.foldcade.language

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * How dates and times read on this device: the locale's month and day order and
 * the system's 12/24-hour setting. The host builds it from Android's best
 * patterns ("MMMd", "yMMMd", "hm" or "Hm"); the defaults are en-US on a 12-hour
 * clock, which is what Android gives there.
 */
data class DateStyle(
    val locale: Locale = Locale.US,
    val monthDayPattern: String = "MMM d",
    val monthDayYearPattern: String = "MMM d, y",
    val is24Hour: Boolean = false,
    val timePattern: String = if (is24Hour) "HH:mm" else "h:mm a",
) {
    // An Android pattern java.time can't read falls back to the en-US shape in this locale.
    private val monthDayFormat = formatter(monthDayPattern, "MMM d")
    private val monthDayYearFormat = formatter(monthDayYearPattern, "MMM d, y")
    private val timeFormat = formatter(timePattern, if (is24Hour) "HH:mm" else "h:mm a")

    /** "Oct 21" or "21 Oct". */
    fun monthDay(date: TemporalAccessor): String = monthDayFormat.format(date)

    /** "Oct 21, 2026" or "21 Oct 2026". */
    fun monthDayYear(date: TemporalAccessor): String = monthDayYearFormat.format(date)

    /** "8:30 AM" or "08:30". */
    fun time(time: TemporalAccessor): String = timeFormat.format(time)

    private fun formatter(pattern: String, fallback: String): DateTimeFormatter =
        runCatching { DateTimeFormatter.ofPattern(pattern, locale) }
            .getOrElse { DateTimeFormatter.ofPattern(fallback, locale) }
}
