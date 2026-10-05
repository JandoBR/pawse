package uk.pixxula.pawse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlockListScreen(
    session: SessionState,
    setBlocked: (app: String, blocked: Boolean) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<App>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { Device.blockableApps(context) }
    }
    var search by rememberSaveable { mutableStateOf("") }

    Scaffold { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            TopBar(stringResource(R.string.home_block_list), back)
            Text(
                stringResource(if (session.on) R.string.list_locked else R.string.list_how),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.on) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.list_search)) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )

            val all = apps
            val shown = all?.filter { it.name.contains(search.trim(), ignoreCase = true) }
            when {
                shown == null -> Note(R.string.list_loading)
                shown.isEmpty() -> Note(R.string.list_none)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            blocked = app.packageName in session.blocked,
                            enabled = !session.on,
                            setBlocked = { setBlocked(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: Int) {
    Text(stringResource(text), modifier = Modifier.padding(24.dp))
}

@Composable
private fun AppRow(app: App, blocked: Boolean, enabled: Boolean, setBlocked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = blocked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = setBlocked,
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(app.packageName, size = 40.dp)
        Text(app.name, modifier = Modifier.weight(1f))
        // The row is what is pressed; the switch only shows the state.
        Switch(checked = blocked, onCheckedChange = null, enabled = enabled)
    }
}
