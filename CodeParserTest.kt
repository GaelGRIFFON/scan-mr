package fr.gael.scanclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeParserTest {
    private val none = emptySet<String>()

    @Test fun roque() {
        val p = CodeParser.parse("ROQUE0005977", none)!!
        assertEquals("ROQUE", p.store); assertEquals("0005977", p.client)
    }

    @Test fun laval_departement_dans_le_magasin() {
        val p = CodeParser.parse("LAV530004757", none)!!
        assertEquals("LAV53", p.store); assertEquals("0004757", p.client)
    }

    @Test fun orle_client_court() {
        val p = CodeParser.parse("ORLE002674", none)!!
        assertEquals("ORLE", p.store); assertEquals("002674", p.client)
    }

    @Test fun magasin_connu_prioritaire() {
        val p = CodeParser.parse("LAV530004757", setOf("LAV", "LAV53"))!!
        assertEquals("LAV53", p.store)
    }

    @Test fun confusion_ocr_dans_le_magasin_corrigee_si_connu() {
        val p = CodeParser.parse("R0QUE0005977", setOf("ROQUE"))!!
        assertEquals("ROQUE", p.store); assertEquals("0005977", p.client)
    }

    @Test fun confusion_ocr_dans_le_client() {
        val p = CodeParser.parse("ROQUE000S977", setOf("ROQUE"))!!
        assertEquals("0005977", p.client)
        val q = CodeParser.parse("ROQUEOOO5977", none)!!
        assertEquals("0005977", q.client)
    }

    @Test fun lignes_a_ignorer() {
        assertNull(CodeParser.parse("M62 4446 306 0007321 00251 U 002004", none))
        assertNull(CodeParser.parse("MIENCE MARIE", none))
        assertNull(CodeParser.parse("7 B RUE DE LA DURANCE", none))
        assertNull(CodeParser.parse("75012 PARIS", none))
        assertNull(CodeParser.parse("VAN GLABEKE MORGANE", none))
        assertNull(CodeParser.parse("0007321", none))
    }
}
