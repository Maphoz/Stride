package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BackgroundSittingService : Service() {

    private val CHANNEL_ID = "SittingServiceChannel"
    private val SITTING_NOTIFICATION_ID = 5
    private var startTime: Long = 0
    private lateinit var activityDao: ActivityDao
    private lateinit var weightDao: WeightDao
    private var weightKg: Float = 0f


    override fun onCreate() {
        super.onCreate()
        // Initialize ActivityDao
        createNotificationChannel()
        val notification = createNotification().build()
        startForeground(SITTING_NOTIFICATION_ID, notification) // Start the service in the foreground immediately
        startTime = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            initializeDaos()
            initializeWeight()
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, SittingFragment::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracciamento seduta")
            .setContentText("Sei fermo? Stiamo tracciando la tua seduta in sottofondo.")
            .setSmallIcon(R.drawable.ic_sitting_ac)  // Ensure you have this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveActivity()
        stopSelf() // Stop the service after saving activity

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sitting Service Channel"
            val descriptionText = "Channel for Sitting Service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun saveActivity() {
        val duration = (System.currentTimeMillis() - startTime) / 1000
        if(duration > 30){
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(System.currentTimeMillis())

            // Example values
            val caloriesBurned = (1.3 * duration * weightKg) / 3600f
            val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()
            val activity = Activity(
                date = currentDate,
                duration = duration,
                caloriesBurned = formattedCaloriesBurned,
                type = "sitting",
                distance = 0f,
                steps = 0
            )

            // Save the activity to your database
            saveActivityToDatabase(activity)
            val activityManager = ActivityManager(this)
            activityManager.saveActivityTime()
        }
    }

    private fun saveActivityToDatabase(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            activityDao.insertActivity(activity)
        }
    }

    private fun initializeDaos() {
        val db = DatabaseSingleton.getDatabase(applicationContext)
        activityDao = db.activityDao
        weightDao = db.weightDao
    }

    private fun initializeWeight(){
        weightKg = weightDao.getCurrentWeight()
    }
}
