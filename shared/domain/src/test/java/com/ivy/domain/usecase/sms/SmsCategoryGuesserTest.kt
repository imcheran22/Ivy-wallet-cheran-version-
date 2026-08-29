package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class SmsCategoryGuesserTest {

    @Test
    fun `guesses a fare for a small payment to a person and says why`() {
        val guess = SmsCategoryGuesser.guessFromRecord(
            payee = "RAMESH KUMAR",
            amount = 85.0,
            type = TransactionType.EXPENSE,
            paidToPerson = true,
        )

        guess?.categoryName shouldBe SmsCategories.CABS_AND_AUTOS
        guess?.reason shouldContain "paid to a person"
    }

    @Test
    fun `does not guess a fare for a merchant payment of the same size`() {
        SmsCategoryGuesser.guessFromRecord(
            payee = "ATLAS STORE",
            amount = 85.0,
            type = TransactionType.EXPENSE,
            paidToPerson = false,
        ).shouldBeNull()
    }

    @Test
    fun `does not guess a fare outside the fare range`() {
        SmsCategoryGuesser.guessFromRecord(
            payee = "RAMESH KUMAR",
            amount = 5000.0,
            type = TransactionType.EXPENSE,
            paidToPerson = true,
        ).shouldBeNull()
    }

    @Test
    fun `keeps a transfer between your own accounts out of earnings`() {
        val parsed = BankSmsParser.parse(
            "Rs 10,000.00 credited to A/c XX1234 from your own account XX9999 on 05-08-25. " +
                "Avbl Bal Rs 20,000.00"
        )!!

        val guess = SmsCategoryGuesser.guess(parsed)

        guess?.categoryName shouldBe SmsCategories.MOVED_BETWEEN_ACCOUNTS
        SmsCategories.excludedFromEarnings.contains(guess?.categoryName) shouldBe true
    }

    @Test
    fun `keeps a refund out of earnings`() {
        val parsed = BankSmsParser.parse(
            "Rs 899.00 credited to your A/c XX1234 towards refund from AMAZON SELLER " +
                "SERVICES. Ref no 998877665544"
        )!!

        SmsCategoryGuesser.guess(parsed)?.categoryName shouldBe SmsCategories.REFUND
    }

    @Test
    fun `explains a keyword guess with the keyword it matched`() {
        val guess = SmsCategoryGuesser.guessFromRecord(
            payee = "SWIGGY",
            amount = 240.0,
            type = TransactionType.EXPENSE,
            paidToPerson = false,
        )

        guess?.categoryName shouldBe "Food"
        guess?.reason shouldContain "swiggy"
    }
}
