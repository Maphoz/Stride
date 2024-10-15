package com.example.myapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContentProviderCompat.requireContext
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient

class ActivityRecognitionService : Service() {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient

    companion object {
        private const val CHANNEL_ID = "activity_recognition_channel"
        private const val NOTIFICATION_ID = 7
    }

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)

        // Request activity updates and handle permissions
        if(!requestActivityUpdates()){
            Log.e("ActivityRecognitionService",
                "errore nel lancio del background tracking.")
            stopSelf()
        }
        // Create and start foreground notification only if permissions are granted
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun requestActivityUpdates(): Boolean {
        val activityRecognitionPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (activityRecognitionPermission != PackageManager.PERMISSION_GRANTED) {
            Log.d("DebugBackground", "Permesso non garantito")
            return false
        }

        // Request activity updates if permissions are granted
        ActivityRecognitionReceiver.getInstance().resetCurrentActivity(this)
        activityRecognitionClient.requestActivityUpdates(
            2000, // Interval in milliseconds
            getPendingIntent()
        ).addOnSuccessListener {
            Log.d("DebugBackground", "Successfully requested updates")
        }.addOnFailureListener { e ->
            Log.e("DebugBackground", "Failed to request updates", e)
            stopSelf() // Stop service if the request fails
        }

        return true
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Activity Recognition Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java) // Adjust to your main activity
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracciamento attività")
            .setContentText("Stiamo tracciando le tue attività in sottofondo")
            .setSmallIcon(R.drawable.ic_fire_home) // Use your own icon
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()

        val activityRecognitionPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (activityRecognitionPermission != PackageManager.PERMISSION_GRANTED) {
            Log.d("DebugBackground", "Permesso non garantito")
        }
        activityRecognitionClient.removeActivityUpdates(getPendingIntent())
        ActivityRecognitionReceiver.getInstance().stopCurrentService(this)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
