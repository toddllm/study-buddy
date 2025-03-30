android {
    // ... existing code ...
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // Add aaptOptions for handling large files
    aaptOptions {
        noCompress("so")
        cruncherEnabled = false
    }
    
    // Add this to handle large model files
    packagingOptions {
        resources {
            excludes += listOf("META-INF/**", "**.bin")
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    buildFeatures {
        // ... existing code ...
    }
    
    // ... existing code ...
} 