package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SittingService : Service() {

    private val CHANNEL_ID = "SittingServiceChannel"
    private val handler = Handler()
    private var startTime: Long = 0

    private val SITTING_NOTIFICATION_ID = 2

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification().build()

        startForeground(SITTING_NOTIFICATION_ID, notification) // Start the service in the foreground immediately
        startTime = System.currentTimeMillis()
        handler.postDelayed(notificationRunnable, 1000)
    }

    private val notificationRunnable = object : Runnable {
        override fun run() {
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            handleNotifications(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, SittingFragment::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracciamento seduta")
            .setContentText("Stiamo tracciando la tua seduta.")
            .setSmallIcon(R.drawable.ic_sitting_ac)  // Ensure you have this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
    }

    private fun handleNotifications(elapsedTime: Long) {
        when (elapsedTime) {
            1800L -> sendNotification("Forse è tempo di alzarsi e fare stretching.")
            3600L -> sendNotification("Alziamoci a fare qualcosa!")
            5400L -> sendNotification("Non ti sarai mica perso al telefono?")
        }
    }

    private fun sendNotification(message: String) {
        val notificationIntent = Intent(this, SittingFragment::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracciamento seduta")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_sitting_ac)  // Placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(notificationRunnable)
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
}
