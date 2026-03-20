package xyz.simoneesposito.writingassistant.model

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id") ?: return Result.failure()
        val url = inputData.getString("model_url") ?: return Result.failure()
        val path = inputData.getString("model_path") ?: return Result.failure()

        return try {
            val outputFile = File(path)
            val tempFile = File("$path.tmp")

            outputFile.parentFile?.mkdirs()
            val resumeOffset = if (tempFile.exists()) tempFile.length() else 0L

            val connection = withContext(Dispatchers.IO) {
                URL(url).openConnection()
            } as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 0
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "WritingAssistant/1.0")
            if (resumeOffset > 0) {
                connection.setRequestProperty("Range", "bytes=$resumeOffset-")
            }
            withContext(Dispatchers.IO) {
                connection.connect()
            }

            val responseCode = connection.responseCode
            val isResuming = responseCode == HttpURLConnection.HTTP_PARTIAL
            when {
                responseCode == HttpURLConnection.HTTP_OK -> {
                    if (tempFile.exists()) tempFile.delete()
                }
                !isResuming -> {
                    Log.e("ModelDownload", "HTTP $responseCode for $url")
                    return if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }

            val contentLength = connection.contentLengthLong
            val alreadyDownloaded = if (isResuming) resumeOffset else 0L
            val totalBytes = if (contentLength > 0) alreadyDownloaded + contentLength else -1L
            var downloadedBytes = alreadyDownloaded

            connection.inputStream.use { input ->
                java.io.FileOutputStream(tempFile, isResuming).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            return Result.failure()
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            setProgress(workDataOf("progress" to progress))
                        }
                    }
                }
            }
            if (tempFile.renameTo(outputFile)) {
                Log.i("ModelDownload", "Successfully downloaded $modelId (${outputFile.length()} bytes)")
                Result.success()
            } else {
                Log.e("ModelDownload", "Failed to rename temp file for $modelId")
                tempFile.delete()
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("ModelDownload", "Error downloading $modelId", e)
            if (runAttemptCount < 3) Result.retry() else {
                File("$path.tmp").delete()
                Result.failure()
            }
        }
    }
}
