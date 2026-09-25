import re

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "r") as f:
    content = f.read()

# Replace addCategory
add_cat_replacement = """    fun addCategory(
        name: String,
        description: String,
        iconName: String,
        isDraft: Boolean = false,
        parentCategoryId: Int? = null,
        documentId: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Saving category online..."
            
            val uuid = if (documentId.isNotEmpty() && !documentId.all { it.isDigit() }) documentId else java.util.UUID.randomUUID().toString()
            val tempId = Math.abs(uuid.hashCode())
            val newCat = Category(
                id = tempId,
                name = name,
                description = description,
                iconName = iconName,
                isDraft = isDraft,
                parentCategoryId = parentCategoryId,
                documentId = uuid
            )
            
            val savedResult = firebaseSyncManager.saveCategoryToFirestore(newCat)
            if (savedResult.isSuccess) {
                repository.insertCategory(savedResult.getOrNull() ?: newCat)
                loadAdminCategories()
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Category saved online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun addCategory\([\s\S]*?loadAdminCategories\(\)\n            updateQuizQuestionsCounts\(\)\n            onComplete\(\)\n        }\n    }', add_cat_replacement, content)

# Replace updateCategory
update_cat_replacement = """    fun updateCategory(category: Category, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Updating category online..."
            val result = firebaseSyncManager.saveCategoryToFirestore(category)
            if (result.isSuccess) {
                repository.updateCategory(result.getOrNull() ?: category)
                loadAdminCategories()
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Category updated online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun updateCategory\(category: Category, onComplete: \(\) -> Unit = \{\}\) \{\n        viewModelScope\.launch \{\n            repository\.updateCategory\(category\)\n            firebaseSyncManager\.saveCategoryToFirestore\(category\)\n            loadAdminCategories\(\)\n            updateQuizQuestionsCounts\(\)\n            onComplete\(\)\n        \}\n    \}', update_cat_replacement, content)


# Replace addQuiz
add_quiz_replacement = """    fun addQuiz(categoryId: Int, title: String, description: String, timeLimitSeconds: Int, isDraft: Boolean = false, shuffleQuestions: Boolean = false, marksPerQuestion: Float = 1.0f, negativeMarking: Float = 0.0f, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Saving quiz online..."
            val uuid = java.util.UUID.randomUUID().toString()
            val tempId = Math.abs(uuid.hashCode())
            val newQuiz = Quiz(
                id = tempId,
                categoryId = categoryId,
                title = title,
                description = description,
                timeLimitSeconds = timeLimitSeconds,
                isDraft = isDraft,
                shuffleQuestions = shuffleQuestions,
                marksPerQuestion = marksPerQuestion,
                negativeMarking = negativeMarking,
                documentId = uuid
            )
            val result = firebaseSyncManager.saveQuizToFirestore(newQuiz)
            if (result.isSuccess) {
                repository.insertQuiz(result.getOrNull() ?: newQuiz)
                loadAdminQuizzes(categoryId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Quiz saved online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun addQuiz\([\s\S]*?loadAdminQuizzes\(categoryId\)\n            updateQuizQuestionsCounts\(\)\n            onComplete\(\)\n        }\n    }', add_quiz_replacement, content)


# Replace updateQuiz
update_quiz_replacement = """    fun updateQuiz(quiz: Quiz, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Updating quiz online..."
            val result = firebaseSyncManager.saveQuizToFirestore(quiz)
            if (result.isSuccess) {
                repository.updateQuiz(result.getOrNull() ?: quiz)
                loadAdminQuizzes(quiz.categoryId)
                updateQuizQuestionsCounts()
                _syncStatusMessage.value = "Quiz updated online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Failed: Must be online."
            }
            _isSyncing.value = false
        }
    }"""
content = re.sub(r'    fun updateQuiz\(quiz: Quiz, onComplete: \(\) -> Unit = \{\}\) \{\n        viewModelScope\.launch \{\n            repository\.updateQuiz\(quiz\)\n            firebaseSyncManager\.saveQuizToFirestore\(quiz\)\n            loadAdminQuizzes\(quiz\.categoryId\)\n            updateQuizQuestionsCounts\(\)\n            onComplete\(\)\n        \}\n    \}', update_quiz_replacement, content)

with open("app/src/main/java/com/example/ui/QuizViewModel.kt", "w") as f:
    f.write(content)
