package com.example.data

import java.util.UUID

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiSchemaProperty(
    val type: String,
    val description: String? = null,
    val items: GeminiSchemaProperty? = null,
    val properties: Map<String, GeminiSchemaProperty>? = null,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchemaProperty>? = null,
    val required: List<String>? = null,
    val items: GeminiSchemaProperty? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchema? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class ParsedQuestion(
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String,
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class UserQuestion(
    val question_text: String,
    val options: List<String>,
    val correct_answer: String,
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class UserQuestionsWrapper(
    val questions: List<UserQuestion>
)

@JsonClass(generateAdapter = true)
data class QuestionBlueprintItem(
    val index: Int = 0,
    val snippet: String = ""
)

@JsonClass(generateAdapter = true)
data class DocumentBlueprint(
    val totalQuestions: Int = 0,
    val questionIndex: List<QuestionBlueprintItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExtractedOptions(
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = ""
)

@JsonClass(generateAdapter = true)
data class ExtractedQuestionItem(
    val question: String = "",
    val options: ExtractedOptions = ExtractedOptions(),
    val correctKey: String = "A",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class ExtractedQuestionsWrapper(
    val questions: List<ExtractedQuestionItem> = emptyList()
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-2.0-flash",
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            // Log the exact request URL before sending for precise debugging on physical devices
            android.util.Log.d("GeminiService", "Sending network request to URL: ${request.url}")
            chain.proceed(request)
        }
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
        retrofit
    }

    suspend fun executeWithFallback(
        apiKey: String,
        request: GeminiRequest
    ): GeminiResponse {
        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest"
        )
        var lastException: Exception? = null

        android.util.Log.d("GeminiService", "Starting execution with API key length: ${apiKey.length}. Is placeholder: ${apiKey == "MY_GEMINI_API_KEY"}")

        for (model in modelsToTry) {
            try {
                android.util.Log.d("GeminiService", "Attempting API call with model: $model")
                return service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
            } catch (e: retrofit2.HttpException) {
                lastException = e
                android.util.Log.w("GeminiService", "Model $model returned HTTP ${e.code()}: ${e.message()}")
                if (e.code() == 404) {
                    continue
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("GeminiService", "Model $model call failed: ${e.message}")
            }
        }
        throw lastException ?: Exception("Gemini API call failed across all candidate models.")
    }
}

object GeminiQuestionGenerator {

    /**
     * Chunk text into smaller blocks (approx. chunkSize characters) on paragraph or word boundaries.
     */
    fun chunkText(text: String, chunkSize: Int = 5000): List<String> {
        if (text.length <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var startIndex = 0

        while (startIndex < text.length) {
            var endIndex = startIndex + chunkSize
            if (endIndex >= text.length) {
                chunks.add(text.substring(startIndex))
                break
            }

            // Try to find a paragraph boundary first (double newline)
            var breakIndex = text.lastIndexOf("\n\n", endIndex)
            if (breakIndex < startIndex || breakIndex < endIndex - 4000) {
                // Try single newline boundary
                breakIndex = text.lastIndexOf('\n', endIndex)
            }
            if (breakIndex < startIndex || breakIndex < endIndex - 3000) {
                // Try word boundary (space)
                breakIndex = text.lastIndexOf(' ', endIndex)
            }
            if (breakIndex < startIndex) {
                // Hard cut
                breakIndex = endIndex
            }

            val chunk = text.substring(startIndex, breakIndex).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
            startIndex = breakIndex + 1
        }
        return chunks
    }

    /**
     * Implements a Two-Pass Execution Plan using gemini-2.0-flash with Structured Outputs (responseSchema):
     * Pass 1: Blueprint & Counting (counts totalQuestions and indexes question snippets)
     * Pass 2: Strict Extraction (extracts questions safely in batches <= 30 items matching the JSON schema)
     */
    suspend fun generateQuestionsTwoPass(
        apiKey: String,
        extractedText: String,
        quizId: String,
        onProgressUpdate: ((progress: Float, status: String) -> Unit)? = null
    ): List<Question> {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        // ---------------------------------------------------------------------
        // PASS 1: Blueprint & Counting
        // ---------------------------------------------------------------------
        onProgressUpdate?.invoke(0.30f, "Pass 1: Generating document blueprint & counting questions...")

        val pass1Schema = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "totalQuestions" to GeminiSchemaProperty(
                    type = "INTEGER",
                    description = "Exact total count of multiple-choice questions present in the document"
                ),
                "questionIndex" to GeminiSchemaProperty(
                    type = "ARRAY",
                    description = "List of indexed question snippets found in the document",
                    items = GeminiSchemaProperty(
                        type = "OBJECT",
                        properties = mapOf(
                            "index" to GeminiSchemaProperty(type = "INTEGER", description = "1-based question index number"),
                            "snippet" to GeminiSchemaProperty(type = "STRING", description = "Brief 5-10 word snippet of the question text")
                        ),
                        required = listOf("index", "snippet")
                    )
                )
            ),
            required = listOf("totalQuestions", "questionIndex")
        )

        val pass1Prompt = """
            Perform Pass 1 of a Two-Pass Question Extraction Plan.
            Scan the entire document text provided below from start to finish.
            1. Count the exact total number of multiple-choice questions present in the document ("totalQuestions") up to a maximum of 120 questions.
            2. Index every question with its 1-based index number and a brief 5-10 word snippet ("questionIndex").

            DOCUMENT CONTENT:
            $extractedText
        """.trimIndent()

        val pass1Request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = pass1Prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1f,
                responseMimeType = "application/json",
                responseSchema = pass1Schema
            )
        )

        var blueprint = DocumentBlueprint()
        var pass1Success = false
        var pass1Attempt = 1
        val maxPass1Attempts = 3

        while (!pass1Success && pass1Attempt <= maxPass1Attempts) {
            try {
                val response = GeminiRetrofitClient.executeWithFallback(
                    apiKey = apiKey,
                    request = pass1Request
                )
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!rawJson.isNullOrBlank()) {
                    val cleanedJson = cleanAndSanitizeJson(rawJson)
                    val adapter = moshi.adapter(DocumentBlueprint::class.java)
                    val parsedBlueprint = adapter.fromJson(cleanedJson)
                    if (parsedBlueprint != null && parsedBlueprint.totalQuestions > 0) {
                        blueprint = parsedBlueprint
                        pass1Success = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiQuestionGenerator", "Pass 1 attempt $pass1Attempt failed: ${e.message}", e)
                if (pass1Attempt == maxPass1Attempts) throw e
                kotlinx.coroutines.delay(1500L * pass1Attempt)
            }
            pass1Attempt++
        }

        if (blueprint.totalQuestions <= 0) {
            blueprint = DocumentBlueprint(totalQuestions = 120)
        }

        android.util.Log.d("GeminiQuestionGenerator", "Pass 1 Blueprint complete. Total questions counted: ${blueprint.totalQuestions}")
        onProgressUpdate?.invoke(0.45f, "Pass 1 complete. Found ${blueprint.totalQuestions} questions. Starting Pass 2 extraction...")

        // ---------------------------------------------------------------------
        // PASS 2: Strict Extraction
        // ---------------------------------------------------------------------
        val rawTotalQuestions = if (blueprint.questionIndex.isNotEmpty()) {
            blueprint.questionIndex.size
        } else {
            blueprint.totalQuestions
        }
        val totalQuestions = minOf(rawTotalQuestions, 120)
        
        // Extract in safe and reliable batches of 10 questions.
        // This allows up to 120 questions per quiz scan cleanly and quickly.
        val batchSize = 10
        val numBatches = (totalQuestions + batchSize - 1) / batchSize

        val pass2Schema = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "questions" to GeminiSchemaProperty(
                    type = "ARRAY",
                    description = "Extracted question items",
                    items = GeminiSchemaProperty(
                        type = "OBJECT",
                        properties = mapOf(
                            "question" to GeminiSchemaProperty(type = "STRING", description = "The question text"),
                            "options" to GeminiSchemaProperty(
                                type = "OBJECT",
                                description = "Object containing keys a, b, c, and d",
                                properties = mapOf(
                                    "a" to GeminiSchemaProperty(type = "STRING", description = "Option A"),
                                    "b" to GeminiSchemaProperty(type = "STRING", description = "Option B"),
                                    "c" to GeminiSchemaProperty(type = "STRING", description = "Option C"),
                                    "d" to GeminiSchemaProperty(type = "STRING", description = "Option D")
                                ),
                                required = listOf("a", "b", "c", "d")
                            ),
                            "correctKey" to GeminiSchemaProperty(type = "STRING", description = "Must strictly be one uppercase letter: 'A', 'B', 'C', or 'D'"),
                            "explanation" to GeminiSchemaProperty(type = "STRING", description = "Detailed step-by-step explanation for the answer")
                        ),
                        required = listOf("question", "options", "correctKey", "explanation")
                    )
                )
            ),
            required = listOf("questions")
        )

        val extractedQuestions = mutableListOf<Question>()

        for (b in 0 until numBatches) {
            val startIdx = b * batchSize + 1
            val endIdx = minOf((b + 1) * batchSize, totalQuestions)

            val batchProgress = 0.45f + (b.toFloat() / numBatches.toFloat()) * 0.40f
            onProgressUpdate?.invoke(batchProgress, "Pass 2: Extracting questions $startIdx-$endIdx of $totalQuestions...")

            val rangeSnippets = blueprint.questionIndex
                .filter { it.index in startIdx..endIdx }
                .joinToString("\n") { "Q${it.index}: ${it.snippet}" }

            val pass2Prompt = """
                Perform Pass 2 of a Two-Pass Question Extraction Plan using gemini-2.0-flash.
                The document contains $totalQuestions multiple-choice questions total.
                Target Extraction Range: Extract questions $startIdx through $endIdx out of $totalQuestions.

                Blueprinted Snippets for this target range:
                $rangeSnippets

                STRICT EXTRACTION INSTRUCTIONS:
                1. Extract every single item in range $startIdx to $endIdx from start to finish without skipping any or cutting off early.
                2. "question": The verbatim question text.
                3. "options": Object containing keys "a", "b", "c", "d" (Strings).
                4. "correctKey": MUST strictly be one uppercase letter: "A", "B", "C", or "D".
                5. "explanation": Detailed step-by-step explanation for the answer.

                DOCUMENT CONTENT:
                $extractedText
            """.trimIndent()

            val pass2Request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = pass2Prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json",
                    responseSchema = pass2Schema
                )
            )

            var pass2Success = false
            var pass2Attempt = 1
            val maxPass2Attempts = 3

            while (!pass2Success && pass2Attempt <= maxPass2Attempts) {
                try {
                    val response = GeminiRetrofitClient.executeWithFallback(
                        apiKey = apiKey,
                        request = pass2Request
                    )
                    val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!rawJson.isNullOrBlank()) {
                        val cleanedJson = cleanAndSanitizeJson(rawJson)

                        try {
                            val wrapperAdapter = moshi.adapter(ExtractedQuestionsWrapper::class.java)
                            val wrapper = wrapperAdapter.fromJson(cleanedJson)
                            if (wrapper != null && wrapper.questions.isNotEmpty()) {
                                wrapper.questions.forEach { item ->
                                    val key = item.correctKey.trim().uppercase()
                                    val validKey = if (key in listOf("A", "B", "C", "D")) key else "A"
                                    extractedQuestions.add(
                                        Question(
                                            documentId = UUID.randomUUID().toString(),
                                            quizId = quizId,
                                            text = item.question,
                                            optionA = item.options.a,
                                            optionB = item.options.b,
                                            optionC = item.options.c,
                                            optionD = item.options.d,
                                            correctOption = validKey,
                                            explanation = item.explanation
                                        )
                                    )
                                }
                                pass2Success = true
                            }
                        } catch (pe: Exception) {
                            val fallbackAdapter = moshi.adapter(UserQuestionsWrapper::class.java)
                            val fallbackWrapper = fallbackAdapter.fromJson(cleanedJson)
                            if (fallbackWrapper != null && fallbackWrapper.questions.isNotEmpty()) {
                                fallbackWrapper.questions.forEach { uq ->
                                    val optA = uq.options.getOrNull(0) ?: ""
                                    val optB = uq.options.getOrNull(1) ?: ""
                                    val optC = uq.options.getOrNull(2) ?: ""
                                    val optD = uq.options.getOrNull(3) ?: ""
                                    val corrAns = uq.correct_answer.trim().uppercase()
                                    val correctOpt = when {
                                        corrAns == "A" || corrAns == "B" || corrAns == "C" || corrAns == "D" -> corrAns
                                        corrAns.equals(optA, ignoreCase = true) -> "A"
                                        corrAns.equals(optB, ignoreCase = true) -> "B"
                                        corrAns.equals(optC, ignoreCase = true) -> "C"
                                        corrAns.equals(optD, ignoreCase = true) -> "D"
                                        else -> "A"
                                    }
                                    extractedQuestions.add(
                                        Question(
                                            documentId = UUID.randomUUID().toString(),
                                            quizId = quizId,
                                            text = uq.question_text,
                                            optionA = optA,
                                            optionB = optB,
                                            optionC = optC,
                                            optionD = optD,
                                            correctOption = correctOpt,
                                            explanation = uq.explanation
                                        )
                                    )
                                }
                                pass2Success = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeminiQuestionGenerator", "Pass 2 batch $b attempt $pass2Attempt failed: ${e.message}", e)
                    if (pass2Attempt == maxPass2Attempts) throw e
                    kotlinx.coroutines.delay(1000L * pass2Attempt)
                }
                pass2Attempt++
            }
            kotlinx.coroutines.delay(300L)
        }

        return extractedQuestions
    }

    /**
     * Translates document text to multiple-choice questions via the Gemini API,
     * mapping the JSON output into our Quiz Question schema.
     */
    suspend fun generateQuestionsFromText(
        apiKey: String,
        text: String,
        quizId: String
    ): List<Question> {
        val chunks = chunkText(text)
        return generateQuestionsFromChunks(apiKey, chunks, quizId)
    }

    /**
     * Translates segments/chunks of text to multiple-choice questions via the Gemini API.
     */
    suspend fun generateQuestionsFromChunks(
        apiKey: String,
        chunks: List<String>,
        quizId: String,
        onProgressUpdate: ((current: Int, total: Int) -> Unit)? = null
    ): List<Question> = kotlinx.coroutines.supervisorScope {
        val allQuestions = java.util.Collections.synchronizedList(mutableListOf<Question>())
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        var lastException: Exception? = null
        val semaphore = Semaphore(1)

        val deferreds = chunks.mapIndexed { index, chunk ->
            async {
                semaphore.withPermit {
                    val parsedForThisChunk = mutableListOf<Question>()
                    var success = false
                    var attempt = 1
                    val maxAttempts = 4

                    while (!success && attempt <= maxAttempts) {
                        try {
                            val modelToUse = "gemini-2.0-flash"
                            android.util.Log.d("GeminiQuestionGenerator", "Processing chunk ${index + 1} of ${chunks.size} with model $modelToUse (length: ${chunk.length}), attempt $attempt")
                            
                            val prompt = """
                                You are an expert Document Question Extractor. Your job is to extract questions from the document EXACTLY as written.

                                STRICT UNCHANGING INSTRUCTIONS:
                                1. VERBATIM EXTRACTION: Extract the EXACT question text, options, and correct answers directly from the document.
                                2. DO NOT REPHRASE OR ALTER: NEVER rephrase questions, NEVER change wordings, and NEVER prepend conversational prefixes (e.g., DO NOT add "Do you know...", "Can you tell...", "What is..."). Use the EXACT text from the PDF.
                                3. OPTIONS: Keep the exact option texts as listed in the PDF. Ensure there are exactly 4 options per question.
                                4. CORRECT ANSWER: Set "correct_answer" to the exact string matching one of the options as specified in the PDF.
                                5. EXPLANATION: Provide a short, accurate explanation for why the answer is correct based on the document content.
                                6. EXTRACT ALL: Extract ALL questions present in the document segment without skipping any.

                                DOCUMENT CONTENT:
                                ${chunk}

                                Return strictly JSON matching this structure:
                                {
                                  "questions": [
                                    {
                                      "question_text": "Exact Question Text From Document",
                                      "options": ["Option A", "Option B", "Option C", "Option D"],
                                      "correct_answer": "Option C",
                                      "explanation": "Explanation here."
                                    }
                                  ]
                                }
                            """.trimIndent()

                            val request = GeminiRequest(
                                contents = listOf(
                                    GeminiContent(
                                        parts = listOf(
                                            GeminiPart(text = prompt)
                                        )
                                    )
                                ),
                                generationConfig = GeminiGenerationConfig(
                                    temperature = 0.0f,
                                    responseMimeType = "application/json",
                                    responseSchema = GeminiSchema(
                                        type = "OBJECT",
                                        properties = mapOf(
                                            "questions" to GeminiSchemaProperty(
                                                type = "ARRAY",
                                                items = GeminiSchemaProperty(
                                                    type = "OBJECT",
                                                    properties = mapOf(
                                                        "question_text" to GeminiSchemaProperty(type = "STRING", description = "The multiple-choice question text"),
                                                        "options" to GeminiSchemaProperty(
                                                            type = "ARRAY",
                                                            items = GeminiSchemaProperty(type = "STRING"),
                                                            description = "Exactly 4 multiple-choice options"
                                                        ),
                                                        "correct_answer" to GeminiSchemaProperty(type = "STRING", description = "The correct option string matching one of the 4 options"),
                                                        "explanation" to GeminiSchemaProperty(type = "STRING", description = "A detailed explanation of why the correct option is the right answer")
                                                    ),
                                                    required = listOf("question_text", "options", "correct_answer", "explanation")
                                                )
                                            )
                                        ),
                                        required = listOf("questions")
                                    )
                                )
                            )

                            val response = GeminiRetrofitClient.executeWithFallback(
                                apiKey = apiKey,
                                request = request
                            )
                            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!rawJson.isNullOrBlank()) {
                                val cleanedJson = cleanAndSanitizeJson(rawJson)

                                val moshi = com.squareup.moshi.Moshi.Builder()
                                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                    .build()

                                // Try parsing primary schema (UserQuestionsWrapper)
                                try {
                                    val wrapperAdapter = moshi.adapter(UserQuestionsWrapper::class.java)
                                    val wrapper = wrapperAdapter.fromJson(cleanedJson)
                                    if (wrapper != null && wrapper.questions.isNotEmpty()) {
                                        wrapper.questions.forEach { uq ->
                                            val optA = uq.options.getOrNull(0) ?: ""
                                            val optB = uq.options.getOrNull(1) ?: ""
                                            val optC = uq.options.getOrNull(2) ?: ""
                                            val optD = uq.options.getOrNull(3) ?: ""

                                            val corrAns = uq.correct_answer.trim()
                                            val correctOpt = when {
                                                corrAns.equals("A", ignoreCase = true) || corrAns.equals(optA, ignoreCase = true) -> "A"
                                                corrAns.equals("B", ignoreCase = true) || corrAns.equals(optB, ignoreCase = true) -> "B"
                                                corrAns.equals("C", ignoreCase = true) || corrAns.equals(optC, ignoreCase = true) -> "C"
                                                corrAns.equals("D", ignoreCase = true) || corrAns.equals(optD, ignoreCase = true) -> "D"
                                                else -> "A"
                                            }

                                            parsedForThisChunk.add(
                                                Question(
                                                    documentId = UUID.randomUUID().toString(),
                                                    quizId = quizId,
                                                    text = uq.question_text,
                                                    optionA = optA,
                                                    optionB = optB,
                                                    optionC = optC,
                                                    optionD = optD,
                                                    correctOption = correctOpt,
                                                    explanation = uq.explanation
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GeminiQuestionGenerator", "Primary format parsing failed for chunk ${index + 1}", e)
                                }

                                // Try parsing fallback schema (List<ParsedQuestion>) if primary failed
                                if (parsedForThisChunk.isEmpty()) {
                                    try {
                                        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, ParsedQuestion::class.java)
                                        val fallbackAdapter = moshi.adapter<List<ParsedQuestion>>(listType)
                                        val fallbackList = fallbackAdapter.fromJson(cleanedJson)
                                        fallbackList?.forEach { pq ->
                                            parsedForThisChunk.add(
                                                Question(
                                                    documentId = UUID.randomUUID().toString(),
                                                    quizId = quizId,
                                                    text = pq.text,
                                                    optionA = pq.optionA,
                                                    optionB = pq.optionB,
                                                    optionC = pq.optionC,
                                                    optionD = pq.optionD,
                                                    correctOption = pq.correctOption,
                                                    explanation = pq.explanation
                                                )
                                            )
                                        }
                                    } catch (e2: Exception) {
                                        android.util.Log.e("GeminiQuestionGenerator", "Fallback format parsing failed for chunk ${index + 1}", e2)
                                    }
                                }

                                // Try parsing using regex fallback if all Moshi parses failed
                                if (parsedForThisChunk.isEmpty()) {
                                    try {
                                        android.util.Log.d("GeminiQuestionGenerator", "Falling back to regex parsing for chunk ${index + 1}")
                                        val regexParsed = parseQuestionsFallbackRegex(rawJson, quizId)
                                        if (regexParsed.isNotEmpty()) {
                                            parsedForThisChunk.addAll(regexParsed)
                                        }
                                    } catch (e3: Exception) {
                                        android.util.Log.e("GeminiQuestionGenerator", "Regex format parsing failed for chunk ${index + 1}", e3)
                                    }
                                }

                                if (parsedForThisChunk.isNotEmpty()) {
                                    success = true
                                } else {
                                    android.util.Log.w("GeminiQuestionGenerator", "No questions parsed from chunk ${index + 1} with JSON: $cleanedJson")
                                    lastException = Exception("No questions could be parsed from segment JSON response.")
                                }
                            } else {
                                lastException = Exception("Gemini API returned empty text content.")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiQuestionGenerator", "Error calling Gemini API or parsing response for chunk ${index + 1}", e)
                            if (e is retrofit2.HttpException) {
                                val errorBody = e.response()?.errorBody()?.string() ?: ""
                                android.util.Log.e("GeminiQuestionGenerator", "HTTP Error details: $errorBody")
                                val msg = when (e.code()) {
                                    429 -> "Gemini API rate limit exceeded. Retrying shortly..."
                                    403 -> "Invalid API Key or unauthorized access. Please check your Gemini API key in settings."
                                    400 -> "Bad request sent to Gemini API. Please check document formatting."
                                    else -> "Gemini API error (${e.code()})"
                                }
                                lastException = Exception(msg)
                                if (e.code() == 429 || e.code() >= 500) {
                                    val delayMs = attempt * 4000L
                                    kotlinx.coroutines.delay(delayMs)
                                }
                            } else {
                                lastException = e
                            }
                            if (!success && attempt < maxAttempts) {
                                kotlinx.coroutines.delay(2000L)
                            }
                        }
                        attempt++
                    }

                    if (success) {
                        allQuestions.addAll(parsedForThisChunk)
                        android.util.Log.d("GeminiQuestionGenerator", "Successfully parsed ${parsedForThisChunk.size} questions from chunk ${index + 1}")
                    } else {
                        android.util.Log.e("GeminiQuestionGenerator", "Chunk ${index + 1} failed completely after $maxAttempts attempts.")
                    }

                    val currentProgress = completedCount.incrementAndGet()
                    onProgressUpdate?.invoke(currentProgress, chunks.size)
                    kotlinx.coroutines.delay(1500L)
                }
            }
        }
        deferreds.awaitAll()

        if (allQuestions.isEmpty() && lastException != null) {
            throw lastException!!
        }

        allQuestions.toList()
    }

    /**
     * Cleans raw JSON responses from markdown wrapping, removes comments, corrects trailing commas,
     * normalizes quote formatting and handles literal newlines in strings.
     */
    fun cleanAndSanitizeJson(rawJson: String): String {
        var clean = rawJson.trim()
        
        // 1. Strip markdown code blocks
        if (clean.contains("```")) {
            val firstIndex = clean.indexOf("```")
            val lastIndex = clean.lastIndexOf("```")
            if (firstIndex != -1 && lastIndex != -1 && lastIndex > firstIndex) {
                var content = clean.substring(firstIndex + 3, lastIndex).trim()
                if (content.startsWith("json", ignoreCase = true)) {
                    content = content.substring(4).trim()
                }
                clean = content
            }
        }
        
        // 2. Locate boundaries of the outer-most JSON object or array
        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        val firstBracket = clean.indexOf('[')
        val lastBracket = clean.lastIndexOf(']')

        if (firstBrace != -1 && lastBrace != -1) {
            if (firstBracket != -1 && firstBracket < firstBrace) {
                if (lastBracket != -1 && lastBracket > lastBrace) {
                    clean = clean.substring(firstBracket, lastBracket + 1)
                } else {
                    clean = clean.substring(firstBracket, lastBrace + 1) + "]"
                }
            } else {
                clean = clean.substring(firstBrace, lastBrace + 1)
            }
        } else if (firstBracket != -1 && lastBracket != -1) {
            clean = clean.substring(firstBracket, lastBracket + 1)
        }
        
        // 3. Replace curly quotes and double-quotes mapping
        clean = clean.replace('“', '"').replace('”', '"')
        clean = clean.replace('‘', '\'').replace('’', '\'')
        
        // 4. Remove single-line comments // ...
        clean = clean.replace(Regex("""(?m)^\s*//.*$"""), "")
        
        // 5. Remove trailing commas before closing braces/brackets
        clean = clean.replace(Regex(""",\s*([\]}])"""), "$1")
        
        // 6. Escape raw newlines inside JSON string literals
        clean = escapeNewlinesInJsonStrings(clean)
        
        return clean
    }

    private fun escapeNewlinesInJsonStrings(json: String): String {
        val sb = java.lang.StringBuilder()
        var inString = false
        var escape = false
        for (i in json.indices) {
            val c = json[i]
            if (escape) {
                sb.append(c)
                escape = false
                continue
            }
            if (c == '\\') {
                sb.append(c)
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                sb.append(c)
                continue
            }
            if (inString && c == '\n') {
                sb.append("\\n")
            } else if (inString && c == '\r') {
                sb.append("\\r")
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Fallback parser that utilizes regular expressions to parse individual question blocks
     * sequentially, making it highly resilient even if the outer JSON container is broken/truncated.
     */
    fun parseQuestionsFallbackRegex(raw: String, quizId: String): List<Question> {
        val list = mutableListOf<Question>()
        
        val normalized = raw.replace('“', '"').replace('”', '"')
            .replace('‘', '\'').replace('’', '\'')
            
        val questionRegex = Regex("""["']question_text["']\s*:\s*["']((?:[^"'\\]|\\.)*)["']""")
        val correctAnswerRegex = Regex("""["']correct_answer["']\s*:\s*["']((?:[^"'\\]|\\.)*)["']""")
        val explanationRegex = Regex("""["']explanation["']\s*:\s*["']((?:[^"'\\]|\\.)*)["']""")
        
        val matches = questionRegex.findAll(normalized).toList()
        for (i in matches.indices) {
            val match = matches[i]
            val startIdx = match.range.first
            val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else normalized.length
            
            val block = normalized.substring(startIdx, endIdx)
            
            val qText = match.groups[1]?.value?.trim() ?: continue
            if (qText.isEmpty()) continue
            
            var optA = ""
            var optB = ""
            var optC = ""
            var optD = ""
            
            // Try matching strict 4-option array structure first
            val strictOptionsRegex = Regex("""["']options["']\s*:\s*\[\s*["']((?:[^"'\\]|\\.)*)["']\s*,\s*["']((?:[^"'\\]|\\.)*)["']\s*,\s*["']((?:[^"'\\]|\\.)*)["']\s*,\s*["']((?:[^"'\\]|\\.)*)["']\s*\]""", RegexOption.DOT_MATCHES_ALL)
            val optMatch = strictOptionsRegex.find(block)
            if (optMatch != null) {
                optA = optMatch.groups[1]?.value?.trim() ?: ""
                optB = optMatch.groups[2]?.value?.trim() ?: ""
                optC = optMatch.groups[3]?.value?.trim() ?: ""
                optD = optMatch.groups[4]?.value?.trim() ?: ""
            } else {
                val optionsSectionRegex = Regex("""["']options["']\s*:\s*\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL)
                val sectionMatch = optionsSectionRegex.find(block)
                if (sectionMatch != null) {
                    val arrayContent = sectionMatch.groups[1]?.value ?: ""
                    val individualOptions = Regex("""["']((?:[^"'\\]|\\.)*)["']""").findAll(arrayContent).map { it.groups[1]?.value?.trim() ?: "" }.toList()
                    optA = individualOptions.getOrNull(0) ?: ""
                    optB = individualOptions.getOrNull(1) ?: ""
                    optC = individualOptions.getOrNull(2) ?: ""
                    optD = individualOptions.getOrNull(3) ?: ""
                }
            }
            
            val corrMatch = correctAnswerRegex.find(block)
            val corrAns = corrMatch?.groups?.get(1)?.value?.trim() ?: ""
            
            val expMatch = explanationRegex.find(block)
            val exp = expMatch?.groups?.get(1)?.value?.trim() ?: ""
            
            val correctOpt = when {
                corrAns.equals("A", ignoreCase = true) || corrAns.equals(optA, ignoreCase = true) -> "A"
                corrAns.equals("B", ignoreCase = true) || corrAns.equals(optB, ignoreCase = true) -> "B"
                corrAns.equals("C", ignoreCase = true) || corrAns.equals(optC, ignoreCase = true) -> "C"
                corrAns.equals("D", ignoreCase = true) || corrAns.equals(optD, ignoreCase = true) -> "D"
                else -> "A"
            }
            
            if (optA.isNotEmpty() && optB.isNotEmpty()) {
                list.add(
                    Question(
                        documentId = UUID.randomUUID().toString(),
                        quizId = quizId,
                        text = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC.ifEmpty { "Option C" },
                        optionD = optD.ifEmpty { "Option D" },
                        correctOption = correctOpt,
                        explanation = exp
                    )
                )
            }
        }
        return list
    }
}
