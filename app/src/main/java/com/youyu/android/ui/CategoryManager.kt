package com.youyu.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.youyu.domain.*

internal val symbols = mapOf(
    "wallet" to Icons.Outlined.AccountBalanceWallet, "card" to Icons.Outlined.CreditCard,
    "savings" to Icons.Outlined.Savings, "bank" to Icons.Outlined.AccountBalance,
    "chart" to Icons.AutoMirrored.Outlined.ShowChart, "loan" to Icons.Outlined.Payments,
    "food" to Icons.Outlined.Restaurant, "home" to Icons.Outlined.Home, "car" to Icons.Outlined.DirectionsCar,
    "shopping" to Icons.Outlined.ShoppingBag, "family" to Icons.Outlined.ChildCare, "ticket" to Icons.Outlined.ConfirmationNumber,
    "health" to Icons.Outlined.MedicalServices, "gift" to Icons.Outlined.CardGiftcard, "book" to Icons.AutoMirrored.Outlined.MenuBook,
    "salary" to Icons.Outlined.Work, "star" to Icons.Outlined.Star, "refund" to Icons.Outlined.Replay,
    "income" to Icons.Outlined.Paid, "dots" to Icons.Outlined.Category, "cafe" to Icons.Outlined.LocalCafe,
    "plane" to Icons.Outlined.Flight, "train" to Icons.Outlined.Train, "fuel" to Icons.Outlined.LocalGasStation,
    "rent" to Icons.Outlined.Apartment, "phone" to Icons.Outlined.PhoneAndroid, "pets" to Icons.Outlined.Pets,
    "music" to Icons.Outlined.MusicNote, "movie" to Icons.Outlined.Movie, "game" to Icons.Outlined.SportsEsports,
    "sport" to Icons.Outlined.FitnessCenter, "school" to Icons.Outlined.School, "computer" to Icons.Outlined.Computer,
    "grocery" to Icons.Outlined.ShoppingCart, "photo" to Icons.Outlined.PhotoCamera, "water" to Icons.Outlined.WaterDrop,
    "insurance" to Icons.Outlined.Shield, "hotel" to Icons.Outlined.Hotel, "park" to Icons.Outlined.Park,
    "store" to Icons.Outlined.Storefront, "heart" to Icons.Outlined.FavoriteBorder, "bus" to Icons.Outlined.DirectionsBus
)
internal val symbolLabels = "钱包 银行卡 储蓄 银行 投资 贷款 餐饮 住房 汽车 购物 子女 娱乐 医疗 礼物 书籍 工作 奖金 退款 收入 分类 咖啡 飞机 火车 加油 房租 手机 宠物 音乐 电影 游戏 运动 学校 电脑 超市 摄影 用水 保险 酒店 公园 商店 爱心 公交".split(" ").let { symbols.keys.zip(it).toMap() }
internal fun symbol(id: String?): ImageVector = symbols[id] ?: Icons.Outlined.Category
private val colors = listOf(0x3973D9, 0x28866C, 0x7B70E8, 0xED765E, 0xB98255, 0xD86B61, 0x9561D1, 0xE85978, 0x2D9FC5, 0x858B94)
private fun tint(rgb: Int) = Color(rgb or 0xff000000.toInt())

@Composable
private fun CategoryMark(item: Category, all: List<Category>) {
    val (icon, rgb) = CategoryCatalog.style(item, all)
    Box(Modifier.size(36.dp).background(tint(rgb).copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(symbol(icon), null, Modifier.size(21.dp), tint = tint(rgb))
    }
}

@Composable
internal fun Categories(state: LedgerSnapshot, model: LedgerViewModel, operation: OperationState, dismiss: () -> Unit) {
    var kind by remember { mutableStateOf(EntryKind.EXPENSE) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deletion by remember { mutableStateOf<CategoryRemoval?>(null) }
    fun edit(value: Category) { model.clearError(); editing = value }
    FormPage("分类管理", operation.busy, operation.error, dismiss, null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EntryKind.EXPENSE, EntryKind.INCOME).forEach { type ->
                FilterChip(kind == type, { kind = type; expanded = null }, { Text(type.title) })
            }
        }
        OutlinedButton({ edit(Category(name = "", kind = kind)) }, Modifier.fillMaxWidth()) { Text("新增一级分类") }
        state.categories.filter { it.kind == kind && it.parentId == null }.forEach { root ->
            val children = state.categories.filter { it.parentId == root.id }
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { expanded = if (expanded == root.id) null else root.id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryMark(root, state.categories)
                        Text(root.name, Modifier.weight(1f))
                        Icon(if (expanded == root.id) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    IconButton({ edit(Category(name = "", kind = kind, parentId = root.id)); expanded = root.id }) {
                        Icon(Icons.Outlined.Add, "为${root.name}新增子分类")
                    }
                    CategoryMenu(root, { edit(root) }, { deletion = CategoryCatalog.removal(root.id, state) })
                }
                if (expanded == root.id) children.forEach { child ->
                    Row(Modifier.fillMaxWidth().padding(start = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryMark(child, state.categories)
                        Text(child.name, Modifier.weight(1f).padding(start = 10.dp))
                        CategoryMenu(child, { edit(child) }, { deletion = CategoryCatalog.removal(child.id, state) })
                    }
                }
            }
        }
    }
    editing?.let { draft ->
        key(draft.id) {
            CategoryForm(draft, state, operation, { editing = null; model.clearError() }) { value ->
                model.perform({ editing = null }) { model.repository.saveCategory(value) }
            }
        }
    }
    deletion?.let { plan ->
        val name = state.categories.find { it.id == plan.rootId }?.name.orEmpty()
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text("删除「$name」？") },
            text = { Text("将删除此分类及其 ${plan.categoryIds.size - 1} 个子分类。关联的 ${plan.entryIds.size} 笔流水将保留，并归入未分类。") },
            confirmButton = { TextButton({ deletion = null; model.perform { model.repository.deleteCategory(plan) } }) { Text("确认删除") } },
            dismissButton = { TextButton({ deletion = null }) { Text("取消") } })
    }
}

@Composable
private fun CategoryMenu(item: Category, edit: () -> Unit, delete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Outlined.MoreVert, "管理${item.name}") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("编辑分类") }, { open = false; edit() })
            DropdownMenuItem({ Text("删除分类") }, { open = false; delete() })
        }
    }
}

@Composable
private fun CategoryForm(draft: Category, state: LedgerSnapshot, op: OperationState, close: () -> Unit, save: (Category) -> Unit) {
    var name by remember { mutableStateOf(draft.name) }
    var icon by remember { mutableStateOf(draft.icon) }
    var color by remember { mutableStateOf(draft.color) }
    var style by remember { mutableStateOf(false) }
    val parent = state.categories.find { it.id == draft.parentId }
    val value = draft.copy(name = name.trim(), icon = icon, color = color)
    FormPage(if (draft.name.isEmpty()) "新增分类" else "编辑分类", op.busy, op.error, close, { save(value) }) {
        Text(parent?.let { "${draft.kind.title} › ${it.name}" } ?: "${draft.kind.title} › 一级分类")
        Field("分类名称", name, { name = it })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryMark(value, state.categories)
            TextButton({ style = !style }) { Text(if (style) "收起样式" else "调整图标和颜色") }
        }
        if (parent != null && icon == null && color == null) Text("沿用「${parent.name}」的图标和颜色", style = MaterialTheme.typography.bodySmall)
        if (style) {
            if (parent != null) TextButton({ icon = null; color = null }) { Text("恢复跟随一级分类") }
            Text("颜色")
            colors.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { rgb ->
                    IconButton({ color = rgb }, Modifier.semantics { contentDescription = "颜色 " + rgb.toString(16) }) {
                        Box(Modifier.size(27.dp).background(tint(rgb), CircleShape), contentAlignment = Alignment.Center) {
                            if (CategoryCatalog.style(value, state.categories).second == rgb) Icon(Icons.Outlined.Check, null, tint = Color.White)
                        }
                    }
                } }
            }
            Text("图标")
            symbols.keys.chunked(5).forEach { row ->
                Row { row.forEach { id ->
                    IconToggleButton(CategoryCatalog.style(value, state.categories).first == id, { icon = id }) {
                        Icon(symbol(id), symbolLabels[id])
                    }
                } }
            }
        }
    }
}
