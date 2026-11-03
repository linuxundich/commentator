package de.christophlangner.commentator.core.time

import android.content.res.Resources
import android.text.format.DateUtils
import de.christophlangner.commentator.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zeitdarstellung über Plattform-APIs, damit Sprache und Format der
 * Systemeinstellung des Geräts folgen.
 */
object RelativeTime {

    /** So weit darf ein Zeitpunkt in der Zukunft liegen und trotzdem „gerade eben“ sein. */
    private const val CLOCK_SKEW_TOLERANCE_MILLIS = 5 * DateUtils.MINUTE_IN_MILLIS

    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    /**
     * Zum Beispiel „vor 5 Minuten“.
     *
     * Unterhalb einer Minute liefert die Plattform „vor 0 Min.“ - für eine
     * gerade veröffentlichte Antwort eine unsinnige Angabe. Dieser Fall wird
     * deshalb eigens behandelt. Dazu zählen auch Zeitpunkte knapp in der
     * Zukunft: Geht die Uhr des Geräts ein paar Sekunden hinter der des
     * Servers, wäre eine eben veröffentlichte Antwort sonst „in 0 Min.“.
     */
    fun relative(
        instant: Instant,
        resources: Resources,
        now: Instant = Instant.now(),
    ): CharSequence {
        val age = now.toEpochMilli() - instant.toEpochMilli()
        if (age in -CLOCK_SKEW_TOLERANCE_MILLIS until DateUtils.MINUTE_IN_MILLIS) {
            return resources.getString(R.string.time_just_now)
        }

        return DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
    }

    /** Vollständiges Datum mit Uhrzeit in lokaler Zeitzone. */
    fun absolute(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        absoluteFormatter.format(instant.atZone(zone))
}
