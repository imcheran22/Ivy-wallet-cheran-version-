package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Every case here is a bug that actually happened, or a format that actually broke the rules.
 * The SMS bodies are redacted versions of real Indian bank alerts - the whole point is that
 * these rules were written against real text rather than imagined text.
 */
class BankSmsParserTest {

    @Test
    fun `parses an SBI UPI alert that prints no currency marker`() {
        val parsed = BankSmsParser.parse(
            "Dear UPI user A/C X1234 debited by 340.0 on date 05Aug25 trf to K MANIKANTA " +
                "Refno 141159140296. If not u? call 1800111109. -SBI"
        )

        parsed.shouldNotBeNullAnd {
            type shouldBe TransactionType.EXPENSE
            amount shouldBe 340.0
            payee shouldBe "K MANIKANTA"
            refNo shouldBe "141159140296"
        }
    }

    @Test
    fun `cuts the bank footer off the payee instead of gluing it to the name`() {
        val parsed = BankSmsParser.parse(
            "Rs 500.00 debited from a/c XX1234 and credited to HARIPRIYA VELLODI IF THIS " +
                "TRANSACTION WAS NOT INITIATED BY YOU TO BLOCK UPI SMS BLOCKUPI CUSTOMER ID " +
                "TO 9215676766"
        )

        parsed.shouldNotBeNullAnd { payee shouldBe "HARIPRIYA VELLODI" }
    }

    @Test
    fun `does not truncate a merchant name at a substring match`() {
        // "AMAZON" contains "on", which a naive indexOf footer cut turns into "AMAZ".
        val parsed = BankSmsParser.parse(
            "Rs 899.00 credited to your A/c XX1234 towards refund from AMAZON SELLER " +
                "SERVICES. Ref no 998877665544"
        )

        parsed.shouldNotBeNullAnd {
            payee shouldBe "AMAZON SELLER SERVICES"
            refundHint.shouldBeTrue()
        }
    }

    @Test
    fun `picks the transaction amount, not the credit limit next to it`() {
        val parsed = BankSmsParser.parse(
            "Transaction Amount: Rs 340.00 debited. Available credit limit: Rs 1,00,000.00. " +
                "Total credit limit: Rs 2,00,000.00"
        )

        parsed.shouldNotBeNullAnd { amount shouldBe 340.0 }
    }

    @Test
    fun `is not fooled into skipping a real amount that follows a balance`() {
        val parsed = BankSmsParser.parse(
            "Your A/C XXXXX1234 Balance is Rs 5000.00. Rs 200.00 debited towards UPI on 05-08-25"
        )

        parsed.shouldNotBeNullAnd { amount shouldBe 200.0 }
    }

    @Test
    fun `reads the payee out of a UPI reference run`() {
        val parsed = BankSmsParser.parse(
            "Your A/c XX5678 is debited by Rs.340.00 on 05-08-25. " +
                "Info: UPI/P2M/141159140296/K MANIKANTA. Avl Bal Rs.12,340.55 -SBI"
        )

        parsed.shouldNotBeNullAnd {
            payee shouldBe "K MANIKANTA"
            // P2M means a registered merchant, so the fare rule must not fire.
            paidToPerson.shouldBeFalse()
        }
    }

    @Test
    fun `treats a card purchase at a one-word merchant as a merchant, not a person`() {
        val parsed = BankSmsParser.parse(
            "Rs.1250.00 spent on HDFC Bank Card x1234 at ZOMATO on 2025-08-05:19:22:11. " +
                "Avl Lmt Rs.98,750.00. Not you? Call 18002586161"
        )

        parsed.shouldNotBeNullAnd {
            payee shouldBe "ZOMATO"
            paidToPerson.shouldBeFalse()
        }
    }

    @Test
    fun `treats a two-word UPI name as a person`() {
        val parsed = BankSmsParser.parse(
            "Dear UPI user A/C X1234 debited by 85.0 on date 05Aug25 trf to RAMESH KUMAR " +
                "Refno 141159140297. -SBI"
        )

        parsed.shouldNotBeNullAnd { paidToPerson.shouldBeTrue() }
    }

    @Test
    fun `flags money arriving from the user's own account`() {
        val parsed = BankSmsParser.parse(
            "Rs 10,000.00 credited to A/c XX1234 from your own account XX9999 on 05-08-25. " +
                "Avbl Bal Rs 20,000.00"
        )

        parsed.shouldNotBeNullAnd {
            type shouldBe TransactionType.INCOME
            selfTransferHint.shouldBeTrue()
            // The footer rules refuse to invent a payee out of "your own account".
            payee.shouldBeNull()
        }
    }

    @Test
    fun `ignores an OTP, which arrives before the payment and may never become one`() {
        BankSmsParser.parse(
            "123456 is your OTP for a transaction of Rs 4500 at FLIPKART. Do not share."
        ).shouldBeNull()
    }

    @Test
    fun `ignores a declined transaction`() {
        BankSmsParser.parse(
            "Your transaction of Rs 340 at ZOMATO was declined due to insufficient balance."
        ).shouldBeNull()
    }

    @Test
    fun `gives the same alert the same dedupe key and different alerts different keys`() {
        val body = "Dear UPI user A/C X1234 debited by 85.0 on date 05Aug25 trf to RAMESH " +
            "KUMAR Refno 141159140297. -SBI"
        val other = body.replace("141159140297", "141159140298")

        val first = BankSmsParser.parse(body)!!.dedupeKey
        first shouldBe BankSmsParser.parse(body)!!.dedupeKey
        (first == BankSmsParser.parse(other)!!.dedupeKey) shouldBe false
    }

    @Test
    fun `falls back to a body hash when the alert carries no reference number`() {
        val parsed = BankSmsParser.parse(
            "Rs.320.00 spent at ATLAS STORE using A/c XX1234 on 05-08-25"
        )

        parsed.shouldNotBeNullAnd {
            refNo.shouldBeNull()
            dedupeKey.startsWith("txt-") shouldBe true
        }
    }

    @Test
    fun `strips the acquirer reference welded onto a QR merchant name`() {
        val parsed = BankSmsParser.parse(
            "Dear UPI user A/C X1234 debited by 11.0 on date 12Aug25 trf to " +
                "BHARATPE9O7A7B2M0F2X04941 Refno 112612388233. -SBI"
        )

        parsed.shouldNotBeNullAnd { payee shouldBe "BHARATPE" }
    }

    @Test
    fun `keeps merchant names that are not reference mashes intact`() {
        BankSmsParser.parse(
            "Rs.1250.00 spent on HDFC Bank Card x1234 at AMAZON SELLER SERVICES on 05-08-25"
        ).shouldNotBeNullAnd { payee shouldBe "AMAZON SELLER SERVICES" }

        BankSmsParser.parse(
            "Dear UPI user A/C X1234 debited by 40.0 on date 05Aug25 trf to K MANIKANTA " +
                "Refno 141159140296. -SBI"
        ).shouldNotBeNullAnd { payee shouldBe "K MANIKANTA" }
    }

    @Test
    fun `recognises money-shaped messages more widely than it can parse them`() {
        BankSmsParser.looksLikeMoneyAlert("Rs 500 debited").shouldBeTrue()
        BankSmsParser.looksLikeMoneyAlert("Your parcel is out for delivery").shouldBeFalse()
    }

    private fun ParsedBankSms?.shouldNotBeNullAnd(assertions: ParsedBankSms.() -> Unit) {
        checkNotNull(this) { "Expected the SMS to parse, but it did not" }.assertions()
    }
}
