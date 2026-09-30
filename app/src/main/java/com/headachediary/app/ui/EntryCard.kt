package com.headachediary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.MedHelp
import com.headachediary.app.data.symptomLabels

@Composable
fun EntryCard(entry: HeadacheEntry, onClick: () -> Unit) {
    val type = HeadacheType.fromKey(entry.type)
    val timeText = entry.endTime
        ?.let { "${formatTime(entry.startTime)} – ${formatTime(it)}" }
        ?: "с ${formatTime(entry.startTime)}"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = softCardColors(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(painColor(entry.intensity)))
            Column(
                Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(timeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.intensity?.let { "$it/10" } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = painColor(entry.intensity),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(type)
                    val duration = entry.endTime?.let { formatDuration(it - entry.startTime) }
                    if (duration != null) {
                        Text(
                            duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val symptoms = entry.symptoms.symptomLabels()
                if (symptoms.isNotEmpty()) {
                    val shown = symptoms.take(3).joinToString(", ")
                    val more = symptoms.size - 3
                    Text(
                        if (more > 0) "$shown и ещё $more" else shown,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                if (entry.medication.isNotBlank()) {
                    val help = MedHelp.entries.firstOrNull { it.name == entry.medicationHelped }
                    Text(
                        "Лекарство: ${entry.medication}" + (help?.let { " · ${it.label.lowercase()}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
