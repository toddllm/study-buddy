package ai.mlc.mlcllm;

import org.apache.tvm.Device;
import org.apache.tvm.Function;
import org.apache.tvm.Module;
import org.apache.tvm.TVMValue;
import android.util.Log;

public class JSONFFIEngine {
    private static final String TAG = "JSONFFIEngine";
    private Module jsonFFIEngine;
    private Function initBackgroundEngineFunc;
    private Function reloadFunc;
    private Function unloadFunc;
    private Function resetFunc;
    private Function chatCompletionFunc;
    private Function abortFunc;
    private Function getLastErrorFunc;
    private Function runBackgroundLoopFunc;
    private Function runBackgroundStreamBackLoopFunc;
    private Function exitBackgroundLoopFunc;
    private Function requestStreamCallback;
    
    // Direct callback handler instead of anonymous inner class
    private static StreamCallbackHandler callbackHandler;

    public JSONFFIEngine() {
        try {
            Function createFunc = Function.getFunction("mlc.json_ffi.CreateJSONFFIEngine");
            if (createFunc == null) {
                Log.e(TAG, "Failed to get CreateJSONFFIEngine function");
                return;
            }
            
            jsonFFIEngine = createFunc.invoke().asModule();
            initBackgroundEngineFunc = jsonFFIEngine.getFunction("init_background_engine");
            reloadFunc = jsonFFIEngine.getFunction("reload");
            unloadFunc = jsonFFIEngine.getFunction("unload");
            resetFunc = jsonFFIEngine.getFunction("reset");
            chatCompletionFunc = jsonFFIEngine.getFunction("chat_completion");
            abortFunc = jsonFFIEngine.getFunction("abort");
            getLastErrorFunc = jsonFFIEngine.getFunction("get_last_error");
            runBackgroundLoopFunc = jsonFFIEngine.getFunction("run_background_loop");
            runBackgroundStreamBackLoopFunc = jsonFFIEngine.getFunction("run_background_stream_back_loop");
            exitBackgroundLoopFunc = jsonFFIEngine.getFunction("exit_background_loop");
            
            Log.d(TAG, "JSONFFIEngine initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing JSONFFIEngine", e);
        }
    }

    // Define a static class to handle callbacks without using anonymous inner classes
    public static class StreamCallbackHandler implements Function.Callback {
        private KotlinFunction callbackFunction;
        
        public StreamCallbackHandler(KotlinFunction callback) {
            this.callbackFunction = callback;
        }
        
        @Override
        public Object invoke(TVMValue... args) {
            try {
                if (args != null && args.length > 0 && args[0] != null) {
                    String response = args[0].asString();
                    if (response != null) {
                        callbackFunction.invoke(response);
                    } else {
                        Log.e(TAG, "Null response from TVM");
                        callbackFunction.invoke("");
                    }
                } else {
                    Log.e(TAG, "Invalid args in callback");
                    callbackFunction.invoke("");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in callback", e);
                callbackFunction.invoke("");
            }
            return 1;
        }
    }

    public void initBackgroundEngine(KotlinFunction callback) {
        try {
            Device device = Device.opencl();
            
            // Create a static handler instead of an anonymous inner class
            callbackHandler = new StreamCallbackHandler(callback);
            requestStreamCallback = Function.convertFunc(callbackHandler);
            
            initBackgroundEngineFunc.pushArg(device.deviceType)
                                   .pushArg(device.deviceId)
                                   .pushArg(requestStreamCallback)
                                   .invoke();
            
            Log.d(TAG, "Background engine initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing background engine", e);
        }
    }

    public void reload(String engineConfigJSONStr) {
        try {
            if (engineConfigJSONStr == null) {
                Log.e(TAG, "Cannot reload with null config");
                return;
            }
            reloadFunc.pushArg(engineConfigJSONStr).invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error reloading engine", e);
        }
    }

    public void chatCompletion(String requestJSONStr, String requestId) {
        try {
            if (requestJSONStr == null || requestId == null) {
                Log.e(TAG, "Cannot complete chat with null parameters");
                return;
            }
            chatCompletionFunc.pushArg(requestJSONStr).pushArg(requestId).invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error in chat completion", e);
        }
    }

    public void runBackgroundLoop() {
        try {
            runBackgroundLoopFunc.invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error running background loop", e);
        }
    }

    public void runBackgroundStreamBackLoop() {
        try {
            runBackgroundStreamBackLoopFunc.invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error running background stream back loop", e);
        }
    }

    public void exitBackgroundLoop() {
        try {
            exitBackgroundLoopFunc.invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error exiting background loop", e);
        }
    }

    public void unload() {
        try {
            unloadFunc.invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error unloading engine", e);
        }
    }

    public interface KotlinFunction {
        void invoke(String arg);
    }

    public void reset() {
        try {
            resetFunc.invoke();
        } catch (Exception e) {
            Log.e(TAG, "Error resetting engine", e);
        }
    }
}
