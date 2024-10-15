package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.TrackingActivity
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StepService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null
    private var initialStepCount: Int = 0
    private var stepCount: Int = 0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f
    private var activityType: String = "walking"

    private val CHANNEL_ID = "StepServiceChannel"
    private val STEP_NOTIFICATION_ID = 1

    // Store the polyline points
    private val pathPoints = mutableListOf<GeoPoint>()

    private val titleMap = mapOf(
        "running" to "Tracciamento corsa",
        "walking" to "Tracciamento camminata"
    )

    private val messageMap = mapOf(
        "running" to "Stiamo tracciando la tua corsa.",
        "walking" to "Stiamo tracciando la tua camminata."
    )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForegroundService()

        GlobalScope.launch(Dispatchers.IO) {
            initializeStepCounter()
            initializeLocationUpdates()
        }

        // Load previous step count and distance values if available
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        initialStepCount = prefs.getInt("initialStepCount", 0)
        stepCount = prefs.getInt("stepCount", 0)
        totalDistance = prefs.getFloat("totalDistance", 0f)
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, TrackingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleMap[activityType])
            .setContentText(messageMap[activityType])
            .setSmallIcon(getIcon(activityType))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(STEP_NOTIFICATION_ID, notification)
    }

    private fun getIcon(activityType: String): Int {
        return if(activityType == "running") R.drawable.ic_running_ac
        else R.drawable.ic_walking_ac
    }

    private suspend fun initializeStepCounter() {
        withContext(Dispatchers.IO) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (stepCounter != null) {
                sensorManager.registerListener(this@StepService, stepCounter, SensorManager.SENSOR_DELAY_GAME)
            } else {
                stopSelf()
            }
        }
    }

    // Coroutine function to initialize the location updates
    @SuppressLint("MissingPermission")
    private suspend fun initializeLocationUpdates() {
        withContext(Dispatchers.IO) {
            // Initialize the location client
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@StepService)
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        lastLocation?.let { lastLoc ->
                            totalDistance += lastLoc.distanceTo(location)
                        }
                        lastLocation = location

                        // Add the new location to the path points
                        pathPoints.add(GeoPoint(location.latitude, location.longitude))

                        // Broadcast the updated step count, distance, and polyline
                        broadcastStepUpdate()
                    }
                }
            }

            startLocationUpdates()
        }
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activityType = intent?.getStringExtra("activityType") ?: "running"
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        resetData()  // Reset data when the service is destroyed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val currentStepCount = it.values[0].toInt()
                if (initialStepCount == 0) {
                    initialStepCount = currentStepCount
                }
                stepCount = currentStepCount - initialStepCount

                broadcastStepUpdate()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        val name = "Step Service Channel"
        val descriptionText = "Channel for Step Service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun broadcastStepUpdate() {
        val stepIntent = Intent("StepUpdate")
        stepIntent.putExtra("stepCount", stepCount)
        stepIntent.putExtra("totalDistance", totalDistance)

        // Include the path points
        stepIntent.putParcelableArrayListExtra("path_points", ArrayList(pathPoints))

        LocalBroadcastManager.getInstance(this).sendBroadcast(stepIntent)
    }

    private fun saveState() {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putInt("initialStepCount", initialStepCount)
            putInt("stepCount", stepCount)
            putFloat("totalDistance", totalDistance)
            apply()
        }
    }

    private fun resetData() {
        // Reset internal state
        initialStepCount = 0
        stepCount = 0
        totalDistance = 0f
        pathPoints.clear()

        // Clear SharedPreferences
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            clear()
            apply()
        }
    }
}
