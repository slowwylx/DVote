package com.dvote.ui.main.navigation

import androidx.annotation.StringRes
import com.dvote.R

enum class TabDestinations(@StringRes val displayName: Int) {
    SURVEYS_LIST(R.string.surveys_list),
    SURVEY_HISTORY(R.string.survey_history);

    companion object {
        val all = listOf(SURVEYS_LIST, SURVEY_HISTORY)
    }
}