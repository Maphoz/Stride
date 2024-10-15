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
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BackgroundStepService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null
    private var initialStepCount: Int = 0
    private var stepCount: Int = 0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f
    private var startTime: Long = 0
    private lateinit var activityDao: ActivityDao
    private lateinit var activityType: String

    private val CHANNEL_ID = "BackgroundStepServiceChannel"
    private val STEP_NOTIFICATION_ID = 4

    private lateinit var weightDao: WeightDao
    private var weightKg: Float = 0f

    private val titleMap = mapOf(
        "running" to "Tracciamento corsa",
        "walking" to "Tracciamento camminata"
    )

    private val messageMap = mapOf(
        "running" to "Stai correndo? Stiamo tracciando la tua corsa in sottofondo.",
        "walking" to "Stai camminando? Stiamo tracciando la tua camminata in sottofondo."
    )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Log.e("BackgroundStepService", "Step Counter sensor not available!")
            stopSelf()
        }

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (lastLocation != null) {
                        totalDistance += lastLocation!!.distanceTo(location)
                    }
                    lastLocation = location
                }
            }
        }

        startLocationUpdates()

        // Initialize start time
        startTime = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            initializeDaos()
            initializeWeight()
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
        // Get the activity type from the intent
        activityType = intent?.getStringExtra("activityType") ?: "running"
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        saveActivity()
        stopSelf() // Stop the service after saving activity
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
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Background Step Service Channel"
            val descriptionText = "Channel for Background Step Service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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

        // Start the service in the foreground
        startForeground(STEP_NOTIFICATION_ID, notification)
    }

    private fun getIcon(activityType: String): Int {
        return if(activityType == "running") R.drawable.ic_running_ac
        else R.drawable.ic_walking_ac
    }

    private fun saveActivity() {
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        if(elapsedTime > 30){
            val distance = totalDistance / 1000  // Convert to kilometers
            val elapsedTimeHours = elapsedTime / 3600f  // Convert seconds to hours

            // Determine the MET value based on average speed (in km/h)
            val averageSpeedKmH = (totalDistance / elapsedTime) * 3.6

            val metValue = when {
                averageSpeedKmH < 4 -> 3.5f
                averageSpeedKmH < 5 -> 4.0f
                averageSpeedKmH < 6 -> 4.5f
                averageSpeedKmH < 7 -> 5.0f
                averageSpeedKmH < 8 -> 6.0f
                averageSpeedKmH < 10 -> 7.0f
                averageSpeedKmH < 12 -> 8.0f
                averageSpeedKmH < 14 -> 9.0f
                averageSpeedKmH < 16 -> 10.0f
                averageSpeedKmH < 18 -> 11.0f
                else -> 12.0f
            }

            // Calculate calories burned
            val caloriesBurned = metValue * weightKg * elapsedTimeHours
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(System.currentTimeMillis())

            val formattedDistance = String.format(Locale.US, "%.1f", distance).toFloat()
            val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()

            val activity = Activity(
                date = currentDate,
                duration = elapsedTime,
                caloriesBurned = formattedCaloriesBurned,
                type = activityType,  // Use the activity type passed from intent
                distance = formattedDistance,
                steps = stepCount
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
