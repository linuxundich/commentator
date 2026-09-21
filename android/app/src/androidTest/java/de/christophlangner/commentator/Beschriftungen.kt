package de.christophlangner.commentator

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Beschriftung aus den Ressourcen der App unter Test.
 *
 * Fest eingetippte deutsche Texte machten die Gerätetests sprachabhängig:
 * Auf einem Emulator mit `en-US` scheiterten sie, weil dort „Approve" statt
 * „Genehmigen" steht – und das sah nach einer Regression aus, war aber nur
 * die Umgebung. Über die Ressource stimmt der Text in jeder Sprache.
 */
internal fun text(@StringRes id: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
