package com.dvote.ui.main.navigation

import androidx.annotation.StringRes
import com.dvote.R

enum class TabDestinations(@StringRes val displayName: Int, val destination: MainDestinations) {
    SURVEYS_LIST(R.string.surveys_list, MainDestinations.SurveysList),
    SURVEY_HISTORY(R.string.survey_history, MainDestinations.SurveyHistory);

    companion object {
        val all = listOf(SURVEYS_LIST, SURVEY_HISTORY)
    }
}