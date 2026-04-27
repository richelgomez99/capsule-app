package com.capsule.app.ai

/**
 * Spec 002 Phase 11 / T122 — embedding output for the cluster engine.
 *
 * Every embedding produced by [LlmProvider.embed] is stamped with the model
 * label that produced it (Principle IX) and its [dimensionality] so the
 * [com.capsule.app.cluster.SimilarityEngine] can refuse to compute cosine
 * across mismatched models / dimension counts (FR-038, FR-039).
 *
 * Hand-written `equals` / `hashCode` because [FloatArray]'s default identity
 * semantics would break unit-test equality and DAO de-dup logic.
 */
class EmbeddingResult(
    val vector: FloatArray,
    val modelLabel: String,
    val dimensionality: Int
) {
    init {
        require(vector.size == dimensionality) {
            "vector.size=${vector.size} != dimensionality=$dimensionality"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingResult) return false
        if (modelLabel != other.modelLabel) return false
        if (dimensionality != other.dimensionality) return false
        if (!vector.contentEquals(other.vector)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + modelLabel.hashCode()
        result = 31 * result + dimensionality
        return result
    }

    override fun toString(): String =
        "EmbeddingResult(modelLabel='$modelLabel', dimensionality=$dimensionality, vector=[${vector.size} floats])"
}
