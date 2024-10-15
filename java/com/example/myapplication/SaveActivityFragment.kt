package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaveActivityFragment : Fragment() {

    private lateinit var activity: Activity
    private lateinit var textViewDate: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewCalories: TextView
    private lateinit var textViewDistance: TextView
    private lateinit var textViewSpeed: TextView
    private lateinit var textViewSteps: TextView
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var activityDao: ActivityDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_save_activity, container, false)

        textViewDate = view.findViewById(R.id.textViewDate)
        textViewDuration = view.findViewById(R.id.textViewDuration)
        textViewCalories = view.findViewById(R.id.textViewCalories)
        textViewDistance = view.findViewById(R.id.textViewDistance)
        textViewSpeed = view.findViewById(R.id.textViewSpeed)
        textViewSteps = view.findViewById(R.id.textViewSteps)
        buttonSave = view.findViewById(R.id.buttonSave)
        buttonDelete = view.findViewById(R.id.buttonDelete)

        activity = arguments?.getParcelable("activity") ?: throw IllegalStateException("No activity data found")

        initializeActivityDao()

        displayActivityData()

        buttonSave.setOnClickListener {
            saveActivityToDatabase()
            val activityManager = ActivityManager(requireContext())
            activityManager.saveActivityTime()
            navigateToMainActivity()
        }

        buttonDelete.setOnClickListener {
            navigateToMainActivity()
        }

        return view
    }

    private fun displayActivityData() {
        textViewDate.text = activity.date
        textViewDuration.text = formatElapsedTime(activity.duration)
        textViewCalories.text = String.format("%.1f kcal", activity.caloriesBurned)

        if (activity.distance!! > 0) {
            textViewDistance.visibility = View.VISIBLE
            textViewDistance.text = String.format("%.2f km", activity.distance)

            val distanceInMeters = activity.distance!! * 1000  // Convert distance to meters if it's in kilometers
            val durationInSeconds = activity.duration.toFloat()  // Duration in seconds

            val speedInMetersPerSecond = if (durationInSeconds > 0) {
                distanceInMeters / durationInSeconds
            } else {
                0f
            }

            val averageSpeedInKmPerHour = speedInMetersPerSecond * 3.6f

            textViewSpeed.visibility = View.VISIBLE
            textViewSpeed.text = String.format("%.2f km/h", averageSpeedInKmPerHour)
        } else {
            textViewDistance.visibility = View.GONE
            textViewSpeed.visibility = View.GONE
        }

        // Show steps only if activity type is "walking" or "running"
        if ((activity.type == "walking" || activity.type == "running") && activity.steps!! > 0) {
            textViewSteps.visibility = View.VISIBLE
            textViewSteps.text = activity.steps.toString()
        } else {
            textViewSteps.visibility = View.GONE
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun navigateToMainActivity() {
        val mainIntent = Intent(requireContext(), MainActivity::class.java)
        startActivity(mainIntent)
        requireActivity().finish()
    }

    private fun saveActivityToDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            activityDao.insertActivity(activity)
        }
    }

    private fun initializeActivityDao() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = DatabaseSingleton.getDatabase(requireContext())
                activityDao = db.activityDao
            }
        }
    }
}
