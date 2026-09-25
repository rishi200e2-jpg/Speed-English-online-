package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

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
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["documentId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val publishedAt: Long? = null
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Quiz::class,
            parentColumns = ["documentId"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
    @PrimaryKey val documentId: String,
    val actionType: String, // "ADDED", "MODIFIED", "DELETED"
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
