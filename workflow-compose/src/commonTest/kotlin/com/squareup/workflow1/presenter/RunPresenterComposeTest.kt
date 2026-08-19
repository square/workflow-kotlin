package com.squareup.workflow1.presenter

import androidx.annotation.RestrictTo
import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@RestrictTo(RestrictTo.Scope.TESTS)
fun runPresenterComposeTest(testBody: TestScope.() -> Unit): TestResult {
  val clock = BroadcastFrameClock()
  return runTest(clock, testBody = testBody)
}
