import re

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "r") as f:
    content = f.read()

delete_quiz = """    fun deleteQuiz(quiz: Quiz, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Deleting quiz online..."
            try {
                val firestore = firebaseSyncManager.getFirestore()
                if (firestore != null && !firebaseSyncManager.isDummyConfig) {
                    val result = firebaseSyncManager.deleteQuizFromFirestore(quiz)
                    if (result.isSuccess) {
                        repository.deleteQuiz(quiz)
                        loadAdminQuizzes(quiz.categoryId)
                        updateQuizQuestionsCounts()
                        _syncStatusMessage.value = "Quiz deleted online."
                        onComplete()
                    } else {
                        _syncStatusMessage.value = "Delete failed: You must be online."
                    }
                } else {
                    repository.deleteQuiz(quiz)
                    loadAdminQuizzes(quiz.categoryId)
                    updateQuizQuestionsCounts()
                    onComplete()
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Delete error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }"""
content = re.sub(r'    fun deleteQuiz\([\s\S]*?\}', delete_quiz, content, count=1)


delete_question = """    fun deleteQuestion(question: Question, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Deleting question online..."
            try {
                val firestore = firebaseSyncManager.getFirestore()
                if (firestore != null && !firebaseSyncManager.isDummyConfig) {
                    val result = firebaseSyncManager.deleteQuestionFromFirestore(question)
                    if (result.isSuccess) {
                        repository.deleteQuestion(question)
                        loadAdminQuestions(question.quizId)
                        updateQuizQuestionsCounts()
                        _syncStatusMessage.value = "Question deleted online."
                        onComplete()
                    } else {
                        _syncStatusMessage.value = "Delete failed: You must be online."
                    }
                } else {
                    repository.deleteQuestion(question)
                    loadAdminQuestions(question.quizId)
                    updateQuizQuestionsCounts()
                    onComplete()
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Delete error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }"""
content = re.sub(r'    fun deleteQuestion\([\s\S]*?\}', delete_question, content, count=1)

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "w") as f:
    f.write(content)
