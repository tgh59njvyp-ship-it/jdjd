package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.network.PinterestFetcher
import com.example.data.repository.PinterestRepository
import com.example.model.PinterestPin
import com.example.model.PinFetchResult
import com.example.model.SavedZipEntity
import com.example.model.ZipProgressState
import com.example.model.ZipStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pinzip_prefs", Context.MODE_PRIVATE)

    private var currentApiToken: String = prefs.getString("pinterest_token", "")
        ?: BuildConfig.PINTEREST_ACCESS_TOKEN.ifBlank { "" }

    private val fetcher = PinterestFetcher {
        currentApiToken.ifBlank { BuildConfig.PINTEREST_ACCESS_TOKEN }
    }

    private val db = AppDatabase.getDatabase(application)
    private val repository = PinterestRepository(application, db.savedZipDao(), fetcher)

    val savedZipsHistory: StateFlow<List<SavedZipEntity>> = repository.savedZips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _urlInput = MutableStateFlow("https://www.pinterest.jp/design_ideas/poster-inspiration/")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _urlValidationError = MutableStateFlow<String?>(null)
    val urlValidationError: StateFlow<String?> = _urlValidationError.asStateFlow()

    private val _isLoadingPins = MutableStateFlow(false)
    val isLoadingPins: StateFlow<Boolean> = _isLoadingPins.asStateFlow()

    private val _pinFetchResult = MutableStateFlow<PinFetchResult?>(null)
    val pinFetchResult: StateFlow<PinFetchResult?> = _pinFetchResult.asStateFlow()

    private val _selectedPinIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPinIds: StateFlow<Set<String>> = _selectedPinIds.asStateFlow()

    private val _searchQueryFilter = MutableStateFlow("")
    val searchQueryFilter: StateFlow<String> = _searchQueryFilter.asStateFlow()

    private val _gridColumns = MutableStateFlow(2)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _previewPin = MutableStateFlow<PinterestPin?>(null)
    val previewPin: StateFlow<PinterestPin?> = _previewPin.asStateFlow()

    private val _zipProgressState = MutableStateFlow(ZipProgressState())
    val zipProgressState: StateFlow<ZipProgressState> = _zipProgressState.asStateFlow()

    private val _apiTokenInput = MutableStateFlow(currentApiToken)
    val apiTokenInput: StateFlow<String> = _apiTokenInput.asStateFlow()

    private val _pastUrls = MutableStateFlow<List<String>>(
        listOf(
            "https://www.pinterest.jp/design_ideas/poster-inspiration/",
            "https://www.pinterest.com/pinterest/travel-inspiration/"
        )
    )
    val pastUrls: StateFlow<List<String>> = _pastUrls.asStateFlow()

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun onUrlInputChanged(newUrl: String) {
        _urlInput.value = newUrl
        _urlValidationError.value = null
    }

    fun onApiTokenInputChanged(newToken: String) {
        _apiTokenInput.value = newToken
        currentApiToken = newToken
        prefs.edit().putString("pinterest_token", newToken).apply()
    }

    fun fetchBoardPins() {
        val rawUrl = _urlInput.value.trim()
        if (rawUrl.isBlank()) {
            _urlValidationError.value = "PinterestのボードURLを入力してください。"
            return
        }

        viewModelScope.launch {
            _isLoadingPins.value = true
            _urlValidationError.value = null
            _pinFetchResult.value = null

            try {
                val result = repository.fetchBoardPins(rawUrl, maxPinsLimit = 500)
                _pinFetchResult.value = result
                _selectedPinIds.value = result.pins.map { it.id }.toSet() // Select all by default

                // Save to past URLs
                val updatedPast = (_pastUrls.value + rawUrl).distinct().takeLast(5)
                _pastUrls.value = updatedPast

            } catch (e: Exception) {
                _urlValidationError.value = e.localizedMessage ?: "ピンの取得に失敗しました。URLをお確かめの上、再度お試しください。"
            } finally {
                _isLoadingPins.value = false
            }
        }
    }

    fun togglePinSelection(pinId: String) {
        val current = _selectedPinIds.value.toMutableSet()
        if (current.contains(pinId)) {
            current.remove(pinId)
        } else {
            current.add(pinId)
        }
        _selectedPinIds.value = current
    }

    fun selectAllPins() {
        val all = _pinFetchResult.value?.pins?.map { it.id }?.toSet() ?: emptySet()
        _selectedPinIds.value = all
    }

    fun deselectAllPins() {
        _selectedPinIds.value = emptySet()
    }

    fun setSearchQueryFilter(query: String) {
        _searchQueryFilter.value = query
    }

    fun setGridColumns(cols: Int) {
        _gridColumns.value = cols
    }

    fun setPreviewPin(pin: PinterestPin?) {
        _previewPin.value = pin
    }

    fun startZipDownload() {
        val result = _pinFetchResult.value ?: return
        val selectedIds = _selectedPinIds.value
        val selectedPinsList = result.pins.filter { selectedIds.contains(it.id) }

        if (selectedPinsList.isEmpty()) {
            _urlValidationError.value = "保存する画像を選択してください。"
            return
        }

        viewModelScope.launch {
            repository.downloadSelectedPinsToZip(
                boardName = result.boardInfo.boardName,
                boardUrl = _urlInput.value,
                pins = selectedPinsList,
                progressFlow = _zipProgressState
            )
        }
    }

    fun resetZipProgress() {
        _zipProgressState.value = ZipProgressState(status = ZipStatus.IDLE)
    }

    fun deleteZipHistoryItem(entity: SavedZipEntity) {
        viewModelScope.launch {
            repository.deleteSavedZip(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
