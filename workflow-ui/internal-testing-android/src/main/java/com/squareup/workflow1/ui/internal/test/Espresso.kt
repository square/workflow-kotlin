package com.squareup.workflow1.ui.internal.test

import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import junit.framework.AssertionFailedError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.hamcrest.TypeSafeMatcher

/**
 * Fork of [Espresso.onView] that looks in all [Root]s, not just the one matched by the default
 * root matcher. The default root matcher will sometimes fail to find views in our dialogs.
 */
@WorkflowUiExperimentalApi
public fun inAnyView(viewMatcher: Matcher<View>): ViewInteraction {
  return Espresso.onView(viewMatcher).inRoot(any(Root::class.java))
}

/**
 * Fork of [Espresso.pressBack] that finds the root view of the focused window only, instead of
 * using the default root matcher. This is necessary because when we are showing dialogs,
 * the default matcher will sometimes match the wrong window, and the back press won't do
 * anything.
 */
@WorkflowUiExperimentalApi
public fun actuallyPressBack() {
  val rootHasFocusMatcher = object : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description) {
      description.appendText("has window focus")
    }

    override fun matchesSafely(item: Root): Boolean {
      return item.decorView.hasWindowFocus()
    }
  }

  Espresso.onView(allOf(isRoot(), isDisplayed()))
    .inRoot(rootHasFocusMatcher)
    .perform(ViewActions.pressBack())
}

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

public inline fun retryBlocking(
  clue: String = "",
  timeout_ms: Long = DEFAULT_RETRY_TIMEOUT,
  crossinline predicate: () -> Any
) {
  runBlocking {
    retry(clue, timeout_ms, predicate)
  }
}
