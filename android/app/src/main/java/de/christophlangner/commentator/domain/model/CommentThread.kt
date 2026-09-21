package de.christophlangner.commentator.domain.model

/**
 * Ein Eintrag in der gefädelten Liste.
 *
 * Bewusst flach mit einer Tiefenangabe statt als Baum: Die Liste ist eine
 * `LazyColumn`, die nur dann sparsam bleibt, wenn sie ihre Einträge einzeln
 * kennt.
 */
data class ThreadEntry(
    val comment: Comment,
    /** Einrückungstiefe; 0 ist der Anfang eines Fadens. */
    val depth: Int,
    /**
     * Ob der Eintrag nur den Zusammenhang herstellt.
     *
     * Solche Einträge gehören nicht zum gewählten Filter – sie stehen da,
     * damit eine Antwort nicht ohne die Frage dasteht. Moderiert wird an
     * ihnen nicht.
     */
    val isContext: Boolean = false,
)

/**
 * Baut aus einer Kommentarliste Gesprächsfäden.
 *
 * Eine rein chronologische Liste reißt Antwort und Frage auseinander: Der
 * Kommentar, auf den sich eine Antwort bezieht, steht oft weit weg oder gar
 * nicht in derselben Liste. Gefädelt steht die Antwort da, wo sie hingehört.
 *
 * Die Fäden selbst bleiben chronologisch geordnet – nach ihrem jüngsten
 * Beitrag, nicht nach dem Anfang. Ein Faden, in dem gerade etwas passiert,
 * gehört nach oben, auch wenn er vor Wochen begonnen hat.
 */
object CommentThreads {

    /**
     * Tiefste Einrückung.
     *
     * Darunter wird nicht weiter eingerückt, sondern auf dieser Stufe
     * weitergeführt: Auf einem Telefon bliebe sonst irgendwann keine Breite
     * mehr für den Text.
     */
    const val MAX_DEPTH = 3

    /**
     * @param comments Was zum gewählten Filter gehört.
     * @param context Kommentare, die nur den Zusammenhang herstellen – in
     *   aller Regel die Verfasser-Kommentare, auf die geantwortet wurde.
     */
    fun build(
        comments: List<Comment>,
        context: List<Comment> = emptyList(),
    ): List<ThreadEntry> {
        if (comments.isEmpty()) return emptyList()

        val eigene = comments.associateBy { it.id }
        // Was schon zum Filter gehört, zählt nicht noch einmal als Zusammenhang.
        val zusammenhang = context.filterNot { it.id in eigene }.associateBy { it.id }
        val alle = eigene + zusammenhang

        val kinder = alle.values
            .filter { it.parentId != 0L && it.parentId in alle }
            .groupBy { it.parentId }

        val wurzeln = alle.values.filter { it.parentId == 0L || it.parentId !in alle }

        // Ein Zusammenhang ohne Antwort darunter hat keinen Zweck - dann wäre
        // es ein Kommentar, der gar nicht zum Filter gehört.
        val sichtbareWurzeln = wurzeln.filter { it.id in eigene || kinder.containsKey(it.id) }

        return sichtbareWurzeln
            .sortedWith(
                compareByDescending<Comment> { juengsteZeit(it, kinder) }
                    .thenByDescending { it.id },
            )
            .flatMap { wurzel -> faden(wurzel, kinder, eigene, tiefe = 0) }
    }

    /**
     * Ein Faden von der Wurzel abwärts.
     *
     * Antworten stehen aufsteigend nach Zeit: Ein Gespräch liest sich von
     * oben nach unten, anders als die Liste der Fäden darüber.
     */
    private fun faden(
        wurzel: Comment,
        kinder: Map<Long, List<Comment>>,
        eigene: Map<Long, Comment>,
        tiefe: Int,
    ): List<ThreadEntry> {
        val eintrag = ThreadEntry(
            comment = wurzel,
            depth = tiefe.coerceAtMost(MAX_DEPTH),
            isContext = wurzel.id !in eigene,
        )

        val nachfolger = kinder[wurzel.id].orEmpty()
            .sortedWith(compareBy<Comment> { it.date }.thenBy { it.id })
            .flatMap { kind -> faden(kind, kinder, eigene, tiefe + 1) }

        return listOf(eintrag) + nachfolger
    }

    /** Wann in diesem Faden zuletzt etwas geschrieben wurde. */
    private fun juengsteZeit(
        wurzel: Comment,
        kinder: Map<Long, List<Comment>>,
    ): java.time.Instant {
        var juengste = wurzel.date
        // Iterativ statt rekursiv: Ein fehlerhafter Datenbestand mit einem
        // Verweiskreis würde sonst den Stapel sprengen.
        val offen = ArrayDeque(kinder[wurzel.id].orEmpty())
        val gesehen = mutableSetOf(wurzel.id)
        while (offen.isNotEmpty()) {
            val naechster = offen.removeFirst()
            if (!gesehen.add(naechster.id)) continue
            if (naechster.date > juengste) juengste = naechster.date
            offen += kinder[naechster.id].orEmpty()
        }
        return juengste
    }
}
