package com.youyu.android.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youyu.android.data.EncryptedLedgerRepository
import com.youyu.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OperationState(val busy: Boolean = false, val error: String? = null)
class LedgerViewModel(val repository: EncryptedLedgerRepository) : ViewModel() {
    internal var photo by mutableStateOf<PhotoRequest?>(null)
    val snapshot = repository.snapshot
    private val mutableOperation = MutableStateFlow(OperationState())
    val operation = mutableOperation.asStateFlow()
    private var job: Job? = null
    private var closeJob: Job? = null
    val hasVault: Boolean get() = repository.exists

    fun unlock(password: CharArray, create: Boolean) {
        if (mutableOperation.value.busy) { password.fill('\u0000'); return }
        mutableOperation.value = OperationState(busy = true)
        job = viewModelScope.launch {
            try {
                closeJob?.join()
                val result = repository.open(password, create)
                mutableOperation.value = OperationState(error = result.exceptionOrNull()?.message)
            } finally { password.fill('\u0000') }
        }
    }
    fun lock() {
        photo = null
        lockForBackground()
    }
    fun lockForBackground() {
        job?.cancel()
        repository.conceal()
        mutableOperation.value = OperationState()
        val previousClose = closeJob
        closeJob = viewModelScope.launch { previousClose?.join(); repository.close() }
    }
    fun clearError() { mutableOperation.value = mutableOperation.value.copy(error = null) }
    fun perform(onSuccess: () -> Unit = {}, action: suspend () -> Result<Unit>) {
        if (mutableOperation.value.busy) return
        mutableOperation.value = OperationState(busy = true)
        job = viewModelScope.launch {
            val result = try { action() } catch (error: LedgerException) { Result.failure(error) }
            mutableOperation.value = OperationState(error = result.exceptionOrNull()?.message)
            if (result.isSuccess) onSuccess()
        }
    }
    override fun onCleared() {
        repository.conceal()
        // onStop closes normally. A separate scope is only for final connection disposal.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.close() }
    }
}
