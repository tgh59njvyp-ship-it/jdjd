package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.HeroHeader
import com.example.ui.components.HistoryScreen
import com.example.ui.components.PinDetailDialog
import com.example.ui.components.PinGrid
import com.example.ui.components.SettingsScreen
import com.example.ui.components.UrlInputSection
import com.example.ui.components.ZipProgressDialog
import com.example.ui.theme.PinZipTheme
import com.example.ui.theme.PinterestRed
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PinZipTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val urlValidationError by viewModel.urlValidationError.collectAsStateWithLifecycle()
    val isLoadingPins by viewModel.isLoadingPins.collectAsStateWithLifecycle()
    val pinFetchResult by viewModel.pinFetchResult.collectAsStateWithLifecycle()
    val selectedPinIds by viewModel.selectedPinIds.collectAsStateWithLifecycle()
    val searchQueryFilter by viewModel.searchQueryFilter.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val previewPin by viewModel.previewPin.collectAsStateWithLifecycle()
    val zipProgressState by viewModel.zipProgressState.collectAsStateWithLifecycle()
    val savedZipsHistory by viewModel.savedZipsHistory.collectAsStateWithLifecycle()
    val apiTokenInput by viewModel.apiTokenInput.collectAsStateWithLifecycle()
    val pastUrls by viewModel.pastUrls.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple(0, "取得・保存", Icons.Default.Download),
                    Triple(1, "ZIP履歴", Icons.Default.History),
                    Triple(2, "設定・ガイド", Icons.Default.Settings)
                )

                items.forEach { (index, title, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        icon = { Icon(imageVector = icon, contentDescription = title) },
                        label = { Text(text = title, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PinterestRed,
                            selectedTextColor = PinterestRed,
                            indicatorColor = PinterestRed.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Downloader Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        HeroHeader()

                        UrlInputSection(
                            urlInput = urlInput,
                            validationError = urlValidationError,
                            isLoading = isLoadingPins,
                            pastUrls = pastUrls,
                            onUrlChanged = viewModel::onUrlInputChanged,
                            onFetchClicked = viewModel::fetchBoardPins
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        pinFetchResult?.let { result ->
                            PinGrid(
                                result = result,
                                selectedPinIds = selectedPinIds,
                                searchFilter = searchQueryFilter,
                                gridColumns = gridColumns,
                                onToggleSelection = viewModel::togglePinSelection,
                                onSelectAll = viewModel::selectAllPins,
                                onDeselectAll = viewModel::deselectAllPins,
                                onSearchFilterChanged = viewModel::setSearchQueryFilter,
                                onGridColumnsChanged = viewModel::setGridColumns,
                                onPinClicked = viewModel::setPreviewPin,
                                onStartZipDownload = viewModel::startZipDownload
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                1 -> {
                    // History Tab
                    HistoryScreen(
                        savedZips = savedZipsHistory,
                        onDeleteZip = viewModel::deleteZipHistoryItem,
                        onClearAll = viewModel::clearAllHistory
                    )
                }

                2 -> {
                    // Settings & Guide Tab
                    SettingsScreen(
                        apiToken = apiTokenInput,
                        onApiTokenChanged = viewModel::onApiTokenInputChanged
                    )
                }
            }
        }

        // Image Preview Modal Dialog
        previewPin?.let { pin ->
            PinDetailDialog(
                pin = pin,
                onDismiss = { viewModel.setPreviewPin(null) }
            )
        }

        // Zip Creation Progress / Success / Error Modal Dialog
        ZipProgressDialog(
            state = zipProgressState,
            onDismiss = viewModel::resetZipProgress
        )
    }
}
