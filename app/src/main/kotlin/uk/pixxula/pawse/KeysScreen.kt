package uk.pixxula.pawse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** What the Keys screen is asking or saying, on top of the list. */
sealed interface KeysPrompt {
    /** Waiting for a tag to write a new key to. */
    data object Write : KeysPrompt

    /** A Pawse tag that is not a key yet: register it under a name? */
    data class Register(val id: String) : KeysPrompt

    data class Rename(val key: Key) : KeysPrompt
    data class Remove(val key: Key) : KeysPrompt
    data class Say(val text: String) : KeysPrompt
}

enum class Nfc { ON, OFF, ABSENT }

@Composable
fun KeysScreen(
    session: SessionState,
    nfc: Nfc,
    prompt: KeysPrompt?,
    setPrompt: (KeysPrompt?) -> Unit,
    /** Called while the screen is listening for tags, and again when it stops. */
    listen: (Boolean) -> Unit,
    register: (id: String, name: String) -> Unit,
    rename: (id: String, name: String) -> Unit,
    remove: (id: String) -> Unit,
    openNfcSettings: () -> Unit,
    back: () -> Unit,
) {
    val usable = !session.on && nfc == Nfc.ON
    DisposableEffect(usable) {
        listen(usable)
        onDispose { listen(false) }
    }

    Scaffold { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar(stringResource(R.string.keys_title), back)
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    session.on -> Text(
                        stringResource(R.string.keys_locked),
                        color = MaterialTheme.colorScheme.error,
                    )
                    nfc == Nfc.ABSENT -> Text(stringResource(R.string.keys_no_nfc))
                    nfc == Nfc.OFF -> {
                        Text(stringResource(R.string.keys_nfc_off))
                        Button(onClick = openNfcSettings) {
                            Text(stringResource(R.string.keys_open_nfc))
                        }
                    }
                    else -> Text(
                        stringResource(R.string.keys_how),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (session.keys.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Paw(size = 72.dp, tint = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(R.string.keys_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                for (key in session.keys) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(26.dp),
                            )
                            Text(
                                key.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(
                                onClick = { setPrompt(KeysPrompt.Rename(key)) },
                                enabled = !session.on,
                            ) { Text(stringResource(R.string.keys_rename)) }
                            TextButton(
                                onClick = { setPrompt(KeysPrompt.Remove(key)) },
                                enabled = !session.on,
                            ) { Text(stringResource(R.string.keys_remove)) }
                        }
                    }
                }

                Button(
                    onClick = { setPrompt(KeysPrompt.Write) },
                    enabled = usable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.keys_write)) }
            }
        }
    }

    val close = { setPrompt(null) }
    when (prompt) {
        null -> Unit
        KeysPrompt.Write -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.keys_write)) },
            text = { Text(stringResource(R.string.keys_write_how)) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
        )
        is KeysPrompt.Register -> NameDialog(
            title = R.string.keys_register_title,
            explanation = R.string.keys_register_how,
            initial = stringResource(R.string.keys_default_name, session.keys.size + 1),
            confirm = R.string.keys_register,
            onConfirm = { register(prompt.id, it) },
            close = close,
        )
        is KeysPrompt.Rename -> NameDialog(
            title = R.string.keys_rename,
            explanation = null,
            initial = prompt.key.name,
            confirm = R.string.keys_rename,
            onConfirm = { rename(prompt.key.id, it) },
            close = close,
        )
        is KeysPrompt.Remove -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.keys_remove_title, prompt.key.name)) },
            text = { Text(stringResource(R.string.keys_remove_how)) },
            confirmButton = {
                TextButton(onClick = { remove(prompt.key.id); close() }) {
                    Text(stringResource(R.string.keys_remove))
                }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
        )
        is KeysPrompt.Say -> AlertDialog(
            onDismissRequest = close,
            text = { Text(prompt.text) },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun NameDialog(
    title: Int,
    explanation: Int?,
    initial: String,
    confirm: Int,
    onConfirm: (String) -> Unit,
    close: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (explanation != null) Text(stringResource(explanation))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.keys_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()); close() },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}
