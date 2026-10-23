package fr.gael.scanclient

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ScanApp(vm: ScanViewModel) {
    val screen by vm.screen.collectAsState()
    BackHandler(enabled = screen != Screen.Home) { vm.goHome() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.Scan -> ScanScreen(vm)
            Screen.Detail -> DetailScreen(vm)
            Screen.Stores -> StoresScreen(vm)
        }
    }
}

@Composable
private fun HomeScreen(vm: ScanViewModel) {
    val sessions by vm.sessions.collectAsState()
    val stores by vm.stores.collectAsState()
    val open = sessions.firstOrNull { it.endedAt == null }
    val past = sessions.filter { it.endedAt != null }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(32.dp))
            Text("ScanClient", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ui.Primary)
            Text("Scannez les références clients, sans rien taper.", color = Ui.Muted)
            Spacer(Modifier.height(8.dp))
        }
        item { BigButton("Nouvelle session", onClick = { vm.newSession() }) }
        if (open != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Ui.SuccessBg),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Session en cours", style = MaterialTheme.typography.labelLarge, color = Ui.Success)
                        Text(open.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${open.scanCount} code(s)", color = Ui.Muted)
                        Spacer(Modifier.height(8.dp))
                        OutlineBigButton("Reprendre", onClick = { vm.resumeSession(open.id) }, color = Ui.Success)
                    }
                }
            }
        }
        item { OutlineBigButton("Magasins (${stores.size})", onClick = { vm.openStores() }) }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Sessions précédentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (past.isEmpty()) Text("Aucune pour le moment.", color = Ui.Muted)
        }
        items(past, key = { it.id }) { s ->
            Card(
                Modifier.fillMaxWidth().clickable { vm.viewSession(s.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Bold)
                        Text(fmtDateTime(s.createdAt), style = MaterialTheme.typography.bodySmall, color = Ui.Muted)
                    }
                    Text("${s.scanCount} ›", fontWeight = FontWeight.Bold, color = Ui.Primary)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DetailScreen(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by vm.currentSession.collectAsState()
    val scans by vm.scans.collectAsState()
    val recap by vm.recap.collectAsState()

    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val s = session
    if (s == null) {
        Text("Session introuvable", modifier = Modifier.padding(24.dp))
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.goHome() }) { Text("‹ Accueil") }
            Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "${fmtDateTime(s.createdAt)} · ${scans.size} code(s)",
                color = Ui.Muted,
            )
            if (recap.isNotEmpty()) {
                Text(recap.joinToString("  ·  ") { "${it.store} ×${it.n}" }, style = MaterialTheme.typography.bodyMedium)
            }
            BigButton("Exporter en CSV", onClick = {
                scope.launch { vm.exportIntent()?.let { ctx.startActivity(it) } }
            }, enabled = scans.isNotEmpty())
            OutlineBigButton("Reprendre cette session", onClick = { vm.resumeSession(s.id) })
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { rename = true }) { Text("Renommer") }
                TextButton(onClick = { confirmDelete = true }) { Text("Supprimer", color = Ui.Danger) }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(scans, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(codeText(e.store, e.client), fontFamily = FontFamily.Monospace, fontSize = 17.sp)
                    Text(fmtTime(e.ts), style = MaterialTheme.typography.bodySmall, color = Ui.Muted)
                }
                HorizontalDivider()
            }
        }
    }

    if (rename) {
        TextInputDialog(
            title = "Renommer la session",
            label = "Nom",
            initial = s.name,
            confirmLabel = "Enregistrer",
            onConfirm = { vm.renameSession(it); rename = false },
            onDismiss = { rename = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer la session ?",
            text = "Les ${scans.size} codes de cette session seront perdus. Pensez à l'exporter avant.",
            confirmLabel = "Supprimer",
            danger = true,
            onConfirm = { confirmDelete = false; vm.deleteCurrentSession() },
            onDismiss = { confirmDelete = false },
        )
    }
}
