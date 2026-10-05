package uk.pixxula.pawse

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var watcher by mutableStateOf(WatcherState.OFF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PawseTheme {
                Home(
                    watcher = watcher,
                    openAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        watcher = when {
            !isWatcherSwitchedOn() -> WatcherState.OFF
            Watcher.running -> WatcherState.ON
            else -> WatcherState.STOPPED
        }
    }

    /** Whether the accessibility service is switched on in Settings. */
    private fun isWatcherSwitchedOn(): Boolean {
        val watcher = ComponentName(this, Watcher::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == watcher }
    }
}

private enum class WatcherState {
    OFF,
    ON,

    /** Switched on in Settings, but not running: Android gave up on it after a crash. */
    STOPPED,
}

@Composable
private fun Home(watcher: WatcherState, openAccessibility: () -> Unit) {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                stringResource(
                    when (watcher) {
                        WatcherState.ON -> R.string.home_watcher_on
                        WatcherState.OFF -> R.string.home_watcher_off
                        WatcherState.STOPPED -> R.string.home_watcher_stopped
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (watcher != WatcherState.ON) {
                Text(
                    stringResource(
                        if (watcher == WatcherState.OFF) R.string.home_watcher_why
                        else R.string.home_watcher_restart,
                    ),
                )
                Button(onClick = openAccessibility) {
                    Text(stringResource(R.string.home_open_accessibility))
                }
            }
            Text(
                stringResource(R.string.home_milestone, Blocking.hardCoded.joinToString()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PawseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
