package fr.gael.scanclient

/** Code client décomposé : magasin + numéro client. */
data class ParsedCode(
    val store: String,
    val client: String,
    /** true si le magasin a été reconnu dans la liste apprise (sinon découpage par heuristique). */
    val knownStore: Boolean,
) {
    val full: String get() = store + client
}

/**
 * Format observé : <magasin alphanumérique><numéro client numérique>, ex. ROQUE0005977, LAV530004757, ORLE002674.
 * La longueur n'est pas fixée : on apprend les codes magasin au fil des scans.
 *
 * Stratégie de découpage :
 *  1. Si le début du code correspond à un magasin déjà connu (tolérant aux confusions OCR 0/O, 1/I, 5/S, 8/B),
 *     le reste est le numéro client.
 *  2. Sinon heuristique : le numéro client = les 7 derniers chiffres (ou toute la fin numérique si elle est plus courte).
 *
 * Pas de dépendance Android : testable en JUnit pur.
 */
object CodeParser {
    const val MIN_LEN = 8
    const val MAX_LEN = 16
    const val MIN_CLIENT = 4
    const val MAX_CLIENT = 7

    private val TO_DIGIT = mapOf('O' to '0', 'I' to '1', 'L' to '1', 'S' to '5', 'B' to '8', 'Z' to '2')
    private val CONFUSABLE = setOf('0' to 'O', '1' to 'I', '1' to 'L', '5' to 'S', '8' to 'B', '2' to 'Z')

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** Corrige les lettres confondues avec des chiffres ; null s'il reste un caractère non numérique. */
    private fun fixDigits(s: String): String? {
        val sb = StringBuilder()
        for (c in s) {
            sb.append(if (c.isDigit()) c else TO_DIGIT[c] ?: return null)
        }
        return sb.toString()
    }

    private fun same(a: Char, b: Char) =
        a == b || (a to b) in CONFUSABLE || (b to a) in CONFUSABLE

    private fun looksLike(prefix: String, store: String) =
        prefix.length == store.length && prefix.indices.all { same(prefix[it], store[it]) }

    fun parse(raw: String, knownStores: Collection<String>): ParsedCode? {
        val t = normalize(raw)
        if (t.length !in MIN_LEN..MAX_LEN) return null

        // 1. Magasin déjà connu (le plus long d'abord : LAV53 avant LAV)
        for (s in knownStores.sortedByDescending { it.length }) {
            if (t.length - s.length !in MIN_CLIENT..MAX_CLIENT) continue
            if (!looksLike(t.substring(0, s.length), s)) continue
            val client = fixDigits(t.substring(s.length)) ?: continue
            return ParsedCode(s, client, knownStore = true)
        }

        // 2. Heuristique : un magasin inconnu doit commencer par 3 lettres (écarte adresses, n° postaux...)
        if (!t.take(3).all { it in 'A'..'Z' }) return null
        // fin numérique (chiffres + O/I probablement mal lus)
        var i = t.length
        while (i > 0 && (t[i - 1].isDigit() || t[i - 1] == 'O' || t[i - 1] == 'I')) i--
        val run = t.length - i
        if (run < MIN_CLIENT) return null
        val cut = t.length - minOf(run, MAX_CLIENT)
        if (cut < 3) return null
        val client = fixDigits(t.substring(cut)) ?: return null
        return ParsedCode(t.substring(0, cut), client, knownStore = false)
    }
}
