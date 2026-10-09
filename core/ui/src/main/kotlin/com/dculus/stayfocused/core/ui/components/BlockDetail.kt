package com.dculus.stayfocused.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.dialLabel
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The grey line under a block's name, e.g. "10m on, 30m off · Instagram, YouTube" (prototype `desc`). */
@Composable
fun blockDetail(
    b: Block,
    appLabels: List<String>,
): String {
    val apps =
        when (appLabels.size) {
            0 -> stringResource(R.string.sf_block_detail_apps_none)
            1, 2 -> appLabels.joinToString(", ")
            else -> stringResource(R.string.sf_block_detail_apps_many, appLabels.size)
        }
    return when (b.type) {
        BlockType.CYCLE -> {
            stringResource(R.string.sf_block_detail_cycle, dialLabel(b.useMins ?: 0), dialLabel(b.restMins ?: 0), apps)
        }

        BlockType.LIMIT -> {
            stringResource(
                if (b.period ==
                    LimitPeriod.HOURLY
                ) {
                    R.string.sf_block_detail_limit_hourly
                } else {
                    R.string.sf_block_detail_limit_daily
                },
                durationLabel(b.limitMins ?: 0),
                apps,
            )
        }

        BlockType.SCHEDULE -> {
            stringResource(R.string.sf_block_detail_schedule, daysLabel(b.days), b.range?.format().orEmpty(), apps)
        }

        BlockType.NOW -> {
            stringResource(R.string.sf_block_detail_now, durationLabel(b.durationMins ?: 0), apps)
        }
    }
}

@Composable
private fun daysLabel(days: DaysOfWeek): String =
    when {
        days == DaysOfWeek.ALL -> {
            stringResource(R.string.sf_block_days_every_day)
        }

        days == DaysOfWeek.WEEKDAYS -> {
            stringResource(R.string.sf_block_days_weekdays)
        }

        days == DaysOfWeek.WEEKENDS -> {
            stringResource(R.string.sf_block_days_weekends)
        }

        days.isEmpty -> {
            stringResource(R.string.sf_block_days_none)
        }

        else -> {
            DayOfWeek.entries
                .filter {
                    it in days
                }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
    }
