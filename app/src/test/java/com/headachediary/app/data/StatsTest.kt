package com.headachediary.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun countsDaysNotAttacks() {
        val list = listOf(
            HeadacheEntry(startTime = at(2026, 3, 1, 8), type = HeadacheType.MIGRAINE.name, intensity = 8),
            HeadacheEntry(startTime = at(2026, 3, 1, 20), type = HeadacheType.MIGRAINE.name, intensity = 4),
            HeadacheEntry(startTime = at(2026, 3, 3, 9), type = HeadacheType.TENSION.name, intensity = 3),
        )

        val stats = computeStats(list)

        assertEquals(3, stats.attacks)
        assertEquals(2, stats.painDays)
        assertEquals(1, stats.migraineDays)
        assertEquals(1, stats.tensionDays)
        assertEquals(5.0, stats.avgIntensity!!, 0.001)
    }

    @Test
    fun averageDurationUsesOnlyFinishedAttacks() {
        val start = at(2026, 3, 1, 8)
        val list = listOf(
            HeadacheEntry(startTime = start, endTime = start + 2 * 3_600_000L),
            HeadacheEntry(startTime = start + 86_400_000L, endTime = start + 86_400_000L + 4 * 3_600_000L),
            HeadacheEntry(startTime = start + 2 * 86_400_000L),
        )

        assertEquals(3 * 3_600_000L, computeStats(list).avgDurationMs)
    }

    @Test
    fun emptyListHasNoAverages() {
        val stats = computeStats(emptyList())

        assertEquals(0, stats.attacks)
        assertNull(stats.avgIntensity)
        assertNull(stats.avgDurationMs)
    }

    @Test
    fun topTriggersAreSortedByFrequency() {
        val list = listOf(
            HeadacheEntry(startTime = 1, triggers = "STRESS,SLEEP"),
            HeadacheEntry(startTime = 2, triggers = "STRESS"),
            HeadacheEntry(startTime = 3, triggers = "STRESS,ALCOHOL"),
            HeadacheEntry(startTime = 4, triggers = "SLEEP"),
        )

        val top = computeStats(list).topTriggers

        assertEquals(Trigger.STRESS to 3, top[0])
        assertEquals(Trigger.SLEEP to 2, top[1])
        assertEquals(Trigger.ALCOHOL to 1, top[2])
    }

    @Test
    fun medicationDaysCountDistinctDays() {
        val list = listOf(
            HeadacheEntry(startTime = at(2026, 3, 1, 8), medication = "a"),
            HeadacheEntry(startTime = at(2026, 3, 1, 22), medication = "b"),
            HeadacheEntry(startTime = at(2026, 3, 2, 8)),
        )

        assertEquals(1, computeStats(list).medDays)
    }

    @Test
    fun pressureRelevanceNeedsEnoughEntries() {
        val few = listOf(HeadacheEntry(startTime = 1, pressureChange3h = -5.0))

        val relevance = pressureRelevance(few)

        assertFalse(relevance.known)
        assertFalse(relevance.unlikely)
    }

    @Test
    fun pressureRelevanceDetectsRareSwings() {
        val calm = (1..10).map { HeadacheEntry(startTime = it.toLong(), pressureChange3h = 0.5) } +
            HeadacheEntry(startTime = 99, pressureChange3h = -4.0)

        val relevance = pressureRelevance(calm)

        assertEquals(1, relevance.sharp)
        assertEquals(11, relevance.total)
        assertTrue(relevance.unlikely)
    }

    @Test
    fun sharpChangeBoundaryIsInclusive() {
        assertTrue(HeadacheEntry(startTime = 1, pressureChange3h = 3.0).hasSharpPressureChange())
        assertTrue(HeadacheEntry(startTime = 1, pressureChange3h = -3.0).hasSharpPressureChange())
        assertFalse(HeadacheEntry(startTime = 1, pressureChange3h = 2.9).hasSharpPressureChange())
        assertFalse(HeadacheEntry(startTime = 1).hasSharpPressureChange())
    }

    @Test
    fun factorResultsNeedEnoughDaysInBothGroups() {
        // 5 дней с сильным стрессом и 5 без; боль была в 4 из 5 стрессовых дней и в 1 из 5 остальных.
        val stressed = (1..5).map { DayFactors(day = it.toLong(), stress = 2) }
        val calm = (6..10).map { DayFactors(day = it.toLong(), stress = 0) }
        val painDays = setOf(1L, 2L, 3L, 4L, 6L)

        val results = computeFactorResults(stressed + calm, painDays)
        val high = results.single { it.factor == DayFactor.HIGH_STRESS }

        assertEquals(0.8, high.shareWith, 0.001)
        assertEquals(0.2, high.shareWithout, 0.001)
        // Остальные факторы ни у кого не отмечены, поэтому сравнивать их нечем.
        assertEquals(listOf(DayFactor.HIGH_STRESS), results.map { it.factor }.filter { it == DayFactor.HIGH_STRESS })
        assertTrue(results.none { it.factor == DayFactor.ALCOHOL })
    }

    @Test
    fun factorResultsSkipSmallGroups() {
        val factors = (1..4).map { DayFactors(day = it.toLong(), alcohol = true) } +
            (5..12).map { DayFactors(day = it.toLong()) }

        assertTrue(computeFactorResults(factors, emptySet()).none { it.factor == DayFactor.ALCOHOL })
    }
}
