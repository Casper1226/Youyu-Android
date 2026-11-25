package com.youyu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.youyu.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun AccountForm(existing: Account?, state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState, dismiss: () -> Unit) {
    val initial = remember { model.photo?.draft ?: AccountDraft.from(existing, state) }
    var draft by remember { mutableStateOf(initial) }
    var library by remember { mutableStateOf(model.photo?.kind) }
    val (id, name, kind, currency, opening, archived, calibrate, currentBalance, icon, cardFace) = draft
    FormPage(if (existing == null) "添加账户" else "编辑账户", operation.busy, operation.error, dismiss, {
        model.perform(dismiss) {
            val checkpoint = if (calibrate) Amounts.parse(currentBalance) else existing?.checkpointAmount
            model.repository.saveAccount(Account(id, name.trim(), kind, currency, Amounts.parse(opening), archived,
                existing?.order ?: state.accounts.size, checkpoint, if (calibrate) Instant.now() else existing?.checkpointAt, icon, cardFace))
        }
    }) {
        Field("账户名称", name, { draft = draft.copy(name = it) })
        AccountAppearanceControls(draft, model) { library = it }
        Choice("账户类型", kind.title, AccountKind.entries, { it.title }) { draft = draft.copy(kind = it) }
        Choice("币种", currency.name, Currency.entries, { it.name + " · " + it.title }) { draft = draft.copy(currency = it) }
        Field("期初余额", opening, { draft = draft.copy(opening = it) })
        Text(if (kind.liability) "信用卡或贷款欠款请填负数，例如 −500；还款通过账户转账记录。" else "账户余额由期初余额和后续流水共同计算。", style = MaterialTheme.typography.bodySmall)
        if (existing != null) {
            HorizontalDivider()
            Text("当前余额  ${money(LedgerRules.balance(existing, state.entries), existing.currency)}", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("校准为当前实际余额", Modifier.weight(1f)); Switch(calibrate, { draft = draft.copy(calibrate = it) }) }
            if (calibrate) {
                Field("实际余额", currentBalance, { draft = draft.copy(currentBalance = it) })
                Text("保存时建立余额检查点；该时刻及以前的流水保留，但不再重复影响余额。", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("归档账户", Modifier.weight(1f)); Switch(archived, { draft = draft.copy(archived = it) }) }
            Text("归档后保留流水，停止向此账户新增记录。", style = MaterialTheme.typography.bodySmall)
        }
    }
    library?.let { type ->
        ArtworkLibrary(type, state, model, operation, draft, { library = null; model.clearError() }) { key ->
            draft = if (type == ArtworkKind.ICON) draft.copy(icon = key) else draft.copy(cardFace = key ?: "quiet")
            library = null
        }
    }

}

@Composable
internal fun EntryForm(existing: Entry?, state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState, dismiss: () -> Unit) {
    val id = remember { existing?.id ?: newId() }
    var kind by remember { mutableStateOf(existing?.kind ?: EntryKind.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.amount?.let(Amounts::encode) ?: "") }
    var accountId by remember { mutableStateOf(existing?.accountId ?: state.accounts.firstOrNull { !it.archived }?.id) }
    var destinationId by remember { mutableStateOf(existing?.destinationId) }
    var received by remember { mutableStateOf(existing?.destinationAmount?.let(Amounts::encode) ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val initialTime = remember { (existing?.occurredAt ?: Instant.now()).atZone(ZoneId.systemDefault()) }
    var date by remember { mutableStateOf(initialTime.toLocalDate().toString()) }
    var time by remember { mutableStateOf(initialTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var amortize by remember { mutableStateOf((existing?.amortizationMonths ?: 1) > 1) }
    var months by remember { mutableStateOf((existing?.amortizationMonths?.takeIf { it > 1 } ?: 3).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val accounts = state.accounts.filter { !it.archived || it.id == existing?.accountId || it.id == existing?.destinationId }
    val account = accounts.find { it.id == accountId }
    val destination = accounts.find { it.id == destinationId }
    FormPage(if (existing == null) "记一笔" else "编辑流水", operation.busy, operation.error, dismiss, {
        model.perform(dismiss) {
            val selected = account ?: throw LedgerException("请先添加并选择账户")
            val occurred = try {
                if (date == initialTime.toLocalDate().toString() && time == initialTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                    initialTime.toInstant()
                else LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant()
            } catch (_: Exception) { throw LedgerException("请填写有效日期 YYYY-MM-DD 与时间 HH:mm") }
            val count = if (amortize && kind == EntryKind.EXPENSE) months.toIntOrNull()?.takeIf { it in 2..120 }
                ?: throw LedgerException("分摊月数需为 2 至 120") else 1
            val value = Amounts.positive(amount)
            model.repository.saveEntry(Entry(id, kind, selected.id, value, selected.currency, occurred, title.trim(), note.trim(),
                if (kind == EntryKind.TRANSFER) null else categoryId,
                if (kind == EntryKind.TRANSFER) destinationId else null,
                if (kind == EntryKind.TRANSFER) Amounts.positive(received.ifBlank { if (destination?.currency == selected.currency) amount else "" }) else null,
                if (kind == EntryKind.TRANSFER) destination?.currency else null, count, existing?.source ?: "manual"))
        }
    }) {
        Choice("类型", kind.title, EntryKind.entries, { it.title }) { kind = it; categoryId = null }
        Choice(if (kind == EntryKind.TRANSFER) "转出账户" else "账户", account?.name ?: "请选择", accounts, { it.name + " · " + it.currency.name }) { accountId = it.id }
        Field("金额 · ${account?.currency?.name ?: "CNY"}", amount, { amount = it }, decimal = true)
        if (kind == EntryKind.TRANSFER) {
            Choice("转入账户", destination?.name ?: "请选择", accounts.filter { it.id != accountId }, { it.name + " · " + it.currency.name }) { destinationId = it.id }
            Field("转入金额 · ${destination?.currency?.name ?: "币种"}", received, { received = it }, decimal = true)
            Text("同币种留空时采用转出金额；跨币种必须填写实际到账金额。", style = MaterialTheme.typography.bodySmall)
        } else {
            Choice("分类", categoryPath(state.categories.find { it.id == categoryId }, state.categories),
                listOf(null) + state.categories.filter { it.kind == kind }.sortedBy { categoryPath(it, state.categories) }, { categoryPath(it, state.categories) }) { categoryId = it?.id }
        }
        Field("名称", title, { title = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("日期 YYYY-MM-DD", date, { date = it }, Modifier.weight(1f))
            Field("时间 HH:mm", time, { time = it }, Modifier.weight(1f))
        }
        Field("备注", note, { note = it }, singleLine = false)
        if (kind == EntryKind.EXPENSE) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("按月分摊预算", Modifier.weight(1f)); Switch(amortize, { amortize = it }) }
            if (amortize) { Field("分摊月数", months, { months = it }, decimal = true); Text("从发生月开始分摊，账户余额仍计入全额。", style = MaterialTheme.typography.bodySmall) }
        }
        if (existing != null) TextButton(onClick = { confirmDelete = true }, enabled = !operation.busy) { Text("删除流水", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete && existing != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除这笔流水？") }, text = { Text("账户余额和统计将同步重新计算，此操作不能撤销。") },
        confirmButton = { TextButton(onClick = { model.perform(dismiss) { model.repository.deleteEntry(existing.id) }; confirmDelete = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@Composable
internal fun BudgetForm(state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState, dismiss: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    val currency = state.settings.reportingCurrency
    var amount by remember { mutableStateOf(state.budgets.find { it.month == YearMonth.now() && it.currency == currency }?.limit?.let(Amounts::encode) ?: "") }
    FormPage("月度预算", operation.busy, operation.error, dismiss, {
        model.perform(dismiss) {
            val selected = try { YearMonth.parse(month) } catch (_: Exception) { throw LedgerException("请输入有效月份 YYYY-MM") }
            model.repository.saveBudget(Budget(selected, currency, Amounts.parse(amount)))
        }
    }) {
        Field("月份 YYYY-MM", month, { month = it })
        Field("预算上限 · ${currency.name}", amount, { amount = it }, decimal = true)
        Text("同一月份与币种的预算会被更新。金额为 0 表示该月不安排支出预算。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun Settings(state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState, dismiss: () -> Unit, categories: () -> Unit, library: (ArtworkKind) -> Unit) {
    var currency by remember { mutableStateOf(state.settings.reportingCurrency) }
    var weekday by remember { mutableStateOf(state.settings.firstWeekday) }
    FormPage("设置", operation.busy, operation.error, dismiss, {
        model.perform(dismiss) { model.repository.saveSettings(LedgerSettings(currency, weekday)) }
    }) {
        Text("账本", style = MaterialTheme.typography.titleLarge)
        Choice("摘要币种", currency.name, Currency.entries, { it.name + " · " + it.title }) { currency = it }
        Text("摘要只合计所选币种；其他币种分别保留，不进行隐式换算。", style = MaterialTheme.typography.bodySmall)
        Choice("每周起始日", weekdayName(weekday), DayOfWeek.entries, ::weekdayName) { weekday = it }
        OutlinedButton(onClick = categories, modifier = Modifier.fillMaxWidth()) { Text("分类管理") }
        HorizontalDivider()
        OutlinedButton({ library(ArtworkKind.ICON) }, Modifier.fillMaxWidth()) { Text("账户图标库") }
        OutlinedButton({ library(ArtworkKind.CARD) }, Modifier.fillMaxWidth()) { Text("卡面管理") }
        HorizontalDivider()
        Text("隐私与安全", style = MaterialTheme.typography.titleLarge)
        Text("账本使用 SQLCipher 加密。金库密钥由口令与 Android Keystore 双重保护；离开应用即锁定。")
        Text("从相册导入时使用系统图片选择器。裁剪后的图片保存在加密账本中。", style = MaterialTheme.typography.bodyMedium)
        Text("当前金库绑定本机设备密钥。请保留口令和应用；可迁移加密备份将在后续阶段提供。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = model::lock, modifier = Modifier.fillMaxWidth()) { Text("立即锁定") }
    }
}
private fun weekdayName(day: DayOfWeek): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day.value - 1]

internal fun categoryPath(category: Category?, all: List<Category>): String {
    if (category == null) return "未分类"
    return all.find { it.id == category.parentId }?.let { it.name + " › " + category.name } ?: category.name
}

@Composable
internal fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}
