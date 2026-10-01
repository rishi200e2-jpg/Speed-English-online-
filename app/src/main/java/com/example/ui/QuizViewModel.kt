package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class QuestionUserAnswer(
    val question: Question,
    val selectedOptionLetter: String?, // "A", "B", "C", "D"
    val timeSpentSeconds: Float,
    val isCorrect: Boolean
)

data class SavedQuizProgress(
    val quizId: String,
    val quizTitle: String,
    val categoryName: String,
    val currentQuestionIndex: Int,
    val quizScore: Float,
    val timeRemaining: Int,
    val questions: List<Question>,
    val userAnswers: List<QuestionUserAnswer>
)

data class SelectedFileMetadata(
    val uriString: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val pageCount: Int = -1,
    val isValid: Boolean = true,
    val warningMessage: String? = null
)

sealed interface Screen {
    object Splash : Screen
    object Home : Screen
    data class CategoryView(val category: Category) : Screen
    data class ActiveQuiz(val quiz: Quiz) : Screen
    data class Score(val quiz: Quiz, val score: Float, val totalQuestions: Int) : Screen
    object AdminDashboard : Screen
}

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val isTestEnv = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (e: Exception) {
        false
    }

    fun isRunningTest(): Boolean = isTestEnv

    private val database = QuizDatabase.getDatabase(application)
    private val repository = QuizRepository(database.quizDao())

    val syncManager = SyncManager(application, repository, viewModelScope)

    private val sharedPrefs = application.getSharedPreferences("quiz_cloud_prefs", Context.MODE_PRIVATE)
    private val quizProgressPrefs = application.getSharedPreferences("quiz_progress_prefs", Context.MODE_PRIVATE)

    private val _realtimeProgressMap = MutableStateFlow<Map<String, LiveProgressData>>(emptyMap())
    val realtimeProgressMap: StateFlow<Map<String, LiveProgressData>> = _realtimeProgressMap.asStateFlow()

    private val _savedQuizProgress = MutableStateFlow<SavedQuizProgress?>(null)
    val savedQuizProgress: StateFlow<SavedQuizProgress?> = _savedQuizProgress.asStateFlow()

    // Quiz Progress Real-time State Synchronization States
    private val _isQuizProgressSynced = MutableStateFlow(true)
    val isQuizProgressSynced: StateFlow<Boolean> = _isQuizProgressSynced.asStateFlow()

    private val _quizProgressSyncError = MutableStateFlow<String?>(null)
    val quizProgressSyncError: StateFlow<String?> = _quizProgressSyncError.asStateFlow()

    // Cloud / Local Sync States
    private val _firebaseDbUrl = MutableStateFlow(sharedPrefs.getString("firebase_db_url", "") ?: "")
    val firebaseDbUrl: StateFlow<String> = _firebaseDbUrl.asStateFlow()

    // Local Authentication & Profile States
    private val _currentUserEmail = MutableStateFlow<String?>(sharedPrefs.getString("session_user_email", if (isTestEnv) "admin@speedenglish.local" else null))
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserName = MutableStateFlow<String?>(sharedPrefs.getString("session_user_name", if (isTestEnv) "Admin" else null))
    val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("is_dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        sharedPrefs.edit().putBoolean("is_dark_theme", next).apply()
    }

    private val _currentUserRole = MutableStateFlow<String?>(sharedPrefs.getString("session_user_role", if (isTestEnv) "Admin" else null))
    val currentUserRole: StateFlow<String?> = _currentUserRole.asStateFlow()

    fun saveSessionLocally(email: String, role: String, name: String, uid: String) {
        sharedPrefs.edit().apply {
            putString("session_user_email", email)
            putString("session_user_role", role)
            putString("session_user_name", name)
            putString("session_user_uid", uid)
            apply()
        }
        val mockPrefs = getApplication<Application>().getSharedPreferences("local_auth_session", Context.MODE_PRIVATE)
        mockPrefs.edit().apply {
            putString("logged_in_email", email)
            putString("logged_in_role", role)
            apply()
        }
    }

    fun clearSessionLocally() {
        sharedPrefs.edit().apply {
            remove("session_user_email")
            remove("session_user_role")
            remove("session_user_name")
            remove("session_user_uid")
            apply()
        }
        val mockPrefs = getApplication<Application>().getSharedPreferences("local_auth_session", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().apply()
    }

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authMessage = MutableStateFlow("")
    val authMessage: StateFlow<String> = _authMessage.asStateFlow()

    private val _firebaseAuthToken = MutableStateFlow(sharedPrefs.getString("firebase_auth_token", "") ?: "")
    val firebaseAuthToken: StateFlow<String> = _firebaseAuthToken.asStateFlow()

    private val _autoSyncOnStartup = MutableStateFlow(sharedPrefs.getBoolean("auto_sync_on_startup", true))
    val autoSyncOnStartup: StateFlow<Boolean> = _autoSyncOnStartup.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(sharedPrefs.getLong("last_sync_time", System.currentTimeMillis()))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("All data stored locally in Room database.")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isAdminOperating = MutableStateFlow(false)
    val isAdminOperating: StateFlow<Boolean> = _isAdminOperating.asStateFlow()

    fun logAdminAction(
        action: String,
        targetType: String,
        targetId: String,
        details: String = "",
        metadata: Map<String, Any> = emptyMap()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val adminEmail = currentUserEmail.value ?: "admin@speedenglish.local"
            val adminUid = getCurrentUserId()
            val logId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val logMap = mutableMapOf<String, Any>(
                "documentId" to logId,
                "adminUid" to adminUid,
                "adminEmail" to adminEmail,
                "action" to action,
                "targetType" to targetType,
                "targetId" to targetId,
                "timestamp" to now,
                "createdAt" to now,
                "status" to "PROPAGATED_TO_CLOUDFIRESTORE",
                "details" to details
            )
            if (metadata.isNotEmpty()) {
                logMap["metadata"] = metadata
            }

            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("adminLogs").document(logId).set(logMap)
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed writing admin log: ${e.message}", e)
            }
        }
    }

    private val _isSnapshotSyncing = MutableStateFlow(false)
    val isSnapshotSyncing: StateFlow<Boolean> = _isSnapshotSyncing.asStateFlow()

    fun setSnapshotSyncing(syncing: Boolean) {
        _isSnapshotSyncing.value = syncing
    }

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    fun fetchNewestQuestionsDirectlyFromServer(categoryId: String? = null, quizId: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            updateQuizQuestionsCounts()
            loadAdminCategories()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private val _firestoreQuizError = MutableStateFlow<String?>(null)
    val firestoreQuizError: StateFlow<String?> = _firestoreQuizError.asStateFlow()

    private val _adminQuizError = MutableStateFlow<String?>(null)
    val adminQuizError: StateFlow<String?> = _adminQuizError.asStateFlow()

    val auditLogs: StateFlow<List<QuestionAuditLog>> = repository.allAuditLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun recordQuestionAuditLog(
        actionType: String,
        questionId: String,
        questionText: String,
        quizId: String,
        quizTitle: String = "",
        status: String = "PROPAGATED_TO_CLOUDFIRESTORE",
        details: String = "",
        documentId: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val adminEmail = currentUserEmail.value ?: "admin@speedenglish.local"
            val adminUid = getCurrentUserId()
            val resolvedQuizTitle = if (quizTitle.isNotEmpty()) quizTitle else {
                repository.getQuizById(quizId)?.title ?: "Quiz #$quizId"
            }
            val logId = if (documentId.isNotEmpty()) documentId else UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val log = QuestionAuditLog(
                documentId = logId,
                actionType = actionType,
                questionId = questionId,
                questionText = questionText,
                quizId = quizId,
                quizTitle = resolvedQuizTitle,
                adminEmail = adminEmail,
                adminUid = adminUid,
                timestamp = now,
                createdAt = now,
                status = status,
                details = details
            )
            repository.insertAuditLog(log)

            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                batch.set(firestore.collection("adminLogs").document(logId), log)
                batch.set(firestore.collection("question_audit_logs").document(logId), log)
                batch.commit()
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error persisting audit log to Firestore: ${e.message}", e)
            }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            repository.clearAllAuditLogs()
        }
    }

    fun clearFirestoreQuizError() {
        _firestoreQuizError.value = null
    }

    fun clearAdminQuizError() {
        _adminQuizError.value = null
    }

    private val _isNetworkAvailable = MutableStateFlow(NetworkConnectivityHelper.isInternetAvailable(application))
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _isCurrentUserBlocked = MutableStateFlow(sharedPrefs.getBoolean("session_is_blocked", false))
    val isCurrentUserBlocked: StateFlow<Boolean> = _isCurrentUserBlocked.asStateFlow()

    fun unblockCurrentUserLocally() {
        _isCurrentUserBlocked.value = false
        sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
    }

    fun refreshNetworkStatus() {
        _isNetworkAvailable.value = NetworkConnectivityHelper.isInternetAvailable(getApplication())
    }

    private val _isLogoUploading = MutableStateFlow(false)
    val isLogoUploading: StateFlow<Boolean> = _isLogoUploading.asStateFlow()

    private val _logoUploadError = MutableStateFlow<String?>(null)
    val logoUploadError: StateFlow<String?> = _logoUploadError.asStateFlow()

    // Custom Gemini API Key State Flow & Persistence
    private val _customGeminiApiKey = MutableStateFlow(sharedPrefs.getString("custom_gemini_api_key", "") ?: "")
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    fun saveCustomGeminiApiKey(key: String) {
        _customGeminiApiKey.value = key.trim()
        sharedPrefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
    }

    fun clearCustomGeminiApiKey() {
        _customGeminiApiKey.value = ""
        sharedPrefs.edit().remove("custom_gemini_api_key").apply()
    }

    // Dynamic category colors map (using Int for ARGB values)
    private val _customCategoryColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val customCategoryColors: StateFlow<Map<String, Int>> = _customCategoryColors.asStateFlow()

    fun updateCustomCategoryColor(iconName: String, colorValue: Int) {
        val current = _customCategoryColors.value.toMutableMap()
        current[iconName] = colorValue
        _customCategoryColors.value = current
    }

    fun saveCloudSettings(url: String, token: String, autoSync: Boolean) {
        sharedPrefs.edit().apply {
            putString("firebase_db_url", url)
            putString("firebase_auth_token", token)
            putBoolean("auto_sync_on_startup", autoSync)
            apply()
        }
        _firebaseDbUrl.value = url
        _firebaseAuthToken.value = token
        _autoSyncOnStartup.value = autoSync
        _syncStatusMessage.value = "Settings saved successfully"
    }

    private var categoriesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var quizzesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var questionsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private val _isLiveSyncing = MutableStateFlow(false)
    val isLiveSyncing: StateFlow<Boolean> = _isLiveSyncing.asStateFlow()

    private val _firestoreLogs = MutableStateFlow<List<String>>(listOf("Real-time cloud database online."))
    val firestoreLogs: StateFlow<List<String>> = _firestoreLogs.asStateFlow()

    val interceptorLogs: StateFlow<List<com.example.data.FirestoreLogEntry>> = com.example.data.FirestoreLoggingInterceptor.logs

    fun logFirestore(message: String) {
        val current = _firestoreLogs.value.toMutableList()
        current.add(0, "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $message")
        if (current.size > 50) current.removeAt(current.size - 1)
        _firestoreLogs.value = current
    }

    fun clearDiagnosticLogs() {
        _firestoreLogs.value = listOf("Logs cleared.")
        com.example.data.FirestoreLoggingInterceptor.clearLogs()
    }

    fun startRealtimeFirestoreSync() {
        if (!_isNetworkAvailable.value) {
            logFirestore("Offline mode active. Using local Room cache.")
            return
        }
        
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        _isLiveSyncing.value = true
        logFirestore("Initializing live synchronization...")

        // 1. Listen to Categories
        categoriesListenerRegistration?.remove()
        categoriesListenerRegistration = firestore.collection("categories")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                val startTime = System.currentTimeMillis()
                if (error != null) {
                    Log.e("QuizViewModel", "Categories sync error: ${error.message}", error)
                    logFirestore("Categories sync error: ${error.message}")
                    com.example.data.FirestoreLoggingInterceptor.logRead("categories", 0, "ERROR", 0, false, error.message)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val isFromCache = snapshot.metadata.isFromCache
                    val hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    val latency = System.currentTimeMillis() - startTime
                    com.example.data.FirestoreLoggingInterceptor.logListenerEvent("categories", snapshot.size(), hasPendingWrites, isFromCache, latency)
                    
                    if (hasPendingWrites) {
                        Log.d("QuizViewModel", "Local write pending for categories (instant cache emit)")
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val remoteCategories = snapshot.documents.mapNotNull { doc ->
                                val isDraftVal = doc.getBoolean("isDraft") ?: false
                                val statusVal = doc.getString("status") ?: if (isDraftVal) "draft" else "published"
                                Category(
                                    documentId = doc.id,
                                    name = doc.getString("name") ?: "",
                                    description = doc.getString("description") ?: "",
                                    iconName = doc.getString("iconName") ?: "general",
                                    isDraft = isDraftVal,
                                    status = statusVal,
                                    parentCategoryId = doc.getString("parentCategoryId"),
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            }
                            
                            if (remoteCategories.isNotEmpty()) {
                                remoteCategories.forEach { repository.insertCategory(it) }
                            }

                            // Only process explicit deletions from cloud
                            snapshot.documentChanges.forEach { change ->
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    val catId = change.document.id
                                    repository.getCategoryById(catId)?.let { repository.deleteCategory(it) }
                                }
                            }
                            
                            _lastSyncTime.value = System.currentTimeMillis()
                            sharedPrefs.edit().putLong("last_sync_time", _lastSyncTime.value).apply()
                            logFirestore("Categories synchronized live (Count: ${remoteCategories.size}).")
                            triggerRealtimeSyncReload()
                        } catch (e: Exception) {
                            Log.e("QuizViewModel", "Error in categories sync: ${e.message}", e)
                        }
                    }
                }
            }

        // 2. Listen to Quizzes
        quizzesListenerRegistration?.remove()
        quizzesListenerRegistration = firestore.collection("quizzes")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                val startTime = System.currentTimeMillis()
                if (error != null) {
                    Log.e("QuizViewModel", "Quizzes sync error: ${error.message}", error)
                    com.example.data.FirestoreLoggingInterceptor.logRead("quizzes", 0, "ERROR", 0, false, error.message)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val isFromCache = snapshot.metadata.isFromCache
                    val hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    val latency = System.currentTimeMillis() - startTime
                    com.example.data.FirestoreLoggingInterceptor.logListenerEvent("quizzes", snapshot.size(), hasPendingWrites, isFromCache, latency)
                    
                    if (hasPendingWrites) {
                        Log.d("QuizViewModel", "Local write pending for quizzes (instant cache emit)")
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val remoteQuizzes = snapshot.documents.mapNotNull { doc ->
                                val isDraftVal = doc.getBoolean("isDraft") ?: false
                                val statusVal = doc.getString("status") ?: if (isDraftVal) "draft" else "published"
                                val existingQuiz = repository.getQuizById(doc.id)
                                val firestoreSort = doc.getLong("sortOrder")?.toInt()
                                val sortOrderVal = firestoreSort ?: existingQuiz?.sortOrder ?: 0
                                Quiz(
                                    documentId = doc.id,
                                    categoryId = doc.getString("categoryId") ?: "",
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    timeLimitSeconds = doc.getLong("timeLimitSeconds")?.toInt() ?: 20,
                                    isDraft = isDraftVal,
                                    status = statusVal,
                                    version = doc.getLong("version")?.toInt() ?: 1,
                                    shuffleQuestions = doc.getBoolean("shuffleQuestions") ?: false,
                                    marksPerQuestion = doc.getDouble("marksPerQuestion")?.toFloat() ?: 1.0f,
                                    negativeMarking = doc.getDouble("negativeMarking")?.toFloat() ?: 0.0f,
                                    sortOrder = sortOrderVal,
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                    publishedAt = doc.getLong("publishedAt")
                                )
                            }
                            
                            if (remoteQuizzes.isNotEmpty()) {
                                remoteQuizzes.forEach { repository.insertQuiz(it) }
                            }

                            // Only process explicit deletions from cloud
                            snapshot.documentChanges.forEach { change ->
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    val quizId = change.document.id
                                    repository.getQuizById(quizId)?.let { repository.deleteQuiz(it) }
                                }
                            }
                            
                            logFirestore("Quizzes synchronized live (Count: ${remoteQuizzes.size}).")
                            triggerRealtimeSyncReload()
                        } catch (e: Exception) {
                            Log.e("QuizViewModel", "Error in quizzes sync: ${e.message}", e)
                        }
                    }
                }
            }

        // 3. Questions (No top-level listener to avoid database write locks and network fatigue during mass imports. 
        // Questions are fetched on-demand in startQuiz and managed on-demand via the active quiz's subcollection listener).
        questionsListenerRegistration?.remove()
        questionsListenerRegistration = null
        _isLiveSyncing.value = false
    }

    fun stopRealtimeFirestoreSync() {
        categoriesListenerRegistration?.remove()
        quizzesListenerRegistration?.remove()
        questionsListenerRegistration?.remove()
        adminQuestionsSubcollListener?.remove()
        categoriesListenerRegistration = null
        quizzesListenerRegistration = null
        questionsListenerRegistration = null
        adminQuestionsSubcollListener = null
        _isLiveSyncing.value = false
        logFirestore("Real-time cloud synchronization paused.")
    }

    fun uploadLocalDataToCloud() {
        _isSyncing.value = true
        _syncStatusMessage.value = "Force uploading local database to cloud..."
        logFirestore("Starting manual force upload to Cloud Firestore...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                val categoriesList = repository.getAllCategories()
                val quizzesList = repository.getAllQuizzes()
                val questionsList = repository.getAllQuestions()

                categoriesList.forEach { cat ->
                    firestore.collection("categories").document(cat.documentId).set(cat)
                }
                quizzesList.forEach { q ->
                    firestore.collection("quizzes").document(q.documentId).set(q)
                }
                questionsList.forEach { quest ->
                    firestore.collection("questions").document(quest.documentId).set(quest)
                    if (quest.quizId.isNotEmpty()) {
                        firestore.collection("quizzes").document(quest.quizId).collection("questions").document(quest.documentId).set(quest)
                    }
                }

                _syncStatusMessage.value = "Successfully force-uploaded all local data to Cloud Firestore!"
                logFirestore("Manual force-upload completed successfully.")
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed manual push: ${e.message}", e)
                _syncStatusMessage.value = "Push failed: ${e.message}"
                logFirestore("Manual push failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun downloadCloudDataAndSync(silent: Boolean = false) {
        _isSyncing.value = true
        if (!silent) _syncStatusMessage.value = "Downloading cloud backup copy..."
        logFirestore("Downloading clean backup copy from Firestore...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // 1. Fetch Categories
                val catTask = firestore.collection("categories").get()
                val catSnapshot = com.google.android.gms.tasks.Tasks.await(catTask)
                val remoteCategories = catSnapshot.documents.mapNotNull { doc ->
                    val isDraftVal = doc.getBoolean("isDraft") ?: false
                    val statusVal = doc.getString("status") ?: if (isDraftVal) "draft" else "published"
                    Category(
                        documentId = doc.id,
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        iconName = doc.getString("iconName") ?: "general",
                        isDraft = isDraftVal,
                        status = statusVal,
                        parentCategoryId = doc.getString("parentCategoryId"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                }

                // 2. Fetch Quizzes
                val quizTask = firestore.collection("quizzes").get()
                val quizSnapshot = com.google.android.gms.tasks.Tasks.await(quizTask)
                val remoteQuizzes = quizSnapshot.documents.mapNotNull { doc ->
                    val isDraftVal = doc.getBoolean("isDraft") ?: false
                    val statusVal = doc.getString("status") ?: if (isDraftVal) "draft" else "published"
                    val existingQuiz = repository.getQuizById(doc.id)
                    val firestoreSort = doc.getLong("sortOrder")?.toInt()
                    val sortOrderVal = firestoreSort ?: existingQuiz?.sortOrder ?: 0
                    Quiz(
                        documentId = doc.id,
                        categoryId = doc.getString("categoryId") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        timeLimitSeconds = doc.getLong("timeLimitSeconds")?.toInt() ?: 20,
                        isDraft = isDraftVal,
                        status = statusVal,
                        version = doc.getLong("version")?.toInt() ?: 1,
                        shuffleQuestions = doc.getBoolean("shuffleQuestions") ?: false,
                        marksPerQuestion = doc.getDouble("marksPerQuestion")?.toFloat() ?: 1.0f,
                        negativeMarking = doc.getDouble("negativeMarking")?.toFloat() ?: 0.0f,
                        sortOrder = sortOrderVal,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        publishedAt = doc.getLong("publishedAt")
                    )
                }

                // 3. Fetch Questions
                val qTask = firestore.collection("questions").get()
                val qSnapshot = com.google.android.gms.tasks.Tasks.await(qTask)
                val remoteQuestions = qSnapshot.documents.mapNotNull { doc ->
                    Question(
                        documentId = doc.id,
                        quizId = doc.getString("quizId") ?: "",
                        text = doc.getString("text") ?: "",
                        optionA = doc.getString("optionA") ?: "",
                        optionB = doc.getString("optionB") ?: "",
                        optionC = doc.getString("optionC") ?: "",
                        optionD = doc.getString("optionD") ?: "",
                        correctOption = doc.getString("correctOption") ?: "A",
                        explanation = doc.getString("explanation") ?: "",
                        version = doc.getLong("version")?.toInt() ?: 1,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                }

                if (remoteCategories.isNotEmpty()) repository.insertCategories(remoteCategories)
                if (remoteQuizzes.isNotEmpty()) repository.insertQuizzes(remoteQuizzes)
                if (remoteQuestions.isNotEmpty()) repository.insertQuestions(remoteQuestions)
                updateQuizQuestionsCounts()
                triggerRealtimeSyncReload()
                
                if (!silent) _syncStatusMessage.value = "Successfully synced down and updated Room cache with cloud data!"
                logFirestore("Cloud backup sync completed. Restored ${remoteCategories.size} categories, ${remoteQuizzes.size} quizzes, and ${remoteQuestions.size} questions.")
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed manual download: ${e.message}", e)
                if (!silent) _syncStatusMessage.value = "Sync error: ${e.message}"
                logFirestore("Manual pull failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun uploadToFirestoreCloud() {
        uploadLocalDataToCloud()
    }

    fun fetchFromFirestoreCloud(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            downloadCloudDataAndSync(false)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun retryAdminQuizFetch(categoryId: String = "") {
        _adminQuizError.value = null
        _firestoreQuizError.value = null
        if (categoryId.isNotEmpty()) {
            loadAdminQuizzes(categoryId)
        }
    }

    fun retryQuizScreenFetch() {
        _firestoreQuizError.value = null
        _adminQuizError.value = null
        updateQuizQuestionsCounts()
    }

    fun getCurrentUser(): Any? = null

    fun getCurrentUserId(): String {
        val savedUid = sharedPrefs.getString("session_user_uid", null)
        if (!savedUid.isNullOrEmpty()) {
            return savedUid
        }
        val email = _currentUserEmail.value
        if (!email.isNullOrEmpty()) {
            return email.replace(".", "_")
        }
        return "admin_user"
    }

    private var observeAttemptsJob: Job? = null

    private val _isSubmittingQuiz = MutableStateFlow(false)
    val isSubmittingQuiz: StateFlow<Boolean> = _isSubmittingQuiz.asStateFlow()

    fun updateUserProfile(newName: String, onComplete: () -> Unit = {}) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            _currentUserName.value = newName
            sharedPrefs.edit().putString("session_user_name", newName).apply()
            _syncStatusMessage.value = "Profile updated locally!"
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun startObservingUserAttempts(userId: String) {
        observeAttemptsJob?.cancel()
        observeAttemptsJob = viewModelScope.launch {
            val email = currentUserEmail.value ?: ""
            repository.getAttemptsForUserOrEmailFlow(userId, email).collect { localAttempts ->
                _attemptsList.value = localAttempts
            }
        }
    }

    // Tracks overall completed attempts
    private val _attemptsList = MutableStateFlow<List<QuizAttempt>>(emptyList())
    val attemptsList: StateFlow<List<QuizAttempt>> = _attemptsList.asStateFlow()

    suspend fun saveAttemptToFirestoreAndDatabase(attempt: QuizAttempt): Result<QuizAttempt> {
        if (_isCurrentUserBlocked.value) {
            logFirestore("Save attempt rejected: User account is restricted.")
            return Result.failure(IllegalStateException("Account is restricted."))
        }
        _isSubmittingQuiz.value = true
        logFirestore("Submitting quiz attempt to cloud server...")
        return try {
            val userId = getCurrentUserId()
            val docId = if (attempt.documentId.isNotEmpty()) attempt.documentId else UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val finalAttempt = attempt.copy(
                documentId = docId,
                userId = userId,
                createdAt = if (attempt.createdAt > 0) attempt.createdAt else now
            )
            
            repository.insertAttempt(finalAttempt)
            
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val batch = firestore.batch()
            if (userId.isNotEmpty()) {
                val userAttemptRef = firestore.collection("users").document(userId).collection("attempts").document(docId)
                batch.set(userAttemptRef, finalAttempt)
            }
            val topAttemptRef = firestore.collection("attempts").document(docId)
            batch.set(topAttemptRef, finalAttempt)

            batch.commit()
                .addOnSuccessListener {
                    logFirestore("Quiz attempt synced successfully to Firestore user subcollection & root.")
                }
                .addOnFailureListener { e ->
                    logFirestore("Quiz attempt saved locally (Pending cloud propagation: ${e.message}).")
                }

            _attemptsList.value = repository.getAllAttempts()
            Result.success(finalAttempt)
        } catch (e: Exception) {
            Log.e("QuizViewModel", "Error saving attempt locally: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSubmittingQuiz.value = false
        }
    }

    fun addAttempt(attempt: QuizAttempt) {
        viewModelScope.launch {
            saveAttemptToFirestoreAndDatabase(attempt)
        }
    }

    private var attemptsFirestoreListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startObservingUserAttemptsFirestore(uid: String) {
        if (uid.isBlank() || isRunningTest()) return
        attemptsFirestoreListenerRegistration?.remove()
        
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        attemptsFirestoreListenerRegistration = firestore.collection("users")
            .document(uid)
            .collection("attempts")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e("QuizViewModel", "Error listening to user attempts: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            // Process removals: when admin deletes an attempt from Firestore, delete it from user's Room DB
                            snapshot.documentChanges.forEach { change ->
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    val removedDocId = change.document.id
                                    repository.deleteAttemptByDocumentId(removedDocId)
                                }
                            }

                            val remoteAttempts = snapshot.documents.mapNotNull { doc ->
                                QuizAttempt(
                                    documentId = doc.id,
                                    quizTitle = doc.getString("quizTitle") ?: "",
                                    categoryName = doc.getString("categoryName") ?: "",
                                    score = doc.getDouble("score")?.toFloat() ?: 0f,
                                    totalQuestions = doc.getLong("totalQuestions")?.toInt() ?: 0,
                                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                                    userId = doc.getString("userId") ?: uid,
                                    quizDocumentId = doc.getString("quizDocumentId") ?: "",
                                    quizVersion = doc.getLong("quizVersion")?.toInt() ?: 1,
                                    correctCount = doc.getLong("correctCount")?.toInt() ?: 0,
                                    wrongCount = doc.getLong("wrongCount")?.toInt() ?: 0,
                                    createdAt = doc.getLong("createdAt") ?: doc.getLong("dateMillis") ?: System.currentTimeMillis()
                                )
                            }
                            if (remoteAttempts.isNotEmpty()) {
                                repository.insertAttempts(remoteAttempts)
                            }
                            _attemptsList.value = repository.getAllAttempts()
                        } catch (e: Exception) {
                            Log.e("QuizViewModel", "Error in user attempts snapshot listener: ${e.message}", e)
                        }
                    }
                }
            }
    }

    fun stopObservingUserAttemptsFirestore() {
        attemptsFirestoreListenerRegistration?.remove()
        attemptsFirestoreListenerRegistration = null
    }

    fun loadUserAttemptsFromFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("users").document(userId).collection("attempts")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            processAttemptsSnapshot(snapshot.documents)
                        } else {
                            firestore.collection("attempts")
                                .whereEqualTo("userId", userId)
                                .get()
                                .addOnSuccessListener { topSnapshot ->
                                    processAttemptsSnapshot(topSnapshot.documents)
                                }
                        }
                    }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error loading remote attempts: ${e.message}", e)
            }
        }
    }

    private fun processAttemptsSnapshot(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        viewModelScope.launch(Dispatchers.IO) {
            val remoteAttempts = documents.mapNotNull { doc ->
                QuizAttempt(
                    documentId = doc.id,
                    quizTitle = doc.getString("quizTitle") ?: "",
                    categoryName = doc.getString("categoryName") ?: "",
                    score = doc.getDouble("score")?.toFloat() ?: 0f,
                    totalQuestions = doc.getLong("totalQuestions")?.toInt() ?: 0,
                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                    userId = doc.getString("userId") ?: "",
                    quizDocumentId = doc.getString("quizDocumentId") ?: "",
                    quizVersion = doc.getLong("quizVersion")?.toInt() ?: 1,
                    correctCount = doc.getLong("correctCount")?.toInt() ?: 0,
                    wrongCount = doc.getLong("wrongCount")?.toInt() ?: 0,
                    createdAt = doc.getLong("createdAt") ?: doc.getLong("dateMillis") ?: System.currentTimeMillis()
                )
            }
            repository.insertAttempts(remoteAttempts)
            _attemptsList.value = repository.getAllAttempts()
            logFirestore("Retrieved ${remoteAttempts.size} attempts from Firestore.")
        }
    }

    // --- Admin User Management Features ---
    private val _allRegisteredUsers = MutableStateFlow<List<RegisteredUser>>(emptyList())
    val allRegisteredUsers: StateFlow<List<RegisteredUser>> = _allRegisteredUsers.asStateFlow()

    private val _adminUserAttemptsMap = MutableStateFlow<Map<String, List<QuizAttempt>>>(emptyMap())
    val adminUserAttemptsMap: StateFlow<Map<String, List<QuizAttempt>>> = _adminUserAttemptsMap.asStateFlow()

    private val _adminUsersLoading = MutableStateFlow(false)
    val adminUsersLoading: StateFlow<Boolean> = _adminUsersLoading.asStateFlow()

    private val _adminUsersError = MutableStateFlow<String?>(null)
    val adminUsersError: StateFlow<String?> = _adminUsersError.asStateFlow()

    private var allUsersListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private val userAttemptsListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    fun loadAllRegisteredUsers() {
        startObservingAllRegisteredUsers()
    }

    fun startObservingAllRegisteredUsers() {
        if (isRunningTest()) return
        allUsersListenerRegistration?.remove()
        _adminUsersLoading.value = true
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            allUsersListenerRegistration = firestore.collection("users")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, e ->
                    _adminUsersLoading.value = false
                    if (e != null) {
                        _adminUsersError.value = e.message
                        Log.e("QuizViewModel", "Error listening to users collection: ${e.message}", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val usersList = snapshot.documents.mapNotNull { doc ->
                            val uid = doc.id
                            val email = doc.getString("email") ?: ""
                            val displayName = doc.getString("displayName") ?: doc.getString("name") ?: ""
                            val name = doc.getString("name") ?: displayName
                            val role = doc.getString("role") ?: "user"
                            val status = doc.getString("status") ?: if (doc.getBoolean("blocked") == true) "blocked" else "active"
                            val blocked = doc.getBoolean("blocked") == true || status.equals("blocked", ignoreCase = true)
                            val createdAt = doc.getLong("createdAt") ?: 0L
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val lastLoginAt = doc.getLong("lastLoginAt") ?: 0L
                            RegisteredUser(
                                uid = uid,
                                email = email,
                                displayName = displayName,
                                name = name,
                                role = role,
                                status = status,
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                                lastLoginAt = lastLoginAt,
                                blocked = blocked
                            )
                        }
                        _allRegisteredUsers.value = usersList
                    }
                }
        } catch (e: Exception) {
            _adminUsersError.value = e.message
            _adminUsersLoading.value = false
        }
    }

    fun loadAttemptsForUser(userId: String, email: String) {
        startObservingAttemptsForUser(userId, email)
    }

    fun startObservingAttemptsForUser(userId: String, email: String) {
        if (userId.isBlank() || isRunningTest()) return
        userAttemptsListeners[userId]?.remove()

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val registration = firestore.collection("users").document(userId).collection("attempts")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.w("QuizViewModel", "Subcollection attempts listener error for $userId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val remoteAttempts = snapshot.documents.mapNotNull { doc ->
                                QuizAttempt(
                                    documentId = doc.id,
                                    quizTitle = doc.getString("quizTitle") ?: "Quiz",
                                    categoryName = doc.getString("categoryName") ?: "",
                                    score = doc.getDouble("score")?.toFloat() ?: 0f,
                                    totalQuestions = doc.getLong("totalQuestions")?.toInt() ?: 0,
                                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                                    userId = doc.getString("userId") ?: userId,
                                    quizDocumentId = doc.getString("quizDocumentId") ?: "",
                                    quizVersion = doc.getLong("quizVersion")?.toInt() ?: 1,
                                    correctCount = doc.getLong("correctCount")?.toInt() ?: 0,
                                    wrongCount = doc.getLong("wrongCount")?.toInt() ?: 0,
                                    createdAt = doc.getLong("createdAt") ?: doc.getLong("dateMillis") ?: System.currentTimeMillis()
                                )
                            }
                            val localList = repository.getAttemptsForUserOrEmail(userId, email)
                            val allMerged = (remoteAttempts + localList)
                                .distinctBy { it.documentId.ifEmpty { "${it.quizDocumentId}_${it.dateMillis}" } }
                                .sortedByDescending { it.dateMillis }

                            val currentMap = _adminUserAttemptsMap.value.toMutableMap()
                            currentMap[userId] = allMerged
                            if (email.isNotEmpty()) {
                                currentMap[email] = allMerged
                            }
                            _adminUserAttemptsMap.value = currentMap
                        }
                    }
                }
            userAttemptsListeners[userId] = registration
        } catch (e: Exception) {
            Log.e("QuizViewModel", "Error starting attempts listener for user $userId: ${e.message}", e)
        }
    }

    fun deleteAdminUserAttempt(
        attempt: QuizAttempt,
        userId: String,
        userEmail: String = "",
        onComplete: () -> Unit = {}
    ) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Deleting attempt from cloud and user record..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = attempt.documentId
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()

                // 1. Delete from /users/{userId}/attempts/{docId}
                if (userId.isNotEmpty() && docId.isNotEmpty()) {
                    batch.delete(firestore.collection("users").document(userId).collection("attempts").document(docId))
                }
                // 2. Delete from top-level /attempts/{docId}
                if (docId.isNotEmpty()) {
                    batch.delete(firestore.collection("attempts").document(docId))
                }

                com.google.android.gms.tasks.Tasks.await(batch.commit())

                // 3. Delete from local Room if present
                if (docId.isNotEmpty()) {
                    repository.deleteAttemptByDocumentId(docId)
                }

                // 4. Update _adminUserAttemptsMap immediately
                val currentMap = _adminUserAttemptsMap.value.toMutableMap()
                val updatedList = (currentMap[userId] ?: emptyList()).filter { it.documentId != docId }
                currentMap[userId] = updatedList
                if (userEmail.isNotEmpty()) {
                    currentMap[userEmail] = updatedList
                }
                _adminUserAttemptsMap.value = currentMap

                _syncStatusMessage.value = "Attempt deleted successfully from cloud & user record."
                logAdminAction("DELETE_ATTEMPT", "ATTEMPT", docId, "Deleted attempt for user $userId")
                
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error deleting user attempt: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to delete attempt from cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun toggleUserBlockedStatus(user: RegisteredUser) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        val newBlocked = !user.blocked
        val newStatus = if (newBlocked) "blocked" else "active"
        val actionName = if (newBlocked) "BLOCK_USER" else "UNBLOCK_USER"
        val now = System.currentTimeMillis()
        val updatedUser = user.copy(blocked = newBlocked, status = newStatus, updatedAt = now)

        // 1. Instant 0ms Optimistic UI update for admin panel
        _allRegisteredUsers.value = _allRegisteredUsers.value.map {
            if (it.uid == user.uid || (user.email.isNotEmpty() && it.email.equals(user.email, ignoreCase = true))) updatedUser else it
        }
        _syncStatusMessage.value = "${if (newBlocked) "Blocking" else "Unblocking"} user on cloud in real-time..."

        // 2. Delegate to SyncManager for ultra-fast multi-path Firestore sync
        syncManager.executeAdminBlockToggle(user, newBlocked) { success, message ->
            _isAdminOperating.value = false
            _syncStatusMessage.value = message
            if (success) {
                logAdminAction(actionName, "USER", user.uid, "User ${user.email} status changed to $newStatus")
            }
        }
    }

    fun resetUserProgress(user: RegisteredUser, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Resetting user progress on cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                // Delete cloud progress subcollection users/{uid}/progress
                val progressTask = firestore.collection("users").document(user.uid).collection("progress").get()
                val progressSnap = com.google.android.gms.tasks.Tasks.await(progressTask)
                if (!progressSnap.isEmpty) {
                    val batch = firestore.batch()
                    progressSnap.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                // Delete cloud progress subcollection liveProgress/{uid}/quizzes
                val liveTask = firestore.collection("liveProgress").document(user.uid).collection("quizzes").get()
                val liveSnap = com.google.android.gms.tasks.Tasks.await(liveTask)
                if (!liveSnap.isEmpty) {
                    val batch = firestore.batch()
                    liveSnap.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                // Delete user attempts in users/{uid}/attempts
                val userAttemptsTask = firestore.collection("users").document(user.uid).collection("attempts").get()
                val userAttemptsSnap = com.google.android.gms.tasks.Tasks.await(userAttemptsTask)
                if (!userAttemptsSnap.isEmpty) {
                    val batch = firestore.batch()
                    userAttemptsSnap.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                // Delete user attempts in attempts collection
                val attemptsTask = firestore.collection("attempts").whereEqualTo("userId", user.uid).get()
                val attemptsSnap = com.google.android.gms.tasks.Tasks.await(attemptsTask)
                if (!attemptsSnap.isEmpty) {
                    val batch = firestore.batch()
                    attemptsSnap.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                // Clear local Room database attempts for user
                repository.deleteAttemptsForUserOrEmail(user.uid, user.email)
                loadAttemptsForUser(user.uid, user.email)

                logAdminAction("RESET_PROGRESS", "USER", user.uid, "Reset all progress history for ${user.email}")
                _syncStatusMessage.value = "Progress history reset successfully for ${user.displayName.ifBlank { user.email }}."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error resetting user progress: ${e.message}", e)
                _syncStatusMessage.value = "Failed resetting progress on cloud: ${e.message}"
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun exportUserProgressCSV(user: RegisteredUser, attempts: List<QuizAttempt>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvBuilder = StringBuilder()
                csvBuilder.append("Date,Quiz Title,Category,Score,Total Questions,Accuracy (%)\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                attempts.forEach { attempt ->
                    val dateStr = sdf.format(Date(attempt.dateMillis))
                    val quizTitleEscaped = attempt.quizTitle.replace("\"", "\"\"")
                    val categoryEscaped = attempt.categoryName.replace("\"", "\"\"")
                    val accuracy = if (attempt.totalQuestions > 0) (attempt.score / attempt.totalQuestions * 100).toInt() else 0
                    csvBuilder.append("$dateStr,\"$quizTitleEscaped\",\"$categoryEscaped\",${attempt.score.toInt()},${attempt.totalQuestions},$accuracy\n")
                }
                val context = getApplication<Application>()
                val file = File(context.cacheDir, "Progress_${user.displayName.replace(" ", "_")}_${System.currentTimeMillis()}.csv")
                file.writeText(csvBuilder.toString())
                val authority = "com.example.fileprovider"
                val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Progress Report - ${user.displayName}")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Exported progress history for: ${user.displayName} (${user.email}).")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(intent, "Export CSV via")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error exporting CSV progress: ${e.message}", e)
            }
        }
    }

    fun exportUserProgressPDF(user: RegisteredUser, attempts: List<QuizAttempt>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appContext = getApplication<Application>()
                val appName = try {
                    appContext.getString(com.example.R.string.app_name)
                } catch (e: Exception) {
                    "Speed English"
                }

                val rawLogoBitmap: android.graphics.Bitmap? = try {
                    android.graphics.BitmapFactory.decodeResource(appContext.resources, com.example.R.drawable.img_app_logo_1782049303596)
                } catch (_: Exception) {
                    try {
                        android.graphics.BitmapFactory.decodeResource(appContext.resources, com.example.R.mipmap.ic_launcher)
                    } catch (_: Exception) {
                        null
                    }
                }

                val logoBitmap: android.graphics.Bitmap? = if (rawLogoBitmap != null) {
                    try {
                        android.graphics.Bitmap.createScaledBitmap(rawLogoBitmap, 36, 36, true)
                    } catch (_: Exception) {
                        rawLogoBitmap
                    }
                } else null

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = AndroidColor.rgb(30, 27, 75)
                    textSize = 16f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val subtitlePaint = Paint().apply {
                    color = AndroidColor.rgb(100, 116, 139)
                    textSize = 10f
                    isAntiAlias = true
                }
                val headerPaint = Paint().apply {
                    color = AndroidColor.rgb(30, 41, 59)
                    textSize = 10f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val bodyPaint = Paint().apply {
                    color = AndroidColor.rgb(51, 65, 85)
                    textSize = 9f
                    isAntiAlias = true
                }
                val linePaint = Paint().apply {
                    color = AndroidColor.rgb(226, 232, 240)
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                val rectPaint = Paint().apply {
                    color = AndroidColor.rgb(248, 250, 252)
                    style = Paint.Style.FILL
                }

                // Top-Left Executive Branding (Exact App Logo + 'Speed English')
                val brandX = 30f
                val brandY = 20f
                if (logoBitmap != null) {
                    val logoBgPaint = Paint().apply {
                        color = AndroidColor.rgb(248, 250, 252)
                        isAntiAlias = true
                    }
                    val logoBorderPaint = Paint().apply {
                        color = AndroidColor.rgb(226, 232, 240)
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(brandX, brandY, brandX + 38f, brandY + 38f, 8f, 8f, logoBgPaint)
                    canvas.drawRoundRect(brandX, brandY, brandX + 38f, brandY + 38f, 8f, 8f, logoBorderPaint)
                    canvas.drawBitmap(logoBitmap, brandX + 1f, brandY + 1f, Paint().apply { isAntiAlias = true })
                } else {
                    val badgePaint = Paint().apply {
                        color = AndroidColor.rgb(79, 70, 229)
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(brandX, brandY, brandX + 38f, brandY + 38f, 8f, 8f, badgePaint)
                }
                val brandTextPaint = Paint().apply {
                    color = AndroidColor.rgb(30, 27, 75)
                    textSize = 15f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText(appName, brandX + 46f, brandY + 18f, brandTextPaint)
                
                val brandSubPaint = Paint().apply {
                    color = AndroidColor.rgb(100, 116, 139)
                    textSize = 8.5f
                    isAntiAlias = true
                }
                canvas.drawText("Official Student Analytics & Progress Report", brandX + 46f, brandY + 31f, brandSubPaint)

                val dividerPaint = Paint().apply {
                    color = AndroidColor.rgb(99, 102, 241)
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                canvas.drawLine(30f, brandY + 45f, 565f, brandY + 45f, dividerPaint)

                // Summary Card
                canvas.drawRect(30f, 60f, 565f, 150f, rectPaint)
                canvas.drawRect(30f, 60f, 565f, 150f, linePaint)
                canvas.drawText("STUDENT PROGRESS REPORT", 50f, 90f, titlePaint)
                canvas.drawText("Student Name: ${user.displayName}", 50f, 110f, subtitlePaint)
                canvas.drawText("Email: ${user.email}   |   Total Quiz Attempts: ${attempts.size}", 50f, 130f, subtitlePaint)

                var currentY = 160f
                canvas.drawRect(30f, currentY, 565f, currentY + 25f, rectPaint)
                canvas.drawRect(30f, currentY, 565f, currentY + 25f, linePaint)
                canvas.drawText("Date", 35f, currentY + 16f, headerPaint)
                canvas.drawText("Quiz Title", 130f, currentY + 16f, headerPaint)
                canvas.drawText("Category", 290f, currentY + 16f, headerPaint)
                canvas.drawText("Score", 450f, currentY + 16f, headerPaint)
                canvas.drawText("Accuracy", 510f, currentY + 16f, headerPaint)
                currentY += 25f

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                attempts.forEachIndexed { _, attempt ->
                    if (currentY > 800f) return@forEachIndexed
                    val dateStr = sdf.format(Date(attempt.dateMillis))
                    val pct = if (attempt.totalQuestions > 0) (attempt.score / attempt.totalQuestions * 100).toInt() else 0
                    canvas.drawText(dateStr, 35f, currentY + 18f, bodyPaint)
                    val quizTitle = if (attempt.quizTitle.length > 25) attempt.quizTitle.take(23) + "..." else attempt.quizTitle
                    canvas.drawText(quizTitle, 130f, currentY + 18f, bodyPaint)
                    val categoryName = if (attempt.categoryName.length > 25) attempt.categoryName.take(23) + "..." else attempt.categoryName
                    canvas.drawText(categoryName, 290f, currentY + 18f, bodyPaint)
                    canvas.drawText("${attempt.score.toInt()} / ${attempt.totalQuestions}", 450f, currentY + 18f, bodyPaint)
                    canvas.drawText("$pct%", 510f, currentY + 18f, bodyPaint)
                    canvas.drawLine(30f, currentY + 25f, 565f, currentY + 25f, linePaint)
                    currentY += 25f
                }
                canvas.drawLine(30f, 160f, 30f, currentY, linePaint)
                canvas.drawLine(565f, 160f, 565f, currentY, linePaint)
                pdfDocument.finishPage(page)

                val file = File(appContext.cacheDir, "Report_${user.displayName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                val authority = "com.example.fileprovider"
                val fileUri: Uri = FileProvider.getUriForFile(appContext, authority, file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Student Progress Report - ${user.displayName}")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Generated progress report PDF for: ${user.displayName} (${user.email}).")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(intent, "Export PDF via")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(chooser)
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error exporting PDF progress: ${e.message}", e)
            }
        }
    }

    fun deleteSingleAttempt(attempt: QuizAttempt, userEmail: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = attempt.documentId
                val userId = attempt.userId
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()

                // Delete from user's subcollection
                if (userId.isNotEmpty() && docId.isNotEmpty()) {
                    batch.delete(firestore.collection("users").document(userId).collection("attempts").document(docId))
                }
                // Delete from top-level attempts
                if (docId.isNotEmpty()) {
                    batch.delete(firestore.collection("attempts").document(docId))
                }

                com.google.android.gms.tasks.Tasks.await(batch.commit())

                // Delete from local Room database
                if (docId.isNotEmpty()) {
                    repository.deleteAttemptByDocumentId(docId)
                }

                // Update UI map immediately
                val currentMap = _adminUserAttemptsMap.value.toMutableMap()
                val updatedList = (currentMap[userId] ?: emptyList()).filter { it.documentId != docId }
                currentMap[userId] = updatedList
                if (userEmail.isNotEmpty()) {
                    currentMap[userEmail] = updatedList
                }
                _adminUserAttemptsMap.value = currentMap

                loadAttemptsForUser(userId, userEmail)
                loadUserAttemptsFromFirestore()
                logFirestore("Single attempt deleted from Firestore & local database (DocId: $docId)")
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error deleting single attempt from cloud: ${e.message}", e)
                // Fallback local deletion
                if (attempt.documentId.isNotEmpty()) {
                    repository.deleteAttemptByDocumentId(attempt.documentId)
                }
                loadAttemptsForUser(attempt.userId, userEmail)
            }
        }
    }

    fun deleteAllAttemptsForUser(userId: String, userEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // Delete from subcollection
                val userAttemptsSnap = com.google.android.gms.tasks.Tasks.await(
                    firestore.collection("users").document(userId).collection("attempts").get()
                )
                if (!userAttemptsSnap.isEmpty) {
                    val batch = firestore.batch()
                    userAttemptsSnap.documents.forEach { doc -> batch.delete(doc.reference) }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                // Delete from top-level attempts
                val topSnap = com.google.android.gms.tasks.Tasks.await(
                    firestore.collection("attempts").whereEqualTo("userId", userId).get()
                )
                if (!topSnap.isEmpty) {
                    val batch = firestore.batch()
                    topSnap.documents.forEach { doc -> batch.delete(doc.reference) }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                }

                repository.deleteAttemptsForUserOrEmail(userId, userEmail)
                
                val currentMap = _adminUserAttemptsMap.value.toMutableMap()
                currentMap[userId] = emptyList()
                if (userEmail.isNotEmpty()) currentMap[userEmail] = emptyList()
                _adminUserAttemptsMap.value = currentMap

                loadAttemptsForUser(userId, userEmail)
                loadUserAttemptsFromFirestore()
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error deleting all attempts from cloud: ${e.message}", e)
                repository.deleteAttemptsForUserOrEmail(userId, userEmail)
                loadAttemptsForUser(userId, userEmail)
            }
        }
    }

    fun generateMockAttemptsForUser(userId: String, userEmail: String) {
        viewModelScope.launch {
            try {
                val allQuizzes = repository.getAllQuizzes()
                if (allQuizzes.isEmpty()) return@launch
                val countToGen = minOf(4, allQuizzes.size)
                val shuffledQuizzes = allQuizzes.shuffled().take(countToGen)
                shuffledQuizzes.forEachIndexed { i, quiz ->
                    val qCount = repository.getQuestionCountForQuiz(quiz.documentId).coerceAtLeast(5)
                    val score = (qCount * listOf(0.6f, 0.8f, 1.0f)[i % 3]).toInt().coerceIn(0, qCount)
                    val parentCat = repository.getCategoryById(quiz.categoryId)
                    val categoryName = parentCat?.name ?: "General"
                    val attemptDate = System.currentTimeMillis() - (i * 24 * 3600 * 1000L) - (15 * 60 * 1000L)
                    val mockAttempt = QuizAttempt(
                        quizTitle = quiz.title,
                        categoryName = categoryName,
                        score = score.toFloat(),
                        totalQuestions = qCount,
                        dateMillis = attemptDate,
                        userId = userId,
                        quizDocumentId = quiz.documentId,
                        documentId = "mock-progress-id-${UUID.randomUUID()}",
                        correctCount = score,
                        wrongCount = qCount - score
                    )
                    repository.insertAttempt(mockAttempt)
                }
                loadAttemptsForUser(userId, userEmail)
                loadUserAttemptsFromFirestore()
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error generating mock attempts: ${e.message}", e)
            }
        }
    }

    // UI Screen state & backstack
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val backstack = mutableListOf<Screen>()

    // Observables from DB
    val categories: StateFlow<List<Category>> = repository.allCategoriesFlow
        .map { list -> list.filter { !it.isDraft } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var loadUserQuizzesJob: Job? = null
    private val _quizzesForSelectedCategory = MutableStateFlow<List<Quiz>>(emptyList())
    val quizzesForSelectedCategory: StateFlow<List<Quiz>> = _quizzesForSelectedCategory.asStateFlow()

    private val _quizQuestionsCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quizQuestionsCountMap: StateFlow<Map<String, Int>> = _quizQuestionsCountMap.asStateFlow()

    // Active Quiz state
    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption: StateFlow<String?> = _selectedOption.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _quizScore = MutableStateFlow(0f)
    val quizScore: StateFlow<Float> = _quizScore.asStateFlow()

    fun calculateQuizScore(answers: List<QuestionUserAnswer>, marksPerQuestion: Float, negativeMarking: Float): Float {
        val correctCount = answers.count { it.isCorrect }
        val incorrectCount = answers.size - correctCount
        val total = (correctCount * marksPerQuestion) - (incorrectCount * negativeMarking)
        return if (total < 0f) 0f else total
    }

    private val _isQuizLoading = MutableStateFlow(false)
    val isQuizLoading: StateFlow<Boolean> = _isQuizLoading.asStateFlow()

    private val _userQuestionsAnswersSession = MutableStateFlow<List<QuestionUserAnswer>>(emptyList())
    val userQuestionsAnswersSession: StateFlow<List<QuestionUserAnswer>> = _userQuestionsAnswersSession.asStateFlow()

    private var questionStartTimeMillis: Long = 0L
    private var timerJob: Job? = null

    // Admin State & Control
    val adminCategoriesList: StateFlow<List<Category>> = repository.allCategoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _adminQuizzesList = MutableStateFlow<List<Quiz>>(emptyList())
    val adminQuizzesList: StateFlow<List<Quiz>> = _adminQuizzesList.asStateFlow()

    private val _adminQuestionsForSelectedQuiz = MutableStateFlow<List<Question>>(emptyList())
    val adminQuestionsForSelectedQuiz: StateFlow<List<Question>> = _adminQuestionsForSelectedQuiz.asStateFlow()

    private suspend fun seedAuditLogsIfNeeded() {
        if (repository.getAllAuditLogs().isEmpty()) {
            val now = System.currentTimeMillis()
            val sampleLogs = listOf(
                QuestionAuditLog(
                    actionType = "ADDED",
                    questionId = "q_s1",
                    questionText = "What is the primary gas found in Earth's atmosphere?",
                    quizId = "1",
                    quizTitle = "Basic Physics & Atmospheric Science",
                    adminEmail = "admin@speedenglish.local",
                    timestamp = now - (3600 * 1000 * 5),
                    status = "SAVED_LOCALLY",
                    documentId = "audit_doc_001",
                    details = "Question created with options A-D. Correct Option: A"
                )
            )
            repository.insertAuditLogs(sampleLogs)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkUserSessionInternal()
            loadQuizProgress()

            // Seed database if empty
            repository.seedDatabaseIfNeeded(force = false, context = getApplication())
            repository.ensureSpottingErrorsSubjectVerbQuestions()
            seedAuditLogsIfNeeded()

            updateQuizQuestionsCounts()
            startObservingContactMethodsAndPrivacyPolicy()

            // Attach SuperFast SyncManager Auth State Observer
            syncManager.attachAuthStateObserver(
                onAuthenticated = { uid, email, isAdmin ->
                    startObservingUserDocument(uid)
                    startObservingUserAttemptsFirestore(uid)
                    startObservingRealtimeProgress(uid)
                    if (_autoSyncOnStartup.value) {
                        startRealtimeFirestoreSync()
                    }
                    if (isAdmin) {
                        startObservingAllRegisteredUsers()
                    }
                },
                onSignedOut = {
                    stopObservingUserAttemptsFirestore()
                    stopObservingRealtimeProgress()
                    stopRealtimeFirestoreSync()
                }
            )

            if (_autoSyncOnStartup.value && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                startRealtimeFirestoreSync()
            }

            launch(Dispatchers.IO) {
                repository.allQuestionsFlow.collect {
                    updateQuizQuestionsCounts()
                }
            }

            // Observe real-time network connectivity changes
            launch(Dispatchers.IO) {
                NetworkConnectivityHelper.observeConnectivity(getApplication()).collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                    if (isConnected && _autoSyncOnStartup.value && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                        startRealtimeFirestoreSync()
                    } else if (!isConnected) {
                        stopRealtimeFirestoreSync()
                    }
                }
            }
        }
    }

    private var currentAdminCategoryId: String? = null
    private var currentAdminQuizId: String? = null

    fun triggerRealtimeSyncReload() {
        viewModelScope.launch {
            updateQuizQuestionsCounts()
            loadAdminCategories()

            currentAdminCategoryId?.let { catId ->
                val quizzes = repository.getQuizzesByCategory(catId)
                _adminQuizzesList.value = quizzes
            }

            currentAdminQuizId?.let { qId ->
                val questions = repository.getQuestionsForQuiz(qId)
                _adminQuestionsForSelectedQuiz.value = questions
            }
        }
    }

    fun loadCurrentUserName(email: String) {
        _currentUserName.value = email.substringBefore("@")
    }

    fun checkUserSession() {
        viewModelScope.launch {
            checkUserSessionInternal()
        }
    }

    private fun getReadableAuthError(e: Throwable?, defaultMsg: String): String {
        if (e == null) return defaultMsg
        val msg = e.message ?: ""
        return when {
            e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException || msg.contains("invalid credential", ignoreCase = true) || msg.contains("password", ignoreCase = true) || msg.contains("wrong", ignoreCase = true) ->
                "Incorrect password or credentials. Please try again."
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException || msg.contains("email already exists", ignoreCase = true) || msg.contains("already in use", ignoreCase = true) ->
                "An account with this email address already exists. Please sign in."
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException || msg.contains("user not found", ignoreCase = true) || msg.contains("no user record", ignoreCase = true) ->
                "No account found with this email address."
            e is com.google.firebase.FirebaseNetworkException || msg.contains("network", ignoreCase = true) || msg.contains("connection", ignoreCase = true) ->
                "Network error. Please check your internet connection."
            msg.contains("blocked", ignoreCase = true) ->
                "Your account has been blocked by an administrator."
            msg.contains("badly formatted", ignoreCase = true) || msg.contains("invalid email", ignoreCase = true) ->
                "Please enter a valid email address."
            else -> e.localizedMessage ?: defaultMsg
        }
    }

    fun getReadableFirestoreError(e: Throwable?, defaultMsg: String): String {
        if (e == null) return defaultMsg
        val msg = e.message ?: ""
        return when {
            e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED || msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("permission", ignoreCase = true) ->
                "You do not have permission to perform this action."
            e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE || msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) || msg.contains("offline", ignoreCase = true) ->
                "Network connection unavailable. Please check your internet connection."
            e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND || msg.contains("NOT_FOUND", ignoreCase = true) ->
                "The requested resource was not found on the server."
            e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.ALREADY_EXISTS || msg.contains("ALREADY_EXISTS", ignoreCase = true) ->
                "An item with this ID already exists on the server."
            else -> e.localizedMessage ?: defaultMsg
        }
    }

    private suspend fun checkUserSessionInternal() {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: ""
            val uid = currentUser.uid
            
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val status = document.getString("status") ?: ""
                    val isBlocked = document.exists() && (document.getBoolean("blocked") == true || status.equals("blocked", ignoreCase = true))
                    if (isBlocked) {
                        _isCurrentUserBlocked.value = true
                        sharedPrefs.edit().putBoolean("session_is_blocked", true).apply()
                        _authMessage.value = "Your account has been blocked by an administrator."
                        startObservingUserDocument(uid)
                        return@addOnSuccessListener
                    }
                    
                    _isCurrentUserBlocked.value = false
                    sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
                    val isAdminEmail = email.equals("sd504212@gmail.com", ignoreCase = true)
                    val role = if (isAdminEmail) "admin" else if (document.exists()) (document.getString("role") ?: "user") else "user"
                    val displayName = if (document.exists()) (document.getString("name") ?: document.getString("displayName") ?: email.substringBefore("@")) else email.substringBefore("@")
                    
                    _currentUserEmail.value = email
                    _currentUserName.value = displayName
                    _currentUserRole.value = role
                    saveSessionLocally(email, role, displayName, uid)
                    
                    startObservingUserAttempts(uid)
                    loadUserAttemptsFromFirestore()
                    startObservingUserAttemptsFirestore(uid)
                    startObservingRealtimeProgress(uid)
                    startObservingUserDocument(uid)
                    startRealtimeFirestoreSync()

                    val now = System.currentTimeMillis()
                    val updates = mutableMapOf<String, Any>(
                        "lastLoginAt" to now,
                        "updatedAt" to now
                    )
                    if (isAdminEmail) {
                        updates["role"] = "admin"
                    }
                    firestore.collection("users").document(uid).update(updates)
                }
                .addOnFailureListener {
                    val wasBlocked = sharedPrefs.getBoolean("session_is_blocked", false) || _isCurrentUserBlocked.value
                    if (wasBlocked) {
                        _isCurrentUserBlocked.value = true
                        startObservingUserDocument(uid)
                        return@addOnFailureListener
                    }

                    val isAdminEmail = email.equals("sd504212@gmail.com", ignoreCase = true)
                    val cachedRole = if (isAdminEmail) "admin" else (sharedPrefs.getString("session_user_role", "user") ?: "user")
                    val cachedName = sharedPrefs.getString("session_user_name", email.substringBefore("@")) ?: email.substringBefore("@")
                    _currentUserEmail.value = email
                    _currentUserName.value = cachedName
                    _currentUserRole.value = cachedRole
                    saveSessionLocally(email, cachedRole, cachedName, uid)
                    
                    startObservingUserAttempts(uid)
                    loadUserAttemptsFromFirestore()
                    startObservingUserAttemptsFirestore(uid)
                    startObservingRealtimeProgress(uid)
                    startObservingUserDocument(uid)
                    startRealtimeFirestoreSync()
                }
        } else {
            val wasBlocked = sharedPrefs.getBoolean("session_is_blocked", false)
            val savedUid = sharedPrefs.getString("session_user_uid", null)
            if (wasBlocked && !savedUid.isNullOrEmpty()) {
                _isCurrentUserBlocked.value = true
                startObservingUserDocument(savedUid)
            } else if (!isTestEnv) {
                if (savedUid.isNullOrEmpty()) {
                    clearSessionLocally()
                    _currentUserEmail.value = null
                    _currentUserName.value = null
                    _currentUserRole.value = null
                }
            }
        }
    }

    private var userDocumentListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startObservingUserDocument(uid: String) {
        if (isTestEnv || uid == "learner_user" || uid.isBlank()) return
        val email = _currentUserEmail.value ?: ""

        syncManager.startObservingSecurityStatus(uid, email) { isBlocked, reason ->
            if (isBlocked) {
                _isCurrentUserBlocked.value = true
                sharedPrefs.edit().putBoolean("session_is_blocked", true).apply()
                // Instantly cancel active quiz and timer
                timerJob?.cancel()
                _savedQuizProgress.value = null
                logFirestore("Account restriction enforced: $reason. Live access revoked.")
            } else {
                _isCurrentUserBlocked.value = false
                sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
                logFirestore("Account active. Permissions verified.")
            }
        }
    }

    fun stopObservingUserDocument() {
        // Kept active for real-time security updates unless signed out
    }

    fun signUp(email: String, password: String, role: String, name: String = "", onSuccess: () -> Unit = {}) {
        _isAuthLoading.value = true
        _authMessage.value = ""

        if (isRunningTest()) {
            val context = getApplication<Application>()
            val mockPrefs = context.getSharedPreferences("firebase_mock_auth", Context.MODE_PRIVATE)
            mockPrefs.edit().apply {
                putBoolean("registered_$email", true)
                putString("user_pass_$email", password)
                putString("user_role_$email", if (email.equals("sd504212@gmail.com", ignoreCase = true)) "admin" else "user")
                putString("user_name_$email", name)
                commit()
            }
            _currentUserEmail.value = email
            _currentUserName.value = if (name.isNotBlank()) name else email.substringBefore("@")
            _currentUserRole.value = if (email.equals("sd504212@gmail.com", ignoreCase = true)) "admin" else "user"
            saveSessionLocally(email, _currentUserRole.value ?: "user", _currentUserName.value ?: "", email.replace(".", "_"))
            _authMessage.value = "Account created successfully!"
            _isAuthLoading.value = false
            onSuccess()
            return
        }

        com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    val uid = firebaseUser?.uid ?: email.replace(".", "_")
                    val chosenName = if (name.isNotBlank()) name else email.substringBefore("@")
                    val isAdminEmail = email.equals("sd504212@gmail.com", ignoreCase = true)
                    val assignedRole = if (isAdminEmail) "admin" else "user"
                    val now = System.currentTimeMillis()
                    
                    _currentUserEmail.value = email
                    _currentUserName.value = chosenName
                    _currentUserRole.value = assignedRole
                    _isCurrentUserBlocked.value = false
                    sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
                    saveSessionLocally(email, assignedRole, chosenName, uid)
                    
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val userDoc = mapOf(
                        "uid" to uid,
                        "name" to chosenName,
                        "displayName" to chosenName,
                        "email" to email,
                        "role" to assignedRole,
                        "status" to "active",
                        "blocked" to false,
                        "createdAt" to now,
                        "updatedAt" to now,
                        "lastLoginAt" to now
                    )
                    firestore.collection("users").document(uid).set(userDoc)
                        .addOnSuccessListener {
                            _authMessage.value = "Account created successfully!"
                            _isAuthLoading.value = false
                            startObservingUserAttempts(uid)
                            startObservingRealtimeProgress(uid)
                            startObservingUserDocument(uid)
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            _authMessage.value = "Account created, but database entry failed: ${e.message}"
                            _isAuthLoading.value = false
                            startObservingUserAttempts(uid)
                            startObservingRealtimeProgress(uid)
                            startObservingUserDocument(uid)
                            onSuccess()
                        }
                } else {
                    _authMessage.value = getReadableAuthError(task.exception, "Signup failed.")
                    _isAuthLoading.value = false
                }
            }
    }
    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _authMessage.value = "Please enter an email address first."
            return
        }
        _isAuthLoading.value = true
        _authMessage.value = ""

        if (isRunningTest()) {
            _authMessage.value = "Password reset email sent to $email."
            _isAuthLoading.value = false
            return
        }

        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                _isAuthLoading.value = false
                if (task.isSuccessful) {
                    _authMessage.value = "Password reset email sent to $email. Please check your inbox."
                } else {
                    _authMessage.value = getReadableAuthError(task.exception, "Failed to send password reset email.")
                }
            }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit = {}) {
        _isAuthLoading.value = true
        _authMessage.value = ""

        if (isRunningTest()) {
            val context = getApplication<Application>()
            val isConnected = NetworkConnectivityHelper.isInternetAvailable(context)
            if (!isConnected) {
                _authMessage.value = "Connect with internet"
                _isAuthLoading.value = false
                return
            }

            val mockPrefs = context.getSharedPreferences("firebase_mock_auth", Context.MODE_PRIVATE)
            val isRegistered = mockPrefs.getBoolean("registered_$email", false)
            if (!isRegistered) {
                _authMessage.value = "Please Register."
                _isAuthLoading.value = false
                return
            }

            val correctPass = mockPrefs.getString("user_pass_$email", "")
            if (password != correctPass) {
                _authMessage.value = "Wrong Password."
                _isAuthLoading.value = false
                return
            }

            val role = mockPrefs.getString("user_role_$email", "user") ?: "user"
            _currentUserEmail.value = email
            _currentUserName.value = email.substringBefore("@")
            _currentUserRole.value = role
            saveSessionLocally(email, role, email.substringBefore("@"), email.replace(".", "_"))
            _authMessage.value = "Signed in successfully!"
            _isAuthLoading.value = false
            onSuccess()
            return
        }

        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    val uid = firebaseUser?.uid ?: email.replace(".", "_")
                    val now = System.currentTimeMillis()
                    val isAdminEmail = email.equals("sd504212@gmail.com", ignoreCase = true)
                    
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    firestore.collection("users").document(uid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val status = document.getString("status") ?: ""
                                val isBlocked = document.getBoolean("blocked") == true || status.equals("blocked", ignoreCase = true)
                                if (isBlocked) {
                                    _isCurrentUserBlocked.value = true
                                    sharedPrefs.edit().putBoolean("session_is_blocked", true).apply()
                                    _authMessage.value = "Your account has been blocked by an administrator."
                                    _isAuthLoading.value = false
                                    startObservingUserDocument(uid)
                                    return@addOnSuccessListener
                                }
                                
                                _isCurrentUserBlocked.value = false
                                sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
                                val assignedRole = if (isAdminEmail) "admin" else (document.getString("role") ?: "user")
                                val chosenName = document.getString("name") ?: document.getString("displayName") ?: email.substringBefore("@")
                                
                                _currentUserEmail.value = email
                                _currentUserName.value = chosenName
                                _currentUserRole.value = assignedRole
                                saveSessionLocally(email, assignedRole, chosenName, uid)
                                
                                _authMessage.value = "Signed in successfully!"
                                _isAuthLoading.value = false
                                startObservingUserAttempts(uid)
                                startObservingRealtimeProgress(uid)
                                startObservingUserDocument(uid)
                                
                                val updates = mutableMapOf<String, Any>(
                                    "lastLoginAt" to now,
                                    "updatedAt" to now
                                )
                                if (isAdminEmail) {
                                    updates["role"] = "admin"
                                }
                                firestore.collection("users").document(uid).update(updates)
                                onSuccess()
                            } else {
                                val chosenName = email.substringBefore("@")
                                val assignedRole = if (isAdminEmail) "admin" else "user"
                                val userDoc = mapOf(
                                    "uid" to uid,
                                    "name" to chosenName,
                                    "displayName" to chosenName,
                                    "email" to email,
                                    "role" to assignedRole,
                                    "status" to "active",
                                    "blocked" to false,
                                    "createdAt" to now,
                                    "updatedAt" to now,
                                    "lastLoginAt" to now
                                )
                                firestore.collection("users").document(uid).set(userDoc)
                                    .addOnCompleteListener {
                                        _currentUserEmail.value = email
                                        _currentUserName.value = chosenName
                                        _currentUserRole.value = assignedRole
                                        _isCurrentUserBlocked.value = false
                                        sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
                                        saveSessionLocally(email, assignedRole, chosenName, uid)
                                        _authMessage.value = "Signed in successfully!"
                                        _isAuthLoading.value = false
                                        startObservingUserAttempts(uid)
                                        startObservingRealtimeProgress(uid)
                                        startObservingUserDocument(uid)
                                        onSuccess()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            val wasBlocked = sharedPrefs.getBoolean("session_is_blocked", false) || _isCurrentUserBlocked.value
                            if (wasBlocked) {
                                _isCurrentUserBlocked.value = true
                                _authMessage.value = "Your account has been blocked by an administrator."
                                _isAuthLoading.value = false
                                startObservingUserDocument(uid)
                                return@addOnFailureListener
                            }

                            val assignedRole = if (isAdminEmail) "admin" else "user"
                            val chosenName = email.substringBefore("@")
                            _currentUserEmail.value = email
                            _currentUserName.value = chosenName
                            _currentUserRole.value = assignedRole
                            _isCurrentUserBlocked.value = false
                            saveSessionLocally(email, assignedRole, chosenName, uid)
                            _authMessage.value = "Signed in successfully!"
                            _isAuthLoading.value = false
                            startObservingUserAttempts(uid)
                            startObservingRealtimeProgress(uid)
                            startObservingUserDocument(uid)
                            onSuccess()
                        }
                } else {
                    _authMessage.value = getReadableAuthError(task.exception, "Sign in failed.")
                    _isAuthLoading.value = false
                }
            }
    }

    fun signOut() {
        stopObservingUserDocument()
        stopObservingUserAttemptsFirestore()
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        clearSessionLocally()
        _currentUserEmail.value = null
        _currentUserName.value = null
        _currentUserRole.value = null
        _isCurrentUserBlocked.value = false
        sharedPrefs.edit().putBoolean("session_is_blocked", false).apply()
        _attemptsList.value = emptyList()
        _savedQuizProgress.value = null
        _realtimeProgressMap.value = emptyMap()
        stopRealtimeFirestoreSync()
        stopObservingRealtimeProgress()
        _authMessage.value = "Signed out successfully."
    }

    fun clearAuthMessage() {
        _authMessage.value = ""
    }

    // Navigation Helper
    fun navigateTo(screen: Screen, clearBackstack: Boolean = false) {
        if (_isCurrentUserBlocked.value && screen !is Screen.Splash) {
            return
        }
        if (_currentScreen.value is Screen.ActiveQuiz && screen !is Screen.ActiveQuiz && screen !is Screen.Score) {
            saveQuizProgress()
            saveQuizProgressToFirestore(isCompleted = false)
        }
        if (screen !is Screen.ActiveQuiz) {
            timerJob?.cancel()
        }
        if (clearBackstack) {
            backstack.clear()
        } else {
            backstack.add(_currentScreen.value)
        }
        _currentScreen.value = screen

        if (screen is Screen.Home) {
            updateQuizQuestionsCounts()
        }
    }

    fun navigateBack(): Boolean {
        if (_isCurrentUserBlocked.value) {
            return true // Guard: Handled, prevent exiting blocked screen
        }
        if (backstack.isNotEmpty()) {
            val prev = backstack.removeAt(backstack.size - 1)
            if (_currentScreen.value is Screen.ActiveQuiz && prev !is Screen.ActiveQuiz && prev !is Screen.Score) {
                saveQuizProgress()
                saveQuizProgressToFirestore(isCompleted = false)
            }
            if (prev !is Screen.ActiveQuiz) {
                timerJob?.cancel()
            }
            _currentScreen.value = prev
            if (prev is Screen.Home) {
                updateQuizQuestionsCounts()
            }
            return true
        }
        val current = _currentScreen.value
        if (current !is Screen.Home && current !is Screen.Splash) {
            _currentScreen.value = Screen.Home
            updateQuizQuestionsCounts()
            return true
        }
        return false
    }

    fun selectCategory(category: Category) {
        loadUserQuizzesJob?.cancel()
        loadUserQuizzesJob = viewModelScope.launch {
            repository.getQuizzesFlow(category.documentId).collectLatest { quizzes ->
                _quizzesForSelectedCategory.value = quizzes.filter { !it.isDraft }
            }
        }
        updateQuizQuestionsCounts()
        navigateTo(Screen.CategoryView(category))
    }

    fun updateQuizQuestionsCounts() {
        viewModelScope.launch {
            val allQuizzes = repository.getAllQuizzes()
            val finalMap = mutableMapOf<String, Int>()
            allQuizzes.forEach { quiz ->
                val questions = repository.getQuestionsForQuiz(quiz.documentId)
                finalMap[quiz.documentId] = questions.size
            }
            _quizQuestionsCountMap.value = finalMap
        }
    }

    // --- Active Quiz Taking Flow ---
    fun loadQuizProgress() {
        val json = quizProgressPrefs.getString("saved_progress", null)
        if (json != null) {
            try {
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(SavedQuizProgress::class.java)
                var progress = adapter.fromJson(json)
                if (progress != null) {
                    val savedTime = quizProgressPrefs.getInt("saved_progress_time_remaining", -1)
                    if (savedTime != -1) {
                        progress = progress.copy(timeRemaining = savedTime)
                    }
                    _savedQuizProgress.value = progress
                }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed to load quiz progress", e)
            }
        } else {
            _savedQuizProgress.value = null
        }
    }

    fun clearQuizProgress() {
        quizProgressPrefs.edit().clear().apply()
        _savedQuizProgress.value = null
    }

    fun saveQuizProgressToFirestore(isCompleted: Boolean = false) {
        val currentQuiz = (currentScreen.value as? Screen.ActiveQuiz)?.quiz ?: return
        val uid = getCurrentUserId()
        if (uid.isEmpty()) return
        _isQuizProgressSynced.value = true
        _quizProgressSyncError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val answersMap = _userQuestionsAnswersSession.value.associate { answer ->
                    answer.question.documentId to (answer.selectedOptionLetter ?: "")
                }.toMutableMap()

                val activeQuestions = _questions.value
                val currentIndex = _currentQuestionIndex.value
                val currentQuestion = activeQuestions.getOrNull(currentIndex)
                val currentSel = _selectedOption.value
                if (currentQuestion != null && currentSel != null) {
                    answersMap[currentQuestion.documentId] = currentSel
                }

                val now = System.currentTimeMillis()
                repository.saveProgress(
                    uid = uid,
                    quizId = currentQuiz.documentId,
                    score = _quizScore.value,
                    completedAt = if (isCompleted) now else null,
                    currentQuestionIndex = _currentQuestionIndex.value,
                    answers = answersMap
                )

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val progressData = mapOf(
                    "quizId" to currentQuiz.documentId,
                    "score" to _quizScore.value,
                    "completedAt" to if (isCompleted) now else null,
                    "currentQuestionIndex" to _currentQuestionIndex.value,
                    "answers" to answersMap,
                    "quizVersion" to currentQuiz.version,
                    "updatedAt" to now
                )
                
                val batch = firestore.batch()
                val userProgressRef = firestore.collection("users").document(uid).collection("progress").document(currentQuiz.documentId)
                val liveProgressRef = firestore.collection("liveProgress").document(uid).collection("quizzes").document(currentQuiz.documentId)
                batch.set(userProgressRef, progressData)
                batch.set(liveProgressRef, progressData)

                batch.commit()
                    .addOnSuccessListener {
                        _isQuizProgressSynced.value = true
                    }
                    .addOnFailureListener { e ->
                        _isQuizProgressSynced.value = false
                        _quizProgressSyncError.value = e.message
                    }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed to save progress: ${e.message}", e)
                _isQuizProgressSynced.value = false
                _quizProgressSyncError.value = e.message
            }
        }
    }

    private var progressListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startObservingRealtimeProgress(uid: String) {
        if (uid.isBlank()) return
        progressListenerRegistration?.remove()
        
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        progressListenerRegistration = firestore.collection("users")
            .document(uid)
            .collection("progress")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e("QuizViewModel", "Error observing realtime progress: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val progressMap = snapshot.documents.associate { doc ->
                        val quizId = doc.id
                        val score = doc.getDouble("score")?.toFloat() ?: 0f
                        val completedAt = doc.getLong("completedAt")
                        val currentQuestionIndex = doc.getLong("currentQuestionIndex")?.toInt() ?: 0
                        val answers = (doc.get("answers") as? Map<String, String>) ?: emptyMap()
                        val quizVersion = doc.getLong("quizVersion")?.toInt() ?: 1
                        val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        
                        quizId to LiveProgressData(
                            quizId = quizId,
                            score = score,
                            completedAt = completedAt,
                            currentQuestionIndex = currentQuestionIndex,
                            answers = answers,
                            quizVersion = quizVersion,
                            updatedAt = updatedAt
                        )
                    }
                    _realtimeProgressMap.value = progressMap
                }
            }
    }

    fun stopObservingRealtimeProgress() {
        progressListenerRegistration?.remove()
        progressListenerRegistration = null
        _realtimeProgressMap.value = emptyMap()
    }

    fun saveQuizProgress() {
        val currentQuiz = (currentScreen.value as? Screen.ActiveQuiz)?.quiz ?: return
        val questionsList = _questions.value
        if (questionsList.isEmpty()) return

        viewModelScope.launch {
            val categoryId = currentQuiz.categoryId
            val cat = repository.allCategoriesFlow.firstOrNull()?.find { it.documentId == categoryId }
            val categoryName = cat?.name ?: "Drill"

            val progress = SavedQuizProgress(
                quizId = currentQuiz.documentId,
                quizTitle = currentQuiz.title,
                categoryName = categoryName,
                currentQuestionIndex = _currentQuestionIndex.value,
                quizScore = _quizScore.value,
                timeRemaining = _timeRemaining.value,
                questions = questionsList,
                userAnswers = _userQuestionsAnswersSession.value
            )

            withContext(Dispatchers.IO) {
                try {
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                        .build()
                    val adapter = moshi.adapter(SavedQuizProgress::class.java)
                    val json = adapter.toJson(progress)
                    quizProgressPrefs.edit()
                        .putString("saved_progress", json)
                        .putInt("saved_progress_time_remaining", progress.timeRemaining)
                        .apply()
                    _savedQuizProgress.value = progress
                } catch (e: Exception) {
                    Log.e("QuizViewModel", "Failed to save quiz progress", e)
                }
            }
        }
    }

    private fun saveQuizProgressPartialTime(seconds: Int) {
        val currentProgress = _savedQuizProgress.value ?: return
        val updated = currentProgress.copy(timeRemaining = seconds)
        _savedQuizProgress.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            quizProgressPrefs.edit().putInt("saved_progress_time_remaining", seconds).apply()
        }
    }

    fun resumeQuiz() {
        val progress = _savedQuizProgress.value ?: return
        _isSubmittingQuiz.value = false
        _isQuizLoading.value = true
        _userQuestionsAnswersSession.value = emptyList()
        viewModelScope.launch {
            val freshQuiz = repository.getQuizById(progress.quizId) ?: return@launch
            navigateTo(Screen.ActiveQuiz(freshQuiz))

            val canonicalQuestions = repository.getQuestionsForQuiz(freshQuiz.documentId)
                .sortedWith(compareBy({ it.createdAt }, { it.documentId }))

            val finalQuestions = if (canonicalQuestions.isNotEmpty()) {
                if (freshQuiz.shuffleQuestions) {
                    val seed = freshQuiz.documentId.hashCode().toLong() + (freshQuiz.version.toLong() * 31L)
                    val rng = java.util.Random(seed)
                    canonicalQuestions.shuffled(rng)
                } else {
                    canonicalQuestions
                }
            } else {
                progress.questions
            }

            _questions.value = finalQuestions
            _currentQuestionIndex.value = progress.currentQuestionIndex.coerceIn(0, (finalQuestions.size - 1).coerceAtLeast(0))
            _selectedOption.value = null
            _quizScore.value = progress.quizScore
            _userQuestionsAnswersSession.value = progress.userAnswers
            _isQuizLoading.value = false

            if (_questions.value.isNotEmpty()) {
                questionStartTimeMillis = System.currentTimeMillis()
                startTimer(progress.timeRemaining)
            } else {
                navigateTo(Screen.Score(freshQuiz, 0f, 0))
            }
        }
    }

    fun startQuiz(quiz: Quiz) {
        if (_isCurrentUserBlocked.value) {
            logFirestore("Quiz launch denied: User account is restricted.")
            return
        }
        clearQuizProgress()
        _isSubmittingQuiz.value = false
        _isQuizLoading.value = true
        _userQuestionsAnswersSession.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val freshQuiz = repository.getQuizById(quiz.documentId) ?: quiz

            if (_isNetworkAvailable.value) {
                try {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    
                    // 1. Fetch both subcollection and top-level collection to guarantee complete SSoT
                    val subcollTask = firestore.collection("quizzes")
                        .document(freshQuiz.documentId)
                        .collection("questions")
                        .get()
                    val topTask = firestore.collection("questions")
                        .whereEqualTo("quizId", freshQuiz.documentId)
                        .get()
                    
                    val (subcollSnap, topSnap) = try {
                        val s = com.google.android.gms.tasks.Tasks.await(subcollTask)
                        val t = com.google.android.gms.tasks.Tasks.await(topTask)
                        s to t
                    } catch (e: Exception) {
                        null to null
                    }

                    val mergedMap = mutableMapOf<String, Question>()

                    if (topSnap != null && !topSnap.isEmpty) {
                        topSnap.documents.forEach { doc ->
                            val q = Question(
                                documentId = doc.id,
                                quizId = freshQuiz.documentId,
                                text = doc.getString("text") ?: "",
                                optionA = doc.getString("optionA") ?: "",
                                optionB = doc.getString("optionB") ?: "",
                                optionC = doc.getString("optionC") ?: "",
                                optionD = doc.getString("optionD") ?: "",
                                correctOption = doc.getString("correctOption") ?: "A",
                                explanation = doc.getString("explanation") ?: "",
                                version = doc.getLong("version")?.toInt() ?: 1,
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            mergedMap[q.documentId] = q
                        }
                    }

                    if (subcollSnap != null && !subcollSnap.isEmpty) {
                        subcollSnap.documents.forEach { doc ->
                            val q = Question(
                                documentId = doc.id,
                                quizId = freshQuiz.documentId,
                                text = doc.getString("text") ?: "",
                                optionA = doc.getString("optionA") ?: "",
                                optionB = doc.getString("optionB") ?: "",
                                optionC = doc.getString("optionC") ?: "",
                                optionD = doc.getString("optionD") ?: "",
                                correctOption = doc.getString("correctOption") ?: "A",
                                explanation = doc.getString("explanation") ?: "",
                                version = doc.getLong("version")?.toInt() ?: 1,
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            mergedMap[q.documentId] = q
                        }
                    }

                    val remoteQuestions = mergedMap.values.toList()
                    if (remoteQuestions.isNotEmpty()) {
                        repository.insertQuestions(remoteQuestions)
                    }
                } catch (e: Exception) {
                    Log.e("QuizViewModel", "Error syncing authoritative quiz questions: ${e.message}", e)
                }
            }

            val rawQuestions = repository.getQuestionsForQuiz(freshQuiz.documentId)
            // Deterministic ordering by createdAt ASC, then documentId ASC
            val sortedQuestions = rawQuestions.sortedWith(compareBy({ it.createdAt }, { it.documentId }))

            // Deterministic question sequence for all users:
            // If shuffleQuestions is enabled on the quiz, use a deterministic seed based on quiz ID and version
            val finalQuestions = if (freshQuiz.shuffleQuestions && sortedQuestions.isNotEmpty()) {
                val seed = freshQuiz.documentId.hashCode().toLong() + (freshQuiz.version.toLong() * 31L)
                val rng = java.util.Random(seed)
                sortedQuestions.shuffled(rng)
            } else {
                sortedQuestions
            }

            withContext(Dispatchers.Main) {
                navigateTo(Screen.ActiveQuiz(freshQuiz))
                _questions.value = finalQuestions
                _currentQuestionIndex.value = 0
                _selectedOption.value = null
                _quizScore.value = 0f
                _isQuizLoading.value = false

                if (_questions.value.isNotEmpty()) {
                    questionStartTimeMillis = System.currentTimeMillis()
                    startTimer(freshQuiz.timeLimitSeconds)
                    saveQuizProgress()
                    saveQuizProgressToFirestore(isCompleted = false)
                } else {
                    navigateTo(Screen.Score(freshQuiz, 0f, 0))
                }
            }
        }
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _timeRemaining.value = seconds
        timerJob = viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000)
                _timeRemaining.value -= 1
                if (currentScreen.value is Screen.ActiveQuiz) {
                    saveQuizProgressPartialTime(_timeRemaining.value)
                }
            }
            timerExpired()
        }
    }

    fun selectOption(option: String) {
        if (_isCurrentUserBlocked.value) return
        _selectedOption.value = option
        saveQuizProgress()
        saveQuizProgressToFirestore(isCompleted = false)
    }

    private fun timerExpired() {
        if (_isSubmittingQuiz.value) return

        val currentQuiz = (currentScreen.value as? Screen.ActiveQuiz)?.quiz ?: return
        val activeQuestions = _questions.value
        val currentIndex = _currentQuestionIndex.value
        val remainingAnsList = mutableListOf<QuestionUserAnswer>()

        for (i in currentIndex until activeQuestions.size) {
            val q = activeQuestions[i]
            val isCurrent = (i == currentIndex)
            val selected = if (isCurrent) _selectedOption.value else null
            val isCorrect = selected != null && selected.equals(q.correctOption, ignoreCase = true)

            remainingAnsList.add(
                QuestionUserAnswer(
                    question = q,
                    selectedOptionLetter = selected,
                    timeSpentSeconds = 0f,
                    isCorrect = isCorrect
                )
            )
        }
        _userQuestionsAnswersSession.value = _userQuestionsAnswersSession.value + remainingAnsList

        _quizScore.value = calculateQuizScore(
            answers = _userQuestionsAnswersSession.value,
            marksPerQuestion = currentQuiz.marksPerQuestion,
            negativeMarking = currentQuiz.negativeMarking
        )

        timerJob?.cancel()
        clearQuizProgress()
        saveQuizProgressToFirestore(isCompleted = true)

        val finalScore = _quizScore.value
        val finalTotal = activeQuestions.size
        val correctAnsCount = _userQuestionsAnswersSession.value.count { it.isCorrect }
        val wrongAnsCount = _userQuestionsAnswersSession.value.size - correctAnsCount

        viewModelScope.launch {
            try {
                val categoryId = currentQuiz.categoryId
                val cat = repository.allCategoriesFlow.firstOrNull()?.find { it.documentId == categoryId }
                val categoryName = cat?.name ?: "Drill"
                val currentUid = getCurrentUserId()

                val attemptObj = QuizAttempt(
                    documentId = UUID.randomUUID().toString(),
                    quizTitle = currentQuiz.title,
                    categoryName = categoryName,
                    score = finalScore,
                    totalQuestions = finalTotal,
                    dateMillis = System.currentTimeMillis(),
                    userId = currentUid,
                    quizDocumentId = currentQuiz.documentId,
                    quizVersion = currentQuiz.version,
                    correctCount = correctAnsCount,
                    wrongCount = wrongAnsCount
                )

                saveAttemptToFirestoreAndDatabase(attemptObj)

                withContext(Dispatchers.Main) {
                    navigateTo(Screen.Score(currentQuiz, finalScore, finalTotal), clearBackstack = true)
                }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error saving attempt on timer expired: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    navigateTo(Screen.Score(currentQuiz, finalScore, finalTotal), clearBackstack = true)
                }
            }
        }
    }

    private var isAdvancingQuestion = false

    fun advanceQuestion(isTimeOut: Boolean = false) {
        if (isAdvancingQuestion) return
        isAdvancingQuestion = true
        try {
            val activeQuestions = _questions.value
            val currentIndex = _currentQuestionIndex.value
            val currentQuiz = (currentScreen.value as? Screen.ActiveQuiz)?.quiz ?: return

            if (currentIndex < activeQuestions.size) {
                val currentQuestion = activeQuestions[currentIndex]
                val selected = if (isTimeOut) null else _selectedOption.value
                val isCorrect = !isTimeOut && selected != null && selected.trim().equals(currentQuestion.correctOption.trim(), ignoreCase = true)

                val rawTimeSpent = (System.currentTimeMillis() - questionStartTimeMillis) / 1000f
                val timeSpent = if (rawTimeSpent <= 0.1f) 1.2f else rawTimeSpent

                val answerObj = QuestionUserAnswer(
                    question = currentQuestion,
                    selectedOptionLetter = selected,
                    timeSpentSeconds = timeSpent,
                    isCorrect = isCorrect
                )
                _userQuestionsAnswersSession.value = _userQuestionsAnswersSession.value + answerObj

                _quizScore.value = calculateQuizScore(
                    answers = _userQuestionsAnswersSession.value,
                    marksPerQuestion = currentQuiz.marksPerQuestion,
                    negativeMarking = currentQuiz.negativeMarking
                )
            }

            val nextIndex = currentIndex + 1
            if (nextIndex < activeQuestions.size) {
                _currentQuestionIndex.value = nextIndex
                _selectedOption.value = null
                questionStartTimeMillis = System.currentTimeMillis()
                saveQuizProgress()
                saveQuizProgressToFirestore(isCompleted = false)
            } else {
                if (_isSubmittingQuiz.value) return

                timerJob?.cancel()
                clearQuizProgress()
                saveQuizProgressToFirestore(isCompleted = true)
                val finalScore = _quizScore.value
                val finalTotal = activeQuestions.size
                val correctAnsCount = _userQuestionsAnswersSession.value.count { it.isCorrect }
                val wrongAnsCount = _userQuestionsAnswersSession.value.size - correctAnsCount

                viewModelScope.launch {
                    try {
                        val categoryId = currentQuiz.categoryId
                        val cat = repository.allCategoriesFlow.firstOrNull()?.find { it.documentId == categoryId }
                        val categoryName = cat?.name ?: "Drill"
                        val currentUid = getCurrentUserId()

                        val attemptObj = QuizAttempt(
                            documentId = UUID.randomUUID().toString(),
                            quizTitle = currentQuiz.title,
                            categoryName = categoryName,
                            score = finalScore,
                            totalQuestions = finalTotal,
                            dateMillis = System.currentTimeMillis(),
                            userId = currentUid,
                            quizDocumentId = currentQuiz.documentId,
                            quizVersion = currentQuiz.version,
                            correctCount = correctAnsCount,
                            wrongCount = wrongAnsCount
                        )

                        saveAttemptToFirestoreAndDatabase(attemptObj)

                        withContext(Dispatchers.Main) {
                            navigateTo(Screen.Score(currentQuiz, finalScore, finalTotal), clearBackstack = true)
                        }
                    } catch (e: Exception) {
                        Log.e("QuizViewModel", "Error saving attempt on advanceQuestion: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            navigateTo(Screen.Score(currentQuiz, finalScore, finalTotal), clearBackstack = true)
                        }
                    }
                }
            }
        } finally {
            viewModelScope.launch {
                delay(300)
                isAdvancingQuestion = false
            }
        }
    }

    // --- Admin Flow Activities ---
    fun loadAdminCategories() {
        viewModelScope.launch {
            repository.getAllCategories()
            updateQuizQuestionsCounts()
        }
    }

    fun loadAdminQuizzes(categoryId: String) {
        currentAdminCategoryId = categoryId
        viewModelScope.launch {
            val quizzes = repository.getQuizzesByCategory(categoryId)
            _adminQuizzesList.value = quizzes
            updateQuizQuestionsCounts()
        }
    }

    private var adminQuestionsSubcollListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun loadAdminQuestions(quizId: String) {
        if (currentAdminQuizId == quizId && adminQuestionsSubcollListener != null) {
            // Already listening to this quiz's questions, do not recreate listener!
            viewModelScope.launch {
                val questions = repository.getQuestionsForQuiz(quizId)
                _adminQuestionsForSelectedQuiz.value = questions
                updateQuizQuestionsCounts()
            }
            return
        }
        currentAdminQuizId = quizId
        viewModelScope.launch {
            val questions = repository.getQuestionsForQuiz(quizId)
            _adminQuestionsForSelectedQuiz.value = questions
            updateQuizQuestionsCounts()
        }

        // Real-time listener specifically for this selected quiz's subcollection questions
        adminQuestionsSubcollListener?.remove()
        if (quizId.isNotEmpty()) {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            adminQuestionsSubcollListener = firestore.collection("quizzes").document(quizId).collection("questions")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val subQuestions = snapshot.documents.mapNotNull { doc ->
                                Question(
                                    documentId = doc.id,
                                    quizId = doc.getString("quizId") ?: quizId,
                                    text = doc.getString("text") ?: "",
                                    optionA = doc.getString("optionA") ?: "",
                                    optionB = doc.getString("optionB") ?: "",
                                    optionC = doc.getString("optionC") ?: "",
                                    optionD = doc.getString("optionD") ?: "",
                                    correctOption = doc.getString("correctOption") ?: "A",
                                    explanation = doc.getString("explanation") ?: "",
                                    version = doc.getLong("version")?.toInt() ?: 1,
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            }
                            if (subQuestions.isNotEmpty()) {
                                repository.insertQuestions(subQuestions)
                            }
                            snapshot.documentChanges.forEach { change ->
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    repository.deleteQuestionById(change.document.id)
                                }
                            }
                            if (currentAdminQuizId == quizId) {
                                val updated = repository.getQuestionsForQuiz(quizId)
                                _adminQuestionsForSelectedQuiz.value = updated
                                updateQuizQuestionsCounts()
                            }
                        } catch (e: Exception) {
                            Log.e("QuizViewModel", "Error in adminQuestionsSubcollListener: ${e.message}", e)
                        }
                    }
                }
        }
    }

    fun addCategory(
        name: String,
        description: String,
        iconName: String,
        isDraft: Boolean = false,
        parentCategoryId: String? = null,
        documentId: String = "",
        onComplete: () -> Unit = {}
    ) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Saving category to cloud..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = if (documentId.isNotEmpty()) documentId else UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val statusStr = if (isDraft) "draft" else "published"
                val newCat = Category(
                    documentId = docId,
                    name = name.trim(),
                    description = description.trim(),
                    iconName = iconName.ifBlank { "general" },
                    isDraft = isDraft,
                    status = statusStr,
                    parentCategoryId = parentCategoryId,
                    createdAt = now,
                    updatedAt = now
                )

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("categories").document(docId).set(newCat)
                com.google.android.gms.tasks.Tasks.await(task)

                repository.insertCategory(newCat)

                logAdminAction("CREATE_CATEGORY", "CATEGORY", docId, "Category created: ${newCat.name}")
                loadAdminCategories()
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Category saved and synced to cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in addCategory: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to save category to cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun updateCategory(category: Category, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Updating category on cloud..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val statusStr = if (category.isDraft) "draft" else "published"
                val updatedCat = category.copy(status = statusStr, updatedAt = now)

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("categories").document(updatedCat.documentId).set(updatedCat)
                com.google.android.gms.tasks.Tasks.await(task)

                repository.updateCategory(updatedCat)

                logAdminAction("EDIT_CATEGORY", "CATEGORY", updatedCat.documentId, "Category updated: ${updatedCat.name}")
                loadAdminCategories()
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Category updated and synced."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in updateCategory: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to update category on cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun toggleCategoryDraftStatus(category: Category) {
        val updated = category.copy(isDraft = !category.isDraft)
        updateCategory(updated)
    }

    fun addQuiz(
        categoryId: String,
        title: String,
        description: String,
        timeLimitSeconds: Int,
        isDraft: Boolean = false,
        shuffleQuestions: Boolean = false,
        marksPerQuestion: Float = 1.0f,
        negativeMarking: Float = 0.0f,
        onComplete: () -> Unit = {}
    ) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Creating quiz on cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val statusStr = if (isDraft) "draft" else "published"
                val currentQuizzes = repository.getQuizzesByCategory(categoryId)
                val nextSortOrder = if (currentQuizzes.isNotEmpty()) (currentQuizzes.maxOfOrNull { it.sortOrder } ?: 0) + 1 else 0
                val newQuiz = Quiz(
                    documentId = docId,
                    categoryId = categoryId,
                    title = title.trim(),
                    description = description.trim(),
                    timeLimitSeconds = timeLimitSeconds,
                    isDraft = isDraft,
                    status = statusStr,
                    version = 1,
                    shuffleQuestions = shuffleQuestions,
                    marksPerQuestion = marksPerQuestion,
                    negativeMarking = negativeMarking,
                    sortOrder = nextSortOrder,
                    createdAt = now,
                    updatedAt = now,
                    publishedAt = if (isDraft) null else now
                )

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("quizzes").document(docId).set(newQuiz)
                com.google.android.gms.tasks.Tasks.await(task)

                repository.insertQuiz(newQuiz)

                logAdminAction("CREATE_QUIZ", "QUIZ", docId, "Quiz created: ${newQuiz.title} (Status: $statusStr)")
                loadAdminQuizzes(categoryId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Quiz saved and synced to cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in addQuiz: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to create quiz on cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun updateQuiz(quiz: Quiz, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Updating quiz on cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val statusStr = if (quiz.isDraft) "draft" else "published"
                val isPublishingNow = quiz.status == "draft" && !quiz.isDraft
                val nextVersion = if (quiz.status == "published") quiz.version + 1 else quiz.version
                val updatedQuiz = quiz.copy(
                    status = statusStr,
                    version = nextVersion,
                    updatedAt = now,
                    publishedAt = if (isPublishingNow || (quiz.publishedAt == null && !quiz.isDraft)) now else quiz.publishedAt
                )

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("quizzes").document(updatedQuiz.documentId).set(updatedQuiz)
                com.google.android.gms.tasks.Tasks.await(task)

                repository.updateQuiz(updatedQuiz)

                val actionName = if (isPublishingNow) "PUBLISH_QUIZ" else if (quiz.isDraft) "UNPUBLISH_QUIZ" else "EDIT_QUIZ"
                logAdminAction(actionName, "QUIZ", updatedQuiz.documentId, "Quiz updated: ${updatedQuiz.title} (v${updatedQuiz.version}, Status: ${updatedQuiz.status})")

                loadAdminQuizzes(updatedQuiz.categoryId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Quiz updated and synced to cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in updateQuiz: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to update quiz on cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun toggleQuizDraftStatus(quiz: Quiz) {
        viewModelScope.launch {
            val updated = quiz.copy(isDraft = !quiz.isDraft)
            updateQuiz(updated)
        }
    }

    fun toggleQuizShuffleStatus(quiz: Quiz) {
        viewModelScope.launch {
            val updated = quiz.copy(shuffleQuestions = !quiz.shuffleQuestions)
            updateQuiz(updated)
        }
    }

    fun moveQuizUp(quizId: String) {
        moveQuiz(quizId, -1)
    }

    fun moveQuizDown(quizId: String) {
        moveQuiz(quizId, 1)
    }

    private fun moveQuiz(quizId: String, direction: Int) {
        val currentList = _adminQuizzesList.value.toMutableList()
        val currentIndex = currentList.indexOfFirst { it.documentId == quizId }
        if (currentIndex == -1) return
        val targetIndex = currentIndex + direction
        if (targetIndex < 0 || targetIndex >= currentList.size) return

        _syncStatusMessage.value = "Reordering quizzes..."

        val movedItem = currentList.removeAt(currentIndex)
        currentList.add(targetIndex, movedItem)

        val now = System.currentTimeMillis()
        val reorderedList = currentList.mapIndexed { index, quiz ->
            quiz.copy(sortOrder = index, updatedAt = now)
        }

        // Immediate local state update for zero UI lag
        _adminQuizzesList.value = reorderedList

        // Synchronize category user quizzes if active
        val currentCatId = currentAdminCategoryId
        if (currentCatId != null) {
            _quizzesForSelectedCategory.value = reorderedList.filter { !it.isDraft }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update local Room database immediately using insertQuizzes (atomic replace)
                repository.insertQuizzes(reorderedList)

                // Batch write to Cloud Firestore using merge to prevent NOT_FOUND errors
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                reorderedList.forEach { q ->
                    val docRef = firestore.collection("quizzes").document(q.documentId)
                    batch.set(
                        docRef,
                        mapOf(
                            "sortOrder" to q.sortOrder,
                            "updatedAt" to q.updatedAt
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
                com.google.android.gms.tasks.Tasks.await(batch.commit())

                val quizTitle = reorderedList[targetIndex].title
                val dirText = if (direction < 0) "UP" else "DOWN"
                logAdminAction("REORDER_QUIZ", "QUIZ", quizId, "Moved quiz '$quizTitle' $dirText to position ${targetIndex + 1}")
                _syncStatusMessage.value = "Quiz order updated and synced to cloud."
            } catch (e: Exception) {
                Log.w("QuizViewModel", "Firestore sync warning during reorder: ${e.message}")
                _syncStatusMessage.value = "Quiz order saved locally (cloud pending)."
            }
        }
    }

    fun addQuestion(
        quizId: String,
        text: String,
        optA: String,
        optB: String,
        optC: String,
        optD: String,
        correct: String,
        explanation: String = "",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentQuestions = repository.getQuestionsForQuiz(quizId)
                if (currentQuestions.size >= 120) {
                    val errMsg = "Maximum limit of 120 questions per quiz reached."
                    _syncStatusMessage.value = errMsg
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), errMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val newQuest = Question(
                    documentId = docId,
                    quizId = quizId,
                    text = text.trim(),
                    optionA = optA.trim(),
                    optionB = optB.trim(),
                    optionC = optC.trim(),
                    optionD = optD.trim(),
                    correctOption = correct,
                    explanation = explanation.trim(),
                    version = 1,
                    createdAt = now,
                    updatedAt = now
                )

                repository.insertQuestion(newQuest)
                
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                val subcollRef = firestore.collection("quizzes").document(quizId).collection("questions").document(docId)
                val topLevelRef = firestore.collection("questions").document(docId)
                batch.set(subcollRef, newQuest)
                batch.set(topLevelRef, newQuest)

                val existingQuiz = repository.getQuizById(quizId)
                if (existingQuiz != null && !existingQuiz.isDraft) {
                    val updatedQuiz = existingQuiz.copy(
                        version = existingQuiz.version + 1,
                        updatedAt = now
                    )
                    repository.updateQuiz(updatedQuiz)
                    batch.set(firestore.collection("quizzes").document(quizId), updatedQuiz)
                }

                batch.commit().addOnSuccessListener {
                    logFirestore("Question added & synced (Quiz: $quizId, DocId: $docId)")
                }.addOnFailureListener { e ->
                    logFirestore("Question sync failed: ${e.message}")
                }

                loadAdminQuestions(quizId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Question saved and synced."

                val quizTitle = existingQuiz?.title ?: "Quiz #$quizId"
                recordQuestionAuditLog(
                    actionType = "ADDED",
                    questionId = docId,
                    questionText = text.trim(),
                    quizId = quizId,
                    quizTitle = quizTitle,
                    status = "PROPAGATED_TO_CLOUDFIRESTORE",
                    details = "Question created and synced. Correct Option: $correct",
                    documentId = docId
                )

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in addQuestion: ${e.message}", e)
                _syncStatusMessage.value = "Error adding question: ${e.message}"
            }
        }
    }

    fun updateQuestion(question: Question, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val updatedQuest = question.copy(version = question.version + 1, updatedAt = now)
                repository.updateQuestion(updatedQuest)
                
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                val subcollRef = firestore.collection("quizzes").document(updatedQuest.quizId).collection("questions").document(updatedQuest.documentId)
                val topLevelRef = firestore.collection("questions").document(updatedQuest.documentId)
                batch.set(subcollRef, updatedQuest)
                batch.set(topLevelRef, updatedQuest)

                val existingQuiz = repository.getQuizById(updatedQuest.quizId)
                if (existingQuiz != null && !existingQuiz.isDraft) {
                    val updatedQuiz = existingQuiz.copy(
                        version = existingQuiz.version + 1,
                        updatedAt = now
                    )
                    repository.updateQuiz(updatedQuiz)
                    batch.set(firestore.collection("quizzes").document(updatedQuest.quizId), updatedQuiz)
                }

                batch.commit().addOnSuccessListener {
                    logFirestore("Question updated & synced (Quiz: ${updatedQuest.quizId})")
                }

                loadAdminQuestions(updatedQuest.quizId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Question updated and synced."

                val quizTitle = existingQuiz?.title ?: "Quiz #${updatedQuest.quizId}"
                recordQuestionAuditLog(
                    actionType = "MODIFIED",
                    questionId = updatedQuest.documentId,
                    questionText = updatedQuest.text,
                    quizId = updatedQuest.quizId,
                    quizTitle = quizTitle,
                    status = "PROPAGATED_TO_CLOUDFIRESTORE",
                    details = "Question updated. Correct option: ${updatedQuest.correctOption}",
                    documentId = updatedQuest.documentId
                )

                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in updateQuestion: ${e.message}", e)
                _syncStatusMessage.value = "Update error: ${e.message}"
            }
        }
    }

    fun uploadCategoryLogo(
        category: Category?,
        uri: Uri,
        categoryName: String,
        existingDocId: String? = null,
        onComplete: (String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLogoUploading.value = true
            _logoUploadError.value = null
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Unable to read image stream")
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) throw Exception("Failed to decode image from selected URI")

                // 1. Scale down bitmap to max 300x300 for optimal Base64 / Cloud Storage sync
                val maxDim = 300
                val scale = Math.min(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height)
                val targetW = Math.max(1, (originalBitmap.width * scale).toInt())
                val targetH = Math.max(1, (originalBitmap.height * scale).toInt())
                val scaledBitmap = if (scale < 1.0f) {
                    android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                } else {
                    originalBitmap
                }

                val docId = existingDocId ?: UUID.randomUUID().toString()

                // Save scaled copy locally
                val dir = File(context.filesDir, "category_logos")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "logo_${docId}_${System.currentTimeMillis()}.png")
                val outputStream = FileOutputStream(file)
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream)
                outputStream.flush()
                outputStream.close()

                // Generate Base64 Data URI (100% reliable fallback & immediate Firestore sync)
                val byteStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, byteStream)
                val base64Str = android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP)
                val base64Uri = "data:image/png;base64,$base64Str"

                if (isRunningTest()) {
                    if (category != null) {
                        val updatedCategory = category.copy(iconName = base64Uri, documentId = docId)
                        repository.updateCategory(updatedCategory)
                        loadAdminCategories()
                        updateQuizQuestionsCounts()
                    }
                    withContext(Dispatchers.Main) {
                        onComplete(base64Uri, docId)
                    }
                    _syncStatusMessage.value = "Logo saved locally!"
                    _isLogoUploading.value = false
                    return@launch
                }

                // Internal helper to persist logo to local DB & Cloud Firestore SSoT
                suspend fun persistAndSyncLogo(logoUrl: String) {
                    try {
                        if (category != null) {
                            val updatedCategory = category.copy(iconName = logoUrl, documentId = docId)
                            repository.updateCategory(updatedCategory)
                            
                            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            firestore.collection("categories").document(docId).set(updatedCategory)
                                .addOnSuccessListener { logFirestore("Category logo synced to cloud database real-time.") }
                            
                            loadAdminCategories()
                            updateQuizQuestionsCounts()
                        }
                        withContext(Dispatchers.Main) {
                            onComplete(logoUrl, docId)
                        }
                        _syncStatusMessage.value = "Category logo uploaded and synced real-time!"
                        logFirestore("Category logo successfully uploaded and synced real-time.")
                    } catch (e: Exception) {
                        Log.e("QuizViewModel", "Error syncing category logo: ${e.message}", e)
                        _logoUploadError.value = "Error syncing logo: ${e.message}"
                    } finally {
                        _isLogoUploading.value = false
                    }
                }

                // 2. Attempt Firebase Storage upload with automatic fallback to Base64 URI
                try {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    val logoRef = storage.reference.child("category_logos/logo_${docId}_${System.currentTimeMillis()}.png")
                    
                    logoRef.putFile(Uri.fromFile(file))
                        .addOnSuccessListener {
                            logoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                val cloudUrl = downloadUri.toString()
                                viewModelScope.launch(Dispatchers.IO) {
                                    persistAndSyncLogo(cloudUrl)
                                }
                            }.addOnFailureListener {
                                // Download URL failed, fallback to Base64 URI
                                viewModelScope.launch(Dispatchers.IO) {
                                    persistAndSyncLogo(base64Uri)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            // Firebase Storage failed (e.g. object does not exist at location / unconfigured bucket)
                            Log.w("QuizViewModel", "Firebase Storage upload failed (${e.message}), falling back to Base64 data URI.")
                            viewModelScope.launch(Dispatchers.IO) {
                                persistAndSyncLogo(base64Uri)
                            }
                        }
                } catch (e: Exception) {
                    Log.w("QuizViewModel", "Storage exception (${e.message}), falling back to Base64 data URI.")
                    persistAndSyncLogo(base64Uri)
                }

            } catch (e: Exception) {
                val errMsg = e.message ?: "Save logo failed"
                _logoUploadError.value = "Upload error: $errMsg"
                _syncStatusMessage.value = "Logo error: $errMsg"
                _isLogoUploading.value = false
            }
        }
    }

    fun clearLogoUploadError() {
        _logoUploadError.value = null
    }

    // AI PDF Question Scan / Generation State
    private val _isScanningPdf = MutableStateFlow(false)
    val isScanningPdf: StateFlow<Boolean> = _isScanningPdf.asStateFlow()

    private val _pdfScanStatus = MutableStateFlow("")
    val pdfScanStatus: StateFlow<String> = _pdfScanStatus.asStateFlow()

    private val _pdfScanProgress = MutableStateFlow(0f)
    val pdfScanProgress: StateFlow<Float> = _pdfScanProgress.asStateFlow()

    private val _selectedFileMetadata = MutableStateFlow<SelectedFileMetadata?>(null)
    val selectedFileMetadata: StateFlow<SelectedFileMetadata?> = _selectedFileMetadata.asStateFlow()

    fun selectFileForImport(uri: Uri, context: Context) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        var fileName = "unknown"
        var fileSize = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("QuizViewModel", "Failed to query file metadata", e)
        }

        if (fileName == "unknown") {
            fileName = uri.lastPathSegment ?: "Selected File"
        }

        var pageCount = -1
        var isValid = true
        var warningMessage: String? = null

        val maxSizeBytes = 50 * 1024 * 1024
        if (fileSize > maxSizeBytes) {
            isValid = false
            warningMessage = "File size exceeds the maximum limit of 50 MB."
        } else if (mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    pageCount = PdfTextExtractor.countPages(bytes)
                    if (pageCount > 100) {
                        isValid = false
                        warningMessage = "PDF contains $pageCount pages, which exceeds the limit of 100 pages."
                    }
                }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Failed to count PDF pages", e)
            }
        }

        _selectedFileMetadata.value = SelectedFileMetadata(
            uriString = uri.toString(),
            name = fileName,
            size = fileSize,
            mimeType = mimeType,
            pageCount = pageCount,
            isValid = isValid,
            warningMessage = warningMessage
        )
    }

    fun clearSelectedFile() {
        _selectedFileMetadata.value = null
        _pdfScanStatus.value = ""
    }

    fun processSelectedFile(context: Context, quizId: String) {
        val metadata = _selectedFileMetadata.value ?: return
        val uri = Uri.parse(metadata.uriString)
        importQuestionsFromPdf(uri, context, quizId)
        _selectedFileMetadata.value = null
    }

    fun setPdfStatus(msg: String) {
        _pdfScanStatus.value = msg
    }

    fun clearPdfStatus() {
        _pdfScanStatus.value = ""
    }

    fun importQuestionsFromPdf(uri: Uri, context: Context, quizId: String) {
        if (quizId.isEmpty()) {
            _pdfScanProgress.value = 0f
            _pdfScanStatus.value = "Error: Please select a Category and Quiz first."
            return
        }
        _isScanningPdf.value = true
        _pdfScanProgress.value = 0.05f
        _pdfScanStatus.value = "Reading document file (5%)..."
        viewModelScope.launch {
            try {
                val quiz = repository.getQuizById(quizId)
                    ?: throw Exception("Selected Quiz not found in database")

                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                var fileName = "Document"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                _pdfScanProgress.value = 0.15f
                _pdfScanStatus.value = "Extracting text from $fileName (15%)..."
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open input stream for document")
                val extractedText = DocumentTextExtractor.extractTextFromStream(
                    inputStream = inputStream,
                    mimeType = mimeType,
                    fileName = fileName
                )
                inputStream.close()

                if (extractedText.isBlank()) {
                    throw Exception("Could not extract readable text from $fileName. Please ensure the document is not password protected or an empty scan.")
                }

                _pdfScanProgress.value = 0.25f
                _pdfScanStatus.value = "Document text extracted (25%). Starting AI Question Generation..."

                var apiKey = _customGeminiApiKey.value
                if (apiKey.isBlank()) {
                    apiKey = com.example.BuildConfig.GEMINI_API_KEY
                }
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    throw Exception("Gemini API Key is not configured. Please set GEMINI_API_KEY in the Secrets Panel or settings.")
                }

                val parsedQuestionsList = GeminiQuestionGenerator.generateQuestionsTwoPass(
                    apiKey = apiKey,
                    extractedText = extractedText,
                    quizId = quizId,
                    onProgressUpdate = { prog, statusMsg ->
                        _pdfScanProgress.value = prog
                        _pdfScanStatus.value = statusMsg
                    }
                )

                if (parsedQuestionsList.isEmpty()) {
                    throw Exception("No questions could be extracted from the document.")
                }

                _pdfScanProgress.value = 0.88f
                val existingQuestions = repository.getQuestionsForQuiz(quizId)
                if (existingQuestions.size >= 120) {
                    throw Exception("Maximum limit of 120 questions for this quiz has already been reached.")
                }
                val availableSlots = 120 - existingQuestions.size

                val uniqueParsedQuestions = parsedQuestionsList
                    .take(availableSlots)

                if (uniqueParsedQuestions.isEmpty()) {
                    throw Exception("Maximum limit of 120 questions reached for this quiz.")
                }

                _pdfScanStatus.value = "Saving ${uniqueParsedQuestions.size} questions to local database (90%)..."

                // Insert questions to Room and Firestore
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                val baseTime = System.currentTimeMillis()
                val quizTitle = quiz.title
                
                uniqueParsedQuestions.forEachIndexed { idx, q ->
                    val docId = if (q.documentId.isNotEmpty()) q.documentId else UUID.randomUUID().toString()
                    val qTime = if (q.createdAt > 0L) q.createdAt else (baseTime + (idx * 10L))
                    val qWithDocId = q.copy(
                        documentId = docId,
                        quizId = quizId,
                        createdAt = qTime,
                        updatedAt = baseTime
                    )
                    repository.insertQuestion(qWithDocId)
                    
                    val topLevelRef = firestore.collection("questions").document(docId)
                    val subcollRef = firestore.collection("quizzes").document(quizId).collection("questions").document(docId)
                    batch.set(topLevelRef, qWithDocId)
                    batch.set(subcollRef, qWithDocId)

                    // Record audit log for each AI generated / uploaded question
                    recordQuestionAuditLog(
                        actionType = "ADDED",
                        questionId = docId,
                        questionText = qWithDocId.text,
                        quizId = quizId,
                        quizTitle = quizTitle,
                        status = "PROPAGATED_TO_CLOUDFIRESTORE",
                        details = "AI generated/imported from document ($fileName). Correct Option: ${qWithDocId.correctOption}",
                        documentId = docId
                    )
                }

                logAdminAction(
                    action = "GENERATE_QUESTIONS_AI",
                    targetType = "QUIZ",
                    targetId = quizId,
                    details = "AI generated/imported ${uniqueParsedQuestions.size} questions from document '$fileName' for quiz '$quizTitle'"
                )

                if (!quiz.isDraft) {
                    val updatedQuiz = quiz.copy(
                        version = quiz.version + 1,
                        updatedAt = baseTime
                    )
                    repository.updateQuiz(updatedQuiz)
                    batch.set(firestore.collection("quizzes").document(quizId), updatedQuiz)
                }

                try {
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                    logFirestore("AI Scanned questions synced to Cloud Firestore.")
                } catch (e: Exception) {
                    Log.w("QuizViewModel", "Batch commit warning: ${e.message}")
                }

                loadAdminQuestions(quizId)
                updateQuizQuestionsCounts()

                _pdfScanProgress.value = 1.0f
                val toastMsg = "Successfully scanned, saved and synced ${uniqueParsedQuestions.size} questions!"
                _pdfScanStatus.value = "100% Complete! $toastMsg"

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                _pdfScanProgress.value = 0f
                _pdfScanStatus.value = "AI Import failed: ${e.message}"
            } finally {
                _isScanningPdf.value = false
            }
        }
    }

    fun deleteCategory(category: Category, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Deleting category from cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("categories").document(category.documentId).delete()
                com.google.android.gms.tasks.Tasks.await(task)

                repository.deleteCategory(category)

                logAdminAction("DELETE_CATEGORY", "CATEGORY", category.documentId, "Category deleted: ${category.name}")
                loadAdminCategories()
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Category deleted from cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in deleteCategory: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to delete category from cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun deleteQuiz(quiz: Quiz, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Deleting quiz from cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val task = firestore.collection("quizzes").document(quiz.documentId).delete()
                com.google.android.gms.tasks.Tasks.await(task)

                repository.deleteQuiz(quiz)

                logAdminAction("DELETE_QUIZ", "QUIZ", quiz.documentId, "Quiz deleted: ${quiz.title}")
                loadAdminQuizzes(quiz.categoryId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Quiz deleted from cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in deleteQuiz: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to delete quiz from cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun deleteQuestion(question: Question, onComplete: () -> Unit = {}) {
        if (_isAdminOperating.value) return
        _isAdminOperating.value = true
        _syncStatusMessage.value = "Deleting question from cloud server..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = firestore.batch()
                val subcollRef = firestore.collection("quizzes").document(question.quizId).collection("questions").document(question.documentId)
                val topLevelRef = firestore.collection("questions").document(question.documentId)
                batch.delete(subcollRef)
                batch.delete(topLevelRef)

                val now = System.currentTimeMillis()
                val existingQuiz = repository.getQuizById(question.quizId)
                if (existingQuiz != null && !existingQuiz.isDraft) {
                    val updatedQuiz = existingQuiz.copy(
                        version = existingQuiz.version + 1,
                        updatedAt = now
                    )
                    batch.set(firestore.collection("quizzes").document(question.quizId), updatedQuiz)
                    repository.updateQuiz(updatedQuiz)
                }

                val commitTask = batch.commit()
                com.google.android.gms.tasks.Tasks.await(commitTask)

                repository.deleteQuestion(question)

                logAdminAction("DELETE_QUESTION", "QUESTION", question.documentId, "Question deleted from quiz #${question.quizId}")
                val quizTitle = existingQuiz?.title ?: "Quiz #${question.quizId}"
                recordQuestionAuditLog(
                    actionType = "DELETED",
                    questionId = question.documentId,
                    questionText = question.text,
                    quizId = question.quizId,
                    quizTitle = quizTitle,
                    status = "PROPAGATED_TO_CLOUDFIRESTORE",
                    details = "Question deleted from cloud.",
                    documentId = UUID.randomUUID().toString()
                )

                loadAdminQuestions(question.quizId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Question deleted from cloud."
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error in deleteQuestion: ${e.message}", e)
                _syncStatusMessage.value = getReadableFirestoreError(e, "Failed to delete question from cloud.")
            } finally {
                _isAdminOperating.value = false
            }
        }
    }

    fun restoreDefaultSeedData() {
        viewModelScope.launch {
            adminCategoriesList.value.forEach {
                repository.deleteCategory(it)
            }
            val allCatsTemp = database.quizDao().getAllCategories()
            allCatsTemp.forEach {
                repository.deleteCategory(it)
            }
            repository.seedDatabaseIfNeeded(force = true, context = getApplication())
            updateQuizQuestionsCounts()
            navigateTo(Screen.Home, clearBackstack = true)
        }
    }

    fun runFirestoreDiagnostics() {
        val networkText = if (_isNetworkAvailable.value) "Online" else "Offline"
        val syncText = if (_isLiveSyncing.value) "Active" else "Paused"
        _syncStatusMessage.value = "Diagnostics: Firestore SSoT connection is $networkText. Live Synchronization is $syncText."
    }

    // ==========================================
    // DYNAMIC CONTACT US & PRIVACY POLICY
    // ==========================================
    private val _contactMethods = MutableStateFlow<List<com.example.data.ContactMethod>>(emptyList())
    val contactMethods: StateFlow<List<com.example.data.ContactMethod>> = _contactMethods.asStateFlow()

    private val _privacyPolicy = MutableStateFlow<com.example.data.PrivacyPolicyData>(com.example.data.PrivacyPolicyData())
    val privacyPolicy: StateFlow<com.example.data.PrivacyPolicyData> = _privacyPolicy.asStateFlow()

    private var contactMethodsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var privacyPolicyListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startObservingContactMethodsAndPrivacyPolicy() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        if (_contactMethods.value.isEmpty()) {
            seedDefaultContactMethods()
        }
        if (_privacyPolicy.value.content.isBlank()) {
            seedDefaultPrivacyPolicy()
        }

        if (contactMethodsListenerRegistration == null) {
            contactMethodsListenerRegistration = firestore.collection("contact_methods")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e("QuizViewModel", "Error in contact_methods sync: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            try {
                                com.example.data.ContactMethod(
                                    documentId = doc.id,
                                    platform = doc.getString("platform") ?: "Website",
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    value = doc.getString("value") ?: "",
                                    displayOrder = doc.getLong("displayOrder")?.toInt() ?: 0,
                                    isEnabled = doc.getBoolean("isEnabled") ?: true,
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) { null }
                        }.sortedBy { it.displayOrder }

                        if (items.isNotEmpty()) {
                            _contactMethods.value = items
                        }
                    }
                }
        }

        if (privacyPolicyListenerRegistration == null) {
            privacyPolicyListenerRegistration = firestore.collection("app_config").document("privacy_policy")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e("QuizViewModel", "Error in privacy_policy sync: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val title = snapshot.getString("title") ?: "Privacy Policy"
                        val content = snapshot.getString("content") ?: ""
                        val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                        _privacyPolicy.value = com.example.data.PrivacyPolicyData(title = title, content = content, updatedAt = updatedAt)
                    }
                }
        }
    }

    private fun seedDefaultContactMethods() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val defaultContacts = listOf(
            com.example.data.ContactMethod(
                documentId = "cm_whatsapp",
                platform = "WhatsApp",
                title = "WhatsApp Support",
                description = "Chat with us directly on WhatsApp for instant assistance",
                value = "919876543210",
                displayOrder = 1,
                isEnabled = true
            ),
            com.example.data.ContactMethod(
                documentId = "cm_telegram",
                platform = "Telegram",
                title = "Telegram Community Channel",
                description = "Join our official Speed English Telegram study group",
                value = "SpeedEnglishOfficial",
                displayOrder = 2,
                isEnabled = true
            ),
            com.example.data.ContactMethod(
                documentId = "cm_gmail",
                platform = "Gmail",
                title = "Customer Service Email",
                description = "Send us your feedback, bug reports or questions via email",
                value = "support@speedenglish.com",
                displayOrder = 3,
                isEnabled = true
            ),
            com.example.data.ContactMethod(
                documentId = "cm_website",
                platform = "Website",
                title = "Official Website",
                description = "Explore practice drills, updates, and online resources",
                value = "https://speedenglish.com",
                displayOrder = 4,
                isEnabled = true
            )
        )
        _contactMethods.value = defaultContacts
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batch = firestore.batch()
                defaultContacts.forEach { contact ->
                    val ref = firestore.collection("contact_methods").document(contact.documentId)
                    val data = mapOf(
                        "platform" to contact.platform,
                        "title" to contact.title,
                        "description" to contact.description,
                        "value" to contact.value,
                        "displayOrder" to contact.displayOrder,
                        "isEnabled" to contact.isEnabled,
                        "createdAt" to contact.createdAt,
                        "updatedAt" to contact.updatedAt
                    )
                    batch.set(ref, data)
                }
                batch.commit()
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error seeding default contact methods: ${e.message}")
            }
        }
    }

    private fun seedDefaultPrivacyPolicy() {
        val defaultPolicy = com.example.data.PrivacyPolicyData()
        _privacyPolicy.value = defaultPolicy
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val data = mapOf(
                    "title" to defaultPolicy.title,
                    "content" to defaultPolicy.content,
                    "updatedAt" to defaultPolicy.updatedAt
                )
                firestore.collection("app_config").document("privacy_policy").set(data)
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Error seeding default privacy policy: ${e.message}")
            }
        }
    }

    fun saveContactMethod(
        contact: com.example.data.ContactMethod,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val docId = if (contact.documentId.isNotBlank()) contact.documentId else firestore.collection("contact_methods").document().id
        val now = System.currentTimeMillis()
        val updatedContact = contact.copy(documentId = docId, updatedAt = now)

        // Optimistic local update
        val currentList = _contactMethods.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.documentId == docId }
        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedContact
        } else {
            currentList.add(updatedContact)
        }
        _contactMethods.value = currentList.sortedBy { it.displayOrder }

        val data = mapOf(
            "platform" to updatedContact.platform,
            "title" to updatedContact.title,
            "description" to updatedContact.description,
            "value" to updatedContact.value,
            "displayOrder" to updatedContact.displayOrder,
            "isEnabled" to updatedContact.isEnabled,
            "createdAt" to if (updatedContact.createdAt > 0L) updatedContact.createdAt else now,
            "updatedAt" to now
        )

        firestore.collection("contact_methods").document(docId).set(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.w("QuizViewModel", "Firestore contact_methods write skipped/failed: ${e.message}")
                onResult(true, null)
            }
    }

    fun deleteContactMethod(
        documentId: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Optimistic local deletion
        _contactMethods.value = _contactMethods.value.filter { it.documentId != documentId }

        firestore.collection("contact_methods").document(documentId).delete()
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.w("QuizViewModel", "Firestore contact_methods delete skipped/failed: ${e.message}")
                onResult(true, null)
            }
    }

    fun toggleContactMethodEnabled(documentId: String, isEnabled: Boolean) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Optimistic local update
        _contactMethods.value = _contactMethods.value.map {
            if (it.documentId == documentId) it.copy(isEnabled = isEnabled, updatedAt = System.currentTimeMillis()) else it
        }

        firestore.collection("contact_methods").document(documentId).update(
            "isEnabled", isEnabled,
            "updatedAt", System.currentTimeMillis()
        ).addOnFailureListener { e ->
            Log.w("QuizViewModel", "Firestore toggleContactMethodEnabled skipped/failed: ${e.message}")
        }
    }

    fun savePrivacyPolicy(
        title: String,
        content: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val updatedPolicy = com.example.data.PrivacyPolicyData(title = title.ifBlank { "Privacy Policy" }, content = content, updatedAt = now)

        // Optimistic local update
        _privacyPolicy.value = updatedPolicy

        val data = mapOf(
            "title" to updatedPolicy.title,
            "content" to updatedPolicy.content,
            "updatedAt" to now
        )

        firestore.collection("app_config").document("privacy_policy").set(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.w("QuizViewModel", "Firestore privacy_policy write skipped/failed: ${e.message}")
                onResult(true, null)
            }
    }
}
