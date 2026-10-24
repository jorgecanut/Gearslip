package app.seb3thehacker.gearslip

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display

/**
 * Asks for runtime permissions on the phone's own screen, for a car screen that can't show
 * Android's dialog itself. It has no look of its own: the dialog appears, the answer is given,
 * and it finishes.
 */
class PermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
            ?.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            .orEmpty()
        if (wanted.isEmpty() || savedInstanceState != null) {
            if (wanted.isEmpty()) finish()
            return
        }
        requestPermissions(wanted.toTypedArray(), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        GearslipLog.i("permissions: ${permissions.zip(grantResults.toList()).joinToString { (p, r) -> "${p.substringAfterLast('.')}=${r == 0}" }}")
        finish()
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "permissions"
        private const val REQUEST = 1

        /** Shows the permission dialog on the phone, whichever screen [context] belongs to. */
        fun ask(context: Context, permissions: Array<String>) {
            val intent = Intent(context, PermissionActivity::class.java)
                .putExtra(EXTRA_PERMISSIONS, permissions)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            runCatching { context.startActivity(intent, options.toBundle()) }
                .onFailure { GearslipLog.w("permissions: could not ask on the phone: ${it.message}") }
        }
    }
}
