package com.dvote.ui.main.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dvote.R
import com.dvote.data.model.SurveyDataItem

@Composable
fun SurveyViewScreen(
    modifier: Modifier = Modifier,
    viewModel: VoteSurveyViewModel,
) {

    val selected = viewModel.selectedOptions.collectAsState()

    val uiState = viewModel.uiState.collectAsState().value

    when (uiState) {
        is SurveyViewUiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SurveyViewUiState.Error -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text((uiState.message))
            }
        }

        is SurveyViewUiState.Success -> {
            SurveyContent(
                survey = uiState.survey,
                selectedOptions = selected.value,
                onOptionClick = viewModel::onOptionToggled,
                onSubmitVote = viewModel::submitVote,
                modifier = modifier
            )
        }
    }

//    if (false) {
//        AlertDialog(
//            onDismissRequest = {  },
//            title = { Text(stringResource(R.string.vote_submitted_title)) },
//            text = { Text(stringResource(R.string.vote_submitted_message)) },
//            confirmButton = {
//                TextButton(onClick = { }) {
//                    Text(stringResource(R.string.ok))
//                }
//            }
//        )
//    }
//
//    if (true) {
//        AlertDialog(
//            onDismissRequest = {  },
//            title = { Text(stringResource(R.string.already_voted_title)) },
//            text = { Text(stringResource(R.string.already_voted_message)) },
//            confirmButton = {
//                TextButton(onClick = {  }) {
//                    Text(stringResource(R.string.ok))
//                }
//            }
//        )
//    }


}

@Composable
fun SurveyContent(
    survey: SurveyDataItem,
    selectedOptions: Set<String>,
    onOptionClick: (String) -> Unit,
    onSubmitVote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = Color(0xFFFFF2E0))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            survey.title,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            stringResource(R.string.survey_info, survey.creatorName, survey.createdAt),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            survey.description,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))
        Text(
            if (survey.multipleChoice)
                stringResource(R.string.text_select_one_or_more_options)
            else
                stringResource(R.string.text_select_one_option),
            style = MaterialTheme.typography.titleMedium
        )

        survey.listOfCandidates.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOptionClick(option.userId) }
                    .padding(vertical = 4.dp)
            ) {
                if (survey.multipleChoice) {
                    Checkbox(
                        checked = selectedOptions.contains(option.userId),
                        onCheckedChange = { onOptionClick(option.userId) }
                    )
                } else {
                    RadioButton(
                        selected = selectedOptions.contains(option.userId),
                        onClick = { onOptionClick(option.userId) }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    option.name,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        if (survey.isActive) {
            Button(
                onClick = onSubmitVote,
                enabled = selectedOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.text_submit_vote),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            Text(
                "This survey is closed",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
