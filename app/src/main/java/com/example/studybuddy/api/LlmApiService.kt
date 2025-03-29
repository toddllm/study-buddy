package com.example.studybuddy.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Data classes for API requests and responses
 */
data class LlmRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 512
)

data class LlmResponse(
    @SerializedName("text") val text: String,
    @SerializedName("model") val model: String? = null,
    @SerializedName("usage") val usage: TokenUsage? = null
)

data class TokenUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

/**
 * Retrofit interface for the LLM API
 */
interface LlmApi {
    @POST("generate")
    suspend fun generateResponse(
        @Body request: LlmRequest
    ): Response<LlmResponse>
}

/**
 * Service class for interacting with the LLM API
 */
class LlmApiService {
    private val TAG = "LlmApiService"
    private val BASE_URL = "https://api.example.com/v1/" // Replace with your actual API endpoint
    
    private val api: LlmApi by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        retrofit.create(LlmApi::class.java)
    }
    
    /**
     * Send a text prompt to the LLM server and get a response
     */
    suspend fun getCompletion(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending prompt to LLM API: $prompt")
                val request = LlmRequest(prompt = prompt)
                val response = api.generateResponse(request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "Received successful response: ${body?.text}")
                    body?.text ?: "Error: Empty response from API"
                } else {
                    Log.e(TAG, "API call failed: ${response.code()} - ${response.message()}")
                    "Error: ${response.code()} - ${response.message()}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call", e)
                "Error: ${e.message ?: "Unknown error occurred"}"
            }
        }
    }
    
    /**
     * For demo purposes when no actual API is available
     */
    fun getDemoResponse(prompt: String): String {
        val trimmedPrompt = prompt.trim().lowercase()
        return when {
            trimmedPrompt.contains("what") && trimmedPrompt.contains("see") -> 
                "Based on the OCR text you've shared, I can see this appears to be about ${getRandomTopic()}. " +
                "This topic is important in ${getRandomSubject()} and relates to ${getRandomConcept()}."
                
            trimmedPrompt.contains("explain") -> 
                "Let me explain the concept shown in the text:\n\n" +
                "The key points are:\n" +
                "1. ${getRandomExplanation()}\n" +
                "2. ${getRandomExplanation()}\n" +
                "3. ${getRandomExplanation()}\n\n" +
                "This is foundational to understanding ${getRandomConcept()}."
                
            else -> 
                "I've analyzed the text you extracted with OCR. This appears to be related to ${getRandomSubject()}. " +
                "Some key points to understand:\n\n" +
                "- ${getRandomExplanation()}\n" +
                "- ${getRandomExplanation()}\n\n" +
                "Would you like me to explain any specific aspect in more detail?"
        }
    }
    
    private fun getRandomTopic(): String {
        val topics = listOf(
            "quantum mechanics", "cellular respiration", "the Pythagorean theorem",
            "photosynthesis", "Newton's laws of motion", "DNA replication",
            "algebraic equations", "classical conditioning", "the water cycle"
        )
        return topics.random()
    }
    
    private fun getRandomSubject(): String {
        val subjects = listOf("physics", "biology", "mathematics", "chemistry", "psychology", "geology")
        return subjects.random()
    }
    
    private fun getRandomConcept(): String {
        val concepts = listOf(
            "energy conservation", "natural selection", "mathematical proof techniques",
            "molecular interactions", "cognitive development", "earth's systems",
            "problem-solving strategies", "scientific inquiry", "critical thinking"
        )
        return concepts.random()
    }
    
    private fun getRandomExplanation(): String {
        val explanations = listOf(
            "This concept demonstrates how variables interact under specific conditions",
            "The process follows a cycle that maintains equilibrium in the system",
            "These elements combine to form a larger structure with unique properties",
            "The equation describes the relationship between multiple interacting forces",
            "This principle is foundational to understanding related phenomena",
            "The pattern repeats at different scales throughout the system",
            "These components work together to transform energy from one form to another"
        )
        return explanations.random()
    }
} 