package de.christophlangner.commentator.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/**
 * Zwischengespeicherter Kommentar.
 *
 * Der Primärschlüssel ist zusammengesetzt aus [instanceId] und [id]. Damit
 * können mehrere WordPress-Instanzen nebeneinander liegen, ohne dass dafür
 * später eine Migration nötig wird.
 */
@Entity(
    tableName = "comments",
    primaryKeys = ["instanceId", "id"],
    indices = [
        Index(value = ["instanceId", "status", "dateEpochMillis"]),
        Index(value = ["instanceId", "parentId"]),
        Index(value = ["instanceId", "postId"]),
    ],
)
data class CommentEntity(
    val instanceId: String,
    val id: Long,
    val postId: Long,
    val parentId: Long,
    /** Nutzer-ID des Verfassers, 0 bei Gaesten. Erkennt Beitraege des Teams. */
    val authorId: Long,
    val authorName: String,
    val authorEmail: String?,
    val authorUrl: String?,
    val avatarUrl: String?,
    val contentHtml: String,
    val contentPlain: String,
    val dateEpochMillis: Long,
    val status: String,
    val link: String?,
)

/** Beitragstitel, getrennt gehalten, damit ein Titelwechsel nur eine Zeile berührt. */
@Entity(tableName = "post_titles", primaryKeys = ["instanceId", "postId"])
data class PostTitleEntity(
    val instanceId: String,
    val postId: Long,
    val title: String,
)

/** Zustand der Synchronisierung je Instanz. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @androidx.room.PrimaryKey val instanceId: String,
    val lastSyncEpochMillis: Long?,
    /** Neuester Kommentar, über den bereits benachrichtigt wurde. */
    val lastNotifiedCommentId: Long,
    val lastNotifiedDateEpochMillis: Long,
)

/**
 * Wer auf einem Blog zum Team gehoert, und in welcher Rolle.
 *
 * Gespeichert, weil die Zuordnung sonst erst nach dem Aktualisieren
 * vorliegt: Beim Start standen die Kommentare des Teams kurz ohne ihre
 * Rollenmarke da, und eingeklappte Rollen klappten erst nachtraeglich zu.
 * Die Zuordnung aendert sich selten - sie zwischen zwei Starts wegzuwerfen
 * kostet nur Zeit.
 */
@Entity(tableName = "team_members", primaryKeys = ["instanceId", "userId"])
data class TeamMemberEntity(
    val instanceId: String,
    val userId: Long,
    val roleSlug: String,
    /**
     * Der Anzeigename der Rolle, wie der Blog ihn meldet.
     *
     * Beim eigenen Konto ohne Plugin leer - wie die Oberflaeche es dann
     * benennt, ist uebersetzter Text und gehoert nicht in die Datenbank.
     */
    val roleName: String,
)

/**
 * Die Rollen, die ein Blog kennt.
 *
 * Getrennt von den Mitgliedern: Eine Rolle kann es geben, ohne dass ihr
 * gerade jemand angehoert - fuer die Auswahl in den Einstellungen muss sie
 * trotzdem dastehen.
 */
@Entity(tableName = "team_roles", primaryKeys = ["instanceId", "slug"])
data class TeamRoleEntity(
    val instanceId: String,
    val slug: String,
    val name: String,
)

/**
 * Wie viele Kommentare ein Filter zuletzt enthielt.
 *
 * Der Zwischenspeicher allein kann "hier ist nichts" nicht von "hier wurde
 * noch nichts geladen" unterscheiden - beides ist eine leere Tabelle. Genau
 * daran hing bisher der Start: Wer nichts Offenes hatte, sah bei jedem
 * Oeffnen erst Platzhalterkarten, bis der Server dasselbe bestaetigte, was
 * die App beim letzten Mal schon wusste.
 *
 * Deshalb wird die Zahl festgehalten, nicht nur die Kommentare. Ein Eintrag
 * mit 0 ist eine Antwort; ein fehlender Eintrag heisst "noch nie geholt".
 * [updatedAtEpochMillis] sagt, wie alt die Auskunft ist.
 */
@Entity(tableName = "filter_counts", primaryKeys = ["instanceId", "filter"])
data class FilterCountEntity(
    val instanceId: String,
    /** Name aus `CommentFilter`, nicht der Wert der API. */
    val filter: String,
    val count: Int,
    val updatedAtEpochMillis: Long,
)

/**
 * Bereits gemeldete Kommentare.
 *
 * Zusammen mit [SyncStateEntity.lastNotifiedCommentId] verhindert diese
 * Tabelle, dass für denselben Kommentar zweimal benachrichtigt wird - auch
 * dann, wenn zwei Hintergrundläufe sich überschneiden.
 */
@Entity(tableName = "notified_comments", primaryKeys = ["instanceId", "commentId"])
data class NotifiedCommentEntity(
    val instanceId: String,
    val commentId: Long,
    val notifiedAtEpochMillis: Long,
)

/**
 * Wie viele Kommentare je Blog in einem Status liegen.
 *
 * Aus dem Zwischenspeicher, nicht vom Server: Der Blogumschalter soll keine
 * Anfrage je Blog auslösen. Ein Blog, der noch nie geladen wurde, kommt hier
 * gar nicht vor - er zeigt dann keine Zahl statt einer falschen Null.
 */
data class InstanceCount(
    val instanceId: String,
    val anzahl: Int,
)

/** Kommentar samt Beitragstitel, wie ihn die Oberfläche braucht. */
data class CommentWithPost(
    @Embedded val comment: CommentEntity,
    val postTitle: String?,
)
