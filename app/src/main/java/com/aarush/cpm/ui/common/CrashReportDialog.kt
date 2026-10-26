package com.aarush.cpm.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.data.crash.CrashReporter

/** Shown once after a crash: what went wrong, with Copy and Share so it can be sent on. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.lastReport(context)) }
    val text = report ?: return
    fun dismiss() { CrashReporter.clear(context); report = null }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("The app stopped last time") },
        text = {
            Column {
                Text("Please copy this and send it to whoever maintains the app. It shows exactly what went wrong.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Aarush CPM crash", text))
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send crash report"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                dismiss()
            }) { Text("Copy & share") }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text("Close") } }
    )
}
