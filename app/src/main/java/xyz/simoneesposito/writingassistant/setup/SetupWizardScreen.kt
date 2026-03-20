package xyz.simoneesposito.writingassistant.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.simoneesposito.writingassistant.WritingAssistantApp

@Composable
fun SetupWizardScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 3
    val context = LocalContext.current

    BackHandler(enabled = currentStep > 0) { currentStep-- }
    val app = context.applicationContext as WritingAssistantApp

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                repeat(totalSteps) { index ->
                    val isActive = index == currentStep
                    val isDone = index < currentStep
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive || isDone -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val springSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                    (slideInHorizontally(animationSpec = springSpec) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(animationSpec = springSpec) { -it } + fadeOut())
                },
                label = "wizard_step"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(onNext = { currentStep = 1 })
                    1 -> ModelDownloadStep(
                        downloadManager = app.modelDownloadManager,
                        onNext = { currentStep = 2 }
                    )
                    2 -> ReadyStep(onComplete = {
                        app.setupManager.markSetupComplete()
                        onComplete()
                    })
                }
            }
        }
    }
}
