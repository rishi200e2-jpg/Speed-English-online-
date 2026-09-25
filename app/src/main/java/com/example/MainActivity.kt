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
    val quizViewModel = ViewModelProvider(this)[QuizViewModel::class.java]

    // Implement a custom OnBackPressedCallback to handle physical/gesture back navigation smoothly
    val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (quizViewModel.isCurrentUserBlocked.value) {
          // If account is suspended, do not navigate back to content screens
          finish()
          return
        }
        val handled = quizViewModel.navigateBack()
        if (!handled) {
          // If we are already on Home/Splash, disable this callback and run the default exit behavior
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
          isEnabled = true
        }
      }
    }
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

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
}
