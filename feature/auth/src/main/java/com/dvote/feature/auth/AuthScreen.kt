package com.dvote.feature.auth

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    isRepairRequired: Boolean,
    onGoogleSignIn: () -> Unit,
    onRepairProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "auth gradient")
    val startColor = infiniteTransition.animateColor(
        initialValue = colorScheme.surface,
        targetValue = colorScheme.secondaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = GradientDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auth gradient start",
    )
    val endColor = infiniteTransition.animateColor(
        initialValue = colorScheme.secondaryContainer,
        targetValue = colorScheme.surface,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = GradientDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auth gradient end",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(startColor.value, endColor.value),
                    ),
                )
            }
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.17f))
        Text(
            text = stringResource(R.string.auth_welcome),
            modifier = Modifier
                .wrapContentSize()
                .semantics { heading() },
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(0.43f))
        Text(
            text = stringResource(
                if (isRepairRequired) {
                    R.string.auth_repair_prompt
                } else {
                    R.string.auth_sign_in_prompt
                }
            ),
            modifier = Modifier.wrapContentSize(),
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(0.035f))
        if (isRepairRequired) {
            AccountRepairActions(
                isLoading = uiState.isLoading,
                onRepairProfile = onRepairProfile,
                onSignOut = onSignOut,
            )
        } else {
            GoogleSignInButton(
                isLoading = uiState.isLoading,
                onClick = onGoogleSignIn,
            )
        }
        uiState.error?.let { error ->
            Text(
                text = stringResource(error.messageResource),
                modifier = Modifier
                    .padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                    )
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(0.365f))
    }
}

@Composable
private fun AccountRepairActions(
    isLoading: Boolean,
    onRepairProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onRepairProfile,
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                Text(
                    text = stringResource(R.string.auth_repair_action),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        TextButton(
            onClick = onSignOut,
            enabled = !isLoading,
        ) {
            Text(
                text = stringResource(R.string.auth_sign_out_action),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signInDescription = stringResource(R.string.auth_google_sign_in)
    val signingInDescription = stringResource(R.string.auth_signing_in)

    Box(
        modifier = modifier
            .size(64.dp)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .clickable(
                enabled = !isLoading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = signInDescription
                if (isLoading) {
                    stateDescription = signingInDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White,
                trackColor = Color.Black,
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
            )
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified,
            )
        }
    }
}

private const val GradientDurationMillis = 6_000
