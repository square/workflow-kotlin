package com.squareup.workflow1.ui.internal.test

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import junit.framework.AssertionFailedError
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

public const val DEFAULT_RETRY_TIMEOUT: Long = 100L
public const val RETRY_POLLING_INTERVAL: Long = 16L

/**
 * Simple brute force retry for Espresso matchers when we use the onTimeout runtime method which
 * means that our IdlingDispatcher won't count the timeout delays.
 */
public suspend inline fun retry(
  clue: String = "",
  timeout_ms: Long = DEFAULT_RETRY_TIMEOUT,
  crossinline predicate: () -> Any
) {
  var exception: Throwable? = null

  withTimeoutOrNull(timeout_ms) {
    while (true) {
      try {
        predicate()
        return@withTimeoutOrNull
      } catch (e: NoMatchingViewException) {
        exception = NoMatchingViewException.Builder()
          .from(e)
          .withCause(AssertionError(clue))
          .build()
        delay(RETRY_POLLING_INTERVAL)
      } catch (e: PerformException) {
        exception = PerformException.Builder()
          .from(e)
          .withCause(AssertionError(clue))
          .build()
        delay(RETRY_POLLING_INTERVAL)
      } catch (e: AssertionFailedError) {
        exception = AssertionFailedError(clue + e.message)
        delay(RETRY_POLLING_INTERVAL)
      }
    }
  } ?: exception?.let {
    throw it
  }
}
