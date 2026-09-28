package io.gh.kdbrian.ttac.ui

import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.draw.MarkGeometry
import io.gh.kdbrian.ttac.net.Aliases
import io.gh.kdbrian.ttac.net.LanSession
import io.gh.kdbrian.ttac.net.SessionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private fun total(strokes: List<List<androidx.compose.ui.geometry.Offset>>) = strokes.sumOf { MarkGeometry.length(it).toDouble() }.toFloat()

    @Test fun `partial strokes have proportional length`() {
        for (style in MarkStyle.entries) for (mark in Mark.entries) {
            val full = MarkGeometry.strokes(mark, style, 3)
            val len = total(full)
            for (f in listOf(0.1f, 0.5f, 0.75f)) {
                assertEquals("$style $mark $f", len * f, total(MarkGeometry.partial(full, f)), len * 0.01f)
            }
            assertTrue(MarkGeometry.partial(full, 0f).isEmpty())
            assertEquals(full, MarkGeometry.partial(full, 1f))
        }
    }

    @Test fun `cross draws its first stroke before the second`() {
        val half = MarkGeometry.partial(MarkGeometry.strokes(Mark.X, MarkStyle.SOLID, 0), 0.4f)
        assertEquals(1, half.size)
    }

    @Test fun `sketch geometry is deterministic per seed`() {
        assertEquals(MarkGeometry.strokes(Mark.O, MarkStyle.SKETCH, 5), MarkGeometry.strokes(Mark.O, MarkStyle.SKETCH, 5))
    }

    @Test fun `session codes round trip and hide the address`() {
        val code = SessionCode.encode("192.168.1.4", 40123)!!
        assertTrue(code.matches(Regex("[0-9A-Z]{5}-[0-9A-Z]{5}")))
        assertTrue("192" !in code)
        assertEquals("192.168.1.4" to 40123, SessionCode.decode(code))
        assertEquals("192.168.1.4" to 40123, SessionCode.decode(code.lowercase().replace("-", "")))
        assertEquals("10.0.0.255" to 1, SessionCode.decode(SessionCode.encode("10.0.0.255", 1)!!))
        assertNull(SessionCode.encode("300.1.1.1", 80))
        assertNull(SessionCode.decode("ABC"))
        assertNull(SessionCode.decode("UUUUU-UUUUU"))
    }

    @Test fun `aliases look like adjective animal number`() {
        val alias = Aliases.random(kotlin.random.Random(3))
        assertTrue(alias, alias.matches(Regex("[A-Z][a-z]+ [A-Z][a-z]+ \\d{2}")))
    }

    @Test fun `service names parse back to alias and host`() {
        assertEquals("Cosmic Otter 42" to "Ann", LanSession.parseServiceName("TTac|Cosmic Otter 42|Ann"))
        assertEquals("Mystery Session", LanSession.parseServiceName("Something else").first)
    }
}
