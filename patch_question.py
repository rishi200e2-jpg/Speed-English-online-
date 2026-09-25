import re

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "r") as f:
    content = f.read()

add_quest = """    fun addQuestion(quizId: Int, text: String, optA: String, optB: String, optC: String, optD: String, correct: String, explanation: String = "", onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Saving question online..."
            val uuid = java.util.UUID.randomUUID().toString()
            val tempId = Math.abs(uuid.hashCode())
            val newQuest = Question(
                id = tempId,
                quizId = quizId,
                text = text,
                optionA = optA,
                optionB = optB,
                optionC = optC,
                optionD = optD,
                correctOption = correct,
                explanation = explanation,
                documentId = uuid
            )
            val result = firebaseSyncManager.saveQuestionToFirestore(newQuest)
            if (result.isSuccess) {
                repository.insertQuestion(result.getOrNull() ?: newQuest)
                loadAdminQuestions(quizId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Question saved online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun addQuestion\([\s\S]*?loadAdminQuestions\(quizId\)\n                updateQuizQuestionsCounts\(\)\n            } catch \(e: Exception\) \{\n                Log\.e\("QuizViewModel", "Error in addQuestion", e\)\n            \}\n        \}\n    \}', add_quest, content)

update_quest = """    fun updateQuestion(question: Question, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Updating question online..."
            val result = firebaseSyncManager.saveQuestionToFirestore(question)
            if (result.isSuccess) {
                repository.updateQuestion(result.getOrNull() ?: question)
                loadAdminQuestions(question.quizId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Question updated online."
                withContext(Dispatchers.Main) { onComplete() }
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun updateQuestion\(question: Question, onComplete: \(\) -> Unit = \{\}\) \{\n        viewModelScope\.launch \{\n            try \{\n                // 1\. Immediately update Room database[\s\S]*?\}\n        \}\n    \}', update_quest, content)

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "w") as f:
    f.write(content)
