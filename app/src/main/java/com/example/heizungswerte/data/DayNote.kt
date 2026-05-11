package com.example.heizungswerte.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_notes")
data class DayNote(
    @PrimaryKey val dateMillis: Long,
    val note: String
)
