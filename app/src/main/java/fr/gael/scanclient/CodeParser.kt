package fr.gael.scanclient

/** Un préfixe de magasin de la liste configurée. clientLen = nombre de chiffres du n° client (null = 6 ou 7). */
data class StoreDef(val prefix: String, val clientLen: Int? = null)

sealed interface Reading {
    /** Magasin reconnu dans la liste + n° client valide. [store] est toujours la forme de la liste. */
    data class Match(val store: String, val client: String) : Reading {
        val full: String get() = store + client
    }

    /** Forme plausible (lettres + chiffres) mais magasin absent de la liste. */
    data class Unknown(val prefix: String, val client: String) : Reading

    /** Plusieurs magasins de la liste sont aussi probables l'un que l'autre. */
    data class Ambiguous(val stores: List<String>, val client: String) : Reading
}

/**
 * Lecture stricte d'un code `<préfixe de magasin><n° client>` à partir d'un texte OCR.
 *
 * Seuls les préfixes de la liste sont acceptés. L'OCR confond souvent des caractères proches ;
 * la comparaison avec la liste tolère ces confusions, et le code renvoyé est toujours celui de la liste :
 *  - confusions fortes (coût 1) : 0/O/D/Q/G, 1/I, 5/S, 8/B, 2/Z
 *  - confusions faibles (coût 2) : entre 5, 6, 8, B, S (ex. LYON06 lu LYONDB)
 *  - dans le n° client, une lettre lue à la place d'un chiffre est corrigée (coût 1)
 * Le magasin de plus faible coût gagne ; à coût égal entre deux magasins, la lecture est ambiguë.
 *
 * Pas de dépendance Android : testable en JUnit pur.
 */
object CodeParser {
    const val MIN_LEN = 8
    const val MAX_LEN = 16
    const val MAX_COST = 6
    val DEFAULT_CLIENT_LENS = listOf(6, 7)

    private val STRONG: Map<Char, Char> = buildMap {
        "0ODQG".forEach { put(it, '0') }
        "1I".forEach { put(it, '1') }
        "5S".forEach { put(it, '5') }
        "8B".forEach { put(it, '8') }
        "2Z".forEach { put(it, '2') }
    }
    private const val WEAK = "568BS"
    private val DIGIT_FIX = mapOf(
        'O' to '0', 'D' to '0', 'Q' to '0', 'G' to '0',
        'I' to '1', 'L' to '1', 'S' to '5', 'B' to '8', 'Z' to '2',
    )

    /** Lettres qui, dans un résultat d'OCR flou, ressemblent à des zéros : elles ne comptent pas comme "vraies" lettres. */
    private const val NOT_REAL_LETTERS = "ODQG"

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private fun cls(c: Char): Char = STRONG[c] ?: c

    private fun prefixCost(read: Char, expected: Char): Int? = when {
        read == expected -> 0
        cls(read) == cls(expected) -> 1
        read in WEAK && expected in WEAK -> 2
        else -> null
    }

    private class Candidate(val store: String, val cost: Int, val client: String)

    private fun match(t: String, s: StoreDef): Candidate? {
        val lens = s.clientLen?.let { listOf(it) } ?: DEFAULT_CLIENT_LENS
        for (n in lens) {
            if (t.length != s.prefix.length + n) continue
            var cost = 0
            for (i in s.prefix.indices) cost += prefixCost(t[i], s.prefix[i]) ?: return null
            val client = StringBuilder()
            for (c in t.substring(s.prefix.length)) {
                if (c.isDigit()) {
                    client.append(c)
                    continue
                }
                val d = DIGIT_FIX[c] ?: return null
                client.append(d)
                cost += 1
            }
            if (cost > MAX_COST) return null
            return Candidate(s.prefix, cost, client.toString())
        }
        return null
    }

    /** @return null si le texte n'a pas la forme d'un code (adresse, nom, ligne de routage, lecture trop courte...). */
    fun read(raw: String, stores: List<StoreDef>): Reading? {
        val t = normalize(raw)
        if (t.length !in MIN_LEN..MAX_LEN) return null

        val candidates = stores.mapNotNull { match(t, it) }
        if (candidates.isNotEmpty()) {
            val min = candidates.minOf { it.cost }
            val best = candidates.filter { it.cost == min }
            return if (best.size == 1) {
                Reading.Match(best[0].store, best[0].client)
            } else {
                Reading.Ambiguous(best.map { it.store }.sorted(), best[0].client)
            }
        }
        return unknown(t)
    }

    /** Forme plausible d'un code dont le magasin n'est pas dans la liste (pour l'info "magasin inconnu"). */
    private fun unknown(t: String): Reading.Unknown? {
        var i = t.length
        while (i > 0 && t[i - 1].isDigit()) i--
        val run = t.length - i
        if (run < 6) return null
        val n = if (run >= 7) 7 else 6
        val prefix = t.substring(0, t.length - n)
        if (prefix.length < 3 || prefix[0] !in 'A'..'Z') return null
        // Une lecture floue donne des "ALOO0", "LYOOO"... : il faut au moins 3 vraies lettres
        if (prefix.count { it in 'A'..'Z' && it !in NOT_REAL_LETTERS } < 3) return null
        return Reading.Unknown(prefix, t.substring(t.length - n))
    }
}
