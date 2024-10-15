package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize the database
        db = DatabaseSingleton.getDatabase(this)

        // Load the animations
        val slideUpAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        // Start the animations
        findViewById<ImageView>(R.id.logoImageView).startAnimation(slideUpAnimation)
        findViewById<TextView>(R.id.strideTextView).startAnimation(fadeInAnimation)

        // Set a delay for the duration of the animations (3 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAndNavigate()
        }, 300)
    }

    private fun checkUserAndNavigate() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userExists = db.userDao.getUserCount() > 0
            withContext(Dispatchers.Main) {
                if (!userExists) {
                    navigateToRegistration()
                } else {
                    navigateToMain()
                }
            }
        }
    }

    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
