package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Team
import kotlinx.coroutines.flow.Flow

/**
 * Wer auf diesem Blog zum Team gehört.
 *
 * Braucht das Plugin: `wp/v2/users` mit `context=edit` verlangt `list_users`,
 * und das hat ein Redakteur nicht – also genau das Konto, mit dem moderiert
 * wird. Ohne Plugin bleibt der Rückfall auf das eigene Konto.
 */
interface TeamRepository {

    suspend fun team(instanceId: String): Outcome<Team>

    /**
     * Das zuletzt bekannte Team, fortlaufend beobachtet.
     *
     * Damit die Kommentare des Teams schon beim Aufbau ihre Rollenmarke
     * tragen und eingeklappte Rollen gleich eingeklappt sind - vorher kam
     * beides erst nach dem Aktualisieren, also sichtbar spaeter.
     */
    fun observeTeam(instanceId: String): Flow<Team>

    /**
     * Verwirft den hinterlegten Stand eines Blogs, etwa nach einer Aenderung
     * seiner Rollenauswahl.
     *
     * Nur dieser Blog: Die Auswahl gilt je Blog, und die uebrigen Staende
     * ohne Anlass wegzuwerfen wuerde nur zusaetzliche Anfragen kosten.
     */
    suspend fun invalidate(instanceId: String)
}
