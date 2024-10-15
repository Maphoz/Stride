package com.example.myapplication


import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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

class BackgroundDrivingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f
    private var startTime: Long = 0
    private lateinit var activityDao: ActivityDao

    private val CHANNEL_ID = "BackgroundDrivingServiceChannel"
    private val DRIVING_NOTIFICATION_ID = 6

    private lateinit var weightDao: WeightDao

    private var weightKg: Float = 0f

    override fun onCreate() {
        super.onCreate()
        // Initialize ActivityDao

        createNotificationChannel()
        startForegroundService()

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

        CoroutineScope(Dispatchers.IO).launch {
            initializeDaos()
            initializeWeight()
        }

        // Initialize start time
        startTime = System.currentTimeMillis()
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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        saveActivity()
        stopSelf() // Stop the service after saving activity
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Background Driving Service Channel"
            val descriptionText = "Channel for Background Driving Service"
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
            .setContentTitle("Tracciamento guida")
            .setContentText("Sei in macchina? Stiamo tracciando la tua guida in sottofondo.")
            .setSmallIcon(R.drawable.ic_driving_ac) // Ensure you have this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        // Start the service in the foreground
        startForeground(DRIVING_NOTIFICATION_ID, notification)
    }

    private fun saveActivity() {
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        if(elapsedTime > 30){
            val distance = totalDistance / 1000  // Convert to kilometers
            val caloriesBurned = 1.5 * (elapsedTime / 3600f) * weightKg
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(System.currentTimeMillis())

            // Format the numbers using Locale.US to ensure a period as the decimal separator
            val formattedDistance = String.format(Locale.US, "%.1f", distance).toFloat()
            val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()

            val activity = Activity(
                date = currentDate,
                duration = elapsedTime,
                caloriesBurned = formattedCaloriesBurned,
                type = "driving",
                distance = formattedDistance,
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
