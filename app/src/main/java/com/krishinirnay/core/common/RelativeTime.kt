package com.krishinirnay.core.common

import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import java.time.Duration
import java.time.Instant

fun Instant.toRelativeLabel(strings: AppStrings = EnglishStrings, now: Instant = Instant.now()): String {
    val seconds = Duration.between(this, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 10 -> strings.timeJustNow
        seconds < 60 -> String.format(strings.timeSecondsAgoTemplate, seconds)
        seconds < 3600 -> String.format(strings.timeMinutesAgoTemplate, seconds / 60)
        seconds < 86_400 -> String.format(strings.timeHoursAgoTemplate, seconds / 3600)
        else -> String.format(strings.timeDaysAgoTemplate, seconds / 86_400)
    }
}
