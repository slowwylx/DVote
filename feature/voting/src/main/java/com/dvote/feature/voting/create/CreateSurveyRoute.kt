package com.dvote.feature.voting.create

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.designsystem.DVoteCard
import com.dvote.core.designsystem.PrimaryAction
import com.dvote.feature.voting.R
import com.dvote.feature.voting.messageResource

@Composable
fun CreateSurveyRoute(
    onSurveyCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateSurveyViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateSurveyEffect.OpenSurvey -> onSurveyCreated(effect.surveyId)
            }
        }
    }

    CreateSurveyScreen(
        uiState = state,
        onTitleChanged = viewModel::onTitleChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onAllowMultipleChoicesChanged = viewModel::onAllowMultipleChoicesChanged,
        onOptionChanged = viewModel::onOptionChanged,
        onRemoveOption = viewModel::removeOption,
        onAddOption = viewModel::addOption,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
fun CreateSurveyScreen(
    uiState: CreateSurveyUiState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAllowMultipleChoicesChanged: (Boolean) -> Unit,
    onOptionChanged: (Int, String) -> Unit,
    onRemoveOption: (Int) -> Unit,
    onAddOption: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
    ) {
        item {
            DVoteCard {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.create_survey_title_label)) },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = onDescriptionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.create_survey_description_label)) },
                    minLines = 3,
                    enabled = !uiState.isSubmitting,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = uiState.allowMultipleChoices,
                            enabled = !uiState.isSubmitting,
                            role = Role.Switch,
                            onValueChange = onAllowMultipleChoicesChanged,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.create_survey_multiple_choices))
                    Switch(
                        checked = uiState.allowMultipleChoices,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics { },
                        enabled = !uiState.isSubmitting,
                    )
                }
                Text(
                    text = stringResource(R.string.create_survey_expiry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.create_survey_options_heading),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        itemsIndexed(uiState.options) { index, option ->
            val removeOptionDescription = stringResource(
                R.string.create_survey_remove_option,
                index + 1,
            )
            val minimumOptionsDescription = stringResource(
                R.string.create_survey_minimum_options
            )
            val canRemoveOption = uiState.options.size > MIN_SURVEY_OPTION_COUNT &&
                !uiState.isSubmitting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = option,
                    onValueChange = { onOptionChanged(index, it) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            stringResource(
                                R.string.create_survey_option_label,
                                index + 1,
                            )
                        )
                    },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                )
                IconButton(
                    modifier = Modifier.semantics {
                        contentDescription = removeOptionDescription
                        if (!canRemoveOption) {
                            stateDescription = minimumOptionsDescription
                        }
                    },
                    onClick = { onRemoveOption(index) },
                    enabled = canRemoveOption,
                ) {
                    Text(
                        text = stringResource(
                            R.string.create_survey_remove_option_symbol
                        ),
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
        item {
            val hasReachedOptionLimit =
                uiState.options.size >= MAX_SURVEY_OPTION_COUNT
            AssistChip(
                onClick = onAddOption,
                label = { Text(stringResource(R.string.create_survey_add_option)) },
                enabled = !uiState.isSubmitting && !hasReachedOptionLimit,
            )
            if (hasReachedOptionLimit) {
                Text(
                    text = stringResource(
                        R.string.create_survey_option_limit_reached
                    ),
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        uiState.error?.let { error ->
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
                modifier = Modifier.padding(horizontal = 8.dp),
                text = stringResource(
                    if (uiState.isSubmitting) {
                        R.string.create_survey_submitting
                    } else {
                        R.string.create_survey_submit
                    }
                ),
                onClick = onSubmit,
                enabled = !uiState.isSubmitting,
            )
            if (uiState.isSubmitting) {
                CircularProgressIndicator()
            }
        }
    }
}
