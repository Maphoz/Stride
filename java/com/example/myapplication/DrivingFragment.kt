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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
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

class DrivingFragment : Fragment() {

    private lateinit var textViewDistance: TextView
    private lateinit var textViewSpeed: TextView
    private lateinit var textViewTime: TextView
    private lateinit var buttonFinish: Button
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var weightDao: WeightDao

    private var weightKg: Float = 0f
    private var startTime: Long = 0
    private var totalDistance: Float = 0f
    private var lastLocation: GeoPoint? = null
    private var hasCenteredMap: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    private val pathOverlay = Polyline() // Polyline to track the path

    private val runnable = object : Runnable {
        override fun run() {
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            textViewTime.text = formatElapsedTime(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pathPoints = intent?.getParcelableArrayListExtra<GeoPoint>("path_points") ?: emptyList()
            val newDistance = intent?.getFloatExtra("total_distance", 0f) ?: 0f

            if (pathPoints.isNotEmpty()) {
                pathOverlay.setPoints(pathPoints)
                val lastPoint = pathPoints.last()
                mapController.setCenter(lastPoint)
                mapView.invalidate()

                updateUI(newDistance)
            }
        }
    }

    private fun updateUI(newDistance: Float) {
        totalDistance = newDistance
        textViewDistance.text = String.format(Locale.getDefault(), "%.2f km", totalDistance / 1000)
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        val speedKM = if (elapsedTime > 0) totalDistance / elapsedTime * 3.6 else 0.0

        textViewSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", speedKM)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driving, container, false)
        textViewDistance = view.findViewById(R.id.textViewDistance)
        textViewSpeed = view.findViewById(R.id.textViewSpeed)
        textViewTime = view.findViewById(R.id.textViewTime)
        buttonFinish = view.findViewById(R.id.buttonFinish)
        mapView = view.findViewById(R.id.mapView)

        // Configure MapView
        Configuration.getInstance().userAgentValue = requireContext().packageName
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller
        mapController.setZoom(18.0)

        // Configure pathOverlay
        pathOverlay.outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.primaryColor)
        pathOverlay.outlinePaint.strokeWidth = 10f
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
        startDrivingService()
        buttonFinish.setOnClickListener {
            finishDriving()
        }
    }

    private fun startDrivingService() {
        val drivingServiceIntent = Intent(requireContext(), DrivingService::class.java)
        ContextCompat.startForegroundService(requireContext(), drivingServiceIntent)
        startTime = System.currentTimeMillis()
        handler.post(runnable)
    }


    private fun finishDriving() {
        val duration = (System.currentTimeMillis() - startTime) / 1000
        val distance = totalDistance / 1000  // Convert to kilometers
        val caloriesBurned = 1.3 * (duration / 3600f) * weightKg

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(System.currentTimeMillis())

        // Format the numbers using Locale.US to ensure a period as the decimal separator
        val formattedDistance = String.format(Locale.US, "%.1f", distance).toFloat()
        val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()

        val activity = Activity(
            date = currentDate,
            duration = duration,
            caloriesBurned = formattedCaloriesBurned,
            type = "driving",
            distance = formattedDistance,
            steps = 0
        )

        val bundle = Bundle().apply {
            putParcelable("activity", activity)
        }

        val intent = Intent(requireContext(), DrivingService::class.java)
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
        handler.post(runnable)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationUpdateReceiver, IntentFilter("location_update")
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(requireContext(), DrivingService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()
    }
}
