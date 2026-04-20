package com.capsule.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class OrbitIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        NoHttpClientOutsideNetDetector.ISSUE
    )
    override val api: Int = CURRENT_API
    override val vendor: Vendor = Vendor(
        vendorName = "Capsule/Orbit",
        identifier = "com.capsule.lint",
        feedbackUrl = "https://github.com/nicholasrichel/Capsule-GitHub/issues"
    )
}
