package com.squareup.workflow1.ui.internal.test

import android.os.SystemClock
import leakcanary.AppWatcher
import leakcanary.KeyedWeakReference.Companion.heapDumpUptimeMillis
import leakcanary.LeakAssertions
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Forked from Leakcanary until https://github.com/square/leakcanary/issues/2297 is fixed.
 *
 * [TestRule] that invokes [LeakAssertions.assertNoLeaks] after the test
 * successfully evaluates. Pay attention to where you set up this rule in the
 * rule chain as you might detect different leaks (e.g. around vs wrapped by the
 * activity rule). It's also possible to use this rule several times in a rule
 * chain.
 */
public class DetectLeaksAfterTestSuccess(
  private val tag: String = DetectLeaksAfterTestSuccess::class.java.simpleName
) : TestRule {
  override fun apply(
    base: Statement,
    description: Description
  ): Statement {
    return object : Statement() {
      override fun evaluate() {
        // If the test fails, evaluate() will throw and we won't run the analysis (which is good).
        var heapDumpUptimeMillis = 0L
        try {
          base.evaluate()
          heapDumpUptimeMillis = SystemClock.uptimeMillis()
          LeakAssertions.assertNoLeaks(tag)
        } finally {
          // Otherwise upstream test failures will be reported as leaks.
          // https://github.com/square/leakcanary/issues/2297
          AppWatcher.objectWatcher.clearObjectsWatchedBefore(heapDumpUptimeMillis)
        }
      }
    }
  }
}

/**
 * Invokes [LeakAssertions.assertNoLeaks] before performing the tear-down logic of the receiver
 * [TestRule], then again *after* the other rule's logic.
 *
 * https://github.com/square/workflow-kotlin/issues/657
 */
public fun TestRule.wrapInLeakCanary(): RuleChain = requireNotNull(
  RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(this)
    .around(DetectLeaksAfterTestSuccess())
)
