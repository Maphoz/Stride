package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SittingFragment : Fragment() {

    private lateinit var textViewMessage: TextView
    private lateinit var textViewTime: TextView
    private lateinit var imageViewEmoji: ImageView
    private lateinit var buttonFinish: Button
    private lateinit var weightDao: WeightDao
    private var weightKg: Float = 0f

    private var startTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    private val runnable: Runnable = object : Runnable {
        override fun run() {
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            textViewTime.text = formatElapsedTime(elapsedTime)
            updateMessageAndIcon(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sitting, container, false)
        textViewMessage = view.findViewById(R.id.textViewMessage)
        textViewTime = view.findViewById(R.id.textViewTime)
        imageViewEmoji = view.findViewById(R.id.imageViewEmoji)
        buttonFinish = view.findViewById(R.id.buttonFinish)

        buttonFinish.setOnClickListener {
            finishSitting()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val serviceIntent = Intent(requireContext(), SittingService::class.java)
        ContextCompat.startForegroundService(requireContext(), serviceIntent)

        lifecycleScope.launch {
            initializeDaos()
            initializeWeight()
        }
    }

    private suspend fun initializeDaos(){
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            weightDao = db.weightDao
        }
    }

    override fun onResume() {
        super.onResume()
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
        handler.post(runnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private suspend fun initializeWeight(){
        withContext(Dispatchers.IO) {
            weightKg = weightDao.getCurrentWeight()
        }
    }

    private fun updateMessageAndIcon(elapsedTime: Long) {

        when {
            elapsedTime < 1800 -> {
                textViewMessage.text = "Tempo di un meritato riposo"
                imageViewEmoji.setImageResource(R.drawable.ic_rest)
                textViewMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }
            elapsedTime < 3600 -> {
                textViewMessage.text = "Forse è tempo di alzarsi e fare stretching"
                imageViewEmoji.setImageResource(R.drawable.ic_stretch)
                textViewMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryColor))
            }
            elapsedTime < 5400 -> {
                textViewMessage.text = "Alziamoci a fare qualcosa!"
                imageViewEmoji.setImageResource(R.drawable.ic_getup)
                textViewMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            }
            else -> {
                textViewMessage.text = "Non ti sarai mica perso al telefono?"
                imageViewEmoji.setImageResource(R.drawable.ic_phone_addiction)
                textViewMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            }
        }
    }

    private fun finishSitting() {
        val duration = (System.currentTimeMillis() - startTime) / 1000
        val caloriesBurned = 1.3 * (duration / 3600f) * weightKg

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(System.currentTimeMillis())

        // Format the calories burned using Locale.US
        val formattedCaloriesBurned = String.format(Locale.US, "%.1f", caloriesBurned).toFloat()

        val activity = Activity(
            date = currentDate,
            duration = duration,
            caloriesBurned = formattedCaloriesBurned,
            type = "sitting",
            distance = 0f,  // No distance for sitting
            steps = 0       // No steps for sitting
        )

        val bundle = Bundle().apply {
            putParcelable("activity", activity)
        }

        val intent = Intent(requireContext(), SittingService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()

        val saveFragment = SaveActivityFragment()
        saveFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.trackingFragmentContainer, saveFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy(){
        super.onDestroy()
        val intent = Intent(requireContext(), SittingService::class.java)
        requireContext().stopService(intent)
        ActivityTrackingController.stopActiveTracking()
    }
}
