package com.headachediary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headachediary.app.data.HeadacheEntry
import kotlinx.coroutines.delay

@Composable
fun JournalScreen(
    entries: List<HeadacheEntry>,
    onPainNow: () -> Unit,
    onOpen: (HeadacheEntry) -> Unit,
    onEnd: (HeadacheEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    // Считаем приступ "идущим", если он не закрыт и начался не более суток назад.
    val ongoing = entries.firstOrNull { it.endTime == null && now - it.startTime < DAY_MS }
    val grouped = entries.groupBy { it.startTime.toLocalDate() }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick = onPainNow,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Болит голова", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Записать время прямо сейчас", fontSize = 14.sp)
                }
            }
        }

        if (ongoing != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Приступ идёт", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "С ${formatTime(ongoing.startTime)} · уже ${formatDuration(now - ongoing.startTime)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onEnd(ongoing) }) { Text("Боль прошла") }
                            OutlinedButton(onClick = { onOpen(ongoing) }) { Text("Добавить детали") }
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "Пока нет записей. Нажмите красную кнопку, когда начнётся боль, — время сохранится автоматически.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        grouped.forEach { (date, list) ->
            item(key = "header-$date") {
                Text(
                    formatDayHeader(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(list, key = { it.id }) { entry ->
                EntryCard(entry, onClick = { onOpen(entry) })
            }
        }
    }
}
