package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the dark colour scheme is active; provided by [PresserlTheme]. */
val LocalDarkScheme = staticCompositionLocalOf { false }

/** The admin app's theme: the Material 3 baseline light or dark scheme, following the system preference by default. */
@Composable
fun PresserlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkScheme provides dark) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
    }
}
