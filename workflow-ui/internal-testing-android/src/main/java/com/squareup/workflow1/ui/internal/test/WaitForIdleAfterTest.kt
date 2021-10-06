package com.squareup.workflow1.ui.internal.test

import androidx.test.espresso.Espresso
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Calls [Espresso.onIdle] after evaluation, allowing one last call
 * to `IdlingResourceRegistry.sync` to ensure any lingering entries are dropped.
 *
 * Used as the last rule in our Compose tests to keep Leakcanary sweet, as a workaround for
 * https://issuetracker.google.com/issues/202190483
 */
@WorkflowUiExperimentalApi
public val WaitForIdleAfterTest: TestRule = TestRule { _, _ ->
  object : Statement() {
    override fun evaluate() {
      Espresso.onIdle()
    }
  }
}
