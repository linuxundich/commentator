package de.christophlangner.commentator.domain.model

/**
 * Wer zum Team gehört, und in welcher Rolle.
 *
 * Die Zuordnung liegt als Abbildung von Nutzer-ID auf Rolle vor und nicht als
 * Rollenprüfung je Kommentar: Ein Kommentar trägt nur die Nutzer-ID, die
 * Rolle steht woanders. Einmal geholt, ist der Abgleich danach kostenlos.
 */
data class Team(
    /** Nutzer-ID auf die Rolle, in der die Person im Team geführt wird. */
    val members: Map<Long, TeamRole> = emptyMap(),
    /** Rollen, die der Blog überhaupt kennt – für die Auswahl in den Einstellungen. */
    val availableRoles: List<TeamRole> = emptyList(),
) {
    val memberIds: Set<Long> get() = members.keys

    /** Die Rolle des Verfassers, oder `null` wenn er nicht zum Team gehört. */
    fun roleOf(authorId: Long): TeamRole? =
        // Gäste tragen bei WordPress die 0. Ohne diese Bedingung wäre jeder
        // Gast Team, sobald 0 versehentlich in der Abbildung landete.
        if (authorId == 0L) null else members[authorId]

    fun contains(authorId: Long): Boolean = roleOf(authorId) != null

    companion object {
        /**
         * Rollen, die standardmäßig als Team gelten.
         *
         * Autoren und Mitarbeiter schreiben Beiträge, moderieren aber nicht –
         * sie als Team zu kennzeichnen wäre auf den meisten Blogs zu weit
         * gefasst.
         */
        val DEFAULT_ROLES = setOf("administrator", "editor")

        /** Rollenkürzel der Administratoren; sie werden eigens hervorgehoben. */
        const val ADMINISTRATOR = "administrator"
        const val EDITOR = "editor"
        const val AUTHOR = "author"
        const val CONTRIBUTOR = "contributor"

        /**
         * Die Rollen, die jede WordPress-Installation mitbringt.
         *
         * Die tatsächlich vorhandenen meldet das Plugin. Ohne Plugin wäre die
         * Rollenauswahl sonst leer, und es ließe sich nichts einstellen –
         * dabei sind es auf den allermeisten Blogs genau diese vier.
         */
        val STANDARD_ROLES = listOf(ADMINISTRATOR, EDITOR, AUTHOR, CONTRIBUTOR)

        /**
         * Ersatzkürzel für das eigene Konto.
         *
         * Das eigene Konto zählt immer zum Team, seine Rolle kennt die App
         * aber nur mit Plugin – ohne bleibt es sogar das einzige erkannte
         * Mitglied. Ohne ein eigenes Kürzel gäbe es für die eigenen
         * Kommentare nichts einzustellen, und gerade sie sind die häufigsten
         * Beiträge des Teams überhaupt.
         *
         * Die zwei Unterstriche halten es von jedem echten Rollenkürzel fern.
         */
        const val SELF = "__self"
    }
}

/**
 * Eine Rolle des Blogs.
 *
 * Wie ein Kommentar aus ihr aussieht und ob er meldet, steht nicht hier,
 * sondern in [RoleStyle] – das ist eine Einstellung des Benutzers und keine
 * Eigenschaft der Rolle.
 */
data class TeamRole(val slug: String, val name: String)
