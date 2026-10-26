package app.seb3thehacker.gearslip.call

import app.seb3thehacker.gearslip.GearslipLog
import app.seb3thehacker.gearslip.Protobuf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The car's phone status channel (PhoneStatusService), which real Android Auto keeps up to date
 * with every call. Without it a call on an AAC-only 2014 head unit was a stranger's: the car
 * switched to its own phone screen and stopped acking video until the session died. Told about
 * the call, a car can show it inside the projection instead.
 *
 *   -> CHANNEL_OPEN_REQUEST   <- CHANNEL_OPEN_RESPONSE
 *   -> PhoneStatus { repeated Call calls = 1; }
 *      Call { PhoneState phone_state = 1; uint32 call_duration_seconds = 2;
 *             string caller_number = 3; string caller_id = 4; }
 *      PhoneState: IN_CALL 1, ON_HOLD 2, INACTIVE 3, INCOMING 4
 *
 * Opened only on cars whose music sink takes AAC alone, the one kind it has been tried on.
 */
class CarPhoneStatus(
    val channelId: Int,
    private val send: (messageId: Int, body: ByteArray) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null
    @Volatile private var open = false

    fun onMessage(messageId: Int, body: ByteArray) {
        when (messageId) {
            MSG_CHANNEL_OPEN_RESPONSE -> {
                val status = Protobuf.readInt32Field(body, 1) ?: 0
                GearslipLog.i("phone status: <- ChannelOpenResponse: status=$status")
                if (status != 0) return
                open = true
                watch = scope.launch { CarCalls.state.collect(::report) }
            }
            // PhoneStatusInput: the car's own answer/hang-up buttons. Logged only, by size.
            else -> GearslipLog.i("phone status: <- message id=$messageId (${body.size} bytes)")
        }
    }

    private fun report(state: CallUiState) {
        if (!open) return
        val call = when (state) {
            CallUiState.None -> null
            is CallUiState.Ringing -> callBody(PHONE_STATE_INCOMING, 0, state.caller)
            is CallUiState.Active -> callBody(
                PHONE_STATE_IN_CALL,
                ((android.os.SystemClock.uptimeMillis() - state.since) / 1000).toInt().coerceAtLeast(0),
                state.caller,
            )
        }
        val body = call?.let { Protobuf.bytesField(1, it) } ?: ByteArray(0)
        runCatching { send(MSG_PHONE_STATUS, body) }
        // Which state only: the caller stays out of the log.
        GearslipLog.i("phone status: -> ${state.javaClass.simpleName}")
    }

    private fun callBody(phoneState: Int, seconds: Int, caller: Caller?): ByteArray =
        Protobuf.varintField(1, phoneState.toLong()) +
            Protobuf.varintField(2, seconds.toLong()) +
            (caller?.number?.takeIf { it.isNotBlank() }?.let { Protobuf.stringField(3, it) } ?: ByteArray(0)) +
            (caller?.name?.let { Protobuf.stringField(4, it) } ?: ByteArray(0))

    fun stop() {
        open = false
        watch?.cancel()
        scope.cancel()
    }

    private companion object {
        const val MSG_CHANNEL_OPEN_RESPONSE = 8
        const val MSG_PHONE_STATUS = 32769
        const val PHONE_STATE_IN_CALL = 1
        const val PHONE_STATE_INCOMING = 4
    }
}
