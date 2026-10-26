package app.seb3thehacker.gearslip.car

import app.seb3thehacker.gearslip.Protobuf
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CarDataTest {
    @After fun reset() = CarData.clear()

    private fun batch(field: Int, body: ByteArray) = Protobuf.bytesField(field, body)

    @Test fun `speed odometer and fuel come out in real units`() {
        CarData.onEvent(
            batch(CarData.SPEED, Protobuf.varintField(1, 12_500)) +
                batch(CarData.ODOMETER, Protobuf.varintField(1, 123_456)) +
                batch(CarData.FUEL, Protobuf.varintField(1, 42) + Protobuf.varintField(2, 380) + Protobuf.varintField(3, 0)),
        )
        val s = CarData.state.value
        assertEquals(12.5f, s.speedMps!!, 0.001f)
        assertEquals(12_345.6f, s.odometerKm!!, 0.01f)
        assertEquals(42, s.fuelPercent)
        assertEquals(380, s.rangeKm)
        assertEquals(false, s.fuelLow)
        assertTrue(s.updatedAt.keys.containsAll(listOf(CarData.SPEED, CarData.ODOMETER, CarData.FUEL)))
    }

    @Test fun `a location west and south of zero keeps its sign`() {
        CarData.onEvent(
            batch(
                CarData.LOCATION,
                Protobuf.varintField(2, -337_000_000) + Protobuf.varintField(3, -706_000_000) +
                    Protobuf.varintField(7, 90_000_000),
            ),
        )
        val l = CarData.state.value.location!!
        assertEquals(-33.7, l.latitude, 1e-6)
        assertEquals(-70.6, l.longitude, 1e-6)
        assertEquals(90f, l.bearingDegrees!!, 0.001f)
        assertNull(l.accuracyMeters)
    }

    @Test fun `the newest reading in a batch wins, and fuel keeps what a later batch leaves out`() {
        CarData.onEvent(batch(CarData.FUEL, Protobuf.varintField(1, 50) + Protobuf.varintField(2, 400)))
        CarData.onEvent(
            batch(CarData.COMPASS, Protobuf.varintField(1, 10_000_000)) +
                batch(CarData.COMPASS, Protobuf.varintField(1, 20_000_000)) +
                batch(CarData.FUEL, Protobuf.varintField(1, 49)),
        )
        val s = CarData.state.value
        assertEquals(20f, s.compassDegrees!!, 0.001f)
        assertEquals(49, s.fuelPercent)
        assertEquals(400, s.rangeKm)
    }

    @Test fun `a car that names no model or year leaves them unknown`() {
        CarData.onModel("", "0")
        assertNull(CarData.model)
        CarData.onModel("Clio", "0")
        assertEquals("Clio", CarData.model?.name)
        assertNull(CarData.model?.year)
    }
}
