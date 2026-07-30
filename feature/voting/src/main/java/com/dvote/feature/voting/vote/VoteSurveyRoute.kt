package com.dvote.feature.voting.vote

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.designsystem.DVoteCard
import com.dvote.core.designsystem.ErrorState
import com.dvote.core.designsystem.PrimaryAction
import com.dvote.core.designsystem.R as DesignSystemR
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.toPortableJson
import com.dvote.feature.voting.R
import com.dvote.feature.voting.messageResource

@Composable
fun VoteSurveyRoute(
    surveyId: String,
    modifier: Modifier = Modifier,
    viewModel: VoteSurveyViewModel = hiltViewModel<
        VoteSurveyViewModel,
        VoteSurveyViewModel.Factory,
    >(
        creationCallback = { factory -> factory.create(surveyId) },
    ),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    VoteSurveyScreen(
        uiState = state,
        onOptionClick = viewModel::toggleOption,
        onSubmit = viewModel::submit,
        onExportReceipt = { receipt -> shareVerificationBundle(context, receipt) },
        onDismissReceipt = viewModel::dismissReceipt,
        modifier = modifier,
    )
}

@Composable
fun VoteSurveyScreen(
    uiState: VoteSurveyUiState,
    onOptionClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onExportReceipt: (VoteReceipt) -> Unit,
    onDismissReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> VoteSurveyLoadingPlaceholder(modifier)
        uiState.survey == null -> ErrorState(
            message = stringResource(
                uiState.error?.messageResource ?: R.string.vote_survey_not_found
            ),
            modifier = modifier,
        )
        else -> VoteSurveyContent(
            state = uiState,
            onOptionClick = onOptionClick,
            onSubmit = onSubmit,
            onExportReceipt = onExportReceipt,
            onDismissReceipt = onDismissReceipt,
            modifier = modifier,
        )
    }
}

@Composable
private fun VoteSurveyContent(
    state: VoteSurveyUiState,
    onOptionClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onExportReceipt: (VoteReceipt) -> Unit,
    onDismissReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val survey = requireNotNull(state.survey)
    val interactionEnabled = !state.isSubmitting &&
        !state.isSurveyClosed &&
        !state.hasVoted &&
        !state.isVoteStatusLoading &&
        state.voteStatusError == null

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 16.dp,
        ),
    ) {
        item {
            DVoteCard {
                Text(
                    text = survey.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = survey.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        when {
            state.isSurveyClosed -> {
                item {
                    VotingAvailabilityCard(
                        message = stringResource(R.string.vote_closed_explanation),
                    )
                }
            }
            state.hasVoted -> {
                item {
                    VotingAvailabilityCard(
                        message = stringResource(R.string.vote_already_accepted_explanation),
                    )
                }
            }
            state.voteStatusError != null -> {
                item {
                    VotingAvailabilityCard(
                        message = stringResource(state.voteStatusError.messageResource),
                        isError = true,
                    )
                }
            }
        }
        item {
            val optionGroupModifier = if (survey.allowMultipleChoices) {
                Modifier
            } else {
                Modifier.selectableGroup()
            }
            Column(
                modifier = optionGroupModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                survey.options.forEach { option ->
                    OptionRow(
                        allowMultipleChoices = survey.allowMultipleChoices,
                        option = option,
                        selected = option.id in state.selectedOptionIds,
                        onClick = { onOptionClick(option.id) },
                        enabled = interactionEnabled,
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error.messageResource),
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            PrimaryAction(
                text = when {
                    state.isSubmitting -> stringResource(R.string.vote_submitting)
                    state.isSurveyClosed -> stringResource(R.string.vote_survey_closed)
                    state.hasVoted -> stringResource(R.string.vote_already_accepted)
                    state.isVoteStatusLoading -> stringResource(R.string.vote_status_checking)
                    else -> stringResource(R.string.vote_submit)
                },
                onClick = onSubmit,
                enabled = interactionEnabled &&
                    state.selectedOptionIds.isNotEmpty(),
            )
            if (state.isSubmitting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            DVoteCard {
                when {
                    !state.isSurveyClosed -> {
                        Text(
                            text = stringResource(R.string.vote_results_hidden),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.isFinalResultLoading -> {
                        Text(
                            text = stringResource(R.string.vote_result_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.finalResultError != null -> {
                        Text(
                            text = stringResource(state.finalResultError.messageResource),
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.finalResult != null -> {
                        val result = state.finalResult
                        Text(
                            text = stringResource(R.string.vote_result_heading),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.vote_total_count,
                                result.totalVotes
                                    .coerceAtMost(Int.MAX_VALUE.toLong())
                                    .toInt(),
                                result.totalVotes,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        survey.options.forEach { option ->
                            Text(
                                text = stringResource(
                                    R.string.vote_option_count,
                                    option.title,
                                    result.optionVotes[option.id] ?: 0L,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.vote_result_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    state.receipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = onDismissReceipt,
            title = {
                Text(
                    text = stringResource(R.string.vote_receipt_title),
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.vote_receipt_details,
                        receipt.receiptId,
                        receipt.commitmentHash,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onExportReceipt(receipt) }) {
                    Text(stringResource(R.string.vote_export_bundle))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReceipt) {
                    Text(stringResource(R.string.vote_receipt_dismiss))
                }
            },
        )
    }
}

@Composable
private fun VoteSurveyLoadingPlaceholder(
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(DesignSystemR.string.designsystem_loading)

    LazyColumn(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = loadingDescription
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 16.dp,
        ),
    ) {
        item {
            DVoteCard {
                LoadingLine(widthFraction = 0.42f, height = 28.dp)
                repeat(4) {
                    LoadingLine(widthFraction = 1f, height = 18.dp)
                }
                LoadingLine(widthFraction = 0.38f, height = 18.dp)
            }
        }
        repeat(2) {
            item {
                DVoteCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                        )
                        LoadingLine(widthFraction = 0.32f, height = 20.dp)
                    }
                }
            }
        }
        item {
            PrimaryAction(
                text = stringResource(R.string.vote_status_checking),
                onClick = {},
                enabled = false,
            )
        }
        item {
            DVoteCard {
                LoadingLine(widthFraction = 0.72f, height = 20.dp)
            }
        }
    }
}

@Composable
private fun LoadingLine(
    widthFraction: Float,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private fun shareVerificationBundle(
    context: Context,
    receipt: VoteReceipt,
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.vote_export_subject))
        putExtra(Intent.EXTRA_TEXT, receipt.toPortableJson())
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.vote_export_chooser),
        )
    )
}

@Composable
private fun OptionRow(
    allowMultipleChoices: Boolean,
    option: SurveyOption,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val selectionModifier = if (allowMultipleChoices) {
        Modifier.toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    }

    DVoteCard(
        modifier = selectionModifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (allowMultipleChoices) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { },
                    enabled = enabled,
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier.clearAndSetSemantics { },
                    enabled = enabled,
                )
            }
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun VotingAvailabilityCard(
    message: String,
    isError: Boolean = false,
) {
    DVoteCard {
        Text(
            text = message,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
