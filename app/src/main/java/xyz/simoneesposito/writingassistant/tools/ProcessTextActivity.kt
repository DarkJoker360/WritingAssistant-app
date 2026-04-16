package xyz.simoneesposito.writingassistant.tools

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import xyz.simoneesposito.writingassistant.MainActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.simoneesposito.writingassistant.R
import xyz.simoneesposito.writingassistant.WritingAssistantApp
import xyz.simoneesposito.writingassistant.model.ModelDownloadManager
import xyz.simoneesposito.writingassistant.model.REQUIRED_MODELS
import xyz.simoneesposito.writingassistant.setup.ModelCard
import xyz.simoneesposito.writingassistant.ui.theme.WritingAssistantTheme

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as WritingAssistantApp

        if (!app.setupManager.isSetupComplete) {
            startActivity(Intent(this, MainActivity::class.java))
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val inputText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        setContent {
            WritingAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val downloadState by app.modelDownloadManager.state.collectAsState()

                    if (downloadState.allRequiredComplete) {
                        WritingToolsScreen(
                            inputText = inputText,
                            isReadOnly = isReadOnly,
                            writingEngine = app.writingEngine,
                            onApply = { resultText ->
                                val result = Intent().apply {
                                    putExtra(Intent.EXTRA_PROCESS_TEXT, resultText)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            },
                            onDismiss = {
                                setResult(RESULT_CANCELED)
                                finish()
                            }
                        )
                    } else {
                        ModelRequiredScreen(
                            downloadManager = app.modelDownloadManager,
                            onDismiss = {
                                setResult(RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelRequiredScreen(
    downloadManager: ModelDownloadManager,
    onDismiss: () -> Unit
) {
    val downloadState by downloadManager.state.collectAsState()
    val totalSizeMB = downloadManager.getTotalRequiredSizeMB()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tools_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sheet_model_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.sheet_model_subtitle, totalSizeMB),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            REQUIRED_MODELS.forEach { model ->
                ModelCard(
                    model = model,
                    progress = downloadState.progress[model.id] ?: 0f,
                    isComplete = model.id in downloadState.completed,
                    error = downloadState.errors[model.id]
                )
            }

            val storageError = downloadState.storageError
            if (storageError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = storageError,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (downloadState.isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.setup_downloading) + " ${downloadState.overallPercent}%")
                }
            } else {
                Button(
                    onClick = { downloadManager.downloadRequired() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sheet_model_download_btn, totalSizeMB))
                }
            }
        }
    }
}
