package com.krishinirnay.core.data.model

/** Static, mock-only for Phase 1 — no real government API is wired up yet. */
data class GovtScheme(
    val id: String,
    val name: String,
    val benefit: String,
    val description: String,
    val eligibility: String,
)
