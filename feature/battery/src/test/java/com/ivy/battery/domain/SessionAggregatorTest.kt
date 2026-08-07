package com.ivy.battery.domain

import com.ivy.battery.data.db.BatterySampleEntity
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class SessionAggregatorTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `charge capacity comes from the coulomb counter when the device has one`() {
        // 20% -> 70% while the counter went from 1000 mAh to 3500 mAh:
        // 2500 mAh bought 50%, so a full battery holds 5000 mAh.
        val samples = listOf(
            sample(at = start, level = 20, chargeCounterUah = 1_000_000, currentMa = 2000.0),
            sample(
                at = start + 10 * minute,
                level = 70,
                chargeCounterUah = 3_500_000,
                currentMa = 2000.0,
            ),
        )

        val session = SessionAggregator.aggregateCharge(
            id = "id",
            samples = samples,
            endedAt = start + 10 * minute,
            designCapacityMah = 5000.0,
            minLevelDelta = 20,
        )!!

        session.chargedMah shouldBe 2500.0
        session.estimatedCapacityMah shouldBe 5000.0
        session.healthPercent shouldBe 100.0
        session.startLevel shouldBe 20
        session.endLevel shouldBe 70
    }

    @Test
    fun `charge without a coulomb counter integrates the reported current`() {
        // A steady 1000 mA across two 15 minute segments is 500 mAh.
        val samples = listOf(
            sample(at = start, level = 40, currentMa = 1000.0),
            sample(at = start + 15 * minute, level = 45, currentMa = 1000.0),
            sample(at = start + 30 * minute, level = 50, currentMa = 1000.0),
        )

        val session = SessionAggregator.aggregateCharge(
            id = "id",
            samples = samples,
            endedAt = null,
            designCapacityMah = 5000.0,
            minLevelDelta = 5,
        )!!

        session.chargedMah shouldBe 500.0
        session.avgCurrentMa shouldBe 1000.0
    }

    @Test
    fun `a charge covering too little of the range yields no capacity estimate`() {
        val samples = listOf(
            sample(at = start, level = 90, chargeCounterUah = 4_500_000),
            sample(at = start + 10 * minute, level = 95, chargeCounterUah = 4_750_000),
        )

        val session = SessionAggregator.aggregateCharge(
            id = "id",
            samples = samples,
            endedAt = start + 10 * minute,
            designCapacityMah = 5000.0,
            minLevelDelta = 20,
        )!!

        session.estimatedCapacityMah.shouldBeNull()
        session.healthPercent.shouldBeNull()
    }

    @Test
    fun `discharge keeps screen-on and screen-off drain apart`() {
        val samples = listOf(
            sample(at = start, level = 100, screenOn = true, currentMa = -900.0),
            sample(at = start + 10 * minute, level = 98, screenOn = true, currentMa = -900.0),
            sample(at = start + 20 * minute, level = 96, screenOn = false, currentMa = -100.0),
            sample(at = start + 30 * minute, level = 95, screenOn = false, currentMa = -100.0),
        )

        val session = SessionAggregator.aggregateDischarge(
            id = "id",
            samples = samples,
            endedAt = start + 30 * minute,
        )!!

        session.screenOnDrainPercent shouldBe 4.0
        session.screenOffDrainPercent shouldBe 1.0
        session.screenOnMs shouldBe 20 * minute
        session.screenOffMs shouldBe 10 * minute
        (session.startLevel - session.endLevel) shouldBe 5
    }

    @Test
    fun `a gap in sampling is not billed to either screen state`() {
        // Nothing was recorded for six hours; that time must not be attributed.
        val samples = listOf(
            sample(at = start, level = 80, currentMa = -100.0),
            sample(at = start + 360 * minute, level = 60, currentMa = -100.0),
        )

        val session = SessionAggregator.aggregateDischarge(
            id = "id",
            samples = samples,
            endedAt = start + 360 * minute,
        )!!

        // Clamped to the 15 minute cap rather than counting the full six hours.
        session.screenOffMs shouldBe 15 * minute
        session.durationMs shouldBe 360 * minute
    }

    @Test
    fun `a reboot mid-session does not corrupt the deep sleep figure`() {
        val samples = listOf(
            sample(at = start, level = 80, elapsedRealtimeMs = 10 * minute, uptimeMs = 5 * minute),
            sample(
                at = start + 10 * minute,
                level = 78,
                elapsedRealtimeMs = 20 * minute,
                uptimeMs = 8 * minute,
            ),
            // Monotonic clocks reset: the device rebooted.
            sample(
                at = start + 20 * minute,
                level = 76,
                elapsedRealtimeMs = minute,
                uptimeMs = minute,
            ),
        )

        val session = SessionAggregator.aggregateDischarge(
            id = "id",
            samples = samples,
            endedAt = start + 20 * minute,
        )!!

        // Only the first pair contributes: 10 min elapsed, 3 min of it awake.
        session.deepSleepMs shouldBe 7 * minute
        session.awakeMs shouldBe 3 * minute
    }

    @Test
    fun `an empty sample list produces no session`() {
        SessionAggregator.aggregateCharge(
            id = "id",
            samples = emptyList(),
            endedAt = null,
            designCapacityMah = 5000.0,
            minLevelDelta = 20,
        ).shouldBeNull()

        SessionAggregator.aggregateDischarge(
            id = "id",
            samples = emptyList(),
            endedAt = null,
        ).shouldBeNull()
    }

    @Test
    fun `average discharge current is derived from energy over time`() {
        val samples = listOf(
            sample(at = start, level = 100, screenOn = true, currentMa = -1000.0),
            sample(at = start + 15 * minute, level = 95, screenOn = true, currentMa = -1000.0),
        )

        val session = SessionAggregator.aggregateDischarge(
            id = "id",
            samples = samples,
            endedAt = null,
        )!!

        // 1000 mA for a quarter of an hour is 250 mAh, i.e. a 1000 mA average.
        session.screenOnMah shouldBe 250.0
        session.avgScreenOnCurrentMa shouldBe (1000.0 plusOrMinus 0.001)
    }

    @Suppress("LongParameterList")
    private fun sample(
        at: Long,
        level: Int,
        chargeCounterUah: Int? = null,
        currentMa: Double = 0.0,
        screenOn: Boolean = false,
        elapsedRealtimeMs: Long = at - start,
        uptimeMs: Long = at - start,
    ) = BatterySampleEntity(
        timestamp = at,
        level = level,
        status = 0,
        plugged = 0,
        temperatureC = 30f,
        voltageMv = 4000,
        currentMa = currentMa,
        chargeCounterUah = chargeCounterUah,
        screenOn = screenOn,
        uptimeMs = uptimeMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
    )
}
