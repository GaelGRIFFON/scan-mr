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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun ScanApp(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val scans by vm.scans.collectAsState()
    val status by vm.status.collectAsState()
    var editing by remember { mutableStateOf<ScanEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // Le dernier scan est en haut de la liste (juste sous la caméra) : on remonte au début à chaque nouveau code
    val listState = rememberLazyListState()
    val lastId = scans.firstOrNull()?.id
    LaunchedEffect(lastId) {
        if (scans.isNotEmpty()) listState.animateScrollToItem(0)
    }

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            CameraSection(vm, granted, status)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${scans.size} code(s)", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(enabled = scans.isNotEmpty(), onClick = { confirmClear = true }) { Text("Vider") }
                    TextButton(
                        enabled = scans.isNotEmpty(),
                        onClick = { scope.launch { ctx.startActivity(vm.exportIntent()) } },
                    ) { Text("Exporter CSV") }
                }
            }
            HorizontalDivider()

            LazyColumn(Modifier.weight(1f), state = listState) {
                items(scans, key = { it.id }) { e ->
                    ScanRow(
                        e,
                        isLast = e.id == lastId,
                        onClick = { editing = e },
                        onDelete = {
                            vm.delete(e)
                            scope.launch {
                                val r = snack.showSnackbar("${e.full} supprimé", "Annuler", duration = androidx.compose.material3.SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) vm.restore(e)
                            }
                        },
                    )
                    HorizontalDivider()
                }
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
            confirmButton = { TextButton(onClick = { vm.update(e, store, client); editing = null }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Les ${scans.size} codes et la liste des magasins appris seront supprimés. Pense à exporter avant.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun CameraSection(vm: ScanViewModel, granted: Boolean, status: Status) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black)) {
        if (granted) {
            CameraPreview(vm, Modifier.fillMaxSize()) { camera = it }
        } else {
            Text("Autorise la caméra pour scanner", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Repère visuel : le code le plus proche du centre est celui qui est pris
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(0.85f).height(90.dp)
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
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
        Status.Idle -> "Vise le code client" to Color(0x99000000)
        is Status.Saved -> (if (status.newStore) "✔ ${status.code} · nouveau magasin" else "✔ ${status.code}") to Color(0xFF2E7D32)
        is Status.Duplicate -> "Déjà scanné : ${status.code}" to Color(0xFFEF6C00)
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
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(modifier = modifier, factory = { c ->
        val previewView = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            val provider = future.get()
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
                .also { it.setAnalyzer(executor, CodeAnalyzer(recognizer, vm::knownStores, vm::onCandidate)) }

            provider.unbindAll()
            onCamera(provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        }, ContextCompat.getMainExecutor(c))
        previewView
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanRow(e: ScanEntity, isLast: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) {
            onDelete(); true
        } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(Color(0xFFC62828)).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Supprimer", color = Color.White)
            }
        },
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            color = if (isLast) Color(0xFFE6F4EC) else MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = if (isLast) 20.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    if (isLast) {
                        Text("Dernier scan", style = MaterialTheme.typography.labelLarge, color = Color(0xFF16794A))
                    }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(e.store) }
                            withStyle(SpanStyle(fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)) { append(e.client) }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isLast) 30.sp else 18.sp,
                    )
                }
                Text(
                    SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(e.ts)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
