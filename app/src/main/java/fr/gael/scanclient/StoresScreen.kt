package fr.gael.scanclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StoresScreen(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val stores by vm.stores.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoreEntity?>(null) }
    var importing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.goHome() }) { Text("‹ Accueil") }
            Text("Magasins", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Seuls les codes commençant par l'un de ces préfixes, suivis de chiffres, sont enregistrés.",
                color = Ui.Muted,
            )
            BigButton("Ajouter un magasin", onClick = { adding = true })
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { importing = true }) { Text("Coller une liste") }
                TextButton(onClick = { ctx.startActivity(CsvExport.storesIntent(stores)) }, enabled = stores.isNotEmpty()) {
                    Text("Partager la liste")
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(stores, key = { it.prefix }) { st ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = st }.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(st.prefix, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ui.Primary)
                    Text(
                        if (st.clientLen != null) "${st.clientLen} chiffres" else "6 ou 7 chiffres",
                        color = Ui.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        StoreDialog(
            title = "Ajouter un magasin",
            initialPrefix = "",
            initialLen = null,
            onConfirm = { p, len -> vm.addStore(p, len); adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { st ->
        StoreDialog(
            title = "Modifier le magasin",
            initialPrefix = st.prefix,
            initialLen = st.clientLen,
            onConfirm = { p, len -> vm.updateStore(st.prefix, p, len); editing = null },
            onDismiss = { editing = null },
            onDelete = { vm.deleteStore(st.prefix); editing = null },
        )
    }
    if (importing) {
        TextInputDialog(
            title = "Coller une liste",
            label = "Un magasin par ligne",
            initial = "",
            confirmLabel = "Importer",
            singleLine = false,
            hint = "Format : PREFIXE, ou PREFIXE;chiffres (ex. LYON06;7)",
            onConfirm = { vm.importStores(it); importing = false },
            onDismiss = { importing = false },
        )
    }
}
