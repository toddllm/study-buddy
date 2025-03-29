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
import com.example.studybuddy.ml.MLCLLMService
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
    
    // Chat history
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    val scrollState = rememberLazyListState()
    
    // Get the LLMMLC service
    val llmService = remember { MLCLLMService.getInstance(context) }
    
    // Collect model loaded state
    val isModelLoaded by llmService.isModelLoaded.collectAsState()
    
    // Collect streaming text
    val streamingText by llmService.textStream.collectAsState()
    
    // Collect error message
    val errorMessage by llmService.errorMessage.collectAsState()
    
    // Update when streaming text changes
    LaunchedEffect(streamingText) {
        if (streamingText.isNotEmpty() && chatHistory.isNotEmpty() && !chatHistory.last().isFromUser) {
            // Update the last message with streaming text
            chatHistory = chatHistory.toMutableList().apply {
                val lastIndex = lastIndex
                this[lastIndex] = ChatMessage(streamingText.first(), false)
            }
        }
    }
    
    // Initialize the model
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val result = llmService.loadModel()
                withContext(Dispatchers.Main) {
                    if (result) {
                        // Add a welcome message when initialized
                        chatHistory = chatHistory + ChatMessage(
                            "Hello! I'm StudyBuddy AI. How can I help you with your studies today?",
                            false
                        )
                        statusMessage = "MLC-LLM initialized successfully"
                    } else {
                        statusMessage = "Failed to initialize MLC-LLM"
                    }
                }
            }
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
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
        if (prompt.isBlank() || !isModelLoaded || isLoading) return
        
        val userMessage = ChatMessage(prompt, true)
        chatHistory = chatHistory + userMessage
        
        // Clear input field immediately after sending
        val currentPrompt = prompt
        prompt = ""
        
        isLoading = true
        
        // Generate the context from previous conversation (last few messages)
        val conversationContext = buildString {
            chatHistory.takeLast(6).forEach { message ->
                if (message.isFromUser) {
                    append("User: ${message.text}\n")
                } else if (!message.isError) {
                    append("Assistant: ${message.text}\n")
                }
            }
        }
        
        // Create a placeholder for the streaming response
        chatHistory = chatHistory + ChatMessage("", false)
        
        // Start streaming generation
        coroutineScope.launch {
            try {
                llmService.streamText(conversationContext + "User: $currentPrompt")
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
                containerColor = if (isModelLoaded) Color(0xFF4CAF50) else Color(0xFFF44336)
            ),
            onClick = { 
                if (!isModelLoaded && errorMessage != null) {
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
                
                if (!isModelLoaded && errorMessage != null) {
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
        
        // Chat history display
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory) { message ->
                ChatMessageBubble(message)
            }
            
            // Show typing indicator if loading
            if (isLoading) {
                item {
                    TypingIndicator()
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
                    onSend = { if (isModelLoaded && !isLoading) sendMessage() }
                ),
                enabled = !isLoading && isModelLoaded,
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
                enabled = prompt.isNotEmpty() && !isLoading && isModelLoaded
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Displays a chat message bubble.
 */
@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val backgroundColor = when {
        message.isError -> Color(0xFFFFEBEE) // Light red for error messages
        message.isFromUser -> Color(0xFFE3F2FD) // Light blue for user messages
        else -> Color(0xFFE8F5E9) // Light green for AI messages
    }
    
    val textColor = when {
        message.isError -> Color(0xFFB71C1C) // Dark red for error text
        else -> Color.Black
    }
    
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val shape = when {
        message.isFromUser -> RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender label
        Text(
            text = if (message.isFromUser) "You" else "StudyBuddy AI",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        
        // Message bubble
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text.ifEmpty { "..." },
                color = textColor
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