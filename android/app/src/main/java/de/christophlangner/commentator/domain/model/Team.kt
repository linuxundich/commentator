package de.christophlangner.commentator.domain.model

/**
 * Wer zum Team gehört.
 *
 * Die Zuordnung ist bewusst eine Menge von Nutzer-IDs und keine Rollenprüfung
 * je Kommentar: Ein Kommentar trägt nur die Nutzer-ID, die Rolle steht
 * woanders. Einmal geholt, ist der Abgleich danach kostenlos.
 */
data class Team(
    val memberIds: Set<Long> = emptySet(),
    /** Rollen, die der Blog überhaupt kennt – für die Auswahl in den Einstellungen. */
    val availableRoles: List<TeamRole> = emptyList(),
) {
    fun contains(authorId: Long): Boolean = authorId != 0L && authorId in memberIds

    companion object {
        /**
         * Rollen, die standardmäßig als Team gelten.
         *
         * Autoren und Mitarbeiter schreiben Beiträge, moderieren aber nicht –
         * sie als Team zu kennzeichnen wäre auf den meisten Blogs zu weit
         * gefasst.
         */
        val DEFAULT_ROLES = setOf("administrator", "editor")
    }
}

data class TeamRole(val slug: String, val name: String)
