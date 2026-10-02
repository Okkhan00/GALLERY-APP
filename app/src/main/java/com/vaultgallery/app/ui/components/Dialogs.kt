package com.vaultgallery.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vaultgallery.app.data.TagCount
import com.vaultgallery.app.data.WalletEntity
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.domain.Currency
import com.vaultgallery.app.security.PinManager
import kotlinx.coroutines.launch

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Bulk add/remove tags for the selected photos, plus quick create. */
@Composable
fun TagPickerDialog(
    tags: List<TagCount>, onAdd: (Long) -> Unit, onRemove: (Long) -> Unit,
    onCreate: suspend (String) -> Boolean, onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Tags for selection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(tags, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${t.name} (${t.photoCount})", Modifier.weight(1f))
                            TextButton(onClick = { onAdd(t.id) }) { Text("Add") }
                            TextButton(onClick = { onRemove(t.id) }) { Text("Remove") }
                        }
                    }
                }
                OutlinedTextField(name, { name = it; error = null }, label = { Text("New tag") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
                OutlinedButton(onClick = { scope.launch { if (onCreate(name)) name = "" else error = "Empty, too long, or already exists" } }) { Text("Create tag") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ManageTagsDialog(
    tags: List<TagCount>, onRename: suspend (Long, String) -> Boolean, onDelete: (Long) -> Unit,
    onCreate: suspend (String) -> Boolean, onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TagCount?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TagCount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Manage tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(tags, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.name} (${t.photoCount})", Modifier.weight(1f))
                            IconButton(onClick = { editing = t; editText = t.name }) { Icon(Icons.Default.Edit, "Rename ${t.name}") }
                            IconButton(onClick = { deleting = t }) { Icon(Icons.Default.Delete, "Delete ${t.name}") }
                        }
                    }
                }
                OutlinedTextField(newName, { newName = it; error = null }, label = { Text("New tag") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
                OutlinedButton(onClick = { scope.launch { if (onCreate(newName)) newName = "" else error = "Empty, too long, or already exists" } }) { Text("Create tag") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    editing?.let { t ->
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("Rename tag") },
            text = { OutlinedTextField(editText, { editText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { if (onRename(t.id, editText)) editing = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { t ->
        ConfirmDialog("Delete tag?", "\"${t.name}\" will be removed from ${t.photoCount} photo(s). The photos stay.", "Delete", { onDelete(t.id) }, { deleting = null })
    }
}

/** Virtual-credit unlock popup. Stars/Coins are local game credits, not money. */
@Composable
fun UnlockDialog(photo: PhotoEntity, wallet: WalletEntity?, onUnlock: (Currency) -> Unit, onDismiss: () -> Unit) {
    val stars = wallet?.stars ?: 0
    val coins = wallet?.coins ?: 0
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Locked photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Balance: ⭐ $stars  ·  🪙 $coins")
                Button(onClick = { onUnlock(Currency.STARS) }, enabled = stars >= photo.unlockCostStars, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock with ⭐ ${photo.unlockCostStars}")
                }
                Button(onClick = { onUnlock(Currency.COINS) }, enabled = coins >= photo.unlockCostCoins, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock with 🪙 ${photo.unlockCostCoins}")
                }
                Text("Stars and Coins are virtual in-app credits with no real-world value.", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun SetPinDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length in 4..12 && a == b
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Set PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { if (it.all(Char::isDigit) && it.length <= 12) a = it }, label = { Text("PIN (4–12 digits)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(b, { if (it.all(Char::isDigit) && it.length <= 12) b = it }, label = { Text("Repeat PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = b.isNotEmpty() && a != b)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(a) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun VerifyPinDialog(pin: PinManager, title: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(input, { if (it.all(Char::isDigit) && it.length <= 12) { input = it; error = null } }, label = { Text("Current PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null, supportingText = error?.let { { Text(it) } })
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.verify(input.toCharArray())) onVerified()
                else { error = if (pin.lockoutRemainingMs() > 0) "Too many attempts. Try later." else "Incorrect PIN"; input = "" }
            }, enabled = input.length >= 4) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
