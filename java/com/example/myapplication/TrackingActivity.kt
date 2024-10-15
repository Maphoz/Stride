package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class TrackingActivity : AppCompatActivity() {

    private val permissionDescriptions = mapOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "Posizione",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Posizione",
        Manifest.permission.ACTIVITY_RECOGNITION to "Attività fisica",
        Manifest.permission.BODY_SENSORS to "Sensore corporeo",
        Manifest.permission.POST_NOTIFICATIONS to "Notifiche"
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if ((permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true )
                && permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
                && permissions[Manifest.permission.BODY_SENSORS] == true){
                // Permissions granted, you may start the tracking service
                startTracking()
            } else {
                val missingPermissions = permissions.filter { !it.value }
                val message = "L'app necessita delle seguenti autorizzazioni per funzionare correttamente: " +
                        missingPermissions.keys.joinToString(", ") { permissionDescriptions[it] ?: it }
                AlertDialog.Builder(this)
                    .setTitle("Autorizzazione richiesta")
                    .setMessage(message)
                    .setPositiveButton("Autorizza") { _, _ ->
                        // Open app settings to allow permissions manually
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton("Chiudi") { _, _ ->
                        finishAffinity()
                    }
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.tracking_activity)

        val toolbar: Toolbar = findViewById(R.id.activity_toolbar)
        setSupportActionBar(toolbar)

        setActivityToolbar()

        checkAndRequestPermissions()
    }

    private fun setActivityToolbar() {
        val activity = intent.getStringExtra("ACTIVITY_TYPE") ?: "walking"
        val activityTitle = when(activity){
            "walking" -> "CAMMINATA"
            "running" -> "CORSA"
            "driving" -> "GUIDA"
            "sitting" -> "SEDUTA"
            else -> {
                "CAMMINATA"
            }
        }

        val toolbar: Toolbar = findViewById(R.id.activity_toolbar)
        val activityText: TextView = toolbar.findViewById(R.id.activityText)
        activityText.text = activityTitle
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED -> {
                // Permissions are already granted
                startTracking()
            }
            else -> {
                // Request permissions
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    private fun startTracking() {
        // Get the intent extras to determine which activity to track
        val activityType = intent.getStringExtra("ACTIVITY_TYPE") ?: "walking"

        // Load the appropriate fragment based on the activity type
        loadTrackingFragment(activityType)
    }

    private fun loadTrackingFragment(activityType: String) {
        val fragment: Fragment = when (activityType) {
            "walking" -> WalkingFragment()
            "running" -> RunningFragment()
            "driving" -> DrivingFragment()
            "sitting" -> SittingFragment()
            else -> WalkingFragment() // Default to walking
        }

        ActivityTrackingController.startActiveTracking()

        // Replace the fragment container with the appropriate fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.trackingFragmentContainer, fragment)
            .commit()
    }
}
