package com.youyu.domain

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.*
import org.junit.Test

class AndroidPortTest {
    private val defaults = CategoryCatalog.defaults()
    private val state = LedgerSnapshot(categories = defaults)
    private val parent = defaults.single { it.name == "子女费用" }
    private val child = defaults.first { it.parentId == parent.id }

    @Test fun defaultCategoriesCoverBothDirectionsAndHaveStableUniqueIds() {
        assertEquals(15, defaults.count { it.parentId == null })
        assertEquals(5, defaults.count { it.parentId == null && it.kind == EntryKind.INCOME })
        assertEquals(defaults.size, defaults.map { it.id }.distinct().size)
        defaults.forEach { CategoryCatalog.validate(it, state) }
        assertEquals(defaults, CategoryCatalog.defaults())
    }
    @Test fun childrenFollowParentEditsUntilOverridden() {
        val new = Category(name = "舞蹈课", kind = parent.kind, parentId = parent.id)
        CategoryCatalog.validate(new, state)
        assertEquals(CategoryCatalog.style(parent, defaults), CategoryCatalog.style(new, defaults))
        val changed = defaults.map { if (it.id == parent.id) it.copy(icon = "music", color = 0x123456) else it }
        assertEquals("music" to 0x123456, CategoryCatalog.style(new, changed))
        assertEquals("car" to 0x123456, CategoryCatalog.style(new.copy(icon = "car"), changed))
    }
    @Test fun sameNameInAnotherBranchIsValidButSameSiblingIsRejected() {
        assertFailsWith<LedgerException> { CategoryCatalog.validate(child.copy(id = "duplicate"), state) }
        CategoryCatalog.validate(child.copy(id = "elsewhere", parentId = "expense-food"), state)
    }
    @Test fun invalidParentsAreRejected() {
        for (parentId in listOf(parent.id, "absent", child.id, "income-salary")) {
            assertFailsWith<LedgerException> { CategoryCatalog.validate(parent.copy(parentId = parentId), state) }
        }
    }
    @Test fun subtreeDeletionCountsOnlyBillsInTheBranch() {
        val bill = Entry(id = "1", kind = EntryKind.EXPENSE, accountId = "a", amount = BigDecimal.ONE,
            currency = Currency.CNY, occurredAt = Instant.EPOCH, categoryId = child.id)
        val before = state.copy(entries = listOf(bill, bill.copy(id = "2", categoryId = parent.id), bill.copy(id = "3", categoryId = "expense-food")))
        val plan = CategoryCatalog.removal(parent.id, before)
        assertEquals(5, plan.categoryIds.size)
        assertEquals(setOf("1", "2"), plan.entryIds)
        assertNotEquals(plan, CategoryCatalog.removal(parent.id, before.copy(entries = listOf(bill))))
        assertEquals(setOf(child.id), CategoryCatalog.removal(child.id, before).categoryIds)
    }
    @Test fun v1UpgradePreservesReferencesDeletedRootsAndCustomNames() {
        val old = listOf(Category("expense-food", "我的餐饮", EntryKind.EXPENSE), Category("income-salary", "工资", EntryKind.INCOME),
            Category("custom", "子女费用", EntryKind.EXPENSE))
        val after = CategoryCatalog.upgrade(old)
        assertEquals("我的餐饮", after.single { it.id == "expense-food" }.name)
        assertEquals("工资收入", after.single { it.id == "income-salary" }.name)
        assertFalse(after.any { it.id == "expense-health" })
        assertEquals(4, after.count { it.parentId == "custom" })
        assertEquals(1, after.count { it.parentId == null && it.name == "子女费用" })
        assertEquals(after, CategoryCatalog.upgrade(after))
    }
    @Test fun upgradeKeepsDistinctNamesWhenTheNewDefaultNameIsAlreadyInUse() {
        val old = listOf(Category("expense-food", "餐饮", EntryKind.EXPENSE), Category("custom", "食品酒水", EntryKind.EXPENSE))
        val after = CategoryCatalog.upgrade(old)
        assertEquals("餐饮", after.single { it.id == "expense-food" }.name)
        assertEquals("食品酒水", after.single { it.id == "custom" }.name)
        assertEquals(1, after.count { it.name == "食品酒水" })
    }
    @Test fun institutionsMatchPreviousAccountsAndLongestAliasFirst() {
        val expected = mapOf("工行储蓄卡" to "icbc", "CMB 1234" to "cmb", "Postal Savings Bank of China" to "psbc",
            "零钱通" to "wechatpay", "盈透证券" to "ibkr", "Nordea savings" to "nordea", "苏州银行工资卡" to "suzhou")
        for ((name, bank) in expected) assertEquals("bank:$bank", AccountAppearance.icon(name, AccountKind.DEBIT, null))
        assertEquals("symbol:card", AccountAppearance.icon("possiblywiseaccount", AccountKind.DEBIT, null))
        assertEquals("photo:user", AccountAppearance.icon("工行", AccountKind.DEBIT, "photo:user"))
        assertEquals(27, AccountAppearance.institutions.map { it.id }.distinct().size)
        assertEquals(13, AccountAppearance.faces.map { it.id }.distinct().size)
    }
    @Test fun cropCenterEdgesZoomAndPortraitCardStayInsideSource() {
        assertEquals(CropBounds(300, 0, 400, 400), ImageCrop.bounds(1000, 400, 1f, 1f, .5f, .5f))
        assertEquals(CropBounds(800, 200, 200, 200), ImageCrop.bounds(1000, 400, 1f, 2f, 1f, 1f))
        for (w in listOf(1, 31, 400, 1400)) for (h in listOf(1, 13, 200, 1200)) for (aspect in listOf(1f, 1.586f))
            for (scale in listOf(1f, 2.5f, 5f)) for (position in listOf(-1f, .23f, 1f, 2f)) {
                val region = ImageCrop.bounds(w, h, aspect, scale, position, 1f - position)
                assertTrue(region.width > 0 && region.height > 0 && region.left >= 0 && region.top >= 0)
                assertTrue(region.left + region.width <= w && region.top + region.height <= h)
            }
    }
}
