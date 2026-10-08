package com.dculus.stayfocused.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.BreakRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel
    @Inject
    constructor(
        private val breaks: BreakRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * The break card. The break itself lives in DataStore, so after process death the countdown simply
         * resumes from the stored end time.
         */
        internal val breakUi: StateFlow<BreakUi> =
            breaks
                .observe()
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(BreakUi.Idle)
                    } else {
                        val total = Duration.between(session.startedAt, session.endsAt).toMillis()
                        countdown(session.endsAt.toEpochMilli(), total)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BreakUi.Idle)

        private fun countdown(
            endsAtMillis: Long,
            totalMs: Long,
        ): Flow<BreakUi> =
            flow {
                while (true) {
                    val remaining = endsAtMillis - clock.millis()
                    if (remaining <= 0L) {
                        emit(BreakUi.Idle)
                        return@flow
                    }
                    emit(BreakUi.Active(remaining, totalMs))
                    delay(millisToNextTick(remaining))
                }
            }

        fun startBreak(mins: Int) {
            viewModelScope.launch { breaks.start(mins) }
        }

        fun endBreak() {
            viewModelScope.launch { breaks.end() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
