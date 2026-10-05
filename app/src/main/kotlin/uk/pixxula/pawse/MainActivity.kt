package uk.pixxula.pawse

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** How the accessibility service stands. */
enum class WatcherState {
    OFF,
    ON,

    /** Switched on in Settings, but not running: Android gave up on it after a crash. */
    STOPPED,
}

private enum class Screen { HOME, BLOCK_LIST, KEYS }

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var nfcAdapter: NfcAdapter? = null

    // Rotation does not restart this activity (see the manifest), so what is
    // on screen can simply live here.
    private var screen by mutableStateOf(Screen.HOME)
    private var session by mutableStateOf(SessionState())
    private var watcher by mutableStateOf(WatcherState.OFF)
    private var nfc by mutableStateOf(Nfc.ABSENT)
    private var phoneMissing by mutableStateOf(emptySet<Missing>())
    private var keysPrompt by mutableStateOf<KeysPrompt?>(null)

    private var listening = false
    private var reading = false
    private var resumed = false
    private val stateChanged: () -> Unit = { runOnUiThread { session = store.state } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        session = store.state
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        handle(intent)
        enableEdgeToEdge()
        setContent {
            PawseTheme {
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        session = session,
                        watcher = watcher,
                        nfc = nfc,
                        missing = Session.missing(session, phoneMissing),
                        openAccessibility = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                        openAppInfo = {
                            open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownPackage())
                        },
                        allowBattery = {
                            open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, ownPackage())
                        },
                        openNfcSettings = { open(Settings.ACTION_NFC_SETTINGS) },
                        openNotificationAccess = {
                            open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        },
                        openBlockList = { screen = Screen.BLOCK_LIST },
                        openKeys = { screen = Screen.KEYS },
                    )
                    Screen.BLOCK_LIST -> BlockListScreen(
                        session = session,
                        setBlocked = ::setBlocked,
                        back = { screen = Screen.HOME },
                    )
                    Screen.KEYS -> KeysScreen(
                        session = session,
                        nfc = nfc,
                        prompt = keysPrompt,
                        setPrompt = { keysPrompt = it },
                        listen = ::listenForTags,
                        register = { id, name -> store.update { Session.addKey(it, Key(id, name)) } },
                        rename = { id, name -> store.update { Session.renameKey(it, id, name) } },
                        remove = { id -> store.update { Session.removeKey(it, id) } },
                        openNfcSettings = { open(Settings.ACTION_NFC_SETTINGS) },
                        back = { screen = Screen.HOME },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A scan that could not switch the session opens the app where it helps. */
    private fun handle(intent: Intent?) {
        val keyId = intent?.getStringExtra(EXTRA_REGISTER_KEY) ?: return
        intent.removeExtra(EXTRA_REGISTER_KEY)
        screen = Screen.KEYS
        keysPrompt = KeysPrompt.Register(keyId)
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
        resumed = true
        val switchedOn = Device.watcherSwitchedOn(this)
        watcher = when {
            !switchedOn -> WatcherState.OFF
            Watcher.running -> WatcherState.ON
            else -> WatcherState.STOPPED
        }
        nfc = Device.nfc(this)
        phoneMissing = Device.missing(this)
        // A force-stop switches the service off without telling it, so a
        // session can be stored as on with nothing behind it.
        if (!switchedOn) store.update(Session::endBecauseWatcherIsOff)
        session = store.state
        applyListening()
    }

    override fun onPause() {
        resumed = false
        applyListening()
        super.onPause()
    }

    private fun open(settingsPage: String, data: Uri? = null) {
        try {
            startActivity(Intent(settingsPage, data))
        } catch (e: ActivityNotFoundException) {
            // This phone has no such page: Settings itself is the next best.
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun ownPackage(): Uri = Uri.fromParts("package", packageName, null)

    private fun setBlocked(app: String, blocked: Boolean) {
        val protected = Device.protectedApps(this)
        store.update { Session.setBlocked(it, app, blocked, protected) }
    }

    // While the Keys screen is listening, tags come here and not to
    // ScanActivity: a tag held up to that screen is being made a key, not used.

    private fun listenForTags(listen: Boolean) {
        listening = listen
        applyListening()
    }

    private fun applyListening() {
        val adapter = nfcAdapter ?: return
        val read = listening && resumed
        if (read == reading) return
        reading = read
        if (read) {
            val anyTag = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
            adapter.enableReaderMode(this, ::tagFound, anyTag, null)
        } else {
            // A tag still against the phone is about to be handed over as a scan.
            Tags.muteScans()
            adapter.disableReaderMode(this)
        }
    }

    /** Called off the main thread, with the tag against the phone. */
    private fun tagFound(tag: Tag) {
        Tags.muteScans()
        val existing = Tags.read(tag)
        val known = existing?.let { id -> store.state.keys.firstOrNull { it.id == id } }
        val next: KeysPrompt = when {
            known != null -> KeysPrompt.Say(getString(R.string.keys_already, known.name))
            keysPrompt == KeysPrompt.Write -> when (val written = Tags.write(this, tag)) {
                is Written.Done -> KeysPrompt.Register(written.id)
                Written.ReadOnly -> KeysPrompt.Say(getString(R.string.keys_read_only))
                is Written.TooSmall -> KeysPrompt.Say(
                    getString(R.string.keys_too_small, written.has, written.needs),
                )
                Written.NotNdef -> KeysPrompt.Say(getString(R.string.keys_not_ndef))
                Written.Failed -> KeysPrompt.Say(getString(R.string.keys_write_failed))
            }
            existing != null -> KeysPrompt.Register(existing)
            else -> KeysPrompt.Say(getString(R.string.keys_not_pawse))
        }
        Tags.muteScans()
        runOnUiThread { keysPrompt = next }
    }

    companion object {
        private const val EXTRA_REGISTER_KEY = "register_key"

        /** Opens the app; with [registerKey], at the offer to register that tag. */
        fun intent(context: Context, registerKey: String? = null): Intent =
            Intent(context, MainActivity::class.java).apply {
                if (registerKey != null) putExtra(EXTRA_REGISTER_KEY, registerKey)
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
