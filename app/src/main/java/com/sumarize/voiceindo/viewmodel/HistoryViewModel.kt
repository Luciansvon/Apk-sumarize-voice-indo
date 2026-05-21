package com.sumarize.voiceindo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.data.db.SummaryDao
import com.sumarize.voiceindo.data.db.SummaryEntity
import com.sumarize.voiceindo.data.repository.SummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: SummaryRepository) : ViewModel() {

    val summaries: StateFlow<List<SummaryEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selected = MutableStateFlow<SummaryEntity?>(null)
    val selected: StateFlow<SummaryEntity?> = _selected.asStateFlow()

    fun loadById(id: Long) {
        viewModelScope.launch {
            _selected.value = repository.getById(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    class Factory(private val dao: SummaryDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(SummaryRepository(dao)) as T
        }
    }
}
