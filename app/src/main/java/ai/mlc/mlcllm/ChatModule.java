package ai.mlc.mlcllm;

/**
 * A simplified interface for ChatModule to allow compilation without the actual implementation.
 * This will be replaced with the real implementation when building with MLC-LLM.
 */
public class ChatModule {
    public ChatModule(String modelPath) {
        // In the real implementation, this would initialize the model
    }
    
    public String chat(String prompt) {
        // In the real implementation, this would generate a response
        return "This is a placeholder response. To get real responses, build with MLC-LLM.";
    }
    
    public void resetChat() {
        // In the real implementation, this would reset the chat state
    }
    
    public void setGenerationConfig(GenerationConfig config) {
        // In the real implementation, this would set the generation config
    }
    
    public void close() {
        // In the real implementation, this would clean up resources
    }
}
