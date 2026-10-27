package fr.gael.scanclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeParserTest {
    private val stores = listOf(
        "GRENO", "ROQUE", "STNAZA", "TOUL", "BAYONN", "LYON02", "LYON06",
    ).map { StoreDef(it) }

    private fun full(raw: String) = (CodeParser.read(raw, stores) as? Reading.Match)?.full

    @Test fun exactCode() = assertEquals("ROQUE0005977", full("ROQUE0005977"))

    @Test fun lyonWithBothSuffixes() {
        assertEquals("LYON060015425", full("LYON060015425"))
        assertEquals("LYON020008025", full("LYON020008025"))
    }

    @Test fun ocrConfusionsInPrefix() {
        assertEquals("STNAZA0005576", full("STNAZAD005576"))
        assertEquals("TOUL0004000", full("TOULOG04000"))
        assertEquals("GRENO0007309", full("GREN00007309"))
        assertEquals("ROQUE0005977", full("R0QUE0005977"))
    }

    @Test fun lyon06WeakConfusions() {
        assertEquals("LYON060002815", full("LYONDB0002815"))
        assertEquals("LYON060008101", full("LYONDS0008101"))
        assertEquals("LYON060003573", full("LYONO60003573"))
    }

    @Test fun lyon02Confusion() = assertEquals("LYON020004558", full("LYONO20004558"))

    @Test fun lettersInClientPartAreFixed() = assertEquals("ROQUE0005977", full("ROQUEOOO5977"))

    @Test fun blurryReadsAreIgnored() {
        assertNull(CodeParser.read("ALOO00007736", stores))
        assertNull(CodeParser.read("LYOOO0001216", stores))
    }

    @Test fun tooShortOrNotACode() {
        assertNull(CodeParser.read("BAYONN00081", stores))
        assertNull(CodeParser.read("PRESE0426", stores))
        assertNull(CodeParser.read("12 RUE DE LA PAIX", stores))
    }

    @Test fun unknownStoreIsReportedNotMatched() {
        val r = CodeParser.read("ANTES0004485", stores)
        assertTrue(r is Reading.Unknown)
        assertEquals("ANTES", (r as Reading.Unknown).prefix)
    }

    @Test fun perStoreClientLength() {
        val s = listOf(StoreDef("CAEN", 5))
        assertEquals("CAEN12345", (CodeParser.read("CAEN12345", s) as Reading.Match).full)
        assertTrue(CodeParser.read("CAEN123456", s) !is Reading.Match)
    }

    @Test fun addingAStoreMakesUnknownRecognised() {
        assertTrue(CodeParser.read("ANTES0004485", stores) is Reading.Unknown)
        assertTrue(CodeParser.read("ANTES0004485", stores + StoreDef("ANTES")) is Reading.Match)
    }

    @Test fun numericPrefixesFromSeedList() {
        val s = listOf(
            StoreDef("AL000", 7), StoreDef("ANT06", 7), StoreDef("BAY1", 7), StoreDef("LY000", 7),
            StoreDef("LYON06"), StoreDef("ROQUE"), StoreDef("RO000", 7),
        )
        fun f(raw: String) = (CodeParser.read(raw, s) as? Reading.Match)?.full
        assertEquals("AL0000007736", f("ALOO00007736"))
        assertEquals("ANT060004485", f("ANTOB0004485"))
        assertEquals("BAY10000981", f("BAY10000981"))
        assertEquals("LY0000001216", f("LYOOO0001216"))
        assertEquals("LYON060010953", f("LYOND60010953"))
        // ROQUE (7 chiffres) et RO000 ne se confondent pas
        assertEquals("ROQUE0005977", f("ROQUE0005977"))
        assertEquals("RO0000005977", f("RO0000005977"))
    }
}
