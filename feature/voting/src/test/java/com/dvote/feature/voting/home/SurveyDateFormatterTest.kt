package com.dvote.feature.voting.home

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SurveyDateFormatterTest {

    private val timestamp = Instant.parse("2026-04-05T12:00:00Z").toEpochMilli()

    @Test
    fun `formats English date using localized medium style`() {
        val formatted = surveyDateFormatter(
            locale = Locale.forLanguageTag("en-US"),
            zoneId = ZoneOffset.UTC,
        ).formatSurveyDate(timestamp)

        assertEquals("Apr 5, 2026", formatted)
    }

    @Test
    fun `formats Ukrainian date using localized medium style`() {
        val formatted = surveyDateFormatter(
            locale = Locale.forLanguageTag("uk-UA"),
            zoneId = ZoneOffset.UTC,
        ).formatSurveyDate(timestamp)

        assertEquals(
            "5 квіт. 2026 р.",
            formatted.replace('\u202F', ' '),
        )
    }
}
