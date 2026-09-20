package de.christophlangner.commentator.core.time

import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zeitdarstellung über Plattform-APIs, damit Sprache und Format der
 * Systemeinstellung des Geräts folgen.
 */
object RelativeTime {

    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    /** Zum Beispiel „vor 5 Minuten“. */
    fun relative(instant: Instant, now: Instant = Instant.now()): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )

    /** Vollständiges Datum mit Uhrzeit in lokaler Zeitzone. */
    fun absolute(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        absoluteFormatter.format(instant.atZone(zone))
}
