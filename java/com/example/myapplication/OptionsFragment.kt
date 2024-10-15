package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentOptionsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OptionsFragment : Fragment() {

    private lateinit var userDao: UserDao
    private lateinit var weightDao: WeightDao
    private lateinit var binding: FragmentOptionsBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted, update preference
                sharedPreferences.edit().putBoolean("isTrackingEnabled", true).apply()
                Toast.makeText(requireContext(), "Tracking abilitato", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied, show a dialog
                AlertDialog.Builder(requireContext())
                    .setTitle("Permesso richiesto")
                    .setMessage("Per funzionare, il tracciamento in background necessita dell'accesso costante alla posizione.")
                    .setPositiveButton("Autorizza") { _, _ ->
                        // Open app settings to allow permissions manually
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton("Nega") { _, _ ->
                        // Reset switch and show toast
                        binding.toggleTrackingSwitch.isChecked = false
                        Toast.makeText(requireContext(), "Tracking non abilitato", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as? MainActivity)?.updateToolbarTitle("OPZIONI")
        binding = FragmentOptionsBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("ActivityTrackingPrefs", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editWeight = binding.editWeight
        val saveWeightButton = binding.saveWeightButton
        val editStepGoal = binding.editStepGoal
        val saveStepGoalButton = binding.saveStepGoalButton
        val editHeight = binding.editHeight
        val saveHeightButton = binding.saveHeightButton
        val toggleTrackingSwitch = binding.toggleTrackingSwitch

        // Initialize toggle button based on saved preference
        val isTrackingEnabled = sharedPreferences.getBoolean("isTrackingEnabled", false)
        toggleTrackingSwitch.isChecked = isTrackingEnabled

        // Set up listener for toggle button
        toggleTrackingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkBackgroundLocationPermission()
            } else {
                sharedPreferences.edit().putBoolean("isTrackingEnabled", false).apply()
                Toast.makeText(requireContext(), "Tracking disabilitato", Toast.LENGTH_SHORT).show()
            }
        }

        // Save weight
        saveWeightButton.setOnClickListener {
            val weight = editWeight.text.toString().toFloatOrNull()
            if (weight != null) {
                saveWeight(weight)
            }
        }

        // Save step goal
        saveStepGoalButton.setOnClickListener {
            val stepGoal = editStepGoal.text.toString().toIntOrNull()
            if (stepGoal != null) {
                updateStepGoal(stepGoal)
            }
        }

        // Save height
        saveHeightButton.setOnClickListener {
            val height = editHeight.text.toString().toFloatOrNull()
            if (height != null) {
                updateHeight(height)
            }
        }

        lifecycleScope.launch {
            initializeDaos()
            loadUserInfo()
        }
    }

    private fun checkBackgroundLocationPermission() {
        when {
            // If permission is already granted
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                sharedPreferences.edit().putBoolean("isTrackingEnabled", true).apply()
                Toast.makeText(requireContext(), "Tracking abilitato", Toast.LENGTH_SHORT).show()
            }

            // If the user denied permission before but did not check "Don't ask again"
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
                // Show your custom dialog explaining why the permission is necessary
                AlertDialog.Builder(requireContext())
                    .setTitle("Permesso richiesto")
                    .setMessage("Per funzionare, il tracciamento in background necessita dell'accesso costante alla posizione.")
                    .setPositiveButton("Autorizza") { _, _ ->
                        // Request the permission after the user understands why it's needed
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Nega") { _, _ ->
                        binding.toggleTrackingSwitch.isChecked = false
                        Toast.makeText(requireContext(), "Tracking non abilitato", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }

            // If the user previously denied and selected "Don't ask again"
            else -> {
                // Show a dialog directing the user to app settings
                AlertDialog.Builder(requireContext())
                    .setTitle("Permesso richiesto")
                    .setMessage("Per funzionare, il tracciamento in background necessita dell'accesso costante alla posizione.")
                    .setPositiveButton("Apri impostazioni") { _, _ ->
                        // Open the app's settings page
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton("Nega") { _, _ ->
                        binding.toggleTrackingSwitch.isChecked = false
                        Toast.makeText(requireContext(), "Tracking non abilitato", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }

    private suspend fun loadUserInfo() {
        withContext(Dispatchers.IO) {
            val user = userDao.getUserInfo()
            withContext(Dispatchers.Main) {
                binding.weight = String.format("%.1f", user.weight)
                binding.height = String.format("%d", user.height.toInt())
                binding.steps = user.stepGoal.toString()
                binding.userName = user.name
            }
        }
    }

    private fun saveWeight(weight: Float) {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        lifecycleScope.launch(Dispatchers.IO) {
            weightDao.insert(WeightRecord(date = date, weight = weight))
            userDao.updateWeight(weight)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Peso aggiornato correttamente", Toast.LENGTH_SHORT).show()
                binding.editWeight.text?.clear()
                loadUserInfo()
            }
        }
    }

    private fun updateStepGoal(stepGoal: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            userDao.updateStepGoal(stepGoal)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Obiettivo giornaliero aggiornato correttamente", Toast.LENGTH_SHORT).show()
                binding.editStepGoal.text?.clear()
                loadUserInfo()
            }
        }
    }

    private fun updateHeight(height: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            userDao.updateHeight(height)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Altezza aggiornata correttamente", Toast.LENGTH_SHORT).show()
                binding.editHeight.text?.clear()
                loadUserInfo()
            }
        }
    }

    private suspend fun initializeDaos() {
        withContext(Dispatchers.IO) {
            val db = DatabaseSingleton.getDatabase(requireContext())
            userDao = db.userDao
            weightDao = db.weightDao
        }
    }
}
