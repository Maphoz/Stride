package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils.formatElapsedTime
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

class RunningFragment : Fragment() {

    private lateinit var textViewSteps: TextView
    private lateinit var textViewCalories: TextView
    private lateinit var textViewTime: TextView
    private lateinit var textViewDistance: TextView
    private lateinit var buttonFinish: Button
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var pathOverlay: Polyline
    private lateinit var weightDao: WeightDao

    private var startTime: Long = 0
    private var totalDistance: Float = 0f
    private var stepCount: Int = 0
    private var elapsedTime: Long = 0
    private var lastLocation: GeoPoint? = null
    private var weightKg: Float = 0f

    private val handler = Handler(Looper.getMainLooper())

    private val timeRunnable = object : Runnable {
        override fun run() {
            elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            textViewTime.text = formatElapsedTime(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }

    private val stepUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val distance = intent?.getFloatExtra("totalDistance", 0f) ?: 0f
            val pathPoints = intent?.getParcelableArrayListExtra<GeoPoint>("path_points") ?: emptyList()

            updateUI(distance)

            if (pathPoints.isNotEmpty()) {
                pathOverlay.setPoints(pathPoints)
                val lastPoint = pathPoints.last()
                mapController.setCenter(lastPoint)
                mapView.invalidate()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_running, container, false)
        textViewSteps = view.findViewById(R.id.textViewSteps)
        textViewCalories = view.findViewById(R.id.textViewCalories)
        textViewTime = view.findViewById(R.id.textViewTime)
        textViewDistance = view.findViewById(R.id.textViewDistanza)
        buttonFinish = view.findViewById(R.id.buttonFinish)
        mapView = view.findViewById(R.id.mapView)

        // Initialize map
        Configuration.getInstance().userAgentValue = requireContext().packageName
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller
        mapController.setZoom(18.0)

        // Initialize path overlay
        pathOverlay = Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.primaryColor) // Set your preferred color
            outlinePaint.strokeWidth = 10f // Set your preferred width
        }
        mapView.overlayManager.add(pathOverlay)

        lifecycleScope.launch {
            initializeDao()
        }

        return view
    }

    private suspend fun initializeDao(){
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            weightDao = db.weightDao
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            initializeWeight()
        }
        val serviceIntent = Intent(context, StepService::class.java).apply {
            putExtra("activityType", "running")
        }
        ContextCompat.startForegroundService(requireContext(), serviceIntent)

        startTime = System.currentTimeMillis()
        handler.post(timeRunnable)

        buttonFinish.setOnClickListener {
            finishRun()
        }
    }

    private suspend fun initializeWeight(){
        withContext(Dispatchers.IO) {
            weightKg = weightDao.getCurrentWeight()
        }
    }

    private fun updateUI(distance: Float) {
        val elapsedTimeSeconds = elapsedTime // Time passed in seconds
        val speedKmH = if (elapsedTimeSeconds > 0) (distance / elapsedTimeSeconds) * 3.6 else 0.0

        // Calculate MET value based on speed
        val met = when {
            speedKmH < 6.0 -> 6.0  // MET for running at 6 km/h
            speedKmH < 7.0 -> 7.0  // MET for running at 7 km/h
            speedKmH < 8.0 -> 8.0  // MET for running at 8 km/h
            speedKmH < 9.0 -> 9.0  // MET for running at 9 km/h
            speedKmH < 10.0 -> 10.0 // MET for running at 10 km/h
            speedKmH < 11.0 -> 11.0 // MET for running at 11 km/h
            else -> 12.0          // MET for running at 12 km/h or faster
        }

        // Convert distance to kilometers and elapsed time to hours
        val distanceKm = distance / 1000
        val elapsedTimeHours = elapsedTimeSeconds / 3600.0

        // Calculate calories burned
        val caloriesBurned = met * weightKg * elapsedTimeHours

        // Update the UI
        totalDistance = distance
        textViewSteps.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmH)
        textViewDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceKm)
        textViewCalories.text = String.format(Locale.getDefault(), "%.1f kcal", caloriesBurned)
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun finishRun() {
        val distance = totalDistance / 1000  // Convert to kilometers
        val caloriesBurned = stepCount / 8.0f

        val duration = elapsedTime

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(System.currentTimeMillis())

        val formattedDistance = String.format(Locale.US, "%.1f", distance).toFloat()
        val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()

        val activity = Activity(
            date = currentDate,
            duration = duration,
            caloriesBurned = formattedCaloriesBurned,
            type = "running",
            distance = formattedDistance,
            steps = stepCount
        )

        val bundle = Bundle().apply {
            putParcelable("activity", activity)
        }

        val intent = Intent(requireContext(), StepService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()

        val saveFragment = SaveActivityFragment()
        saveFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.trackingFragmentContainer, saveFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
        handler.post(timeRunnable)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            stepUpdateReceiver, IntentFilter("StepUpdate")
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(timeRunnable)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stepUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(requireContext(), StepService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()
    }
}
