package de.christophlangner.commentator.domain.model

/**
 * Wählbare Symbolvarianten.
 *
 * [aliasName] ist der Name des `activity-alias` im Manifest. Android kennt
 * keinen Weg, das Startsymbol zur Laufzeit direkt zu ändern; stattdessen gibt
 * es je Variante einen Alias, von dem immer genau einer eingeschaltet ist.
 */
enum class AppIcon(val aliasName: String) {
    /** Android-Grün. Im Manifest eingeschaltet, also der Ausgangszustand. */
    Green(".LauncherGreen"),

    /** WordPress-Blau. */
    Blue(".LauncherBlue"),
    ;

    companion object {
        val DEFAULT = Green
    }
}
