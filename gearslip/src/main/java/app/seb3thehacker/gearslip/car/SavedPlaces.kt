package app.seb3thehacker.gearslip.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.GearslipLog
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** One saved place. Home and Work keep a blank [label]; their names come from strings.xml. */
data class SavedPlace(val label: String = "", val address: String = "")

/**
 * Up to [COUNT] places a driver can name by voice in any map app: slot [HOME], slot [WORK],
 * then three they name themselves. "Navigate home" sends the saved address as the search.
 */
object SavedPlaces {

    const val COUNT = 5
    const val HOME = 0
    const val WORK = 1

    private const val KEY = "saved_places"

    private val HOME_WORDS = setOf("home", "house", "place", "home address", "my home", "my house", "my place")
    private val WORK_WORDS = setOf("work", "office", "job", "work address", "my work", "my office", "my job", "the office")

    fun all(context: Context): List<SavedPlace> {
        val saved = runCatching {
            val array = JSONArray(AppSettings.getString(context, KEY, "[]"))
            List(array.length()) { i ->
                array.getJSONObject(i).let { SavedPlace(it.optString("label"), it.optString("address")) }
            }
        }.getOrDefault(emptyList())
        return List(COUNT) { saved.getOrNull(it) ?: SavedPlace() }
    }

    fun set(context: Context, index: Int, place: SavedPlace) {
        val next = all(context).toMutableList().also { it[index] = place }
        val array = JSONArray()
        next.forEach { array.put(JSONObject().put("label", it.label).put("address", it.address)) }
        AppSettings.putString(context, KEY, array.toString())
    }

    /**
     * The slot [spoken] names, if any: "home", "my house", "work", or a custom label. Returns the
     * slot even when its address is blank, so the assistant can say it isn't set yet.
     */
    fun match(context: Context, spoken: String): Pair<Int, SavedPlace>? {
        val said = spoken.lowercase().trim()
        val places = all(context)
        if (said in HOME_WORDS) return HOME to places[HOME]
        if (said in WORK_WORDS) return WORK to places[WORK]
        val squashed = squash(said.removePrefix("my ").removePrefix("the "))
        if (squashed.isEmpty()) return null
        places.forEachIndexed { i, place ->
            if (i > WORK && place.label.isNotBlank() && squash(place.label) == squashed) return i to place
        }
        return null
    }

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun hasLocationPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Where the phone is now, as a street address, or as coordinates when there's no geocoder. Null if unknown. */
    suspend fun here(context: Context): String? = withContext(Dispatchers.IO) {
        val location = locate(context) ?: return@withContext null
        val address = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.getAddressLine(0)
        }.getOrNull()
        address ?: "%.6f,%.6f".format(Locale.US, location.latitude, location.longitude)
    }

    private suspend fun locate(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { it in manager.allProviders && manager.isProviderEnabled(it) }
        // A fresh fix: saving the spot you're standing on wants better than an hour-old one.
        val fresh = provider?.let {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine { done ->
                    runCatching {
                        manager.getCurrentLocation(it, null, context.mainExecutor) { fix -> done.resume(fix) }
                    }.onFailure { e ->
                        GearslipLog.w("places: fix failed: ${e.message}")
                        done.resume(null)
                    }
                }
            }
        }
        return fresh ?: manager.allProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private const val FIX_TIMEOUT_MS = 15_000L
}
