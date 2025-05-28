package com.dvote.ui.main.create_survey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dvote.extensions.collectAsMutableState
import com.dvote.ui.theme.Purple40
import com.dvote.ui.theme.Purple80
import com.dvote.ui.theme.PurpleGrey80

@Composable
fun CreateSurveyScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateSurveyViewModel = hiltViewModel(),
) {
    val (title, titleChanged) = viewModel.surveyTitle.collectAsMutableState()
    val (description, descriptionChanged) = viewModel.surveyDescription.collectAsMutableState()
    val (candidates, candidatesChanged) = viewModel.candidates.collectAsMutableState()

    val (isMultipleChoice, isMultipleChoiceChanged) = viewModel.isMultipleChoice.collectAsMutableState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF2E0))
            .padding(16.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Survey Title")
            StyledTextField(
                value = title,
                onValueChange = titleChanged,
                placeholder = "Enter survey title",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minHeight = 56.dp
            )

            Text("Survey Description")
            StyledTextField(
                value = description,
                onValueChange = descriptionChanged,
                placeholder = "Enter survey description",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minHeight = 100.dp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Candidates")
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .background(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp))
                        .clip(shape = RoundedCornerShape(8.dp))
                        .clickable(onClick = { viewModel.addCandidate() })
                        .padding(4.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Default.Add,
                        contentDescription = "Clear Candidates",
                        tint = Purple40
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Multiple Choice?",
                    textAlign = TextAlign.Start,
                )
                Spacer(modifier = Modifier.weight(1f))
                Checkbox(
                    modifier = Modifier.size(24.dp),
                    checked = isMultipleChoice,
                    onCheckedChange = isMultipleChoiceChanged,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedColor = PurpleGrey80
                    )
                )
            }


            candidates.forEachIndexed { index, candidateMap ->
                CandidateInputField(
                    value = candidateMap["name"].orEmpty(),
                    onValueChange = { new ->
                        viewModel.updateCandidate(index, new)
                    },
                    onRemove = {
                        viewModel.removeCandidate(index)
                    }
                )
            }

        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = { viewModel.createSurvey() },
        ) {
            Text(text = "Create Survey", color = Purple40)
        }
    }


}

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minHeight: Dp? = null
) {
    Box(
        modifier = modifier
            .background(color = Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color.Gray)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.copy(color = Color.Black),
            modifier = modifier.then(
                if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier
            ),
            decorationBox = { inner ->
                inner()
            }
        )
    }
}

@Composable
fun CandidateInputField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyledTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            placeholder = "Candidate name",
            singleLine = true
        )

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
            )
        }
    }
}