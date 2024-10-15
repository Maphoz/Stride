package com.example.myapplication

object  ActivityTrackingController {
    var isActiveTracking: Boolean = false
    var isBackgroundTracking: Boolean = false

    fun startActiveTracking() {
        isActiveTracking = true
    }

    fun stopActiveTracking() {
        isActiveTracking = false
    }

    fun startBackgroundTracking() {
        isBackgroundTracking = true
    }

    fun stopBackgroundTracking() {
        isBackgroundTracking = false
    }
}
