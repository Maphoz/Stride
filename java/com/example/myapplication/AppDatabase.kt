package com.example.myapplication

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, Activity::class, WeightRecord::class],
    version = 3,
)
abstract class AppDatabase: RoomDatabase() {
    abstract val userDao: UserDao
    abstract val activityDao: ActivityDao
    abstract val weightDao: WeightDao
}