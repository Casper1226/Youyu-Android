package com.youyu.domain

import kotlinx.coroutines.flow.StateFlow

interface LedgerRepository {
    val snapshot: StateFlow<LedgerSnapshot?>
    suspend fun saveAccount(account: Account): Result<Unit>
    suspend fun reorderAccounts(ids: List<String>): Result<Unit>
    suspend fun saveEntry(entry: Entry): Result<Unit>
    suspend fun deleteEntry(id: String): Result<Unit>
    suspend fun saveCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(confirmed: CategoryRemoval): Result<Unit>
    suspend fun saveBudget(budget: Budget): Result<Unit>
    suspend fun saveSettings(settings: LedgerSettings): Result<Unit>
}
