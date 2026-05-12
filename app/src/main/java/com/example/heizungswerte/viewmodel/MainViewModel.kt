package com.example.heizungswerte.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.heizungswerte.data.DayNote
import com.example.heizungswerte.data.Radiator
import com.example.heizungswerte.data.Reading
import com.example.heizungswerte.data.ReadingRepository
import com.example.heizungswerte.util.BackupManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(
    private val repository: ReadingRepository,
    private val context: Context
) : ViewModel() {

    val uniqueDates: StateFlow<List<Long>> = repository.uniqueDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReadings: StateFlow<List<Reading>> = repository.allReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<DayNote>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val radiatorValues = mutableStateMapOf<String, String>()
    var dayNote = mutableStateOf("")

    data class ConsumptionStats(
        val totalYearToDate: Int,
        val lastDifference: Int
    )

    private fun triggerBackup() {
        viewModelScope.launch {
            BackupManager.createCsvBackup(
                context,
                allReadings.value,
                allNotes.value,
                uniqueDates.value
            )
        }
    }

    fun getStats(readings: List<Reading>, dates: List<Long>): ConsumptionStats {
        if (dates.isEmpty() || readings.isEmpty()) return ConsumptionStats(0, 0)

        // 1. Latest Reading Sum (Total Year to Date, since meters show period total)
        val latestDate = dates[0]
        val latestReadingsSum = readings.filter { it.dateMillis == latestDate }.sumOf { it.value }
        val totalYtd = latestReadingsSum // Formula: Latest Sum - 0

        // 2. Consumption since last reading
        var lastDiff = 0
        if (dates.size > 1) {
            val previousDate = dates[1]
            val previousReadingsSum = readings.filter { it.dateMillis == previousDate }.sumOf { it.value }
            lastDiff = latestReadingsSum - previousReadingsSum // Formula: Latest - Previous
        }

        return ConsumptionStats(totalYtd, lastDiff)
    }

    fun clearInputs() {
        radiatorValues.clear()
        dayNote.value = ""
    }

    fun loadReadingsForDate(dateMillis: Long) {
        viewModelScope.launch {
            val normalizedDate = normalizeDate(dateMillis)
            val readings = repository.getReadingsForDateOnce(normalizedDate)
            val note = repository.getNoteForDate(normalizedDate)
            
            // Clear current inputs
            radiatorValues.keys.forEach { radiatorValues[it] = "" }
            dayNote.value = note?.note ?: ""
            
            // Fill with database values
            readings.forEach {
                radiatorValues[it.radiatorId] = it.value.toString()
            }
        }
    }

    fun saveReadings(dateMillis: Long, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val normalizedDate = normalizeDate(dateMillis)
            val readings = radiatorValues.mapNotNull { (id, value) ->
                value.toIntOrNull()?.let {
                    Reading(dateMillis = normalizedDate, radiatorId = id, value = it)
                }
            }
            if (readings.isNotEmpty()) {
                repository.saveReadings(readings)
                repository.saveNote(DayNote(normalizedDate, dayNote.value))
                // Trigger backup
                triggerBackup()
                // Clear inputs after save
                clearInputs()
                onSaved()
            }
        }
    }

    fun deleteReadings(dateMillis: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteReadingsForDate(normalizeDate(dateMillis))
            // Trigger backup
            triggerBackup()
            onDeleted()
        }
    }

    fun normalizeDate(millis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
