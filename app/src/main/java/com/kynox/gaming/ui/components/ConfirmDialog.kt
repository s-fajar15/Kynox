package com.kynox.gaming.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kynox.gaming.R

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.dialog_confirm),
    dismissLabel: String = stringResource(R.string.dialog_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { KTextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { KTextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
