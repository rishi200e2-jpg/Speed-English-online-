    fun addCategory(
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
            _syncStatusMessage.value = "Saving to cloud server..."
            
            val uuid = if (documentId.isNotEmpty() && !documentId.all { it.isDigit() }) documentId else java.util.UUID.randomUUID().toString()
            // Assume random ID for now, Firestore will be the source of truth
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
                // Online write succeeded! Now update local cache so UI reacts immediately.
                repository.insertCategory(savedResult.getOrNull() ?: newCat)
                _syncStatusMessage.value = "Saved successfully online."
                onComplete()
            } else {
                _syncStatusMessage.value = "Error: Cannot save while offline."
                _adminQuizError.value = "You must be online to make changes."
            }
            _isSyncing.value = false
        }
    }
