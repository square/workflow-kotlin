package com.squareup.workflow1

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * This file is copied from workflow-runtime:commonTest so our tests that test across the runtime
 * look consistent. We could have used a JUnit library like Jupiter, but didn't.
 *
 * This file is copied so as to avoid creating a workflow-core-testing module (for now).
 *
 * We do our best to tell you what the parameter was when the failure occured by wrapping
 * assertions from kotlin.test and injecting our own message.
 */
class ParameterizedTestRunner<P : Any> {

  var currentParam: P? = null

  fun runParametrizedTest(
    paramSource: Sequence<P>,
    before: () -> Unit = {},
    after: () -> Unit = {},
    test: ParameterizedTestRunner<P>.(param: P) -> Unit
  ) {
    paramSource.forEach {
      before()
      currentParam = it
      test(it)
      after()
    }
  }

  fun <T> assertEquals(expected: T, actual: T) {
    assertEquals(expected, actual, message = "Using: ${currentParam?.toString()}")
  }

  fun <T> assertEquals(expected: T, actual: T, originalMessage: String) {
    assertEquals(expected, actual, message = "$originalMessage; Using: ${currentParam?.toString()}")
  }

  fun assertTrue(statement: Boolean) {
    assertTrue(statement, message = "Using: ${currentParam?.toString()}")
  }

  fun assertFalse(statement: Boolean) {
    assertFalse(statement, message = "Using: ${currentParam?.toString()}")
  }

  inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    assertFailsWith<T>(message = "Using: ${currentParam?.toString()}", block)
  }

  fun <T : Any?> assertNotSame(illegal: T, actual: T) {
    assertNotSame(illegal, actual, message = "Using: ${currentParam?.toString()}")
  }

  fun <T : Any> assertNotNull(actual: T?) {
    assertNotNull(actual, message = "Using: ${currentParam?.toString()}")
  }

  fun assertNull(actual: Any?) {
    assertNull(actual, message = "Using: ${currentParam?.toString()}")
  }
}
