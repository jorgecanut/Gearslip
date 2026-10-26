package app.seb3thehacker.gearslip.audio

import app.seb3thehacker.gearslip.ServiceDiscovery
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The media sink's start-up as an AHU (GalReceiver 2014, AAC-only media sink) saw it: Start, then
 * the first chunk, and the head unit reset the connection half a second later.
 */
class AudioLinkTest {

    private class Sent(val messageId: Int, val body: ByteArray)

    private fun sink(codec: Int) = ServiceDiscovery.AudioService(
        serviceId = 4,
        streamType = 3,
        codecType = codec,
        configs = listOf(ServiceDiscovery.AudioConfig(48_000, 16, 2)),
    )

    private fun link(codec: Int, sent: MutableList<Sent>) = AudioLink(
        sink(codec),
        sendOnChannel = { id, body -> sent += Sent(id, body) },
        sendControl = { _, _ -> },
        onReady = {},
        onClose = {},
    ).also { it.onMessage(MSG_CONFIG, byteArrayOf(0x08, 0x02, 0x10, 0x18, 0x18, 0x00)) }

    /**
     * Video sends the encoder's SPS/PPS as a codec config before any frame, and the head unit's
     * decoder is set up from it. Raw AAC-LC carries no header of its own, so the sink needs the
     * AudioSpecificConfig the same way before the first frame: 0x11 0x90 is AAC-LC, 48 kHz, stereo.
     */
    @Test
    fun aacSinkGetsCodecConfigBeforeAudio() {
        val sent = mutableListOf<Sent>()
        val link = link(ServiceDiscovery.CODEC_AAC_LC, sent)
        link.onFocus(FOCUS_GAIN)
        link.write(ByteArray(512), 512)

        assertEquals(listOf(MSG_START, MSG_CODEC_CONFIG, MSG_DATA), sent.map { it.messageId })
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), sent[1].body)
    }

    @Test
    fun pcmSinkGetsNoCodecConfig() {
        val sent = mutableListOf<Sent>()
        link(ServiceDiscovery.CODEC_PCM, sent).onFocus(FOCUS_GAIN)

        assertEquals(0, sent.count { it.messageId == MSG_CODEC_CONFIG })
    }

    @Test
    fun audioSpecificConfig() {
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), AacEncoder.audioSpecificConfig(48_000, 2))
        assertArrayEquals(byteArrayOf(0x12, 0x10), AacEncoder.audioSpecificConfig(44_100, 2))
        assertArrayEquals(byteArrayOf(0x14, 0x08), AacEncoder.audioSpecificConfig(16_000, 1))
    }

    private companion object {
        const val MSG_DATA = 0
        const val MSG_START = 32769
        const val MSG_CONFIG = 32771
        const val MSG_CODEC_CONFIG = 1
        const val FOCUS_GAIN = 1
    }
}
