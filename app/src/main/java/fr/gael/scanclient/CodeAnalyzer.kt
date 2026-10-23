package fr.gael.scanclient

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlin.math.hypot

/**
 * Lit le texte de chaque image et ne garde que ce qui ressemble à un code (voir [CodeParser]).
 *
 * Vote sur une fenêtre glissante (~1,2 s) plutôt que "2 images consécutives identiques" :
 *  - un code est validé quand il est lu [REQUIRED] fois dans la fenêtre ; pour un même n° client,
 *    le magasin le plus fréquent gagne (un tir à égalité n'est pas validé) ;
 *  - un magasin inconnu n'est signalé que s'il est lu [UNKNOWN_REQUIRED] fois (les lectures floues varient d'une image à l'autre) ;
 *  - une lecture ambiguë (deux magasins possibles) est signalée mais jamais enregistrée.
 * Les callbacks sont appelés sur le thread principal (callbacks ML Kit).
 */
class CodeAnalyzer(
    private val recognizer: TextRecognizer,
    private val stores: () -> List<StoreDef>,
    private val onMatch: (Reading.Match) -> Unit,
    private val onUnknown: (Reading.Unknown) -> Unit,
    private val onAmbiguous: (Reading.Ambiguous) -> Unit,
) : ImageAnalysis.Analyzer {

    private class Obs(val t: Long, val r: Reading)

    private val window = ArrayDeque<Obs>()
    private var lastMatchKey: String? = null
    private var lastUnknown: String? = null
    private var lastAmbiguous: String? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val upright = rotation % 180 == 0
        val w = if (upright) proxy.width else proxy.height
        val h = if (upright) proxy.height else proxy.width

        recognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { handle(it, w, h) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun rank(r: Reading) = when (r) {
        is Reading.Match -> 3
        is Reading.Ambiguous -> 2
        is Reading.Unknown -> 1
    }

    /** Meilleure lecture de l'image : un code reconnu prime, à égalité on prend le plus proche du centre. */
    private fun handle(text: Text, w: Int, h: Int) {
        val defs = stores()
        var best: Reading? = null
        var bestRank = -1
        var bestDist = Float.MAX_VALUE

        for (block in text.textBlocks) for (line in block.lines) {
            val box = line.boundingBox ?: continue
            val dist = hypot(box.centerX().toFloat() / w - 0.5f, box.centerY().toFloat() / h - 0.5f)
            val reading = (line.elements.map { it.text } + line.text)
                .mapNotNull { CodeParser.read(it, defs) }
                .maxByOrNull { rank(it) } ?: continue
            val r = rank(reading)
            if (r > bestRank || (r == bestRank && dist < bestDist)) {
                best = reading
                bestRank = r
                bestDist = dist
            }
        }
        record(best)
    }

    private fun record(r: Reading?) {
        val now = SystemClock.elapsedRealtime()
        while (window.isNotEmpty() && now - window.first().t > WINDOW_MS) window.removeFirst()
        if (r != null) window.addLast(Obs(now, r))

        // 1. Codes reconnus
        val matches = window.mapNotNull { it.r as? Reading.Match }
        if (matches.isEmpty()) lastMatchKey = null
        for ((client, list) in matches.groupBy { it.client }) {
            val counts = list.groupingBy { it.store }.eachCount()
            val top = counts.maxByOrNull { it.value } ?: continue
            if (top.value < REQUIRED || counts.count { it.value == top.value } != 1) continue
            val key = top.key + client
            if (key == lastMatchKey) continue
            lastMatchKey = key
            onMatch(Reading.Match(top.key, client))
            return
        }

        // 2. Lectures ambiguës (jamais enregistrées)
        val ambiguous = window.mapNotNull { it.r as? Reading.Ambiguous }
        if (ambiguous.isEmpty()) lastAmbiguous = null
        val amb = ambiguous.groupBy { it.stores }.maxByOrNull { it.value.size }
        if (amb != null && amb.value.size >= UNKNOWN_REQUIRED) {
            val key = amb.key.joinToString("|")
            if (key != lastAmbiguous) {
                lastAmbiguous = key
                onAmbiguous(amb.value.last())
            }
        }

        // 3. Magasins inconnus
        val unknowns = window.mapNotNull { it.r as? Reading.Unknown }
        if (unknowns.isEmpty()) lastUnknown = null
        val unk = unknowns.groupBy { it.prefix }.maxByOrNull { it.value.size }
        if (unk != null && unk.value.size >= UNKNOWN_REQUIRED && unk.key != lastUnknown) {
            lastUnknown = unk.key
            onUnknown(unk.value.last())
        }
    }

    companion object {
        const val WINDOW_MS = 1_200L
        const val REQUIRED = 2
        const val UNKNOWN_REQUIRED = 3
    }
}
