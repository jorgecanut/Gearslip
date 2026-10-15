package app.seb3thehacker.gearslip

/**
 * Advertises our supported 1.x version, as Android Auto does for older head units. Head units that
 * offer a newer major version (LIVI asks for 6.1, a RAV4 and the DHU for 4.2) still accept a 1.x
 * answer, so only a major version of 0 is refused.
 */
internal object ProtocolVersion {
    data class Response(val major: Int, val minor: Int, val supported: Boolean) {
        val bytes: ByteArray get() = byteArrayOf(
            (major ushr 8).toByte(), major.toByte(), (minor ushr 8).toByte(), minor.toByte(),
            if (supported) 0 else -1, if (supported) 0 else -1,
        )
    }

    fun respond(major: Int, minor: Int): Response = Response(1, 7, major >= 1 && minor in 0..0xffff)
}
