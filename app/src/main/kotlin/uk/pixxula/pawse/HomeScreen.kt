package uk.pixxula.pawse

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    session: SessionState,
    watcher: WatcherState,
    nfc: Nfc,
    /** Everything a session cannot start without and does not have. */
    missing: Set<Missing>,
    openAccessibility: () -> Unit,
    openAppInfo: () -> Unit,
    allowBattery: () -> Unit,
    openNfcSettings: () -> Unit,
    openBlockList: () -> Unit,
    openKeys: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (session.on) R.string.home_on else R.string.home_off),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (session.on) {
                            val since = DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(Date(session.since))
                            stringResource(R.string.home_on_detail, since)
                        } else {
                            stringResource(R.string.home_off_detail)
                        },
                    )
                    if (session.blocked.isNotEmpty()) {
                        val names = remember(session.blocked) {
                            session.blocked.map { Device.appName(context, it) }.sorted()
                        }
                        Text(
                            pluralStringResource(
                                if (session.on) R.plurals.home_blocking
                                else R.plurals.home_would_block,
                                names.size,
                                names.size,
                                names.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (missing.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(
                                if (session.on) R.string.setup_title_on else R.string.setup_title,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (Missing.WATCHER in missing) {
                            Item(
                                if (watcher == WatcherState.STOPPED) R.string.setup_watcher_stopped
                                else R.string.setup_watcher_off,
                                R.string.setup_open_accessibility,
                                openAccessibility,
                            )
                            if (watcher == WatcherState.OFF && restrictedSettingsApply) {
                                Text(
                                    stringResource(R.string.setup_restricted),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = openAppInfo) {
                                    Text(stringResource(R.string.setup_open_app_info))
                                }
                            }
                        }
                        if (Missing.BATTERY in missing) {
                            Item(R.string.setup_battery, R.string.setup_allow_battery, allowBattery)
                        }
                        if (Missing.NFC in missing) {
                            if (nfc == Nfc.ABSENT) {
                                Text(stringResource(R.string.keys_no_nfc))
                            } else {
                                Item(R.string.setup_nfc_off, R.string.keys_open_nfc, openNfcSettings)
                            }
                        }
                        if (Missing.KEY in missing) {
                            Item(R.string.setup_no_keys, R.string.setup_add_key, openKeys)
                        }
                        if (Missing.APPS in missing) {
                            Item(R.string.setup_no_apps, R.string.setup_choose_apps, openBlockList)
                        }
                    }
                }
            }

            Button(onClick = openBlockList, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_block_list))
            }

            Button(onClick = openKeys, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.keys_title))
            }
        }
    }
}

/** One thing that is missing: what it is for, and the way to put it right. */
@Composable
private fun Item(explanation: Int, action: Int, onAction: () -> Unit) {
    Text(stringResource(explanation))
    Button(onClick = onAction) { Text(stringResource(action)) }
}

/** From Android 13 a sideloaded app is at first refused accessibility. */
private val restrictedSettingsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
