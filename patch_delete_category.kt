    fun deleteCategory(category: Category, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Deleting category online..."
            try {
                val firestore = firebaseSyncManager.getFirestore()
                if (firestore != null && !firebaseSyncManager.isDummyConfig) {
                    val result = firebaseSyncManager.deleteCategoryFromFirestore(category)
                    if (result.isSuccess) {
                        repository.deleteCategory(category)
                        loadAdminCategories()
                        updateQuizQuestionsCounts()
                        _syncStatusMessage.value = "Category deleted online."
                        onComplete()
                    } else {
                        _syncStatusMessage.value = "Delete failed: You must be online."
                    }
                } else {
                    repository.deleteCategory(category)
                    loadAdminCategories()
                    updateQuizQuestionsCounts()
                    onComplete()
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Delete failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }
