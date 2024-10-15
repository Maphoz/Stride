package com.example.myapplication

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "activity_table")
data class Activity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "caloriesBurned") val caloriesBurned: Float,
    @ColumnInfo(name = "type") val type: String, // walking, running, sitting, driving
    @ColumnInfo(name = "distance") val distance: Float? = null,
    @ColumnInfo(name = "steps") val steps: Int? = null
) : Parcelable

