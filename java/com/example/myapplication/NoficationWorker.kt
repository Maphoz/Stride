package com.example.myapplication

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val activityManager = ActivityManager(applicationContext)
        val lastActivityTime = activityManager.getLastActivityTime()
        val currentTime = System.currentTimeMillis()
        val eightHoursInMillis = 8 * 60 * 60 * 1000

        if (currentTime - lastActivityTime >= eightHoursInMillis) {
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (currentHour !in 22..23 && currentHour !in 0..7) {
                sendNotification()
                activityManager.saveActivityTime()  // Reset the time
            }
        }

        return Result.success()
    }

    private fun sendNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "activity_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Activity Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(applicationContext, MainActivity::class.java)  // Replace with your activity
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Promemoria attività")
            .setContentText("E' tanto tempo che non registri un'attività, tieniti in forma!")
            .setSmallIcon(R.drawable.ic_running_ac)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}
