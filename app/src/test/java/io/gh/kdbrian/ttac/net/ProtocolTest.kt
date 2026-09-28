package io.gh.kdbrian.ttac.net

import io.gh.kdbrian.ttac.net.LanSession.Companion.readBoundedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolTest {

    @Test fun `messages round trip`() {
        val messages = listOf(
            LanMessage.Hello("Zoë ✕", 0xFFFF3D7F, 4),
            LanMessage.Move(3, 7),
            LanMessage.Rematch(2),
            LanMessage.Bye,
        )
        for (m in messages) assertEquals(m, Protocol.decode(Protocol.encode(m)))
    }

    @Test fun `encoded messages are single lines`() {
        assert(!Protocol.encode(LanMessage.Hello("a\nb", 1, 3)).contains('\n'))
    }

    @Test fun `garbage and unknown types decode to null`() {
        assertNull(Protocol.decode("not json"))
        assertNull(Protocol.decode("""{"t":"nuke"}"""))
        assertNull(Protocol.decode("""{"t":"move","round":"x"}"""))
    }

    @Test fun `bounded line reader handles utf8 and eof`() {
        val input = ByteArrayInputStream("héllo\nwörld".toByteArray(Charsets.UTF_8))
        assertEquals("héllo", input.readBoundedLine())
        assertEquals("wörld", input.readBoundedLine())
        assertNull(input.readBoundedLine())
    }

    @Test(expected = IllegalStateException::class)
    fun `bounded line reader rejects huge lines`() {
        ByteArrayInputStream(ByteArray(10_000) { 'a'.code.toByte() }).readBoundedLine()
    }
}
