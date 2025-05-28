package com.dvote.ui.main.vote

import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        Text(survey.title)
        Text("By ${survey.createdBy} • created at ${survey.createdAt}")
        Spacer(Modifier.height(8.dp))
        Text(survey.description)

        Spacer(Modifier.height(16.dp))
        Text(
            if (survey.multipleChoice) "Select one or more options:"
            else "Select one option:",
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
                Text(option.name)
            }
        }

        Spacer(Modifier.height(24.dp))
        if (survey.isActive) {
            Button(
                onClick = onSubmitVote,
                enabled = selectedOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit Vote")
            }
        } else {
            Text("This survey is closed", color = Color.Gray)
        }
    }
}
