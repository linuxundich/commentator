package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.data.remote.dto.ApiRootDto
import de.christophlangner.commentator.data.remote.dto.BlocklistDto
import de.christophlangner.commentator.data.remote.dto.BlocklistRequest
import de.christophlangner.commentator.data.remote.dto.BridgeStatusDto
import de.christophlangner.commentator.data.remote.dto.BridgeSummaryDto
import de.christophlangner.commentator.data.remote.dto.CommentDto
import de.christophlangner.commentator.data.remote.dto.CreateCommentRequest
import de.christophlangner.commentator.data.remote.dto.EmptyRequest
import de.christophlangner.commentator.data.remote.dto.EmptyResultDto
import de.christophlangner.commentator.data.remote.dto.PostDto
import de.christophlangner.commentator.data.remote.dto.PushRequest
import de.christophlangner.commentator.data.remote.dto.PushTestDto
import de.christophlangner.commentator.data.remote.dto.TeamDto
import de.christophlangner.commentator.data.remote.dto.UpdateCommentRequest
import de.christophlangner.commentator.data.remote.dto.UserDto
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Die WordPress-REST-API, soweit die App sie benötigt.
 *
 * Basisadresse ist immer `https://<site>/wp-json/`. Moderationsrelevante
 * Abfragen laufen mit `context=edit`, weil WordPress Status und
 * E-Mail-Adresse andernfalls nicht ausliefert.
 */
interface WordPressApi {

    /** Wurzel der REST-API. Nimmt eine vollständige URL, weil sie vor der Einrichtung aufgerufen wird. */
    @GET
    suspend fun index(@Url url: String): Response<ApiRootDto>

    /**
     * Die Startseite des Blogs als rohes HTML.
     *
     * Nur für einen Fall da: Hat der Blog kein WordPress-Site-Icon gesetzt,
     * steht sein Symbol oft nur als `<link rel="icon">` im Seitenkopf. Über
     * die REST-API ist es dann nicht zu finden.
     *
     * `@Streaming`, damit nicht die ganze Seite im Speicher landet – gelesen
     * wird nur bis zum Ende des Kopfbereichs.
     */
    @Streaming
    @GET
    suspend fun homePage(@Url url: String): Response<ResponseBody>

    @GET("wp/v2/users/me")
    suspend fun currentUser(
        @Query("context") context: String = "edit",
    ): Response<UserDto>

    @GET("wp/v2/comments")
    suspend fun listComments(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("search") search: String? = null,
        @Query("after") after: String? = null,
        /**
         * Verfasser, die ausgeblendet werden sollen, kommagetrennt.
         *
         * Bewusst eine Zeichenkette statt einer Liste: Retrofit schreibt eine
         * Liste als wiederholten Parameter, und davon wertet WordPress nur den
         * letzten Wert aus - von zwei ausgeschlossenen Konten waere also nur
         * eines wirksam. Gegen den Testblog nachgemessen: 7 Eintraege, mit
         * wiederholtem Parameter 6, kommagetrennt 5.
         *
         * Wirkt auch auf `X-WP-Total` und damit auf die Zahlen an der
         * Filterleiste.
         */
        @Query("author_exclude") authorExclude: String? = null,
        /**
         * Nur diese IDs, kommagetrennt - wie bei `author_exclude` und aus
         * demselben Grund keine Liste.
         *
         * Wird gebraucht, um die Kommentare nachzuholen, auf die geantwortet
         * wurde: Sie haben oft einen anderen Status als der gewaehlte Filter
         * und fehlen deshalb in der Liste.
         */
        @Query("include") include: String? = null,
        /** Nur Antworten auf diese IDs, kommagetrennt. */
        @Query("parent") parents: String? = null,
        @Query("context") context: String = "edit",
        @Query("orderby") orderBy: String = "date_gmt",
        @Query("order") order: String = "desc",
        @Query("type") type: String = "comment",
    ): Response<List<CommentDto>>

    @GET("wp/v2/comments/{id}")
    suspend fun getComment(
        @Path("id") id: Long,
        @Query("context") context: String = "edit",
    ): Response<CommentDto>

    /**
     * Zaehlt die Kommentare einer Adresse. Ausgewertet wird allein die
     * Kopfzeile `X-WP-Total`; der Rumpf wird bewusst klein gehalten.
     */
    @GET("wp/v2/comments")
    suspend fun countCommentsOfAuthor(
        @Query("author_email") authorEmail: String,
        @Query("status") status: String,
        @Query("exclude") exclude: Long,
        @Query("per_page") perPage: Int = 1,
        @Query("context") context: String = "edit",
        @Query("_fields") fields: String = "id",
    ): Response<List<CommentDto>>

    @GET("wp/v2/comments")
    suspend fun listReplies(
        @Query("parent") parentId: Long,
        @Query("status") status: String = "all",
        @Query("per_page") perPage: Int = 50,
        @Query("context") context: String = "edit",
        @Query("order") order: String = "asc",
    ): Response<List<CommentDto>>

    @POST("wp/v2/comments/{id}")
    suspend fun updateComment(
        @Path("id") id: Long,
        @Body body: UpdateCommentRequest,
    ): Response<CommentDto>

    @POST("wp/v2/comments")
    suspend fun createComment(
        @Body body: CreateCommentRequest,
    ): Response<CommentDto>

    /** Ohne `force` wandert der Kommentar in den Papierkorb. */
    @DELETE("wp/v2/comments/{id}")
    suspend fun deleteComment(
        @Path("id") id: Long,
        @Query("force") force: Boolean = false,
    ): Response<JsonElement>

    @GET("wp/v2/posts")
    suspend fun listPosts(
        @Query("include") include: String,
        @Query("per_page") perPage: Int,
        @Query("_fields") fields: String = "id,title,link",
    ): Response<List<PostDto>>

    @GET("wp/v2/pages")
    suspend fun listPages(
        @Query("include") include: String,
        @Query("per_page") perPage: Int,
        @Query("_fields") fields: String = "id,title,link",
    ): Response<List<PostDto>>

    // --- Optionales Plugin commentator-bridge ---

    @GET("commentator/v1/status")
    suspend fun bridgeStatus(): Response<BridgeStatusDto>

    @GET("commentator/v1/summary")
    suspend fun bridgeSummary(): Response<BridgeSummaryDto>

    /** Leert Spam oder Papierkorb in Stapeln; die Antwort nennt den Rest. */
    @POST("commentator/v1/empty")
    suspend fun bridgeEmpty(@Body body: EmptyRequest): Response<EmptyResultDto>

    @GET("commentator/v1/team")
    suspend fun bridgeTeam(): Response<TeamDto>

    @POST("commentator/v1/blocklist")
    suspend fun bridgeBlock(@Body body: BlocklistRequest): Response<BlocklistDto>

    @POST("commentator/v1/push")
    suspend fun bridgePushRegister(@Body body: PushRequest): Response<ResponseBody>

    @POST("commentator/v1/push/test")
    suspend fun bridgePushTest(): Response<PushTestDto>

    @DELETE("commentator/v1/push")
    suspend fun bridgePushRemove(@Query("endpoint") endpoint: String): Response<ResponseBody>
}
