package com.krishinirnay.feature.schemes

import androidx.lifecycle.ViewModel
import com.krishinirnay.core.data.model.GovtScheme
import com.krishinirnay.core.data.repository.SchemesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SchemesViewModel @Inject constructor(
    schemesRepository: SchemesRepository,
) : ViewModel() {
    val schemes: StateFlow<List<GovtScheme>> = schemesRepository.schemes
}
