package xyz.simoneesposito.writingassistant.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import xyz.simoneesposito.writingassistant.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.simoneesposito.writingassistant.engine.EngineState
import xyz.simoneesposito.writingassistant.engine.WritingEngine
import xyz.simoneesposito.writingassistant.util.WHITESPACE_REGEX

private const val MAX_INPUT_CHARS = 4_000

private sealed class ScreenState {
    object ToolGrid : ScreenState()
    data class Processing(val tool: WritingTool) : ScreenState()
    data class Result(val tool: WritingTool) : ScreenState()
    data class Error(val tool: WritingTool, val message: String) : ScreenState()
}

private enum class ToolGroup { Edit, Tone, Structure }

private val WritingTool.group: ToolGroup
    get() = when (this) {
        WritingTool.PROOFREAD, WritingTool.REWRITE, WritingTool.MAKE_CONCISE -> ToolGroup.Edit
        WritingTool.MAKE_FRIENDLY, WritingTool.MAKE_PROFESSIONAL -> ToolGroup.Tone
        WritingTool.SUMMARIZE, WritingTool.KEY_POINTS, WritingTool.TABLE, WritingTool.LIST -> ToolGroup.Structure
    }

@Composable
private fun ToolGroup.containerColor(enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.surfaceContainerLow
    return when (this) {
        ToolGroup.Edit -> MaterialTheme.colorScheme.primaryContainer
        ToolGroup.Tone -> MaterialTheme.colorScheme.tertiaryContainer
        ToolGroup.Structure -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
private fun ToolGroup.contentColor(enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    return when (this) {
        ToolGroup.Edit -> MaterialTheme.colorScheme.onPrimaryContainer
        ToolGroup.Tone -> MaterialTheme.colorScheme.onTertiaryContainer
        ToolGroup.Structure -> MaterialTheme.colorScheme.onSecondaryContainer
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingToolsScreen(
    inputText: String,
    isReadOnly: Boolean,
    writingEngine: WritingEngine,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val engineState by writingEngine.state.collectAsState()
    var screenState: ScreenState by remember { mutableStateOf(ScreenState.ToolGrid) }
    var currentInputText by rememberSaveable { mutableStateOf(inputText.take(MAX_INPUT_CHARS)) }
    var resultText by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val jobHolder = remember { object { var job: Job? = null } }
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMsg = stringResource(R.string.tools_copied)
    val wasInputTruncated = remember { inputText.length > MAX_INPUT_CHARS }
    val truncatedMsg = stringResource(R.string.tools_input_too_long, MAX_INPUT_CHARS)
    LaunchedEffect(wasInputTruncated) {
        if (wasInputTruncated) snackbarHostState.showSnackbar(truncatedMsg)
    }

    BackHandler {
        when (screenState) {
            is ScreenState.Result, is ScreenState.Processing, is ScreenState.Error -> {
                jobHolder.job?.cancel()
                screenState = ScreenState.ToolGrid
            }
            else -> onDismiss()
        }
    }

    LaunchedEffect(engineState) {
        if (engineState is EngineState.NotInitialized) {
            writingEngine.initialize()
        }
    }

    fun runTool(tool: WritingTool) {
        jobHolder.job?.cancel()
        screenState = ScreenState.Processing(tool)
        jobHolder.job = scope.launch {
            try {
                if (writingEngine.state.value is EngineState.Error ||
                    writingEngine.state.value is EngineState.NotInitialized
                ) {
                    writingEngine.initialize()
                }
                writingEngine.state.first { it is EngineState.Ready || it is EngineState.Error }
                val state = writingEngine.state.value
                if (state is EngineState.Error) {
                    screenState = ScreenState.Error(tool, state.message)
                    return@launch
                }
                resultText = writingEngine.transform(tool, currentInputText)
                screenState = ScreenState.Result(tool)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState = ScreenState.Error(tool, e.message ?: "Unknown error")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (screenState) {
                            is ScreenState.Result, is ScreenState.Processing, is ScreenState.Error -> {
                                jobHolder.job?.cancel()
                                screenState = ScreenState.ToolGrid
                            }
                            else -> onDismiss()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tools_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = screenState,
            transitionSpec = {
                val enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it / 3 } + fadeIn()
                val exit = slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) { -it / 4 } + fadeOut()
                enter togetherWith exit
            },
            label = "writing_tools_state",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { state ->
            when (state) {
                is ScreenState.ToolGrid -> ToolGridContent(
                    currentInputText = currentInputText,
                    onInputTextChange = { currentInputText = it },
                    engineState = engineState,
                    onToolSelected = { tool -> runTool(tool) },
                    onRetryEngine = { scope.launch { writingEngine.initialize() } }
                )
                is ScreenState.Processing -> ProcessingContent(
                    tool = state.tool,
                    onCancel = {
                        jobHolder.job?.cancel()
                        screenState = ScreenState.ToolGrid
                    }
                )
                is ScreenState.Result -> ResultContent(
                    tool = state.tool,
                    resultText = resultText,
                    onResultTextChange = { resultText = it },
                    isReadOnly = isReadOnly,
                    onCopy = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("result", resultText))); snackbarHostState.showSnackbar(copiedMsg) } },
                    onApply = { onApply(resultText) },
                    onRetry = { runTool(state.tool) },
                    onChangeTool = { runTool(it) }
                )
                is ScreenState.Error -> ErrorContent(
                    message = state.message.ifBlank { stringResource(R.string.tools_error) },
                    onRetry = { runTool(state.tool) },
                    onBack = { screenState = ScreenState.ToolGrid }
                )
            }
        }
    }
}

@Composable
private fun ToolGridContent(
    currentInputText: String,
    onInputTextChange: (String) -> Unit,
    engineState: EngineState,
    onToolSelected: (WritingTool) -> Unit,
    onRetryEngine: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Text(
            text = stringResource(R.string.tools_your_text),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(horizontal = 16.dp)
        ) {
            Box {
                TextField(
                    value = currentInputText,
                    onValueChange = { if (it.length <= MAX_INPUT_CHARS) onInputTextChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            stringResource(R.string.tools_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
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
                if (currentInputText.isNotBlank()) {
                    val atLimit = currentInputText.length >= MAX_INPUT_CHARS
                    val nearLimit = currentInputText.length >= (MAX_INPUT_CHARS * 0.85).toInt()
                    val wordCount = currentInputText.trim().split(WHITESPACE_REGEX).size
                    Text(
                        text = if (nearLimit) "${currentInputText.length} / $MAX_INPUT_CHARS"
                               else pluralStringResource(R.plurals.word_count, wordCount, wordCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            atLimit  -> MaterialTheme.colorScheme.error
                            nearLimit -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 8.dp)
                    )
                }
            }
        }

        if (engineState is EngineState.Error) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = engineState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onRetryEngine) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tools_retry), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(WritingTool.entries) { tool ->
                ToolCard(
                    tool = tool,
                    enabled = (engineState is EngineState.Ready || engineState is EngineState.Loading) && currentInputText.isNotBlank(),
                    onClick = { onToolSelected(tool) }
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: WritingTool, enabled: Boolean, onClick: () -> Unit) {
    val containerColor = tool.group.containerColor(enabled)
    val contentColor = tool.group.contentColor(enabled)

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = contentColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tool.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProcessingContent(tool: WritingTool, onCancel: () -> Unit) {
    val iconContainerColor = tool.group.containerColor(enabled = true)
    val iconContentColor = tool.group.contentColor(enabled = true)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = iconContentColor
                    )
                }
            }
            Text(
                text = stringResource(R.string.tools_applying, tool.label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp),
                color = iconContentColor,
                trackColor = iconContainerColor
            )
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.tools_cancel))
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.tools_back))
                }
                Button(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tools_retry))
                }
            }
        }
    }
}

private fun isMarkdownTable(text: String): Boolean {
    val lines = text.trim().lines().filter { it.isNotBlank() }
    if (lines.size < 3) return false
    val header = lines[0].trim()
    val sep = lines[1].trim()
    return header.contains("|") &&
        sep.startsWith("|") && sep.endsWith("|") && sep.contains(Regex("-{2,}"))
}

@Composable
private fun MarkdownTableView(markdown: String, modifier: Modifier = Modifier) {
    val separatorRegex = Regex("^\\|[-:| ]+\\|$")
    val contentLines = markdown.trim().lines()
        .filter { it.isNotBlank() && !it.trim().matches(separatorRegex) }

    if (contentLines.isEmpty()) return

    val parseRow: (String) -> List<String> = { line ->
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
    }

    val headers = parseRow(contentLines.first())
    val dataRows = contentLines.drop(1).map(parseRow)
    val colCount = headers.size.coerceAtLeast(1)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 4.dp)
                ) {
                    repeat(colCount) { idx ->
                        Text(
                            text = headers.getOrElse(idx) { "" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .widthIn(min = 80.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                dataRows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (rowIndex % 2 == 0) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        repeat(colCount) { idx ->
                            Text(
                                text = row.getOrElse(idx) { "" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .widthIn(min = 80.dp)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (rowIndex < dataRows.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    tool: WritingTool,
    resultText: String,
    onResultTextChange: (String) -> Unit,
    isReadOnly: Boolean,
    onCopy: () -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onChangeTool: (WritingTool) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(WritingTool.entries) { t ->
                val selected = t == tool
                val chipContainerColor = t.group.containerColor(enabled = true)
                val chipContentColor = t.group.contentColor(enabled = true)
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onChangeTool(t) },
                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(t.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipContainerColor,
                        selectedLabelColor = chipContentColor,
                        selectedLeadingIconColor = chipContentColor
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (tool == WritingTool.TABLE && isMarkdownTable(resultText)) {
            MarkdownTableView(
                markdown = resultText,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            OutlinedTextField(
                value = resultText,
                onValueChange = onResultTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text(stringResource(R.string.tools_result_label)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tools_retry))
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tools_copy))
            }
            if (!isReadOnly) {
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tools_replace))
                }
            }
        }
    }
}
