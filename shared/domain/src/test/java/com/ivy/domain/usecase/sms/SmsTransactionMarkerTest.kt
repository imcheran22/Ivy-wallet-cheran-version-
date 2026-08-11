package com.ivy.domain.usecase.sms

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class SmsTransactionMarkerTest {

    @Test
    fun `round-trips the dedupe key and the person flag`() {
        val description = SmsTransactionMarker.describe(
            refNo = "141159140296",
            dedupeKey = "ref-141159140296",
            paidToPerson = true,
        )

        SmsTransactionMarker.isAutoImported(description).shouldBeTrue()
        SmsTransactionMarker.dedupeKeyOf(description) shouldBe "ref-141159140296"
        SmsTransactionMarker.paidToPerson(description).shouldBeTrue()
    }

    @Test
    fun `omits the person flag for merchant payments`() {
        val description = SmsTransactionMarker.describe(
            refNo = null,
            dedupeKey = "txt-1a2b3c",
            paidToPerson = false,
        )

        SmsTransactionMarker.dedupeKeyOf(description) shouldBe "txt-1a2b3c"
        SmsTransactionMarker.paidToPerson(description).shouldBeFalse()
    }

    @Test
    fun `does not claim a hand-written transaction`() {
        SmsTransactionMarker.isAutoImported("Lunch with Priya").shouldBeFalse()
        SmsTransactionMarker.dedupeKeyOf("Lunch with Priya").shouldBeNull()
        SmsTransactionMarker.isAutoImported(null).shouldBeFalse()
    }
}
