package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.auth.AuthClient
import info.unterrainer.presserl.admin.auth.AuthState
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.log_in_again
import info.unterrainer.presserl.admin.resources.log_out
import info.unterrainer.presserl.admin.resources.nav_accounts
import info.unterrainer.presserl.admin.resources.nav_articles
import info.unterrainer.presserl.admin.resources.nav_sections
import info.unterrainer.presserl.admin.resources.login_failed
import info.unterrainer.presserl.admin.resources.no_roles
import info.unterrainer.presserl.admin.resources.no_writing_role
import info.unterrainer.presserl.admin.resources.something_went_wrong
import info.unterrainer.presserl.admin.resources.try_again
import info.unterrainer.presserl.admin.ui.account.AccountListScreen
import info.unterrainer.presserl.admin.ui.account.AccountSlipScreen
import info.unterrainer.presserl.admin.ui.account.EditRolesScreen
import info.unterrainer.presserl.admin.ui.account.NewAccountScreen
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import info.unterrainer.presserl.admin.ui.editor.EditorScreen
import info.unterrainer.presserl.admin.ui.section.SectionFormScreen
import info.unterrainer.presserl.admin.ui.section.SectionListScreen
import info.unterrainer.presserl.admin.ui.section.SectionMembersScreen
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

sealed interface Screen {
    data object Loading : Screen
    data class LoginFailed(val reason: String) : Screen
    data class LoggedIn(val newspaper: NewspaperDto, val me: MeDto) : Screen {
        val navEntries: List<NavEntry> get() = navEntries(me.allowedActions)

        /** Without it the article list is replaced by a notice. */
        val mayWriteArticles: Boolean get() = NewspaperAction.WRITE_ARTICLES in me.allowedActions
    }
    data class Error(val message: String) : Screen
}

/** Screens below the header; the browser URL stays `/admin/` (design D6). */
sealed interface Route {
    data class ArticleList(val tab: ListTab) : Route
    data class Editor(val articleId: Long) : Route
    data class Revisions(val articleId: Long) : Route
    data class Revision(val articleId: Long, val number: Int) : Route
    data object Sections : Route
    /** "New section" when [section] is `null`, preselecting [defaultColor]; otherwise "Edit". */
    data class SectionForm(val section: SectionDto?, val defaultColor: String) : Route
    data class SectionMembers(val section: SectionDto) : Route
    data object Accounts : Route
    data class NewAccount(val assignableRoles: List<String>, val sections: List<SectionDto>) : Route
    data class EditRoles(val account: AccountDto, val assignableRoles: List<String>, val sections: List<SectionDto>) : Route
    /** Holds the generated password; leaving the slip drops it. */
    data class AccountSlip(val created: CreatedAccountDto) : Route
}

/** [siteUrl] is the origin of the reader, used for "View in reader" and on the account slip. */
@Composable
fun App(auth: AuthClient, api: ApiClient, siteUrl: String, slipPrinter: SlipPrinter) {
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }

    LaunchedEffect(Unit) {
        screen = try {
            when (val state = auth.start()) {
                AuthState.Redirecting -> Screen.Loading
                is AuthState.LoginFailed -> Screen.LoginFailed(state.reason)
                AuthState.LoggedIn -> Screen.LoggedIn(api.newspaper(), api.me())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Browser fetch failures surface as kotlin.Error, not Exception
            Screen.Error(e.message ?: e.toString())
        }
    }

    MaterialTheme {
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
                is Screen.LoggedIn -> LoggedIn(current, api, siteUrl, slipPrinter, onLogout = auth::logout)
            }
        }
    }
}

@Composable
private fun Message(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun LoggedIn(screen: Screen.LoggedIn, api: ApiClient, siteUrl: String, slipPrinter: SlipPrinter, onLogout: () -> Unit) {
    // empty without a writing role: the notice is shown instead of a route
    var stack by remember {
        mutableStateOf(if (screen.mayWriteArticles) listOf<Route>(Route.ArticleList(ListTab.MINE)) else emptyList())
    }
    // the last review-queue response, kept while the editor is open (design D3/D4)
    var queue by remember { mutableStateOf<List<ArticleSummaryDto>?>(null) }
    val push = { route: Route -> stack = stack + route }
    val back = { stack = stack.dropLast(1) }

    Column(Modifier.fillMaxSize()) {
        Header(
            screen,
            entry = when (stack.firstOrNull()) {
                null -> null
                Route.Accounts -> NavEntry.ACCOUNTS
                Route.Sections -> NavEntry.SECTIONS
                else -> NavEntry.ARTICLES
            },
            onEntry = { entry ->
                stack = listOf(
                    when (entry) {
                        NavEntry.ARTICLES -> Route.ArticleList(ListTab.MINE)
                        NavEntry.SECTIONS -> Route.Sections
                        NavEntry.ACCOUNTS -> Route.Accounts
                    },
                )
            },
            onLogout = onLogout,
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
                    )
                    is Route.Editor -> key(route) {
                        EditorScreen(
                            api,
                            route.articleId,
                            readerUrl = { "$siteUrl/articles/$it" },
                            onBack = back,
                            onRevisions = { push(Route.Revisions(route.articleId)) },
                        )
                    }
                    is Route.Revisions -> RevisionsScreen(api, route.articleId, onBack = back, onOpen = { push(Route.Revision(route.articleId, it)) })
                    is Route.Revision -> key(route) { RevisionScreen(api, route.articleId, route.number, onBack = back) }
                    Route.Sections -> SectionListScreen(
                        api,
                        onNew = { push(Route.SectionForm(null, it)) },
                        onEdit = { push(Route.SectionForm(it, it.color)) },
                        onOpen = { push(Route.SectionMembers(it)) },
                    )
                    is Route.SectionForm -> key(route) {
                        SectionFormScreen(api, route.section, route.defaultColor, onBack = back, onSaved = { stack = listOf(Route.Sections) })
                    }
                    is Route.SectionMembers -> key(route) { SectionMembersScreen(api, route.section, onBack = back) }
                    Route.Accounts -> AccountListScreen(
                        api,
                        onNew = { roles, sections -> push(Route.NewAccount(roles, sections)) },
                        onEditRoles = { account, roles, sections -> push(Route.EditRoles(account, roles, sections)) },
                        onReset = { push(Route.AccountSlip(it)) },
                    )
                    is Route.NewAccount -> key(route) {
                        NewAccountScreen(
                            api,
                            route.assignableRoles,
                            route.sections,
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
                }
            }
        }
    }
}

@Composable
private fun Header(screen: Screen.LoggedIn, entry: NavEntry?, onEntry: (NavEntry) -> Unit, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(screen.newspaper.name, style = MaterialTheme.typography.titleLarge)
            val roles = screen.me.roles.map { roleText(it) } +
                screen.me.sectionRoles.map { sectionRoleText(it.role) + " · " + it.sectionName }
            Text(
                screen.me.displayName + " · " + (if (roles.isEmpty()) stringResource(Res.string.no_roles) else roles.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        for (navEntry in screen.navEntries) {
            val label = when (navEntry) {
                NavEntry.ARTICLES -> Res.string.nav_articles
                NavEntry.SECTIONS -> Res.string.nav_sections
                NavEntry.ACCOUNTS -> Res.string.nav_accounts
            }
            NavButton(stringResource(label), entry == navEntry) { onEntry(navEntry) }
        }
        OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.log_out)) }
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
