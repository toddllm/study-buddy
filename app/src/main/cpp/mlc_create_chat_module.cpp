#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Export the required function with exact name
extern "C" {
    // This is the function MLC-LLM is looking for: mlc_create_chat_module
    void* mlc_create_chat_module(const char* model_path) {
        // Just return a non-null pointer
        static int dummy = 42;
        return &dummy;
    }
    
    // Basic implementations of required model functions
    void load_model() {
        // No-op implementation
    }
    
    char* generate(const char* prompt) {
        // Return a static message
        const char* message = "I am Gemma, a lightweight language model. Since I'm running in compatibility mode with limited functionality, "
                             "I can only provide this response. In a real implementation, I would analyze your prompt and generate a helpful answer.";
        
        char* result = (char*)malloc(strlen(message) + 1);
        strcpy(result, message);
        return result;
    }
    
    void reset_chat() {
        // No-op implementation
    }
    
    void set_parameter(const char* key, float value) {
        // No-op implementation
    }
} 