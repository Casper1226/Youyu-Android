package com.youyu.domain

import java.math.BigDecimal
import java.time.*
import kotlin.test.*
import org.junit.Test

class LedgerRulesTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val bank = Account(id = "a", name = "合成账户", kind = AccountKind.DEBIT, currency = Currency.CNY, openingBalance = BigDecimal("1000"))
    private val credit = Account(id = "b", name = "合成信用卡", kind = AccountKind.CREDIT, currency = Currency.CNY, openingBalance = BigDecimal("-500"))
    private fun expense(amount: String = "0.1", date: Instant = now) = Entry(kind = EntryKind.EXPENSE, accountId = bank.id, amount = BigDecimal(amount), currency = bank.currency, occurredAt = date)

    @Test fun decimalArithmeticRemainsExact() {
        val entries = (1..10).map { expense() }
        assertEquals(0, LedgerRules.balance(bank, entries, now).compareTo(BigDecimal("999")))
    }
    @Test fun rejectsExponentAndUnboundedAmounts() {
        listOf("NaN", "1e3", "1,000", "01", "0.000000001", "100000000000000000000").forEach {
            assertFailsWith<LedgerException> { Amounts.parse(it) }
        }
    }
    @Test fun rejectsZeroNegativeAndInvalidAccounts() {
        val state = LedgerSnapshot(accounts = listOf(bank))
        listOf("0", "-1").forEach { assertFailsWith<LedgerException> { LedgerRules.validate(expense(it), state) } }
        assertFailsWith<LedgerException> { LedgerRules.validate(expense().copy(accountId = "missing"), state) }
    }
    @Test fun creditRepaymentIsTransferAndDoesNotBecomeExpense() {
        val payment = Entry(kind = EntryKind.TRANSFER, accountId = bank.id, amount = BigDecimal("100"), currency = Currency.CNY,
            occurredAt = now, destinationId = credit.id, destinationAmount = BigDecimal("100"), destinationCurrency = Currency.CNY)
        val state = LedgerSnapshot(accounts = listOf(bank, credit))
        LedgerRules.validate(payment, state)
        assertEquals(0, LedgerRules.balance(credit, listOf(payment), now).compareTo(BigDecimal("-400")))
        assertEquals(0, LedgerRules.balance(bank, listOf(payment), now).compareTo(BigDecimal("900")))
        assertEquals(0, LedgerRules.expense(listOf(payment), LedgerCalendar(ZoneOffset.UTC).month(now), now, Currency.CNY).signum())
    }
    @Test fun crossCurrencyUsesActualDestinationAmount() {
        val dollar = bank.copy(id = "usd", currency = Currency.USD, openingBalance = BigDecimal.ZERO)
        val transfer = expense("700").copy(kind = EntryKind.TRANSFER, destinationId = dollar.id, destinationAmount = BigDecimal("98.12"), destinationCurrency = Currency.USD)
        LedgerRules.validate(transfer, LedgerSnapshot(accounts = listOf(bank, dollar)))
        assertEquals(0, LedgerRules.balance(dollar, listOf(transfer), now).compareTo(BigDecimal("98.12")))
    }
    @Test fun refusesSameAccountTransfer() {
        assertFailsWith<LedgerException> { LedgerRules.validate(expense().copy(kind = EntryKind.TRANSFER,
            destinationId = bank.id, destinationAmount = BigDecimal.ONE, destinationCurrency = Currency.CNY), LedgerSnapshot(accounts = listOf(bank))) }
    }
    @Test fun checkpointExcludesBoundaryAndOlderEntries() {
        val account = bank.copy(checkpointAmount = BigDecimal("50"), checkpointAt = now.minusSeconds(1))
        assertEquals(0, LedgerRules.balance(account, listOf(expense("100", now.minusSeconds(1)), expense("2")), now).compareTo(BigDecimal("48")))
        assertEquals(0, LedgerRules.balance(account, emptyList(), now.minusSeconds(2)).compareTo(BigDecimal("1000")))
    }
    @Test fun futureAndOtherCurrencyDoNotEnterExpenseTotal() {
        val entries = listOf(expense("2"), expense("8", now.plusSeconds(1)), expense("5").copy(currency = Currency.USD))
        assertEquals(0, LedgerRules.expense(entries, LedgerCalendar(ZoneOffset.UTC).month(now), now, Currency.CNY).compareTo(BigDecimal("2")))
    }
    @Test fun periodIsHalfOpen() {
        val calendar = LedgerCalendar(ZoneOffset.UTC)
        val period = calendar.month(now)
        val entries = listOf(expense("3", period.start), expense("5", period.end))
        assertEquals(0, LedgerRules.expense(entries, period, period.end, Currency.CNY).compareTo(BigDecimal("3")))
    }
    @Test fun weekUsesCalendarDaysAcrossDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val period = LedgerCalendar(zone).week(Instant.parse("2026-03-08T12:00:00Z"))
        assertEquals(LocalDate.of(2026, 3, 2), period.start.atZone(zone).toLocalDate())
        assertEquals(167, Duration.between(period.start, period.end).toHours())
    }
    @Test fun firstWeekdayIsSharedAndExplicit() {
        val period = LedgerCalendar(ZoneOffset.UTC, DayOfWeek.SUNDAY).week(now)
        assertEquals(DayOfWeek.SUNDAY, period.start.atZone(ZoneOffset.UTC).dayOfWeek)
    }
    @Test fun amortizationPreservesCashAndExactRemainder() {
        val entry = expense("100").copy(amortizationMonths = 3)
        val month = YearMonth.of(2026, 11)
        val parts = (0L..2L).map { LedgerRules.monthlyBudgetUsed(listOf(entry), month.plusMonths(it), Currency.CNY, ZoneOffset.UTC, now) }
        assertTrue(parts.first().scale() > 2)
        assertEquals(0, parts.reduce(BigDecimal::add).compareTo(BigDecimal("100")))
        assertEquals(parts[0], parts[1])
        assertEquals(0, LedgerRules.balance(bank, listOf(entry), now).compareTo(BigDecimal("900")))
    }
    @Test fun referencedAccountCannotChangeCurrency() {
        val state = LedgerSnapshot(accounts = listOf(bank), entries = listOf(expense()))
        assertFailsWith<LedgerException> { LedgerRules.validate(bank.copy(currency = Currency.USD), state) }
    }
    @Test fun archivedAccountCannotReceiveNewEntries() {
        assertFailsWith<LedgerException> { LedgerRules.validate(expense(), LedgerSnapshot(accounts = listOf(bank.copy(archived = true)))) }
    }
    @Test fun entitiesNeverExposeFinancialContentsThroughToString() {
        assertEquals("Entry(redacted)", expense("12345.67").copy(title = "secret merchant").toString())
        assertEquals("Account(redacted)", bank.toString())
        assertEquals("LedgerSnapshot(redacted)", LedgerSnapshot(accounts = listOf(bank)).toString())
    }
}
