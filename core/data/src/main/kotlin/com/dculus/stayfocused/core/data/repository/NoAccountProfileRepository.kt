package com.dculus.stayfocused.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Placeholder until accounts (M6) provide the real profile: nobody is ever signed in. */
internal class NoAccountProfileRepository
    @Inject
    constructor() : AccountProfileRepository {
        override fun observeFirstName(): Flow<String?> = flowOf(null)
    }
