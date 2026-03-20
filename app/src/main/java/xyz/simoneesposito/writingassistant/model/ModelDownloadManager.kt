package xyz.simoneesposito.writingassistant.model

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Map<String, Float> = emptyMap(),
    val completed: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val storageError: String? = null
) {
    val allRequiredComplete: Boolean
        get() = REQUIRED_MODELS.all { it.id in completed }

    val overallPercent: Int
        get() {
            val models = REQUIRED_MODELS
            if (models.isEmpty()) return 100
            val total = models.sumOf { (progress[it.id] ?: if (it.id in completed) 1f else 0f).toDouble() }
            return ((total / models.size) * 100).toInt()
        }
}

class ModelDownloadManager(private val context: Context) {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()
    private val modelsDir = File(context.filesDir, "models")
    private val activeObservers = java.util.concurrent.ConcurrentHashMap<String, Pair<LiveData<WorkInfo?>, Observer<WorkInfo?>>>()
    private val reattachObservers = java.util.concurrent.ConcurrentHashMap<String, Pair<LiveData<List<WorkInfo>>, Observer<List<WorkInfo>>>>()

    init {
        modelsDir.mkdirs()
        val completed = mutableSetOf<String>()
        REQUIRED_MODELS.forEach { model ->
            if (isModelDownloaded(model.id)) {
                completed.add(model.id)
            }
        }
        _state.update { it.copy(completed = completed) }
        reattachInProgressObservers()
    }

    fun downloadRequired() {
        val neededBytes = REQUIRED_MODELS
            .filter { !isModelDownloaded(it.id) }
            .sumOf { it.sizeMB.toLong() * 1024 * 1024 }

        if (neededBytes > 0 && !hasEnoughStorage(neededBytes)) {
            _state.update { it.copy(storageError = "Not enough storage space. Free up at least ${neededBytes / 1024 / 1024} MB and try again.") }
            return
        }

        _state.update { it.copy(isDownloading = true, storageError = null) }

        REQUIRED_MODELS.forEach { model ->
            if (!isModelDownloaded(model.id)) {
                enqueueDownload(model)
            } else {
                _state.update { state ->
                    state.copy(
                        completed = state.completed + model.id,
                        progress = state.progress + (model.id to 1f)
                    )
                }
            }
        }
    }

    private fun enqueueDownload(model: ModelInfo) {
        val targetPath = File(modelsDir, model.fileName).absolutePath
        val data = workDataOf(
            "model_id" to model.id,
            "model_url" to model.url,
            "model_path" to targetPath
        )

        _state.update { state ->
            state.copy(
                progress = state.progress + (model.id to 0f),
                errors = state.errors - model.id,
                isDownloading = true
            )
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("model_download")
            .addTag("model_${model.id}")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "download_${model.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )

        reattachObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
        activeObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }

        val liveData = WorkManager.getInstance(context).getWorkInfoByIdLiveData(request.id)
        val observer = Observer<WorkInfo?> { workInfo ->
            workInfo?.let { info ->
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt("progress", 0) / 100f
                        _state.update { state ->
                            state.copy(progress = state.progress + (model.id to progress))
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _state.update { state ->
                            state.copy(
                                completed = state.completed + model.id,
                                progress = state.progress + (model.id to 1f)
                            )
                        }
                        checkAllComplete()
                        activeObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                    }
                    WorkInfo.State.FAILED -> {
                        _state.update { state ->
                            state.copy(
                                errors = state.errors + (model.id to "Download failed"),
                                isDownloading = false
                            )
                        }
                        Log.e("ModelDownload", "Failed to download ${model.id}")
                        activeObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                    }
                    WorkInfo.State.CANCELLED -> {
                        activeObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                    }
                    else -> { /* ENQUEUED/BLOCKED */ }
                }
            }
        }
        activeObservers[model.id] = liveData to observer
        liveData.observeForever(observer)
    }

    private fun reattachInProgressObservers() {
        REQUIRED_MODELS.forEach { model ->
            if (isModelDownloaded(model.id)) return@forEach

            val liveData = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData("download_${model.id}")

            val observer = object : Observer<List<WorkInfo>> {
                override fun onChanged(value: List<WorkInfo>) {
                    val info = value.firstOrNull()
                    if (info == null) {
                        reattachObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                        return
                    }
                    when (info.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            val progress = info.progress.getInt("progress", 0) / 100f
                            _state.update { state ->
                                state.copy(
                                    progress = state.progress + (model.id to progress),
                                    isDownloading = true
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _state.update { state ->
                                state.copy(
                                    completed = state.completed + model.id,
                                    progress = state.progress + (model.id to 1f)
                                )
                            }
                            checkAllComplete()
                            reattachObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                        }
                        WorkInfo.State.FAILED -> {
                            _state.update { state ->
                                state.copy(
                                    errors = state.errors + (model.id to "Download failed"),
                                    isDownloading = false
                                )
                            }
                            Log.e("ModelDownload", "Reattached observer: failed to download ${model.id}")
                            reattachObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                        }
                        WorkInfo.State.CANCELLED -> {
                            reattachObservers.remove(model.id)?.let { (ld, obs) -> ld.removeObserver(obs) }
                        }
                    }
                }
            }

            reattachObservers[model.id] = liveData to observer
            liveData.observeForever(observer)
        }
    }

    private fun checkAllComplete() {
        if (_state.value.allRequiredComplete) {
            _state.update { it.copy(isDownloading = false) }
        }
    }

    private fun hasEnoughStorage(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(modelsDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong >= requiredBytes
        } catch (_: Exception) {
            true
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val model = REQUIRED_MODELS.firstOrNull { it.id == modelId }
            ?: return false
        return File(modelsDir, model.fileName).exists()
    }

    fun getModelPath(modelId: String): String? {
        val model = REQUIRED_MODELS.firstOrNull { it.id == modelId }
            ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun getTotalRequiredSizeMB(): Int = REQUIRED_MODELS.sumOf { it.sizeMB }
}
