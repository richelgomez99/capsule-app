package com.capsule.app.ai.model

import com.capsule.app.data.model.Intent

data class IntentClassification(
    val intent: Intent,
    val confidence: Float,
    val provenance: LlmProvenance
)
