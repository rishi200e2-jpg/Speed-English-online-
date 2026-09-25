package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    // Category Operations
    @Query("SELECT * FROM categories ORDER BY documentId ASC")
    fun getAllCategoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY documentId ASC")
    suspend fun getAllCategories(): List<Category>

    @Query("SELECT * FROM categories WHERE documentId = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    // Quiz Operations
    @Query("SELECT * FROM quizzes WHERE documentId = :quizId LIMIT 1")
    suspend fun getQuizById(quizId: String): Quiz?

    @Query("SELECT * FROM quizzes WHERE categoryId = :categoryId ORDER BY documentId ASC")
    fun getQuizzesByCategoryFlow(categoryId: String): Flow<List<Quiz>>

    @Query("SELECT * FROM quizzes WHERE categoryId = :categoryId ORDER BY documentId ASC")
    suspend fun getQuizzesByCategory(categoryId: String): List<Quiz>

    @Query("SELECT * FROM quizzes ORDER BY documentId ASC")
    suspend fun getAllQuizzes(): List<Quiz>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: Quiz)

    @Update
    suspend fun updateQuiz(quiz: Quiz)

    @Delete
    suspend fun deleteQuiz(quiz: Quiz)

    // Question Operations
    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY createdAt ASC, documentId ASC")
    fun getQuestionsByQuizFlow(quizId: String): Flow<List<Question>>

    @Query("SELECT * FROM questions ORDER BY createdAt ASC, documentId ASC")
    fun getAllQuestionsFlow(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY createdAt ASC, documentId ASC")
    suspend fun getQuestionsForQuiz(quizId: String): List<Question>

    @Query("SELECT COUNT(*) FROM questions WHERE quizId = :quizId")
    suspend fun getQuestionCountForQuiz(quizId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question)

    @Update
    suspend fun updateQuestion(question: Question)

    @Query("SELECT * FROM questions WHERE documentId = :questionId LIMIT 1")
    suspend fun getQuestionById(questionId: String): Question?

    @Query("DELETE FROM questions WHERE documentId = :questionId")
    suspend fun deleteQuestionById(questionId: String)

    @Delete
    suspend fun deleteQuestion(question: Question)

    @Query("DELETE FROM questions WHERE quizId = :quizId")
    suspend fun deleteQuestionsForQuiz(quizId: String)

    // Sync Helper Operations
    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<Question>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM quizzes")
    suspend fun deleteAllQuizzes()

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizzes(quizzes: List<Quiz>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    // Quiz Attempt Operations
    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId ORDER BY dateMillis DESC")
    fun getAttemptsForUserFlow(userId: String): Flow<List<QuizAttempt>>

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId OR (:userEmail != '' AND (userId = :userEmail OR userId = REPLACE(:userEmail, '.', '_'))) ORDER BY dateMillis DESC")
    fun getAttemptsForUserOrEmailFlow(userId: String, userEmail: String): Flow<List<QuizAttempt>>

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId ORDER BY dateMillis DESC")
    suspend fun getAttemptsForUser(userId: String): List<QuizAttempt>

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId OR (:userEmail != '' AND (userId = :userEmail OR userId = REPLACE(:userEmail, '.', '_'))) ORDER BY dateMillis DESC")
    suspend fun getAttemptsForUserOrEmail(userId: String, userEmail: String): List<QuizAttempt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttempt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<QuizAttempt>)

    @Query("DELETE FROM quiz_attempts WHERE userId = :userId")
    suspend fun deleteAttemptsForUser(userId: String)

    @Query("DELETE FROM quiz_attempts WHERE userId = :userId OR (:userEmail != '' AND (userId = :userEmail OR userId = REPLACE(:userEmail, '.', '_')))")
    suspend fun deleteAttemptsForUserOrEmail(userId: String, userEmail: String)

    @Query("DELETE FROM quiz_attempts WHERE documentId = :documentId")
    suspend fun deleteAttemptByDocumentId(documentId: String)

    @Query("SELECT * FROM quiz_attempts ORDER BY dateMillis DESC")
    fun getAllAttemptsFlow(): Flow<List<QuizAttempt>>

    @Query("SELECT * FROM quiz_attempts ORDER BY dateMillis DESC")
    suspend fun getAllAttempts(): List<QuizAttempt>

    @Transaction
    suspend fun clearAndRestoreData(
        categories: List<Category>,
        quizzes: List<Quiz>,
        questions: List<Question>
    ) {
        deleteAllCategories()
        deleteAllQuizzes()
        deleteAllQuestions()
        insertCategories(categories)
        insertQuizzes(quizzes)
        insertQuestions(questions)
    }

    // Question Audit Log Operations
    @Query("SELECT * FROM question_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<QuestionAuditLog>>

    @Query("SELECT * FROM question_audit_logs ORDER BY timestamp DESC")
    suspend fun getAllAuditLogs(): List<QuestionAuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: QuestionAuditLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<QuestionAuditLog>)

    @Query("DELETE FROM question_audit_logs")
    suspend fun clearAllAuditLogs()
}
