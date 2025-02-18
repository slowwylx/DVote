package com.dvote.ui.main.surveys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SurveysListScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFFFCECD8))
    ) {
        Text(text = "Surveys List Screen")
    }
}