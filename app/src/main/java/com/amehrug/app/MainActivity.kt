package com.amehrug.app

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.diagnostics.DiagnosticsScreen
import com.amehrug.app.model.AppSettings
import com.amehrug.app.ui.LockScreen
import com.amehrug.app.ui.SettingsScreen
import com.amehrug.app.ui.theme.AmehrugTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hides notes from screenshots, screen recording and the recents
        // preview. Always on for now. Roadmap task 19 turns it into a
        // setting that stays on by default.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            AmehrugTheme {
                AmehrugApp()
            }
        }
        if (savedInstanceState == null) {
            val sinceStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            Diagnostics.log.info("start", "process to activity $sinceStart ms")
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        AppGraph.lock.onBackground()
    }
}

private sealed interface SettingsState {
    data object Loading : SettingsState

    data object Failed : SettingsState

    data class Ready(val settings: AppSettings) : SettingsState
}

@Composable
private fun AmehrugApp() {
    // Opening the database happens here, off the main thread.
    val state by produceState<SettingsState>(SettingsState.Loading) {
        try {
            AppGraph.settings.settings.collect { settings ->
                AppGraph.lock.onSettings(settings)
                value = SettingsState.Ready(settings)
            }
        } catch (e: Exception) {
            Diagnostics.log.error("settings", "reading settings", e)
            value = SettingsState.Failed
        }
    }
    val locked by AppGraph.lock.locked.collectAsState()

    when (val current = state) {
        SettingsState.Loading -> MessageScreen(stringResource(R.string.opening))
        // Nothing is shown when the settings cannot be read: the lock state
        // is unknown, so the notes stay hidden.
        SettingsState.Failed -> MessageScreen(stringResource(R.string.open_failed))
        is SettingsState.Ready ->
            if (locked) {
                LockScreen(onUnlocked = { AppGraph.lock.unlock() })
            } else {
                AmehrugRoot(current.settings)
            }
    }
}

// Three screens for now. A navigation library arrives with the notes list,
// roadmap task 10.
private enum class Screen { HOME, SETTINGS, DIAGNOSTICS }

@Composable
private fun AmehrugRoot(settings: AppSettings) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    when (screen) {
        Screen.HOME -> HomeScreen(
            onSettings = { screen = Screen.SETTINGS },
            onDiagnostics = { screen = Screen.DIAGNOSTICS },
        )
        Screen.SETTINGS -> SettingsScreen(settings = settings, onBack = { screen = Screen.HOME })
        Screen.DIAGNOSTICS -> DiagnosticsScreen(onBack = { screen = Screen.HOME })
    }
}

@Composable
private fun MessageScreen(message: String) {
    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onSettings: () -> Unit, onDiagnostics: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = onSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                    TextButton(onClick = onDiagnostics) {
                        Text(stringResource(R.string.diagnostics_title))
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AmehrugTheme(dynamicColor = false) {
        HomeScreen(onSettings = {}, onDiagnostics = {})
    }
}
