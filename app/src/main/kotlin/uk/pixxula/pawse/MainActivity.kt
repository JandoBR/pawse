package uk.pixxula.pawse

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** How the accessibility service stands. */
enum class WatcherState {
    OFF,
    ON,

    /** Switched on in Settings, but not running: Android gave up on it after a crash. */
    STOPPED,
}

private enum class Screen { HOME, BLOCK_LIST }

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var session by mutableStateOf(SessionState())
    private var watcher by mutableStateOf(WatcherState.OFF)
    private val stateChanged: () -> Unit = { runOnUiThread { session = store.state } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        session = store.state
        enableEdgeToEdge()
        setContent {
            PawseTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        session = session,
                        watcher = watcher,
                        openAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        openBlockList = { screen = Screen.BLOCK_LIST },
                        debugToggle = ::debugToggle,
                    )
                    Screen.BLOCK_LIST -> BlockListScreen(
                        session = session,
                        setBlocked = ::setBlocked,
                        back = { screen = Screen.HOME },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        store.listen(stateChanged)
    }

    override fun onStop() {
        store.unlisten(stateChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val switchedOn = Device.watcherSwitchedOn(this)
        watcher = when {
            !switchedOn -> WatcherState.OFF
            Watcher.running -> WatcherState.ON
            else -> WatcherState.STOPPED
        }
        // A force-stop switches the service off without telling it, so a
        // session can be stored as on with nothing behind it.
        if (!switchedOn) store.update(Session::endBecauseWatcherIsOff)
        session = store.state
    }

    private fun setBlocked(app: String, blocked: Boolean) {
        val protected = Device.protectedApps(this)
        store.update { Session.setBlocked(it, app, blocked, protected) }
    }

    /** Stands in for scanning a tag until tags exist. */
    private fun debugToggle(): Scan {
        val missing = Device.missing(this)
        return store.updateWith { Session.toggle(it, Device.now(), missing, needsKey = false) }
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
