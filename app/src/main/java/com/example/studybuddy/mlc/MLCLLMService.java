package com.example.studybuddy.mlc;

import android.content.Context;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Service for interacting with the on-device MLC-LLM engine.
 * This class handles model loading, text generation, and resource management.
 */
public class MLCLLMService {
    private static final String TAG = "MLCLLMService";
    private static final String MODEL_DIRECTORY_NAME = "mlc_models";
    private static final String DEFAULT_MODEL_ASSET_PATH = "models/gemma2_2b_it";
    
    private final Context context;
    private boolean isInitialized = false;
    private String modelPath;
    private String lastErrorMessage = null;
    private boolean streamingEnabled = true;
    
    public MLCLLMService(Context context) {
        this.context = context.getApplicationContext();
        Log.d(TAG, "MLCLLMService initialized with context from: " + context.getPackageName());
    }
    
    /**
     * Initialize the LLM with the default model.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        Log.d(TAG, "Initializing with default model path: " + DEFAULT_MODEL_ASSET_PATH);
        return initialize(DEFAULT_MODEL_ASSET_PATH);
    }
    
    /**
     * Get the last error message if initialization or generation failed
     */
    public String getLastErrorMessage() {
        return lastErrorMessage != null ? lastErrorMessage : "Unknown error";
    }
    
    /**
     * Initialize the LLM with a specified model.
     * @param modelAssetPath Path to the model directory in assets
     * @return true if initialization was successful
     */
    public boolean initialize(String modelAssetPath) {
        if (isInitialized) {
            Log.i(TAG, "LLM already initialized");
            return true;
        }
        
        // Reset error message
        lastErrorMessage = null;
        
        // Print all available libraries
        try {
            File jniLibsDir = new File(context.getApplicationInfo().nativeLibraryDir);
            Log.d(TAG, "JNI libraries directory: " + jniLibsDir.getAbsolutePath());
            if (jniLibsDir.exists()) {
                String[] libraries = jniLibsDir.list();
                if (libraries != null) {
                    Log.d(TAG, "Available libraries: " + Arrays.toString(libraries));
                } else {
                    Log.w(TAG, "No libraries found in native directory");
                }
            } else {
                Log.w(TAG, "Native library directory does not exist");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking native libraries: " + e.getMessage(), e);
        }
        
        // Check if libraries are loaded
        if (!TVMBridge.areLibrariesLoaded()) {
            lastErrorMessage = "Failed to load MLC-LLM native libraries";
            Log.e(TAG, lastErrorMessage);
            return false;
        }
        
        try {
            // Check if model files already exist in the internal storage
            String modelName = modelAssetPath.substring(modelAssetPath.lastIndexOf('/') + 1);
            File modelDir = new File(context.getFilesDir(), MODEL_DIRECTORY_NAME);
            File specificModelDir = new File(modelDir, modelName);
            
            if (specificModelDir.exists() && specificModelDir.isDirectory()) {
                String[] files = specificModelDir.list();
                if (files != null && files.length > 0) {
                    Log.i(TAG, "Found existing model files in " + specificModelDir.getAbsolutePath());
                    Log.d(TAG, "Files: " + Arrays.toString(files));
                    
                    // Check if config.json exists
                    if (Arrays.asList(files).contains("config.json")) {
                        Log.i(TAG, "Using existing model files");
                        modelPath = specificModelDir.getAbsolutePath();
                        
                        // Initialize the MLC-LLM runtime with existing files
                        isInitialized = TVMBridge.initRuntime(modelPath);
                        
                        if (isInitialized) {
                            Log.i(TAG, "MLC-LLM initialized successfully with existing model files");
                            // Set default parameters
                            TVMBridge.setTemperature(0.7f);
                            TVMBridge.setTopP(0.95f);
                            TVMBridge.setRepetitionPenalty(1.1f);
                            return true;
                        } else {
                            Log.w(TAG, "Failed to initialize with existing model files, will try to extract again");
                        }
                    }
                }
            }
            
            // Print assets to debug
            String[] assetFiles = context.getAssets().list("");
            Log.d(TAG, "Assets root: " + Arrays.toString(assetFiles));
            
            // Also check if pre-extracted model exists in assets
            try {
                String[] preExtractedFiles = context.getAssets().list(modelAssetPath);
                if (preExtractedFiles != null && preExtractedFiles.length > 0) {
                    Log.d(TAG, "Found pre-extracted model in assets with " + preExtractedFiles.length + " files");
                    
                    // Check if config.json exists among pre-extracted files
                    boolean hasConfigJson = false;
                    for (String file : preExtractedFiles) {
                        if ("config.json".equals(file)) {
                            hasConfigJson = true;
                            break;
                        }
                    }
                    
                    if (hasConfigJson) {
                        Log.i(TAG, "Using pre-extracted model files from assets");
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "No pre-extracted model found in assets: " + e.getMessage());
            }
            
            // Check models directory in assets
            String[] modelAssets = context.getAssets().list("models");
            Log.d(TAG, "Models in assets: " + Arrays.toString(modelAssets));
            
            // Extract model files to accessible storage
            Log.i(TAG, "Extracting model files from assets: " + modelAssetPath);
            File extractedModelDir = extractModelFromAssets(modelAssetPath);
            
            if (extractedModelDir == null) {
                lastErrorMessage = "Failed to extract model files from assets";
                Log.e(TAG, lastErrorMessage);
                
                // Try to extract the tar file instead
                if (modelAssetPath.equals(DEFAULT_MODEL_ASSET_PATH)) {
                    Log.i(TAG, "Attempting to extract from tar file...");
                    extractedModelDir = extractModelFromTarFile();
                    
                    if (extractedModelDir == null) {
                        lastErrorMessage = "Failed to extract model from tar file";
                        Log.e(TAG, lastErrorMessage);
                        return false;
                    }
                } else {
                    return false;
                }
            }
            
            // List extracted files for debugging
            Log.d(TAG, "Model directory contents: " + Arrays.toString(extractedModelDir.list()));
            
            // Initialize the MLC-LLM runtime
            modelPath = extractedModelDir.getAbsolutePath();
            Log.i(TAG, "Initializing MLC-LLM with model at: " + modelPath);
            isInitialized = TVMBridge.initRuntime(modelPath);
            
            if (isInitialized) {
                Log.i(TAG, "MLC-LLM initialized successfully");
                // Set default parameters
                TVMBridge.setTemperature(0.7f);
                TVMBridge.setTopP(0.95f);
                TVMBridge.setRepetitionPenalty(1.1f);
            } else {
                lastErrorMessage = "TVMBridge.initRuntime() returned false";
                Log.e(TAG, lastErrorMessage);
            }
            
            return isInitialized;
        } catch (Exception e) {
            lastErrorMessage = "Error initializing MLC-LLM: " + e.getMessage();
            Log.e(TAG, lastErrorMessage, e);
            return false;
        }
    }
    
    /**
     * Extract model from a tar file in assets using Apache Commons Compress
     */
    private File extractModelFromTarFile() {
        InputStream inputStream = null;
        TarArchiveInputStream tarIn = null;
        
        try {
            Log.d(TAG, "Extracting model from tar file");
            // Look for gemma tar file
            String tarFileName = "gemma-2b-it-q4f16_1-android.tar";
            
            // Create a model directory
            File modelDir = new File(context.getFilesDir(), MODEL_DIRECTORY_NAME);
            if (!modelDir.exists()) {
                if (!modelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create model directory: " + modelDir.getAbsolutePath());
                    return null;
                }
            }
            
            // Create a subdirectory for this specific model
            String modelName = "gemma2_2b_it";
            File specificModelDir = new File(modelDir, modelName);
            if (!specificModelDir.exists()) {
                if (!specificModelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create specific model directory: " + specificModelDir.getAbsolutePath());
                    return null;
                }
            }
            
            // Open the tar file from assets
            inputStream = context.getAssets().open(tarFileName);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            tarIn = new TarArchiveInputStream(bufferedInputStream);
            
            TarArchiveEntry entry;
            int extractedFiles = 0;
            
            Log.d(TAG, "Starting extraction of tar entries to " + specificModelDir.getAbsolutePath());
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    File directory = new File(specificModelDir, entry.getName());
                    if (!directory.exists() && !directory.mkdirs()) {
                        Log.w(TAG, "Failed to create directory: " + directory.getAbsolutePath());
                    }
                    continue;
                }
                
                // Fix file path (remove directory prefix if present)
                String filename = entry.getName();
                int lastSlashIndex = filename.lastIndexOf('/');
                if (lastSlashIndex != -1) {
                    filename = filename.substring(lastSlashIndex + 1);
                }
                
                File outFile = new File(specificModelDir, filename);
                
                // Skip if file already exists
                if (outFile.exists() && outFile.length() > 0) {
                    Log.d(TAG, "File already exists, skipping: " + filename);
                    continue;
                }
                
                // Ensure parent directory exists
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                Log.d(TAG, "Extracting: " + filename + " (size: " + entry.getSize() + " bytes)");
                
                FileOutputStream fos = new FileOutputStream(outFile);
                IOUtils.copy(tarIn, fos);
                fos.close();
                extractedFiles++;
            }
            
            // Create a config.json file if it doesn't exist
            File configFile = new File(specificModelDir, "config.json");
            if (!configFile.exists()) {
                Log.d(TAG, "Creating minimal config.json file");
                FileOutputStream fos = new FileOutputStream(configFile);
                String configContent = "{\n" +
                    "    \"model_name\": \"gemma-2b-it\",\n" +
                    "    \"quantization\": \"q4f16_1\",\n" +
                    "    \"model_lib\": \"libmlc_llm.so\",\n" +
                    "    \"runtime_lib\": \"libtvm_runtime.so\"\n" +
                    "}";
                fos.write(configContent.getBytes());
                fos.close();
                extractedFiles++;
            }
            
            Log.i(TAG, "Extracted " + extractedFiles + " files from tar");
            
            // Check if extraction worked
            if (extractedFiles > 0) {
                Log.i(TAG, "Successfully extracted files to " + specificModelDir.getAbsolutePath());
                return specificModelDir;
            } else {
                Log.e(TAG, "No files were extracted from the tar file");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from tar file: " + e.getMessage(), e);
            return null;
        } finally {
            try {
                if (tarIn != null) tarIn.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Generate text based on the provided prompt.
     * @param prompt The input prompt to generate text from
     * @param maxTokens Maximum number of tokens to generate
     * @return Generated text response
     */
    public String generateText(String prompt, int maxTokens) {
        if (!isInitialized) {
            Log.e(TAG, "MLC-LLM not initialized. Call initialize() first.");
            return "Error: MLC-LLM not initialized";
        }
        
        try {
            Log.i(TAG, "Generating text for prompt: " + prompt);
            String response = TVMBridge.generateText(prompt, maxTokens);
            Log.i(TAG, "Text generation completed");
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error generating text: " + e.getMessage(), e);
            return "Error generating text: " + e.getMessage();
        }
    }
    
    /**
     * Adjust the temperature parameter for text generation.
     * Higher values increase randomness.
     * @param temperature Value between 0.0 and 1.0
     */
    public void setTemperature(float temperature) {
        if (isInitialized) {
            TVMBridge.setTemperature(temperature);
        }
    }
    
    /**
     * Adjust the top-p (nucleus sampling) parameter.
     * @param topP Value between 0.0 and 1.0
     */
    public void setTopP(float topP) {
        if (isInitialized) {
            TVMBridge.setTopP(topP);
        }
    }
    
    /**
     * Adjust the repetition penalty to reduce repetitive text.
     * @param penalty Usually between 1.0 and 1.5
     */
    public void setRepetitionPenalty(float penalty) {
        if (isInitialized) {
            TVMBridge.setRepetitionPenalty(penalty);
        }
    }
    
    /**
     * Clean up resources used by the LLM.
     */
    public void shutdown() {
        if (isInitialized) {
            TVMBridge.destroyRuntime();
            isInitialized = false;
            Log.i(TAG, "MLC-LLM resources released");
        }
    }
    
    /**
     * Extract model files from assets to accessible storage.
     * @param assetPath Path to the model in assets
     * @return The directory containing the extracted model
     */
    private File extractModelFromAssets(String assetPath) {
        try {
            // Create the model directory in app's files directory
            File modelDir = new File(context.getFilesDir(), MODEL_DIRECTORY_NAME);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            
            // Create a subdirectory for this specific model
            String modelName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            File specificModelDir = new File(modelDir, modelName);
            if (!specificModelDir.exists()) {
                specificModelDir.mkdirs();
            }
            
            // Check if the asset directory exists
            try {
                context.getAssets().open(assetPath); // This will throw an exception if it's a directory
                Log.e(TAG, "Expected directory but found file at: " + assetPath);
                return null;
            } catch (IOException e) {
                // This is expected for directories
            }
            
            // Get the list of files in the asset directory
            String[] files;
            try {
                files = context.getAssets().list(assetPath);
            } catch (IOException e) {
                Log.e(TAG, "Failed to list assets in path: " + assetPath + ", error: " + e.getMessage());
                return null;
            }
            
            if (files != null && files.length > 0) {
                Log.d(TAG, "Found " + files.length + " files in asset path: " + assetPath);
                
                for (String fileName : files) {
                    Log.d(TAG, "Processing asset file: " + fileName);
                    
                    // Copy each file to the model directory
                    try {
                        InputStream input = context.getAssets().open(assetPath + "/" + fileName);
                        File outFile = new File(specificModelDir, fileName);
                        
                        // Skip if file already exists
                        if (outFile.exists() && outFile.length() > 0) {
                            Log.d(TAG, "File already exists, skipping: " + fileName);
                            continue;
                        }
                        
                        OutputStream output = new FileOutputStream(outFile);
                        byte[] buffer = new byte[8192];
                        int read;
                        long total = 0;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                            total += read;
                        }
                        input.close();
                        output.flush();
                        output.close();
                        Log.d(TAG, "Extracted file: " + fileName + " (" + total + " bytes)");
                    } catch (IOException e) {
                        Log.e(TAG, "Error extracting file " + fileName + ": " + e.getMessage());
                    }
                }
                
                // Check if we actually extracted any files
                if (specificModelDir.list() != null && specificModelDir.list().length > 0) {
                    Log.i(TAG, "Successfully extracted model files to: " + specificModelDir.getAbsolutePath());
                    return specificModelDir;
                } else {
                    Log.e(TAG, "No files were extracted to: " + specificModelDir.getAbsolutePath());
                    return null;
                }
            } else {
                Log.e(TAG, "No files found in asset path: " + assetPath);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting model files: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Generate text using streaming mode
     * @param prompt Input prompt
     * @param maxTokens Maximum tokens to generate
     * @param callback Callback to receive generated tokens
     * @return True if streaming generation started successfully
     */
    public boolean generateTextStreaming(String prompt, int maxTokens, TVMBridge.StreamingCallback callback) {
        if (!isInitialized) {
            Log.e(TAG, "Cannot generate text: model not initialized");
            lastErrorMessage = "Model not initialized";
            return false;
        }
        
        try {
            Log.d(TAG, "Starting streaming text generation for prompt: " + prompt);
            return TVMBridge.startStreamingGeneration(prompt, maxTokens, callback);
        } catch (Exception e) {
            lastErrorMessage = "Error in streaming text generation: " + e.getMessage();
            Log.e(TAG, lastErrorMessage, e);
            return false;
        }
    }
    
    /**
     * Stop any ongoing streaming text generation
     */
    public void stopTextStreaming() {
        try {
            TVMBridge.stopStreamingGeneration();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping streaming generation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if streaming text generation is enabled
     * @return true if streaming is enabled
     */
    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }
    
    /**
     * Enable or disable streaming text generation
     * @param enabled true to enable streaming, false to disable
     */
    public void setStreamingEnabled(boolean enabled) {
        streamingEnabled = enabled;
    }
    
    /**
     * Generate text using the most appropriate method (streaming or non-streaming)
     * @param prompt Input prompt
     * @param maxTokens Maximum tokens to generate
     * @param callback Callback for streaming tokens (if streaming is enabled)
     * @return Generated text if not using streaming, empty string if using streaming
     */
    public String generateTextAuto(String prompt, int maxTokens, TVMBridge.StreamingCallback callback) {
        if (streamingEnabled && callback != null) {
            generateTextStreaming(prompt, maxTokens, callback);
            return ""; // Response will come through callback
        } else {
            return generateText(prompt, maxTokens);
        }
    }
} 