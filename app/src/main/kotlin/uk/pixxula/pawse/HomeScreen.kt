package uk.pixxula.pawse

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    session: SessionState,
    watcher: WatcherState,
    nfc: Nfc,
    /** Whether notification access is granted, running or not. */
    notificationAccess: Boolean,
    /** Whether to point at Xiaomi's Autostart switch. */
    autostartTip: Boolean,
    openAutostart: () -> Unit,
    autostartDone: () -> Unit,
    /** Everything a session cannot start without and does not have. */
    missing: Set<Missing>,
    openAccessibility: () -> Unit,
    openAppInfo: () -> Unit,
    allowBattery: () -> Unit,
    openNfcSettings: () -> Unit,
    openNotificationAccess: () -> Unit,
    openBlockList: () -> Unit,
    openKeys: () -> Unit,
) {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Paw(size = 34.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Status(session, ready = missing.isEmpty(), openBlockList = openBlockList)

            if (missing.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(
                                if (session.on) R.string.setup_title_on else R.string.setup_title,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (Missing.WATCHER in missing) {
                            Item(
                                R.string.setup_watcher_title,
                                if (watcher == WatcherState.STOPPED) R.string.setup_watcher_stopped
                                else R.string.setup_watcher_off,
                                R.string.setup_open_accessibility,
                                openAccessibility,
                            ) {
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
                        }
                        if (Missing.NOTIFICATIONS in missing) {
                            Item(
                                R.string.setup_notifications_title,
                                if (notificationAccess) R.string.setup_notifications_stopped
                                else R.string.setup_notifications,
                                R.string.setup_open_notifications,
                                openNotificationAccess,
                            )
                        }
                        if (Missing.BATTERY in missing) {
                            Item(
                                R.string.setup_battery_title,
                                R.string.setup_battery,
                                R.string.setup_allow_battery,
                                allowBattery,
                            )
                        }
                        if (Missing.NFC in missing) {
                            if (nfc == Nfc.ABSENT) {
                                Text(stringResource(R.string.keys_no_nfc))
                            } else {
                                Item(
                                    R.string.setup_nfc_title,
                                    R.string.setup_nfc_off,
                                    R.string.keys_open_nfc,
                                    openNfcSettings,
                                )
                            }
                        }
                        if (Missing.KEY in missing) {
                            Item(
                                R.string.setup_keys_title,
                                R.string.setup_no_keys,
                                R.string.setup_add_key,
                                openKeys,
                            )
                        }
                        if (Missing.APPS in missing) {
                            Item(
                                R.string.setup_apps_title,
                                R.string.setup_no_apps,
                                R.string.setup_choose_apps,
                                openBlockList,
                            )
                        }
                    }
                }
            }

            if (autostartTip) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.autostart_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.autostart_why),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = openAutostart) {
                                Text(stringResource(R.string.autostart_open))
                            }
                            TextButton(onClick = autostartDone) {
                                Text(stringResource(R.string.autostart_done))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(
                    icon = R.drawable.ic_apps,
                    title = stringResource(R.string.home_block_list),
                    detail = pluralStringResource(
                        R.plurals.home_apps,
                        session.blocked.size,
                        session.blocked.size,
                    ),
                    onClick = openBlockList,
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    icon = R.drawable.ic_tag,
                    title = stringResource(R.string.keys_title),
                    detail = pluralStringResource(
                        R.plurals.home_keys,
                        session.keys.size,
                        session.keys.size,
                    ),
                    onClick = openKeys,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Whether a session is on, since when, and what it blocks. */
@Composable
private fun Status(session: SessionState, ready: Boolean, openBlockList: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (session.on) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (session.on) colors.onPrimary else colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        if (session.on) colors.onPrimary.copy(alpha = 0.16f)
                        else colors.primaryContainer,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Paw(
                    size = 64.dp,
                    tint = if (session.on) colors.onPrimary else colors.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (session.on) R.string.home_on else R.string.home_off),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    session.on -> stringResource(
                        R.string.home_on_detail,
                        elapsed(session.since),
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.since)),
                    )
                    ready -> stringResource(R.string.home_off_detail)
                    else -> stringResource(R.string.home_off_not_ready)
                },
                textAlign = TextAlign.Center,
            )
            if (session.on) {
                Text(
                    stringResource(R.string.home_on_how),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            if (session.blocked.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val shown = session.blocked.sorted().take(MAX_ICONS)
                Surface(
                    onClick = openBlockList,
                    shape = CircleShape,
                    color = if (session.on) colors.onPrimary.copy(alpha = 0.16f)
                    else colors.surfaceContainerLowest,
                    contentColor = if (session.on) colors.onPrimary else colors.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (app in shown) AppIcon(app, size = 30.dp)
                        val more = session.blocked.size - shown.size
                        Text(
                            if (more > 0) stringResource(R.string.home_more, more)
                            else pluralStringResource(
                                if (session.on) R.plurals.home_blocked else R.plurals.home_to_block,
                                session.blocked.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** How long the session has been on, kept up to date while it is shown. */
@Composable
private fun elapsed(since: Long): String {
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    val minutes = (now - since).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.duration_under_a_minute)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
    }
}

/** One thing that is missing: what it is for, and the way to put it right. */
@Composable
private fun Item(
    title: Int,
    explanation: Int,
    action: Int,
    onAction: () -> Unit,
    more: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(explanation), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onAction) { Text(stringResource(action)) }
        more()
    }
}

/** A way into one of the two other screens, with how much is in it. */
@Composable
private fun Tile(
    icon: Int,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_ICONS = 5

/** From Android 13 a sideloaded app is at first refused accessibility. */
private val restrictedSettingsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

