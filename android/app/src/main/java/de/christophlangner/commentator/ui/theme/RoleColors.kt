package de.christophlangner.commentator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.christophlangner.commentator.domain.model.RoleAccent

/**
 * Die Farben eines Rollentons.
 *
 * [container] traegt die ganze Karte, [badge] die kleine Marke darauf,
 * [onRole] den Text auf beiden.
 */
data class RoleColors(
    val container: Color,
    val badge: Color,
    val onRole: Color,
)

/**
 * Die Toene, mit denen Beitraege des Teams ausgezeichnet werden.
 *
 * Alle bewusst blass: Eine Karte ist damit erkennbar anders als die daneben,
 * ohne nach Warnung auszusehen. Der Vorgaenger nahm dafuer `errorContainer` -
 * dasselbe Rot, das die App fuer Spam und abgelaufene Sitzungen verwendet.
 * Ein Beitrag der eigenen Redaktion sah damit aus wie ein Problem.
 *
 * Die Werte sind als HSL gerechnet, je Ton derselbe Farbwinkel: hell mit
 * L 94/85/26, dunkel mit L 18/30/84. Dadurch haben alle Toene dasselbe
 * Gewicht, und Text auf Karte wie auf Marke erreicht in beiden
 * Erscheinungsbildern mindestens das 7-fache Kontrastverhaeltnis.
 */
private data class Tonpaar(
    val containerLight: Long,
    val badgeLight: Long,
    val onLight: Long,
    val containerDark: Long,
    val badgeDark: Long,
    val onDark: Long,
)

private val TOENE: Map<RoleAccent, Tonpaar> = mapOf(
    RoleAccent.SCHIEFER to Tonpaar(
        0xFFEEF1F4, 0xFFD1D7DE, 0xFF334051, 0xFF272C32, 0xFF434B56, 0xFFD0D5DD,
    ),
    RoleAccent.INDIGO to Tonpaar(
        0xFFEBECF7, 0xFFCACDE5, 0xFF242A60, 0xFF222437, 0xFF3A3E5F, 0xFFC9CCE3,
    ),
    RoleAccent.TEAL to Tonpaar(
        0xFFEBF5F7, 0xFFCAE1E5, 0xFF245860, 0xFF223437, 0xFF3A5A5F, 0xFFC9E0E3,
    ),
    RoleAccent.MOOS to Tonpaar(
        0xFFEBF7EF, 0xFFCAE5D3, 0xFF246038, 0xFF223729, 0xFF3A5F46, 0xFFC9E3D2,
    ),
    RoleAccent.OCKER to Tonpaar(
        0xFFF7F4EB, 0xFFE5DECA, 0xFF605024, 0xFF373222, 0xFF5F553A, 0xFFE3DCC9,
    ),
    RoleAccent.TERRAKOTTA to Tonpaar(
        0xFFF7EEEB, 0xFFE5D1CA, 0xFF603424, 0xFF372822, 0xFF5F443A, 0xFFE3D0C9,
    ),
    RoleAccent.PFLAUME to Tonpaar(
        0xFFF6ECF6, 0xFFE3CBE4, 0xFF5B275D, 0xFF362336, 0xFF5C3C5D, 0xFFE1CAE2,
    ),
)

/**
 * Die Farben eines Tons, passend zum gerade geltenden Erscheinungsbild.
 *
 * Gefragt wird die Helligkeit der Oberflaeche und nicht die Systemeinstellung:
 * Mit Dynamic Color bestimmt der Benutzer das Schema, und eine Vorschau darf
 * auch dann stimmen, wenn sie in einem hellen Ausschnitt einer dunklen App
 * steht.
 */
@Composable
@ReadOnlyComposable
fun roleColors(accent: RoleAccent): RoleColors {
    val ton = TOENE[accent] ?: TOENE.getValue(RoleAccent.DEFAULT)
    val dunkel = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dunkel) {
        RoleColors(
            container = Color(ton.containerDark),
            badge = Color(ton.badgeDark),
            onRole = Color(ton.onDark),
        )
    } else {
        RoleColors(
            container = Color(ton.containerLight),
            badge = Color(ton.badgeLight),
            onRole = Color(ton.onLight),
        )
    }
}
