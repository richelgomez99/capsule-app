package com.capsule.app.ai.model

data class DayHeaderResult(
    val text: String,
    val generationLocale: String,
    val provenance: LlmProvenance
)
