package com.example.heizungswerte.util

import android.content.Context
import com.example.heizungswerte.data.DayNote
import com.example.heizungswerte.data.Radiator
import com.example.heizungswerte.data.Reading
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {
    fun createCsvBackup(context: Context, readings: List<Reading>, notes: List<DayNote>, dates: List<Long>) {
        try {
            val folder = context.getExternalFilesDir(null) ?: return
            val file = File(folder, "heizungswerte_backup.csv")
            
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val radiators = Radiator.entries
            val header = "Datum;" + radiators.joinToString(";") { it.roomName } + ";Gesamt;Notiz\n"
            
            val content = StringBuilder(header)
            
            dates.sortedDescending().forEach { date ->
                val rowReadings = readings.filter { it.dateMillis == date }
                val dateStr = sdf.format(Date(date))
                
                val values = radiators.map { radiator ->
                    rowReadings.find { it.radiatorId == radiator.id }?.value?.toString() ?: ""
                }
                
                val total = rowReadings.sumOf { it.value }
                val note = notes.find { it.dateMillis == date }?.note?.replace(";", ",")?.replace("\n", " ") ?: ""
                
                content.append("$dateStr;${values.joinToString(";")};$total;$note\n")
            }

            file.writeText(content.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
