package fr.gael.scanclient

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlin.math.hypot

/**
 * Lit le texte de chaque image, garde les lignes qui ressemblent à un code client,
 * choisit celle la plus proche du centre de l'image, et ne valide qu'après
 * [REQUIRED] images consécutives identiques (élimine les erreurs de lecture ponctuelles).
 */
class CodeAnalyzer(
    private val recognizer: TextRecognizer,
    private val knownStores: () -> Set<String>,
    private val onStable: (ParsedCode) -> Unit,
) : ImageAnalysis.Analyzer {

    private var last: String? = null
    private var streak = 0

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

    private fun handle(text: Text, w: Int, h: Int) {
        val known = knownStores()
        var best: ParsedCode? = null
        var bestDist = Float.MAX_VALUE

        for (block in text.textBlocks) for (line in block.lines) {
            val box = line.boundingBox ?: continue
            val dist = hypot(box.centerX().toFloat() / w - 0.5f, box.centerY().toFloat() / h - 0.5f)
            if (dist >= bestDist) continue
            val candidates = line.elements.map { it.text } + line.text
            val parsed = candidates.firstNotNullOfOrNull { CodeParser.parse(it, known) } ?: continue
            best = parsed
            bestDist = dist
        }

        val key = best?.full
        if (key == null) {
            last = null
            streak = 0
            return
        }
        if (key == last) streak++ else {
            last = key
            streak = 1
        }
        if (streak == REQUIRED) onStable(best!!)
    }

    companion object {
        const val REQUIRED = 2
    }
}
