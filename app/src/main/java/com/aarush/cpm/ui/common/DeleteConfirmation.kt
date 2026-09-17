package com.aarush.cpm.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Are you sure?" before any delete — used the same way everywhere (BOQ, Expense, Vendor,
 *  Area/Rate breakdown) so the confirmation always looks and behaves identically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDeleteDialog(itemLabel: String = "this item", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete?") },
        text = { Text("Are you sure you want to delete $itemLabel? You'll have a few seconds to undo it right after.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shows a brief "Undo" snackbar after a delete has already happened. Call this right after
 *  the delete completes; if the person taps Undo before it times out, [onUndo] re-creates
 *  the item. Deletion itself is never held back waiting for this — it already happened, this
 *  is purely the safety net. */
fun CoroutineScope.showUndoDeleteSnackbar(
    snackbarHostState: SnackbarHostState,
    message: String = "Deleted",
    onUndo: () -> Unit
) {
    launch {
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
