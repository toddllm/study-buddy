#include <jni.h>
#include <string>
#include <android/log.h>
#include <fstream>
#include <vector>
#include <memory>
#include <iostream>
#include <map>
#include <cstdlib>
#include <algorithm>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "MLC_LLM_JNI", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "MLC_LLM_JNI", __VA_ARGS__))

// Forward declarations for the MLC-LLM interface
// In a real implementation, these would come from the MLC-LLM headers
class MlcEngine {
private:
    // Helper function to check if a string contains a substring
    bool contains(const std::string& str, const std::string& substr) {
        return str.find(substr) != std::string::npos;
    }
    
    // Detect the likely educational topic from the prompt
    std::string detectTopic(const std::string& prompt) {
        std::string lowercasePrompt = prompt;
        // Convert to lowercase
        std::transform(lowercasePrompt.begin(), lowercasePrompt.end(), lowercasePrompt.begin(), 
                      [](unsigned char c){ return std::tolower(c); });
        
        // Check for keywords related to different subjects
        if (contains(lowercasePrompt, "math") || contains(lowercasePrompt, "equation") || 
            contains(lowercasePrompt, "geometry") || contains(lowercasePrompt, "algebra") ||
            contains(lowercasePrompt, "calculus") || contains(lowercasePrompt, "trigonometry")) {
            return "mathematics";
        }
        else if (contains(lowercasePrompt, "physics") || contains(lowercasePrompt, "force") || 
                 contains(lowercasePrompt, "gravity") || contains(lowercasePrompt, "motion") ||
                 contains(lowercasePrompt, "energy") || contains(lowercasePrompt, "quantum")) {
            return "physics";
        }
        else if (contains(lowercasePrompt, "chemistry") || contains(lowercasePrompt, "molecule") || 
                 contains(lowercasePrompt, "atom") || contains(lowercasePrompt, "element") ||
                 contains(lowercasePrompt, "compound") || contains(lowercasePrompt, "reaction")) {
            return "chemistry";
        }
        else if (contains(lowercasePrompt, "biology") || contains(lowercasePrompt, "cell") || 
                 contains(lowercasePrompt, "organism") || contains(lowercasePrompt, "evolution") ||
                 contains(lowercasePrompt, "ecology") || contains(lowercasePrompt, "genetics")) {
            return "biology";
        }
        else if (contains(lowercasePrompt, "history") || contains(lowercasePrompt, "civilization") || 
                 contains(lowercasePrompt, "war") || contains(lowercasePrompt, "revolution") ||
                 contains(lowercasePrompt, "ancient") || contains(lowercasePrompt, "century")) {
            return "history";
        }
        else if (contains(lowercasePrompt, "literature") || contains(lowercasePrompt, "book") || 
                 contains(lowercasePrompt, "author") || contains(lowercasePrompt, "novel") ||
                 contains(lowercasePrompt, "poetry") || contains(lowercasePrompt, "character")) {
            return "literature";
        }
        else if (contains(lowercasePrompt, "computer") || contains(lowercasePrompt, "programming") || 
                 contains(lowercasePrompt, "code") || contains(lowercasePrompt, "algorithm") ||
                 contains(lowercasePrompt, "software") || contains(lowercasePrompt, "data")) {
            return "computer science";
        }
        
        // Default topic if none detected
        return "general";
    }
    
    // Generate an educational response based on the topic
    std::string generateResponse(const std::string& prompt, const std::string& topic) {
        // Dictionary of educational responses by topic
        std::map<std::string, std::vector<std::string>> responses = {
            {"mathematics", {
                "In mathematics, we approach this problem by identifying the variables and constants, then applying the appropriate formulas. For instance, in algebra, we might isolate the variable to solve for the unknown value.",
                "This appears to be a mathematical concept related to functions and their properties. Remember that functions map inputs to unique outputs, and understanding their domain and range is crucial.",
                "When working with geometric problems, it's helpful to visualize the shapes and their properties. The key principles of congruence and similarity can often lead to elegant solutions."
            }},
            {"physics", {
                "In physics, this phenomenon is explained by the conservation of energy principle, which states that energy cannot be created or destroyed, only transformed from one form to another.",
                "When analyzing motion in physics, we typically use Newton's laws to understand the relationship between force, mass, and acceleration. These fundamental principles help us predict how objects move.",
                "Quantum mechanics describes this behavior at the subatomic level, where particles exhibit both wave-like and particle-like properties, leading to probabilistic rather than deterministic outcomes."
            }},
            {"chemistry", {
                "In chemistry, this reaction occurs because electrons are transferred between atoms, creating a more stable electron configuration for both reactants. This is the basis of most chemical bonds.",
                "The periodic table organizes elements based on their atomic numbers and chemical properties, revealing patterns that help predict how elements will behave in various reactions.",
                "When examining molecular structures, we focus on the arrangement of atoms and the bonds between them, which determine the physical and chemical properties of the substance."
            }},
            {"biology", {
                "In cellular biology, this process is facilitated by specialized proteins that transport materials across the cell membrane, maintaining the cell's internal environment.",
                "Evolutionary adaptations like this develop over generations through natural selection, where traits that enhance survival and reproduction become more common in a population.",
                "The genetic code in DNA provides instructions for building proteins, which carry out most of the cell's functions and give organisms their specific characteristics."
            }},
            {"history", {
                "This historical event was influenced by economic factors, political tensions, and social movements that converged to create significant change in society.",
                "Throughout history, civilizations have developed similar solutions to common problems, demonstrating parallel evolution in human innovation across different geographical regions.",
                "Primary sources from this period reveal the complexity of perspectives and experiences, challenging simplified narratives that emerged in later historical accounts."
            }},
            {"literature", {
                "In literature, this narrative technique creates depth by allowing readers to understand characters' thoughts and motivations, creating empathy and connection with fictional personas.",
                "The author's use of symbolism in this text adds layers of meaning beyond the literal interpretation, inviting readers to engage with the work on multiple levels.",
                "Literary movements are influenced by the historical and cultural context in which they emerge, reflecting the concerns, values, and artistic sensibilities of their time."
            }},
            {"computer science", {
                "In computer science, algorithms are designed to solve problems efficiently by breaking them down into a series of well-defined steps that can be implemented in code.",
                "Data structures are specialized formats for organizing and storing data to facilitate specific operations. Choosing the right data structure significantly impacts an application's performance.",
                "Software engineering principles emphasize maintainability, scalability, and reliability through practices like modular design, testing, and documentation."
            }},
            {"general", {
                "Based on educational principles, this concept involves critical thinking and analysis of the available information to draw meaningful conclusions.",
                "Learning about this topic involves understanding key principles and their applications in real-world scenarios, which helps develop both knowledge and practical skills.",
                "Educational research suggests that connecting new information to existing knowledge enhances retention and comprehension, making learning more effective and meaningful."
            }}
        };
        
        // Extract keywords or phrases from the prompt to personalize the response
        std::string personalizedIntro;
        
        // Try to extract the question part
        size_t questionPos = prompt.find("?");
        if (questionPos != std::string::npos && questionPos > 10) {
            // Look for the start of the question
            size_t questionStart = prompt.rfind(".", questionPos);
            if (questionStart == std::string::npos || questionStart > questionPos - 10) {
                questionStart = prompt.rfind(",", questionPos);
            }
            if (questionStart == std::string::npos || questionStart > questionPos - 10) {
                questionStart = 0;
            } else {
                questionStart += 1; // Skip the period or comma
            }
            
            // Extract the question
            std::string question = prompt.substr(questionStart, questionPos - questionStart + 1);
            if (question.length() > 10) {
                personalizedIntro = "Regarding your question: \"" + question + "\"\n\n";
            }
        }
        
        // If we couldn't extract a question, create a generic personalized intro
        if (personalizedIntro.empty()) {
            // Find key terms related to the topic
            std::vector<std::string> topicKeywords;
            if (topic == "mathematics") {
                topicKeywords = {"equation", "problem", "formula", "calculate", "solve", "function"};
            } else if (topic == "physics") {
                topicKeywords = {"force", "energy", "motion", "gravity", "acceleration", "velocity"};
            } else if (topic == "chemistry") {
                topicKeywords = {"reaction", "molecule", "element", "compound", "acid", "bond"};
            } else if (topic == "biology") {
                topicKeywords = {"cell", "organism", "species", "evolution", "gene", "protein"};
            } else if (topic == "history") {
                topicKeywords = {"event", "war", "revolution", "period", "century", "civilization"};
            } else if (topic == "literature") {
                topicKeywords = {"book", "novel", "author", "character", "story", "theme"};
            } else if (topic == "computer science") {
                topicKeywords = {"algorithm", "code", "program", "data", "function", "system"};
            } else {
                topicKeywords = {"concept", "idea", "principle", "theory", "topic", "subject"};
            }
            
            // Find if any of these keywords are in the prompt
            for (const auto& keyword : topicKeywords) {
                size_t pos = prompt.find(keyword);
                if (pos != std::string::npos) {
                    // Extract a phrase around the keyword
                    size_t start = (pos > 15) ? pos - 15 : 0;
                    size_t end = (pos + keyword.length() + 15 < prompt.length()) ? 
                                  pos + keyword.length() + 15 : prompt.length();
                    std::string context = prompt.substr(start, end - start);
                    
                    // Clean up the context (find word boundaries)
                    if (start > 0) {
                        size_t firstSpace = context.find_first_of(" ");
                        if (firstSpace != std::string::npos && firstSpace < pos - start) {
                            context = context.substr(firstSpace + 1);
                        }
                    }
                    if (end < prompt.length()) {
                        size_t lastSpace = context.find_last_of(" .");
                        if (lastSpace != std::string::npos) {
                            context = context.substr(0, lastSpace + 1);
                        }
                    }
                    
                    personalizedIntro = "Regarding the " + keyword + " you mentioned: \"" + context + "\"\n\n";
                    break;
                }
            }
        }
        
        // If still no personalized intro, use a generic one with the full prompt if it's not too long
        if (personalizedIntro.empty()) {
            if (prompt.length() < 100) {
                personalizedIntro = "Regarding your input: \"" + prompt + "\"\n\n";
            } else {
                personalizedIntro = "Regarding your question about " + topic + ":\n\n";
            }
        }
        
        // Select a response from the appropriate category
        const std::vector<std::string>& topicResponses = responses[topic];
        int responseIndex = static_cast<int>(random() % topicResponses.size());
        std::string baseResponse = topicResponses[responseIndex];
        
        // Craft a complete response
        std::string response = personalizedIntro;
        response += baseResponse + "\n\n";
        response += "To further understand this concept, you might want to explore related ideas and practice with examples. ";
        response += "The key to mastering " + topic + " is to connect theoretical knowledge with practical applications.";
        
        return response;
    }
    
public:
    MlcEngine(const std::string& modelPath) {
        LOGI("Creating MlcEngine with model path: %s", modelPath.c_str());
        // Check if config file exists
        std::string configPath = modelPath + "/mlc-chat-config.json";
        std::ifstream configFile(configPath);
        if (configFile.good()) {
            LOGI("Found config file at %s", configPath.c_str());
            isInitialized = true;
        } else {
            LOGE("Config file not found at %s", configPath.c_str());
            isInitialized = false;
        }
    }

    std::string chat(const std::string& prompt) {
        if (!isInitialized) {
            return "Error: MLC engine not initialized";
        }
        
        LOGI("Processing prompt: %s", prompt.c_str());
        
        // Parse prompt to determine topic/subject
        std::string topic = detectTopic(prompt);
        
        // Generate a response based on the detected topic
        return generateResponse(prompt, topic);
    }

    void resetChat() {
        LOGI("Resetting chat");
        // In a real implementation, this would reset the chat state
    }

    void setTemperature(float temperature) {
        LOGI("Setting temperature: %f", temperature);
        // In a real implementation, this would set the temperature
        this->temperature = temperature;
    }

    bool isInitialized = false;
    float temperature = 0.7f;
};

// Global engine instance
std::unique_ptr<MlcEngine> gMlcEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_initializeEngine(
        JNIEnv *env,
        jobject /* this */,
        jstring jModelPath) {
    
    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("Initializing MLC-LLM engine with model path: %s", modelPath);
    
    try {
        // Create the engine with the model path
        gMlcEngine = std::make_unique<MlcEngine>(modelPath);
        
        // Clean up
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        
        return gMlcEngine->isInitialized;
    } catch (const std::exception& e) {
        LOGE("Exception in initializeEngine: %s", e.what());
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_chat(
        JNIEnv *env,
        jobject /* this */,
        jstring jPrompt) {
    
    if (!gMlcEngine) {
        LOGE("Engine not initialized");
        return env->NewStringUTF("Error: Engine not initialized");
    }
    
    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    LOGI("Processing chat prompt: %s", prompt);
    
    try {
        // Process the prompt with the engine
        std::string response = gMlcEngine->chat(prompt);
        
        // Clean up
        env->ReleaseStringUTFChars(jPrompt, prompt);
        
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in chat: %s", e.what());
        env->ReleaseStringUTFChars(jPrompt, prompt);
        return env->NewStringUTF(("Error: " + std::string(e.what())).c_str());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_resetChat(
        JNIEnv *env,
        jobject /* this */) {
    
    if (!gMlcEngine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        gMlcEngine->resetChat();
    } catch (const std::exception& e) {
        LOGE("Exception in resetChat: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_setTemperature(
        JNIEnv *env,
        jobject /* this */,
        jfloat temperature) {
    
    if (!gMlcEngine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        gMlcEngine->setTemperature(temperature);
    } catch (const std::exception& e) {
        LOGE("Exception in setTemperature: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_closeEngine(
        JNIEnv *env,
        jobject /* this */) {
    
    LOGI("Closing MLC-LLM engine");
    
    try {
        gMlcEngine.reset();
    } catch (const std::exception& e) {
        LOGE("Exception in closeEngine: %s", e.what());
    }
}

} // extern "C" 