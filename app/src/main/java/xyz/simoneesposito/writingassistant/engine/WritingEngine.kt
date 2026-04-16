package xyz.simoneesposito.writingassistant.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import xyz.simoneesposito.writingassistant.model.ModelDownloadManager
import xyz.simoneesposito.writingassistant.model.REQUIRED_MODELS
import xyz.simoneesposito.writingassistant.tools.WritingTool
import java.io.File

sealed class EngineState {
    object NotInitialized : EngineState()
    object Loading : EngineState()
    object Ready : EngineState()
    data class Error(val message: String) : EngineState()
}

class WritingEngine(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) {

    companion object {
        private const val TAG = "WritingEngine"
    }

    private var engine: Engine? = null
    private val _state = MutableStateFlow<EngineState>(EngineState.NotInitialized)
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private val sessionLock = Any()
    @Volatile private var activeConversation: AutoCloseable? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_state.value is EngineState.Ready || _state.value is EngineState.Loading) return@withContext
        _state.value = EngineState.Loading
        try {
            val modelPath = modelDownloadManager.getModelPath(REQUIRED_MODELS.first { it.required }.id)

            if (modelPath == null || !File(modelPath).exists()) {
                _state.value = EngineState.Error("No model available. Please download a model first.")
                return@withContext
            }

            engine = try {
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).also { it.initialize() }
            } catch (e: Exception) {
                Log.w(TAG, "GPU backend unavailable, falling back to CPU", e)
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).also { it.initialize() }
            }
            _state.value = EngineState.Ready
            Log.i(TAG, "WritingEngine ready with model: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            _state.value = EngineState.Error("Failed to load model: ${e.message}")
        }
    }

    fun cancelActiveSession() {
        synchronized(sessionLock) {
            try { activeConversation?.close() } catch (_: Exception) {}
            activeConversation = null
        }
    }

    suspend fun transform(tool: WritingTool, inputText: String): String =
        withContext(Dispatchers.IO) {
            val eng = engine ?: throw IllegalStateException("Engine not initialized.")
            try {
                // Close any pending session before creating a new one
                cancelActiveSession()

                val conversation = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(tool.systemPrompt),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = tool.temperature.toDouble())
                    )
                )
                synchronized(sessionLock) { activeConversation = conversation }
                try {
                    conversation.sendMessage(
                        "Apply the transformation to the text below. Output ONLY the result — no explanations, no responses to the text.\n\n<input>\n$inputText\n</input>"
                    ).toString().trim()
                } finally {
                    synchronized(sessionLock) {
                        if (activeConversation === conversation) {
                            try { conversation.close() } catch (_: Exception) {}
                            activeConversation = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transform failed for tool ${tool.name}", e)
                throw e
            }
        }

    fun release() {
        cancelActiveSession()
        try { engine?.close() } catch (e: Exception) { Log.e(TAG, "Error closing engine", e) }
        engine = null
        _state.value = EngineState.NotInitialized
    }
}
