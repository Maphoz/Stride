package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var navController: NavController

    private val permissionDescriptions = mapOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "Posizione",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Posizione",
        Manifest.permission.ACTIVITY_RECOGNITION to "Attività fisica",
        Manifest.permission.BODY_SENSORS to "Sensore corporeo",
        Manifest.permission.POST_NOTIFICATIONS to "Notifiche"
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == false ||
                permissions[Manifest.permission.ACTIVITY_RECOGNITION] == false ||
                permissions[Manifest.permission.BODY_SENSORS] == false ||
                permissions[Manifest.permission.POST_NOTIFICATIONS] == false
            ) {
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
                    .show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {

            }
            else -> {
                // Request permissions
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        toolbar = findViewById(R.id.custom_toolbar)
        toolbarTitle = findViewById(R.id.toolbar_section_title)
        setSupportActionBar(toolbar)

        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_progress, R.id.navigation_filter, R.id.navigation_options
            )
        )
        setupActionBarWithNavController(this, navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        bottomNavItemChangeListener(navView)

        // Check and request permissions
        checkAndRequestPermissions()

        onBackButtonPressed {
            // Attempt to handle back press via NavController
            if (!navController.navigateUp()) {
                // If no more fragments to pop, return false to allow default back press behavior
                false
            } else {
                // Return true as back press was handled
                true
            }
        }
    }

    private fun bottomNavItemChangeListener(navView: BottomNavigationView) {
        navView.setOnItemSelectedListener { item ->
            if (item.itemId != navView.selectedItemId) {
                navController.popBackStack(item.itemId, true)
                navController.navigate(item.itemId)
            }
            true
        }
    }

    fun onBackButtonPressed(callback: (() -> Boolean)) {
        (this as? FragmentActivity)?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!callback()) {
                    remove()
                    performBackPress()
                }
            }
        })
    }

    fun performBackPress() {
        (this as? FragmentActivity)?.onBackPressedDispatcher?.onBackPressed()
    }

    fun updateToolbarTitle(title: String) {
        toolbarTitle.text = title
    }
}
