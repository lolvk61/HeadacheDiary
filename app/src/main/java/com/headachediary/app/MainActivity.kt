package com.headachediary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.headachediary.app.ui.CalendarScreen
import com.headachediary.app.ui.EditorScreen
import com.headachediary.app.ui.HeadacheTheme
import com.headachediary.app.ui.JournalScreen
import com.headachediary.app.ui.StatsScreen
import com.headachediary.app.ui.toMillis
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeadacheTheme { App() }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Журнал", Icons.Default.List),
    Tab("Календарь", Icons.Default.DateRange),
    Tab("Статистика", Icons.Default.Info),
)

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } }

    if (editingId != null) {
        // Пока запись только что созданной ещё не пришла из базы, показываем пустой экран.
        if (editing != null) {
            EditorScreen(
                entry = editing,
                onSave = vm::save,
                onDelete = {
                    vm.delete(editing)
                    editingId = null
                },
                onClose = { editingId = null },
            )
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> JournalScreen(
                entries = entries,
                // Время фиксируется в момент нажатия, затем сразу открывается форма деталей.
                onPainNow = { vm.startNow { id -> editingId = id } },
                onOpen = { editingId = it.id },
                onEnd = vm::endNow,
                modifier = m,
            )
            1 -> CalendarScreen(
                entries = entries,
                onOpen = { editingId = it.id },
                onAddForDate = { date: LocalDate ->
                    val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
                    vm.addAt(date.atTime(time).toMillis()) { id -> editingId = id }
                },
                modifier = m,
            )
            else -> StatsScreen(entries = entries, modifier = m)
        }
    }
}
