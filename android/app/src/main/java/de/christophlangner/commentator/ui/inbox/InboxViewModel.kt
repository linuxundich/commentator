package de.christophlangner.commentator.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.CommentThreads
import de.christophlangner.commentator.domain.model.ThreadEntry
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Timeline
import de.christophlangner.commentator.domain.model.TimelineRow
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.SiteSettings
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Zustand des Posteingangs.
 *
 * Die Liste kommt immer aus dem lokalen Cache; [isRefreshing] und [lastSync]
 * sagen, wie aktuell sie ist. Dadurch gibt es keinen Zustand, in dem die
 * Oberfläche leer bleibt, nur weil gerade keine Verbindung besteht.
 */
data class InboxUiState(
    val instance: WordPressInstance? = null,
    /** Alle eingerichteten Blogs, fuer den Umschalter in der Kopfleiste. */
    val instances: List<WordPressInstance> = emptyList(),
    /**
     * Offene Kommentare je Blog aus dem Zwischenspeicher.
     *
     * Nur zur Orientierung im Umschalter. Ein Blog ohne Eintrag zeigt keine
     * Zahl - besser als eine Null, die nach "nichts zu tun" aussieht, obwohl
     * dort nur noch nichts geladen wurde.
     */
    val pendingPerInstance: Map<String, Int> = emptyMap(),
    val filter: CommentFilter = CommentFilter.PENDING,
    val comments: List<Comment> = emptyList(),
    val isInitialLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val isOffline: Boolean = false,
    val sessionInvalid: Boolean = false,
    val lastSync: Instant? = null,
    val error: AppError? = null,
    val showAvatars: Boolean = false,
    /** Auffaelligkeiten je Kommentar-ID; fehlt ein Eintrag, gibt es nichts zu zeigen. */
    val signals: Map<Long, CommentSignals> = emptyMap(),
    /** Anzahl je Filter. Fehlt ein Eintrag, zeigt die Leiste dort keine Zahl. */
    val counts: Map<CommentFilter, Int> = emptyMap(),
    /** Nutzer-IDs des Teams; deren Kommentare werden abgesetzt dargestellt. */
    val team: Team = Team(),
    /** Farbe und Sichtbarkeit je Rolle. */
    val roleStyles: RoleStyles = RoleStyles.DEFAULT,
    /**
     * Die Liste als Gespraechsfaden.
     *
     * Bei abgeschalteter Faedelung und waehrend der Suche steht hier
     * dasselbe wie in [comments], nur ohne Einrueckung. Angezeigt wird
     * [entries], nicht dieses Feld.
     */
    val threadEntries: List<ThreadEntry> = emptyList(),
    /** Ob das Suchfeld offen ist. Die Liste zeigt dann Treffer statt des Filters. */
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    /** Ob zur aktuellen Eingabe schon ein Ergebnis vorliegt. */
    val searchDone: Boolean = false,
) {
    /** Ohne Verbindung, ohne Berechtigung oder mit ungültiger Sitzung wird nicht moderiert. */
    val moderationEnabled: Boolean
        get() = !isOffline && !sessionInvalid && instance?.canModerate == true

    /**
     * Was die Liste anzeigt.
     *
     * Ohne Fadenstruktur die flache Liste. Das ist zugleich der Rückfall für
     * den entarteten Fall, dass sich aus den Bezügen kein Faden bauen lässt –
     * dann steht lieber die chronologische Liste da als eine leere Fläche.
     */
    val entries: List<ThreadEntry>
        get() = threadEntries.ifEmpty { comments.map { ThreadEntry(comment = it, depth = 0) } }

    /**
     * Die Liste, wie sie gezeichnet wird.
     *
     * Eingeklappte Rollen stehen hier als eine Zeile statt als viele Karten.
     */
    val rows: List<TimelineRow>
        get() = Timeline.rows(entries, team, roleStyles)
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val commentRepository: CommentRepository,
    private val moderateComment: ModerateCommentUseCase,
    private val teamRepository: TeamRepository,
    private val undoModeration: UndoModerationUseCase,
    settingsRepository: SettingsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    /** Ereignisse, die genau einmal passieren sollen - etwa eine Snackbar. */
    sealed interface Event {
        data class ModerationDone(
            val commentId: Long,
            val action: ModerationAction,
            val previousStatus: CommentStatus,
            val undoable: Boolean,
        ) : Event

        data class Emptied(val deleted: Int, val remaining: Int) : Event
        data class Failed(val error: AppError) : Event
    }

    private data class Transient(
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val initialLoadDone: Boolean = false,
        val error: AppError? = null,
        /** Ob die Zahlen in diesem Lauf schon einmal geholt wurden. */
        val countsLoaded: Boolean = false,
        val team: Team = Team(),
    )

    private val filter = MutableStateFlow(CommentFilter.PENDING)
    private val transient = MutableStateFlow(Transient())

    /**
     * Zustand der Suche.
     *
     * [treffer] ist `null`, solange zur aktuellen Eingabe nichts gesucht
     * wurde - das unterscheidet "noch nichts gesucht" von "nichts gefunden".
     */
    private data class Suche(
        val aktiv: Boolean = false,
        val text: String = "",
        val treffer: List<Long>? = null,
        val laeuft: Boolean = false,
    )

    private val suche = MutableStateFlow(Suche())

    /** Ob Rechte und Zaehlungen in diesem Lauf schon einmal geholt wurden. */
    private var siteDataLoaded = false

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Die aktive Instanz, dauerhaft im Speicher gehalten.
     *
     * Bewusst `Eagerly` und nicht aus [state] abgeleitet: [state] verwendet
     * `WhileSubscribed` und liefert ohne Abonnenten den Anfangswert. Aktionen
     * würden dann still ins Leere laufen, weil keine Instanz bekannt wäre.
     */
    private val instance = authRepository.observeActiveInstance()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val comments = combine(
        instance,
        filter,
        // Nur die beiden Felder, die die Liste bestimmen - sonst wuerde jeder
        // Tastendruck die Abfrage neu aufsetzen.
        suche.map { it.aktiv to it.treffer }.distinctUntilChanged(),
        ::Triple,
    ).flatMapLatest { (instance, filter, suchstand) ->
        val (aktiv, treffer) = suchstand
        when {
            instance == null -> flowOf(emptyList())
            // Welche Kommentare Treffer sind, entscheidet der Server; was in
            // ihnen steht, kommt wie sonst auch aus dem Zwischenspeicher.
            aktiv -> commentRepository.observeCommentsByIds(instance.id, treffer.orEmpty())
            else -> commentRepository.observeComments(instance.id, filter)
        }
    }

    /**
     * Die zuletzt bekannten Zahlen je Filter.
     *
     * Aus dem Zwischenspeicher und nicht aus dem laufenden Abruf: Die
     * Filterleiste soll schon beim Aufbau Zahlen tragen. Der Abruf schreibt
     * dorthin und wirkt darum von selbst hierher zurueck.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val zaehlungen = instance.flatMapLatest { instance ->
        if (instance == null) flowOf(emptyMap()) else commentRepository.observeCounts(instance.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val syncState = instance.flatMapLatest { instance ->
        if (instance == null) flowOf(null) else commentRepository.observeSyncState(instance.id)
    }

    /**
     * Was aus den Einstellungen die Darstellung bestimmt.
     *
     * Gebuendelt und nicht jedes Feld als eigener Fluss: `combine` nimmt in
     * der typisierten Fassung fuenf Quellen, und die sind belegt.
     */
    private data class Anzeige(
        val showAvatars: Boolean,
        val threaded: Boolean,
        val roleStyles: RoleStyles,
    )

    /**
     * Die Rolleneinstellungen des angezeigten Blogs.
     *
     * Jeder Blog hat seine eigenen: Ein Wechsel muss deshalb auch die Farben
     * und das Eingeklappte mitnehmen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val siteSettings = instance.flatMapLatest { instance ->
        if (instance == null) {
            flowOf(SiteSettings.DEFAULT)
        } else {
            settingsRepository.siteSettings(instance.id)
        }
    }

    private val anzeige = combine(
        settingsRepository.settings
            .map { it.showAvatars to it.threadedInbox }
            .distinctUntilChanged(),
        siteSettings.map { it.roleStyles }.distinctUntilChanged(),
    ) { (avatare, faden), roleStyles ->
        Anzeige(showAvatars = avatare, threaded = faden, roleStyles = roleStyles)
    }

    private val environment = combine(
        connectivity.isOnline,
        authRepository.observeSessionInvalid(),
        anzeige,
        syncState,
    ) { isOnline, sessionInvalid, anzeige, sync ->
        Environment(
            isOnline = isOnline,
            sessionInvalid = sessionInvalid,
            showAvatars = anzeige.showAvatars,
            lastSync = sync?.lastSuccessfulSync,
            threaded = anzeige.threaded,
            roleStyles = anzeige.roleStyles,
        )
    }

    private data class Environment(
        val isOnline: Boolean,
        val sessionInvalid: Boolean,
        val showAvatars: Boolean,
        val lastSync: Instant?,
        val threaded: Boolean,
        val roleStyles: RoleStyles,
    )

    /**
     * Die Kommentare, auf die sich die geladenen Antworten beziehen.
     *
     * Sie gehoeren nicht zum Filter - beim Filter "Offen" ist der Kommentar
     * davor meist laengst genehmigt. Das Repository holt sie beim
     * Aktualisieren nach; hier werden sie nur aus dem Zwischenspeicher
     * gelesen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val threadContext = combine(
        instance,
        comments.map { liste ->
            val eigene = liste.mapTo(mutableSetOf()) { it.id }
            liste.mapNotNull { it.parentId.takeIf { id -> id != 0L && id !in eigene } }
                .distinct()
                .sorted()
        }.distinctUntilChanged(),
        ::Pair,
    ).flatMapLatest { (instance, bezuege) ->
        if (instance == null || bezuege.isEmpty()) {
            flowOf(emptyList())
        } else {
            commentRepository.observeCommentsByIds(instance.id, bezuege)
        }
    }

    /**
     * Was der Umschalter braucht.
     *
     * Gebuendelt, damit `combine` im Zustand nicht ueber seine typisierten
     * Ueberladungen hinauswaechst.
     */
    private data class Blogs(
        val alle: List<WordPressInstance> = emptyList(),
        val offen: Map<String, Int> = emptyMap(),
    )

    private val blogs = combine(
        authRepository.observeInstances(),
        commentRepository.observePendingCounts(),
        ::Blogs,
    )

    private data class Zwischenstand(
        val instance: WordPressInstance?,
        val filter: CommentFilter,
        val comments: List<Comment>,
        val transient: Transient,
        val environment: Environment,
    )

    val state: StateFlow<InboxUiState> = combine(
        combine(instance, filter, comments, transient, environment, ::Zwischenstand),
        suche,
        threadContext,
        blogs,
        zaehlungen,
    ) { stand, suche, bezuege, blogs, zaehlungen ->
        val instance = stand.instance
        val filter = stand.filter
        val comments = stand.comments
        val transient = stand.transient
        val environment = stand.environment
        // Der Zwischenspeicher kann "hier ist nichts" nicht von "hier wurde
        // noch nichts geladen" unterscheiden - beides ist eine leere Liste.
        // Die gespeicherte Zahl kann es: Eine 0 ist eine Antwort, und dann
        // gehoert der Leerzustand auf den Schirm, keine Platzhalterkarte.
        val bekanntLeer = zaehlungen[filter] == 0

        InboxUiState(
            instance = instance,
            instances = blogs.alle,
            pendingPerInstance = blogs.offen,
            filter = filter,
            comments = comments,
            isInitialLoad = !transient.initialLoadDone && comments.isEmpty() && !bekanntLeer,
            isRefreshing = transient.isRefreshing,
            isLoadingMore = transient.isLoadingMore,
            // Waehrend der Suche wird nicht nachgeladen: Die Trefferliste
            // ist eine Antwort auf eine Frage, keine fortlaufende Liste.
            canLoadMore = !suche.aktiv &&
                instance != null &&
                comments.isNotEmpty() &&
                commentRepository.hasMorePages(instance.id, filter),
            isOffline = !environment.isOnline,
            sessionInvalid = environment.sessionInvalid,
            lastSync = environment.lastSync,
            error = transient.error,
            showAvatars = environment.showAvatars,
            threadEntries = eintraege(
                comments = comments,
                context = bezuege,
                // Waehrend der Suche nicht: Eine Trefferliste ist die Antwort
                // auf eine Frage. Fremde Kommentare als Zusammenhang
                // dazwischenzuschieben stuende gegen das Gesuchte.
                threaded = environment.threaded && !suche.aktiv,
            ),
            signals = signalsFor(comments),
            counts = zaehlungen,
            team = transient.team,
            roleStyles = environment.roleStyles,
            searchActive = suche.aktiv,
            searchQuery = suche.text,
            isSearching = suche.laeuft,
            searchDone = suche.treffer != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = InboxUiState(),
    )

    init {
        viewModelScope.launch {
            combine(instance, filter, ::Pair)
                .distinctUntilChanged()
                .collect { (instance, filter) ->
                    if (instance != null) refreshIfStale(instance.id, filter)
                }
        }

        // Gesucht wird erst, wenn die Eingabe einen Moment steht. Jeder
        // Tastendruck waere eine eigene Anfrage an den Blog.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(suche.map { it.text.trim() }.distinctUntilChanged(), filter, ::Pair)
                .debounce(SUCHVERZOEGERUNG)
                .collect { (text, _) -> sucheAusfuehren(text) }
        }
    }

    /**
     * Wechselt den angezeigten Blog.
     *
     * Eine stehende Suche wird dabei verworfen: Die Treffer gehoeren zum alten
     * Blog, und auf dem neuen bedeuten dieselben Kennungen etwas anderes.
     * Der Filter bleibt - wer offene Kommentare sichtet, will das auf dem
     * naechsten Blog auch.
     */
    fun switchTo(instanceId: String) {
        if (instance.value?.id == instanceId) return
        suche.value = Suche()
        // Der neue Blog braucht seine eigenen Rechte und Zaehlungen; ohne das
        // stuenden die des vorherigen da, bis etwas anderes sie anfasst.
        siteDataLoaded = false
        transient.update { it.copy(countsLoaded = false, team = Team(), initialLoadDone = false) }
        viewModelScope.launch { authRepository.setActiveInstance(instanceId) }
    }

    fun openSearch() {
        suche.update { it.copy(aktiv = true) }
    }

    /** Schliesst die Suche und zeigt wieder die gefilterte Liste. */
    fun closeSearch() {
        suche.value = Suche()
    }

    fun setSearchQuery(text: String) {
        suche.update { it.copy(text = text, treffer = if (text.isBlank()) null else it.treffer) }
    }

    /**
     * Fuehrt die Suche aus.
     *
     * Unter [MINDESTLAENGE] Zeichen wird nicht gesucht: Ein einzelner
     * Buchstabe trifft fast alles und kostet nur eine Anfrage.
     */
    private suspend fun sucheAusfuehren(text: String) {
        if (!suche.value.aktiv) return
        val instanceId = instance.value?.id
        if (instanceId == null || text.length < MINDESTLAENGE) {
            suche.update { it.copy(treffer = null, laeuft = false) }
            return
        }

        suche.update { it.copy(laeuft = true) }
        when (val outcome = commentRepository.search(instanceId, text, filter.value)) {
            is Outcome.Success -> suche.update {
                // Nur uebernehmen, wenn die Antwort noch zur Eingabe passt -
                // sonst ueberholt eine langsame Anfrage eine neuere.
                if (it.text.trim() == text) {
                    it.copy(treffer = outcome.value, laeuft = false)
                } else {
                    it
                }
            }

            is Outcome.Failure -> {
                suche.update { it.copy(laeuft = false) }
                _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    /**
     * Holt einen Filter nur, wenn es sich lohnt.
     *
     * Beim Umschalten kann sich nichts geändert haben, was die App nicht
     * ohnehin schon weiß: Die Liste steht im Zwischenspeicher, Rechte und
     * Zählungen gehören nicht zum Filter. Vorher löste jeder Tipp auf einen
     * Filter bis zu neun Anfragen aus - Liste, Beitragstitel, Rechteabfrage
     * und Zählungen -, und die Liste flackerte durch den Ladezustand.
     *
     * Was gerade erst geholt wurde, bleibt deshalb stehen. Ein ausdrückliches
     * Aktualisieren umgeht das immer.
     */
    private suspend fun refreshIfStale(instanceId: String, filter: CommentFilter) {
        val lastRefresh = commentRepository.lastRefreshAt(instanceId, filter)
        val fresh = lastRefresh != null &&
            Duration.between(lastRefresh, Instant.now()) < FILTER_CACHE_LIFETIME
        // Der erste Lauf holt auch Rechte und Bridge-Erkennung: Beides kann
        // sich geaendert haben, seit die App zuletzt lief. Erst die spaeteren
        // Filterwechsel lassen es aus - da kann es sich nicht geaendert haben.
        val firstRun = !siteDataLoaded
        siteDataLoaded = true

        if (fresh && !firstRun) {
            // Ohne diesen Vermerk bliebe ein Bildschirm, der nie geladen
            // wurde, im Anfangszustand haengen.
            transient.update { it.copy(initialLoadDone = true) }
            return
        }
        refresh(withSiteData = firstRun)
    }

    fun setFilter(newFilter: CommentFilter) {
        filter.value = newFilter
    }

    /** Vom Benutzer ausgeloest: holt alles, auch Rechte und Zaehlungen. */
    fun refresh() {
        // Steht eine Suche, ist das Erwartete ein neuer Blick auf dieselbe
        // Frage - nicht ein Nachladen der Liste dahinter.
        if (suche.value.aktiv) {
            viewModelScope.launch { sucheAusfuehren(suche.value.text.trim()) }
            return
        }
        refresh(withSiteData = true)
    }

    private fun refresh(withSiteData: Boolean) {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true, error = null) }
            val outcome = commentRepository.refresh(instanceId, filter.value)

            if (withSiteData) {
                // Zieht nebenbei nach, was sich am Blog geändert hat - etwa ein
                // nachträglich installiertes Bridge-Plugin. Ein Fehler hier darf
                // die Liste nicht beeinflussen. Beim blossen Umschalten hat sich
                // daran nichts geaendert, deshalb entfaellt es dort.
                authRepository.refreshSiteCapabilities(instanceId)
            }

            transient.update {
                it.copy(
                    isRefreshing = false,
                    initialLoadDone = true,
                    error = outcome.errorOrNull,
                )
            }

            if (withSiteData || !transient.value.countsLoaded) loadCounts()
            if (withSiteData || transient.value.team.memberIds.isEmpty()) loadTeam()
        }
    }

    /**
     * Auffaelligkeiten der geladenen Kommentare.
     *
     * Rein lokal aus dem Cache, ohne zusaetzlichen Abruf. Dubletten lassen
     * sich nur innerhalb des Geladenen erkennen - das genuegt fuer den Fall,
     * um den es geht: derselbe Text mehrfach in derselben Welle.
     */
    /**
     * Holt die Zahlen fuer die Filterleiste.
     *
     * Nach dem Aktualisieren, nicht davor: Die Liste soll nicht darauf
     * warten. Scheitert es, bleiben die bisherigen Zahlen stehen - eine
     * verschwindende Zahl waere irritierender als eine kurz veraltete.
     */
    private suspend fun loadCounts() {
        val instanceId = instance.value?.id ?: return
        val outcome = commentRepository.countsByFilter(instanceId)
        // Geschrieben hat das Repository schon; hier bleibt nur der Vermerk,
        // dass es in diesem Lauf geschehen ist.
        if (outcome is Outcome.Success && outcome.value.isNotEmpty()) {
            transient.update { it.copy(countsLoaded = true) }
        }
    }

    private companion object {
        /**
         * Wie lange ein geholter Filter als frisch gilt.
         *
         * Zwei Minuten sind lang genug, um Hin- und Herwechseln sofort
         * wirken zu lassen, und kurz genug, dass niemand laenger auf einem
         * veralteten Stand sitzt, ohne es zu merken.
         */
        val FILTER_CACHE_LIFETIME: Duration = Duration.ofMinutes(2)

        /** Wie lange die Eingabe stehen muss, bevor gesucht wird. */
        const val SUCHVERZOEGERUNG = 400L

        /** Ab wie vielen Zeichen gesucht wird. */
        const val MINDESTLAENGE = 2
    }

    /**
     * Holt die Teamzugehoerigkeit.
     *
     * Wie die Zaehlungen: nach dem Aktualisieren, und beim blossen
     * Filterwechsel gar nicht - wer zum Team gehoert, aendert sich nicht beim
     * Umschalten.
     */
    private suspend fun loadTeam() {
        val instanceId = instance.value?.id ?: return
        val outcome = teamRepository.team(instanceId)
        if (outcome is Outcome.Success) {
            transient.update { it.copy(team = outcome.value) }
        }
    }

    /**
     * Die Liste, wie sie angezeigt wird.
     *
     * Ohne Faedelung bleibt es bei der chronologischen Reihenfolge, nur in
     * derselben Form - so muss die Oberflaeche nicht zwei Faelle kennen.
     */
    private fun eintraege(
        comments: List<Comment>,
        context: List<Comment>,
        threaded: Boolean,
    ): List<ThreadEntry> = if (threaded) {
        CommentThreads.build(comments, context)
    } else {
        comments.map { ThreadEntry(comment = it, depth = 0) }
    }

    private fun signalsFor(comments: List<Comment>): Map<Long, CommentSignals> {
        if (comments.isEmpty()) return emptyMap()
        val duplicated = CommentSignals.duplicatedIds(comments)
        return comments.associate { comment ->
            comment.id to CommentSignals(
                linkCount = CommentSignals.linkCountOf(comment.contentHtml),
                duplicated = comment.id in duplicated,
            )
        }.filterValues { it.hasAny }
    }

    fun loadMore() {
        val instanceId = instance.value?.id ?: return
        val current = state.value
        if (current.isLoadingMore || !current.canLoadMore) return

        viewModelScope.launch {
            transient.update { it.copy(isLoadingMore = true) }
            val outcome = commentRepository.loadNextPage(instanceId, filter.value)
            transient.update { it.copy(isLoadingMore = false) }
            if (outcome is Outcome.Failure) _events.tryEmit(Event.Failed(outcome.error))
        }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    fun moderate(comment: Comment, action: ModerationAction) {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            when (val outcome = moderateComment(instanceId, comment.id, action)) {
                is Outcome.Success -> {
                    _events.tryEmit(
                        Event.ModerationDone(
                            commentId = comment.id,
                            action = action,
                            previousStatus = outcome.value.previousStatus,
                            undoable = outcome.value.undoable,
                        ),
                    )
                    // Jede Moderation verschiebt einen Kommentar zwischen zwei
                    // Filtern; ohne das blieben die Zahlen bis zum naechsten
                    // Aktualisieren falsch.
                    loadCounts()
                }

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    /**
     * Leert Spam oder Papierkorb.
     *
     * Bei vielen Eintraegen arbeitet ein Aufruf nur einen Stapel ab; das
     * Ereignis nennt deshalb auch den Rest, damit die Oberflaeche nicht
     * faelschlich "fertig" meldet.
     */
    fun emptyCurrentFilter() {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true) }
            val outcome = commentRepository.emptyStatus(instanceId, filter.value)
            transient.update { it.copy(isRefreshing = false) }
            when (outcome) {
                is Outcome.Success -> {
                    _events.tryEmit(Event.Emptied(outcome.value.deleted, outcome.value.remaining))
                    loadCounts()
                }

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    fun undo(commentId: Long, previousStatus: CommentStatus) {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            val outcome = undoModeration(instanceId, commentId, previousStatus)
            if (outcome is Outcome.Failure) {
                _events.tryEmit(Event.Failed(outcome.error))
            } else {
                loadCounts()
            }
        }
    }
}
