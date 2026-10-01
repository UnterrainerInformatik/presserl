package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaFilter
import info.unterrainer.presserl.admin.api.MediaUsageDto
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.auth.AuthClient
import info.unterrainer.presserl.admin.auth.AuthState
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.deletion_requested_marker
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.log_in_again
import info.unterrainer.presserl.admin.resources.log_out
import info.unterrainer.presserl.admin.resources.nav_accounts
import info.unterrainer.presserl.admin.resources.nav_articles
import info.unterrainer.presserl.admin.resources.nav_images
import info.unterrainer.presserl.admin.resources.nav_issues
import info.unterrainer.presserl.admin.resources.nav_newspaper
import info.unterrainer.presserl.admin.resources.nav_sections
import info.unterrainer.presserl.admin.resources.login_failed
import info.unterrainer.presserl.admin.resources.no_roles
import info.unterrainer.presserl.admin.resources.no_writing_role
import info.unterrainer.presserl.admin.resources.something_went_wrong
import info.unterrainer.presserl.admin.resources.try_again
import info.unterrainer.presserl.admin.ui.account.AccountListScreen
import info.unterrainer.presserl.admin.ui.account.AccountSlipScreen
import info.unterrainer.presserl.admin.ui.account.EditRolesScreen
import info.unterrainer.presserl.admin.ui.account.MyAccountScreen
import info.unterrainer.presserl.admin.ui.account.NewAccountScreen
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import info.unterrainer.presserl.admin.ui.editor.EditorScreen
import info.unterrainer.presserl.admin.ui.issue.IssueDetailScreen
import info.unterrainer.presserl.admin.ui.issue.IssueListScreen
import info.unterrainer.presserl.admin.ui.media.MediaDetailScreen
import info.unterrainer.presserl.admin.ui.media.MediaEditScreen
import info.unterrainer.presserl.admin.ui.media.MediaGridScreen
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import info.unterrainer.presserl.admin.ui.media.mediaGridOf
import info.unterrainer.presserl.admin.ui.newspaper.NewspaperScreen
import info.unterrainer.presserl.admin.ui.section.SectionFormScreen
import info.unterrainer.presserl.admin.ui.section.SectionListScreen
import info.unterrainer.presserl.admin.ui.section.SectionMembersScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

sealed interface Screen {
    data object Loading : Screen
    data class LoginFailed(val reason: String) : Screen
    /** [spellCheck]: the installation offers the spell check (`GET /api/client-config`). */
    data class LoggedIn(val newspaper: NewspaperDto, val me: MeDto, val spellCheck: Boolean = false) : Screen {
        val navEntries: List<NavEntry> get() = navEntries(me.allowedActions)

        /** The view opened after login; without one the notice about the missing writing role is shown. */
        val start: NavEntry? get() = startEntry(me.allowedActions)
    }
    data class Error(val message: String) : Screen
}

/** Screens below the header; the browser URL stays `/admin/` (design D6). */
sealed interface Route {
    data class ArticleList(val tab: ListTab) : Route
    data class Editor(val articleId: Long) : Route
    data class Revisions(val articleId: Long) : Route
    data class Revision(val articleId: Long, val number: Int) : Route
    /** The comparison of revision [number] with the one before it. */
    data class RevisionDiff(val articleId: Long, val number: Int) : Route
    data object Sections : Route
    /** "New section" when [section] is `null`, preselecting [defaultColor]; otherwise "Edit". */
    data class SectionForm(val section: SectionDto?, val defaultColor: String) : Route
    data class SectionMembers(val section: SectionDto) : Route
    data object Issues : Route
    data class IssueDetail(val issueId: Long) : Route
    data object Accounts : Route
    /** [mayAssignSectionlessReporter] offers "Redakteur (ohne Ressort)". */
    data class NewAccount(
        val assignableRoles: List<String>,
        val sections: List<SectionDto>,
        val mayAssignSectionlessReporter: Boolean = false,
    ) : Route
    data class EditRoles(
        val account: AccountDto,
        val assignableRoles: List<String>,
        val sections: List<SectionDto>,
        val mayAssignSectionlessReporter: Boolean = false,
    ) : Route
    /** Holds the generated password; leaving the slip drops it. */
    data class AccountSlip(val created: CreatedAccountDto) : Route
    data object Newspaper : Route
    data object Media : Route
    data class MediaDetail(val mediaId: Long) : Route
    data class MediaEdit(val media: MediaDto, val usage: MediaUsageDto) : Route
    /** Opened from the header's user line, for every logged-in user. */
    data object MyAccount : Route
}

/** The deployment's upload limit among the newspaper settings (e.g. `10M`). */
private const val MAX_UPLOAD_SIZE = "media.max-size"

/** [siteUrl] is the origin of the reader, used for "View in reader" and on the account slip. */
@Composable
fun App(auth: AuthClient, api: ApiClient, siteUrl: String, slipPrinter: SlipPrinter) {
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }

    LaunchedEffect(Unit) {
        screen = try {
            when (val state = auth.start()) {
                AuthState.Redirecting -> Screen.Loading
                is AuthState.LoginFailed -> Screen.LoginFailed(state.reason)
                AuthState.LoggedIn -> coroutineScope {
                    // Without the configuration the app works as before, only unchecked
                    val spellCheck = async { attempt({}) { api.clientConfig().spellCheck } ?: false }
                    Screen.LoggedIn(api.newspaper(), api.me(), spellCheck.await())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Browser fetch failures surface as kotlin.Error, not Exception
            Screen.Error(e.message ?: e.toString())
        }
    }

    PresserlTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = screen) {
                Screen.Loading -> Message { Text(stringResource(Res.string.loading)) }
                is Screen.LoginFailed -> Message {
                    Text(stringResource(Res.string.login_failed, current.reason), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = auth::login) { Text(stringResource(Res.string.try_again)) }
                }
                is Screen.Error -> Message {
                    Text(stringResource(Res.string.something_went_wrong, current.message))
                    Button(onClick = auth::login) { Text(stringResource(Res.string.log_in_again)) }
                }
                is Screen.LoggedIn -> LoggedIn(
                    current,
                    api,
                    siteUrl,
                    slipPrinter,
                    onLogout = auth::logout,
                    onDeletionRequest = { requestedAt ->
                        (screen as? Screen.LoggedIn)?.let { screen = it.copy(me = it.me.copy(deletionRequestedAt = requestedAt)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun Message(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun LoggedIn(
    screen: Screen.LoggedIn,
    api: ApiClient,
    siteUrl: String,
    slipPrinter: SlipPrinter,
    onLogout: () -> Unit,
    onDeletionRequest: (String?) -> Unit,
) {
    // empty without a writing role or images: the notice is shown instead of a route
    var stack by remember { mutableStateOf(startStack(screen.start)) }
    // the last review-queue response, kept while the editor is open (design D3/D4)
    var queue by remember { mutableStateOf<List<ArticleSummaryDto>?>(null) }
    // the order of the article lists, kept while the app is open
    var sort by remember { mutableStateOf(ArticleSort.CHANGED) }
    // kept while detail and edit views are open, so the grid keeps its pages and thumbnails
    val newMediaGrid = { mediaGridOf(api) }
    var mediaGrid by remember { mutableStateOf(newMediaGrid()) }
    val mediaThumbnails = remember { Thumbnails { api.mediaRendition(it, "thumbnail") } }
    val push = { route: Route -> stack = stack + route }
    val back = { stack = stack.dropLast(1) }
    // screens with their own leave action (editor, image details and edit) register an inner handler
    val afterBack = stackAfterBack(stack, screen.start)
    SystemBackHandler(enabled = afterBack != null) { afterBack?.let { stack = it } }

    Column(Modifier.fillMaxSize()) {
        Header(
            screen,
            entry = when (stack.firstOrNull()) {
                null -> null
                Route.Accounts -> NavEntry.ACCOUNTS
                Route.Sections -> NavEntry.SECTIONS
                Route.Issues -> NavEntry.ISSUES
                Route.Newspaper -> NavEntry.NEWSPAPER
                Route.Media -> NavEntry.IMAGES
                Route.MyAccount -> null
                else -> NavEntry.ARTICLES
            },
            onEntry = { entry ->
                if (entry == NavEntry.IMAGES) mediaGrid = newMediaGrid()
                stack = listOf(
                    when (entry) {
                        NavEntry.ARTICLES -> Route.ArticleList(ListTab.MINE)
                        NavEntry.IMAGES -> Route.Media
                        NavEntry.SECTIONS -> Route.Sections
                        NavEntry.ISSUES -> Route.Issues
                        NavEntry.ACCOUNTS -> Route.Accounts
                        NavEntry.NEWSPAPER -> Route.Newspaper
                    },
                )
            },
            onLogout = onLogout,
            onMyAccount = { if (stack.lastOrNull() != Route.MyAccount) push(Route.MyAccount) },
        )
        HorizontalDivider()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (val route = stack.lastOrNull()) {
                    null -> Text(stringResource(Res.string.no_writing_role), style = MaterialTheme.typography.titleMedium)
                    is Route.ArticleList -> ArticleListScreen(
                        api,
                        route.tab,
                        queue,
                        onQueue = { queue = it },
                        onTab = { stack = stack.dropLast(1) + Route.ArticleList(it) },
                        onOpen = { push(Route.Editor(it)) },
                        sort = sort,
                        onSort = { sort = it },
                    )
                    is Route.Editor -> key(route) {
                        EditorScreen(
                            api,
                            route.articleId,
                            readerUrl = { "$siteUrl/articles/$it" },
                            spellCheck = screen.spellCheck,
                            onBack = back,
                            onRevisions = { push(Route.Revisions(route.articleId)) },
                            username = screen.me.username,
                            onShowChanges = { push(Route.RevisionDiff(route.articleId, it)) },
                            roles = screen.me.roles,
                        )
                    }
                    is Route.Revisions -> RevisionsScreen(
                        api,
                        route.articleId,
                        onBack = back,
                        onOpen = { push(Route.Revision(route.articleId, it)) },
                        onChanges = { push(Route.RevisionDiff(route.articleId, it)) },
                    )
                    is Route.Revision -> key(route) { RevisionScreen(api, route.articleId, route.number, onBack = back) }
                    is Route.RevisionDiff -> key(route) { RevisionDiffScreen(api, route.articleId, route.number, onBack = back) }
                    Route.Sections -> SectionListScreen(
                        api,
                        onNew = { push(Route.SectionForm(null, it)) },
                        onEdit = { push(Route.SectionForm(it, it.color)) },
                        onOpen = { push(Route.SectionMembers(it)) },
                    )
                    is Route.SectionForm -> key(route) {
                        SectionFormScreen(
                            api,
                            route.section,
                            route.defaultColor,
                            spellCheck = screen.spellCheck,
                            onBack = back,
                            onSaved = { stack = listOf(Route.Sections) },
                        )
                    }
                    is Route.SectionMembers -> key(route) { SectionMembersScreen(api, route.section, onBack = back) }
                    Route.Issues -> IssueListScreen(api, onOpen = { push(Route.IssueDetail(it)) })
                    is Route.IssueDetail -> key(route) {
                        IssueDetailScreen(api, route.issueId, siteUrl, onBack = back, onDeleted = { stack = listOf(Route.Issues) })
                    }
                    Route.Accounts -> AccountListScreen(
                        api,
                        onNew = { roles, sections, marker -> push(Route.NewAccount(roles, sections, marker)) },
                        onEditRoles = { account, roles, sections, marker -> push(Route.EditRoles(account, roles, sections, marker)) },
                        onReset = { push(Route.AccountSlip(it)) },
                    )
                    is Route.NewAccount -> key(route) {
                        NewAccountScreen(
                            api,
                            route.assignableRoles,
                            route.sections,
                            route.mayAssignSectionlessReporter,
                            onBack = back,
                            onCreated = { stack = stack.dropLast(1) + Route.AccountSlip(it) },
                        )
                    }
                    is Route.EditRoles -> key(route) {
                        EditRolesScreen(
                            api,
                            route.account,
                            route.assignableRoles,
                            route.sections,
                            route.mayAssignSectionlessReporter,
                            onBack = back,
                            onSaved = { stack = listOf(Route.Accounts) },
                        )
                    }
                    is Route.AccountSlip -> AccountSlipScreen(
                        screen.newspaper.name,
                        siteUrl,
                        route.created,
                        slipPrinter,
                        onDone = { stack = listOf(Route.Accounts) },
                    )
                    Route.Newspaper -> NewspaperScreen(
                        api,
                        spellCheck = screen.spellCheck,
                        mayConfigureSpellCheck = NewspaperAction.CONFIGURE_SPELL_CHECK in screen.me.allowedActions,
                        mayConfigureCorrections = NewspaperAction.CONFIGURE_CORRECTIONS in screen.me.allowedActions,
                    )
                    Route.Media -> MediaGridScreen(
                        api,
                        mediaGrid,
                        mediaThumbnails,
                        maxUploadSize = screen.newspaper.settings[MAX_UPLOAD_SIZE]?.jsonPrimitive?.contentOrNull,
                        onOpen = { push(Route.MediaDetail(it)) },
                    )
                    is Route.MediaDetail -> key(route) {
                        MediaDetailScreen(
                            api,
                            route.mediaId,
                            onBack = back,
                            onEdit = { media, usage -> push(Route.MediaEdit(media, usage)) },
                            onOpenArticle = { push(Route.Editor(it)) },
                            onSaved = { mediaGrid.replace(it) },
                            onTag = { tag ->
                                mediaGrid.showFilter(MediaFilter(tags = listOf(tag)))
                                stack = listOf(Route.Media)
                            },
                        )
                    }
                    Route.MyAccount -> MyAccountScreen(api, screen.me, onBack = back, onChanged = onDeletionRequest)
                    is Route.MediaEdit -> key(route) {
                        MediaEditScreen(
                            api,
                            route.media,
                            route.usage,
                            onBack = back,
                            onSaved = { saved ->
                                mediaThumbnails.invalidate(saved.id)
                                mediaGrid.replace(saved)
                                back()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Below this width the header takes two rows so the names never get squeezed. */
private val NARROW_HEADER = 720.dp

@Composable
private fun Header(
    screen: Screen.LoggedIn,
    entry: NavEntry?,
    onEntry: (NavEntry) -> Unit,
    onLogout: () -> Unit,
    onMyAccount: () -> Unit,
) {
    val identity = @Composable { modifier: Modifier ->
        Column(modifier) {
            Text(screen.newspaper.name, style = MaterialTheme.typography.titleLarge)
            val roles = screen.me.roles.map { roleText(it) } +
                (if (screen.me.sectionlessReporter) listOf(stringResource(sectionlessReporterLabel)) else emptyList()) +
                screen.me.sectionRoles.map { sectionRoleText(it.role) + " · " + it.sectionName }
            val userLine = listOfNotNull(
                screen.me.displayName,
                if (roles.isEmpty()) stringResource(Res.string.no_roles) else roles.joinToString(", "),
                stringResource(Res.string.deletion_requested_marker).takeIf { screen.me.deletionRequestedAt != null },
            )
            // the user line opens "My account" for everyone, whatever the navigation offers
            TextButton(
                onClick = onMyAccount,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(userLine.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    val navigation = @Composable {
        for (navEntry in screen.navEntries) {
            val label = when (navEntry) {
                NavEntry.ARTICLES -> Res.string.nav_articles
                NavEntry.IMAGES -> Res.string.nav_images
                NavEntry.SECTIONS -> Res.string.nav_sections
                NavEntry.ISSUES -> Res.string.nav_issues
                NavEntry.ACCOUNTS -> Res.string.nav_accounts
                NavEntry.NEWSPAPER -> Res.string.nav_newspaper
            }
            NavButton(stringResource(label), entry == navEntry) { onEntry(navEntry) }
        }
    }
    val logout = @Composable { OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.log_out)) } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (maxWidth >= NARROW_HEADER) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                identity(Modifier.weight(1f))
                navigation()
                logout()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    identity(Modifier.weight(1f))
                    logout()
                }
                if (screen.navEntries.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        navigation()
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}
