package app.seb3thehacker.gearslip

import org.junit.Assert.*
import org.junit.Test

class ProtocolVersionTest {
    @Test fun `advertises supported version to older and newer 1x peers`() {
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, 0, 0), ProtocolVersion.respond(1, 3).bytes)
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, 0, 0), ProtocolVersion.respond(1, 7).bytes)
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, 0, 0), ProtocolVersion.respond(1, 9).bytes)
    }

    @Test fun `newer head units get the 1x answer they accept`() {
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, 0, 0), ProtocolVersion.respond(4, 2).bytes)
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, 0, 0), ProtocolVersion.respond(6, 1).bytes)
    }

    @Test fun `major version 0 is refused`() {
        val response = ProtocolVersion.respond(0, 9)
        assertFalse(response.supported)
        assertArrayEquals(byteArrayOf(0, 1, 0, 7, -1, -1), response.bytes)
    }
}
