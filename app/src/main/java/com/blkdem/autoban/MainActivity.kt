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
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import com.blkdem.autoban.data.Backup
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.CsvExport
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.ui.EventActions
import com.blkdem.autoban.ui.StatsCard
import com.blkdem.autoban.ui.AutobanTheme
import com.blkdem.autoban.ui.EventDialog
import com.blkdem.autoban.ui.JournalScreen
import com.blkdem.autoban.rules.ListImport
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.ui.ListsScreen
import com.blkdem.autoban.ui.RuleEditor
import com.blkdem.autoban.ui.RulesScreen
import com.blkdem.autoban.ui.TesterDialog
import com.blkdem.autoban.ui.JournalSettingsCard
import com.blkdem.autoban.ui.LookupCard
import com.blkdem.autoban.ui.LiveCard
import com.blkdem.autoban.ui.ReportSection
import com.blkdem.autoban.ui.StatusCard
import com.blkdem.autoban.ui.TelegramCard
import com.blkdem.autoban.ui.SystemStatus

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }
    private val permRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    private val permsRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }

    private val app get() = AutobanApp.from(this)

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    /** Записать текст в выбранный пользователем файл (SAF). */
    private fun writeTo(uri: Uri?, produce: () -> String, done: String) {
        if (uri == null) return
        thread {
            runCatching { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(produce().toByteArray(Charsets.UTF_8)) } }
                .onSuccess { toast(done) }.onFailure { toast("Ошибка: ${it.message}") }
        }
    }

    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        writeTo(uri, { CsvExport.journal(app.repository.events(Repository.EventFilter(limit = 1_000_000))) }, "Журнал сохранён")
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        writeTo(uri, { Backup.encode(app.repository.exportBackup()) }, "Резервная копия сохранена")
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) thread {
            runCatching {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val b = Backup.decode(text)
                app.repository.importBackup(b)
                b
            }.onSuccess { toast("Восстановлено: правил ${it.rules.size}, бан ${it.blocklist.size}, белых ${it.whitelist.size}") }
                .onFailure { toast("Не удалось прочитать файл: ${it.message}") }
        }
    }

    /** В какой список импортировать — запоминается до возврата из выбора файла. */
    private var importListType = ListType.BLOCK
    private val importList = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) thread {
            val type = importListType
            runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val parsed = ListImport.parse(ListImport.decode(bytes))
                parsed to app.repository.addAllToList(type, parsed.items, "импорт")
            }.onSuccess { (parsed, added) ->
                val dup = parsed.items.size - added
                toast(buildString {
                    append("Добавлено: $added")
                    if (dup > 0) append(", уже были: $dup")
                    if (parsed.skipped > 0) append(", не распознано строк: ${parsed.skipped}")
                })
            }.onFailure { toast("Не удалось прочитать файл: ${it.message}") }
        }
    }

    private fun stamp() = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AutobanTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Подхватить нажатия кнопок/команды бота, пока пользователь смотрит в приложение.
        thread {
            val s = app.telegramSettings.snapshot()
            if (s.enabled && s.configured && s.commands) com.blkdem.autoban.telegram.BotPoller.pollSoon(this)
        }
    }

    private fun status() = SystemStatus(
        roleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        roleHeld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        phoneStateGranted = granted(Manifest.permission.READ_PHONE_STATE),
        notificationAccess = com.blkdem.autoban.live.LiveCalls.hasNotificationAccess(this),
        answerCalls = granted(Manifest.permission.ANSWER_PHONE_CALLS),
        callLog = granted(Manifest.permission.READ_CALL_LOG),
        radioLog = com.blkdem.autoban.live.RadioLog.available(this),
    )

    private fun copyGrantCommand() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", com.blkdem.autoban.live.RadioLog.GRANT_COMMAND))
        toast("Команда скопирована — выполните её на компьютере с подключённым телефоном")
    }

    private fun openNotificationAccess() {
        runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { toast("Откройте: Настройки → Уведомления → Доступ к уведомлениям") }
    }

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

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                        StatsCard(app.repository)
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
                    Tab.LISTS -> ListsScreen(app.repository) { type ->
                        importListType = type
                        importList.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
                    }
                    Tab.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        StatusCard(status, protection, ::requestRole,
                            { permRequest.launch(Manifest.permission.READ_PHONE_STATE) },
                            { app.settings.protectionEnabled = it })
                        TelegramCard(
                            app.telegramSettings,
                            onSaved = { thread { app.telegram.schedule() } },
                            onRetryFailed = { thread { app.repository.retryFailedTg(); app.telegram.schedule() } },
                            onNeedPermissions = { permsRequest.launch(it) },
                        )
                        LiveCard(
                            status,
                            onNotificationAccess = ::openNotificationAccess,
                            onAnswerCalls = { permRequest.launch(Manifest.permission.ANSWER_PHONE_CALLS) },
                            onCallLog = { permRequest.launch(Manifest.permission.READ_CALL_LOG) },
                            onRadioLog = ::copyGrantCommand,
                        )
                        LookupCard(
                            enabled = remember(settingsChanges) { app.settings.lookupEnabled },
                            onChange = { app.settings.lookupEnabled = it },
                        )
                        com.blkdem.autoban.ui.SyncCard(app.repository, app.telegramSettings)
                        JournalSettingsCard(
                            retentionDays = remember(settingsChanges) { app.settings.retentionDays },
                            onRetention = { app.settings.retentionDays = it },
                            onClear = { thread { app.repository.clearEvents() } },
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { exportCsv.launch("autoban-journal-${stamp()}.csv") }) { Text("Журнал в CSV") }
                                OutlinedButton(onClick = { exportBackup.launch("autoban-backup-${stamp()}.json") }) { Text("Резервная копия") }
                                OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Восстановить") }
                            }
                            Text(
                                "Резервная копия — правила и списки. При восстановлении правила заменяются, списки дополняются.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        opened?.let { e ->
            EventDialog(e, onDismiss = { opened = null }) {
                ReportSection(e.id, e.number, app.repository, fetch = { n, force -> app.processor.report(n, force) })
                EventActions(e, app.repository, onCreateRule = { r -> opened = null; editing = r; tab = Tab.RULES })
            }
        }
        if (tester) TesterDialog(simulate = app.processor::simulate, onDismiss = { tester = false })
    }
}
