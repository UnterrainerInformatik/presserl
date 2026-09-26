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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.auth.AuthClient
import info.unterrainer.presserl.admin.auth.AuthState
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.log_in_again
import info.unterrainer.presserl.admin.resources.log_out
import info.unterrainer.presserl.admin.resources.login_failed
import info.unterrainer.presserl.admin.resources.no_roles
import info.unterrainer.presserl.admin.resources.something_went_wrong
import info.unterrainer.presserl.admin.resources.try_again
import info.unterrainer.presserl.admin.ui.editor.EditorScreen
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

sealed interface Screen {
    data object Loading : Screen
    data class LoginFailed(val reason: String) : Screen
    data class LoggedIn(val newspaper: NewspaperDto, val me: MeDto) : Screen
    data class Error(val message: String) : Screen
}

/** Screens below the header; the browser URL stays `/admin/` (design D6). */
sealed interface Route {
    data class ArticleList(val tab: ListTab) : Route
    data class Editor(val articleId: Long) : Route
    data class Revisions(val articleId: Long) : Route
    data class Revision(val articleId: Long, val number: Int) : Route
}

/** [siteUrl] is the origin of the reader, used for "View in reader". */
@Composable
fun App(auth: AuthClient, api: ApiClient, siteUrl: String) {
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
                is Screen.LoggedIn -> LoggedIn(current, api, siteUrl, onLogout = auth::logout)
            }
        }
    }
}

@Composable
private fun Message(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun LoggedIn(screen: Screen.LoggedIn, api: ApiClient, siteUrl: String, onLogout: () -> Unit) {
    var stack by remember { mutableStateOf(listOf<Route>(Route.ArticleList(ListTab.MINE))) }
    val push = { route: Route -> stack = stack + route }
    val back = { stack = stack.dropLast(1) }

    Column(Modifier.fillMaxSize()) {
        Header(screen, onLogout)
        HorizontalDivider()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (val route = stack.last()) {
                    is Route.ArticleList -> ArticleListScreen(
                        api,
                        route.tab,
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
                }
            }
        }
    }
}

@Composable
private fun Header(screen: Screen.LoggedIn, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(screen.newspaper.name, style = MaterialTheme.typography.titleLarge)
            val roles = screen.me.roles.map { roleText(it) }
            Text(
                screen.me.displayName + " · " + (if (roles.isEmpty()) stringResource(Res.string.no_roles) else roles.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.log_out)) }
    }
}
