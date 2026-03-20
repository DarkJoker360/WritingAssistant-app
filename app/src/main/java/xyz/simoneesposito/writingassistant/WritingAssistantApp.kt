package xyz.simoneesposito.writingassistant

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.simoneesposito.writingassistant.engine.WritingEngine
import xyz.simoneesposito.writingassistant.model.ModelDownloadManager
import xyz.simoneesposito.writingassistant.model.REQUIRED_MODELS
import xyz.simoneesposito.writingassistant.setup.SetupManager

class WritingAssistantApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val setupManager: SetupManager by lazy { SetupManager(this) }
    val modelDownloadManager: ModelDownloadManager by lazy { ModelDownloadManager(this) }
    val writingEngine: WritingEngine by lazy { WritingEngine(this, modelDownloadManager) }

    override fun onCreate() {
        super.onCreate()
        // Pre-load model
        if (modelDownloadManager.isModelDownloaded(REQUIRED_MODELS.first { it.required }.id)) {
            applicationScope.launch {
                writingEngine.initialize()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_COMPLETE) {
            writingEngine.release()
        }
    }
}
