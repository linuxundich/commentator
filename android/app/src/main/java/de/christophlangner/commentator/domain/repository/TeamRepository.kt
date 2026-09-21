package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Team

/**
 * Wer auf diesem Blog zum Team gehört.
 *
 * Braucht das Plugin: `wp/v2/users` mit `context=edit` verlangt `list_users`,
 * und das hat ein Redakteur nicht – also genau das Konto, mit dem moderiert
 * wird. Ohne Plugin bleibt der Rückfall auf das eigene Konto.
 */
interface TeamRepository {

    suspend fun team(instanceId: String): Outcome<Team>

    /** Verwirft den hinterlegten Stand, etwa nach einer Aenderung der Rollen. */
    suspend fun invalidate()
}
