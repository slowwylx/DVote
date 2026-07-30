package com.dvote.feature.voting

import androidx.annotation.StringRes
import com.dvote.domain.repository.RepositoryFailure
import com.dvote.domain.usecase.CreateSurveyValidationFailure

enum class VotingUiError {
    SERVICE_UNAVAILABLE,
    INVALID_SERVICE_DATA,
    REQUEST_REJECTED,
    CREATE_SURVEY_FAILED,
    CREATE_TITLE_LENGTH,
    CREATE_DESCRIPTION_LENGTH,
    CREATE_OPTION_COUNT,
    CREATE_OPTION_LENGTH,
    CREATE_EXPIRATION,
    LOAD_SURVEYS_FAILED,
    LOAD_PROFILE_FAILED,
    SIGN_OUT_FAILED,
    SURVEY_NOT_FOUND,
    LOAD_SURVEY_FAILED,
    LOAD_FINAL_RESULT_FAILED,
    FINAL_RESULT_UNAVAILABLE,
    CHECK_VOTE_STATUS_FAILED,
    PREPARE_VOTE_FAILED,
    SUBMIT_VOTE_FAILED,
}

internal fun Throwable.toVotingUiError(
    fallback: VotingUiError,
): VotingUiError {
    if (this is CreateSurveyValidationFailure) {
        return when (reason) {
            CreateSurveyValidationFailure.Reason.TITLE_LENGTH ->
                VotingUiError.CREATE_TITLE_LENGTH
            CreateSurveyValidationFailure.Reason.DESCRIPTION_LENGTH ->
                VotingUiError.CREATE_DESCRIPTION_LENGTH
            CreateSurveyValidationFailure.Reason.OPTION_COUNT ->
                VotingUiError.CREATE_OPTION_COUNT
            CreateSurveyValidationFailure.Reason.OPTION_LENGTH ->
                VotingUiError.CREATE_OPTION_LENGTH
            CreateSurveyValidationFailure.Reason.EXPIRATION ->
                VotingUiError.CREATE_EXPIRATION
        }
    }

    return when ((this as? RepositoryFailure)?.kind) {
        RepositoryFailure.Kind.RETRYABLE -> VotingUiError.SERVICE_UNAVAILABLE
        RepositoryFailure.Kind.INVALID_DATA -> VotingUiError.INVALID_SERVICE_DATA
        RepositoryFailure.Kind.REJECTED -> VotingUiError.REQUEST_REJECTED
        null -> fallback
    }
}

@get:StringRes
val VotingUiError.messageResource: Int
    get() = when (this) {
        VotingUiError.SERVICE_UNAVAILABLE -> R.string.error_service_unavailable
        VotingUiError.INVALID_SERVICE_DATA -> R.string.error_invalid_service_data
        VotingUiError.REQUEST_REJECTED -> R.string.error_request_rejected
        VotingUiError.CREATE_SURVEY_FAILED -> R.string.error_create_survey
        VotingUiError.CREATE_TITLE_LENGTH -> R.string.error_create_title_length
        VotingUiError.CREATE_DESCRIPTION_LENGTH ->
            R.string.error_create_description_length
        VotingUiError.CREATE_OPTION_COUNT -> R.string.error_create_option_count
        VotingUiError.CREATE_OPTION_LENGTH -> R.string.error_create_option_length
        VotingUiError.CREATE_EXPIRATION -> R.string.error_create_expiration
        VotingUiError.LOAD_SURVEYS_FAILED -> R.string.error_load_surveys
        VotingUiError.LOAD_PROFILE_FAILED -> R.string.error_load_profile
        VotingUiError.SIGN_OUT_FAILED -> R.string.error_sign_out
        VotingUiError.SURVEY_NOT_FOUND -> R.string.vote_survey_not_found
        VotingUiError.LOAD_SURVEY_FAILED -> R.string.error_load_survey
        VotingUiError.LOAD_FINAL_RESULT_FAILED -> R.string.error_load_final_result
        VotingUiError.FINAL_RESULT_UNAVAILABLE -> R.string.vote_result_unavailable
        VotingUiError.CHECK_VOTE_STATUS_FAILED -> R.string.error_check_vote_status
        VotingUiError.PREPARE_VOTE_FAILED -> R.string.error_prepare_vote
        VotingUiError.SUBMIT_VOTE_FAILED -> R.string.error_submit_vote
    }
