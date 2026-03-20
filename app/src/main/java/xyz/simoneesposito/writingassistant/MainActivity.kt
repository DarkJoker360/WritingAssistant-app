package xyz.simoneesposito.writingassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Warning

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.simoneesposito.writingassistant.model.REQUIRED_MODELS
import xyz.simoneesposito.writingassistant.setup.ModelCard
import xyz.simoneesposito.writingassistant.setup.SetupWizardScreen
import xyz.simoneesposito.writingassistant.tools.WritingToolsScreen
import xyz.simoneesposito.writingassistant.ui.theme.WritingAssistantTheme
import xyz.simoneesposito.writingassistant.util.WHITESPACE_REGEX

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as WritingAssistantApp
        val startRoute = if (app.setupManager.isSetupComplete) "home" else "setup"

        setContent {
            WritingAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startRoute) {
                        composable("setup") {
                            SetupWizardScreen(
                                onComplete = {
                                    navController.navigate("home") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val app = LocalContext.current.applicationContext as WritingAssistantApp
    val downloadState by app.modelDownloadManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by rememberSaveable { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    val allDownloaded = REQUIRED_MODELS.all { it.id in downloadState.completed }
    val msgApplied = stringResource(R.string.home_applied)

    // Auto-show sheet on startup if models are missing
    LaunchedEffect(Unit) {
        if (!allDownloaded) showModelSheet = true
    }
    LaunchedEffect(allDownloaded) {
        if (allDownloaded) {
            delay(1500)
            showModelSheet = false
        }
    }

    if (showModelSheet) {
        ModelDownloadSheet(
            downloadManager = app.modelDownloadManager,
            onDismiss = { showModelSheet = false }
        )
    }

    AnimatedContent(
        targetState = showTools,
        transitionSpec = {
            val enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it / 4 } + fadeIn()
            val exit = slideOutVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) { -it / 4 } + fadeOut()
            enter togetherWith exit
        },
        label = "home_tools_toggle"
    ) { inToolsMode ->
        if (inToolsMode) {
            WritingToolsScreen(
                inputText = inputText,
                isReadOnly = false,
                writingEngine = app.writingEngine,
                onApply = { resultText ->
                    inputText = resultText
                    showTools = false
                    scope.launch { snackbarHostState.showSnackbar(msgApplied) }
                },
                onDismiss = { showTools = false }
            )
        } else {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.app_name)) }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                stringResource(R.string.home_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        when {
                            !allDownloaded -> TextButton(onClick = { showModelSheet = true }) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.home_download_model),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            inputText.isNotBlank() -> {
                                val wordCount = inputText.trim().split(WHITESPACE_REGEX).size
                                Text(
                                    pluralStringResource(R.plurals.word_count, wordCount, wordCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            else -> Spacer(Modifier.width(1.dp))
                        }

                        if (inputText.isNotBlank()) {
                            IconButton(onClick = { inputText = "" }) {
                                Icon(
                                    Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.home_clear_text_cd),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Button(
                            enabled = allDownloaded && inputText.isNotBlank(),
                            onClick = { showTools = true }
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_writing_btn))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadSheet(
    downloadManager: xyz.simoneesposito.writingassistant.model.ModelDownloadManager,
    onDismiss: () -> Unit
) {
    val downloadState by downloadManager.state.collectAsState()
    val totalSizeMB = downloadManager.getTotalRequiredSizeMB()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.sheet_model_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(R.string.sheet_model_subtitle, totalSizeMB),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            REQUIRED_MODELS.forEach { model ->
                ModelCard(
                    model = model,
                    progress = downloadState.progress[model.id] ?: 0f,
                    isComplete = model.id in downloadState.completed,
                    error = downloadState.errors[model.id]
                )
            }

            Spacer(Modifier.height(4.dp))

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

            if (downloadState.isDownloading) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.setup_downloading) + " ${downloadState.overallPercent}%")
                }
            } else if (downloadState.allRequiredComplete) {
                Text(
                    stringResource(R.string.sheet_model_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
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
