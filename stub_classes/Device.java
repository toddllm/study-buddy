package org.apache.tvm;

/**
 * Stub implementation of TVM Device class.
 * This is a placeholder until we integrate the actual TVM Java API.
 */
public class Device {
    // Device properties
    public int deviceType = 0;
    public int deviceId = 0;
    
    /**
     * Create an OpenCL device.
     */
    public static Device opencl() {
        return new Device();
    }
} 