package com.capsule.app.data.model

/**
 * Open-ended cluster taxonomy. v1 ships [RESEARCH_SESSION] only
 * (D-NARROW per spec 002 amendment). Adding a new cluster type does
 * NOT require a Room migration — extending the enum is a code change
 * only.
 *
 * D-AS-WRITTEN stretch types (TASK_CLUSTER, etc.) are gated on the
 * May 4 multimodal precision/recall measurement; see TODOS.md.
 */
enum class ClusterType {
    RESEARCH_SESSION
}
