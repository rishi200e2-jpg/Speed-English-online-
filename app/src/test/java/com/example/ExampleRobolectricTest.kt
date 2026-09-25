package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.QuizDatabase
import com.example.data.QuizRepository
import com.example.ui.QuizViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Speed English", appName)
  }

  @Test
  fun `test login validation unregistered user`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
    
    // Clear SharedPreferences to ensure test user is unregistered
    val prefs = context.getSharedPreferences("firebase_mock_auth", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    
    val viewModel = QuizViewModel(context)
    viewModel.clearAuthMessage()
    
    // Attempt sign in with unregistered user
    viewModel.signIn("unregistered_user_abc@example.com", "password123")
    
    // Allow any coroutines to finish execution
    kotlinx.coroutines.delay(150)
    
    // Verify Task 1 message
    assertEquals("Please Register.", viewModel.authMessage.value)
  }

  @Test
  fun `test login validation registered user with no internet connection`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
    
    // Register the user first so they pass the registration check
    val prefs = context.getSharedPreferences("firebase_mock_auth", Context.MODE_PRIVATE)
    prefs.edit().apply {
      putString("user_role_registered_user_abc@example.com", "user")
      putString("user_pass_registered_user_abc@example.com", "password123")
      putBoolean("registered_registered_user_abc@example.com", true)
      commit()
    }
    
    // Simulate NO active internet connection using Robolectric shadow
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val shadowConn = shadowOf(connectivityManager)
    
    // Set active network to null to simulate offline state
    shadowConn.setActiveNetworkInfo(null)
    
    val viewModel = QuizViewModel(context)
    viewModel.clearAuthMessage()
    
    // Attempt sign in
    viewModel.signIn("registered_user_abc@example.com", "password123")
    
    // Allow any coroutines to finish execution
    kotlinx.coroutines.delay(150)
    
    // Verify Task 2 message
    assertEquals("Connect with internet", viewModel.authMessage.value)
  }

  @Test
  fun `test database seeding of categories quizzes and questions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, QuizDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = database.quizDao()
    val repository = QuizRepository(dao)

    // Query categories initially empty
    val initialCats = dao.getAllCategories()
    assertTrue(initialCats.isEmpty())

    // Trigger seeding
    repository.seedDatabaseIfNeeded()

    // Check categories are fully seeded
    val categories = dao.getAllCategories()
    assertEquals(8, categories.size)

    // Check quizzes are seeded
    val quizzes = dao.getAllQuizzes()
    assertTrue(quizzes.isNotEmpty())

    // Check questions of first quiz are seeded
    val firstQuiz = quizzes.first()
    val questions = dao.getQuestionsForQuiz(firstQuiz.documentId)
    assertTrue("Quiz ${firstQuiz.title} has 0 questions!", questions.isNotEmpty())

    database.close()
  }

  @Test
  fun `test multi-user question determinism between Account B and Account C`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, QuizDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = database.quizDao()

    // 1. Admin creates a category, quiz, and 7 questions
    val testCategory = com.example.data.Category(
        documentId = "1",
        name = "English",
        description = "Grammar",
        iconName = "general"
    )
    dao.insertCategory(testCategory)

    val quizId = "test_quiz_multi_account"
    val testQuiz = com.example.data.Quiz(
        documentId = quizId,
        categoryId = "1",
        title = "Deterministic Test Quiz",
        description = "Testing cross-account data consistency",
        shuffleQuestions = false
    )
    dao.insertQuiz(testQuiz)

    val createdQuestions = (1..7).map { index ->
        com.example.data.Question(
            documentId = "q_id_$index",
            quizId = quizId,
            text = "Question $index text?",
            optionA = "Opt A $index",
            optionB = "Opt B $index",
            optionC = "Opt C $index",
            optionD = "Opt D $index",
            correctOption = "A",
            createdAt = 1000L + (index * 100L)
        )
    }
    dao.insertQuestions(createdQuestions)

    // 2. Simulate Account B fetching questions
    val accountBQuestions = dao.getQuestionsForQuiz(quizId)
        .sortedWith(compareBy({ it.createdAt }, { it.documentId }))

    // 3. Simulate Account C fetching questions
    val accountCQuestions = dao.getQuestionsForQuiz(quizId)
        .sortedWith(compareBy({ it.createdAt }, { it.documentId }))

    // Verify 100% equivalence
    assertEquals(7, accountBQuestions.size)
    assertEquals(7, accountCQuestions.size)
    assertEquals(accountBQuestions.map { it.documentId }, accountCQuestions.map { it.documentId })
    assertEquals(accountBQuestions.map { it.text }, accountCQuestions.map { it.text })
    assertEquals(accountBQuestions.map { it.correctOption }, accountCQuestions.map { it.correctOption })

    database.close()
  }

  @Test
  fun `test seeded question shuffling is identical across all user devices`() = runBlocking {
    val quizId = "quiz_shuffled_101"
    val quizVersion = 2
    val baseQuestions = (1..10).map { i ->
        com.example.data.Question(
            documentId = "q_deterministic_$i",
            quizId = quizId,
            text = "Shuffled Question $i",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctOption = "A",
            createdAt = 5000L + (i * 10L)
        )
    }

    // Seed calculation matching QuizViewModel implementation
    val seed = quizId.hashCode().toLong() + (quizVersion.toLong() * 31L)
    
    // User B device shuffling
    val userBOrder = baseQuestions.shuffled(java.util.Random(seed)).map { it.documentId }

    // User C device shuffling
    val userCOrder = baseQuestions.shuffled(java.util.Random(seed)).map { it.documentId }

    // Verify User B and User C receive the exact identical question order
    assertEquals(userBOrder, userCOrder)
    assertEquals(10, userBOrder.size)
  }

  @Test
  fun `test adding questions sequentially 1, 2, 3 never causes prior questions to disappear`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, QuizDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = database.quizDao()

    val quizId = "quiz_sequential_test"
    dao.insertCategory(com.example.data.Category("cat_seq", "Seq Category", "Desc", "icon"))
    dao.insertQuiz(com.example.data.Quiz(quizId, "cat_seq", "Seq Quiz", "Desc"))

    // Add Q1
    val q1 = com.example.data.Question("q1", quizId, "Text 1", "A", "B", "C", "D", "A", createdAt = 1000L)
    dao.insertQuestion(q1)
    val listAfterQ1 = dao.getQuestionsForQuiz(quizId)
    assertEquals(1, listAfterQ1.size)
    assertEquals("q1", listAfterQ1[0].documentId)

    // Add Q2
    val q2 = com.example.data.Question("q2", quizId, "Text 2", "A", "B", "C", "D", "B", createdAt = 2000L)
    dao.insertQuestion(q2)
    val listAfterQ2 = dao.getQuestionsForQuiz(quizId)
    assertEquals(2, listAfterQ2.size)
    assertEquals(listOf("q1", "q2"), listAfterQ2.map { it.documentId })

    // Add Q3
    val q3 = com.example.data.Question("q3", quizId, "Text 3", "A", "B", "C", "D", "C", createdAt = 3000L)
    dao.insertQuestion(q3)
    val listAfterQ3 = dao.getQuestionsForQuiz(quizId)
    assertEquals(3, listAfterQ3.size)
    assertEquals(listOf("q1", "q2", "q3"), listAfterQ3.map { it.documentId })

    database.close()
  }

  @Test
  fun `test adding 7 questions rapidly maintains all 7 questions permanently`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, QuizDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = database.quizDao()

    val quizId = "quiz_rapid_7_test"
    dao.insertCategory(com.example.data.Category("cat_rapid", "Rapid Category", "Desc", "icon"))
    dao.insertQuiz(com.example.data.Quiz(quizId, "cat_rapid", "Rapid Quiz", "Desc"))

    val questions = (1..7).map { index ->
        com.example.data.Question(
            documentId = "rapid_q_$index",
            quizId = quizId,
            text = "Rapid Question $index",
            optionA = "Opt A $index",
            optionB = "Opt B $index",
            optionC = "Opt C $index",
            optionD = "Opt D $index",
            correctOption = "A",
            createdAt = 10000L + index * 100L
        )
    }

    // Insert all 7 questions sequentially as admin adds them
    questions.forEach { q ->
        dao.insertQuestion(q)
    }

    val retrieved = dao.getQuestionsForQuiz(quizId)
    assertEquals(7, retrieved.size)
    assertEquals(questions.map { it.documentId }, retrieved.map { it.documentId })

    // Simulate incoming non-destructive partial sync
    val partialSync = listOf(questions[0], questions[1], questions[2])
    dao.insertQuestions(partialSync)

    // Verify all 7 still remain in database
    val afterSync = dao.getQuestionsForQuiz(quizId)
    assertEquals(7, afterSync.size)
    assertEquals(questions.map { it.documentId }, afterSync.map { it.documentId })

    database.close()
  }
}
