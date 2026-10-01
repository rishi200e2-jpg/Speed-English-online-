package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainQuizApp
import com.example.ui.QuizViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Retrieve the shared QuizViewModel at the Activity level
    val quizViewModel = try {
      ViewModelProvider(this)[QuizViewModel::class.java]
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Failed initializing QuizViewModel: ${e.message}", e)
      ViewModelProvider(this)[QuizViewModel::class.java]
    }

    // Implement a custom OnBackPressedCallback to handle physical/gesture back navigation smoothly
    val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        try {
          if (quizViewModel.isCurrentUserBlocked.value) {
            // If account is suspended, do not navigate back to content screens
            finish()
            return
          }

          val currentScreen = quizViewModel.currentScreen.value
          if (currentScreen is com.example.ui.Screen.Home || currentScreen is com.example.ui.Screen.Splash) {
            // Root screens: disable callback and execute system exit / minimize behavior directly
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
            return
          }

          // Trigger back ad with robust fallback before navigation
          com.example.ads.RewardedInterstitialAdManager.showBackNavigationAd(this@MainActivity) {
            val handled = quizViewModel.navigateBack()
            if (!handled) {
              isEnabled = false
              onBackPressedDispatcher.onBackPressed()
              isEnabled = true
            }
          }
        } catch (e: Exception) {
          android.util.Log.e("MainActivity", "Error in onBackPressed: ${e.message}", e)
          finish()
        }
      }
    }
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    // Preload Rewarded and Interstitial ads in advance
    com.example.ads.RewardedInterstitialAdManager.preloadAll(this)

    setContent {
      val isDarkTheme by quizViewModel.isDarkTheme.collectAsState()
      MyApplicationTheme(darkTheme = isDarkTheme) {
        MainQuizApp(
          viewModel = quizViewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      com.example.ads.NativeAdManager.destroyAll()
      com.example.ads.RewardedInterstitialAdManager.destroyAll()
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Error destroying ads in onDestroy: ${e.message}", e)
    }
  }
}
