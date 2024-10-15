package com.example.myapplication

import android.app.Application
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var numActivities = 0
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val startBackgroundTrackingRunnable = Runnable {
        startActivityRecognitionService()
    }

    companion object {
        private const val CHANNEL_ID_PERMISSION = "warning_activity_channel"
        private const val NOTIFICATION_ID_PERMISSION = 10
    }

    override fun onCreate() {
        super.onCreate()

        val workRequest = PeriodicWorkRequest.Builder(NotificationWorker::class.java, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        sharedPreferences = getSharedPreferences("ActivityTrackingPrefs", Context.MODE_PRIVATE)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        numActivities++
        handler.removeCallbacks(startBackgroundTrackingRunnable)
        if (numActivities == 1 && ActivityTrackingController.isBackgroundTracking) {
            // App has come to foreground
            ActivityTrackingController.stopBackgroundTracking()
            stopActivityRecognitionService()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        numActivities--
        if (numActivities == 0) {
            val isTrackingEnabled = sharedPreferences.getBoolean(
                "isTrackingEnabled",
                false
            )
            if (isTrackingEnabled && !ActivityTrackingController.isActiveTracking) {
                handler.postDelayed(startBackgroundTrackingRunnable, 100)
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    private fun startActivityRecognitionService() {
        if (arePermissionsGranted()) {
            // Permissions are granted, start the service
            if(!ActivityTrackingController.isBackgroundTracking){
                val intent = Intent(this, ActivityRecognitionService::class.java)
                ActivityTrackingController.startBackgroundTracking()
                ContextCompat.startForegroundService(this, intent)
            }
        } else {
            // Permissions are not granted, send notification
            sendPermissionNotification()
        }
    }

    private fun arePermissionsGranted(): Boolean {
        val activityRecognitionPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        val backgroundLocationPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        return activityRecognitionPermission == PackageManager.PERMISSION_GRANTED &&
                backgroundLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun sendPermissionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set up notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PERMISSION,
                "Permessi Necessari",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open the app's settings for permission grant
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${packageName}")
        }

        // Create a PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_PERMISSION)
            .setContentTitle("Permessi non concessi")
            .setContentText("I seguenti permessi sono necessari: Attività fisica, Posizione: consenti sempre")
            .setSmallIcon(R.drawable.ic_warning) // Use your own icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle() // Expandable style for larger text
                    .bigText("I seguenti permessi sono necessari perché il tracciamento funzioni correttamente: Attività fisica, Posizione: consenti sempre.")
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_PERMISSION, notification)
    }

    private fun stopActivityRecognitionService() {
        val intent = Intent(this, ActivityRecognitionService::class.java)
        this.stopService(intent)
    }
}
