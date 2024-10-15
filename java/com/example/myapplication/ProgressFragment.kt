package com.example.myapplication

import android.graphics.BlendMode
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.decoration.rememberHorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.fullWidth
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.rememberExtraLambda
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.of
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.compose.common.shape.dashed
import com.patrykandpatrick.vico.core.cartesian.HorizontalLayout
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.HorizontalPosition
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ComponentShader
import com.patrykandpatrick.vico.core.common.shader.DynamicShader
import com.patrykandpatrick.vico.core.common.shader.DynamicShader.Companion.verticalGradient
import com.patrykandpatrick.vico.core.common.shape.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt


enum class TimePeriod {
    TODAY, THIS_WEEK, THIS_MONTH, LAST_7_DAYS, LAST_30_DAYS
}

class ProgressFragment : Fragment() {

    private lateinit var composeView: ComposeView
    private lateinit var activityDao: ActivityDao // Assume this is initialized properly
    private lateinit var weightDao: WeightDao
    private var selectedTimePeriod: TimePeriod = TimePeriod.THIS_WEEK
    private var selectedBarChartTimePeriod: TimePeriod = TimePeriod.THIS_WEEK

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_progress, container, false)
        composeView = view.findViewById(R.id.composeView)

        (activity as? MainActivity)?.updateToolbarTitle("STATISTICHE")

        val doughnutButtons = listOf(
            view.findViewById<Button>(R.id.doughnutButtonToday),
            view.findViewById<Button>(R.id.doughnutButtonThisWeek),
            view.findViewById<Button>(R.id.doughnutButtonSevenDays),
            view.findViewById<Button>(R.id.doughnutButtonThirtyDays)
        )

        doughnutButtons.forEach { button ->
            button.setOnClickListener {
                selectButton(button, false)
            }
        }

        val barChartButtons = listOf(
            view.findViewById<Button>(R.id.chartButtonThisWeek),
            view.findViewById<Button>(R.id.chartButtonThisMonth),
            view.findViewById<Button>(R.id.chartButtonSevenDays),
            view.findViewById<Button>(R.id.chartButtonThirtyDays)
        )

        barChartButtons.forEach { button ->
            button.setOnClickListener {
                selectButton(button, true)
            }
        }

        lifecycleScope.launch {
            initializeDaos()
            selectButton(view.findViewById(R.id.doughnutButtonThisWeek), false)
            selectButton(view.findViewById(R.id.chartButtonThisWeek), true)
        }
        return view
    }

    private fun setupDoughnutChart(chart: PieChart, data: List<PieEntry>) {
        val colors = listOf(
            Color(0xFF1E90FF).toArgb(), // Vibrant Blue (Dodger Blue)
            Color(0xFF00DADA).toArgb(), // Cyan (00DADA)
            Color(0xFF00FF00).toArgb(), // Vibrant Green (Lime Green)
            Color(0xFF8A2BE2).toArgb()  // Vibrant Purple (Electric Purple)
        )

        val dataSet = PieDataSet(data, "")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f


        dataSet.valueTextSize = 0f
        dataSet.valueTextColor = Color.Transparent.toArgb()
        dataSet.setDrawValues(false)

        chart.isDrawHoleEnabled = true
        chart.holeRadius = 45f
        chart.transparentCircleRadius = 55f

        chart.description.isEnabled = false
        chart.setDrawEntryLabels(false)

        chart.legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            isWordWrapEnabled = true
            // Adjust the position to ensure it is centered
            xOffset = 0f
            yOffset = 10f
            formSize = 10f
            textSize = 12f
            form = Legend.LegendForm.CIRCLE
        }

        chart.data = PieData(dataSet)
        chart.invalidate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private suspend fun initializeDaos(){
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            activityDao = db.activityDao
            weightDao = db.weightDao
        }
    }

    private suspend fun getActivityDurationsGroupedByType(): List<PieEntry> = withContext(Dispatchers.IO){
        val startDate = when (selectedTimePeriod) {
            TimePeriod.TODAY -> getToday()
            TimePeriod.THIS_WEEK -> getStartOfWeek()
            TimePeriod.LAST_7_DAYS -> get7daysAgo()
            TimePeriod.LAST_30_DAYS -> get30daysAgo()
            TimePeriod.THIS_MONTH -> get30daysAgo()
        }

        // Fetch the data grouped by type
        val typeDurations = activityDao.getTotalDurationPerTypeAfterDate(startDate)

        // Map to ensure the order: walking, running, sitting, driving
        val durationMap = typeDurations.associate { it.type to (it.totalDuration).toInt() }

        // Ensure all types are present, even if the duration is 0
        val walking = durationMap["walking"] ?: 0
        val running = durationMap["running"] ?: 0
        val sitting = durationMap["sitting"] ?: 0
        val driving = durationMap["driving"] ?: 0

        // Create PieEntry list
        return@withContext listOf(
            PieEntry(walking.toFloat(), "Walking"),
            PieEntry(running.toFloat(), "Running"),
            PieEntry(sitting.toFloat(), "Sitting"),
            PieEntry(driving.toFloat(), "Driving")
        )
    }

    private fun getToday(): String {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return today.format(formatter)
    }

    private fun get7daysAgo(): String {
        //devo toglierne 6 piuttosto che 7 per includere l'ultimo giorno
        val sevenDaysAgo = LocalDate.now().minusDays(6)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return sevenDaysAgo.format(formatter)
    }

    private fun get30daysAgo(): String {
        //devo toglierne 29 piuttosto che 30 per includere l'ultimo giorno

        val thirtyDaysAgo = LocalDate.now().minusDays(29)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return thirtyDaysAgo.format(formatter)
    }

    private fun selectButton(selected: Button, isBarChart: Boolean) {
        val buttons = if (isBarChart) {
            listOf(
                view?.findViewById<Button>(R.id.chartButtonThisWeek),
                view?.findViewById<Button>(R.id.chartButtonThisMonth),
                view?.findViewById<Button>(R.id.chartButtonSevenDays),
                view?.findViewById<Button>(R.id.chartButtonThirtyDays)
            )
        } else {
            listOf(
                view?.findViewById<Button>(R.id.doughnutButtonToday),
                view?.findViewById<Button>(R.id.doughnutButtonThisWeek),
                view?.findViewById<Button>(R.id.doughnutButtonSevenDays),
                view?.findViewById<Button>(R.id.doughnutButtonThirtyDays)
            )
        }

        buttons.forEach { button ->
            button?.isSelected = button == selected
        }

        if (isBarChart) {
            selectedBarChartTimePeriod = when (selected.id) {
                R.id.chartButtonThisWeek -> TimePeriod.THIS_WEEK
                R.id.chartButtonThisMonth -> TimePeriod.THIS_MONTH
                R.id.chartButtonSevenDays -> TimePeriod.LAST_7_DAYS
                R.id.chartButtonThirtyDays -> TimePeriod.LAST_30_DAYS
                else -> TimePeriod.THIS_WEEK
            }
            lifecycleScope.launch {
                refreshBarChartData()
            }
        } else {
            selectedTimePeriod = when (selected.id) {
                R.id.doughnutButtonToday -> TimePeriod.TODAY
                R.id.doughnutButtonThisWeek -> TimePeriod.THIS_WEEK
                R.id.doughnutButtonSevenDays -> TimePeriod.LAST_7_DAYS
                R.id.doughnutButtonThirtyDays -> TimePeriod.LAST_30_DAYS
                else -> TimePeriod.THIS_WEEK
            }
            lifecycleScope.launch {
                refreshChartData()
            }
        }
    }

    private suspend fun refreshBarChartData() {
        fetchBarChartData()
    }

    private suspend fun fetchBarChartData() {
        val (startDate, periodDays) = when (selectedBarChartTimePeriod) {
            TimePeriod.TODAY -> get7daysAgo() to 7
            TimePeriod.THIS_WEEK -> getStartOfWeek() to 7
            TimePeriod.LAST_7_DAYS -> get7daysAgo() to 7
            TimePeriod.LAST_30_DAYS -> get30daysAgo() to 30
            TimePeriod.THIS_MONTH -> getStartOfMonth() to LocalDate.now().lengthOfMonth() // For a month, get the exact number of days
        }

        val caloriesData: List<Pair<String, Float>>
        val stepsData: List<Pair<String, Float>>
        val distanceData: List<Pair<String, Float>>
        val weightData: List<Pair<String, Float>>

        withContext(Dispatchers.IO) {
            val calories = activityDao.getCaloriesBurnedAfter(startDate)
            val steps = activityDao.getStepsMadeAfter(startDate)
            val distance = activityDao.getDistanceAfter(startDate)
            val weightMeasurements = weightDao.getWeightMeasurementsGroupedByDate()
                .map { it.date to it.averageWeight }

            caloriesData = fillMissingDataBar(calories.map { it.date to it.totalCalories }, startDate, periodDays)
            stepsData = fillMissingDataBar(steps.map { it.date to it.totalSteps.toFloat() }, startDate, periodDays)
            distanceData = fillMissingDataBar(distance.map { it.date to it.totalDistance }, startDate, periodDays)
            weightData = weightMeasurements
        }

        // Once data is fetched, call setupComposeChart to update the UI
        setupComposeChart(caloriesData.map { it.second }, stepsData.map { it.second }, distanceData.map { it.second }, weightData)
    }


    private suspend fun refreshChartData() {
        val doughnutChart = view?.findViewById<PieChart>(R.id.doughnutChart)
        val data = getActivityDurationsGroupedByType()

        // Check if all values are zero
        val allZero = data.all { it.value == 0f }

        if (data.isNotEmpty() && !allZero) {
            view?.findViewById<TextView>(R.id.noDataTextView)?.visibility = View.GONE
            doughnutChart?.visibility = View.VISIBLE
            view?.findViewById<ConstraintLayout>(R.id.doughnutButtonContainer)?.visibility = View.VISIBLE
            setupDoughnutChart(doughnutChart!!, data)
        }
    }



    private fun setupComposeChart(
        caloriesData: List<Float>,
        stepsData: List<Float>,
        distanceData: List<Float>,
        weightData: List<Pair<String, Float>>
    ) {
        val allCaloriesZero = caloriesData.all { it == 0f }
        val allStepsZero = stepsData.all { it == 0f }
        val allDistanceZero = distanceData.all { it == 0f }
        composeView.setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!allCaloriesZero || !allStepsZero || !allDistanceZero) {
                    view?.findViewById<ComposeView>(R.id.composeView)?.visibility = View.VISIBLE
                    view?.findViewById<TextView>(R.id.noDataChartText)?.visibility = View.GONE
                    view?.findViewById<ConstraintLayout>(R.id.chartButtonContainer)?.visibility = View.VISIBLE
                    if (!allCaloriesZero) {
                        BarChart(
                            title = "Calorie bruciate",
                            titleColor = Color(0xff246EE9),
                            gradientColors = listOf(Color(0xff0000ff), Color(0xff00dada)),
                            values = caloriesData,
                            timePeriod = selectedBarChartTimePeriod
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!allStepsZero) {
                        BarChart(
                            title = "Passi compiuti",
                            titleColor = Color(0xff246EE9),
                            gradientColors = listOf(Color(0xff0000ff), Color(0xff00dada)),
                            values = stepsData,
                            timePeriod = selectedBarChartTimePeriod
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!allDistanceZero) {
                        BarChart(
                            title = "Distanza percorsa",
                            titleColor = Color(0xff246EE9),
                            gradientColors = listOf(Color(0xff0000ff), Color(0xff00dada)),
                            values = distanceData,
                            timePeriod = selectedBarChartTimePeriod
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if(weightData.size > 1){
                        LineChart(
                            title = "Peso nel tempo",
                            titleColor = Color(0xff246EE9),
                            lineColor = Color(0xff00FF00),
                            values = weightData // Pass date-weight pairs
                        )
                    }
                }
            }
        }
    }


    private fun getStartOfWeek(): String {
        val today = LocalDate.now()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return startOfWeek.format(formatter)
    }

    private fun getStartOfMonth(): String {
        val today = LocalDate.now()
        val startOfMonth = today.withDayOfMonth(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return startOfMonth.format(formatter)
    }

    private fun fillMissingDataBar(
        data: List<Pair<String, Float>>, // The original list of date-value pairs
        startDate: String,               // The start date as a string in "yyyy-MM-dd" format
        periodDays: Int                  // Number of days in the period (e.g., 7 or 30)
    ): List<Pair<String, Float>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startLocalDate = LocalDate.parse(startDate, formatter)

        // Generate all dates within the period starting from the startDate
        val allDatesInRange = (0 until periodDays).map { startLocalDate.plusDays(it.toLong()) }

        // Convert input data into a map for easier lookup
        val dataMap = data.toMap()

        // Create a list of date-value pairs, filling missing dates with 0
        return allDatesInRange.map { date ->
            val dateString = date.format(formatter)
            dateString to (dataMap[dateString] ?: 0f) // Use the value from the map or 0 if not present
        }
    }

    private fun fillMissingData(data: List<Pair<String, Float>>): List<Float> {
        val startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dataMap = data.toMap()
        return (0..6).map {
            val date = startDate.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            dataMap[date] ?: 0f
        }
    }
}

fun getLast7Days(): List<String> {
    val formatter = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
    return (0..6).map { LocalDate.now().minusDays(it.toLong()).format(formatter) }.reversed()
}

// Utility function to get the days of the current month as strings
fun getCurrentMonthDays(): List<String> {
    val now = LocalDate.now()
    val daysInMonth = now.lengthOfMonth()
    return (1..daysInMonth).map { it.toString().padStart(2, '0') }
}

// Utility function to get the last 30 days as strings
fun getLast30Days(): List<String> {
    val formatter = DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault())
    return (0..29).map { LocalDate.now().minusDays(it.toLong()).format(formatter) }.reversed()
}

fun createCustomValueFormatter(
    valueCount: Int,
    timePeriod: TimePeriod
): CartesianValueFormatter {
    return CartesianValueFormatter { value, _, _ ->
        val intValue = value.toInt()
        when (timePeriod) {
            TimePeriod.THIS_WEEK -> {
                val daysOfWeek = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
                daysOfWeek.getOrElse(intValue) { "" }
            }
            TimePeriod.LAST_7_DAYS -> getLast7Days().getOrElse(intValue) { "" }
            TimePeriod.THIS_MONTH -> {
                val daysOfMonth = getCurrentMonthDays()
                if (valueCount == 30) {
                    // Spread 6 labels evenly
                    val interval = daysOfMonth.size / 6
                    if (intValue % interval == 0) daysOfMonth.getOrElse(intValue) { "" } else ""
                } else {
                    daysOfMonth.getOrElse(intValue) { "" }
                }
            }
            TimePeriod.LAST_30_DAYS -> {
                val last30Days = getLast30Days()
                if (valueCount == 30) {
                    // Spread 6 labels evenly
                    val interval = last30Days.size / 6
                    if (intValue % interval == 0) last30Days.getOrElse(intValue) { "" } else ""
                } else {
                    last30Days.getOrElse(intValue) { "" }
                }
            }
            else -> ""
        }
    }
}

fun createWeeklyDataMap(values: List<Float>): Map<String, Float> {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    require(values.size == 7) { "The list must contain exactly 7 values." }
    return daysOfWeek.zip(values).toMap()
}

@Composable
fun BarChart(
    title: String,
    titleColor: Color,
    gradientColors: List<Color>,
    values: List<Float>,
    modifier: Modifier = Modifier,
    timePeriod: TimePeriod
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val average = values.average().toInt()

    val horizontalLineKey = ExtraStore.Key<Float>()

    val xLabelsFormatter = createCustomValueFormatter(values.size, timePeriod)

    val integerValueFormatter = CartesianValueFormatter { value, _, _ ->
        value.roundToInt().toString()
    }


    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            textSize = 14.sp,
            color = Color(0xff000000)
        ),
        labelPosition = DefaultCartesianMarker.LabelPosition.AbovePoint,
    )

    LaunchedEffect(values) {
        withContext(Dispatchers.Default) {
            modelProducer.runTransaction {
                columnSeries {
                    series(values) }
                extras {
                    it[horizontalLineKey] = average.toFloat()
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(top = 15.dp, start = 18.dp, bottom = 2.dp)
                .align(Alignment.Start)
        )
        Text(
            text = buildAnnotatedString {
                append("Media periodo: ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(average.toString()) // Mock value
                }
            }, // Mock value
            color = Color(0xff333333),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(start = 18.dp, bottom = 6.dp)
                .align(Alignment.Start)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(end = 22.dp, bottom = 10.dp, top = 10.dp) // Adjust padding for proper fitting
        ) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnCollectionSpacing = Dp(0.1f),
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                color = Color(0xff00dada),
                                shape = remember { Shape.rounded(topLeftPercent = 20, topRightPercent = 20) },
                            ),
                        )
                    ),
                    marker = marker,
                    decorations = listOf(
                        rememberHorizontalLine(
                            y = { average.toDouble() },
                            line = rememberLineComponent(
                                color = Color(0xff246EE9),
                                thickness = Dp(2f)
                            )
                        )
                    ),
                    startAxis = rememberStartAxis(
                        line = rememberAxisLineComponent(
                            color = Color.Transparent
                        ),
                        tick = rememberAxisTickComponent(
                            color = Color.Transparent,
                            thickness = Dp(0f)
                        ),
                        guideline = rememberLineComponent(
                            color = colorResource(id = R.color.grayLight)
                        ),
                        label = rememberAxisLabelComponent(
                            color = Color.Black,
                        ),
                        itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
                        valueFormatter = integerValueFormatter
                        ),
                    bottomAxis = rememberBottomAxis(
                        line = rememberAxisLineComponent(
                            color = Color.Transparent
                        ),
                        tick = rememberAxisTickComponent(
                            color = Color.Transparent,
                            thickness = Dp(0f)
                        ),
                        guideline = rememberAxisGuidelineComponent(
                            color = Color.Transparent,
                            thickness = Dp(0f)
                        ),
                        label = rememberAxisLabelComponent(
                            color = Color.Gray,
                            textSize = 8.sp
                            ),
                        valueFormatter = xLabelsFormatter
                    ),
                    horizontalLayout = HorizontalLayout.fullWidth()
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxSize() // Ensure it fills the entire space of the Box
            )
        }
    }
}

fun formatDates(dates: List<String>): CartesianValueFormatter {
    // Define the input and output date formatters
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    val outputFormatter = DateTimeFormatter.ofPattern("dd-MM", Locale.getDefault())

    return CartesianValueFormatter { value, _, _ ->
        val intValue = value.toInt()
        if (intValue in dates.indices) {
            try {
                // Parse the date and format it to "dd-MM"
                val date = LocalDate.parse(dates[intValue], inputFormatter)
                date.format(outputFormatter)
            } catch (e: Exception) {
                // Handle parsing or formatting errors if needed
                ""
            }
        } else {
            ""
        }
    }
}


@Composable
fun LineChart(
    title: String,
    titleColor: Color,
    lineColor: Color,
    values: List<Pair<String, Float>>, // Accept a list of date-weight pairs
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Convert the values into a format suitable for the chart
    val dataMap = values.mapIndexed { index, value -> value.first to value.second }.toMap()

    // Extract the dates from the values
    val dates = values.map { it.first}

        // Compute the minimum of the values
    val minValue = values.minOf { it.second }

    // Compute 80% of the minimum value
    val eightyPercentOfMin = (minValue * 0.8).toInt()

    // Round to the nearest 5 or 0
    val startY = ((eightyPercentOfMin + 4) / 5) * 5

    // Define the circle style for the points
    val pointStyle = rememberShapeComponent(
        color = lineColor,
        shape = Shape.Pill
    )

    // Define the area fill with transparency and adjusted tinterruption
    val areaFill = remember {
        LineCartesianLayer.AreaFill.single(
            fill = Fill(
                DynamicShader.compose(
                    verticalGradient(
                        Color(0xffb3e0e0).toArgb(),
                        android.graphics.Color.TRANSPARENT
                    ),
                    verticalGradient(
                        Color(0xffb3e0e0).toArgb(),
                        android.graphics.Color.TRANSPARENT
                    ),
                    PorterDuff.Mode.LIGHTEN
                )
            ),
            splitY = { 0 }
        )
    }

    // Define the line with the specified color
    val line = rememberLine(
        fill = remember {
            LineCartesianLayer.LineFill.single(fill(Color(0xff00dada)))
        },
        areaFill = areaFill
    )

    // Define the marker with labels for the points
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            textSize = 10.sp,
            color = Color.Black
        ),
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        indicatorSize = 5.dp,
        indicator = {
            pointStyle
        }
    )

    val integerValueFormatter = CartesianValueFormatter { value, _, _ ->
        value.roundToInt().toString()
    }

    LaunchedEffect(values) {
        withContext(Dispatchers.Default) {
            modelProducer.runTransaction {
                lineSeries {
                    series(dataMap.values)
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(top = 15.dp, start = 18.dp, bottom = 6.dp)
                .align(Alignment.Start)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(end = 22.dp, bottom = 10.dp, top = 10.dp) // Adjust padding for proper fitting
        ) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            line
                        ),
                        axisValueOverrider = AxisValueOverrider.fixed(minY = startY.toDouble())
                    ),
                    startAxis = rememberStartAxis(
                        label = rememberAxisLabelComponent(
                            color = Color.Black,
                            margins = Dimensions.of(end = 8.dp),
                            padding = Dimensions.of(6.dp, 2.dp)
                        ),
                        line = null,
                        tick = null,
                        guideline = rememberLineComponent(
                            color = colorResource(id = R.color.grayLight)
                        ),
                        itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 4 }) },
                        valueFormatter = integerValueFormatter
                    ),
                    bottomAxis = rememberBottomAxis(
                        guideline = null,
                        itemPlacer = remember { HorizontalAxis.ItemPlacer.default(addExtremeLabelPadding = true) },
                        valueFormatter = formatDates(dates)
                    ),
                    horizontalLayout = HorizontalLayout.fullWidth(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

