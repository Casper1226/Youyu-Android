package com.youyu.android.data

import com.youyu.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import java.io.File

/** One serialized connection; each user mutation validates and commits atomically. */
class EncryptedLedgerRepository(
    private val directory: File,
    private val keys: VaultKeys = VaultKeys(directory),
) : LedgerRepository {
    private val gate = Mutex()
    private var database: SQLiteDatabase? = null
    private val mutableSnapshot = MutableStateFlow<LedgerSnapshot?>(null)
    override val snapshot: StateFlow<LedgerSnapshot?> = mutableSnapshot.asStateFlow()
    val exists: Boolean get() = keys.exists

    suspend fun open(password: CharArray, create: Boolean): Result<Unit> = try { safe {
        withContext(Dispatchers.IO) {
            gate.withLock {
                requireRule(database == null, "金库已经打开")
                val key = if (create) keys.create(password) else keys.unlock(password)
                var opened: SQLiteDatabase? = null
                try {
                    ensureActive()
                    System.loadLibrary("sqlcipher")
                    Logger.setTarget(NoopTarget())
                    opened = SQLiteDatabase.openOrCreateDatabase(File(directory, "ledger.db"), key, null, null, null)
                    opened.rawQuery("PRAGMA cipher_memory_security = ON", emptyArray<String>()).use { it.moveToFirst() }
                    opened.rawQuery("PRAGMA cipher_log_level = NONE", emptyArray<String>()).use { it.moveToFirst() }
                    opened.rawQuery("PRAGMA temp_store = MEMORY", emptyArray<String>()).use { it.moveToFirst() }
                    opened.rawQuery("PRAGMA secure_delete = ON", emptyArray<String>()).use { it.moveToFirst() }
                    val version = opened.version
                    requireRule(version <= 2, "此金库需要更新版本的有余")
                    if (version < 2) {
                        opened.beginTransaction()
                        try {
                            if (version == 0) opened.execSQL("CREATE TABLE records (type TEXT NOT NULL, id TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY (type,id)) WITHOUT ROWID")
                            opened.execSQL("CREATE TABLE artwork (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, image BLOB NOT NULL, listed INTEGER NOT NULL)")
                            val catalog = if (version == 0) CategoryCatalog.defaults() else CategoryCatalog.upgrade(read(opened).categories)
                            catalog.forEach { put(opened, "category", it.id, LedgerCodec.category(it)) }
                            opened.version = 2
                            opened.setTransactionSuccessful()
                        } finally { opened.endTransaction() }
                    }
                    val initial = read(opened)
                    ensureActive()
                    database = opened
                    mutableSnapshot.value = initial
                    opened = null
                } finally { key.fill(0); opened?.close() }
            }
        }
    } } finally { password.fill('\u0000') }

    /** Hide data immediately; close waits for the current atomic write. */
    fun conceal() { mutableSnapshot.value = null }
    suspend fun close() {
        conceal()
        withContext(NonCancellable + Dispatchers.IO) { gate.withLock { database?.close(); database = null } }
    }

    override suspend fun saveAccount(account: Account): Result<Unit> = write { db, state ->
        LedgerRules.validate(account, state)
        for ((key, kind) in listOf(account.icon to ArtworkKind.ICON, account.cardFace to ArtworkKind.CARD)) {
            if (key?.startsWith("photo:") == true) requireRule(state.artwork.any { it.key == key && it.kind == kind }, "图片已移除，请重新选择")
        }
        put(db, "account", account.id, LedgerCodec.account(account))
    }
    override suspend fun reorderAccounts(ids: List<String>): Result<Unit> = write { db, state ->
        requireRule(ids.toSet() == state.accounts.map { it.id }.toSet() && ids.size == state.accounts.size, "账户列表已更新，请重试")
        ids.forEachIndexed { index, id -> state.accounts.find { it.id == id }?.let { put(db, "account", id, LedgerCodec.account(it.copy(order = index))) } }
    }
    override suspend fun saveEntry(entry: Entry): Result<Unit> = write { db, state ->
        LedgerRules.validate(entry, state)
        put(db, "entry", entry.id, LedgerCodec.entry(entry))
    }
    override suspend fun deleteEntry(id: String): Result<Unit> = write { db, state ->
        requireRule(state.entries.any { it.id == id }, "流水已不存在")
        remove(db, "entry", id)
    }
    override suspend fun saveCategory(category: Category): Result<Unit> = write { db, state ->
        CategoryCatalog.validate(category, state)
        put(db, "category", category.id, LedgerCodec.category(category))
    }
    override suspend fun deleteCategory(confirmed: CategoryRemoval): Result<Unit> = write { db, state ->
        val current = CategoryCatalog.removal(confirmed.rootId, state)
        requireRule(current == confirmed, "分类或关联流水已有变化，请重新确认删除")
        for (entry in state.entries.filter { it.id in current.entryIds })
            put(db, "entry", entry.id, LedgerCodec.entry(entry.copy(categoryId = null)))
        current.categoryIds.forEach { remove(db, "category", it) }
    }

    suspend fun saveArtwork(art: Artwork, bytes: ByteArray): Result<Unit> = write { db, _ ->
        requireRule(bytes.size in 1..2_000_000 && art.name.isNotBlank() && art.name.length <= 40, "请检查图片及名称")
        db.execSQL("INSERT INTO artwork(id,kind,name,image,listed) VALUES(?,?,?,?,1)", arrayOf(art.id, art.kind.name, art.name.trim(), bytes))
    }
    suspend fun renameArtwork(id: String, name: String): Result<Unit> = write { db, _ ->
        requireRule(name.isNotBlank() && name.length <= 40, "名称需为 1 至 40 字")
        db.execSQL("UPDATE artwork SET name=? WHERE id=? AND listed=1", arrayOf(name.trim(), id))
    }
    suspend fun removeArtwork(art: Artwork): Result<Unit> = write { db, state ->
        if (state.accounts.any { it.icon == art.key || it.cardFace == art.key })
            db.execSQL("UPDATE artwork SET listed=0 WHERE id=?", arrayOf(art.id))
        else db.execSQL("DELETE FROM artwork WHERE id=?", arrayOf(art.id))
    }
    suspend fun image(id: String): ByteArray? = withContext(Dispatchers.IO) {
        gate.withLock {
            if (mutableSnapshot.value == null) return@withLock null
            database?.rawQuery("SELECT image FROM artwork WHERE id=?", arrayOf(id))?.use { if (it.moveToFirst()) it.getBlob(0) else null }
        }
    }
    override suspend fun saveBudget(budget: Budget): Result<Unit> = write { db, _ ->
        Amounts.validate(budget.limit)
        requireRule(budget.limit.signum() >= 0, "预算不能为负数")
        put(db, "budget", budget.month.toString() + ":" + budget.currency.name, LedgerCodec.budget(budget))
    }
    override suspend fun saveSettings(settings: LedgerSettings): Result<Unit> = write { db, _ ->
        put(db, "settings", "main", LedgerCodec.settings(settings))
    }

    private suspend fun write(change: (SQLiteDatabase, LedgerSnapshot) -> Unit): Result<Unit> = safe {
        withContext(Dispatchers.IO) {
            gate.withLock {
                requireRule(mutableSnapshot.value != null, "金库已锁定")
                val db = database ?: throw LedgerException("金库已锁定")
                db.beginTransaction()
                val updated: LedgerSnapshot
                try {
                    change(db, read(db))
                    updated = read(db)
                    ensureActive()
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                if (mutableSnapshot.value != null) mutableSnapshot.value = updated
            }
        }
    }
    private fun put(db: SQLiteDatabase, type: String, id: String, body: String) {
        db.execSQL("INSERT OR REPLACE INTO records(type,id,body) VALUES(?,?,?)", arrayOf(type, id, body))
    }
    private fun remove(db: SQLiteDatabase, type: String, id: String) { db.execSQL("DELETE FROM records WHERE type=? AND id=?", arrayOf(type, id)) }
    private fun read(db: SQLiteDatabase): LedgerSnapshot {
        val accounts = mutableListOf<Account>(); val entries = mutableListOf<Entry>()
        val categories = mutableListOf<Category>(); val budgets = mutableListOf<Budget>()
        var settings = LedgerSettings()
        db.rawQuery("SELECT type,body FROM records", emptyArray<String>()).use { cursor ->
            while (cursor.moveToNext()) {
                val body = cursor.getString(1)
                when (cursor.getString(0)) {
                    "account" -> accounts += LedgerCodec.account(body)
                    "entry" -> entries += LedgerCodec.entry(body)
                    "category" -> categories += LedgerCodec.category(body)
                    "budget" -> budgets += LedgerCodec.budget(body)
                    "settings" -> settings = LedgerCodec.settings(body)
                }
            }
        }
        val artwork = buildList {
            db.rawQuery("SELECT id,kind,name,listed FROM artwork ORDER BY rowid DESC", emptyArray<String>()).use {
                while (it.moveToNext()) add(Artwork(it.getString(0), ArtworkKind.valueOf(it.getString(1)), it.getString(2), it.getInt(3) != 0))
            }
        }
        return LedgerSnapshot(accounts.sortedWith(compareBy<Account> { it.order }.thenBy { it.id }), entries.sortedByDescending { it.occurredAt }, categories, budgets, settings, artwork)
    }
    private suspend fun safe(block: suspend () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (error: LedgerException) { Result.failure(error)
    } catch (_: Exception) { Result.failure(LedgerException("金库操作失败，请重试或重新解锁")) }
}
