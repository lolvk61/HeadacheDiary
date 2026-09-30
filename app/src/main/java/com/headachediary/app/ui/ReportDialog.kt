package com.headachediary.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.report.DoctorReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val periods = listOf(
    30 to R.string.period_30,
    90 to R.string.period_90,
    365 to R.string.period_year,
    0 to R.string.period_all,
)

/** Выбор периода и создание PDF-отчёта для врача. */
@Composable
fun DoctorReportDialog(entries: List<HeadacheEntry>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableIntStateOf(90) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.report_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.report_dialog_text))
                periods.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = period == value, role = Role.RadioButton, onClick = { period = value })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = period == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val days = if (period == 0) null else period
                    if (DoctorReport.select(entries, days).isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.report_empty), Toast.LENGTH_LONG).show()
                    } else {
                        busy = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching { DoctorReport.build(context, entries, days) }.getOrNull()
                            }
                            busy = false
                            if (file != null) {
                                DoctorReport.share(context, file)
                            } else {
                                Toast.makeText(context, context.getString(R.string.report_failed), Toast.LENGTH_LONG).show()
                            }
                            onDismiss()
                        }
                    }
                },
            ) { Text(stringResource(if (busy) R.string.report_creating else R.string.report_create)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
