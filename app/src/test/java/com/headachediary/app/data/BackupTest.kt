package com.headachediary.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val full = HeadacheEntry(
        startTime = 1_800_000_000_000L,
        endTime = 1_800_000_000_000L + 2 * 3_600_000L,
        type = HeadacheType.MIGRAINE.name,
        intensity = 7,
        symptoms = "AURA,NAUSEA",
        triggers = "STRESS",
        medication = "ibuprofen 400",
        medicationHelped = MedHelp.PARTLY.name,
        notes = "after a long meeting",
        temperature = 12.5,
        pressure = 995.3,
        pressureChange3h = -3.4,
        pressureChange24h = -8.0,
        humidity = 71,
        weatherCode = 61,
        sleepMinutes = 350,
        steps24h = 4200,
        restingHeartRate = 62,
        perimenstrual = true,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val factors = listOf(DayFactors(day = 20_000, stress = 2, caffeine = 3, alcohol = true))
        val json = Backup.toJson(listOf(full), listOf(19_990L, 19_991L), factors)

        val data = Backup.parse(json)

        assertEquals(listOf(full), data.entries)
        assertEquals(listOf(19_990L, 19_991L), data.painFreeDays)
        assertEquals(factors, data.dayFactors)
    }

    @Test
    fun optionalFieldsStayNull() {
        val bare = HeadacheEntry(startTime = 1_800_000_000_000L)

        val parsed = Backup.parse(Backup.toJson(listOf(bare), emptyList())).entries.single()

        assertNull(parsed.endTime)
        assertNull(parsed.intensity)
        assertNull(parsed.pressure)
        assertNull(parsed.perimenstrual)
        assertNull(parsed.sleepMinutes)
    }

    @Test
    fun unknownKeysAndBadValuesAreDropped() {
        val json = """
            {"app":"HeadacheDiary","version":5,"entries":[
              {"startTime":1,"type":"NOT_A_TYPE","intensity":99,"symptoms":"AURA,HACK","triggers":"STRESS,BAD",
               "medicationHelped":"MAYBE"}
            ]}
        """.trimIndent()

        val entry = Backup.parse(json).entries.single()

        assertEquals(HeadacheType.UNKNOWN.name, entry.type)
        assertNull(entry.intensity)
        assertEquals("AURA", entry.symptoms)
        assertEquals("STRESS", entry.triggers)
        assertEquals("", entry.medicationHelped)
    }

    @Test
    fun dayFactorsAreClamped() {
        val json = """
            {"app":"HeadacheDiary","entries":[],
             "dayFactors":[{"day":5,"stress":9,"caffeine":42,"alcohol":true}]}
        """.trimIndent()

        val factors = Backup.parse(json).dayFactors.single()

        assertEquals(2, factors.stress)
        assertEquals(4, factors.caffeine)
        assertTrue(factors.alcohol)
    }

    @Test
    fun oldBackupWithoutNewSectionsStillParses() {
        val json = """{"app":"HeadacheDiary","version":1,"entries":[{"startTime":42}]}"""

        val data = Backup.parse(json)

        assertEquals(1, data.entries.size)
        assertTrue(data.painFreeDays.isEmpty())
        assertTrue(data.dayFactors.isEmpty())
    }

    @Test
    fun foreignFileIsRejected() {
        try {
            Backup.parse("""{"app":"SomethingElse","entries":[]}""")
            fail("A file from another app must not be accepted")
        } catch (expected: IllegalArgumentException) {
            // ожидаемо
        }
    }
}
