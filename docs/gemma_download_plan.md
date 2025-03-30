# Gemma 2B-IT Model Download Plan

## Model Analysis Summary

The Gemma 2B-IT model in MLC format (q4f16_1) has been analyzed, and here's what we found:

- **Total Size**: 2.67 GB (176 files)
- **Weight Files**: 38 sharded parameter files (1.31 GB total)
- **Tokenizer Files**: 2 files (16.67 MB total)
- **Other Important Files**: 
  - `tokenizer.model` (4.04 MB)
  - `mlc-chat-config.json` (1.77 KB)
  - `ndarray-cache.json` (76.48 KB)

### Key Components to Download

1. **Parameter Files**: 
   - 38 sharded files (`params_shard_0.bin` through `params_shard_37.bin`)
   - Largest shard is 250MB, most are 16-32MB

2. **Tokenizer Files**:
   - `tokenizer_config.json` (2.10 KB)
   - `tokenizer.json` (16.67 MB)
   - `tokenizer.model` (4.04 MB)

3. **Configuration Files**:
   - `mlc-chat-config.json` (1.77 KB)
   - `ndarray-cache.json` (76.48 KB)

## Implementation Plan

### Step 1: Create Model Downloader Class

We will create a `GemmaModelDownloader` class with the following capabilities:

```kotlin
class GemmaModelDownloader(context: Context) {
    // Directory to store model files
    private val modelDir = File(context.filesDir, "models/gemma2_2b_it")
    
    // Download a single file from HF repository
    suspend fun downloadFile(fileName: String, progressCallback: (Float) -> Unit): Boolean
    
    // Download all required model files
    suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean
    
    // Check if model is already downloaded
    fun isModelDownloaded(): Boolean
    
    // Get model directory
    fun getModelDirectory(): File
}
```

### Step 2: Update ModelDownloadScreen

We'll enhance the existing `ModelDownloadScreen` to:

1. Check if model is already downloaded
2. Show detailed download progress for each file
3. Handle download failures with appropriate retry mechanisms
4. Verify downloaded files with checksums

### Step 3: LLM Integration

We'll update our existing JNI wrapper to:

1. Look for model files in the correct location
2. Initialize the model with the downloaded files
3. Pass the appropriate configuration parameters

## Download Strategy

1. **Parallel Downloads**: 
   - Download multiple files concurrently for speed
   - Limit concurrency to 3-4 files to avoid memory issues

2. **Resumable Downloads**:
   - Support resuming interrupted downloads
   - Check existing files before downloading

3. **Progressive Loading**:
   - Download essential files first (config, tokenizer)
   - Then download parameter shards in order
   - Allow partial model usage if possible

4. **Verification**:
   - Verify file sizes after download
   - Consider adding checksums for critical files

## URL Structure

The model files will be downloaded from Hugging Face using this URL pattern:

```
https://huggingface.co/mlc-ai/gemma-2b-it-q4f16_1-MLC/resolve/main/{filename}
```

Example:
```
https://huggingface.co/mlc-ai/gemma-2b-it-q4f16_1-MLC/resolve/main/tokenizer.json
```

## Next Implementation Steps

1. Create the `GemmaModelDownloader` class
2. Implement download functionality for a single file
3. Test downloading the smallest essential file (`tokenizer_config.json`)
4. Expand to download the full model
5. Integrate with the JNI wrapper

This plan represents the simplest path to get the real Gemma model working on device, replacing the mock implementation currently in use. 