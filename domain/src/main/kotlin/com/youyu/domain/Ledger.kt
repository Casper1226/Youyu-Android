package com.youyu.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class Currency(val title: String) { CNY("人民币"), USD("美元"), EUR("欧元"), HKD("港币"), GBP("英镑"), JPY("日元"), SGD("新加坡元") }

object Amounts {
    private val pattern = Regex("-?(0|[1-9][0-9]{0,19})(\\.[0-9]{1,8})?")
    fun parse(text: String): BigDecimal {
        val value = text.trim()
        requireRule(pattern.matches(value), "请输入有效金额（最多 8 位小数）")
        return BigDecimal(value)
    }
    fun positive(text: String): BigDecimal = parse(text).also { requireRule(it.signum() > 0, "金额必须大于零") }
    fun encode(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
    fun validate(value: BigDecimal) { parse(encode(value)) }
}

class LedgerException(val userMessage: String) : Exception(userMessage)
fun requireRule(condition: Boolean, message: String) { if (!condition) throw LedgerException(message) }
fun newId(): String = UUID.randomUUID().toString()

enum class AccountKind(val title: String) {
    CASH("现金"), DEBIT("银行卡"), SAVINGS("储蓄"), CREDIT("信用卡"), INVESTMENT("投资"), LOAN("贷款"), OTHER("其他");
    val liability: Boolean get() = this == CREDIT || this == LOAN
}
enum class EntryKind(val title: String) { EXPENSE("支出"), INCOME("收入"), TRANSFER("转账") }

data class Account(
    val id: String = newId(),
    val name: String,
    val kind: AccountKind,
    val currency: Currency,
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    val archived: Boolean = false,
    val order: Int = 0,
    val checkpointAmount: BigDecimal? = null,
    val checkpointAt: Instant? = null,
    val icon: String? = null,
    val cardFace: String = "quiet",
) {
    override fun toString(): String = "Account(redacted)"
}
data class Category(val id: String = newId(), val name: String, val kind: EntryKind,
                    val parentId: String? = null, val icon: String? = null, val color: Int? = null) {
    override fun toString(): String = "Category(redacted)"
}
data class Entry(
    val id: String = newId(),
    val kind: EntryKind,
    val accountId: String,
    val amount: BigDecimal,
    val currency: Currency,
    val occurredAt: Instant,
    val title: String = "",
    val note: String = "",
    val categoryId: String? = null,
    val destinationId: String? = null,
    val destinationAmount: BigDecimal? = null,
    val destinationCurrency: Currency? = null,
    val amortizationMonths: Int = 1,
    val source: String = "manual",
) {
    override fun toString(): String = "Entry(redacted)"
}
data class Budget(val month: YearMonth, val currency: Currency, val limit: BigDecimal) {
    override fun toString(): String = "Budget(redacted)"
}
data class LedgerSettings(val reportingCurrency: Currency = Currency.CNY, val firstWeekday: DayOfWeek = DayOfWeek.MONDAY)
data class LedgerSnapshot(
    val accounts: List<Account> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val categories: List<Category> = defaults(),
    val budgets: List<Budget> = emptyList(),
    val settings: LedgerSettings = LedgerSettings(),
    val artwork: List<Artwork> = emptyList(),
) {
    override fun toString(): String = "LedgerSnapshot(redacted)"
    companion object {
        fun defaults(): List<Category> = CategoryCatalog.defaults()
    }
}

data class DatePeriod(val start: Instant, val end: Instant) {
    operator fun contains(time: Instant): Boolean = time >= start && time < end
}
class LedgerCalendar(val zone: ZoneId, val firstWeekday: DayOfWeek = DayOfWeek.MONDAY) {
    fun month(now: Instant): DatePeriod = month(YearMonth.from(now.atZone(zone)))
    fun month(month: YearMonth): DatePeriod = DatePeriod(
        month.atDay(1).atStartOfDay(zone).toInstant(),
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    )
    fun week(now: Instant): DatePeriod {
        val start = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstWeekday))
        return DatePeriod(start.atStartOfDay(zone).toInstant(), start.plusDays(7).atStartOfDay(zone).toInstant())
    }
}

object LedgerRules {
    fun validate(account: Account, snapshot: LedgerSnapshot) {
        requireRule(account.name.isNotBlank() && account.name.length <= 80, "账户名称需为 1 至 80 字")
        Amounts.validate(account.openingBalance)
        account.checkpointAmount?.let(Amounts::validate)
        requireRule((account.checkpointAt == null) == (account.checkpointAmount == null), "余额校准信息不完整")
        val old = snapshot.accounts.find { it.id == account.id }
        if (old != null && old.currency != account.currency) {
            requireRule(snapshot.entries.none { it.accountId == account.id || it.destinationId == account.id }, "已有流水的账户不能修改币种")
        }
    }
    fun validate(entry: Entry, snapshot: LedgerSnapshot) {
        Amounts.validate(entry.amount)
        requireRule(entry.amount.signum() > 0, "金额必须大于零")
        requireRule(entry.title.length <= 120 && entry.note.length <= 2000, "名称或备注过长")
        val account = snapshot.accounts.find { it.id == entry.accountId }
            ?: throw LedgerException("请选择有效账户")
        val old = snapshot.entries.find { it.id == entry.id }
        requireRule(!account.archived || old?.accountId == account.id, "不能向已归档账户添加流水")
        requireRule(account.currency == entry.currency, "流水币种与账户不一致")
        requireRule(entry.amortizationMonths in 1..120, "分摊月数需为 2 至 120")
        requireRule(entry.kind == EntryKind.EXPENSE || entry.amortizationMonths == 1, "只有支出可以分摊")
        if (entry.kind == EntryKind.TRANSFER) {
            val target = snapshot.accounts.find { it.id == entry.destinationId }
                ?: throw LedgerException("请选择转入账户")
            requireRule(target.id != account.id, "转出与转入账户不能相同")
            requireRule(!target.archived || old?.destinationId == target.id, "不能转入已归档账户")
            val received = entry.destinationAmount ?: throw LedgerException("请输入转入金额")
            Amounts.validate(received)
            requireRule(received.signum() > 0, "转入金额必须大于零")
            requireRule(target.currency == entry.destinationCurrency, "转入币种与账户不一致")
            requireRule(entry.categoryId == null, "转账不计入收支分类")
        } else {
            requireRule(entry.destinationId == null && entry.destinationAmount == null && entry.destinationCurrency == null, "收支流水不能含转入账户")
            entry.categoryId?.let { id ->
                requireRule(snapshot.categories.any { it.id == id && it.kind == entry.kind }, "分类与收支类型不匹配")
            }
        }
    }
    fun balance(account: Account, entries: List<Entry>, through: Instant = Instant.now()): BigDecimal {
        val hasCheckpoint = account.checkpointAt?.let { it <= through } == true
        var total = if (hasCheckpoint) account.checkpointAmount ?: account.openingBalance else account.openingBalance
        entries.filter { it.occurredAt <= through && (!hasCheckpoint || it.occurredAt > account.checkpointAt) }.forEach { entry ->
            if (entry.accountId == account.id && entry.currency == account.currency)
                total += if (entry.kind == EntryKind.INCOME) entry.amount else entry.amount.negate()
            if (entry.destinationId == account.id && entry.destinationCurrency == account.currency)
                total += entry.destinationAmount ?: BigDecimal.ZERO
        }
        return total
    }
    fun expense(entries: List<Entry>, period: DatePeriod, through: Instant, currency: Currency): BigDecimal =
        entries.filter { it.kind == EntryKind.EXPENSE && it.currency == currency && it.occurredAt in period && it.occurredAt <= through }
            .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.amount }

    // Budget-only allocation. The final month retains the exact remainder.
    fun monthlyBudgetUsed(entries: List<Entry>, month: YearMonth, currency: Currency, zone: ZoneId, through: Instant): BigDecimal =
        entries.filter { it.kind == EntryKind.EXPENSE && it.currency == currency && it.occurredAt <= through }
            .fold(BigDecimal.ZERO) { sum, entry ->
                val start = YearMonth.from(entry.occurredAt.atZone(zone))
                val offset = java.time.temporal.ChronoUnit.MONTHS.between(start, month)
                if (offset !in 0 until entry.amortizationMonths.toLong()) sum
                else {
                    val part = entry.amount.divide(BigDecimal(entry.amortizationMonths), java.math.MathContext(38, java.math.RoundingMode.HALF_EVEN))
                    sum + if (offset == entry.amortizationMonths - 1L) entry.amount - part * BigDecimal(entry.amortizationMonths - 1) else part
                }
            }
}
