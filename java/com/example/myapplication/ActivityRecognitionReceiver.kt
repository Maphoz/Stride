package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.google.android.gms.location.ActivityRecognition

class ActivityRecognitionReceiver : BroadcastReceiver() {

    private val PREFS_NAME = "ActivityRecognitionPrefs"
    private val KEY_CURRENT_ACTIVITY = "currentActivity"
    private var unknownDetected: Boolean = false

    companion object {
        private var instance: ActivityRecognitionReceiver? = null

        fun getInstance(): ActivityRecognitionReceiver {
            if (instance == null) {
                instance = ActivityRecognitionReceiver()
            }
            return instance!!
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val detectedActivities = result?.probableActivities
            // Find the most probable activity
            val mostProbableActivity = detectedActivities?.maxByOrNull { it.confidence }

            mostProbableActivity?.let {
                if (it.type == DetectedActivity.UNKNOWN) {
                    unknownDetected = if (unknownDetected) {
                        // Second unknown in a row, handle as needed (e.g., break the streak)
                        handleDetectedActivity(context, DetectedActivity.UNKNOWN)
                        false // Reset the flag
                    } else {
                        // First unknown, give a pass
                        true
                    }
                } else {
                    unknownDetected = false // Reset the flag on a known activity

                    if (it.type == DetectedActivity.ON_FOOT) {
                        // If the most probable activity is ON_FOOT, check the confidence of WALKING and RUNNING
                        val walkingActivity = detectedActivities.find { activity -> activity.type == DetectedActivity.WALKING }
                        val runningActivity = detectedActivities.find { activity -> activity.type == DetectedActivity.RUNNING }

                        // Determine which has higher confidence, walking or running
                        val selectedActivity = when {
                            walkingActivity != null && runningActivity != null -> {
                                if (runningActivity.confidence >= walkingActivity.confidence) {
                                    DetectedActivity.RUNNING
                                } else {
                                    DetectedActivity.WALKING
                                }
                            }
                            walkingActivity != null -> DetectedActivity.WALKING
                            runningActivity != null -> DetectedActivity.RUNNING
                            else -> DetectedActivity.WALKING // Fall back to ON_FOOT if neither is available
                        }

                        // Pass the correct activity to the handler
                        handleDetectedActivity(context, selectedActivity)
                    } else {
                        // For other activities, pass the detected type directly
                        handleDetectedActivity(context, it.type)
                    }
                }
            }
        }
    }

    private fun handleDetectedActivity(context: Context, activityType: Int) {
        val newActivity = when (activityType) {
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.IN_VEHICLE -> "driving"
            DetectedActivity.STILL -> "sitting"
            else -> "unknown"
        }

        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentActivity = sharedPreferences.getString(KEY_CURRENT_ACTIVITY, null)

        if(currentActivity != newActivity){
            stopCurrentService(context)
            // Save the new activity to SharedPreferences
            sharedPreferences.edit().putString(KEY_CURRENT_ACTIVITY, newActivity).apply()

            // Start the new service
            when (newActivity) {
                "walking" -> startForegroundService(context, BackgroundStepService::class.java, newActivity)
                "running" -> startForegroundService(context, BackgroundStepService::class.java, newActivity)
                "driving" -> startForegroundService(context, BackgroundDrivingService::class.java, newActivity)
                "sitting" -> {
                    startForegroundService(context, BackgroundSittingService::class.java, newActivity)
                }
            }
        }
    }

    fun stopCurrentService(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (sharedPreferences.getString(KEY_CURRENT_ACTIVITY, null)) {
            "walking", "running" -> stopForegroundService(context, BackgroundStepService::class.java)
            "driving" -> stopForegroundService(context, BackgroundDrivingService::class.java)
            "sitting" -> stopForegroundService(context, BackgroundSittingService::class.java)
        }
        resetCurrentActivity(context)
    }

    private fun startForegroundService(context: Context, serviceClass: Class<out Service>, activityType: String) {
        val intent = Intent(context, serviceClass).apply {
            putExtra("activityType", activityType)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopForegroundService(context: Context, serviceClass: Class<out Service>) {
        val intent = Intent(context, serviceClass)
        context.stopService(intent)
    }

    fun resetCurrentActivity(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().remove(KEY_CURRENT_ACTIVITY).apply()
    }
}

