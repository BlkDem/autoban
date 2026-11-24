package com.blkdem.autoban

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.screening.CallLogFile

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    private val permRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun hasRole(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun requestRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rm = getSystemService(RoleManager::class.java)
        roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    @Composable
    private fun Screen() {
        val tick = refresh
        val lines = remember(tick) { CallLogFile.readLast(this) }
        val role = remember(tick) { hasRole() }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Text("Autoban · этап 0 (логгер)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(if (role) "✅ Роль «Фильтр звонков» получена" else "❌ Роль «Фильтр звонков» не назначена")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!role) Button(onClick = ::requestRole) { Text("Назначить") }
                OutlinedButton(onClick = { permRequest.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("Доступ к SIM") }
                OutlinedButton(onClick = { refresh++ }) { Text("Обновить") }
            }
            Spacer(Modifier.height(12.dp))
            if (lines.isEmpty()) Text("Звонков пока не было.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lines) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
