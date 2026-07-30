package com.dvote.feature.voting.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.designsystem.DVoteCard
import com.dvote.core.designsystem.EmptyState
import com.dvote.core.designsystem.ErrorState
import com.dvote.core.designsystem.LoadingState
import com.dvote.core.model.Survey
import com.dvote.feature.voting.R
import com.dvote.feature.voting.messageResource
import java.time.ZoneId

@Composable
fun HomeRoute(
    onSurveyClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = ZoneId.systemDefault()
    val dateFormatter = remember(locale, zoneId) {
        surveyDateFormatter(locale, zoneId)
    }

    HomeScreen(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        formatClosingDate = dateFormatter::formatSurveyDate,
        onSurveyClick = onSurveyClick,
        onLoadMore = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    formatClosingDate: (Long) -> String,
    onSurveyClick: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> LoadingState(modifier)
        is HomeUiState.Error -> ErrorState(
            message = stringResource(uiState.error.messageResource),
            modifier = modifier,
            onRetry = onRetry,
        )
        is HomeUiState.Content -> {
            if (uiState.surveys.isEmpty() && !uiState.canLoadMore) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    modifier = modifier,
                )
            } else if (uiState.surveys.isEmpty()) {
                LoadingState(modifier)
                PaginationTrigger(onLoadMore)
            } else {
                LazyColumn(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                ) {
                    items(
                        items = uiState.surveys,
                        key = { it.id },
                    ) { survey ->
                        SurveyCard(
                            survey = survey,
                            formattedClosingDate = formatClosingDate(survey.expiresAt),
                            onClick = { onSurveyClick(survey.id) },
                        )
                    }
                    when {
                        uiState.isLoadingMore -> item(
                            key = PAGINATION_LOADING_KEY,
                        ) {
                            PageLoadingIndicator()
                        }
                        uiState.loadMoreError != null -> item(
                            key = PAGINATION_ERROR_KEY,
                        ) {
                            PageLoadError(
                                message = stringResource(
                                    uiState.loadMoreError.messageResource
                                ),
                                onRetry = onLoadMore,
                            )
                        }
                        uiState.canLoadMore -> item(
                            key = PAGINATION_TRIGGER_KEY,
                        ) {
                            PaginationTrigger(onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginationTrigger(onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
    )
    LaunchedEffect(Unit) {
        onLoadMore()
    }
}

@Composable
private fun PageLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PageLoadError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.home_retry_loading_more))
        }
    }
}

@Composable
private fun SurveyCard(
    survey: Survey,
    formattedClosingDate: String,
    onClick: () -> Unit,
) {
    DVoteCard(
        modifier = Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        Text(
            text = survey.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = survey.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_creator, survey.creatorName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(
                        R.string.home_closes,
                        formattedClosingDate,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.home_results_after_close),
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 6.dp,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private const val PAGINATION_LOADING_KEY = "pagination-loading"
private const val PAGINATION_ERROR_KEY = "pagination-error"
private const val PAGINATION_TRIGGER_KEY = "pagination-trigger"
