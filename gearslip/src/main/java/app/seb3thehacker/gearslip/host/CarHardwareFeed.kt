package app.seb3thehacker.gearslip.host

import android.os.IBinder
import androidx.car.app.hardware.ICarHardwareResult
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.serialization.Bundleable
import app.seb3thehacker.gearslip.GearslipLog
import app.seb3thehacker.gearslip.car.CarData
import app.seb3thehacker.gearslip.car.CarSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What a car app gets when it asks the car hardware API (CarInfo, CarSensors) about the car:
 * the model, speed, odometer, fuel and range, compass and the car's own GPS, read from [CarData].
 * Anything else, or anything the car never sends, is answered "this car doesn't have that".
 *
 * A one-off question ([get]) is answered at once. A subscription gets the current value, if
 * there is one, and then each new one as the car sends it, until the app unsubscribes or the
 * connection ends ([stop]).
 */
internal class CarHardwareFeed(private val callback: (name: String) -> IBinder) {

    private val subscribers = ConcurrentHashMap<Int, CopyOnWriteArrayList<ICarHardwareResult>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var watching: Job? = null

    fun get(type: Int, result: ICarHardwareResult?) {
        val target = result ?: return
        send(target, type, valueOf(type, CarData.state.value) ?: return unsupported(target, type))
    }

    fun subscribe(type: Int, result: ICarHardwareResult?) {
        val target = result ?: return
        if (type !in SENSOR_OF) return unsupported(target, type)
        subscribers.getOrPut(type) { CopyOnWriteArrayList() }.add(target)
        GearslipLog.i("host: an app subscribed to car hardware ${NAMES[type] ?: type}")
        valueOf(type, CarData.state.value)?.let { send(target, type, it) }
        watch()
    }

    fun unsubscribe(type: Int) {
        subscribers.remove(type)
    }

    fun stop() {
        subscribers.clear()
        watching?.cancel()
        watching = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    /** Sends each subscribed kind again when the car's reading of it changes. */
    private fun watch() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            var seen = CarData.state.value.updatedAt
            CarData.state.collect { s ->
                for ((type, targets) in subscribers) {
                    val sensor = SENSOR_OF[type] ?: continue
                    if (s.updatedAt[sensor] == seen[sensor]) continue
                    val value = valueOf(type, s) ?: continue
                    targets.forEach { send(it, type, value) }
                }
                seen = s.updatedAt
            }
        }
    }

    private fun valueOf(type: Int, s: CarData.Snapshot): Any? {
        val imperial = CarSettings.imperial()
        val distanceUnit = known(if (imperial) CarUnit.MILE else CarUnit.KILOMETER)
        val at = s.updatedAt[SENSOR_OF[type]] ?: System.currentTimeMillis()
        fun <T : Any> v(value: T) = CarValue(value, at, CarValue.STATUS_SUCCESS)
        return when (type) {
            TYPE_MODEL -> CarData.model?.let { m ->
                Model.Builder().setName(v(m.name)).apply { m.year?.let { setYear(v(it)) } }.build()
            }
            TYPE_SPEED -> s.speedMps?.let {
                Speed.Builder()
                    .setRawSpeedMetersPerSecond(v(it))
                    .setDisplaySpeedMetersPerSecond(v(it))
                    .setSpeedDisplayUnit(known(if (imperial) CarUnit.MILES_PER_HOUR else CarUnit.KILOMETERS_PER_HOUR))
                    .build()
            }
            TYPE_MILEAGE -> s.odometerKm?.let {
                Mileage.Builder().setOdometerMeters(v(it * 1000f)).setDistanceDisplayUnit(distanceUnit).build()
            }
            TYPE_ENERGY_LEVEL -> if (s.fuelPercent == null && s.rangeKm == null && s.fuelLow == null) null else {
                EnergyLevel.Builder().apply {
                    s.fuelPercent?.let { setFuelPercent(v(it.toFloat())) }
                    s.rangeKm?.let { setRangeRemainingMeters(v(it * 1000f)) }
                    s.fuelLow?.let { setEnergyIsLow(v(it)) }
                    setDistanceDisplayUnit(distanceUnit)
                }.build()
            }
            TYPE_COMPASS -> s.compassDegrees?.let { Compass(v(listOf(it))) }
            TYPE_CAR_LOCATION -> s.location?.let { l ->
                val location = android.location.Location("car").apply {
                    latitude = l.latitude
                    longitude = l.longitude
                    l.accuracyMeters?.let { accuracy = it }
                    l.altitudeMeters?.let { altitude = it }
                    l.speedMps?.let { speed = it }
                    l.bearingDegrees?.let { bearing = it }
                    time = at
                }
                CarHardwareLocation(v(location))
            }
            else -> null
        }
    }

    private fun known(value: Int) = CarValue(value, System.currentTimeMillis(), CarValue.STATUS_SUCCESS)

    private fun send(target: ICarHardwareResult, type: Int, value: Any) {
        runCatching {
            target.onCarHardwareResult(type, true, Bundleable.create(value), callback("carHardwareResult"))
        }.onFailure {
            // The app went away: stop telling it.
            subscribers.values.forEach { list -> list.remove(target) }
            GearslipLog.w("host: could not send car hardware ${NAMES[type] ?: type}: ${it.javaClass.simpleName}")
        }
    }

    private fun unsupported(target: ICarHardwareResult, type: Int) {
        runCatching { target.onCarHardwareResult(type, false, null, callback("carHardwareResult")) }
            .onFailure { GearslipLog.w("host: could not answer hardware result $type") }
    }

    companion object {
        // ICarHardwareResultTypes in the car library.
        private const val TYPE_MODEL = 1
        private const val TYPE_ENERGY_LEVEL = 4
        private const val TYPE_SPEED = 5
        private const val TYPE_MILEAGE = 6
        private const val TYPE_COMPASS = 21
        private const val TYPE_CAR_LOCATION = 23

        /** Which of the car's sensors each kind comes from, for knowing when it changed. */
        private val SENSOR_OF = mapOf(
            TYPE_ENERGY_LEVEL to CarData.FUEL,
            TYPE_SPEED to CarData.SPEED,
            TYPE_MILEAGE to CarData.ODOMETER,
            TYPE_COMPASS to CarData.COMPASS,
            TYPE_CAR_LOCATION to CarData.LOCATION,
        )

        private val NAMES = mapOf(
            TYPE_MODEL to "model", TYPE_ENERGY_LEVEL to "energy level", TYPE_SPEED to "speed",
            TYPE_MILEAGE to "mileage", TYPE_COMPASS to "compass", TYPE_CAR_LOCATION to "car location",
        )
    }
}
