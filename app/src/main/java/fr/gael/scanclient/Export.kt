package fr.gael.scanclient

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExport {
    private fun esc(s: String) =
        if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /**
     * CSV ';' en UTF-8 avec BOM (ouverture directe dans Excel FR), un fichier par session.
     * Attention : Excel supprime les zéros de tête de la colonne "client" ; la colonne "code" (texte) est sûre.
     */
    suspend fun chooserIntent(context: Context, session: SessionEntity, rows: List<ScanEntity>): Intent =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val safe = session.name.replace(Regex("[^A-Za-z0-9À-ÿ_-]+"), "_").trim('_').ifEmpty { "session" }
            val file = File(dir, "$safe.csv")
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)

            file.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("﻿")
                w.write("session;horodatage;magasin;client;code\r\n")
                rows.forEach {
                    w.write("${esc(session.name)};${df.format(Date(it.ts))};${it.store};${it.client};${it.full}\r\n")
                }
            }
            shareFile(context, file, "text/csv", "Exporter la session")
        }

    private fun shareFile(context: Context, file: File, mime: String, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /** Liste des magasins en texte ("PREFIXE" ou "PREFIXE;chiffres"), pour la partager / la recoller ailleurs. */
    fun storesText(stores: List<StoreEntity>): String =
        stores.joinToString("\n") { if (it.clientLen != null) "${it.prefix};${it.clientLen}" else it.prefix }

    fun storesIntent(stores: List<StoreEntity>): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, storesText(stores))
        }
        return Intent.createChooser(send, "Partager la liste des magasins")
    }
}
