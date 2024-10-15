package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var heightInput: TextInputEditText
    private lateinit var ageInput: TextInputEditText
    private lateinit var stepsInput: TextInputEditText
    private lateinit var registerButton: MaterialButton

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.registration_activity)

        // Initialize the database
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "user-database"
        ).build()

        // Initialize views
        nameInput = findViewById(R.id.nameInput)
        weightInput = findViewById(R.id.weightInput)
        heightInput = findViewById(R.id.heightInput)
        ageInput = findViewById(R.id.ageInput)
        stepsInput = findViewById(R.id.stepsInput)
        registerButton = findViewById(R.id.registerButton)

        // Set click listener for the register button
        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val name = nameInput.text.toString().trim()
        val weight = weightInput.text.toString().trim()
        val height = heightInput.text.toString().trim()
        val age = ageInput.text.toString().trim()
        val steps = stepsInput.text.toString().trim()

        if (name.isEmpty() || weight.isEmpty() || height.isEmpty() || age.isEmpty() || steps.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val user = User(
            name = name,
            age = age.toInt(),
            gender = "Not specified",  // Assuming gender is not being asked
            weight = weight.toFloat(),
            height = height.toFloat(),
            stepGoal = steps.toInt()
        )

        // Save user to the database
        saveUser(user)
    }

    private fun saveUser(user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                db.userDao.insertUser(user)
                db.weightDao.insert(WeightRecord(
                    date = date,
                    weight = user.weight
                ))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistrationActivity, "Registrazione avvenuta con successo", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()  // Navigate to the main activity
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistrationActivity, "Errore avvenuto nella registrazione", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()  // Close the registration activity
    }
}
