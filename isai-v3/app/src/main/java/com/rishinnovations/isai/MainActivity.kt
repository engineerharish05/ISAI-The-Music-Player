package com.rishinnovations.isai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rishinnovations.isai.ui.ISAIApp
import com.rishinnovations.isai.ui.theme.ISAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ISAITheme { ISAIApp() } }
    }
}
