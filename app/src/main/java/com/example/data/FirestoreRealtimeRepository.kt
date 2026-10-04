package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-Time Firestore Repository implementation using Kotlin callbackFlow and Firestore addSnapshotListener
 * for 'categories', 'quizzes', and 'questions' collections to enable real-time data streaming.
 */
class FirestoreRealtimeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "FirestoreRealtimeRepo"

    /**
     * Real-time stream of all Categories from Firestore collection 'categories'
     */
    fun getCategoriesRealtime(): Flow<List<Category>> = callbackFlow {
        val subscription = firestore.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to categories: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val categories = snapshot.documents.mapNotNull { doc ->
                        try {
                            Category(
                                documentId = doc.getString("documentId") ?: doc.id,
                                name = doc.getString("name") ?: "",
                                description = doc.getString("description") ?: "",
                                iconName = doc.getString("iconName") ?: "general",
                                isDraft = doc.getBoolean("isDraft") ?: false,
                                status = doc.getString("status") ?: "published",
                                parentCategoryId = doc.getString("parentCategoryId"),
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse Category document ${doc.id}", e)
                            null
                        }
                    }
                    trySend(categories)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing categories snapshot listener")
            subscription.remove()
        }
    }

    /**
     * Real-time stream of Quizzes from Firestore collection 'quizzes'
     * Optionally filter by categoryId if provided.
     */
    fun getQuizzesRealtime(categoryId: String? = null): Flow<List<Quiz>> = callbackFlow {
        var query: Query = firestore.collection("quizzes")
        if (!categoryId.isNullOrBlank()) {
            query = query.whereEqualTo("categoryId", categoryId)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to quizzes: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val quizzes = snapshot.documents.mapNotNull { doc ->
                    try {
                        Quiz(
                            documentId = doc.getString("documentId") ?: doc.id,
                            categoryId = doc.getString("categoryId") ?: "",
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            timeLimitSeconds = doc.getLong("timeLimitSeconds")?.toInt() ?: 20,
                            isDraft = doc.getBoolean("isDraft") ?: false,
                            status = doc.getString("status") ?: "published",
                            version = doc.getLong("version")?.toInt() ?: 1,
                            shuffleQuestions = doc.getBoolean("shuffleQuestions") ?: false,
                            marksPerQuestion = doc.getDouble("marksPerQuestion")?.toFloat() ?: 1.0f,
                            negativeMarking = doc.getDouble("negativeMarking")?.toFloat() ?: 0.0f,
                            sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                            publishedAt = doc.getLong("publishedAt")
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse Quiz document ${doc.id}", e)
                        null
                    }
                }
                val sortedQuizzes = quizzes.sortedWith(compareBy<Quiz> { it.sortOrder }.thenBy { it.createdAt })
                trySend(sortedQuizzes)
            }
        }

        awaitClose {
            Log.d(TAG, "Closing quizzes snapshot listener")
            subscription.remove()
        }
    }

    /**
     * Real-time stream of Questions from Firestore collection 'questions'
     * Optionally filter by quizId if provided.
     */
    fun getQuestionsRealtime(quizId: String? = null): Flow<List<Question>> = callbackFlow {
        var query: Query = firestore.collection("questions")
        if (!quizId.isNullOrBlank()) {
            query = query.whereEqualTo("quizId", quizId)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to questions: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val questions = snapshot.documents.mapNotNull { doc ->
                    try {
                        Question(
                            documentId = doc.getString("documentId") ?: doc.id,
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
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse Question document ${doc.id}", e)
                        null
                    }
                }
                trySend(questions)
            }
        }

        awaitClose {
            Log.d(TAG, "Closing questions snapshot listener")
            subscription.remove()
        }
    }

    /**
     * Real-time stream of Questions for a specific Quiz from subcollection 'quizzes/{quizId}/questions'
     */
    fun getQuizSubcollectionQuestionsRealtime(quizId: String): Flow<List<Question>> = callbackFlow {
        val subscription = firestore.collection("quizzes")
            .document(quizId)
            .collection("questions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to subcollection questions for quiz $quizId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val questions = snapshot.documents.mapNotNull { doc ->
                        try {
                            Question(
                                documentId = doc.getString("documentId") ?: doc.id,
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
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse Question subcollection doc ${doc.id}", e)
                            null
                        }
                    }
                    trySend(questions)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing quiz subcollection questions listener for quiz $quizId")
            subscription.remove()
        }
    }

    // =========================================================================
    // CRUD Operations using Coroutine Tasks await()
    // =========================================================================

    suspend fun saveCategory(category: Category): Result<Unit> = runCatching {
        firestore.collection("categories").document(category.documentId).set(category).await()
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        firestore.collection("categories").document(categoryId).delete().await()
    }

    suspend fun saveQuiz(quiz: Quiz): Result<Unit> = runCatching {
        firestore.collection("quizzes").document(quiz.documentId).set(quiz).await()
    }

    suspend fun deleteQuiz(quizId: String): Result<Unit> = runCatching {
        firestore.collection("quizzes").document(quizId).delete().await()
    }

    suspend fun saveQuestion(question: Question): Result<Unit> = runCatching {
        firestore.collection("questions").document(question.documentId).set(question).await()
        if (question.quizId.isNotEmpty()) {
            firestore.collection("quizzes")
                .document(question.quizId)
                .collection("questions")
                .document(question.documentId)
                .set(question)
                .await()
        }
    }

    suspend fun deleteQuestion(question: Question): Result<Unit> = runCatching {
        firestore.collection("questions").document(question.documentId).delete().await()
        if (question.quizId.isNotEmpty()) {
            firestore.collection("quizzes")
                .document(question.quizId)
                .collection("questions")
                .document(question.documentId)
                .delete()
                .await()
        }
    }

    /**
     * Real-time stream of all Social Posts from Firestore collection 'social_posts'
     */
    fun getSocialPostsRealtime(): Flow<List<SocialPost>> = callbackFlow {
        val subscription = firestore.collection("social_posts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to social_posts: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val posts = snapshot.documents.map { doc ->
                        val getLongVal: (String, Long) -> Long = { field, defaultVal ->
                            val raw = doc.get(field)
                            when (raw) {
                                is Number -> raw.toLong()
                                is String -> raw.toLongOrNull() ?: defaultVal
                                else -> defaultVal
                            }
                        }
                        val getBoolVal: (String, Boolean) -> Boolean = { field, defaultVal ->
                            val raw = doc.get(field)
                            when (raw) {
                                is Boolean -> raw
                                is String -> raw.equals("true", ignoreCase = true)
                                is Number -> raw.toInt() != 0
                                else -> defaultVal
                            }
                        }

                        SocialPost(
                            documentId = doc.getString("documentId")?.ifBlank { doc.id } ?: doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            mediaUrl = doc.getString("mediaUrl") ?: "",
                            feedMediaUrl = doc.getString("feedMediaUrl") ?: "",
                            originalMediaUrl = doc.getString("originalMediaUrl") ?: "",
                            mediaType = doc.getString("mediaType") ?: "image",
                            videoDuration = doc.getString("videoDuration") ?: "",
                            authorName = doc.getString("authorName") ?: "Speed English",
                            authorAvatarUrl = doc.getString("authorAvatarUrl") ?: "",
                            isPinned = getBoolVal("isPinned", false),
                            isPublished = getBoolVal("isPublished", true),
                            viewCount = getLongVal("viewCount", 0L),
                            likeCount = getLongVal("likeCount", 0L),
                            shareCount = getLongVal("shareCount", 0L),
                            likedUserIds = (doc.get("likedUserIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                            createdAt = getLongVal("createdAt", System.currentTimeMillis()),
                            updatedAt = getLongVal("updatedAt", System.currentTimeMillis())
                        )
                    }.sortedWith(compareByDescending<SocialPost> { it.isPinned }.thenByDescending { it.createdAt })
                    trySend(posts)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing social_posts snapshot listener")
            subscription.remove()
        }
    }
}
