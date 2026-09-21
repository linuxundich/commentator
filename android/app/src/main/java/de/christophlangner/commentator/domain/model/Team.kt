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
    }
}

data class TeamRole(val slug: String, val name: String) {
    /**
     * Ob diese Rolle die volle Verfügungsgewalt hat.
     *
     * Ein Kommentar aus dieser Rolle wird kräftiger ausgezeichnet: Wer
     * moderiert, soll auf einen Blick sehen, ob eine Wortmeldung aus der
     * Leitung kommt oder von einem Redakteur.
     */
    val isAdministrator: Boolean get() = slug == Team.ADMINISTRATOR
}
