package com.rishinnovations.isai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rishinnovations.isai.ui.AppViewModel
import com.rishinnovations.isai.ui.IsaiApp
import com.rishinnovations.isai.ui.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The position ticker only runs while the screen is visible, so playback costs no UI work in the background.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = playerViewModel.onUiVisible(true)
            override fun onStop(owner: LifecycleOwner) = playerViewModel.onUiVisible(false)
        })

        setContent { IsaiApp(playerViewModel, appViewModel) }
    }
}
