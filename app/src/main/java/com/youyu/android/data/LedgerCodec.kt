package com.youyu.android.data

import com.youyu.domain.*
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth

internal object LedgerCodec {
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    fun account(value: Account): String = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("kind", value.kind.name); put("currency", value.currency.name)
        put("opening", Amounts.encode(value.openingBalance)); put("archived", value.archived); put("order", value.order)
        put("icon", value.icon); put("cardFace", value.cardFace)
        put("checkpoint", value.checkpointAmount?.let(Amounts::encode)); put("checkpointAt", value.checkpointAt?.toString())
    }.toString()
    fun account(value: String): Account = JSONObject(value).let {
        Account(it.getString("id"), it.getString("name"), AccountKind.valueOf(it.getString("kind")), Currency.valueOf(it.getString("currency")),
            Amounts.parse(it.getString("opening")), it.getBoolean("archived"), it.getInt("order"),
            it.stringOrNull("checkpoint")?.let(Amounts::parse), it.stringOrNull("checkpointAt")?.let(Instant::parse), it.stringOrNull("icon"), it.optString("cardFace", "quiet"))
    }
    fun entry(value: Entry): String = JSONObject().apply {
        put("id", value.id); put("kind", value.kind.name); put("account", value.accountId)
        put("amount", Amounts.encode(value.amount)); put("currency", value.currency.name); put("occurredAt", value.occurredAt.toString())
        put("title", value.title); put("note", value.note); put("category", value.categoryId)
        put("destination", value.destinationId); put("received", value.destinationAmount?.let(Amounts::encode))
        put("receivedCurrency", value.destinationCurrency?.name); put("months", value.amortizationMonths); put("source", value.source)
    }.toString()
    fun entry(value: String): Entry = JSONObject(value).let {
        Entry(it.getString("id"), EntryKind.valueOf(it.getString("kind")), it.getString("account"), Amounts.positive(it.getString("amount")),
            Currency.valueOf(it.getString("currency")), Instant.parse(it.getString("occurredAt")), it.getString("title"), it.getString("note"),
            it.stringOrNull("category"), it.stringOrNull("destination"), it.stringOrNull("received")?.let(Amounts::positive),
            it.stringOrNull("receivedCurrency")?.let(Currency::valueOf), it.getInt("months"), it.getString("source"))
    }
    fun category(value: Category): String = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("kind", value.kind.name)
        put("parent", value.parentId); put("icon", value.icon); put("color", value.color)
    }.toString()
    fun category(value: String): Category = JSONObject(value).let { Category(it.getString("id"), it.getString("name"), EntryKind.valueOf(it.getString("kind")),
        it.stringOrNull("parent"), it.stringOrNull("icon"), if (it.isNull("color")) null else it.getInt("color")) }
    fun budget(value: Budget): String = JSONObject().apply { put("month", value.month.toString()); put("currency", value.currency.name); put("limit", Amounts.encode(value.limit)) }.toString()
    fun budget(value: String): Budget = JSONObject(value).let { Budget(YearMonth.parse(it.getString("month")), Currency.valueOf(it.getString("currency")), Amounts.parse(it.getString("limit"))) }
    fun settings(value: LedgerSettings): String = JSONObject().apply { put("currency", value.reportingCurrency.name); put("weekday", value.firstWeekday.value) }.toString()
    fun settings(value: String): LedgerSettings = JSONObject(value).let { LedgerSettings(Currency.valueOf(it.getString("currency")), DayOfWeek.of(it.getInt("weekday"))) }
}
