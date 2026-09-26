package de.christophlangner.commentator.domain.model

/**
 * Eine Zeile der Liste im Posteingang.
 *
 * Ein Eintrag steht entweder fuer sich, oder mehrere aufeinanderfolgende
 * Beitraege des Teams sind zu einer Zeile zusammengefasst.
 */
sealed interface TimelineRow {

    /** Stabile Kennung fuer `LazyColumn`. */
    val key: String

    data class Single(val entry: ThreadEntry) : TimelineRow {
        override val key: String get() = "k" + entry.comment.id
    }

    /**
     * Zusammengefasste Beitraege des Teams.
     *
     * Sie sind nicht weg, nur eingeklappt: Die Zeile nennt Anzahl und Rollen
     * und laesst sich aufklappen. Wer moderiert, soll sehen, dass an dieser
     * Stelle etwas aus dem Team steht - nur eben nicht in voller Hoehe.
     */
    data class TeamGroup(
        val entries: List<ThreadEntry>,
        /** Die beteiligten Rollen, in der Reihenfolge ihres Auftretens. */
        val roles: List<TeamRole>,
    ) : TimelineRow {
        override val key: String get() = "g" + entries.first().comment.id

        /** Einrueckung der Zeile: die geringste der zusammengefassten. */
        val depth: Int get() = entries.minOf { it.depth }

        val count: Int get() = entries.size
    }
}

/**
 * Fasst die eingeklappten Rollen zu Gruppen zusammen.
 *
 * Nur unmittelbar aufeinanderfolgende Eintraege werden zusammengelegt: Die
 * Reihenfolge der Liste ist beim Faden die Gestalt des Gespraechs, und sie
 * umzusortieren, um mehr zusammenlegen zu koennen, wuerde genau das zerstoeren.
 */
object Timeline {

    fun rows(
        entries: List<ThreadEntry>,
        team: Team,
        styles: RoleStyles,
    ): List<TimelineRow> {
        if (entries.isEmpty()) return emptyList()

        val ergebnis = mutableListOf<TimelineRow>()
        val offen = mutableListOf<ThreadEntry>()

        fun gruppeSchliessen() {
            when (offen.size) {
                0 -> Unit
                // Eine einzelne Karte einzuklappen spart nichts und kostet
                // einen Handgriff - dann steht sie besser gleich da.
                1 -> ergebnis += TimelineRow.Single(offen.single())
                else -> ergebnis += TimelineRow.TeamGroup(
                    entries = offen.toList(),
                    roles = offen.mapNotNull { team.roleOf(it.comment.authorId) }.distinct(),
                )
            }
            offen.clear()
        }

        entries.forEach { entry ->
            if (istEingeklappt(entry, team, styles)) {
                offen += entry
            } else {
                gruppeSchliessen()
                ergebnis += TimelineRow.Single(entry)
            }
        }
        gruppeSchliessen()

        return ergebnis
    }

    /**
     * Zusammenhangs-Eintraege bleiben immer sichtbar: Sie stehen nur da,
     * damit die Antwort darunter nicht ohne die Frage dasteht. Waeren sie
     * eingeklappt, fehlte genau der Bezug, dessentwegen sie geladen wurden.
     */
    private fun istEingeklappt(entry: ThreadEntry, team: Team, styles: RoleStyles): Boolean {
        if (entry.isContext) return false
        val rolle = team.roleOf(entry.comment.authorId) ?: return false
        return !styles.of(rolle.slug).showInTimeline
    }
}
