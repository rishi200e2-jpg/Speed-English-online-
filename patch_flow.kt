    val categoriesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Category>>(emptyList())
    val quizzesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Quiz>>(emptyList())
    val questionsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Question>>(emptyList())
