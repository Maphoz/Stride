package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DisplayFragment : Fragment() {

    private lateinit var textViewDate: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewCalories: TextView
    private lateinit var textViewDistance: TextView
    private lateinit var textViewSpeed: TextView
    private lateinit var textViewSteps: TextView
    private lateinit var textViewTitle: TextView
    private lateinit var buttonDelete: Button
    private lateinit var buttonBack: Button
    private lateinit var activityDao: ActivityDao
    private lateinit var callingFragment: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_display, container, false)

        textViewDate = view.findViewById(R.id.textViewDate)
        textViewDuration = view.findViewById(R.id.textViewDuration)
        textViewCalories = view.findViewById(R.id.textViewCalories)
        textViewDistance = view.findViewById(R.id.textViewDistance)
        textViewSpeed = view.findViewById(R.id.textViewSpeed)
        textViewSteps = view.findViewById(R.id.textViewSteps)
        buttonBack = view.findViewById(R.id.buttonBack)
        buttonDelete = view.findViewById(R.id.buttonDelete)
        textViewTitle = view.findViewById(R.id.statsTitle)

        lifecycleScope.launch {
            initializeActivityDao()
        }

        return view
    }

    private suspend fun initializeActivityDao() {
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            activityDao = db.activityDao
        }
    }

    fun formatDateToItalian(dateString: String): String {
        // Define the input formatter
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

        // Define the output formatter for month in Italian
        val monthNames = listOf(
            "Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
            "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
        )

        // Parse the input date
        val date = LocalDate.parse(dateString, inputFormatter)

        // Extract day, month, and year
        val day = date.dayOfMonth
        val month = monthNames[date.monthValue - 1] // monthValue is 1-based
        val year = date.year

        // Return the formatted string
        return "$day $month $year"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve the activity data from arguments
        val activity = arguments?.getParcelable<Activity>("activity")
        callingFragment = arguments?.getString("callingFragment").toString()

        activity?.let {
            val distanceInMeters = activity.distance!! * 1000  // Convert distance to meters if it's in kilometers
            val durationInSeconds = activity.duration.toFloat()  // Duration in seconds

            val speedInMetersPerSecond = if (durationInSeconds > 0) {
                distanceInMeters / durationInSeconds
            } else {
                0f
            }

            val averageSpeedInKmPerHour = speedInMetersPerSecond * 3.6f

            val formattedSpeed = String.format("%.1f", averageSpeedInKmPerHour)
            textViewDate.text = formatDateToItalian(it.date)
            textViewDuration.text = formatDuration(it.duration)
            textViewCalories.text = "${it.caloriesBurned} kcal"
            textViewDistance.text = it.distance?.let { distance -> "$distance km" } ?: "N/A"
            textViewSpeed.text = it.distance?.let { "$formattedSpeed km/h" } ?: "N/A"
            textViewSteps.text = it.steps?.toString() ?: "N/A"
            textViewTitle.text = getTitle(activity.type)
        }

        // Set click listener for the buttons
        buttonDelete.setOnClickListener {
            if (activity != null) {
                deteleteActivity(activity)
            }
            navigateBack()
        }

        buttonBack.setOnClickListener {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if(callingFragment == "Home"){
            findNavController().navigate(R.id.action_display_to_home)
        } else{
            findNavController().navigate(R.id.action_display_to_filter)
        }
    }

    private fun getTitle(type: String): CharSequence? {
        return when (type) {
            "walking" -> "Statistiche camminata"
            "running" -> "Statistiche corsa"
            "driving" -> "Statistiche guida"
            "sitting" -> "Statistiche seduta"
            else -> null
        }
    }

    private fun deteleteActivity(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            activityDao.deleteActivity(activity)
        }
    }


    // Utility function to format duration
    private fun formatDuration(duration: Long): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return "$minutes min $seconds sec"
    }
}
