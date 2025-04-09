package com.squareup.workflow1.ui.internal.test

import androidx.test.espresso.Espresso
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Calls [Espresso.onIdle] after evaluation, allowing one last call
 * to `IdlingResourceRegistry.sync` to ensure any lingering entries are dropped.
 *
 * Used as the last rule in our Compose tests to keep Leakcanary sweet, as a workaround for
 * https://issuetracker.google.com/issues/202190483
 */
public object IdleAfterTestRule : TestRule {
  override fun apply(
    base: Statement,
    description: Description
  ): Statement {
    var statementOrNull: Statement? = base
    return object : Statement() {
      override fun evaluate() {
        statementOrNull?.evaluate()
        statementOrNull = null
        Espresso.onIdle()
      }
    }
  }
}
