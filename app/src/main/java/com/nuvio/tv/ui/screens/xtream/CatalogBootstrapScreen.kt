package com.nuvio.tv.ui.screens.xtream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.XtreamCatalogBootPhase
import com.nuvio.tv.data.xtream.XtreamCatalogBootProgress
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.NumberFormat

/**
 * Cold-start readiness gate shown while the Xtream catalog is being prepared
 * (first bootstrap or after credentials change with no usable cache). Home only
 * composes once the catalog is ready, so no title is ever shown before its
 * playback availability is known — and the staged progress below keeps the wait
 * honest and alive instead of a static spinner.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogBootstrapScreen(
    progress: XtreamCatalogBootProgress,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "catalogBootAlpha",
    )
    val translateY by animateFloatAsState(
        targetValue = if (entered) 0f else 14f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "catalogBootTranslate",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center,
    ) {
        // Soft brand glow behind the content keeps the dark surface alive.
        Box(
            modifier = Modifier
                .size(760.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NuvioTheme.colors.Secondary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = translateY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.lume_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.width(190.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (failed) R.string.catalog_boot_error_title
                    else R.string.catalog_boot_title
                ),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = NuvioTheme.colors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (failed) R.string.catalog_boot_error_body
                    else R.string.catalog_boot_subtitle
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(38.dp))

            if (failed) {
                RetryButton(onRetry = onRetry)
            } else {
                BootStepList(progress = progress)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetryButton(onRetry: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Button(
        onClick = onRetry,
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        modifier = Modifier.focusRequester(focusRequester),
    ) {
        Text(stringResource(R.string.action_retry))
    }
}

private enum class BootStepStatus { PENDING, ACTIVE, DONE }

@Composable
private fun BootStepList(progress: XtreamCatalogBootProgress) {
    val vodDone = progress.vodCount != null
    val seriesDone = progress.seriesCount != null
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BootStepRow(
            status = BootStepStatus.DONE,
            label = stringResource(R.string.catalog_boot_account),
        )
        BootStepRow(
            status = if (vodDone) BootStepStatus.DONE else BootStepStatus.ACTIVE,
            label = stringResource(R.string.catalog_boot_movies),
            detail = progress.vodCount?.let(::formatCount),
        )
        BootStepRow(
            status = when {
                seriesDone -> BootStepStatus.DONE
                vodDone -> BootStepStatus.ACTIVE
                else -> BootStepStatus.PENDING
            },
            label = stringResource(R.string.catalog_boot_series),
            detail = progress.seriesCount?.let(::formatCount),
        )
        BootStepRow(
            status = if (progress.phase == XtreamCatalogBootPhase.INDEXING) {
                BootStepStatus.ACTIVE
            } else {
                BootStepStatus.PENDING
            },
            label = stringResource(R.string.catalog_boot_indexing),
        )
    }
}

private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BootStepRow(
    status: BootStepStatus,
    label: String,
    detail: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BootStepIndicator(status = status)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = when (status) {
                BootStepStatus.PENDING -> NuvioTheme.colors.TextTertiary
                else -> NuvioTheme.colors.TextPrimary
            },
        )
        if (detail != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun BootStepIndicator(status: BootStepStatus) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            BootStepStatus.DONE -> {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(initialScale = 0.5f, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(300)),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(NuvioTheme.colors.Secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NuvioTheme.colors.OnSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            BootStepStatus.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = NuvioTheme.colors.Secondary,
                )
            }
            BootStepStatus.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = NuvioTheme.colors.TextTertiary.copy(alpha = 0.55f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
