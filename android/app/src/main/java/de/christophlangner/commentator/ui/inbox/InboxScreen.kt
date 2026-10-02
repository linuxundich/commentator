package de.christophlangner.commentator.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.ThreadEntry
import de.christophlangner.commentator.domain.model.TimelineRow
import de.christophlangner.commentator.ui.common.CommentSkeletonList
import de.christophlangner.commentator.ui.common.EmptyState
import de.christophlangner.commentator.ui.common.ErrorState
import de.christophlangner.commentator.ui.common.ErrorTexts
import de.christophlangner.commentator.ui.common.OfflineBanner
import de.christophlangner.commentator.ui.common.SessionInvalidBanner
import de.christophlangner.commentator.ui.theme.Bewegung
import de.christophlangner.commentator.ui.theme.CommentatorFormen

/**
 * Posteingang: die wichtigste Ansicht der App.
 *
 * Filter, Aktualisierung und die häufigsten Moderationsentscheidungen sind
 * ohne Umweg erreichbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenComment: (Comment) -> Unit,
    onOpenSettings: () -> Unit,
    onAddSite: () -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    var showEmptyDialog by rememberSaveable { mutableStateOf(false) }

    RequestNotificationPermission()

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxViewModel.Event.ModerationDone -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(event.action.messageRes()),
                        actionLabel = if (event.undoable) {
                            resources.getString(R.string.action_undo)
                        } else {
                            null
                        },
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.commentId, event.previousStatus)
                    }
                }

                is InboxViewModel.Event.Emptied -> {
                    val done = resources.getQuantityString(
                        R.plurals.empty_done,
                        event.deleted,
                        event.deleted,
                    )
                    val message = if (event.remaining > 0) {
                        done + " · " + resources.getQuantityString(
                            R.plurals.empty_remaining,
                            event.remaining,
                            event.remaining,
                        )
                    } else {
                        done
                    }
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                }

                is InboxViewModel.Event.Failed -> {
                    snackbarHostState.showSnackbar(
                        message = ErrorTexts.message(resources, event.error),
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                }
            }
        }
    }

    if (showEmptyDialog) {
        val filter = state.filter
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text(stringResource(R.string.dialog_empty_title)) },
            text = {
                Text(
                    stringResource(
                        if (filter == CommentFilter.SPAM) {
                            R.string.dialog_empty_spam_message
                        } else {
                            R.string.dialog_empty_trash_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyDialog = false
                    viewModel.emptyCurrentFilter()
                }) {
                    Text(stringResource(R.string.action_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    InboxScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onEmptyRequest = { showEmptyDialog = true },
        onFilterSelected = viewModel::setFilter,
        onOpenComment = onOpenComment,
        onModerate = viewModel::moderate,
        onLoadMore = viewModel::loadMore,
        onOpenSettings = onOpenSettings,
        onSwitchSite = viewModel::switchTo,
        onAddSite = onAddSite,
        onReauthenticate = onReauthenticate,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChange = viewModel::setSearchQuery,
        modifier = modifier,
    )
}

/**
 * Der Posteingang ohne eigenen Zustand.
 *
 * Getrennt vom [InboxScreen], damit die Oberfläche ohne ViewModel und ohne
 * Dependency Injection geprüft werden kann - die UI-Tests setzen genau hier an.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxScreenContent(
    state: InboxUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onEmptyRequest: () -> Unit,
    onFilterSelected: (CommentFilter) -> Unit,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onReauthenticate: () -> Unit,
    onSwitchSite: (String) -> Unit = {},
    onAddSite: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Die Kopfleiste weicht beim Scrollen nach oben und kommt beim
    // Zurueckscrollen sofort wieder - auf einer langen Liste gewinnt das eine
    // ganze Kartenhoehe an sichtbarem Inhalt.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var showSiteSwitcher by rememberSaveable { mutableStateOf(false) }

    if (showSiteSwitcher) {
        SiteSwitcherSheet(
            instances = state.instances,
            activeId = state.instance?.id,
            pendingPerInstance = state.pendingPerInstance,
            onSelect = { instanceId ->
                showSiteSwitcher = false
                onSwitchSite(instanceId)
            },
            onAddSite = {
                showSiteSwitcher = false
                onAddSite()
            },
            onDismiss = { showSiteSwitcher = false },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (state.searchActive) {
                        IconButton(onClick = onCloseSearch) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(
                                    R.string.action_search_close,
                                ),
                            )
                        }
                    }
                },
                title = {
                    if (state.searchActive) {
                        SearchField(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                        )
                    } else {
                        BlogTitleSwitcher(
                            instance = state.instance,
                            instanceCount = state.instances.size,
                            onClick = { showSiteSwitcher = true },
                        )
                    }
                },
                actions = {
                    // Waehrend der Suche bleibt die Leiste dem Suchfeld
                    // ueberlassen; die uebrigen Knoepfe passen ohnehin nicht
                    // mehr daneben.
                    if (!state.searchActive) {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search),
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            enabled = !state.isRefreshing,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SessionInvalidBanner(
                visible = state.sessionInvalid,
                onReauthenticate = onReauthenticate,
            )
            OfflineBanner(isOffline = state.isOffline, lastSync = state.lastSync)
            FilterRow(
                selected = state.filter,
                counts = state.counts,
                onSelect = onFilterSelected,
            )

            PullToRefreshBox(
                // Waehrend des Erstaufbaus nicht: Dort stehen bereits
                // Platzhalterkarten, und beides zusammen ergaebe zwei
                // Ladeanzeigen uebereinander.
                isRefreshing = state.isRefreshing && !state.isInitialLoad,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                InboxContent(
                    state = state,
                    onOpenComment = onOpenComment,
                    onModerate = onModerate,
                    onLoadMore = onLoadMore,
                    onRetry = onRefresh,
                    onEmptyRequest = onEmptyRequest,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InboxContent(
    state: InboxUiState,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onEmptyRequest: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Welche zusammengefassten Gruppen aufgeklappt sind. Ueber den
    // Zustandswechsel hinweg gemerkt, damit ein Drehen des Geraets das
    // Aufgeklappte nicht wieder zuklappt.
    var expandedGroups by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }

    // Nachladen, sobald das Ende der Liste in Sicht kommt.
    val rows = state.rows
    val shouldLoadMore by remember(rows.size, state.canLoadMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && lastVisible >= rows.size - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    when {
        state.isInitialLoad && state.comments.isEmpty() && state.error == null ->
            CommentSkeletonList()

        state.comments.isEmpty() && state.error != null ->
            ErrorState(
                message = ErrorTexts.message(LocalResources.current, state.error),
                onRetry = onRetry,
            )

        // Waehrend der Suche sagt der leere Zustand etwas anderes: Nicht
        // "hier ist nichts", sondern "dazu wurde nichts gefunden".
        state.comments.isEmpty() && state.searchActive -> EmptyState(
            icon = Icons.Default.Search,
            title = if (state.searchDone) {
                stringResource(R.string.search_empty_title)
            } else {
                stringResource(R.string.search_hint)
            },
            description = if (state.searchDone) {
                stringResource(R.string.search_empty_description, state.searchQuery.trim())
            } else {
                stringResource(R.string.search_hint_short)
            },
        )

        state.comments.isEmpty() -> EmptyState(
            icon = Icons.Default.Email,
            title = stringResource(state.filter.emptyTitleRes()),
            description = stringResource(state.filter.emptyDescriptionRes()),
        )

        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Leeren steht als beschriftete Zeile über der Liste, nicht als
            // Symbol in der Kopfleiste: Dort fand es niemand, weil es nur in
            // zwei Filtern auftauchte und nichts darüber sagte, was es tut.
            if (!state.searchActive && state.filter.emptiable) {
                item(key = "empty-folder") {
                    EmptyFolderBar(
                        filter = state.filter,
                        count = state.counts[state.filter] ?: state.comments.size,
                        enabled = state.moderationEnabled && !state.isRefreshing,
                        onEmpty = onEmptyRequest,
                    )
                }
            }

            items(rows, key = { it.key }) { row ->
                when (row) {
                    is TimelineRow.Single -> InboxEntry(
                        entry = row.entry,
                        state = state,
                        onOpenComment = onOpenComment,
                        onModerate = onModerate,
                    )

                    is TimelineRow.TeamGroup -> TeamGroupRow(
                        group = row,
                        expanded = row.key in expandedGroups,
                        onToggle = {
                            expandedGroups = if (row.key in expandedGroups) {
                                expandedGroups - row.key
                            } else {
                                expandedGroups + row.key
                            }
                        },
                        state = state,
                        onOpenComment = onOpenComment,
                        onModerate = onModerate,
                    )
                }
            }

            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Die Expressive-Ladeanzeige wandert durch eine Folge
                        // von Formen, statt einen Kreis zu drehen.
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

/**
 * Ein Kommentar an seinem Platz im Faden.
 *
 * Eigene Funktion, weil dieselbe Zeile an zwei Stellen steht: einzeln und
 * aufgeklappt innerhalb einer zusammengefassten Gruppe.
 */
@Composable
private fun InboxEntry(
    entry: ThreadEntry,
    state: InboxUiState,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
) {
    val comment = entry.comment
    val rolle = state.team.roleOf(comment.authorId)
    ThreadItem(
        entry = entry,
        signals = state.signals[comment.id] ?: CommentSignals(),
        teamRole = rolle,
        roleStyle = state.roleStyles.of(rolle?.slug.orEmpty()),
        showAvatar = state.showAvatars,
        actionsEnabled = state.moderationEnabled,
        onOpen = { onOpenComment(comment) },
        onModerate = { action -> onModerate(comment, action) },
        onReply = { onOpenComment(comment) },
    )
}

/**
 * Zusammengefasste Beitraege des Teams.
 *
 * Zugeklappt steht dort eine schmale Zeile mit Anzahl und Rollen - die
 * Kommentare sind nicht weg, sie nehmen nur keinen Platz weg. Aufgeklappt
 * stehen die Karten darunter wie sonst auch.
 */
@Composable
private fun TeamGroupRow(
    group: TimelineRow.TeamGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    state: InboxUiState,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
) {
    // Das eigene Konto traegt keinen Rollennamen - es waere sonst ein
    // Komma ohne Wort dahinter.
    val rollen = group.roles.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
        .ifBlank { stringResource(R.string.comment_from_team) }
    val beschriftung = pluralStringResource(
        R.plurals.inbox_team_group,
        group.count,
        group.count,
        rollen,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp * group.depth),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) {
                            R.string.inbox_team_group_collapse
                        } else {
                            R.string.inbox_team_group_expand
                        },
                    ),
                    onClick = onToggle,
                ),
        ) {
            // 14 dp senkrecht: Mit dem 20-dp-Zeichen wird die Zeile damit
            // 48 dp hoch und bleibt bequem zu treffen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = beschriftung,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Ohne Beschriftung: Was das Antippen tut, sagt bereits die
                // Zeile selbst - sie ist die Schaltflaeche, das Zeichen nur
                // ihr Zeiger. Zweimal vorgelesen waere es nur im Weg.
                //
                // Gedreht statt ausgetauscht: Zwei Zeichen, die einander
                // ersetzen, springen. Dasselbe Zeichen, das sich dreht, zeigt
                // den Weg von zu nach auf - und traegt dabei die Feder, die
                // auch die Gruppe darunter aufschiebt.
                val drehung by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = Bewegung.raeumlich(),
                    label = "Gruppenpfeil",
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(drehung),
                )
            }
        }

        // Die eingeklappten Beitraege schieben sich auf, statt zu erscheinen.
        // Vorher wechselte die Liste ohne Uebergang, und es war nicht zu
        // sehen, woher die neuen Karten kamen - gerade bei einer Gruppe in der
        // Mitte einer langen Liste.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Bewegung.raeumlich()) + fadeIn(Bewegung.effekt()),
            exit = shrinkVertically(Bewegung.raeumlich()) + fadeOut(Bewegung.schnellerEffekt()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                group.entries.forEach { entry ->
                    InboxEntry(
                        entry = entry,
                        state = state,
                        onOpenComment = onOpenComment,
                        onModerate = onModerate,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: CommentFilter,
    counts: Map<CommentFilter, Int>,
    onSelect: (CommentFilter) -> Unit,
) {
    val filter = CommentFilter.entries.toList()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        // Eng gesetzt: Die Marken bilden eine zusammenhaengende Gruppe. Mit
        // dem vorherigen Abstand von 8 dp standen dort fuenf einzelne Dinge,
        // zwischen denen nichts erkennbar zusammengehoerte.
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(filter) { index, eintrag ->
            val name = stringResource(eintrag.labelRes())
            val anzahl = counts[eintrag]
            val gewaehlt = eintrag == selected

            ToggleButton(
                checked = gewaehlt,
                onCheckedChange = { onSelect(eintrag) },
                shapes = gruppenFormen(index = index, anzahl = filter.size),
                // Der Mittelpunkt trennt fuers Auge; vorgelesen ergibt er
                // nichts. Fuer Bildschirmleser steht die Zahl deshalb
                // ausgeschrieben da.
                modifier = if (anzahl == null) {
                    Modifier
                } else {
                    val gesprochen = pluralStringResource(
                        R.plurals.cd_filter_with_count,
                        anzahl,
                        name,
                        anzahl,
                    )
                    Modifier.semantics { contentDescription = gesprochen }
                },
            ) {
                // Ist die Zahl nicht bekannt, steht dort nur der Name -
                // eine erfundene Null waere schlechter als keine Angabe.
                Text(anzahl?.let { "$name · $it" } ?: name)
            }
        }
    }
}

/**
 * Die drei Formen eines Schalters in der verbundenen Filterleiste.
 *
 * Material 3 Expressive fasst verwandte Schalter zu einer Gruppe zusammen: Sie
 * ist aussen rund und innen fast gerade, und dadurch als ein Ding erkennbar.
 * Gedrueckt und ausgewaehlt traegt ein Schalter jeweils eine eigene, rundere
 * Form - [ToggleButton] wandelt zwischen ihnen, statt sie zu tauschen.
 *
 * Die Form und nicht nur die Farbe: Wer Farben schlecht unterscheidet, sieht
 * an einer eingefaerbten Marke nichts. An einer runden schon. Und weil der
 * Wandel animiert ist, ist auch die Richtung der Aenderung ablesbar - welcher
 * Schalter gerade aufgeht und welcher zugeht.
 *
 * Die Leiste scrollt waagerecht. Aussen heisst deshalb "erster und letzter
 * Eintrag der Liste", nicht "am Bildschirmrand" - sonst aenderte sich die Form
 * beim Scrollen.
 */
@Composable
private fun gruppenFormen(index: Int, anzahl: Int): ToggleButtonShapes {
    val aussen = CommentatorFormen.GruppeAussen
    val innen = CommentatorFormen.GruppeInnen
    val erster = index == 0
    val letzter = index == anzahl - 1

    return ToggleButtonShapes(
        shape = RoundedCornerShape(
            topStart = if (erster) aussen else innen,
            bottomStart = if (erster) aussen else innen,
            topEnd = if (letzter) aussen else innen,
            bottomEnd = if (letzter) aussen else innen,
        ),
        // Unter dem Finger geht der Schalter auf. Das ist die Rueckmeldung,
        // die sonst allein die Farbe traegt.
        pressedShape = RoundedCornerShape(CommentatorFormen.GruppeGedrueckt),
        // Der gewaehlte loest sich aus der Gruppe: rundum die volle Rundung.
        checkedShape = RoundedCornerShape(CommentatorFormen.GruppeGewaehlt),
    )
}

/**
 * Fragt die Benachrichtigungsberechtigung ab Android 13 an - einmal je
 * Sitzung und nur, wenn sie noch nicht erteilt ist. Eine Ablehnung führt
 * nicht zu wiederholtem Nachfragen.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    // Hier wird keine Ressource nachgeschlagen, sondern der Berechtigungsstand
    // geprüft - dafür ist der Context der richtige Weg.
    val context = LocalContext.current
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )

    LaunchedEffect(alreadyAsked) {
        if (alreadyAsked) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        alreadyAsked = true
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun ModerationAction.messageRes(): Int = when (this) {
    ModerationAction.Approve -> R.string.snackbar_approved
    ModerationAction.Hold -> R.string.snackbar_held
    ModerationAction.MarkAsSpam -> R.string.snackbar_spam
    is ModerationAction.Delete ->
        if (permanent) R.string.snackbar_deleted else R.string.snackbar_trashed
}

fun CommentFilter.labelRes(): Int = when (this) {
    CommentFilter.ALL -> R.string.filter_all
    CommentFilter.PENDING -> R.string.filter_pending
    CommentFilter.UNANSWERED -> R.string.filter_unanswered
    CommentFilter.APPROVED -> R.string.filter_approved
    CommentFilter.SPAM -> R.string.filter_spam
    CommentFilter.TRASH -> R.string.filter_trash
}

private fun CommentFilter.emptyTitleRes(): Int = when (this) {
    CommentFilter.PENDING -> R.string.empty_pending_title
    CommentFilter.UNANSWERED -> R.string.empty_unanswered_title
    CommentFilter.SPAM -> R.string.empty_spam_title
    CommentFilter.TRASH -> R.string.empty_trash_title
    else -> R.string.empty_generic_title
}

private fun CommentFilter.emptyDescriptionRes(): Int = when (this) {
    CommentFilter.PENDING -> R.string.empty_pending_description
    CommentFilter.UNANSWERED -> R.string.empty_unanswered_description
    else -> R.string.empty_generic_description
}

/**
 * Das Suchfeld in der Kopfleiste.
 *
 * Ohne eigenen Rahmen und ohne Hintergrund: Es sitzt bereits in der
 * Kopfleiste, ein zweiter Rahmen darin wirkte wie ein Feld im Feld. Der Fokus
 * springt beim Öffnen hinein, damit die Tastatur ohne weiteren Tipp kommt.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val fokus = remember { FocusRequester() }

    LaunchedEffect(Unit) { fokus.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fokus),
    )
}

private val CommentFilter.emptiable: Boolean
    get() = this == CommentFilter.SPAM || this == CommentFilter.TRASH

/** „12 Kommentare im Spam – Spam leeren“, über der Liste von Spam und Papierkorb. */
@Composable
private fun EmptyFolderBar(
    filter: CommentFilter,
    count: Int,
    enabled: Boolean,
    onEmpty: () -> Unit,
) {
    val (plural, action) = when (filter) {
        CommentFilter.TRASH -> R.plurals.inbox_trash_count to R.string.action_empty_trash
        else -> R.plurals.inbox_spam_count to R.string.action_empty_spam
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = pluralStringResource(plural, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEmpty, enabled = enabled && count > 0) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(action))
            }
        }
    }
}

