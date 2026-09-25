package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuizRepository(private val quizDao: QuizDao) {

    val allCategoriesFlow: Flow<List<Category>> = quizDao.getAllCategoriesFlow()

    fun getQuizzesFlow(categoryId: String): Flow<List<Quiz>> {
        return quizDao.getQuizzesByCategoryFlow(categoryId)
    }

    val allQuestionsFlow: Flow<List<Question>> = quizDao.getAllQuestionsFlow()

    fun getQuestionsFlow(quizId: String): Flow<List<Question>> {
        return quizDao.getQuestionsByQuizFlow(quizId)
    }

    suspend fun getQuizById(quizId: String): Quiz? = withContext(Dispatchers.IO) {
        quizDao.getQuizById(quizId)
    }

    suspend fun getCategoryById(categoryId: String): Category? = withContext(Dispatchers.IO) {
        quizDao.getCategoryById(categoryId)
    }

    suspend fun getQuestionsForQuiz(quizId: String): List<Question> = withContext(Dispatchers.IO) {
        quizDao.getQuestionsForQuiz(quizId)
    }

    suspend fun getQuestionCountForQuiz(quizId: String): Int = withContext(Dispatchers.IO) {
        quizDao.getQuestionCountForQuiz(quizId)
    }

    suspend fun insertCategory(category: Category) = withContext(Dispatchers.IO) {
        quizDao.insertCategory(category)
    }

    suspend fun insertCategories(categories: List<Category>) = withContext(Dispatchers.IO) {
        quizDao.insertCategories(categories)
    }

    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        quizDao.updateCategory(category)
    }

    suspend fun insertQuiz(quiz: Quiz) = withContext(Dispatchers.IO) {
        quizDao.insertQuiz(quiz)
    }

    suspend fun insertQuizzes(quizzes: List<Quiz>) = withContext(Dispatchers.IO) {
        quizDao.insertQuizzes(quizzes)
    }

    suspend fun updateQuiz(quiz: Quiz) = withContext(Dispatchers.IO) {
        quizDao.updateQuiz(quiz)
    }

    suspend fun insertQuestion(question: Question) = withContext(Dispatchers.IO) {
        quizDao.insertQuestion(question)
    }

    suspend fun insertQuestions(questions: List<Question>) = withContext(Dispatchers.IO) {
        quizDao.insertQuestions(questions)
    }

    suspend fun updateQuestion(question: Question) = withContext(Dispatchers.IO) {
        quizDao.updateQuestion(question)
    }

    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        quizDao.deleteCategory(category)
    }

    suspend fun deleteQuiz(quiz: Quiz) = withContext(Dispatchers.IO) {
        quizDao.deleteQuiz(quiz)
    }

    suspend fun getQuestionById(questionId: String): Question? = withContext(Dispatchers.IO) {
        quizDao.getQuestionById(questionId)
    }

    suspend fun deleteQuestionById(questionId: String) = withContext(Dispatchers.IO) {
        quizDao.deleteQuestionById(questionId)
    }

    suspend fun deleteQuestion(question: Question) = withContext(Dispatchers.IO) {
        quizDao.deleteQuestion(question)
    }

    suspend fun deleteQuestionsForQuiz(quizId: String) = withContext(Dispatchers.IO) {
        quizDao.deleteQuestionsForQuiz(quizId)
    }

    suspend fun getAllCategories(): List<Category> = withContext(Dispatchers.IO) {
        quizDao.getAllCategories()
    }

    suspend fun getAllQuizzes(): List<Quiz> = withContext(Dispatchers.IO) {
        quizDao.getAllQuizzes()
    }

    suspend fun getQuizzesByCategory(categoryId: String): List<Quiz> = withContext(Dispatchers.IO) {
        quizDao.getQuizzesByCategory(categoryId)
    }

    suspend fun getAllQuestions(): List<Question> = withContext(Dispatchers.IO) {
        quizDao.getAllQuestions()
    }

    fun getAttemptsForUserFlow(userId: String): Flow<List<QuizAttempt>> {
        return quizDao.getAttemptsForUserFlow(userId)
    }

    fun getAttemptsForUserOrEmailFlow(userId: String, userEmail: String): Flow<List<QuizAttempt>> {
        return quizDao.getAttemptsForUserOrEmailFlow(userId, userEmail)
    }

    val allAuditLogsFlow: Flow<List<QuestionAuditLog>> = quizDao.getAllAuditLogsFlow()

    suspend fun insertAuditLog(log: QuestionAuditLog) = withContext(Dispatchers.IO) {
        quizDao.insertAuditLog(log)
    }

    suspend fun insertAuditLogs(logs: List<QuestionAuditLog>) = withContext(Dispatchers.IO) {
        quizDao.insertAuditLogs(logs)
    }

    suspend fun getAllAuditLogs(): List<QuestionAuditLog> = withContext(Dispatchers.IO) {
        quizDao.getAllAuditLogs()
    }

    suspend fun clearAllAuditLogs() = withContext(Dispatchers.IO) {
        quizDao.clearAllAuditLogs()
    }

    suspend fun getAttemptsForUser(userId: String): List<QuizAttempt> = withContext(Dispatchers.IO) {
        quizDao.getAttemptsForUser(userId)
    }

    suspend fun getAttemptsForUserOrEmail(userId: String, userEmail: String): List<QuizAttempt> = withContext(Dispatchers.IO) {
        quizDao.getAttemptsForUserOrEmail(userId, userEmail)
    }

    suspend fun insertAttempt(attempt: QuizAttempt) = withContext(Dispatchers.IO) {
        quizDao.insertAttempt(attempt)
    }

    suspend fun insertAttempts(attempts: List<QuizAttempt>) = withContext(Dispatchers.IO) {
        quizDao.insertAttempts(attempts)
    }

    suspend fun deleteAttemptsForUser(userId: String) = withContext(Dispatchers.IO) {
        quizDao.deleteAttemptsForUser(userId)
    }

    suspend fun deleteAttemptsForUserOrEmail(userId: String, userEmail: String) = withContext(Dispatchers.IO) {
        quizDao.deleteAttemptsForUserOrEmail(userId, userEmail)
    }

    suspend fun deleteAttemptByDocumentId(documentId: String) = withContext(Dispatchers.IO) {
        if (documentId.isNotEmpty()) {
            quizDao.deleteAttemptByDocumentId(documentId)
        }
    }

    fun getAllAttemptsFlow(): Flow<List<QuizAttempt>> = quizDao.getAllAttemptsFlow()

    suspend fun getAllAttempts(): List<QuizAttempt> = withContext(Dispatchers.IO) {
        quizDao.getAllAttempts()
    }

    suspend fun clearAndRestoreData(
        categories: List<Category>,
        quizzes: List<Quiz>,
        questions: List<Question>
    ) = withContext(Dispatchers.IO) {
        // Safe database restoration
        quizDao.clearAndRestoreData(categories, quizzes, questions)
    }

    suspend fun seedDatabaseIfNeeded(force: Boolean = false, context: Context? = null) = withContext(Dispatchers.IO) {
        val prefs = context?.getSharedPreferences("quiz_app_prefs", Context.MODE_PRIVATE)
        val hasSeeded = prefs?.getBoolean("has_seeded", false) ?: false

        val existingCategories = quizDao.getAllCategories()

        if (!force) {
            if (hasSeeded || existingCategories.isNotEmpty()) {
                prefs?.edit()?.putBoolean("has_seeded", true)?.apply()
                return@withContext
            }
        }

        // Clean-up local DB
        quizDao.deleteAllCategories()
        quizDao.deleteAllQuizzes()
        quizDao.deleteAllQuestions()

        // 1. Insert Main parent categories
        quizDao.insertCategory(
            Category(documentId = "p1", name = "English", description = "Grammar, vocabulary, syntax and idioms drills.", iconName = "general")
        )

        quizDao.insertCategory(
            Category(documentId = "p2", name = "Science", description = "Astronomy, physics, and general science drills.", iconName = "science")
        )

        // 2. Insert Beautiful Subcategories
        quizDao.insertCategory(
            Category(documentId = "1", name = "Spotting Errors", description = "Subject-verb, nouns, syntax.", iconName = "spotting", parentCategoryId = "p1")
        )

        quizDao.insertCategory(
            Category(documentId = "2", name = "Vocabulary", description = "Synonyms & Antonyms.", iconName = "vocabulary", parentCategoryId = "p1")
        )

        quizDao.insertCategory(
            Category(documentId = "3", name = "Idioms & Phrases", description = "Common exam figures.", iconName = "idioms", parentCategoryId = "p1")
        )

        quizDao.insertCategory(
            Category(documentId = "4", name = "Voice & Narration", description = "Direct-Indirect conversions.", iconName = "voice", parentCategoryId = "p1")
        )

        quizDao.insertCategory(
            Category(documentId = "5", name = "GENERAL", description = "General mixed English questions.", iconName = "general", parentCategoryId = "p1")
        )

        quizDao.insertCategory(
            Category(documentId = "6", name = "SCIENCE", description = "General Science quizzes.", iconName = "science", parentCategoryId = "p2")
        )

        // 3. Insert SCIENCE Quizzes & Questions
        quizDao.insertQuiz(
            Quiz(documentId = "1", categoryId = "6", title = "Cosmic Voyage", description = "Outer space, astronomy, galaxies, and celestial discoveries.", timeLimitSeconds = 15)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_s1",
                quizId = "1",
                text = "Which planet in our solar system is known as the Red Planet?",
                optionA = "Venus", optionB = "Mars", optionC = "Jupiter", optionD = "Saturn",
                correctOption = "B"
            )
        )
        quizDao.insertQuestion(
            Question(
                documentId = "q_s2",
                quizId = "1",
                text = "What is the approximate age of the universe in billions of years?",
                optionA = "4.5 Billion", optionB = "9.1 Billion", optionC = "13.8 Billion", optionD = "20.0 Billion",
                correctOption = "C"
            )
        )

        quizDao.insertQuiz(
            Quiz(documentId = "2", categoryId = "6", title = "Chemistry of Life", description = "Atoms, molecules, reactions, and biochemical systems.", timeLimitSeconds = 20)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_s3",
                quizId = "2",
                text = "What is the most abundant chemical element in the human body by mass?",
                optionA = "Carbon", optionB = "Hydrogen", optionC = "Oxygen", optionD = "Nitrogen",
                correctOption = "C"
            )
        )

        // 4. Insert GENERAL Quizzes
        quizDao.insertQuiz(
            Quiz(documentId = "3", categoryId = "5", title = "Ancient Empires", description = "Rise and fall of dynasties, Pharaohs, and Roman Emperors.", timeLimitSeconds = 15)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_g1",
                quizId = "3",
                text = "Who was the first official Emperor of the Roman Empire, ruling from 27 BC until his death in AD 14?",
                optionA = "Julius Caesar", optionB = "Nero", optionC = "Augustus Caesar", optionD = "Marcus Aurelius",
                correctOption = "C"
            )
        )

        // 5. Insert Voice & Narration Quizzes
        quizDao.insertQuiz(
            Quiz(documentId = "4", categoryId = "4", title = "Active & Passive Drill", description = "Mastering the voice conversions easily.", timeLimitSeconds = 15)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_v1",
                quizId = "4",
                text = "Choose the correct passive voice: 'The chef prepared a delicious meal.'",
                optionA = "A delicious meal is prepared by chef.",
                optionB = "A delicious meal was prepared by the chef.",
                optionC = "A delicious meal has been prepared by the chef.",
                optionD = "Chef was preparing a delicious meal.",
                correctOption = "B"
            )
        )

        // 6. Insert Spotting Errors Quizzes
        quizDao.insertQuiz(
            Quiz(documentId = "5", categoryId = "1", title = "Subject-Verb Agreement", description = "Spot classic subject-verb agreement and syntactical errors.", timeLimitSeconds = 300)
        )

        getSubjectVerbAgreementQuestions("5").forEach { q ->
            quizDao.insertQuestion(q)
        }

        // 7. Insert Vocabulary Quizzes
        quizDao.insertQuiz(
            Quiz(documentId = "6", categoryId = "2", title = "Synonyms Express", description = "Learn synonyms, polar antonyms, and complex words.", timeLimitSeconds = 15)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_vc1",
                quizId = "6",
                text = "Which of the following represents the closest synonym to the adjective 'Ephemeral'?",
                optionA = "Enduring", optionB = "Eternal", optionC = "Short-lived", optionD = "Spacious",
                correctOption = "C"
            )
        )
        quizDao.insertQuestion(
            Question(
                documentId = "q_vc2",
                quizId = "6",
                text = "Which word functions as the correct polar antonym of the adjective 'Benevolent'?",
                optionA = "Generous", optionB = "Kindhearted", optionC = "Malevolent", optionD = "Indifferent",
                correctOption = "C"
            )
        )

        // 8. Insert Idioms & Phrases Quizzes
        quizDao.insertQuiz(
            Quiz(documentId = "7", categoryId = "3", title = "Exam Idioms Drills", description = "Metaphors, historic idioms, and common figures.", timeLimitSeconds = 15)
        )

        quizDao.insertQuestion(
            Question(
                documentId = "q_i1",
                quizId = "7",
                text = "What is the true figurative meaning of the English idiom 'Bite the bullet'?",
                optionA = "To eat something unpleasant", optionB = "To speak aggressively", optionC = "To resolve to face a difficult situation with courage", optionD = "To react randomly to a shock",
                correctOption = "C"
            )
        )
        quizDao.insertQuestion(
            Question(
                documentId = "q_i2",
                quizId = "7",
                text = "When someone wishes a theatrical performer to 'break a leg', what are they wishing him or her?",
                optionA = "A fast recovery", optionB = "To fail a critical scene", optionC = "Good luck", optionD = "An accident",
                correctOption = "C"
            )
        )

        prefs?.edit()?.putBoolean("has_seeded", true)?.apply()
    }

    private val localProgressMap = mutableMapOf<String, LiveProgressData>()

    /**
     * Saves user progress for a quiz in local offline cache
     */
    suspend fun saveProgress(
        uid: String,
        quizId: String,
        score: Float,
        completedAt: Long?,
        currentQuestionIndex: Int,
        answers: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        localProgressMap[quizId] = LiveProgressData(
            quizId = quizId,
            score = score,
            completedAt = completedAt,
            currentQuestionIndex = currentQuestionIndex,
            answers = answers
        )
        Result.success(Unit)
    }

    /**
     * Returns the local saved progress
     */
    fun fetchProgress(
        uid: String,
        onProgressUpdate: (Map<String, LiveProgressData>) -> Unit
    ) {
        onProgressUpdate(localProgressMap.toMap())
    }

    /**
     * Ensures the Spotting Errors category, Subject-Verb Agreement quiz, and 13 questions exist in the database
     */
    suspend fun ensureSpottingErrorsSubjectVerbQuestions(): Unit = withContext(Dispatchers.IO) {
        val categories = quizDao.getAllCategories()
        var spottingCat = categories.find { 
            it.name.trim().contains("spotting", ignoreCase = true) 
        }

        if (spottingCat == null) {
            val engParent = categories.find { it.name.trim().equals("English", ignoreCase = true) }
            val docId = "1"
            quizDao.insertCategory(
                Category(
                    documentId = docId,
                    name = "Spotting Errors",
                    description = "Subject-verb, nouns, syntax, and grammatical agreement drills.",
                    iconName = "spotting",
                    parentCategoryId = engParent?.documentId
                )
            )
            spottingCat = quizDao.getCategoryById(docId)
        }

        if (spottingCat == null) return@withContext

        val existingQuizzes = quizDao.getQuizzesByCategory(spottingCat.documentId)
        var svaQuiz = existingQuizzes.find {
            it.title.trim().contains("subject", ignoreCase = true)
        }

        if (svaQuiz == null) {
            val docId = "5"
            quizDao.insertQuiz(
                Quiz(
                    documentId = docId,
                    categoryId = spottingCat.documentId,
                    title = "Subject-Verb Agreement",
                    description = "Spot classic subject-verb agreement and syntactical errors.",
                    timeLimitSeconds = 300
                )
            )
            svaQuiz = quizDao.getQuizById(docId)
        }

        if (svaQuiz == null) return@withContext

        val targetQuizId = svaQuiz.documentId
        val existingQuestions = quizDao.getQuestionsForQuiz(targetQuizId)
        val existingTexts = existingQuestions.map { q -> q.text.trim().lowercase() }.toSet()

        val all13Questions = getSubjectVerbAgreementQuestions(targetQuizId)
        for (q in all13Questions) {
            if (q.text.trim().lowercase() !in existingTexts) {
                quizDao.insertQuestion(q)
            }
        }
    }

    companion object {
        fun getSubjectVerbAgreementQuestions(quizId: String): List<Question> = listOf(
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nNeither the manager (A) / nor the employees (B) / was present at the meeting. (C) / No error (D)",
                optionA = "Neither the manager",
                optionB = "nor the employees",
                optionC = "was present at the meeting.",
                optionD = "No error",
                correctOption = "C",
                explanation = "When two subjects are connected by 'neither... nor', the verb agrees with the subject closest to it ('the employees' - plural). Therefore, 'was' should be 'were'.",
                documentId = "sva_q1"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nThe committee (A) / have decided to postpone (B) / the final evaluation until Monday. (C) / No error (D)",
                optionA = "The committee",
                optionB = "have decided to postpone",
                optionC = "the final evaluation until Monday.",
                optionD = "No error",
                correctOption = "B",
                explanation = "Collective nouns like 'committee' acting as a single unified entity take a singular verb. 'have decided' must be corrected to 'has decided'.",
                documentId = "sva_q2"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nBread and butter (A) / are considered (B) / a wholesome breakfast. (C) / No error (D)",
                optionA = "Bread and butter",
                optionB = "are considered",
                optionC = "a wholesome breakfast.",
                optionD = "No error",
                correctOption = "B",
                explanation = "When two nouns express a single compound idea or entity ('Bread and butter'), they take a singular verb ('is considered').",
                documentId = "sva_q3"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nThe professor, along with (A) / his research assistants, (B) / were attending the symposium. (C) / No error (D)",
                optionA = "The professor, along with",
                optionB = "his research assistants,",
                optionC = "were attending the symposium.",
                optionD = "No error",
                correctOption = "C",
                explanation = "Phrases joined by 'along with', 'as well as', or 'together with' do not change the subject number. 'The professor' is singular, so use 'was attending'.",
                documentId = "sva_q4"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nTen miles (A) / are a long distance (B) / to cover on foot. (C) / No error (D)",
                optionA = "Ten miles",
                optionB = "are a long distance",
                optionC = "to cover on foot.",
                optionD = "No error",
                correctOption = "B",
                explanation = "Expressions of measurement, distance, time, and money take a singular verb when considered as a single unit or quantity ('is a long distance').",
                documentId = "sva_q5"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nEach of the candidates (A) / have submitted their certificates (B) / to the admission council. (C) / No error (D)",
                optionA = "Each of the candidates",
                optionB = "have submitted their certificates",
                optionC = "to the admission council.",
                optionD = "No error",
                correctOption = "B",
                explanation = "'Each' is an indefinite singular pronoun requiring a singular verb. 'have submitted' must be corrected to 'has submitted'.",
                documentId = "sva_q6"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nA variety of books (A) / on modern history (B) / is available in the library. (C) / No error (D)",
                optionA = "A variety of books",
                optionB = "on modern history",
                optionC = "is available in the library.",
                optionD = "No error",
                correctOption = "C",
                explanation = "'A variety of + plural noun' takes a plural verb ('are available'), whereas 'The variety of' takes a singular verb.",
                documentId = "sva_q7"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nNot only the teacher (A) / but also the students (B) / was enthusiastic about the project. (C) / No error (D)",
                optionA = "Not only the teacher",
                optionB = "but also the students",
                optionC = "was enthusiastic about the project.",
                optionD = "No error",
                correctOption = "C",
                explanation = "With 'not only... but also', the verb agrees with the subject closest to it ('the students' - plural). Therefore, 'was' should be 'were'.",
                documentId = "sva_q8"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nThe quality of these apples (A) / and juicy oranges (B) / are not up to the standard. (C) / No error (D)",
                optionA = "The quality of these apples",
                optionB = "and juicy oranges",
                optionC = "are not up to the standard.",
                optionD = "No error",
                correctOption = "C",
                explanation = "The true head subject is 'The quality' (singular), not 'apples and oranges'. Hence, the verb must be 'is not up to the standard'.",
                documentId = "sva_q9"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nOne of my closest friends (A) / are flying to London (B) / for higher studies. (C) / No error (D)",
                optionA = "One of my closest friends",
                optionB = "are flying to London",
                optionC = "for higher studies.",
                optionD = "No error",
                correctOption = "B",
                explanation = "'One of + plural noun' takes a singular verb because the head subject is 'One'. 'are flying' should be replaced by 'is flying'.",
                documentId = "sva_q10"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nMore than one student (A) / were absent from class (B) / due to heavy rainfall. (C) / No error (D)",
                optionA = "More than one student",
                optionB = "were absent from class",
                optionC = "due to heavy rainfall.",
                optionD = "No error",
                correctOption = "B",
                explanation = "'More than one + singular noun' takes a singular verb ('was absent').",
                documentId = "sva_q11"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nThe council of ministers (A) / have unanimously passed (B) / the new economic bill. (C) / No error (D)",
                optionA = "The council of ministers",
                optionB = "have unanimously passed",
                optionC = "the new economic bill.",
                optionD = "No error",
                correctOption = "B",
                explanation = "'The council' acts as a collective unit in this context, requiring the singular verb 'has unanimously passed'.",
                documentId = "sva_q12"
            ),
            Question(
                quizId = quizId,
                text = "Find the part with the grammatical error:\nPhysics and Mathematics (A) / is mandatory subjects (B) / for this engineering program. (C) / No error (D)",
                optionA = "Physics and Mathematics",
                optionB = "is mandatory subjects",
                optionC = "for this engineering program.",
                optionD = "No error",
                correctOption = "B",
                explanation = "When two separate subjects are linked with 'and', they form a plural compound subject requiring the plural verb 'are'.",
                documentId = "sva_q13"
            )
        )
    }
}
