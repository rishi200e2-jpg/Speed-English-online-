package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

sealed interface LceState<out T> {
    object Loading : LceState<Nothing>
    data class Content<T>(val data: T) : LceState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : LceState<Nothing>
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val documentId: String,
    val name: String,
    val description: String,
    val iconName: String, // e.g. "science", "history", "public", "translate"
    val isDraft: Boolean = false,
    val status: String = if (isDraft) "draft" else "published", // "draft", "published", "archived"
    val parentCategoryId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quizzes",
    indices = [
        Index(value = ["categoryId"])
    ]
)
data class Quiz(
    @PrimaryKey val documentId: String,
    val categoryId: String,
    val title: String,
    val description: String,
    val timeLimitSeconds: Int = 20, // Default timer per question
    val isDraft: Boolean = false,
    val status: String = if (isDraft) "draft" else "published", // "draft", "published", "archived"
    val version: Int = 1,
    val shuffleQuestions: Boolean = false,
    val marksPerQuestion: Float = 1.0f,
    val negativeMarking: Float = 0.0f,
    val questionCount: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val publishedAt: Long? = null
)

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["quizId"])
    ]
)
data class Question(
    @PrimaryKey val documentId: String,
    val quizId: String,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String, // "A", "B", "C", "D"
    val explanation: String = "",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttempt(
    @PrimaryKey val documentId: String,
    val quizTitle: String,
    val categoryName: String,
    val score: Float,
    val totalQuestions: Int,
    val dateMillis: Long = System.currentTimeMillis(),
    val userId: String = "",
    val quizDocumentId: String = "",
    val quizVersion: Int = 1,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val timeTakenSeconds: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

data class LiveProgressData(
    val quizId: String,
    val score: Float,
    val completedAt: Long?,
    val currentQuestionIndex: Int,
    val answers: Map<String, String>,
    val quizVersion: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "question_audit_logs")
data class QuestionAuditLog(
    @PrimaryKey val documentId: String = "",
    val actionType: String = "MODIFIED", // "ADDED", "MODIFIED", "DELETED"
    val questionId: String = "",
    val questionText: String = "",
    val quizId: String = "",
    val quizTitle: String = "",
    val adminEmail: String = "admin@speedenglish.com",
    val adminUid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PROPAGATED_TO_CLOUDFIRESTORE", // "PROPAGATED_TO_CLOUDFIRESTORE", "SAVED_LOCALLY", "FAILED"
    val details: String = ""
)

data class AdminLog(
    val documentId: String,
    val adminUid: String = "",
    val adminEmail: String = "",
    val action: String = "", // "CREATE_QUIZ", "EDIT_QUIZ", "DELETE_QUIZ", "CREATE_CATEGORY", "EDIT_CATEGORY", "DELETE_CATEGORY", "CREATE_QUESTION", "EDIT_QUESTION", "DELETE_QUESTION", "PUBLISH_QUIZ", "UNPUBLISH_QUIZ", "BLOCK_USER", "UNBLOCK_USER", "RESET_PROGRESS"
    val targetType: String = "", // "QUIZ", "CATEGORY", "QUESTION", "USER"
    val targetId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PROPAGATED_TO_CLOUDFIRESTORE",
    val details: String = ""
)

data class RegisteredUser(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val name: String = displayName,
    val role: String = "user",
    val blocked: Boolean = false,
    val status: String = if (blocked) "blocked" else "active",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L
)

@Entity(tableName = "contact_methods")
data class ContactMethod(
    @PrimaryKey val documentId: String = "",
    val platform: String = "Website",
    val title: String = "",
    val description: String = "",
    val value: String = "",
    val displayOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "privacy_policy")
data class PrivacyPolicyData(
    @PrimaryKey val documentId: String = "privacy_policy",
    val title: String = "Privacy Policy",
    val content: String = "Welcome to Speed English! Your privacy is very important to us.\n\n" +
            "1. Information We Collect:\n" +
            "We collect basic account information (such as your email address and display name) and quiz performance statistics to calculate your XP and accuracy scores.\n\n" +
            "2. How We Use Your Data:\n" +
            "Your learning data is used exclusively to display personal progress charts, sync quiz history, and provide personalized drill recommendations.\n\n" +
            "3. Data Protection & Sharing:\n" +
            "We do not sell or trade your personal information to any third parties. All cloud communication is secured via Firebase Cloud Firestore encrypted connections.\n\n" +
            "4. Contact Us:\n" +
            "If you have questions regarding this Privacy Policy, please reach out via our official Contact Us channels.",
    val updatedAt: Long = System.currentTimeMillis()
)

data class SocialPost(
    val documentId: String = "",
    val title: String = "",
    val description: String = "",
    val mediaUrl: String = "",
    val feedMediaUrl: String = "",
    val originalMediaUrl: String = "",
    val mediaType: String = "image", // "image" or "video"
    val videoDuration: String = "", // e.g. "01:24" for video badge
    val authorName: String = "Speed English",
    val authorAvatarUrl: String = "",
    val isPinned: Boolean = false,
    val isPublished: Boolean = true,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val shareCount: Long = 0L,
    val likedUserIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
