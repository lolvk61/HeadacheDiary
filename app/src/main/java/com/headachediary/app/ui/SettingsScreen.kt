package com.headachediary.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.MainViewModel
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.settings.AppLanguage
import com.headachediary.app.settings.ThemeMode
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    entries: List<HeadacheEntry>,
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showReport by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            vm.exportBackup(uri) { ok ->
                toast(context.getString(if (ok) R.string.backup_saved else R.string.backup_failed))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            vm.importBackup(uri) { result ->
                toast(
                    if (result == null) {
                        context.getString(R.string.import_failed)
                    } else {
                        context.getString(R.string.import_done, result.added, result.skipped)
                    },
                )
            }
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        SectionCard(stringResource(R.string.settings_appearance)) {
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = language == AppLanguage.SYSTEM,
                    onClick = { onLanguageChange(AppLanguage.SYSTEM) },
                    label = { Text(stringResource(R.string.lang_system)) },
                )
                // Названия языков не переводятся: их узнают на своём языке.
                FilterChip(
                    selected = language == AppLanguage.RU,
                    onClick = { onLanguageChange(AppLanguage.RU) },
                    label = { Text("Русский") },
                )
                FilterChip(
                    selected = language == AppLanguage.EN,
                    onClick = { onLanguageChange(AppLanguage.EN) },
                    label = { Text("English") },
                )
            }
        }

        SectionCard(stringResource(R.string.settings_data)) {
            ActionRow(
                stringResource(R.string.action_doctor_report),
                stringResource(R.string.action_doctor_report_desc),
            ) { showReport = true }
            HorizontalDivider()
            ActionRow(
                stringResource(R.string.action_backup),
                stringResource(R.string.action_backup_desc),
            ) { exportLauncher.launch("headache-diary-${LocalDate.now()}.json") }
            HorizontalDivider()
            ActionRow(
                stringResource(R.string.action_restore),
                stringResource(R.string.action_restore_desc),
            ) { importLauncher.launch(arrayOf("*/*")) }
        }

        SectionCard(stringResource(R.string.settings_about)) {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull().orEmpty()
            }
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showReport) DoctorReportDialog(entries) { showReport = false }
}

@Composable
private fun ActionRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
