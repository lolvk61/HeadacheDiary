package com.headachediary.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.headachediary.app.reminders.Notifications
import com.headachediary.app.reminders.ReminderScheduler
import com.headachediary.app.settings.AppLanguage
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.settings.ThemeMode
import com.headachediary.app.settings.localized
import com.headachediary.app.ui.CalendarScreen
import com.headachediary.app.ui.EditorScreen
import com.headachediary.app.ui.HeadacheTheme
import com.headachediary.app.ui.JournalScreen
import com.headachediary.app.ui.LockScreen
import com.headachediary.app.ui.SettingsScreen
import com.headachediary.app.ui.StatsScreen
import com.headachediary.app.ui.toMillis
import com.headachediary.app.widget.PainWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

// FragmentActivity нужен для системного окна отпечатка (BiometricPrompt); Compose в нём работает как обычно.
class MainActivity : FragmentActivity() {
    companion object {
        /** Ключ дополнительного значения интента: сразу открыть новую запись о боли. */
        const val EXTRA_NEW_ENTRY = "com.headachediary.app.NEW_ENTRY"
        private const val STATE_LOCKED = "locked"

        /** Если приложение было свёрнуто дольше этого времени, при возврате снова просим разблокировку. */
        private const val LOCK_GRACE_MS = 30_000L
    }

    /** Кнопка «Была боль» в уведомлении просит сразу открыть новую запись. */
    private var newEntryRequest by mutableStateOf(false)

    private var lockEnabled by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var authenticating = false

    /** Подменяем контекст, чтобы интерфейс использовал язык, выбранный в настройках приложения. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localized())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_NEW_ENTRY, false)) newEntryRequest = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        ReminderScheduler.scheduleAll(this)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_NEW_ENTRY, false)) newEntryRequest = true

        // Если замок экрана на телефоне убрали, защиту приложения включённой оставить нельзя — иначе не войти.
        if (AppSettings.lockEnabled(this) && !isDeviceSecure()) AppSettings.setLockEnabled(this, false)
        lockEnabled = AppSettings.lockEnabled(this)
        // При пересоздании (смена языка, поворот) не просим разблокировку заново, если приложение уже открыто.
        locked = savedInstanceState?.getBoolean(STATE_LOCKED) ?: lockEnabled
        applySecureFlag()

        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.themeMode(this@MainActivity)) }
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Цвет значков в строке состояния и навигации должен следовать выбранной теме, а не системной.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { darkTheme },
                )
                onDispose { }
            }

            LaunchedEffect(locked) { if (locked) authenticate() }

            HeadacheTheme(darkTheme) {
                // Приложение остаётся в композиции под экраном блокировки, чтобы не потерять несохранённую запись.
                Box(Modifier.fillMaxSize()) {
                    App(
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            AppSettings.setThemeMode(this@MainActivity, it)
                        },
                        newEntryRequest = newEntryRequest,
                        onNewEntryHandled = { newEntryRequest = false },
                        language = AppSettings.language(this@MainActivity),
                        onLanguageChange = {
                            AppSettings.setLanguage(this@MainActivity, it)
                            // Виджет рисуется отдельно от приложения, поэтому перерисовываем его на новом языке.
                            val appContext = applicationContext
                            CoroutineScope(Dispatchers.IO).launch { PainWidget.updateAll(appContext) }
                            // Пересоздаём Activity, чтобы весь интерфейс перечитал строки на новом языке.
                            this@MainActivity.recreate()
                        },
                        lockEnabled = lockEnabled,
                        onLockChange = { on ->
                            AppSettings.setLockEnabled(this@MainActivity, on)
                            lockEnabled = on
                            applySecureFlag()
                        },
                    )
                    if (locked) LockScreen(onUnlock = ::authenticate)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, locked)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lockEnabled && !locked && stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > LOCK_GRACE_MS) {
            locked = true
        }
    }

    private fun isDeviceSecure(): Boolean = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** При включённой защите содержимое не попадает в скриншоты и в список недавних приложений. */
    private fun applySecureFlag() {
        if (lockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Suppress("DEPRECATION")
    private fun authenticate() {
        if (authenticating) return
        if (!isDeviceSecure()) {
            AppSettings.setLockEnabled(this, false)
            lockEnabled = false
            locked = false
            applySecureFlag()
            return
        }
        authenticating = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Остаёмся на экране блокировки; там есть кнопка «Разблокировать».
                    authenticating = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}

private data class Tab(val titleRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_journal, Icons.Default.List),
    Tab(R.string.tab_calendar, Icons.Default.DateRange),
    Tab(R.string.tab_stats, Icons.Default.Info),
    Tab(R.string.tab_settings, Icons.Default.Settings),
)

@Composable
fun App(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    newEntryRequest: Boolean,
    onNewEntryHandled: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val painFreeDays by vm.painFreeDays.collectAsStateWithLifecycle()
    val dayFactors by vm.dayFactors.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } }

    // Кнопка «Была боль» в уведомлении: сразу создаём запись со временем «сейчас» и открываем её.
    LaunchedEffect(newEntryRequest) {
        if (newEntryRequest) {
            vm.startNow { id -> editingId = id }
            onNewEntryHandled()
        }
    }

    // При запуске обновляем запомненное местоположение: виджету в фоне Android живые координаты не отдаёт.
    LaunchedEffect(Unit) {
        vm.refreshLocation()
        vm.fillRecentHealth()
    }

    if (editingId != null) {
        // Пока только что созданная запись ещё не пришла из базы, показываем пустой экран.
        if (editing != null) {
            EditorScreen(
                entry = editing,
                onSave = vm::save,
                onDelete = {
                    vm.delete(editing)
                    editingId = null
                },
                onClose = { editingId = null },
                onEnsureWeather = { vm.ensureWeather(editing) },
                onRefreshWeather = { done -> vm.refreshWeather(editing.id, done) },
                onEnsureHealth = { vm.ensureHealth(editing) },
                onRefreshHealth = { done -> vm.refreshHealth(editing.id, done) },
            )
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    val title = stringResource(t.titleRes)
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = title) },
                        label = { Text(title, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> JournalScreen(
                entries = entries,
                painFreeDays = painFreeDays,
                dayFactors = dayFactors,
                // Время фиксируется в момент нажатия, затем сразу открывается форма деталей.
                onPainNow = { vm.startNow { id -> editingId = id } },
                onOpen = { editingId = it.id },
                onEnd = vm::endNow,
                onMarkPainFree = vm::markPainFree,
                onUnmarkPainFree = vm::unmarkPainFree,
                onSaveFactors = vm::saveDayFactors,
                modifier = m,
            )
            1 -> CalendarScreen(
                entries = entries,
                painFreeDays = painFreeDays,
                dayFactors = dayFactors,
                onOpen = { editingId = it.id },
                onAddForDate = { date: LocalDate ->
                    val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
                    vm.addAt(date.atTime(time).toMillis()) { id -> editingId = id }
                },
                onMarkPainFree = vm::markPainFree,
                onUnmarkPainFree = vm::unmarkPainFree,
                onSaveFactors = vm::saveDayFactors,
                modifier = m,
            )
            2 -> StatsScreen(entries = entries, painFreeDays = painFreeDays, dayFactors = dayFactors, modifier = m)
            else -> SettingsScreen(
                entries = entries,
                painFreeDays = painFreeDays,
                vm = vm,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                lockEnabled = lockEnabled,
                onLockChange = onLockChange,
                modifier = m,
            )
        }
    }
}
