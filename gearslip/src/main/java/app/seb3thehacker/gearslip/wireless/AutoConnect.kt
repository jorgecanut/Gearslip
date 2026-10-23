package app.seb3thehacker.gearslip.wireless

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.GearslipActivity
import app.seb3thehacker.gearslip.GearslipLog
import app.seb3thehacker.gearslip.SessionStatus

/**
 * Connects wirelessly on its own when the phone reaches the car over Bluetooth, the way Android
 * Auto does.
 *
 * Joining the car's Wi-Fi needs Gearslip on screen, so the trigger opens the app, and the app
 * starts [WirelessLink] with the car it was told about. Android stops apps opening themselves from
 * the background; linking the car as Gearslip's companion device (a one-time system dialog) is
 * what lets it. The car is the last one picked under Connect wirelessly.
 */
object AutoConnect {

    private val log = GearslipLog.tagged("AAW")

    private const val KEY_ENABLED = "wireless_auto_connect"
    private const val KEY_CAR = "wireless_last_car"
    private const val KEY_CAR_NAME = "wireless_last_car_name"
    private const val EXTRA_CAR = "app.seb3thehacker.gearslip.AUTO_CONNECT_CAR"
    private const val CHANNEL = "gearslip_auto_connect"
    private const val NOTIFICATION_ID = 44

    fun enabled(context: Context): Boolean = AppSettings.getString(context, KEY_ENABLED, "false") == "true"

    fun setEnabled(context: Context, on: Boolean) = AppSettings.putString(context, KEY_ENABLED, on.toString())

    /** The car's Bluetooth address, or null before the first wireless connection. */
    fun car(context: Context): String? = AppSettings.getString(context, KEY_CAR, "").ifEmpty { null }

    fun carName(context: Context): String = AppSettings.getString(context, KEY_CAR_NAME, "").ifEmpty { car(context).orEmpty() }

    /** Called when the driver picks a car under Connect wirelessly. */
    fun remember(context: Context, device: BluetoothDevice) {
        AppSettings.putString(context, KEY_CAR, device.address)
        AppSettings.putString(context, KEY_CAR_NAME, WirelessLink.name(device))
    }

    /** Whether [address] is already linked as Gearslip's companion device. */
    fun linked(context: Context, address: String): Boolean {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { it.equals(address, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    /**
     * Asks Android to link [address] as Gearslip's companion device. [onDialog] must launch the
     * system dialog it is handed; [onDone] hears whether the link was made.
     */
    fun link(context: Context, address: String, onDialog: (IntentSender) -> Unit, onDone: (Boolean) -> Unit) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) {
            log.w("auto-connect: this phone has no companion device support")
            onDone(false)
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onDialog(intentSender)

            @Deprecated("Deprecated in Java, still the only callback before Android 13")
            override fun onDeviceFound(intentSender: IntentSender) = onDialog(intentSender)

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                log.i("auto-connect: the car is linked")
                onDone(true)
            }

            override fun onFailure(error: CharSequence?) {
                log.w("auto-connect: couldn't link the car: $error")
                onDone(false)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.associate(request, context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                cdm.associate(request, callback, null)
            }
        }.onFailure {
            log.e("auto-connect: couldn't ask to link the car", it)
            onDone(false)
        }
    }

    /** The phone reached a Bluetooth device. If it is the car, open Gearslip and connect. */
    fun onBluetoothConnected(context: Context, device: BluetoothDevice) {
        val car = car(context) ?: return
        if (!enabled(context) || !device.address.equals(car, ignoreCase = true)) return
        if (!AppSettings.gearslipEnabled(context)) return
        val phase = SessionStatus.state.value.phase
        if (phase == SessionStatus.Phase.CONNECTING || phase == SessionStatus.Phase.PROJECTING) return
        log.i("auto-connect: the phone reached ${carName(context)} over Bluetooth - opening Gearslip")
        val open = Intent(context, GearslipActivity::class.java)
            .putExtra(EXTRA_CAR, car)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Android may still refuse to bring the app up, silently. The notification is the way
        // in if it does; opening the app takes it away.
        notify(context, open)
        runCatching { context.startActivity(open) }
            .onFailure { log.w("auto-connect: Android didn't let Gearslip open itself (${it.message})") }
    }

    /** From the activity: connects if [intent] came from [onBluetoothConnected]. */
    @SuppressLint("MissingPermission")
    fun handle(context: Context, intent: Intent?) {
        val address = intent?.getStringExtra(EXTRA_CAR) ?: return
        intent.removeExtra(EXTRA_CAR)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        val phase = SessionStatus.state.value.phase
        if (phase == SessionStatus.Phase.CONNECTING || phase == SessionStatus.Phase.PROJECTING) return
        val device = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        }.getOrNull() ?: return
        WirelessLink.start(context, device)
    }

    private fun notify(context: Context, open: Intent) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Connecting to the car", NotificationManager.IMPORTANCE_HIGH),
            )
            val tap = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.notify(
                NOTIFICATION_ID,
                Notification.Builder(context, CHANNEL)
                    .setContentTitle("Connect to ${carName(context)}")
                    .setContentText("Tap to start Gearslip in the car.")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

/** Hears the phone connecting to any Bluetooth device; [AutoConnect] decides whether it is the car. */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        AutoConnect.onBluetoothConnected(context, device)
    }
}
