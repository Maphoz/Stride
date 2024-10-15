package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.slider.RangeSlider
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class FilterFragment : Fragment() {

    private var activityTypes = arrayListOf<String>("walking", "running", "driving", "sitting")

    private var offset = 0
    private var limit = 5
    private lateinit var activityDao: ActivityDao
    private lateinit var activitiesContainer: ConstraintLayout
    private lateinit var btnLoadMore: Button
    private lateinit var btnApplyFilters: Button
    private lateinit var btnClearFilters: Button
    private lateinit var rangeSlider: RangeSlider

    private lateinit var totalActivitiesTextView: TextView
    private lateinit var totalTimeTextView: TextView
    private lateinit var favoriteActivityTextView: TextView
    private lateinit var favoriteCaloriesTextView: TextView
    private lateinit var totalCaloriesTextView: TextView
    private lateinit var noActivitiesMessage: TextView

    private var selectedSortView: TextView? = null // To keep track of the selected sorting option

    private val allActivities = mutableListOf<Activity>()
    private val filteredActivities = mutableListOf<Activity>()
    private val activitiesToRender = mutableListOf<Activity>()

    private val filterTypesSelected = mutableListOf<String>()

    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null

    private var maxCalories: Float = 1000f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as? MainActivity)?.updateToolbarTitle("FILTRI")
        return inflater.inflate(R.layout.fragment_filter, container, false).apply {
            activitiesContainer = findViewById(R.id.activitiesContainer)
            btnLoadMore = findViewById(R.id.btnLoadMore)
            btnApplyFilters = findViewById(R.id.applyFiltersButton)
            btnClearFilters = findViewById(R.id.clearFiltersButton)
            rangeSlider = findViewById(R.id.caloriesBurnedSlider)

            totalActivitiesTextView = findViewById(R.id.valueTotalActivities)
            totalTimeTextView = findViewById(R.id.valueTotalTime)
            favoriteActivityTextView = findViewById(R.id.valueFavoriteActivity)
            favoriteCaloriesTextView = findViewById(R.id.valueFavoriteCalories)
            totalCaloriesTextView = findViewById(R.id.totalCaloriesCardActivity)
            noActivitiesMessage = findViewById(R.id.noActivitiesMessage)

            btnLoadMore.setOnClickListener {
                loadMoreActivities(activitiesToRender)
            }

            val sortHeader = findViewById<LinearLayout>(R.id.sortHeader)
            val filterHeader = findViewById<LinearLayout>(R.id.filterHeader)
            val sortOptionsContainer = findViewById<LinearLayout>(R.id.sortOptionsContainer)
            val filterOptionsContainer = findViewById<LinearLayout>(R.id.filterOptionsContainer)

            val constraintLayout = findViewById<ConstraintLayout>(R.id.filterPageContainer) // The parent ConstraintLayout
            val constraintSet = ConstraintSet()

            fun updateConstraints() {
                constraintSet.clone(constraintLayout)

                if (sortOptionsContainer.visibility == View.VISIBLE) {
                    constraintSet.connect(R.id.activitiesContainer, ConstraintSet.TOP, R.id.sortOptionsContainer, ConstraintSet.BOTTOM)
                } else if (filterOptionsContainer.visibility == View.VISIBLE) {
                    constraintSet.connect(R.id.activitiesContainer, ConstraintSet.TOP, R.id.filterOptionsContainer, ConstraintSet.BOTTOM)
                } else {
                    constraintSet.connect(R.id.activitiesContainer, ConstraintSet.TOP, R.id.sortFilterContainer, ConstraintSet.BOTTOM)
                }

                constraintSet.applyTo(constraintLayout)
            }

            fun updateSortSelection(selectedView: TextView) {
                // Reset previously selected view
                selectedSortView?.let {
                    it.setBackgroundResource(R.drawable.sort_option_background) // Default state
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.black)) // Default text color
                    it.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0) // Remove the tick icon
                }

                // Set selected view
                selectedView.setBackgroundResource(R.drawable.sort_option_background_selected) // Selected state
                selectedView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white)) // Selected text color
                selectedView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_tick, 0, 0, 0) // Set tick icon

                // Update the currently selected view
                selectedSortView = selectedView
            }

            // Initialize sort header click listener
            sortHeader.setOnClickListener {
                sortOptionsContainer.visibility = if (sortOptionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                filterOptionsContainer.visibility = View.GONE
                updateConstraints()
            }

            // Initialize filter header click listener
            filterHeader.setOnClickListener {
                filterOptionsContainer.visibility = if (filterOptionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                sortOptionsContainer.visibility = View.GONE
                updateConstraints()
            }

            updateSortSelection(findViewById(R.id.sortByDateNewest))

            btnApplyFilters.setOnClickListener {
                applyFilters()
                filterOptionsContainer.visibility = if (filterOptionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                sortOptionsContainer.visibility = View.GONE
                updateConstraints()
            }

            btnClearFilters.setOnClickListener {
                clearFilters()
                filterOptionsContainer.visibility = if (filterOptionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                sortOptionsContainer.visibility = View.GONE
                updateConstraints()
            }

            // Set up click listeners for sorting options
            listOf(
                R.id.sortByDateNewest,
                R.id.sortByDateOldest,
                R.id.sortByCaloriesAsc,
                R.id.sortByCaloriesDesc,
                R.id.sortByDurationLongest,
                R.id.sortByDurationShortest
            ).forEach { id ->
                findViewById<TextView>(id).setOnClickListener { view ->
                    updateSortSelection(view as TextView)
                    handleSorting(view.id)
                }
            }

            setupFilterSelection(this)

            val filterDateRange = findViewById<TextView>(R.id.filterDateRange)
            filterDateRange.setOnClickListener {
                val constraintsBuilder = CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())

                val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Seleziona intervallo di date")
                    .setTheme(R.style.ThemeOverlay_CustomDatePicker)
                    .build()

                datePicker.show(parentFragmentManager, "date_range_picker")

                datePicker.addOnPositiveButtonClickListener { selection ->
                    selectedStartDate = selection.first
                    selectedEndDate = selection.second

                    val formattedStartDate = formatDate(selectedStartDate)
                    val formattedEndDate = formatDate(selectedEndDate)

                    filterDateRange.text = "$formattedStartDate - $formattedEndDate"
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            initializeActivityDao()
            maxCalories = withContext(Dispatchers.IO) {
                activityDao.getMaxCaloriesBurned()
            }
            setupRangeSlider(maxCalories)
            loadActivities()
            loadMoreActivities(allActivities) // Load the first batch
            updateCardsData()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCardsData() {
        val totalActivities = activitiesToRender.size
        totalActivitiesTextView.text = totalActivities.toString()

        val totalTimeInSeconds = activitiesToRender.sumOf { it.duration }
        totalTimeTextView.text = timeInHM(totalTimeInSeconds.toInt()) + " H"

        val activityTypeDurations = activitiesToRender.groupBy { it.type }
            .mapValues { entry -> entry.value.sumOf { it.duration } }

        val favoriteActivity = activityTypeDurations.maxByOrNull { it.value }?.key ?: "N/A"
        val favoriteActivityMapped = mapFavoriteActivity(favoriteActivity)
        favoriteActivityTextView.text = favoriteActivityMapped

        val favoriteCalories = activitiesToRender
            .filter { it.type == favoriteActivity }
            .sumOf { it.caloriesBurned.toInt() }
        favoriteCaloriesTextView.text = favoriteCalories.toString() + " kcal"

        if(favoriteActivityMapped == "Nessun dato"){
            totalCaloriesTextView.text = "Calorie"
        } else{
            totalCaloriesTextView.text = "Calorie " + favoriteActivityMapped
        }
    }

    private fun mapFavoriteActivity(favoriteActivity: String): CharSequence? {
        return when (favoriteActivity.lowercase()) {
            "walking" -> "Camminata"
            "running" -> "Corsa"
            "driving" -> "Guida"
            "sitting" -> "Seduta"
            else -> "Nessun dato" // Returns null if the activity is not recognized
        }
    }

    private suspend fun initializeActivityDao() {
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            activityDao = db.activityDao
        }
    }

    private suspend fun loadActivities() {
        val activities = withContext(Dispatchers.IO) {
            activityDao.getAllActivities()
        }
        allActivities.addAll(activities)

        // Initially, `activitiesToRender` should be all activities
        activitiesToRender.clear()
        if(allActivities.isEmpty()){
            noActivitiesMessage.visibility = View.VISIBLE
            activitiesContainer.visibility = View.GONE
        }
        activitiesToRender.addAll(allActivities)
    }


    private fun loadMoreActivities(activities: List<Activity>) {
        val activitiesToLoad = activities.drop(offset).take(limit)

        activitiesToLoad.forEach { activity ->
            addActivityCard(activity)
        }

        offset += activitiesToLoad.size

        // Show or hide "Load More" button based on whether there are more activities to load
        btnLoadMore.visibility = if (offset < activities.size) View.VISIBLE else View.GONE
    }


    private fun applyFilters() {
        lifecycleScope.launch {
            filteredActivities.clear()
            activitiesContainer.removeAllViews()

            val minCalories: Float?
            val maxCalories: Float?

            // Retrieve the values from the range slider
            val slider = view?.findViewById<RangeSlider>(R.id.caloriesBurnedSlider)
            val values = slider?.values
            if (values != null && values.size == 2) {
                minCalories = values[0]
                maxCalories = values[1]
            } else {
                minCalories = null
                maxCalories = null
            }

            withContext(Dispatchers.IO) {
                val activities = activityDao.getFilteredActivities(
                    startDate = selectedStartDate?.let { formatDateForQuery(it) }, // Pass startDate if not null
                    endDate = selectedEndDate?.let { formatDateForQuery(it) }, // Pass endDate if not null
                    minCalories = minCalories,
                    maxCalories = maxCalories,
                    types = if (filterTypesSelected.size in 1..3) filterTypesSelected else activityTypes
                )
                filteredActivities.addAll(activities)
            }

            offset = 0

            // Set `activitiesToRender` to the filtered activities
            activitiesToRender.clear()
            activitiesToRender.addAll(filteredActivities)

            loadMoreActivities(activitiesToRender)
            updateCardsData()
        }
    }

    private fun clearFilters() {
        filterTypesSelected.clear()
        offset = 0
        activitiesContainer.removeAllViews()

        // Reset `activitiesToRender` to all activities
        activitiesToRender.clear()
        activitiesToRender.addAll(allActivities)

        // Reset RangeSlider
        resetRangeSlider()

        // Reset DatePicker
        resetDatePicker()

        loadMoreActivities(activitiesToRender)
        updateCardsData()
        resetFilterSelection()
    }

    private fun resetRangeSlider() {
        rangeSlider.apply {
            valueFrom = 0f
            valueTo = maxCalories
            values = listOf(0f, maxCalories)
            setLabelFormatter { value -> "${value.toInt()} kcal" }
        }
    }

    private fun resetDatePicker() {
        // Assuming you have a TextView for displaying selected date range
        val filterDateRange = view?.findViewById<TextView>(R.id.filterDateRange)
        filterDateRange?.text = "Seleziona intervallo" // Reset to default text

        // Reset the date range variables if needed
        selectedStartDate = null
        selectedEndDate = null
    }


    private fun resetFilterSelection() {
        val filterOptions = mapOf(
            R.id.filterCamminata to "Camminata",
            R.id.filterCorsa to "Corsa",
            R.id.filterGuida to "Guida",
            R.id.filterSeduta to "Seduta"
        )

        filterOptions.forEach { (viewId, _) ->
            val filterView = view?.findViewById<TextView>(viewId)
            filterView?.setBackgroundResource(R.drawable.filter_option_background)
            filterView?.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
    }

    private fun handleSorting(sortOptionId: Int) {
        offset = 0

        when (sortOptionId) {
            R.id.sortByDateNewest -> activitiesToRender.sortByDescending { it.date }
            R.id.sortByDateOldest -> activitiesToRender.sortBy { it.date }
            R.id.sortByCaloriesAsc -> activitiesToRender.sortByDescending { it.caloriesBurned }
            R.id.sortByCaloriesDesc -> activitiesToRender.sortBy { it.caloriesBurned }
            R.id.sortByDurationLongest -> activitiesToRender.sortByDescending { it.duration }
            R.id.sortByDurationShortest -> activitiesToRender.sortBy { it.duration }
        }

        activitiesContainer.removeAllViews()
        loadMoreActivities(activitiesToRender)
    }



    private fun setupFilterSelection(view: View) {
        val filterOptions = mapOf(
            R.id.filterCamminata to "walking",
            R.id.filterCorsa to "running",
            R.id.filterGuida to "driving",
            R.id.filterSeduta to "sitting"
        )

        filterOptions.forEach { (viewId, filterType) ->
            val filterView = view.findViewById<TextView>(viewId)
            filterView.setOnClickListener {
                toggleFilterSelection(filterView, filterType)
            }
        }
    }

    private fun toggleFilterSelection(view: TextView, filterType: String) {
        if (filterTypesSelected.contains(filterType)) {
            // Deselect filter
            filterTypesSelected.remove(filterType)
            view.setBackgroundResource(R.drawable.filter_option_background)
            view.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        } else {
            // Select filter
            filterTypesSelected.add(filterType)
            view.setBackgroundResource(R.drawable.selected_filter_option_background)
            view.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
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
                    putString("callingFragment", "Filter")
                }
            }

            // Replace the current fragment with DisplayFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, displayFragment)
                .addToBackStack(null)
                .commit()
        }

        activitiesContainer.addView(cardView)

        // Update constraints for all children in activitiesContainer
        val parentSet = ConstraintSet()
        parentSet.clone(activitiesContainer)
        val childCount = activitiesContainer.childCount

        if (childCount > 1) {
            val previousChild = activitiesContainer.getChildAt(childCount - 2)
            parentSet.connect(cardView.id, ConstraintSet.TOP, previousChild.id, ConstraintSet.BOTTOM, dpToPx(12)) // Increased margin
        } else {
            parentSet.connect(cardView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }

        parentSet.applyTo(activitiesContainer)
    }

    private fun setupRangeSlider(maxCalories: Float) {
        rangeSlider.apply {
            valueFrom = 0f
            valueTo = maxCalories
            values = listOf(0f, maxCalories)
            setLabelFormatter { value -> "${value.toInt()} KCAL" } // Adds "KCAL" to the label
        }

        // Update labels based on initial values
        rangeSlider.setLabelFormatter { value: Float ->
            "${value.toInt()} KCAL"
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
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

    private fun formatDate(timeInMillis: Long?): String {
        return timeInMillis?.let {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(it))
        } ?: "N/A"
    }

    private fun formatDateForQuery(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun timeInHM(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60

        return String.format("%02d:%02d", hours, minutes)
    }

}
