package com.example.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {

    @Insert
    fun insertUser(user: User) : Long

    @Delete
    fun deleteUser(user: User) : Int

    @Query("SELECT * FROM user LIMIT 1")
    fun getUserInfo(): User

    @Query("SELECT COUNT(*) FROM user")
    fun getUserCount(): Int

    @Query("UPDATE user SET stepGoal = :stepGoal WHERE id = 1")
    fun updateStepGoal(stepGoal: Int) : Int

    @Query("UPDATE user SET height = :height WHERE id = 1")
    fun updateHeight(height: Float) : Int

    @Query("UPDATE user SET weight = :weight WHERE id = 1")
    fun updateWeight(weight: Float) : Int
}