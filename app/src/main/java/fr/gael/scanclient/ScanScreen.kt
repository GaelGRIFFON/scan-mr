package fr.gael.scanclient

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class AddStoreRequest(val prefix: String, val client: String?)

@Composable
fun ScanScreen(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val scans by vm.scans.collectAsState()
    val status by vm.status.collectAsState()
    val tray by vm.unknownTray.collectAsState()
    val session by vm.currentSession.collectAsState()
    val recap by vm.recap.collectAsState()

    var editing by remember { mutableStateOf<ScanEntity?>(null) }
    var addStore by remember { mutableStateOf<AddStoreRequest?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    // Le plus récent est en haut (sous la caméra) : on remonte au début à chaque nouveau code
    val listState = rememberLazyListState()
    val newestId = scans.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (scans.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Smartphone portrait : caméra en haut, liste dessous. Paysage (tablette ou téléphone) : caméra à gauche, liste à droite.
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val camHeight = (cfg.screenHeightDp * 0.36f).dp.coerceIn(220.dp, 420.dp)

    val trayContent: @Composable () -> Unit = {
        if (tray.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Ui.InfoBg).horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tray.forEach { u ->
                    Text("Magasin ${u.prefix} inconnu", color = Ui.Info, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { addStore = AddStoreRequest(u.prefix, u.client) }) { Text("Ajouter") }
                    TextButton(onClick = { vm.dismissUnknown(u.prefix) }) { Text("Ignorer", color = Ui.Muted) }
                }
            }
        }
    }

    val listContent: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(session?.name ?: "", style = MaterialTheme.typography.labelLarge, color = Ui.Muted)
                Text("${scans.size} code(s)", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { addStore = AddStoreRequest("", null) }) { Text("+ Magasin") }
            TextButton(onClick = { confirmEnd = true }) { Text("Terminer", color = Ui.Danger) }
        }
        if (recap.isNotEmpty()) {
            Text(
                recap.joinToString("  ·  ") { "${it.store} ×${it.n}" },
                style = MaterialTheme.typography.bodySmall,
                color = Ui.Muted,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f), state = listState) {
            items(scans, key = { it.id }) { e ->
                ScanRow(
                    e,
                    isLast = e.id == newestId,
                    onClick = { editing = e },
                    onDelete = {
                        vm.deleteScan(e)
                        scope.launch {
                            val r = snack.showSnackbar("${e.full} supprimé", "Annuler", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.restoreScan(e)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        if (landscape) {
            Row(Modifier.padding(pad).fillMaxSize()) {
                CameraSection(vm, granted, status, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    trayContent()
                    listContent()
                }
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize()) {
                CameraSection(vm, granted, status, Modifier.fillMaxWidth().height(camHeight))
                trayContent()
                listContent()
            }
        }
    }

    editing?.let { e ->
        var store by remember(e.id) { mutableStateOf(e.store) }
        var client by remember(e.id) { mutableStateOf(e.client) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Corriger le code") },
            text = {
                Column {
                    OutlinedTextField(
                        store, { store = it }, label = { Text("Magasin") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                    OutlinedTextField(
                        client, { client = it }, label = { Text("N° client") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.updateScan(e, store, client); editing = null }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }

    addStore?.let { req ->
        StoreDialog(
            title = "Ajouter un magasin",
            initialPrefix = req.prefix,
            initialLen = null,
            hint = if (req.client != null) "Corrigez le préfixe si besoin : le code lu sera enregistré avec." else null,
            onConfirm = { p, len ->
                vm.addStore(p, len, req.client)
                addStore = null
            },
            onDismiss = { addStore = null },
        )
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "Terminer la session ?",
            text = "Les ${scans.size} codes sont conservés. Vous pourrez les exporter plus tard depuis l'accueil.",
            confirmLabel = "Terminer",
            onConfirm = { vm.endSession(); confirmEnd = false },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun CameraSection(vm: ScanViewModel, granted: Boolean, status: Status, modifier: Modifier) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    Box(modifier.background(Color.Black)) {
        if (granted) {
            CameraPreview(vm, Modifier.fillMaxSize()) { camera = it }
        } else {
            Text("Autorisez la caméra pour scanner", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Repère visuel : le code le plus proche du centre est celui qui est pris
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(0.85f).height(80.dp)
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        )

        FilterChip(
            selected = false,
            onClick = { vm.goHome() },
            label = { Text("‹ Accueil") },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
        FilterChip(
            selected = torch,
            onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
            label = { Text("Lampe") },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )

        StatusBanner(status, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatusBanner(status: Status, modifier: Modifier) {
    val (text, color) = when (status) {
        Status.Idle -> "Placez le code dans le cadre" to Color(0xB3000000)
        is Status.Saved -> "✔ ${status.code}" to Ui.Success
        is Status.Duplicate -> "Déjà scanné : ${status.code}" to Ui.Warn
        is Status.UnknownStore -> "Magasin ${status.prefix} inconnu" to Ui.Info
        is Status.Uncertain -> "Lecture incertaine (${status.stores.joinToString(" ou ")}) : rapprochez-vous" to Ui.Warn
    }
    Box(modifier.fillMaxWidth().background(color).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CameraPreview(vm: ScanViewModel, modifier: Modifier, onCamera: (Camera) -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val alive = remember { AtomicBoolean(true) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    // Quitter l'écran de scan libère la caméra
    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            providerHolder[0]?.unbindAll()
            executor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(modifier = modifier, factory = { c ->
        val previewView = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            if (!alive.get()) return@addListener
            val provider = future.get()
            providerHolder[0] = provider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                // 1080p minimum : à 640x480 par défaut l'OCR rate les petits caractères
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        ).build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        executor,
                        CodeAnalyzer(recognizer, vm::storeDefs, vm::onMatch, vm::onUnknown, vm::onAmbiguous),
                    )
                }

            val selector = try {
                if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
            } catch (e: Exception) {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            onCamera(provider.bindToLifecycle(activity, selector, preview, analysis))
        }, ContextCompat.getMainExecutor(c))
        previewView
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanRow(e: ScanEntity, isLast: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) {
            onDelete(); true
        } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(Ui.Danger).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Supprimer", color = Color.White)
            }
        },
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            color = if (isLast) Ui.SuccessBg else MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = if (isLast) 20.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    if (isLast) Text("Dernier scan", style = MaterialTheme.typography.labelLarge, color = Ui.Success)
                    Text(
                        codeText(e.store, e.client, boldClient = isLast),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isLast) (if (wide) 40.sp else 30.sp) else (if (wide) 22.sp else 18.sp),
                    )
                }
                Text(fmtTime(e.ts), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
