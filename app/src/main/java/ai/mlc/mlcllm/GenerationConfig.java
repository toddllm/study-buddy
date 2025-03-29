package ai.mlc.mlcllm;

/**
 * A simplified GenerationConfig class to allow compilation without the actual implementation.
 */
public class GenerationConfig {
    private float temperature;
    private float topP;
    private int maxGenLen;
    
    private GenerationConfig(Builder builder) {
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxGenLen = builder.maxGenLen;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float temperature = 0.7f;
        private float topP = 0.95f;
        private int maxGenLen = 1024;
        
        public Builder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public Builder topP(float topP) {
            this.topP = topP;
            return this;
        }
        
        public Builder maxGenLen(int maxGenLen) {
            this.maxGenLen = maxGenLen;
            return this;
        }
        
        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}
