package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class QuizApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Setup Global Uncaught Exception Handler to prevent process crash
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("QuizApplication", "Uncaught exception intercepted on thread ${thread.name}: ${throwable.message}", throwable)
            // Catch and log uncaught runtime exceptions without forcing process exit
            if (throwable is java.lang.OutOfMemoryError) {
                System.exit(1)
            }
        }

        // 2. Initialize Firebase safely
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("QuizApplication", "Firebase initialized successfully in Application.onCreate")
            }
        } catch (e: Exception) {
            Log.e("QuizApplication", "Firebase auto-initialization failed: ${e.message}", e)
        }
    }

    private fun isFatalLaunchException(throwable: Throwable): Boolean {
        val msg = throwable.message ?: ""
        return msg.contains("FirebaseApp", ignoreCase = true) ||
               msg.contains("Room", ignoreCase = true) ||
               msg.contains("SQLite", ignoreCase = true)
    }
}
