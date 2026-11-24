package com.blkdem.autoban

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.ui.AutobanTheme
import com.blkdem.autoban.ui.EventDialog
import com.blkdem.autoban.ui.JournalScreen
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.ui.ListsScreen
import com.blkdem.autoban.ui.RuleEditor
import com.blkdem.autoban.ui.RulesScreen
import com.blkdem.autoban.ui.TesterDialog
import com.blkdem.autoban.ui.StatusCard
import com.blkdem.autoban.ui.SystemStatus

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }
    private val permRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    private val app get() = AutobanApp.from(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AutobanTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun status() = SystemStatus(
        roleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        roleHeld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        phoneStateGranted = granted(Manifest.permission.READ_PHONE_STATE),
    )

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        roleRequest.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private enum class Tab(val title: String, val icon: ImageVector) {
        JOURNAL("Журнал", Icons.AutoMirrored.Filled.List),
        RULES("Правила", Icons.Filled.Star),
        LISTS("Списки", Icons.Filled.Lock),
        SETTINGS("Настройки", Icons.Filled.Settings),
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Root() {
        var tab by rememberSaveable { mutableStateOf(Tab.JOURNAL) }
        var opened by remember { mutableStateOf<CallEvent?>(null) }
        var editing by remember { mutableStateOf<Rule?>(null) }
        var tester by remember { mutableStateOf(false) }
        val tick = resumeTick
        val status = remember(tick) { status() }
        val settingsChanges by app.settings.changes.collectAsState()
        val protection = remember(settingsChanges) { app.settings.protectionEnabled }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Autoban · ${tab.title}") }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach {
                        NavigationBarItem(
                            selected = tab == it, onClick = { tab = it },
                            icon = { Icon(it.icon, contentDescription = null) }, label = { Text(it.title) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Tab.JOURNAL -> Column {
                        if (!status.roleHeld || !protection) {
                            StatusCard(status, protection, ::requestRole,
                                { permRequest.launch(Manifest.permission.READ_PHONE_STATE) },
                                { app.settings.protectionEnabled = it })
                        }
                        JournalScreen(app.repository, onOpen = { opened = it })
                    }
                    Tab.RULES -> editing?.let { r ->
                        RuleEditor(
                            r,
                            onSave = { thread { app.repository.saveRule(it) }; editing = null },
                            onDelete = { thread { app.repository.deleteRule(it.id) }; editing = null },
                            onClose = { editing = null },
                        )
                    } ?: RulesScreen(app.repository, onEdit = { editing = it }) {
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editing = Rule(name = "", conditions = emptyList()) }) { Text("+ Правило") }
                            OutlinedButton(onClick = { tester = true }) { Text("Проверить звонок") }
                        }
                    }
                    Tab.LISTS -> ListsScreen(app.repository)
                    Tab.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        StatusCard(status, protection, ::requestRole,
                            { permRequest.launch(Manifest.permission.READ_PHONE_STATE) },
                            { app.settings.protectionEnabled = it })
                    }
                }
            }
        }
        opened?.let { EventDialog(it, onDismiss = { opened = null }) }
        if (tester) TesterDialog(simulate = app.processor::simulate, onDismiss = { tester = false })
    }
}
