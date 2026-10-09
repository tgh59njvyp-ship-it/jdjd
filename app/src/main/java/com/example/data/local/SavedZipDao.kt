package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.SavedZipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedZipDao {
    @Query("SELECT * FROM saved_zips ORDER BY createdAtTimestamp DESC")
    fun getAllSavedZips(): Flow<List<SavedZipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedZip(zip: SavedZipEntity): Long

    @Delete
    suspend fun deleteSavedZip(zip: SavedZipEntity)

    @Query("DELETE FROM saved_zips WHERE id = :id")
    suspend fun deleteSavedZipById(id: Long)

    @Query("DELETE FROM saved_zips")
    suspend fun clearAll()
}
