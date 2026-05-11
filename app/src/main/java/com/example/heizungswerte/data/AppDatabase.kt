package com.example.heizungswerte.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY dateMillis DESC, radiatorId ASC")
    fun getAllReadings(): Flow<List<Reading>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<Reading>)

    @Query("SELECT DISTINCT dateMillis FROM readings ORDER BY dateMillis DESC")
    fun getUniqueDates(): Flow<List<Long>>

    @Query("SELECT * FROM readings WHERE dateMillis = :date")
    fun getReadingsForDate(date: Long): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE dateMillis = :date")
    suspend fun getReadingsForDateOnce(date: Long): List<Reading>

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun getCount(): Int

    @Query("DELETE FROM readings WHERE dateMillis = :date")
    suspend fun deleteReadingsForDate(date: Long)

    // Note methods
    @Query("SELECT * FROM day_notes WHERE dateMillis = :date")
    suspend fun getNoteForDate(date: Long): DayNote?

    @Query("SELECT * FROM day_notes")
    fun getAllNotes(): Flow<List<DayNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: DayNote)

    @Query("DELETE FROM day_notes WHERE dateMillis = :date")
    suspend fun deleteNoteForDate(date: Long)
}

@Database(entities = [Reading::class, DayNote::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
}
