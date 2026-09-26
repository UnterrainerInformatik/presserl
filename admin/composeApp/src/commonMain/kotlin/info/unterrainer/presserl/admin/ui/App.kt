package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.auth.AuthClient
import info.unterrainer.presserl.admin.auth.AuthState
import kotlinx.coroutines.CancellationException

sealed interface Screen {
    data object Loading : Screen
    data class LoginFailed(val reason: String) : Screen
    data class LoggedIn(val newspaper: NewspaperDto, val me: MeDto) : Screen
    data class Error(val message: String) : Screen
}

@Composable
fun App(auth: AuthClient, api: ApiClient) {
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
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = screen) {
                    Screen.Loading -> Text("Loading…")
                    is Screen.LoginFailed -> {
                        Text("Login failed: ${current.reason}", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = auth::login) { Text("Try again") }
                    }
                    is Screen.Error -> {
                        Text("Something went wrong: ${current.message}")
                        Button(onClick = auth::login) { Text("Log in again") }
                    }
                    is Screen.LoggedIn -> LoggedIn(current, onLogout = auth::logout)
                }
            }
        }
    }
}

@Composable
private fun LoggedIn(screen: Screen.LoggedIn, onLogout: () -> Unit) {
    Text(screen.newspaper.name, style = MaterialTheme.typography.headlineLarge)
    Text("Logged in as ${screen.me.displayName}", style = MaterialTheme.typography.bodyLarge)
    val roles = screen.me.roles.map(::roleLabel)
    Text(if (roles.isEmpty()) "No newspaper roles" else "Roles: ${roles.joinToString(", ")}")
    OutlinedButton(onClick = onLogout) { Text("Log out") }
}
