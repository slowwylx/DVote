package com.dvote.ui.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dvote.ui.main.navigation.TabDestinations
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {

    val surveyList = viewModel.surveyListFlow.collectAsState().value

    val tabs = TabDestinations.all
    val startDestination = TabDestinations.SURVEYS_LIST
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }

    val pagerState = rememberPagerState(
        initialPage = selectedDestination,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    fun onTabClick(index: Int) {
        selectedDestination = index
        coroutineScope.launch {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = selectedDestination,
            containerColor = Color.White
        ) {
            tabs.forEachIndexed { index, item ->
                Tab(
                    text = { Text(text = stringResource(item.displayName)) },
                    selected = selectedDestination == index,
                    onClick = { onTabClick(index) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (TabDestinations.entries[page]) {
                TabDestinations.SURVEYS_LIST -> SurveysListScreen(
                    surveyList = surveyList,
                    onSurveyClick = { surveyId ->

                    }
                )
                TabDestinations.SURVEY_HISTORY -> SurveyHistoryScreen()
            }
        }
    }
}

@Composable
fun SurveyListItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    surveyItemUi: SurveyItemUi
) {

    val iconTint = if (!surveyItemUi.isProtected) Color.Red else Color(0xFF80F58B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = CenterVertically
        ) {
            Text(text = surveyItemUi.title, textAlign = TextAlign.Start)
            if (surveyItemUi.isProtected) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Default.Lock,
                    tint = iconTint,
                    contentDescription = null,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = surveyItemUi.description, textAlign = TextAlign.Start)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = CenterVertically
        ) {
            Text(text = surveyItemUi.createdBy, textAlign = TextAlign.Start)
            Text(text = surveyItemUi.expirationDate, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun SurveysListScreen(
    modifier: Modifier = Modifier,
    onSurveyClick: (String) -> Unit = {},
    surveyList: List<SurveyItemUi>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF2E0))
    ) {
        if (surveyList.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(surveyList) { index, item ->
                    key(item.id){
                        val survey = surveyList[index]
                        SurveyListItem(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSurveyClick(survey.id) },
                            surveyItemUi = survey
                        )
                    }
                }
            }
        }else{
            Text(
                text = "No surveys available",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SurveyHistoryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF2E0))
    ) {
        Text(text = "Survey History Screen")
    }
}