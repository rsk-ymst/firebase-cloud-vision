package com.example.firebaseauth

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class NewMessageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        supportActionBar?.title = "Select User"
    }
}