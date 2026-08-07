package com.ivy.battery.domain

import com.ivy.battery.data.db.ChargeSessionEntity
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class HealthCalculatorTest {

    private val start = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `no usable measurement means no health figure`() {
        val estimate = HealthCalculator.estimate(
            sessions = listOf(chargeSession(startLevel = 90, endLevel = 95, capacity = null)),
            designCapacityMah = 5000.0,
        )

        estimate.estimatedCapacityMah.shouldBeNull()
        estimate.healthPercent.shouldBeNull()
        estimate.measurementCount shouldBe 0
    }

    @Test
    fun `measurements are weighted by how much of the range they covered`() {
        // A 60% sweep saying 4000 mAh outweighs a 20% sweep saying 4600 mAh.
        val estimate = HealthCalculator.estimate(
            sessions = listOf(
                chargeSession(startLevel = 20, endLevel = 80, capacity = 4000.0),
                chargeSession(startLevel = 60, endLevel = 80, capacity = 4600.0),
            ),
            designCapacityMah = 5000.0,
        )

        // (4000*60 + 4600*20) / 80 = 4150
        estimate.estimatedCapacityMah!! shouldBe (4150.0 plusOrMinus 0.001)
        estimate.healthPercent!! shouldBe (83.0 plusOrMinus 0.001)
        estimate.measurementCount shouldBe 2
    }

    @Test
    fun `an implausible measurement is discarded rather than dragging the mean`() {
        // 500 mAh out of a 5000 mAh battery is a sensor glitch, not wear.
        val estimate = HealthCalculator.estimate(
            sessions = listOf(
                chargeSession(startLevel = 20, endLevel = 80, capacity = 4500.0),
                chargeSession(startLevel = 20, endLevel = 80, capacity = 500.0),
            ),
            designCapacityMah = 5000.0,
        )

        estimate.estimatedCapacityMah!! shouldBe (4500.0 plusOrMinus 0.001)
        estimate.measurementCount shouldBe 1
    }

    @Test
    fun `cycles count charge added, not the number of charges`() {
        val estimate = HealthCalculator.estimate(
            sessions = listOf(
                chargeSession(startLevel = 50, endLevel = 100, capacity = null),
                chargeSession(startLevel = 60, endLevel = 100, capacity = null),
                chargeSession(startLevel = 90, endLevel = 100, capacity = null),
            ),
            designCapacityMah = 5000.0,
        )

        // 50 + 40 + 10 = 100% of charge, which is one full-equivalent cycle.
        estimate.chargeCycles shouldBe (1.0 plusOrMinus 0.001)
    }

    @Test
    fun `an unknown design capacity still yields a measured capacity`() {
        val estimate = HealthCalculator.estimate(
            sessions = listOf(chargeSession(startLevel = 10, endLevel = 90, capacity = 4200.0)),
            designCapacityMah = null,
        )

        estimate.estimatedCapacityMah!! shouldBe (4200.0 plusOrMinus 0.001)
        estimate.healthPercent.shouldBeNull()
    }

    private var sessionIndex = 0

    private fun chargeSession(
        startLevel: Int,
        endLevel: Int,
        capacity: Double?,
    ): ChargeSessionEntity {
        val index = sessionIndex++
        return ChargeSessionEntity(
            id = "session-$index",
            startedAt = start + index * hour,
            endedAt = start + index * hour + hour,
            startLevel = startLevel,
            endLevel = endLevel,
            plugged = 1,
            chargedMah = 0.0,
            estimatedCapacityMah = capacity,
            healthPercent = null,
            avgCurrentMa = 1500.0,
            maxCurrentMa = 2000.0,
            avgTemperatureC = 32.0,
            maxTemperatureC = 36.0,
            screenOnMs = 0,
            screenOffMs = hour,
            durationMs = hour,
            sampleCount = 60,
        )
    }
}
