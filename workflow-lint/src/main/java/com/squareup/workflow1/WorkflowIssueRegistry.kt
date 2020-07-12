@file:Suppress("UnstableApiUsage")

package com.squareup.workflow1

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class WorkflowIssueRegistry : IssueRegistry() {
  override val issues: List<Issue>
    get() = WrongSnapshotUsageDetector.issues

  override val api: Int get() = CURRENT_API
}
