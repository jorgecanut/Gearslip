package app.seb3thehacker.gearslip.call

import android.os.SystemClock
import android.view.KeyEvent
import app.seb3thehacker.gearslip.GearslipLog
import app.seb3thehacker.gearslip.Protobuf
import app.seb3thehacker.gearslip.Wire
import app.seb3thehacker.gearslip.car.CarKeys

/**
 * Tells the car about calls, so its own screens show them: the instrument cluster, the steering
 * wheel display, the car's phone page. Android Auto does the same over the car's
 * PhoneStatusService (Service field 10):
 *
 *   -> PhoneStatusUpdate { repeated PhoneCall calls = 1 }
 *        PhoneCall { state = 1 (1 in call, 4 incoming, 6 muted); duration_seconds = 2;
 *                    number = 3; name = 4 }
 *   <- PhoneStatusInput { input = 1 { action = 1 (7 call) }; caller = 2; name = 3 }
 *
 * Read once a second from [CarCalls]. An update goes when the call changes, and every second
 * while one is going, so the car's timer counts along; no calls goes once, as an empty list.
 * The car's own call button answers through [CarKeys], like the steering wheel's.
 */
class CarPhoneStatus(
    val channelId: Int,
    private val sendOnChannel: (messageId: Int, body: ByteArray) -> Unit,
) {
    @Volatile private var running = false
    private var sent: String? = null

    fun onMessage(messageId: Int, body: ByteArray) {
        when (messageId) {
            MSG_CHANNEL_OPEN_RESPONSE -> {
                val status = Protobuf.readInt32Field(body, 1)
                GearslipLog.i("phone status: <- ChannelOpenResponse: status=$status")
                if (status == 0) start()
            }
            MSG_INPUT -> onInput(body)
            else -> GearslipLog.i("phone status: <- unhandled message id=$messageId (${body.size} bytes)")
        }
    }

    private fun start() {
        if (running) return
        running = true
        Thread({
            while (running) {
                runCatching { tick() }.onFailure { GearslipLog.w("phone status: ${it.message}") }
                Thread.sleep(TICK_MS)
            }
        }, "gearslip-phone-status").start()
    }

    fun stop() {
        running = false
    }

    private fun tick() {
        val call = when (val state = CarCalls.state.value) {
            CallUiState.None -> null
            is CallUiState.Ringing -> Call(STATE_INCOMING, 0, state.caller)
            is CallUiState.Active -> Call(
                if (CarCalls.muted.value) STATE_MUTED else STATE_IN_CALL,
                ((SystemClock.uptimeMillis() - state.since) / 1000).coerceAtLeast(0),
                state.caller,
            )
        }
        // Only a call that's going changes from one second to the next.
        val key = call?.let { "${it.state}|${it.caller?.number}|${it.caller?.name}|${if (it.state == STATE_INCOMING) 0 else it.seconds}" } ?: ""
        if (key == sent) return
        val changed = call?.state != lastState
        sent = key
        lastState = call?.state
        val body = call?.let { Protobuf.bytesField(1, it.encode()) } ?: ByteArray(0)
        sendOnChannel(MSG_STATUS, body)
        if (changed) GearslipLog.i("phone status: -> ${call?.let { STATE_NAMES[it.state] } ?: "no calls"}")
    }

    private var lastState: Int? = null

    private class Call(val state: Int, val seconds: Long, val caller: Caller?) {
        fun encode(): ByteArray {
            var body = Protobuf.varintField(1, state.toLong()) + Protobuf.varintField(2, seconds)
            caller?.number?.takeIf { it.isNotBlank() }?.let { body += Protobuf.stringField(3, it) }
            caller?.name?.takeIf { it.isNotBlank() }?.let { body += Protobuf.stringField(4, it) }
            return body
        }
    }

    /** The car's own phone buttons. Only "call" does anything: it answers, as the wheel's does. */
    private fun onInput(body: ByteArray) {
        val action = Wire.bytes(Wire.fields(body), 1)?.let { Wire.varint(Wire.fields(it), 1)?.toInt() }
        GearslipLog.i("phone status: <- input action=${action ?: "?"}")
        if (action == ACTION_CALL) CarKeys.onKey(KeyEvent.KEYCODE_CALL, down = true, longPress = false)
    }

    companion object {
        private const val TICK_MS = 1_000L
        private const val MSG_CHANNEL_OPEN_RESPONSE = 8
        private const val MSG_STATUS = 32769 // 0x8001
        private const val MSG_INPUT = 32770 // 0x8002
        private const val STATE_IN_CALL = 1
        private const val STATE_INCOMING = 4
        private const val STATE_MUTED = 6
        private const val ACTION_CALL = 7
        private val STATE_NAMES = mapOf(STATE_IN_CALL to "in a call", STATE_INCOMING to "ringing", STATE_MUTED to "in a call, muted")
    }
}
