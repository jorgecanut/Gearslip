package app.seb3thehacker.gearslip.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import app.seb3thehacker.gearslip.GearslipLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A caller as the car shows it: whatever the contacts app knows, or just the number. */
data class Caller(val number: String, val name: String?) {
    val label: String get() = name ?: number.ifBlank { "Unknown number" }
}

sealed interface CallUiState {
    data object None : CallUiState
    data class Ringing(val caller: Caller, val canDecline: Boolean) : CallUiState
    /** [since] is the uptime the call went off-hook, for the timer. */
    data class Active(val caller: Caller?, val since: Long) : CallUiState
}

/**
 * Tracks the phone's call state for the car screen, and answers or declines through the
 * doors a sideloaded app can use: [android.telecom.TelecomManager.acceptRingingCall] to answer,
 * [GearslipCallScreening]'s response to decline one that is still ringing, and
 * [android.telecom.TelecomManager.endCall] to hang up, which needs only ANSWER_PHONE_CALLS for an
 * ordinary carrier call. Mute is the phone's microphone mute, put back when the call ends.
 *
 * Call audio stays on the phone's normal voice path; nothing here sends it over the USB link,
 * which only ever carries media playback.
 */
object CarCalls {

    private val _state = MutableStateFlow<CallUiState>(CallUiState.None)
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var telephony: TelephonyManager? = null
    private var watcher: TelephonyCallback? = null
    private var pendingRespond: ((allow: Boolean) -> Unit)? = null
    private var ringingCaller: Caller? = null

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private var audio: android.media.AudioManager? = null

    fun start(context: Context) {
        if (watcher != null) return
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        audio = context.getSystemService(android.media.AudioManager::class.java)
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onPhoneState(state)
        }
        runCatching { manager.registerTelephonyCallback(context.mainExecutor, callback) }
            .onSuccess { telephony = manager; watcher = callback }
            .onFailure { GearslipLog.w("calls: could not watch call state: ${it.message}") }
    }

    fun stop() {
        val callback = watcher ?: return
        runCatching { telephony?.unregisterTelephonyCallback(callback) }
        telephony = null
        watcher = null
        pendingRespond = null
        ringingCaller = null
        unmute()
        _state.value = CallUiState.None
    }

    private fun onPhoneState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                pendingRespond = null
                ringingCaller = null
                // A mute left on would silence the microphone for everything after the call.
                unmute()
                _state.value = CallUiState.None
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                pendingRespond = null
                if (_state.value !is CallUiState.Active) {
                    _state.value = CallUiState.Active(ringingCaller, android.os.SystemClock.uptimeMillis())
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // The number itself only ever arrives through the screening service; if this
                // fires first, the ringing card appears once incomingCall() catches up.
                ringingCaller?.let { _state.value = CallUiState.Ringing(it, pendingRespond != null) }
            }
        }
    }

    /** Called by [GearslipCallScreening] the moment a call comes in - the only place with a number. */
    fun incomingCall(context: Context, number: String?, respond: (allow: Boolean) -> Unit) {
        val caller = Caller(number.orEmpty(), number?.let { lookUpName(context, it) })
        ringingCaller = caller
        pendingRespond = respond
        _state.value = CallUiState.Ringing(caller, canDecline = true)
    }

    /** The screening window closed by itself - the call rings normally now, and can't be declined from here. */
    fun screeningExpired() {
        pendingRespond = null
        val caller = ringingCaller ?: return
        if (_state.value is CallUiState.Ringing) _state.value = CallUiState.Ringing(caller, canDecline = false)
    }

    fun decline() {
        val respond = pendingRespond ?: return
        pendingRespond = null
        runCatching { respond(false) }.onFailure { GearslipLog.w("calls: decline failed: ${it.message}") }
    }

    /** A call Gearslip itself is placing, so the call pill can say who it's to. */
    fun outgoingCall(context: Context, number: String) {
        ringingCaller = Caller(number, lookUpName(context, number))
    }

    fun end(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            GearslipLog.w("calls: ending a call needs ANSWER_PHONE_CALLS, which was never granted")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        @Suppress("DEPRECATION")
        runCatching { telecom.endCall() }
            .onSuccess { if (!it) GearslipLog.w("calls: the phone wouldn't end the call") }
            .onFailure { GearslipLog.w("calls: end failed: ${it.message}") }
    }

    fun toggleMute() {
        val manager = audio ?: return
        val want = !_muted.value
        runCatching { manager.isMicrophoneMute = want }
            .onFailure { GearslipLog.w("calls: mute failed: ${it.message}") }
        // Read back rather than trusted: some phones let the call app overrule it.
        _muted.value = manager.isMicrophoneMute
        if (_muted.value != want) GearslipLog.w("calls: the phone didn't take the mute change")
    }

    private fun unmute() {
        if (!_muted.value) return
        runCatching { audio?.isMicrophoneMute = false }
        _muted.value = false
    }

    fun answer(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            GearslipLog.w("calls: answer needs ANSWER_PHONE_CALLS, which was never granted")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { telecom.acceptRingingCall() }
            .onFailure { GearslipLog.w("calls: answer failed: ${it.message}") }
    }

    private fun lookUpName(context: Context, number: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }
}

/** Posts to the main thread after a delay - used to give up on a screening response Gearslip never sent. */
internal fun mainThreadDelay(ms: Long, action: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(action, ms)
}
