package io.gh.kdbrian.ttac.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WordHiveTest {

    @Test fun `disc sizes`() {
        assertEquals(19, Hex.disc(2).size)
        assertEquals(37, Hex.disc(3).size)
        assertEquals(61, Hex.disc(4).size)
    }

    @Test fun `straight lines only along axes`() {
        assertEquals(3, Hex.line(Hex(0, 0), Hex(2, 0))?.size)
        assertEquals(3, Hex.line(Hex(0, 0), Hex(2, -2))?.size)
        assertNull(Hex.line(Hex(0, 0), Hex(1, 1)))
    }

    @Test fun `every hidden word is spelled by a path of neighbours`() {
        for (level in 1..9) {
            val p = HivePuzzle.generate(HiveSpec(level), Random(level))
            assertTrue("level $level placed ${p.words.size}", p.words.size >= minOf(3, HiveSpec(level).wordCount))
            for (w in p.words) {
                assertEquals(w.word, p.spell(w.cells))
                assertEquals(w.cells.size, w.cells.toSet().size)
                w.cells.zipWithNext().forEach { (a, b) -> assertTrue(HivePuzzle.adjacent(a, b)) }
                assertEquals(HiveVerdict.Target(w), p.evaluate(w.cells, emptySet(), emptySet()))
            }
        }
    }

    @Test fun `verdicts cover repeat and rejection`() {
        val p = HivePuzzle.generate(HiveSpec(1), Random(7))
        val w = p.words.first()
        assertTrue(p.evaluate(w.cells, setOf(w.word), emptySet()) is HiveVerdict.Repeat)
        assertTrue(p.evaluate(w.cells.take(2), emptySet(), emptySet()) is HiveVerdict.Rejected)
    }

    @Test fun `shuffle keeps unfound words spellable and moves letters`() {
        val p = HivePuzzle.generate(HiveSpec(4), Random(11))
        val found = setOf(p.words.first().word)
        val s = p.shuffled(found, Random(12))
        assertEquals(p.words.map { it.word }, s.words.map { it.word })
        for (w in s.words.filter { it.word !in found }) {
            assertEquals(w.word, s.spell(w.cells))
        }
        assertTrue(s.letters != p.letters)
    }

    @Test fun `longer words earn bigger bonuses`() {
        val four = hiveScore("TREE", 1, extra = false, hinted = false)
        val six = hiveScore("GARDEN", 1, extra = false, hinted = false)
        val eight = hiveScore("TREEHOUSE".take(8), 1, extra = false, hinted = false)
        assertEquals(0, four.bonus)
        assertEquals("Great", six.tier)
        assertEquals("Legendary", eight.tier)
        assertTrue(eight.total > six.total && six.total > four.total)
        assertTrue(hiveScore("GARDEN", 1, extra = true, hinted = false).total < six.total)
        assertTrue(hiveScore("GARDEN", 1, extra = false, hinted = true).total < six.total)
    }

    @Test fun `dictionary includes targets and common words`() {
        assertTrue("HONEY" in WordBank.dictionary)
        assertTrue("HOUSE" in WordBank.dictionary)
    }
}
