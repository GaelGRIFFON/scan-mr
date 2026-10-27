package fr.gael.scanclient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {
    val Primary = Color(0xFF1D4ED8)
    val Success = Color(0xFF16794A)
    val SuccessBg = Color(0xFFE6F4EC)
    val Warn = Color(0xFFB45309)
    val Danger = Color(0xFFB42318)
    val Muted = Color(0xFF5B6675)
    val Info = Color(0xFF1E3A8A)
    val InfoBg = Color(0xFFE8EEFD)
}

fun fmtDateTime(ts: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ts))
fun fmtTime(ts: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(ts))

/** Magasin en couleur, n° client en noir. */
fun codeText(store: String, client: String, boldClient: Boolean = false): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Ui.Primary, fontWeight = FontWeight.Bold)) { append(store) }
    withStyle(SpanStyle(fontWeight = if (boldClient) FontWeight.Bold else FontWeight.Normal)) { append(client) }
}

/**
 * Logo de marque : affiche `res/drawable-nodpi/<resName>.png` s'il existe, sinon le nom en texte.
 * Résolu à l'exécution pour que l'app compile même sans les fichiers de logo.
 */
@Composable
fun BrandLogo(resName: String, fallbackText: String, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val ctx = LocalContext.current
    val id = remember(resName) { ctx.resources.getIdentifier(resName, "drawable", ctx.packageName) }
    if (id != 0) {
        Image(
            painter = painterResource(id),
            contentDescription = fallbackText,
            modifier = modifier.height(height),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(fallbackText, modifier = modifier, fontWeight = FontWeight.Bold, color = Ui.Muted)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Ui.Primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
    ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun OutlineBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ui.Primary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Ajout / édition d'un préfixe de magasin.
 * @param onDelete non null = mode édition avec bouton "Supprimer".
 */
@Composable
fun StoreDialog(
    title: String,
    initialPrefix: String,
    initialLen: Int?,
    hint: String? = null,
    onConfirm: (prefix: String, len: Int?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var prefix by remember { mutableStateOf(initialPrefix) }
    var len by remember { mutableStateOf(initialLen?.toString() ?: "") }
    val cleanPrefix = CodeParser.normalize(prefix)
    val lenValue = len.toIntOrNull()
    val lenOk = len.isBlank() || (lenValue != null && lenValue in 4..10)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) Text(hint, color = Ui.Muted, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Préfixe du magasin") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = len,
                    onValueChange = { len = it.filter { c -> c.isDigit() } },
                    label = { Text("Chiffres du n° client") },
                    placeholder = { Text("vide = 6 ou 7") },
                    singleLine = true,
                    isError = !lenOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanPrefix.length >= 2 && lenOk,
                onClick = { onConfirm(cleanPrefix, if (len.isBlank()) null else lenValue) },
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Column {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Supprimer", color = Ui.Danger) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) Ui.Danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    singleLine: Boolean = true,
    hint: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) Text(hint, color = Ui.Muted, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = singleLine,
                    modifier = if (singleLine) Modifier else Modifier.fillMaxWidth().height(220.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
