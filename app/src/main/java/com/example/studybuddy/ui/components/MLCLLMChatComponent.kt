package com.example.studybuddy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.SuggestionChip
import com.example.studybuddy.ml.MlcLanguageModel

// Data class to represent a message in the chat
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A composable UI component for testing the MLC-LLM integration.
 * Shows a simple chat interface with the on-device language model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MLCLLMChatComponent(
    model: MlcLanguageModel,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // States
    var prompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing MLC-LLM...") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Chat history
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    val scrollState = rememberLazyListState()
    
    // Get the model from parameter
    val languageModel = remember { model }
    val modelInitialized = remember { mutableStateOf(false) }
    
    // Initialize the model
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                try {
                    // Check if model is already initialized
                    modelInitialized.value = languageModel.areModelFilesAvailable()
                    
                    if (!modelInitialized.value) {
                        // Model files not available - we'd need to download them first
                        statusMessage = "Model files not found. Please download the model first."
                        return@withContext
                    }
                    
                    // Initialize the model
                    languageModel.initialize()
                    modelInitialized.value = true
                    withContext(Dispatchers.Main) {
                        statusMessage = "MLC-LLM initialized successfully"
                        // Add a welcome message when initialized
                        chatHistory = chatHistory + ChatMessage(
                            "Hello! I'm StudyBuddy AI. How can I help you with your studies today?",
                            false
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Failed to create MLC-LLM model"
                        errorMessage = e.message
                        modelInitialized.value = false
                    }
                }
            }
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
            errorMessage = e.message
            chatHistory = chatHistory + ChatMessage(
                "Error initializing the language model: ${e.message}",
                false,
                true
            )
        }
    }
    
    // Auto-scroll to the bottom when new messages are added
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            scrollState.animateScrollToItem(chatHistory.size - 1)
        }
    }
    
    // Function to send a message and get a response
    fun sendMessage() {
        if (prompt.isBlank() || !modelInitialized.value || isLoading) return
        
        val userMessage = ChatMessage(prompt, true)
        chatHistory = chatHistory + userMessage
        
        // Clear input field immediately after sending
        val currentPrompt = prompt
        prompt = ""
        
        isLoading = true
        
        // Generate the context from previous conversation (last few messages)
        val conversationContext = buildString {
            // Add a system prompt to guide the model's behavior for a study assistant
            append("Assistant: Hello! I'm StudyBuddy AI, a helpful study assistant. I can answer questions, explain concepts, and help you learn efficiently. How can I assist you today?\n")
            
            // Add the recent conversation history (last 6 messages maximum)
            chatHistory.takeLast(6).forEach { message ->
                if (message.isFromUser) {
                    append("User: ${message.text}\n")
                } else if (!message.isError) {
                    append("Assistant: ${message.text}\n")
                }
            }
        }
        
        // Create a placeholder for the response
        chatHistory = chatHistory + ChatMessage("", false)
        
        // Start text generation
        coroutineScope.launch {
            try {
                val fullPrompt = conversationContext + "User: $currentPrompt\nAssistant:"
                
                // Use streamText for streaming response
                var responseText = ""
                
                languageModel.streamText(
                    prompt = fullPrompt,
                    onToken = { token ->
                        responseText += token
                        chatHistory = chatHistory.toMutableList().apply {
                            val lastIndex = lastIndex
                            this[lastIndex] = ChatMessage(responseText, false)
                        }
                    },
                    onError = { error ->
                        chatHistory = chatHistory.toMutableList().apply {
                            val lastIndex = lastIndex
                            this[lastIndex] = ChatMessage(
                                "Error generating response: $error",
                                false,
                                true
                            )
                        }
                    }
                )
                
                isLoading = false
            } catch (e: Exception) {
                // Handle errors
                isLoading = false
                chatHistory = chatHistory.toMutableList().apply {
                    val lastIndex = lastIndex
                    this[lastIndex] = ChatMessage(
                        "Error generating response: ${e.message}",
                        false,
                        true
                    )
                }
            }
        }
    }
    
    // Error dialog
    if (showErrorDialog && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error Details") },
            text = { 
                Text(errorMessage ?: "Unknown error")
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (modelInitialized.value) Color(0xFF4CAF50) else Color(0xFFF44336)
            ),
            onClick = { 
                if (!modelInitialized.value && errorMessage != null) {
                    showErrorDialog = true
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusMessage,
                    color = Color.White
                )
                
                if (!modelInitialized.value && errorMessage != null) {
                    TextButton(
                        onClick = { showErrorDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("View Details")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main chat UI layout - after the status indicator card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Chat history display
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = scrollState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Add suggested topics at the top if this is a fresh conversation
                if (chatHistory.isEmpty() || (chatHistory.size == 1 && !chatHistory[0].isFromUser)) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Study Topics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuggestedTopic("Explain quantum physics") { 
                                        prompt = it
                                        sendMessage()
                                    }
                                    SuggestedTopic("Calculus help") { 
                                        prompt = it
                                        sendMessage()
                                    }
                                    SuggestedTopic("Photosynthesis") { 
                                        prompt = it
                                        sendMessage()
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Display chat messages
                items(chatHistory) { message ->
                    ChatMessageItem(message)
                }
                
                // Show typing indicator if loading
                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Input area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter your message...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (modelInitialized.value && !isLoading) sendMessage() }
                ),
                enabled = !isLoading && modelInitialized.value,
                trailingIcon = {
                    if (prompt.isNotEmpty()) {
                        IconButton(onClick = { prompt = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = { sendMessage() },
                enabled = prompt.isNotEmpty() && !isLoading && modelInitialized.value
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Display a suggested topic as a chip/button that the user can click to start a conversation
 */
@Composable
fun SuggestedTopic(topic: String, onClick: (String) -> Unit) {
    SuggestionChip(
        onClick = { onClick(topic) },
        label = { Text(topic) }
    )
}

/**
 * Displays a chat message with appropriate styling based on whether it's from the user or AI
 */
@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isFromUser = message.isFromUser
    val isError = message.isError
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = if (isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isFromUser) 12.dp else 4.dp,
                        bottomEnd = if (isFromUser) 4.dp else 12.dp
                    )
                )
                .background(
                    when {
                        isError -> MaterialTheme.colorScheme.errorContainer
                        isFromUser -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (isFromUser) "You" else "StudyBuddy AI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isError) 
                    MaterialTheme.colorScheme.error 
                else if (isFromUser) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Displays a typing indicator animation.
 */
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    
    val dotScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val dotScale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val dotScale3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8F5E9))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp * dotScale1)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray)
            )
            Box(
                modifier = Modifier
                    .size(8.dp * dotScale2)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray)
            )
            Box(
                modifier = Modifier
                    .size(8.dp * dotScale3)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray)
            )
        }
    }
} 