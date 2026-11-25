@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.youyu.android.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youyu.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*

@Composable
fun YouyuApp(model: LedgerViewModel) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA0C0FF), onPrimary = Color(0xFF122442),
        primaryContainer = Color(0xFF252A3A), onPrimaryContainer = Color(0xFFEEF0F6),
        secondary = Color(0xFFA0C0FF), secondaryContainer = Color(0xFF252A3A), onSecondaryContainer = Color(0xFFEEF0F6),
        background = Color(0xFF111216), onBackground = Color(0xFFEEF0F6),
        surface = Color(0xFF111216), onSurface = Color(0xFFEEF0F6),
        surfaceVariant = Color(0xFF252A3A), onSurfaceVariant = Color(0xFFC0C4D0),
        surfaceContainerLowest = Color(0xFF111216), surfaceContainerLow = Color(0xFF1C1E24),
        surfaceContainer = Color(0xFF1C1E24), surfaceContainerHigh = Color(0xFF252A3A),
        surfaceContainerHighest = Color(0xFF303441), outline = Color(0xFF8B8F9B), outlineVariant = Color(0xFF363A46)
    ) else lightColorScheme(
        primary = Color(0xFF3973D9), onPrimary = Color.White,
        primaryContainer = Color(0xFFE9F0FE), onPrimaryContainer = Color(0xFF181B24),
        secondary = Color(0xFF3973D9), secondaryContainer = Color(0xFFE9F0FE), onSecondaryContainer = Color(0xFF244D90),
        background = Color(0xFFF7F8FA), onBackground = Color(0xFF181B24),
        surface = Color(0xFFF7F8FA), onSurface = Color(0xFF181B24),
        surfaceVariant = Color(0xFFEFF2FA), onSurfaceVariant = Color(0xFF5B6170),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEFF2FA),
        surfaceContainerHighest = Color(0xFFE8ECF4), outline = Color(0xFF747A88), outlineVariant = Color(0xFFDDE1EA)
    )
    MaterialTheme(colorScheme = colors) {
        val state by model.snapshot.collectAsStateWithLifecycle()
        val operation by model.operation.collectAsStateWithLifecycle()
        PhotoHost(model) { Surface(Modifier.fillMaxSize()) {
            val current = state
            if (current == null) UnlockScreen(model, operation)
            else LedgerScreen(current, model, operation)
        } }
    }
}

@Composable
private fun UnlockScreen(model: LedgerViewModel, operation: OperationState) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val create = !model.hasVault
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (operation.busy || password.isEmpty()) return
        keyboard?.hide()
        focus.clearFocus()
        when {
            create && password != confirmation -> localError = "两次口令不一致"
            create && password.length < 12 -> localError = "金库口令至少需要 12 个字符"
            else -> {
                model.unlock(password.toCharArray(), create)
                password = ""
                confirmation = ""
            }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text("有余", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(if (create) "从一份只属于你的账本开始。" else "欢迎回来。解锁你的本机账本。", Modifier.padding(top = 12.dp, bottom = 32.dp))
        OutlinedTextField(password, { password = it; localError = null }, Modifier.fillMaxWidth(),
            label = { Text("金库口令") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (create) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }))
        if (create) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(),
                label = { Text("再次输入口令") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            Text("至少 12 个字符。口令无法找回；卸载应用或丢失设备密钥后，本机账本将无法恢复。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        }
        (localError ?: operation.error)?.let { ErrorText(it) }
        Button(onClick = ::submit, enabled = !operation.busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(if (operation.busy) "正在打开…" else if (create) "创建加密金库" else "解锁")
        }
        Text("加密保存在这台设备 · 默认不联网", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 28.dp))
    }
}

private enum class Tab(val title: String) { HOME("主页"), LEDGER("流水"), PLANNING("预算"), ASSETS("资产") }
private sealed interface Editor {
    data class AccountForm(val value: Account?) : Editor
    data class Detail(val id: String) : Editor
    data class Library(val kind: ArtworkKind) : Editor
    data class EntryForm(val value: Entry?) : Editor
    data object BudgetForm : Editor
    data object Settings : Editor
    data object Categories : Editor
}
@Composable
private fun LedgerScreen(state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState) {
    val request = model.photo
    val pager = rememberPagerState(initialPage = if (request?.draft != null) Tab.ASSETS.ordinal else 0) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries[pager.currentPage]
    var detailId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<Editor?>(request?.let { pending ->
        pending.draft?.let { Editor.AccountForm(state.accounts.find { account -> account.id == it.id }) } ?: Editor.Library(pending.kind)
    }) }
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(operation.error, editor) {
        if (editor == null) operation.error?.let { snackbar.showSnackbar(it); model.clearError() }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.title, fontWeight = FontWeight.SemiBold) }, actions = {
            IconButton(onClick = { editor = Editor.Settings }) { Icon(Icons.Outlined.Settings, "设置") }
            IconButton(onClick = model::lock) { Icon(Icons.Outlined.Lock, "锁定金库") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavigationBar {
            Tab.entries.forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = {
                    scope.launch { pager.animateScrollToPage(item.ordinal) }; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }, label = { Text(item.title) }, icon = {
                    Icon(when (item) { Tab.HOME -> Icons.Outlined.Home; Tab.LEDGER -> Icons.AutoMirrored.Outlined.ReceiptLong; Tab.PLANNING -> Icons.Outlined.CalendarMonth; Tab.ASSETS -> Icons.Outlined.AccountBalanceWallet }, null)
                })
            }
        } },
        floatingActionButton = { if (tab != Tab.PLANNING) FloatingActionButton(onClick = {
            editor = if (state.accounts.none { !it.archived } || tab == Tab.ASSETS) Editor.AccountForm(null) else Editor.EntryForm(null)
        }) {
            Icon(Icons.Outlined.Add, if (tab == Tab.ASSETS || state.accounts.none { !it.archived }) "添加账户" else "记一笔")
        } },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding).testTag("main-pages"), beyondViewportPageCount = 0) { page ->
            when (Tab.entries[page]) {
                Tab.HOME -> Home(state, Modifier, { scope.launch { pager.animateScrollToPage(Tab.ASSETS.ordinal) } }, { editor = Editor.EntryForm(it) })
                Tab.LEDGER -> Transactions(state, Modifier) { editor = Editor.EntryForm(it) }
                Tab.PLANNING -> Planning(state, Modifier) { editor = Editor.BudgetForm }
                Tab.ASSETS -> Accounts(state, model, Modifier) { account ->
                    detailId = account.id
                    editor = Editor.Detail(account.id)
                }
            }
        }
    }
    val dismiss = { editor = null; model.clearError() }
    val closeEdit = { editor = detailId?.let { Editor.Detail(it) }; model.clearError() }
    when (val current = editor) {
        is Editor.AccountForm -> AccountForm(current.value, state, model, operation, closeEdit)
        is Editor.EntryForm -> EntryForm(current.value, state, model, operation, closeEdit)
        is Editor.Detail -> state.accounts.find { it.id == current.id }?.let { account ->
            AccountDetails(account, state, model, { detailId = null; dismiss() }, { editor = Editor.AccountForm(account) }, { editor = Editor.EntryForm(it) })
        }
        Editor.BudgetForm -> BudgetForm(state, model, operation, dismiss)
        Editor.Settings -> Settings(state, model, operation, dismiss, { editor = Editor.Categories }, { editor = Editor.Library(it) })
        Editor.Categories -> Categories(state, model, operation) { editor = Editor.Settings; model.clearError() }
        is Editor.Library -> ArtworkLibrary(current.kind, state, model, operation, null, { editor = Editor.Settings; model.clearError() })
        null -> Unit
    }

}

@Composable
private fun Home(state: LedgerSnapshot, modifier: Modifier, onAccounts: () -> Unit, onEntry: (Entry) -> Unit) {
    val now = Instant.now()
    val currency = state.settings.reportingCurrency
    val calendar = LedgerCalendar(ZoneId.systemDefault(), state.settings.firstWeekday)
    val accounts = state.accounts.filter { !it.archived && it.currency == currency }
    val balance = accounts.fold(BigDecimal.ZERO) { sum, account -> sum + LedgerRules.balance(account, state.entries, now) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(onClick = onAccounts, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("净资产 · ${currency.name}", style = MaterialTheme.typography.labelLarge)
                    Text(money(balance, currency), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 16.dp))
                    Text("账户余额与负债合计", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.accounts.any { !it.archived && it.currency != currency }) item {
            Text("其他币种在资产页分别显示，当前未做汇率折算。", style = MaterialTheme.typography.bodySmall)
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("本月支出", money(LedgerRules.expense(state.entries, calendar.month(now), now, currency), currency), Modifier.weight(1f))
            SummaryCard("本周支出", money(LedgerRules.expense(state.entries, calendar.week(now), now, currency), currency), Modifier.weight(1f))
        } }
        if (state.accounts.isEmpty()) item { EmptyCard("开始记录", "先添加一个账户，填写当前余额，再记下第一笔收支。") }
        item { Text("最近流水", style = MaterialTheme.typography.titleMedium) }
        if (state.entries.isEmpty()) item { EmptyCard("还没有流水", "每一次清晰的记录，都让下一步更从容。") }
        items(state.entries.take(5), key = { it.id }) { EntryRow(it, state) { onEntry(it) } }
    }
}

@Composable
private fun Transactions(state: LedgerSnapshot, modifier: Modifier, onEntry: (Entry) -> Unit) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<EntryKind?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var uncategorized by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf("全部日期") }
    val now = Instant.now()
    val calendar = LedgerCalendar(ZoneId.systemDefault(), state.settings.firstWeekday)
    val range = when (period) { "本月" -> calendar.month(now); "本周" -> calendar.week(now); else -> null }
    val entries = state.entries.filter {
        (!uncategorized || (it.kind != EntryKind.TRANSFER && it.categoryId == null)) && (kind == null || it.kind == kind) && (accountId == null || it.accountId == accountId || it.destinationId == accountId) &&
            (range == null || it.occurredAt in range) && (query.isBlank() || it.title.contains(query, true) || it.note.contains(query, true))
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索名称或备注") }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("类型", kind?.title ?: "全部", listOf(null) + EntryKind.entries, { it?.title ?: "全部" }, Modifier.weight(1f)) { kind = it }
            Choice("日期", period, listOf("全部日期", "本月", "本周"), { it }, Modifier.weight(1f)) { period = it }
        } }
        item { Choice("账户", state.accounts.find { it.id == accountId }?.name ?: "全部账户", listOf(null) + state.accounts, { it?.name ?: "全部账户" }) { accountId = it?.id } }
        item { FilterChip(uncategorized, { uncategorized = !uncategorized }, { Text("未分类") }) }
        item { Text("${entries.size} 笔流水", style = MaterialTheme.typography.labelLarge) }
        if (entries.isEmpty()) item { EmptyCard("没有匹配的流水", "调整筛选，或通过右下角记录一笔。") }
        items(entries, key = { it.id }) { EntryRow(it, state) { onEntry(it) } }
    }
}

@Composable
private fun Accounts(state: LedgerSnapshot, model: LedgerViewModel, modifier: Modifier, onAccount: (Account) -> Unit) {
    var showArchived by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("显示归档账户", Modifier.weight(1f)); Switch(showArchived, { showArchived = it })
        } }
        if (state.accounts.isEmpty()) item { EmptyCard("账户让流水有归处", "添加现金、银行卡、信用卡或其他账户。") }
        items(state.accounts.filter { showArchived || !it.archived }, key = { it.id }) { account ->
            val open by rememberUpdatedState { onAccount(account) }
            Card(onClick = { open() }, modifier = Modifier.pointerInput(account.id) {
                val threshold = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val change = awaitHorizontalTouchSlopOrCancellation(down.id) { event, excess ->
                        if (excess > 0f) event.consume()
                    } ?: return@awaitEachGesture
                    var distance = change.position.x - down.position.x
                    val completed = horizontalDrag(change.id) { drag ->
                        distance += drag.positionChange().x
                        drag.consume()
                    }
                    if (completed && distance >= threshold) open()
                }
            }) {
                AccountSurface(account.cardFace, model, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountIcon(AccountAppearance.icon(account.name, account.kind, account.icon), model)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium)
                        Text(account.kind.title + if (account.archived) " · 已归档" else "", style = MaterialTheme.typography.bodySmall)
                        Text(money(LedgerRules.balance(account, state.entries), account.currency), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                        if (account.kind.liability) Text("负数表示待还款", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        val ids = state.accounts.map { it.id }.toMutableList()
                        val index = ids.indexOf(account.id)
                        if (index > 0) { ids.removeAt(index); ids.add(index - 1, account.id); model.perform { model.repository.reorderAccounts(ids) } }
                    }, enabled = state.accounts.firstOrNull()?.id != account.id) { Icon(Icons.Outlined.ArrowUpward, "上移账户") }
                } }
            }
        }
    }
}

@Composable
private fun AccountDetails(account: Account, state: LedgerSnapshot, model: LedgerViewModel, close: () -> Unit, edit: () -> Unit, onEntry: (Entry) -> Unit) {
    val entries = state.entries.filter { it.accountId == account.id || it.destinationId == account.id }
    var limit by remember(account.id) { mutableIntStateOf(20) }
    FormPage("账户详情", false, null, close, null) {
        AccountSurface(account.cardFace, model, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountIcon(AccountAppearance.icon(account.name, account.kind, account.icon), model)
                    Text(account.name, style = MaterialTheme.typography.titleLarge)
                }
                Text(money(LedgerRules.balance(account, state.entries), account.currency), style = MaterialTheme.typography.headlineMedium)
                Text(account.kind.title + if (account.archived) " · 已归档" else "")
            }
        }
        OutlinedButton(edit) { Text("编辑账户") }
        Text("账户流水 · ${entries.size} 笔", style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) Text("这个账户还没有流水。")
        entries.take(limit).forEach { EntryRow(it, state) { onEntry(it) } }
        if (entries.size > limit) TextButton({ limit += 20 }) { Text("查看更多流水") }
    }
}

@Composable
private fun Planning(state: LedgerSnapshot, modifier: Modifier, onBudget: () -> Unit) {
    val month = YearMonth.now()
    val currency = state.settings.reportingCurrency
    val limit = state.budgets.find { it.month == month && it.currency == currency }?.limit
    val used = LedgerRules.monthlyBudgetUsed(state.entries, month, currency, ZoneId.systemDefault(), Instant.now())
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("月度预算 · ${currency.name}", style = MaterialTheme.typography.titleMedium)
            Text(if (limit == null) "尚未设置" else money(limit - used, currency), style = MaterialTheme.typography.headlineLarge)
            Text(if (limit == null) "设置一个适合自己的月度上限。" else "剩余预算")
            Text("预算已用  ${money(used, currency)}")
            if (limit != null) Text("预算上限  ${money(limit, currency)}")
            Button(onClick = onBudget) { Text(if (limit == null) "设置月预算" else "调整月预算") }
        } }
        Text("分摊只调整月预算；流水和账户始终记入实际发生的全额。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EntryRow(entry: Entry, state: LedgerSnapshot, onClick: () -> Unit) {
    val account = state.accounts.find { it.id == entry.accountId }
    val category = state.categories.find { it.id == entry.categoryId }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title.ifBlank { category?.name ?: entry.kind.title }, style = MaterialTheme.typography.titleSmall)
                    if (entry.kind != EntryKind.TRANSFER) Text(categoryPath(category, state.categories), style = MaterialTheme.typography.labelSmall)
                    Text("${account?.name ?: "账户"} · ${entry.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (entry.kind == EntryKind.INCOME) "+" else "−") + money(entry.amount, entry.currency),
                    style = MaterialTheme.typography.titleSmall)
            }
            if (entry.kind == EntryKind.TRANSFER) Text("转入 ${state.accounts.find { it.id == entry.destinationId }?.name ?: "账户"} · ${money(entry.destinationAmount ?: BigDecimal.ZERO, entry.destinationCurrency ?: entry.currency)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun SummaryCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.titleLarge) } }
}
@Composable private fun EmptyCard(title: String, message: String) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(message, style = MaterialTheme.typography.bodyMedium) } }
}
internal fun money(amount: BigDecimal, currency: Currency): String {
    val format = NumberFormat.getNumberInstance().apply { minimumFractionDigits = 2; maximumFractionDigits = maxOf(2, amount.stripTrailingZeros().scale().coerceAtMost(8)) }
    return "${currency.name} ${format.format(amount)}"
}
@Composable internal fun ErrorText(message: String) { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp)) }
@Composable
internal fun <T> Choice(label: String, selected: String, options: List<T>, title: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · $selected", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(title(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}
@Composable
internal fun FormPage(title: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("form:$title")) {
            Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "关闭") }
            }, actions = { if (onSave != null) TextButton(onClick = onSave, enabled = !busy) { Text(if (busy) "保存中…" else "保存") } }) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    error?.let { ErrorText(it) }
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
