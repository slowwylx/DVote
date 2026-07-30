package com.dvote.feature.voting.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun surveyDateFormatter(
    locale: Locale,
    zoneId: ZoneId,
): DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .withZone(zoneId)

internal fun DateTimeFormatter.formatSurveyDate(timestamp: Long): String =
    format(Instant.ofEpochMilli(timestamp))
