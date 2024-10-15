package com.example.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

data class CaloriesByDate(
    val date: String,
    val totalCalories: Float
)

data class StepsByDate(
    val date: String,
    val totalSteps: Int
)

data class DistanceByDate(
    val date: String,
    val totalDistance: Float
)

data class TypeDuration(
    val type: String,
    val totalDuration: Long
)

@Dao
interface ActivityDao {

    @Insert
    fun insertActivity(activity: Activity): Long

    @Delete
    fun deleteActivity(activity: Activity): Int

    @Query("SELECT * FROM activity_table WHERE type = :type")
    fun getActivitiesByType(type: String): List<Activity>

    @Query("SELECT * FROM activity_table WHERE id = :id LIMIT 1")
    fun getActivityById(id: Int): Activity?

    @Query("SELECT COUNT(*) FROM activity_table")
    fun getActivityCount(): Int

    @Query("SELECT * FROM activity_table WHERE date = :date")
    fun getActivitiesByDate(date: String): List<Activity>

    @Query("""
        SELECT date, SUM(caloriesBurned) as totalCalories 
        FROM activity_table 
        WHERE date >= :startDate 
        GROUP BY date
    """)
    fun getCaloriesBurnedAfter(startDate: String): List<CaloriesByDate>

    @Query("""
        SELECT date, SUM(steps) as totalSteps 
        FROM activity_table 
        WHERE date >= :startDate 
        GROUP BY date
    """)
    fun getStepsMadeAfter(startDate: String): List<StepsByDate>

    @Query("""
        SELECT date, SUM(distance) as totalDistance 
        FROM activity_table 
        WHERE date >= :startDate 
        GROUP BY date
    """)
    fun getDistanceAfter(startDate: String): List<DistanceByDate>

    @Query("""
        SELECT SUM(caloriesBurned) as totalCalories
        FROM activity_table
        WHERE date >= :startDate
    """)
    fun getTotalCaloriesAfterDate(startDate: String): Float

    @Query("""
        SELECT SUM(distance) as totalDistance
        FROM activity_table
        WHERE date >= :startDate AND type IS NOT 'sitting'
    """)
    fun getTotalDistanceAfterDate(startDate: String): Float

    @Query("""
        SELECT SUM(duration) as totalDuration
        FROM activity_table
        WHERE date >= :startDate AND type IS NOT 'sitting'
    """)
    fun getTotalTimeAfterDate(startDate: String): Int

    @Query("""
        SELECT SUM(steps) as totalSteps
        FROM activity_table
        WHERE date = :date AND (type = 'walking' OR type = 'running')
    """)
    fun getTotalStepsOnDate(date: String): Int

    @Query("""
        SELECT * FROM activity_table 
        ORDER BY date DESC, id DESC 
        LIMIT 5
    """)
    fun getLatestFiveActivities(): List<Activity>

    @Query("""
        SELECT * FROM activity_table 
        ORDER BY date DESC, id DESC 
        LIMIT :offset, 5
    """)
    fun getActivitiesOffset(offset: Int): List<Activity>

    @Query("""
        SELECT type, SUM(duration) as totalDuration
        FROM activity_table
        WHERE date >= :startDate
        GROUP BY type
    """)
    fun getTotalDurationPerTypeAfterDate(startDate: String): List<TypeDuration>

    @Query("""
        SELECT * FROM activity_table 
        ORDER BY date DESC, id DESC
    """)
    fun getAllActivities(): List<Activity>


    @Query("""
        SELECT * FROM activity_table
        WHERE (:startDate IS NULL OR date >= :startDate)
        AND (:endDate IS NULL OR date <= :endDate)
        AND (:minCalories IS NULL OR caloriesBurned >= :minCalories)
        AND (:maxCalories IS NULL OR caloriesBurned <= :maxCalories)
        AND (type IN (:types))
        ORDER BY date DESC, id DESC
    """)
    fun getFilteredActivities(
        startDate: String? = null,
        endDate: String? = null,
        minCalories: Float? = null,
        maxCalories: Float? = null,
        types: List<String>? = null
    ): List<Activity>


    @Query("SELECT MAX(caloriesBurned) FROM activity_table")
    fun getMaxCaloriesBurned(): Float
}
