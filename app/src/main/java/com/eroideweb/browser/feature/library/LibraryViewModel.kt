package com.eroideweb.browser.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.data.DownloadEntry
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.history.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab {
    HISTORY,
    BOOKMARKS,
    DOWNLOADS,
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.HISTORY,
    val query: String = "",
    val history: List<HistoryEntry> = emptyList(),
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val downloads: List<DownloadEntry> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Libreria locale: cronologia e segnalibri. Nessuna rete, solo il database
 * dell'app. In modalità privata il ViewModel non viene nemmeno creato.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")

    /** La libreria è a schermo: solo allora ha senso aggiornare i download. */
    @Volatile
    private var visible = false

    init {
        viewModelScope.launch {
            query.flatMapLatest { q -> historyRepository.observe(q) }
                .collect { entries ->
                    _state.update { it.copy(history = entries, isLoading = false) }
                }
        }
        viewModelScope.launch {
            query.flatMapLatest { q -> bookmarkRepository.observe(q) }
                .collect { entries ->
                    _state.update { it.copy(bookmarks = entries) }
                }
        }
        viewModelScope.launch {
            downloadRepository.observe().collect { entries ->
                _state.update { it.copy(downloads = entries) }
            }
        }
        // Finché la scheda Download è visibile, lo stato avanza da solo.
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                if (visible && _state.value.tab == LibraryTab.DOWNLOADS) {
                    downloadRepository.refresh()
                }
            }
        }
    }

    /** Chiamato dalla UI quando la libreria si apre o si chiude. */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible && _state.value.tab == LibraryTab.DOWNLOADS) {
            viewModelScope.launch { downloadRepository.refresh() }
        }
    }

    fun setTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == LibraryTab.DOWNLOADS) {
            viewModelScope.launch { downloadRepository.refresh() }
        }
    }

    fun setQuery(text: String) {
        query.value = text
        _state.update { it.copy(query = text) }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { historyRepository.delete(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.deleteAll() }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    fun clearBookmarks() {
        viewModelScope.launch { bookmarkRepository.deleteAll() }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { downloadRepository.delete(id) }
    }

    fun clearDownloads() {
        viewModelScope.launch { downloadRepository.deleteAll() }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2000L
    }
}
