package com.headachediary.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.headachediary.app.MainViewModel
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.health.HealthService
import com.headachediary.app.reminders.Notifications
import com.headachediary.app.reminders.ReminderScheduler
import com.headachediary.app.settings.AppLanguage
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.settings.PressureUnit
import com.headachediary.app.settings.ThemeMode
import com.headachediary.app.weather.LocationHelper
import com.headachediary.app.weather.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    entries: List<HeadacheEntry>,
    painFreeDays: Set<Long>,
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showReport by remember { mutableStateOf(false) }
    var weatherOn by remember { mutableStateOf(AppSettings.weatherEnabled(context)) }
    var pressureUnit by remember { mutableStateOf(AppSettings.pressureUnit(context)) }
    var hasLocation by remember { mutableStateOf(LocationHelper.hasAny(context)) }
    var place by remember { mutableStateOf(LocationHelper.manualPlace(context)) }
    var cityQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var reminderOn by remember { mutableStateOf(AppSettings.reminderEnabled(context)) }
    var reminderMinutes by remember { mutableStateOf(AppSettings.reminderMinutes(context)) }
    var forecastOn by remember { mutableStateOf(AppSettings.forecastAlertEnabled(context)) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun setReminder(on: Boolean) {
        AppSettings.setReminderEnabled(context, on)
        reminderOn = on
        ReminderScheduler.scheduleAll(context)
    }

    fun setForecast(on: Boolean) {
        AppSettings.setForecastAlertEnabled(context, on)
        forecastOn = on
        ReminderScheduler.scheduleAll(context)
    }

    // Какой переключатель ждёт результата запроса права на уведомления (с Android 13 оно обязательно).
    var pendingNotificationSwitch by remember { mutableStateOf<String?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val which = pendingNotificationSwitch
        pendingNotificationSwitch = null
        if (!granted) {
            toast(context.getString(R.string.notif_permission_denied))
        } else if (which == "reminder") {
            setReminder(true)
        } else if (which == "forecast") {
            setForecast(true)
        }
    }

    /** Включает переключатель, предварительно запросив право на уведомления, если оно нужно. */
    fun requestNotificationsThen(which: String, enable: () -> Unit) {
        if (Notifications.canPost(context)) {
            enable()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotificationSwitch = which
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            toast(context.getString(R.string.notif_permission_denied))
        }
    }

    fun enableWeather() {
        AppSettings.setWeatherEnabled(context, true)
        weatherOn = true
        vm.refreshLocation(force = true) { _ -> hasLocation = LocationHelper.hasAny(context) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Без доступа к геолокации погоду всё равно можно включить: достаточно указать город.
        if (result.values.none { it }) toast(context.getString(R.string.weather_permission_denied))
        enableWeather()
    }

    var healthOn by remember { mutableStateOf(AppSettings.healthEnabled(context)) }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted: Set<String> ->
        if (granted.isEmpty()) {
            toast(context.getString(R.string.health_permission_denied))
        } else {
            AppSettings.setHealthEnabled(context, true)
            healthOn = true
            vm.fillRecentHealth()
        }
    }

    fun searchCity() {
        if (cityQuery.isBlank() || searching) return
        searching = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { WeatherClient.geocode(cityQuery, context.appLocale().language) }.getOrNull()
            }
            searching = false
            if (found == null) {
                toast(context.getString(R.string.weather_city_not_found))
            } else {
                LocationHelper.setManualPlace(context, found)
                place = found
                hasLocation = true
                cityQuery = ""
            }
        }
    }

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

        SectionCard(stringResource(R.string.settings_weather)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.weather_switch),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = weatherOn,
                    onCheckedChange = { on ->
                        when {
                            !on -> {
                                AppSettings.setWeatherEnabled(context, false)
                                weatherOn = false
                            }
                            LocationHelper.hasPermission(context) -> enableWeather()
                            else -> locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.weather_switch_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (weatherOn) {
                val currentPlace = place
                if (currentPlace != null) {
                    Text(
                        stringResource(R.string.weather_city_set, currentPlace.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = {
                        LocationHelper.setManualPlace(context, null)
                        place = null
                        hasLocation = LocationHelper.hasAny(context)
                        vm.refreshLocation(force = true) { _ -> hasLocation = LocationHelper.hasAny(context) }
                    }) { Text(stringResource(R.string.weather_city_use_location)) }
                } else {
                    Text(
                        stringResource(if (hasLocation) R.string.weather_location_ok else R.string.weather_location_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = cityQuery,
                        onValueChange = { cityQuery = it },
                        label = { Text(stringResource(R.string.weather_city_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::searchCity, enabled = cityQuery.isNotBlank() && !searching) {
                        Text(stringResource(R.string.weather_city_find))
                    }
                }
            }
            Text(
                stringResource(R.string.pressure_unit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = pressureUnit == unit,
                        onClick = {
                            AppSettings.setPressureUnit(context, unit)
                            pressureUnit = unit
                        },
                        label = { Text(stringResource(unit.labelRes)) },
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.settings_health)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.health_switch),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = healthOn,
                    onCheckedChange = { on ->
                        if (!on) {
                            AppSettings.setHealthEnabled(context, false)
                            healthOn = false
                        } else {
                            when (HealthService.sdkStatus(context)) {
                                HealthConnectClient.SDK_AVAILABLE -> healthLauncher.launch(HealthService.permissions)
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                    toast(context.getString(R.string.health_update_required))
                                else -> toast(context.getString(R.string.health_unavailable))
                            }
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.health_switch_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(stringResource(R.string.settings_reminders)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.reminder_switch),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = reminderOn,
                    onCheckedChange = { on ->
                        if (on) requestNotificationsThen("reminder") { setReminder(true) } else setReminder(false)
                    },
                )
            }
            Text(
                stringResource(R.string.reminder_switch_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reminderOn) {
                TextButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            reminderMinutes = hour * 60 + minute
                            AppSettings.setReminderMinutes(context, reminderMinutes)
                            ReminderScheduler.scheduleAll(context)
                        },
                        reminderMinutes / 60,
                        reminderMinutes % 60,
                        true,
                    ).show()
                }) {
                    Text(
                        stringResource(
                            R.string.reminder_time,
                            String.format(context.appLocale(), "%02d:%02d", reminderMinutes / 60, reminderMinutes % 60),
                        ),
                    )
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.forecast_switch),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = forecastOn,
                    onCheckedChange = { on ->
                        when {
                            !on -> setForecast(false)
                            !weatherOn -> toast(context.getString(R.string.forecast_needs_weather))
                            else -> requestNotificationsThen("forecast") { setForecast(true) }
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.forecast_switch_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Text(
                stringResource(R.string.about_weather_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showReport) DoctorReportDialog(entries, painFreeDays) { showReport = false }
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
