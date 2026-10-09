package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class PinterestPin(
    val id: String,
    val title: String,
    val description: String,
    val originalImageUrl: String,
    val thumbnailUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val pinUrl: String = ""
)

data class PinterestBoardInfo(
    val username: String,
    val boardName: String,
    val title: String = "",
    val description: String = "",
    val pinCount: Int = 0,
    val coverImageUrl: String = ""
)

data class PinFetchResult(
    val boardInfo: PinterestBoardInfo,
    val pins: List<PinterestPin>,
    val skippedCount: Int = 0,
    val hasMore: Boolean = false,
    val nextBookmark: String? = null,
    val totalAvailableInBoard: Int = 0
)

enum class ZipStatus {
    IDLE,
    PREPARING,
    DOWNLOADING,
    ZIPPING,
    SUCCESS,
    ERROR
}

data class ZipProgressState(
    val status: ZipStatus = ZipStatus.IDLE,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val failedItems: Int = 0,
    val currentFilename: String = "",
    val zipFilePath: String? = null,
    val zipFileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

@Entity(tableName = "saved_zips")
data class SavedZipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardName: String,
    val boardUrl: String,
    val itemCount: Int,
    val zipFilePath: String,
    val zipFileName: String,
    val fileSizeBytes: Long,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
