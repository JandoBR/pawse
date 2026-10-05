package uk.pixxula.pawse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.home_block_list),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (session.on) {
                Text(
                    stringResource(R.string.list_locked),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.list_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
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
        AppIcon(app.packageName)
        Text(app.name, modifier = Modifier.weight(1f))
        // The row is what is pressed; the switch only shows the state.
        Switch(checked = blocked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? =
    try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PIXELS, ICON_PIXELS)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }

private const val ICON_PIXELS = 96
