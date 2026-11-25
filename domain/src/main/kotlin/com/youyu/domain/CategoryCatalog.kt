package com.youyu.domain

data class CategoryRemoval(val rootId: String, val categoryIds: Set<String>, val entryIds: Set<String>) {
    override fun toString() = "CategoryRemoval(redacted)"
}
enum class ArtworkKind { ICON, CARD }
data class Artwork(val id: String, val kind: ArtworkKind, val name: String, val listed: Boolean = true) {
    val key: String get() = "photo:" + id
    override fun toString() = "Artwork(redacted)"
}

object CategoryCatalog {
    fun defaults(): List<Category> = buildList {
        fun group(id: String, name: String, kind: EntryKind, icon: String, color: Int, vararg children: String) {
            add(Category(id, name, kind, icon = icon, color = color))
            children.forEachIndexed { index, child -> add(Category("$id-$index", child, kind, parentId = id)) }
        }
        group("expense-shopping", "购物消费", EntryKind.EXPENSE, "shopping", 0x7B70E8, "日用清洁", "超市购物", "洗护用品", "美妆护肤", "服饰鞋包", "数码家电")
        group("expense-food", "食品酒水", EntryKind.EXPENSE, "food", 0xED765E, "早午晚餐", "零食饮料", "买菜做饭", "外出美食", "柴米油盐")
        group("expense-home", "居家生活", EntryKind.EXPENSE, "home", 0xB98255, "水电燃气", "网费", "手机话费", "物业费", "房租", "洗衣干洗", "家政维修")
        group("expense-transport", "行车交通", EntryKind.EXPENSE, "car", 0x3478F6, "公交地铁", "加油", "打车", "火车", "飞机", "停车过路")
        group("expense-children", "子女费用", EntryKind.EXPENSE, "family", 0xD86B61, "吃穿用", "学费", "培训班", "其他")
        group("expense-leisure", "休闲娱乐", EntryKind.EXPENSE, "ticket", 0x9561D1, "聚会娱乐", "影音会员", "运动", "旅行住宿", "电影", "游戏")
        group("expense-health", "医疗健康", EntryKind.EXPENSE, "health", 0xE85978, "看病买药", "体检护理")
        group("expense-gifts", "人情往来", EntryKind.EXPENSE, "gift", 0xE98558, "红包礼金", "送礼请客")
        group("expense-study", "学习工作", EntryKind.EXPENSE, "book", 0x2D9FC5, "书籍课程", "考试培训", "办公用品")
        group("expense-other", "其他支出", EntryKind.EXPENSE, "dots", 0x858B94, "软件服务", "保险手续费", "其他", "宠物")
        group("income-salary", "工资收入", EntryKind.INCOME, "salary", 0x28866C, "工资", "津贴补助", "加班费")
        group("income-bonus", "奖金收入", EntryKind.INCOME, "star", 0xAD762D, "绩效奖金", "年终奖", "奖励红包")
        group("income-investment", "投资收入", EntryKind.INCOME, "chart", 0x397DA0, "利息", "股息分红", "投资收益")
        group("income-refund", "退款报销", EntryKind.INCOME, "refund", 0x716CB1, "消费退款", "费用报销")
        group("income-other", "其他收入", EntryKind.INCOME, "income", 0x638178, "兼职副业", "礼金收入", "其他")
    }

    // v1 used these stable root IDs. Keep custom names and deleted roots when adding children.
    private val legacy = mapOf("expense-shopping" to "购物", "expense-food" to "餐饮",
        "expense-home" to "居住", "expense-transport" to "交通", "expense-health" to "健康",
        "expense-other" to "其他支出", "income-salary" to "工资", "income-other" to "其他收入")

    fun upgrade(old: List<Category>): List<Category> {
        val result = old.associateByTo(linkedMapOf()) { it.id }
        val presets = defaults()
        for (root in presets.filter { it.parentId == null }) {
            val existing = result[root.id] ?: old.firstOrNull { it.parentId == null && it.kind == root.kind && it.name == root.name }
            if (existing == null && root.id in legacy) continue
            val canRename = existing?.name == legacy[root.id] && result.values.none {
                it.id != existing?.id && it.parentId == null && it.kind == root.kind && it.name == root.name
            }
            val parent = existing?.copy(name = if (canRename) root.name else existing.name,
                icon = existing.icon ?: root.icon, color = existing.color ?: root.color) ?: root
            result[parent.id] = parent
            for (child in presets.filter { it.parentId == root.id }) {
                if (result.values.none { it.id == child.id || (it.parentId == parent.id && it.name == child.name) })
                    result[child.id] = child.copy(parentId = parent.id)
            }
        }
        return result.values.toList()
    }

    fun style(category: Category, categories: List<Category>): Pair<String, Int> {
        val parent = categories.firstOrNull { it.id == category.parentId }
        val income = category.kind == EntryKind.INCOME
        return (category.icon ?: parent?.icon ?: if (income) "income" else "dots") to
            (category.color ?: parent?.color ?: if (income) 0x28866C else 0x3973D9)
    }

    fun validate(category: Category, state: LedgerSnapshot) {
        requireRule(category.name.isNotBlank() && category.name.length <= 40, "分类名称需为 1 至 40 字")
        requireRule(category.kind != EntryKind.TRANSFER, "转账不使用收支分类")
        requireRule(state.categories.none { it.id != category.id && it.parentId == category.parentId &&
            it.kind == category.kind && it.name == category.name }, "同一级别已有同名分类")
        val old = state.categories.find { it.id == category.id }
        requireRule(old == null || old.kind == category.kind, "已有分类不能改变收支类型")
        category.parentId?.let { id ->
            val parent = state.categories.firstOrNull { it.id == id }
            requireRule(parent != null && parent.id != category.id && parent.parentId == null && parent.kind == category.kind,
                "请选择同一收支类型的一级分类")
            requireRule(state.categories.none { it.parentId == category.id }, "含子分类的分类不能移到其他分类下")
        }
    }

    fun removal(id: String, state: LedgerSnapshot): CategoryRemoval {
        requireRule(state.categories.any { it.id == id }, "分类已移除")
        val children = state.categories.groupBy { it.parentId }
        val pending = ArrayDeque(listOf(id))
        val ids = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (ids.add(current)) children[current].orEmpty().forEach { pending.addLast(it.id) }
        }
        return CategoryRemoval(id, ids, state.entries.filter { it.categoryId in ids }.mapTo(mutableSetOf()) { it.id })
    }
}
