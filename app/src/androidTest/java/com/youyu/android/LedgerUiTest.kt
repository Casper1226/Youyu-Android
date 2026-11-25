package com.youyu.android

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import android.net.Uri
import android.content.res.Configuration
import android.graphics.Bitmap
import com.youyu.domain.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.youyu.android.data.EncryptedLedgerRepository
import com.youyu.android.data.VaultKeys
import com.youyu.android.ui.LedgerViewModel
import com.youyu.android.ui.YouyuApp
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.math.BigDecimal
import java.security.KeyStore
import java.util.UUID

class LedgerUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var repository: EncryptedLedgerRepository
    private lateinit var model: LedgerViewModel
    private var largeDark by mutableStateOf(false)
    private var pickerCode = -1
    private val pickerOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                pickerCode = requestCode
            }
        }
    }
    @Before fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        directory = File(context.cacheDir, "synthetic-ui-$id")
        alias = "youyu.synthetic.ui.$id"
        repository = EncryptedLedgerRepository(directory, VaultKeys(directory, alias))
        model = LedgerViewModel(repository)
        compose.setContent {
            val base = LocalDensity.current
            val config = Configuration(LocalConfiguration.current)
            if (largeDark) config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner, LocalDensity provides Density(base.density, if (largeDark) 1.6f else base.fontScale), LocalConfiguration provides config) { YouyuApp(model) }
        }
    }
    @After fun cleanup() = runBlocking {
        repository.close()
        directory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        Unit
    }
    private fun openSyntheticVault() {
        runBlocking { repository.open("synthetic-only-passphrase".toCharArray(), create = true).getOrThrow() }
        compose.waitForIdle()
    }

    private fun createVaultThroughUi() {
        compose.onNodeWithText("金库口令").performScrollTo().performTextInput("synthetic-only-passphrase")
        compose.onNodeWithText("再次输入口令").performScrollTo().performTextInput("synthetic-only-passphrase")
        compose.onNodeWithText("再次输入口令").performImeAction()
        compose.waitUntil(30000) { repository.snapshot.value != null || model.operation.value.error != null }
        assertNull(model.operation.value.error)
        compose.waitForIdle()
    }
    @Test fun userCreatesAccountExpenseEditsAndLocks() {
        createVaultThroughUi()
        compose.onNodeWithContentDescription("添加账户").performClick()
        compose.onNodeWithText("账户名称").performScrollTo().performTextInput("合成账户")
        compose.onNodeWithText("期初余额").performScrollTo().performTextReplacement("1000")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.accounts?.size == 1 }
        compose.onNodeWithContentDescription("记一笔").performClick()
        compose.onNodeWithText("金额 · CNY").performScrollTo().performTextInput("12.34")
        compose.onNodeWithText("名称").performScrollTo().performTextInput("合成午餐")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.entries?.size == 1 }
        compose.onNodeWithText("CNY 987.66").assertIsDisplayed()
        compose.onNodeWithText("合成午餐").performClick()
        compose.onNodeWithText("金额 · CNY").performScrollTo().performTextReplacement("20")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.entries?.single()?.amount?.compareTo(BigDecimal("20")) == 0 }
        compose.onNodeWithText("CNY 980.00").assertIsDisplayed()
        compose.onNodeWithContentDescription("锁定金库").performClick()
        compose.waitUntil(10000) { repository.snapshot.value == null }
        compose.onNodeWithText("解锁").assertIsDisplayed()
        compose.onNodeWithText("合成午餐").assertDoesNotExist()
    }
    @Test fun swipesThroughTabsAndOpensTheTouchedAccount() {
        openSyntheticVault()
        runBlocking {
            for (id in listOf("一", "二")) repository.saveAccount(Account(name = "银行卡$id", kind = AccountKind.DEBIT, currency = Currency.CNY)).getOrThrow()
        }
        for (title in listOf("流水", "预算", "资产")) {
            compose.onNodeWithTag("main-pages").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNode(hasText(title) and isSelected()).assertExists()
        }
        compose.onNodeWithText("银行卡二").performTouchInput { swipeRight() }
        compose.onNodeWithText("账户详情").assertIsDisplayed()
        compose.onNode(hasText("银行卡二") and hasAnyAncestor(hasTestTag("form:账户详情"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("显示归档账户").performTouchInput { swipeRight() }
        compose.onNode(hasText("预算") and isSelected()).assertExists()
    }

    @Test fun incomeChildrenInheritStyleWithoutLeavingTheList() {
        openSyntheticVault()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("分类管理").performScrollTo().performClick()
        compose.onNodeWithText("收入").performClick()
        compose.onNodeWithText("工资收入").performScrollTo().performClick()
        compose.onNodeWithText("津贴补助").assertExists()
        compose.onNodeWithContentDescription("为工资收入新增子分类").performScrollTo().performClick()
        compose.onNodeWithText("分类名称").performScrollTo().performTextInput("合成津贴")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.categories?.any { it.name == "合成津贴" } == true }
        val categories = repository.snapshot.value!!.categories
        val new = categories.single { it.name == "合成津贴" }
        assertEquals("income-salary", new.parentId)
        assertEquals(CategoryCatalog.style(categories.single { it.id == new.parentId }, categories), CategoryCatalog.style(new, categories))
        compose.onNodeWithText("合成津贴").assertExists()
    }

    @Test fun pickerReturnAfterRelockingRestoresDraftAndAppliesCroppedIcon() {
        openSyntheticVault()
        compose.onNodeWithContentDescription("添加账户").performClick()
        compose.onNodeWithText("账户名称").performScrollTo().performTextInput("合成工行")
        compose.onNodeWithText("选择图标").performScrollTo().performClick()
        compose.onNodeWithText("从相册裁剪图标").performScrollTo().performClick()
        val synthetic = File(directory, "synthetic-picker.png")
        val input = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
        try {
            for (x in 0 until 100) for (y in 0 until 40)
                input.setPixel(x, y, if (x in 30..69) android.graphics.Color.RED else android.graphics.Color.BLUE)
            synthetic.outputStream().use { input.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { input.recycle() }
        compose.runOnIdle {
            assertTrue(pickerCode != -1)
            model.lockForBackground()
            pickerOwner.activityResultRegistry.dispatchResult(pickerCode, Uri.fromFile(synthetic))
        }
        compose.onNodeWithText("金库口令").performTextInput("synthetic-only-passphrase")
        compose.onNodeWithText("金库口令").performImeAction()
        compose.waitUntil(30000) { compose.onAllNodesWithText("重置裁剪").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("保存") and hasAnyAncestor(hasTestTag("form:裁剪图标"))).performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.artwork?.size == 1 }
        compose.onNode(hasText("保存") and hasAnyAncestor(hasTestTag("form:添加账户"))).performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.accounts?.size == 1 }
        val snapshot = repository.snapshot.value!!
        val artwork = snapshot.artwork.single()
        assertEquals("合成工行", snapshot.accounts.single().name)
        assertEquals(artwork.key, snapshot.accounts.single().icon)
        val bytes = runBlocking { repository.image(artwork.id) }!!
        val result = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(256, result.width)
        assertEquals(256, result.height)
        assertEquals(android.graphics.Color.RED, result.getPixel(128, 128))
        assertEquals(android.graphics.Color.RED, result.getPixel(8, 128))
        result.recycle()
    }

    @Test fun mismatchedPassphrasesNeverCreateVault() {
        compose.onNodeWithText("金库口令").performTextInput("synthetic-only-passphrase")
        compose.onNodeWithText("再次输入口令").performTextInput("a-different-passphrase")
        compose.onNodeWithText("创建加密金库").performScrollTo().performClick()
        compose.onNodeWithText("两次口令不一致").performScrollTo().assertIsDisplayed()
        assertFalse(repository.exists)
    }
    @Test fun invalidAmountRemainsEditableWithoutSaving() {
        openSyntheticVault()
        compose.onNodeWithContentDescription("添加账户").performClick()
        compose.onNodeWithText("账户名称").performScrollTo().performTextInput("合成账户")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.accounts?.size == 1 }
        compose.onNodeWithContentDescription("记一笔").performClick()
        compose.onNodeWithText("金额 · CNY").performScrollTo().performTextInput("0")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("金额必须大于零").assertIsDisplayed()
        assertTrue(requireNotNull(repository.snapshot.value).entries.isEmpty())
    }
    @Test fun userTransfersBetweenAccountsAndAdjustsBudget() {
        openSyntheticVault()
        runBlocking {
            repository.saveAccount(Account(id = "a", name = "合成银行卡", kind = AccountKind.DEBIT, currency = Currency.CNY, openingBalance = BigDecimal("1000"))).getOrThrow()
            repository.saveAccount(Account(id = "b", name = "合成储蓄", kind = AccountKind.SAVINGS, currency = Currency.CNY, order = 1)).getOrThrow()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("记一笔").performClick()
        compose.onNodeWithText("类型 · 支出").performClick()
        compose.onNodeWithText("转账").performClick()
        compose.onNodeWithText("金额 · CNY").performScrollTo().performTextInput("100")
        compose.onNodeWithText("转入账户 · 请选择").performScrollTo().performClick()
        compose.onNodeWithText("合成储蓄 · CNY").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.entries?.size == 1 }
        compose.onNodeWithText("CNY 1,000.00").assertIsDisplayed()
        compose.onNodeWithText("预算").performClick()
        compose.onNodeWithText("设置月预算").performClick()
        compose.onNodeWithText("预算上限 · CNY").performScrollTo().performTextInput("500")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.budgets?.size == 1 }
        compose.onNodeWithText("CNY 500.00").assertIsDisplayed()
        compose.onNodeWithText("预算已用  CNY 0.00").assertIsDisplayed()
    }
    @Test fun catalogScreensShowTheSameDemoAccountsAndEntries() {
        openSyntheticVault()
        val now = java.time.Instant.now()
        val daily = Account(id = "demo-daily", name = "招商银行 · 日常", kind = AccountKind.DEBIT,
            currency = Currency.CNY, openingBalance = BigDecimal("3200"), cardFace = "oceanWhale")
        val savings = Account(id = "demo-savings", name = "工商银行 · 储蓄", kind = AccountKind.SAVINGS,
            currency = Currency.CNY, openingBalance = BigDecimal("7800"), order = 1, cardFace = "inkGold")
        val wallet = Account(id = "demo-wallet", name = "微信 · 零钱", kind = AccountKind.CASH,
            currency = Currency.CNY, openingBalance = BigDecimal("150"), order = 2, cardFace = "paperRed")
        runBlocking {
            listOf(daily, savings, wallet).forEach { repository.saveAccount(it).getOrThrow() }
            for ((index, item) in listOf("午餐" to "28.50", "地铁" to "4.00", "周末买菜" to "53.60").withIndex()) {
                repository.saveEntry(Entry(id = "demo-$index", accountId = daily.id, kind = EntryKind.EXPENSE,
                    currency = Currency.CNY, amount = BigDecimal(item.second), title = item.first,
                    categoryId = if (index == 1) "expense-transport" else "expense-food", occurredAt = now.minusSeconds(600L * (index + 1)))).getOrThrow()
            }
        }
        compose.onNodeWithText("午餐").assertExists()
        captureDemo("home")
        compose.onNodeWithText("资产").performClick()
        compose.onNodeWithText(daily.name).assertIsDisplayed()
        compose.onNodeWithText(savings.name).assertIsDisplayed()
        captureDemo("accounts")
        compose.onNodeWithText(daily.name).performClick()
        compose.onNodeWithText("账户详情").assertIsDisplayed()
        compose.onNodeWithText("账户流水 · 3 笔").assertExists()
        captureDemo("account", "账户详情")
    }

    private fun captureDemo(name: String, dialog: String? = null) {
        compose.waitForIdle()
        val destination = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "demo-gallery")
        destination.mkdirs()
        val root = if (dialog == null) compose.onRoot() else compose.onNodeWithTag("form:$dialog")
        File(destination, "$name.png").outputStream().use {
            check(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun largeTextDarkModeKeepsAccountFormUsable() {
        compose.runOnIdle { largeDark = true }
        openSyntheticVault()
        compose.onNodeWithContentDescription("添加账户").performClick()
        compose.onNodeWithText("账户名称").performScrollTo().performTextInput("合成大字账户")
        compose.onNodeWithText("期初余额").performScrollTo().performTextReplacement("123.45")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { repository.snapshot.value?.accounts?.size == 1 }
        compose.onNodeWithText("CNY 123.45").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "synthetic-android-large-dark.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

}
