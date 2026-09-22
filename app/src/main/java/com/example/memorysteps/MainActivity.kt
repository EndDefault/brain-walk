package com.example.memorysteps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.memorysteps.ui.MemoryStepsApp
import com.example.memorysteps.ui.theme.MemoryStepsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoryStepsTheme {
                MemoryStepsApp()
            }
        }
    }
}
