package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

class WalkingFragment : Fragment() {

    private lateinit var textViewSteps: TextView
    private lateinit var textViewCalories: TextView
    private lateinit var textViewTime: TextView
    private lateinit var textViewDistance: TextView
    private lateinit var buttonFinish: Button
    private lateinit var weightDao: WeightDao

    private var startTime: Long = 0
    private var totalDistance: Float = 0f
    private var stepCount: Int = 0
    private var elapsedTime: Long = 0
    private var weightKg: Float = 0f

    private val handler = Handler(Looper.getMainLooper())

    private val runnable: Runnable = object : Runnable {
        override fun run() {
            elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            textViewTime.text = formatElapsedTime(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }

    private val stepUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val steps = intent?.getIntExtra("stepCount", 0) ?: 0
            val distance = intent?.getFloatExtra("totalDistance", 0f) ?: 0f
            val pathPoints = intent?.getParcelableArrayListExtra<GeoPoint>("path_points")
                ?: emptyList()

            updateUI(steps, distance)

            if (pathPoints.isNotEmpty()) {
                pathOverlay.setPoints(pathPoints)
                val lastPoint = pathPoints.last()
                mapController.setCenter(lastPoint)
                mapView.invalidate()
            }
        }
    }

    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private var lastLocation: GeoPoint? = null
    private lateinit var pathOverlay: Polyline

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_walking, container, false)
        textViewSteps = view.findViewById(R.id.textViewSteps)
        textViewCalories = view.findViewById(R.id.textViewCalories)
        textViewTime = view.findViewById(R.id.textViewTime)
        textViewDistance = view.findViewById(R.id.textViewDistanza)
        buttonFinish = view.findViewById(R.id.buttonFinish)

        mapView = view.findViewById(R.id.mapView)

        Configuration.getInstance().userAgentValue = requireContext().packageName
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller
        mapController.setZoom(18.0)

        pathOverlay = Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.primaryColor) // Set your preferred color
            outlinePaint.strokeWidth = 10f // Set your preferred width
        }
        mapView.overlayManager.add(pathOverlay)

        lifecycleScope.launch {
            initializeActivityDao()
            initializeWeight()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val serviceIntent = Intent(context, StepService::class.java).apply {
            putExtra("activityType", "walking")
        }
        ContextCompat.startForegroundService(requireContext(), serviceIntent)

        startTime = System.currentTimeMillis()
        handler.post(runnable)
        buttonFinish.setOnClickListener {
            finishWalk()
        }
    }

    private suspend fun initializeActivityDao() {
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            weightDao = db.weightDao
        }
    }

    private suspend fun initializeWeight(){
        withContext(Dispatchers.IO) {
            weightKg = weightDao.getCurrentWeight()
        }
    }

    override fun onResume() {
        super.onResume()
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
        handler.post(runnable)

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            stepUpdateReceiver, IntentFilter("StepUpdate")
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stepUpdateReceiver)
    }

    private fun updateUI(steps: Int, distance: Float) {
        stepCount = steps
        totalDistance = distance
        textViewSteps.text = steps.toString()
        textViewDistance.text = String.format(Locale.getDefault(), "%.2f km", distance / 1000)
        val calories = steps / 10
        textViewCalories.text = "$calories kcal"
    }

    private fun updateLocation(geoPoint: GeoPoint) {
        lastLocation = geoPoint
        mapController.setCenter(geoPoint)
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun finishWalk() {
        val distance = totalDistance / 1000  // Convert to kilometers
        val elapsedTimeHours = elapsedTime / 3600f  // Convert seconds to hours

        // Determine the MET value based on average speed (in km/h)
        val averageSpeedKmH = if (elapsedTime > 0) (distance / elapsedTime) * 3.6 else 0.0

        val metValue = when {
            averageSpeedKmH < 4 -> 3.5f
            averageSpeedKmH < 5 -> 4.0f
            averageSpeedKmH < 6 -> 4.5f
            averageSpeedKmH < 7 -> 5.0f
            else -> 5.5f
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
            type = "walking",
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

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(requireContext(), StepService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()
    }
}
