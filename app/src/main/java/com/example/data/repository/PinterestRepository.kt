package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.SavedZipDao
import com.example.data.network.PinterestFetcher
import com.example.model.PinterestPin
import com.example.model.PinFetchResult
import com.example.model.SavedZipEntity
import com.example.model.ZipProgressState
import com.example.model.ZipStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PinterestRepository(
    private val context: Context,
    private val savedZipDao: SavedZipDao,
    private val fetcher: PinterestFetcher
) {
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val savedZips: Flow<List<SavedZipEntity>> = savedZipDao.getAllSavedZips()

    suspend fun fetchBoardPins(
        rawUrl: String,
        maxPinsLimit: Int = 500,
        bookmark: String? = null
    ): PinFetchResult {
        return fetcher.fetchBoardPins(rawUrl, maxPinsLimit, bookmark)
    }

    suspend fun downloadSelectedPinsToZip(
        boardName: String,
        boardUrl: String,
        pins: List<PinterestPin>,
        progressFlow: MutableStateFlow<ZipProgressState>
    ) = withContext(Dispatchers.IO) {
        if (pins.isEmpty()) {
            progressFlow.value = ZipProgressState(
                status = ZipStatus.ERROR,
                errorMessage = "ダウンロード対象の画像が選択されていません。"
            )
            return@withContext
        }

        // De-duplicate pins by originalImageUrl
        val uniquePins = pins.distinctBy { it.originalImageUrl }
        val totalCount = uniquePins.size

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cleanBoardName = boardName.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_").take(30).ifBlank { "pinterest_board" }
        val zipFileName = "${cleanBoardName}_$dateStr.zip"

        // Output file in app cache or downloads dir
        val outputDir = File(context.cacheDir, "zips").apply { if (!exists()) mkdirs() }
        val zipFile = File(outputDir, zipFileName)
        if (zipFile.exists()) zipFile.delete()

        progressFlow.value = ZipProgressState(
            status = ZipStatus.DOWNLOADING,
            totalItems = totalCount,
            completedItems = 0,
            failedItems = 0,
            zipFileName = zipFileName
        )

        var completedCount = 0
        var failedCount = 0

        val concurrencySemaphore = Semaphore(5)

        try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos.buffered()).use { zos ->
                    uniquePins.forEachIndexed { index, pin ->
                        val itemIndexStr = String.format(Locale.US, "%03d", index + 1)

                        concurrencySemaphore.withPermit {
                            val result = downloadSingleImage(pin.originalImageUrl)
                            if (result != null) {
                                val (bytes, mimeExtension) = result
                                val entryName = "${itemIndexStr}.$mimeExtension"

                                try {
                                    val zipEntry = ZipEntry(entryName)
                                    zos.putNextEntry(zipEntry)
                                    zos.write(bytes)
                                    zos.closeEntry()
                                    completedCount++
                                } catch (e: Exception) {
                                    Log.e("PinterestRepository", "Zip writing error for $entryName", e)
                                    failedCount++
                                }
                            } else {
                                failedCount++
                            }
                        }

                        progressFlow.value = progressFlow.value.copy(
                            status = ZipStatus.ZIPPING,
                            completedItems = completedCount,
                            failedItems = failedCount,
                            currentFilename = "${itemIndexStr} (${completedCount}/$totalCount)"
                        )
                    }
                }
            }

            val fileSize = zipFile.length()

            // Save to Room DB history
            val savedEntity = SavedZipEntity(
                boardName = boardName,
                boardUrl = boardUrl,
                itemCount = completedCount,
                zipFilePath = zipFile.absolutePath,
                zipFileName = zipFileName,
                fileSizeBytes = fileSize
            )
            savedZipDao.insertSavedZip(savedEntity)

            progressFlow.value = ZipProgressState(
                status = ZipStatus.SUCCESS,
                totalItems = totalCount,
                completedItems = completedCount,
                failedItems = failedCount,
                zipFilePath = zipFile.absolutePath,
                zipFileName = zipFileName,
                fileSizeBytes = fileSize
            )

        } catch (e: Exception) {
            Log.e("PinterestRepository", "ZIP generation failed", e)
            progressFlow.value = ZipProgressState(
                status = ZipStatus.ERROR,
                errorMessage = "ZIPファイルの作成中にエラーが発生しました: ${e.localizedMessage}"
            )
        }
    }

    private fun downloadSingleImage(imageUrl: String): Pair<ByteArray, String>? {
        return try {
            val request = Request.Builder().url(imageUrl).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                
                var ext = "jpg"
                val contentType = response.header("Content-Type", "")?.lowercase() ?: ""
                when {
                    contentType.contains("png") -> ext = "png"
                    contentType.contains("webp") -> ext = "webp"
                    contentType.contains("gif") -> ext = "gif"
                    imageUrl.endsWith(".png", ignoreCase = true) -> ext = "png"
                    imageUrl.endsWith(".webp", ignoreCase = true) -> ext = "webp"
                    imageUrl.endsWith(".gif", ignoreCase = true) -> ext = "gif"
                }

                Pair(bytes, ext)
            }
        } catch (e: Exception) {
            Log.e("PinterestRepository", "Failed to download image $imageUrl", e)
            null
        }
    }

    suspend fun deleteSavedZip(entity: SavedZipEntity) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(entity.zipFilePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.e("PinterestRepository", "Failed deleting zip file: ${entity.zipFilePath}", e)
            }
            savedZipDao.deleteSavedZip(entity)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            savedZipDao.clearAll()
        }
    }
}
