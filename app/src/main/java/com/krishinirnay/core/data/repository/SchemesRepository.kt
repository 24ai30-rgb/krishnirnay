package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.GovtScheme
import kotlinx.coroutines.flow.StateFlow

interface SchemesRepository {
    val schemes: StateFlow<List<GovtScheme>>
}
