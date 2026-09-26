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
 * Wiederverwendbare Antworttexte, je Blog.
 *
 * Rein lokal: Diese Texte gehen WordPress nichts an, solange sie nicht
 * abgeschickt werden.
 *
 * Je Blog und nicht gemeinsam: Der Ton auf einem Fachblog ist ein anderer als
 * auf einem Kundenprojekt, und eine Liste aus allen Bausteinen aller Blogs
 * waere gerade dort im Weg, wo es schnell gehen soll.
 */
interface ReplyTemplateRepository {

    fun templates(instanceId: String): Flow<List<ReplyTemplate>>

    /** Legt einen Baustein an und gibt ihn zurück, oder `null` bei leerem Text. */
    suspend fun add(instanceId: String, text: String): ReplyTemplate?

    suspend fun update(instanceId: String, id: String, text: String)

    suspend fun remove(instanceId: String, id: String)
}
