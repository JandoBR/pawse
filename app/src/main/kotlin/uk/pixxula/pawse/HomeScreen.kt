package uk.pixxula.pawse

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    openAccessibility: () -> Unit,
    openBlockList: () -> Unit,
    debugToggle: () -> Scan,
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

            val noApps = session.blocked.isEmpty()
            if (watcher != WatcherState.ON || noApps) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.setup_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (watcher != WatcherState.ON) {
                            Text(
                                stringResource(
                                    if (watcher == WatcherState.OFF) R.string.setup_watcher_off
                                    else R.string.setup_watcher_stopped,
                                ),
                            )
                            Button(onClick = openAccessibility) {
                                Text(stringResource(R.string.setup_open_accessibility))
                            }
                        }
                        if (noApps) {
                            Text(stringResource(R.string.setup_no_apps))
                            Button(onClick = openBlockList) {
                                Text(stringResource(R.string.setup_choose_apps))
                            }
                        }
                    }
                }
            }

            Button(onClick = openBlockList, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_block_list))
            }

            var refusal by remember { mutableStateOf<Int?>(null) }
            OutlinedButton(
                onClick = {
                    refusal = when (debugToggle()) {
                        Scan.TooSoon -> R.string.scan_too_soon
                        is Scan.NotReady -> R.string.scan_not_ready
                        else -> null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (session.on) R.string.debug_end else R.string.debug_start))
            }
            Text(
                stringResource(refusal ?: R.string.debug_note),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
