package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InitialActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the database
        db = DatabaseSingleton.getDatabase(this)

        // Check if there is any user registered
        checkUserAndNavigate()
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
