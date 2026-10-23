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
    /**
     * CSV ';' en UTF-8 avec BOM (ouverture directe dans Excel FR).
     * Attention : Excel supprime les zéros de tête de la colonne "client" ; la colonne "code" (texte) est sûre.
     */
    suspend fun chooserIntent(context: Context, rows: List<ScanEntity>): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
        val file = File(dir, "scans_$stamp.csv")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)

        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("horodatage;magasin;client;code\r\n")
            rows.forEach { w.write("${df.format(Date(it.ts))};${it.store};${it.client};${it.full}\r\n") }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Exporter les codes")
    }
}
