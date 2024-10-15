package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var selectedActivity: String = "running"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as? MainActivity)?.updateToolbarTitle("HOME")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.startActivityString = "Corsa"
        // Initialize UI with default selection
        setCardSelected(binding.layoutRunning, true)
        setCardSelected(binding.layoutWalking, false)
        setCardSelected(binding.layoutDriving, false)
        setCardSelected(binding.layoutSitting, false)

        // Set click listeners for the cards
        binding.cardRunning.setOnClickListener { onCardSelected("running") }
        binding.cardWalking.setOnClickListener { onCardSelected("walking") }
        binding.cardDriving.setOnClickListener { onCardSelected("driving") }
        binding.cardSitting.setOnClickListener { onCardSelected("sitting") }


        binding.startActivityButton.setOnClickListener {
            val intent = Intent(requireContext(), TrackingActivity::class.java).apply {
                putExtra("ACTIVITY_TYPE", selectedActivity)
            }
            startActivity(intent)
        }


        binding.btnShowMore.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_filter)
        }
        // Retrieve user info from database and set it
        lifecycleScope.launch {
            val user = getUserFromDatabase()
            if (user != null) {
                binding.user = user
            }
            loadActivityData(user?.stepGoal)
        }

        // Load the latest 5 activities and display them
        lifecycleScope.launch {
            loadLatestActivities()
        }

        return view
    }

    private suspend fun getUserFromDatabase(): User? {
        return withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            val userDao = db.userDao
            userDao.getUserInfo()
        }
    }

    private suspend fun loadActivityData(stepGoal : Int?) {
        val db = DatabaseSingleton.getDatabase(requireContext())
        val activityDao = db.activityDao

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // Query all activities for today
        val todayCalories = withContext(Dispatchers.IO) {
            activityDao.getTotalCaloriesAfterDate(todayDate)
        }

        val todayDistance = withContext(Dispatchers.IO) {
            activityDao.getTotalDistanceAfterDate(todayDate)
        }

        val todayTime = withContext(Dispatchers.IO) {
            activityDao.getTotalTimeAfterDate(todayDate)
        }

        val dailySteps = withContext(Dispatchers.IO) {
            activityDao.getTotalStepsOnDate(todayDate)
        }
        // Update UI
        withContext(Dispatchers.Main) {
            binding.calories = todayCalories.toInt()
            binding.distance = todayDistance
            binding.time = timeInHM(todayTime.toInt())
            binding.currentSteps = dailySteps.toString()
            binding.stepGoal = stepGoal.toString()
            val goal = stepGoal ?: 1
            val calculatedPercentage = Math.min(100f, dailySteps.toFloat() / goal * 100)
            binding.calculatedPercentage = calculatedPercentage.toInt()
        }
    }

    private fun timeInHM(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60

        return String.format("%02d:%02d", hours, minutes)
    }

    private fun getStartOfWeek(): String {
        // Get today's date
        val today = LocalDate.now()

        // Find the start of the week (Monday)
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Format the date to "yyyy-MM-dd"
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return startOfWeek.format(formatter)
    }

    private fun onCardSelected(activity: String) {
        if (selectedActivity != activity) {
            when (selectedActivity) {
                "running" -> setCardSelected(binding.layoutRunning, false)
                "walking" -> setCardSelected(binding.layoutWalking, false)
                "driving" -> setCardSelected(binding.layoutDriving, false)
                "sitting" -> setCardSelected(binding.layoutSitting, false)
            }

            selectedActivity = activity

            when (selectedActivity) {
                "running" -> {
                    setCardSelected(binding.layoutRunning, true)
                    binding.startActivityString = "Corsa"
                }
                "walking" -> {
                    setCardSelected(binding.layoutWalking, true)
                    binding.startActivityString = "Camminata"
                }
                "driving" -> {
                    setCardSelected(binding.layoutDriving, true)
                    binding.startActivityString = "Guida"
                }
                "sitting" -> {
                    setCardSelected(binding.layoutSitting, true)
                    binding.startActivityString = "Seduta"
                }
            }
        }
    }

    private fun setCardSelected(layout: ConstraintLayout, isSelected: Boolean) {
        layout.setBackgroundResource(
            if (isSelected) R.drawable.selected_activity_border else R.drawable.default_activity_border
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun loadLatestActivities() {
        val db = DatabaseSingleton.getDatabase(requireContext())
        val activityDao = db.activityDao

        val latestActivities = withContext(Dispatchers.IO) {
            activityDao.getLatestFiveActivities()
        }

        withContext(Dispatchers.Main) {
            binding.activitiesContainer.removeAllViews()
            if(latestActivities.isEmpty()){
                binding.noActivitiesMessage.visibility = View.VISIBLE
                binding.activitiesContainer.visibility = View.GONE
                binding.btnShowMore.visibility = View.GONE
            } else{
                latestActivities.forEach { activity ->
                    addActivityCard(activity)
                }
            }
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

    private fun addActivityCard(activity: Activity) {
        val cardView = CardView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12))
            }
            radius = 12f
            cardElevation = 4f
        }

        val constraintLayout = ConstraintLayout(requireContext()).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val icon = ImageView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(30),
                dpToPx(30)
            )
            setImageResource(getIconResource(activity.type))
        }

        val textContainer = LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                0,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(16)
            }
            orientation = LinearLayout.VERTICAL
        }

        val durationTextView = TextView(requireContext()).apply {
            text = formatDuration(activity.duration)
            textSize = 16f
            setTextColor(resources.getColor(R.color.black, null))
        }

        val dateTextView = TextView(requireContext()).apply {
            text = formatDateToItalian(activity.date)
            textSize = 14f
            setTextColor(resources.getColor(R.color.graySecondary, null))
        }

        val caloriesTextView = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = "${activity.caloriesBurned} kcal"
            textSize = 16f
            setTextColor(resources.getColor(R.color.black, null))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }

        textContainer.addView(durationTextView)
        textContainer.addView(dateTextView)

        constraintLayout.addView(icon)
        constraintLayout.addView(textContainer)
        constraintLayout.addView(caloriesTextView)

        val set = ConstraintSet().apply {
            clone(constraintLayout)

            connect(icon.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(icon.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(icon.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            connect(textContainer.id, ConstraintSet.START, icon.id, ConstraintSet.END, 32)
            connect(textContainer.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(textContainer.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(textContainer.id, ConstraintSet.END, caloriesTextView.id, ConstraintSet.START, 16)

            connect(caloriesTextView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(caloriesTextView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(caloriesTextView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        set.applyTo(constraintLayout)
        cardView.addView(constraintLayout)

        cardView.setOnClickListener {
            // Create a new instance of DisplayFragment
            val displayFragment = DisplayFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("activity", activity)
                    putString("callingFragment", "Home")
                }
            }

            // Replace the current fragment with DisplayFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, displayFragment)
                .addToBackStack(null)
                .commit()
        }

        binding.activitiesContainer.addView(cardView)

        // Update constraints for all children in activitiesContainer
        val parentSet = ConstraintSet()
        parentSet.clone(binding.activitiesContainer)
        val childCount = binding.activitiesContainer.childCount

        if (childCount > 1) {
            val previousChild = binding.activitiesContainer.getChildAt(childCount - 2)
            parentSet.connect(cardView.id, ConstraintSet.TOP, previousChild.id, ConstraintSet.BOTTOM, dpToPx(12)) // Increased margin
        } else {
            parentSet.connect(cardView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }

        parentSet.applyTo(binding.activitiesContainer)
    }


    private fun getIconResource(type: String): Int {
        return when (type) {
            "running" -> R.drawable.ic_running_ac
            "walking" -> R.drawable.ic_walking_ac
            "sitting" -> R.drawable.ic_sitting_ac
            "driving" -> R.drawable.ic_driving_ac
            else -> R.drawable.ic_walking_ac
        }
    }

    private fun formatDuration(duration: Long): String {
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
