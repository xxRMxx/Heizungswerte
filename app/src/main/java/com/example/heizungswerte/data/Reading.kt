package com.example.heizungswerte.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "readings",
    primaryKeys = ["dateMillis", "radiatorId"]
)
@Serializable
data class Reading(
    val dateMillis: Long,
    val radiatorId: String,
    val value: Int
)
