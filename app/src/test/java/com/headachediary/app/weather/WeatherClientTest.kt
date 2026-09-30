package com.headachediary.app.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherClientTest {
    private val start = 1_800_000_000L // секунды Unix, начало ряда

    private fun hourly(hours: Int = 60, pressure: (Int) -> Double): Hourly = Hourly(
        times = LongArray(hours) { start + it * 3600L },
        temperature = DoubleArray(hours) { 10.0 },
        humidity = DoubleArray(hours) { 50.0 },
        pressure = DoubleArray(hours) { pressure(it) },
        weatherCode = DoubleArray(hours) { 3.0 },
    )

    private fun ms(hour: Int) = (start + hour * 3600L) * 1000

    @Test
    fun snapshotComputesPressureChanges() {
        val h = hourly { 1000.0 - it * 0.5 } // давление падает на 0,5 гПа в час

        val s = WeatherClient.snapshotAt(h, ms(30))!!

        assertEquals(985.0, s.pressure!!, 0.01)
        assertEquals(-1.5, s.pressureChange3h!!, 0.01)
        assertEquals(-12.0, s.pressureChange24h!!, 0.01)
        assertEquals(10.0, s.temperature!!, 0.01)
        assertEquals(50, s.humidity)
        assertEquals(3, s.weatherCode)
    }

    @Test
    fun snapshotUsesTheHourThatAlreadyStarted() {
        val h = hourly { it.toDouble() }

        // 30 минут после начала 10-го часа: берём значение этого часа
        val s = WeatherClient.snapshotAt(h, ms(10) + 30 * 60_000L)!!

        assertEquals(10.0, s.pressure!!, 0.01)
    }

    @Test
    fun snapshotBeforeTheSeriesIsNull() {
        assertNull(WeatherClient.snapshotAt(hourly { 1000.0 }, ms(0) - 1))
    }

    @Test
    fun changeIsNullWhenHistoryIsMissing() {
        val s = WeatherClient.snapshotAt(hourly { 1000.0 }, ms(1))!!

        assertNotNull(s.pressure)
        assertNull(s.pressureChange3h)
        assertNull(s.pressureChange24h)
    }

    @Test
    fun calmWeatherHasNoSharpHours() {
        val share = WeatherClient.sharpChangeShare(hourly { 1000.0 }, ms(59))

        assertEquals(0.0, share!!, 0.0001)
    }

    @Test
    fun fastFallMakesEveryHourSharp() {
        val share = WeatherClient.sharpChangeShare(hourly { 1000.0 - it * 1.5 }, ms(59))

        assertEquals(1.0, share!!, 0.0001)
    }

    @Test
    fun tooLittleDataGivesNoBaseline() {
        assertNull(WeatherClient.sharpChangeShare(hourly(hours = 10) { 1000.0 }, ms(9)))
    }

    @Test
    fun outlookFindsTheStrongestUpcomingSwing() {
        // Ровно до 40-го часа, дальше падение на 2 гПа в час: за три часа −6.
        val h = hourly { if (it <= 40) 1000.0 else 1000.0 - 2.0 * (it - 40) }

        val outlook = WeatherClient.outlook(h, ms(36))!!

        assertEquals(-6.0, outlook.changeHpa, 0.01)
        assertEquals(ms(43), outlook.atMs)
        assertTrue(outlook.isSharp)
    }

    @Test
    fun outlookIgnoresPastAndDistantHours() {
        // Резкие перепады есть до «сейчас» (часы 10–12) и после ближайших 24 часов (с 58-го), но не внутри окна.
        val h = hourly(hours = 70) {
            when {
                it in 10..12 -> 1000.0 - 20.0 * (it - 9)
                it >= 58 -> 900.0
                else -> 1000.0
            }
        }
        val outlook = WeatherClient.outlook(h, ms(30))

        assertNotNull(outlook)
        assertFalse(outlook!!.isSharp)
    }

    @Test
    fun outlookIsNullWithoutFutureData() {
        assertNull(WeatherClient.outlook(hourly(hours = 30) { 1000.0 }, ms(29)))
    }
}
