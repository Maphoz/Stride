package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class WeightMeasurement(
    val date: String,
    val averageWeight: Float
)

@Dao
interface WeightDao {
    @Insert
    fun insert(weight: WeightRecord): Long

    @Query("SELECT weight FROM weight_table ORDER BY date DESC LIMIT 1")
    fun getCurrentWeight(): Float

    // Query to get all weight measurements grouped by date
    @Query("""
        SELECT date, AVG(weight) as averageWeight 
        FROM weight_table 
        GROUP BY date 
        ORDER BY date ASC
    """)
    fun getWeightMeasurementsGroupedByDate(): List<WeightMeasurement>
}