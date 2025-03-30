#include <cstdio>
#include <cstring>
#include <string>

// Define platform specific exports
#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

// Module creation function (name must match what's looked up in registry)
extern "C" EXPORT void* mlc_create_chat_module(const char* model_path) {
    // Log the call
    fprintf(stderr, "mlc_create_chat_module called with path: %s\n", model_path);
    
    // Return a non-null pointer to indicate success
    static int dummy = 42;
    return &dummy;
}

// Function implementation stubs
extern "C" EXPORT void model_load() {
    fprintf(stderr, "model_load called\n");
}

extern "C" EXPORT char* generate(const char* prompt) {
    fprintf(stderr, "generate called with prompt: %s\n", prompt);
    
    // Create a simple response
    static const char* message = "This is a simplified response from the Gemma model.";
    char* result = new char[strlen(message) + 1];
    strcpy(result, message);
    return result;
}

extern "C" EXPORT void reset_chat() {
    fprintf(stderr, "reset_chat called\n");
}

extern "C" EXPORT void set_parameter(const char* key, float value) {
    fprintf(stderr, "set_parameter called with %s=%f\n", key, value);
}

// Library initialization function
extern "C" EXPORT int __attribute__((constructor)) init_library() {
    fprintf(stderr, "Gemma model library initialized\n");
    return 0;
} 