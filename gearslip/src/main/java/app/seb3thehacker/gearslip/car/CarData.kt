package app.seb3thehacker.gearslip.car

import app.seb3thehacker.gearslip.Wire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The car's sensors, read into real values for the car apps that ask (a map's speed, an EV app's
 * range). [CarSensors] keeps the raw readings for the Vehicle data screen; this keeps the few with
 * a known meaning, decoded the way Android Auto's SensorBatch scales them:
 *
 *   gps_location = 1 { latitude_e7 = 2; longitude_e7 = 3; accuracy_e3 = 4; altitude_e2 = 5;
 *                      speed_e3 = 6; bearing_e6 = 7 }
 *   compass = 2 { bearing_e6 = 1 }      speed = 3 { speed_e3 = 1 }  (metres per second)
 *   odometer = 5 { kms_e1 = 1 }         fuel = 6 { level = 1 (percent); range = 2 (km); low = 3 }
 *
 * Each value is null until the car first sends it, and the whole lot clears when it disconnects.
 */
object CarData {

    class Location(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val altitudeMeters: Double?,
        val speedMps: Float?,
        val bearingDegrees: Float?,
    )

    data class Snapshot(
        val speedMps: Float? = null,
        val odometerKm: Float? = null,
        val fuelPercent: Int? = null,
        val rangeKm: Int? = null,
        val fuelLow: Boolean? = null,
        val compassDegrees: Float? = null,
        val location: Location? = null,
        /** When each kind last changed, by its SensorBatch field number. */
        val updatedAt: Map<Int, Long> = emptyMap(),
    )

    /** What the car says it is, from its service discovery: model and year. It names no maker. */
    class Model(val name: String, val year: Int?)

    private val flow = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = flow

    @Volatile var model: Model? = null
        private set

    fun onModel(name: String, year: String) {
        model = name.takeIf { it.isNotBlank() }?.let { Model(it, year.toIntOrNull()?.takeIf { y -> y > 0 }) }
    }

    fun onEvent(body: ByteArray) {
        val now = System.currentTimeMillis()
        val batch = Wire.fields(body)
        var s = flow.value
        val touched = HashSet<Int>()
        // Each kind may come several times in one batch: the last is the newest.
        Wire.allBytes(batch, LOCATION).lastOrNull()?.let { bytes ->
            val f = Wire.fields(bytes)
            val lat = Wire.varint(f, 2)?.toInt()
            val lon = Wire.varint(f, 3)?.toInt()
            if (lat != null && lon != null) {
                s = s.copy(
                    location = Location(
                        lat / 1e7, lon / 1e7,
                        Wire.varint(f, 4)?.let { it / 1e3f },
                        Wire.varint(f, 5)?.let { it.toInt() / 1e2 },
                        Wire.varint(f, 6)?.let { it.toInt() / 1e3f },
                        Wire.varint(f, 7)?.let { it.toInt() / 1e6f },
                    ),
                )
                touched += LOCATION
            }
        }
        Wire.allBytes(batch, COMPASS).lastOrNull()?.let { bytes ->
            Wire.varint(Wire.fields(bytes), 1)?.let { s = s.copy(compassDegrees = it.toInt() / 1e6f); touched += COMPASS }
        }
        Wire.allBytes(batch, SPEED).lastOrNull()?.let { bytes ->
            Wire.varint(Wire.fields(bytes), 1)?.let { s = s.copy(speedMps = it.toInt() / 1e3f); touched += SPEED }
        }
        Wire.allBytes(batch, ODOMETER).lastOrNull()?.let { bytes ->
            Wire.varint(Wire.fields(bytes), 1)?.let { s = s.copy(odometerKm = it.toInt() / 10f); touched += ODOMETER }
        }
        Wire.allBytes(batch, FUEL).lastOrNull()?.let { bytes ->
            val f = Wire.fields(bytes)
            s = s.copy(
                fuelPercent = Wire.varint(f, 1)?.toInt() ?: s.fuelPercent,
                rangeKm = Wire.varint(f, 2)?.toInt() ?: s.rangeKm,
                fuelLow = Wire.varint(f, 3)?.let { it != 0L } ?: s.fuelLow,
            )
            touched += FUEL
        }
        if (touched.isEmpty()) return
        flow.value = s.copy(updatedAt = s.updatedAt + touched.associateWith { now })
    }

    fun clear() {
        flow.value = Snapshot()
        model = null
    }

    const val LOCATION = 1
    const val COMPASS = 2
    const val SPEED = 3
    const val ODOMETER = 5
    const val FUEL = 6
}
