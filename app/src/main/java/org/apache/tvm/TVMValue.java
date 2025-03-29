package org.apache.tvm;

/**
 * Stub implementation of TVM TVMValue class.
 * This is a placeholder until we integrate the actual TVM Java API.
 */
public class TVMValue {
    /**
     * Get string value.
     */
    public String asString() {
        return "";
    }

    /**
     * Get int value.
     */
    public int asInt() {
        return 0;
    }

    /**
     * Get float value.
     */
    public float asFloat() {
        return 0.0f;
    }
    
    /**
     * Get as module.
     */
    public Module asModule() {
        return new Module();
    }
} 