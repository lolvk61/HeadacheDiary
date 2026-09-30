package com.headachediary.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.headachediary.app.R
import com.headachediary.app.data.DayFactors

/**
 * Карточка «Факторы дня»: стресс, кофеин, алкоголь, вода, еда. Заполняется за любой день, а не только за день с болью,
 * поэтому по ней можно сравнить дни с болью и без неё. Изменения сохраняются сразу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactorsCard(
    day: Long,
    factors: DayFactors?,
    onChange: (DayFactors) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = factors ?: DayFactors(day)
    var expanded by rememberSaveable(day) { mutableStateOf(false) }

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.factors_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        when {
                            expanded -> R.string.factors_hide
                            factors != null -> R.string.factors_edit
                            else -> R.string.factors_fill
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.factors_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.factors_stress),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to R.string.stress_none, 1 to R.string.stress_moderate, 2 to R.string.stress_high)
                        .forEach { (level, label) ->
                            FilterChip(
                                selected = current.stress == level,
                                onClick = { onChange(current.copy(stress = level)) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                }
                Text(
                    stringResource(R.string.factors_caffeine),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { cups ->
                        FilterChip(
                            selected = current.caffeine == cups,
                            onClick = { onChange(current.copy(caffeine = cups)) },
                            label = { Text(if (cups == 4) "4+" else cups.toString()) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = current.alcohol,
                        onClick = { onChange(current.copy(alcohol = !current.alcohol)) },
                        label = { Text(stringResource(R.string.factors_alcohol)) },
                    )
                    FilterChip(
                        selected = current.lowWater,
                        onClick = { onChange(current.copy(lowWater = !current.lowWater)) },
                        label = { Text(stringResource(R.string.factors_low_water)) },
                    )
                    FilterChip(
                        selected = current.skippedMeal,
                        onClick = { onChange(current.copy(skippedMeal = !current.skippedMeal)) },
                        label = { Text(stringResource(R.string.factors_skipped_meal)) },
                    )
                }
            }
        }
    }
}
