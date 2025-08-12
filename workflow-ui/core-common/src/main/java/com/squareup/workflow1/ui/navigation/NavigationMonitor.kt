package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.unwrap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Reports navigation across a series of calls to [update], probably made
 * for each rendering posted by
 * [renderWorkflowIn][com.squareup.workflow1.renderWorkflowIn].
 *
 * Takes advantage of [unwrap()] and [Compatible.keyFor] to provide navigation
 * logging by reporting the top (read: last-most, inner-most) sub-rendering,
 * which conventionally is the one that is visible and accessible to the user.
 *
 * Reports each time the [Compatible.keyFor] the top is unequal to the previous one,
 * which conventionally indicates that a new view object will replace the previous one.
 */
public class NavigationMonitor(
  skipFirstScreen: Boolean = false,
  private val onNavigate: (Any) -> Unit = { println(Compatible.keyFor(it)) }
) {
  @Volatile
  private var lastKey: String? = if (skipFirstScreen) null else ""

  /**
   * Uses [unwrap] to find the topmost element of [rendering] and
   * reports it with [onNavigate] if [Compatible.keyFor] reveals that
   * it is of a different kind from the previous top.
   */
  public fun update(rendering: Any) {
    val unwrapped = rendering.unwrap()

    Compatible.keyFor(unwrapped).takeIf { it != lastKey }?.let { newKey ->
      if (lastKey != null) onNavigate(unwrapped)
      lastKey = newKey
    }
  }
}

/**
 * Creates a [NavigationMonitor] and [updates it][NavigationMonitor.update]
 * with [each element collected][Flow.onEach] by the receiving [Flow].
 *
 * Note that one of the best ways to use this is with an installed
 * [com.squareup.workflow1.tracing.WorkflowRuntimeMonitor] and then calling
 * [com.squareup.workflow1.tracing.RuntimeTraceContext.addRuntimeUpdate] with a
 * [com.squareup.workflow1.tracing.UiUpdateLogLine] in [onNavigate].
 */
public fun <T : Any> Flow<T>.reportNavigation(
  skipFirstScreen: Boolean = false,
  onNavigate: (Any) -> Unit = { println(it) }
): Flow<T> {
  val monitor = NavigationMonitor(skipFirstScreen, onNavigate)
  return onEach { monitor.update(it) }
}
