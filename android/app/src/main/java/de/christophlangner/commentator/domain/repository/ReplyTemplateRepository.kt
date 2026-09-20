package de.christophlangner.commentator.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Ein gespeicherter Antworttext.
 *
 * Die Kennung bleibt über das Bearbeiten hinweg gleich, damit die Liste beim
 * Ändern nicht springt und ein Eintrag eindeutig gelöscht werden kann.
 */
data class ReplyTemplate(
    val id: String,
    val text: String,
) {
    /** Kurzfassung für die Auswahlleiste über dem Antwortfeld. */
    val shortLabel: String
        get() = text.lineSequence().first().let { line ->
            if (line.length <= LABEL_LENGTH) line else line.take(LABEL_LENGTH).trimEnd() + "…"
        }

    companion object {
        const val LABEL_LENGTH = 28
        const val MAX_TEMPLATES = 20
    }
}

/**
 * Wiederverwendbare Antworttexte.
 *
 * Rein lokal: Diese Texte gehen WordPress nichts an, solange sie nicht
 * abgeschickt werden.
 */
interface ReplyTemplateRepository {

    val templates: Flow<List<ReplyTemplate>>

    /** Legt einen Baustein an und gibt ihn zurück, oder `null` bei leerem Text. */
    suspend fun add(text: String): ReplyTemplate?

    suspend fun update(id: String, text: String)

    suspend fun remove(id: String)
}
