package com.youyu.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youyu.android.data.EncryptedLedgerRepository
import com.youyu.android.data.VaultKeys
import com.youyu.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedLedgerTest {
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var repository: EncryptedLedgerRepository
    private val password get() = "synthetic-only-passphrase".toCharArray()
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val account = Account(id = "synthetic-a", name = "synthetic private account", kind = AccountKind.DEBIT,
        currency = Currency.CNY, openingBalance = BigDecimal("1000.12"))
    @Before fun prepare() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        directory = File(context.cacheDir, "synthetic-vault-$id")
        alias = "youyu.synthetic.$id"
        repository = EncryptedLedgerRepository(directory, VaultKeys(directory, alias))
        val opened = repository.open(password, create = true)
        assertTrue(opened.exceptionOrNull()?.message, opened.isSuccess)
    }
    @After fun cleanup() = runBlocking {
        repository.close()
        directory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        Unit
    }
    @Test fun encryptedRoundTripAndWrongPasswordDoNotResetData() = runBlocking {
        assertTrue(repository.saveAccount(account).isSuccess)
        val entry = Entry(kind = EntryKind.EXPENSE, accountId = account.id, amount = BigDecimal("0.12"),
            currency = Currency.CNY, occurredAt = now, title = "synthetic-private-merchant")
        assertTrue(repository.saveEntry(entry).isSuccess)
        repository.close()
        val bytes = File(directory, "ledger.db").readBytes()
        assertFalse(bytes.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("synthetic-private-merchant"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("synthetic private account"))
        val wrong = repository.open("wrong-passphrase".toCharArray(), create = false)
        assertTrue(wrong.isFailure)
        assertNull(repository.snapshot.value)
        assertTrue(repository.open(password, create = false).isSuccess)
        val state = requireNotNull(repository.snapshot.value)
        assertEquals(1, state.entries.size)
        assertEquals(0, LedgerRules.balance(state.accounts.single(), state.entries, now).compareTo(BigDecimal("1000")))
    }
    @Test fun concurrentEditsAreSerializedAndEntryIdsAreIdempotent() = runBlocking {
        repository.saveAccount(account).getOrThrow()
        coroutineScope { (0..19).map { index -> async(Dispatchers.Default) {
            repository.saveEntry(Entry(id = "test-$index", kind = EntryKind.EXPENSE, accountId = account.id,
                amount = BigDecimal("0.01"), currency = Currency.CNY, occurredAt = now)).getOrThrow()
        } }.awaitAll() }
        val state = requireNotNull(repository.snapshot.value)
        assertEquals(20, state.entries.size)
        val first = state.entries.first()
        repository.saveEntry(first.copy(amount = BigDecimal("1"))).getOrThrow()
        assertEquals(20, requireNotNull(repository.snapshot.value).entries.size)
        assertEquals(0, LedgerRules.balance(account, requireNotNull(repository.snapshot.value).entries, now).compareTo(BigDecimal("998.93")))
    }
    @Test fun invalidMutationDoesNotPartiallyPersist() = runBlocking {
        repository.saveAccount(account).getOrThrow()
        val invalid = Entry(kind = EntryKind.TRANSFER, accountId = account.id, amount = BigDecimal("50"), currency = Currency.CNY,
            occurredAt = now, destinationId = "missing", destinationAmount = BigDecimal("50"), destinationCurrency = Currency.CNY)
        assertTrue(repository.saveEntry(invalid).isFailure)
        assertTrue(requireNotNull(repository.snapshot.value).entries.isEmpty())
        repository.close()
        repository.open(password, create = false).getOrThrow()
        assertTrue(requireNotNull(repository.snapshot.value).entries.isEmpty())
    }
    @Test fun lockConcealsStateAndRejectsFurtherWrites() = runBlocking {
        repository.saveAccount(account).getOrThrow()
        repository.conceal()
        assertNull(repository.snapshot.value)
        repository.close()
        assertTrue(repository.saveAccount(account.copy(name = "changed")).isFailure)
        repository.open(password, create = false).getOrThrow()
        assertEquals(account.name, requireNotNull(repository.snapshot.value).accounts.single().name)
    }
    @Test fun subtreeRemovalReclassifiesHistoryAtomicallyAndSurvivesReopen() = runBlocking {
        repository.saveAccount(account).getOrThrow()
        val family = "expense-children"
        val first = Entry(id = "family-1", kind = EntryKind.EXPENSE, accountId = account.id, amount = BigDecimal("15.20"),
            currency = account.currency, occurredAt = now, categoryId = family)
        val child = repository.snapshot.value!!.categories.first { it.parentId == family }
        repository.saveEntry(first).getOrThrow()
        val stale = CategoryCatalog.removal(family, repository.snapshot.value!!)
        repository.saveEntry(first.copy(id = "child", categoryId = child.id)).getOrThrow()
        repository.saveEntry(first.copy(id = "other", categoryId = "expense-food")).getOrThrow()
        assertTrue(repository.deleteCategory(stale).isFailure)
        val before = repository.snapshot.value!!
        assertEquals(family, before.entries.single { it.id == first.id }.categoryId)
        repository.deleteCategory(CategoryCatalog.removal(family, before)).getOrThrow()
        repository.close()
        repository.open(password, create = false).getOrThrow()
        val after = repository.snapshot.value!!
        assertFalse(after.categories.any { it.id == family || it.parentId == family })
        assertEquals(before.entries.associateBy { it.id }.mapValues { if (it.key == "other") it.value else it.value.copy(categoryId = null) },
            after.entries.associateBy { it.id })
        assertEquals(LedgerRules.balance(account, before.entries, now), LedgerRules.balance(account, after.entries, now))
    }

    @Test fun artworkRoundTripsAndLibraryRemovalPreservesUsedImages() = runBlocking {
        val art = Artwork("test-image", ArtworkKind.ICON, "测试图标")
        val image = "synthetic-private-image-bytes".toByteArray()
        repository.saveArtwork(art, image).getOrThrow()
        repository.saveAccount(account.copy(icon = art.key, cardFace = "oceanWhale")).getOrThrow()
        repository.renameArtwork(art.id, "新图标").getOrThrow()
        repository.removeArtwork(art).getOrThrow()
        repository.close()
        assertFalse(String(File(directory, "ledger.db").readBytes(), Charsets.ISO_8859_1).contains(String(image)))
        repository.open(password, create = false).getOrThrow()
        val state = repository.snapshot.value!!
        assertEquals(art.key, state.accounts.single().icon)
        assertEquals("oceanWhale", state.accounts.single().cardFace)
        assertFalse(state.artwork.single().listed)
        assertEquals("新图标", state.artwork.single().name)
        assertArrayEquals(image, repository.image(art.id))
    }

    @Test fun migratesLegacyVaultOnceWithoutChangingBillsOrRecreatingDeletedCategories() = runBlocking {
        repository.saveAccount(account).getOrThrow()
        val entry = Entry(kind = EntryKind.EXPENSE, accountId = account.id, amount = BigDecimal("1"),
            currency = account.currency, occurredAt = now, categoryId = "expense-food")
        repository.saveEntry(entry).getOrThrow()
        repository.close()
        val key = VaultKeys(directory, alias).unlock(password)
        try {
            val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(File(directory, "ledger.db"), key, null, null, null)
            try {
                db.execSQL("DROP TABLE artwork")
                db.execSQL("DELETE FROM records WHERE type='category' AND id!='expense-food'")
                db.execSQL("UPDATE records SET body=? WHERE type='category'", arrayOf("""{"id":"expense-food","kind":"EXPENSE","name":"餐饮"}"""))
                db.version = 1
            } finally { db.close() }
        } finally { key.fill(0) }
        repository.open(password, create = false).getOrThrow()
        val state = repository.snapshot.value!!
        assertEquals(entry, state.entries.single())
        assertEquals(account, state.accounts.single())
        assertEquals("食品酒水", state.categories.single { it.id == entry.categoryId }.name)
        assertFalse(state.categories.any { it.id == "expense-health" })
        val family = CategoryCatalog.removal("expense-children", state)
        repository.deleteCategory(family).getOrThrow()
        repository.close()
        repository.open(password, create = false).getOrThrow()
        assertFalse(repository.snapshot.value!!.categories.any { it.id in family.categoryIds })
    }
    @Test fun keyEnvelopeTamperingFailsClosed() = runBlocking {
        repository.close()
        val file = File(directory, "key.envelope")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertTrue(repository.open(password, create = false).isFailure)
        assertNull(repository.snapshot.value)
    }
}
