package com.dculus.stayfocused.core.data.repository

import com.dculus.stayfocused.core.model.LinkedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Placeholder until device linking (M7) provides the real repository: no phone is ever linked. */
internal class EmptyLinkedDevicesRepository
    @Inject
    constructor() : LinkedDevicesRepository {
        override fun observeAll(): Flow<List<LinkedDevice>> = flowOf(emptyList())
    }
