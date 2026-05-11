package com.example.heizungswerte.data

import kotlinx.coroutines.flow.Flow

class ReadingRepository(private val readingDao: ReadingDao) {
    val allReadings: Flow<List<Reading>> = readingDao.getAllReadings()
    val uniqueDates: Flow<List<Long>> = readingDao.getUniqueDates()
    val allNotes: Flow<List<DayNote>> = readingDao.getAllNotes()

    suspend fun saveReadings(readings: List<Reading>) {
        readingDao.insertReadings(readings)
    }

    suspend fun saveNote(note: DayNote) {
        readingDao.insertNote(note)
    }

    fun getReadingsForDate(date: Long): Flow<List<Reading>> {
        return readingDao.getReadingsForDate(date)
    }

    suspend fun getReadingsForDateOnce(date: Long): List<Reading> {
        return readingDao.getReadingsForDateOnce(date)
    }

    suspend fun getNoteForDate(date: Long): DayNote? {
        return readingDao.getNoteForDate(date)
    }

    suspend fun deleteReadingsForDate(date: Long) {
        readingDao.deleteReadingsForDate(date)
        readingDao.deleteNoteForDate(date)
    }
}
