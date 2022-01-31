@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.backstack

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.asScreen
import com.squareup.workflow1.ui.container.BackStackScreen as NewBackStackScreen

@WorkflowUiExperimentalApi
@Deprecated("Use com.squareup.workflow1.ui.container.BackStackScreen")
public class BackStackScreen<StackedT : Any>(
  bottom: StackedT,
  rest: List<StackedT>
) {
  /**
   * Creates a screen with elements listed from the [bottom] to the top.
   */
  public constructor(
    bottom: StackedT,
    vararg rest: StackedT
  ) : this(bottom, rest.toList())

  public val frames: List<StackedT> = listOf(bottom) + rest

  /**
   * The active screen.
   */
  public val top: StackedT = frames.last()

  /**
   * Screens to which we may return.
   */
  public val backStack: List<StackedT> = frames.subList(0, frames.size - 1)

  public operator fun get(index: Int): StackedT = frames[index]

  public operator fun plus(other: BackStackScreen<StackedT>?): BackStackScreen<StackedT> {
    return if (other == null) this
    else BackStackScreen(frames[0], frames.subList(1, frames.size) + other.frames)
  }

  public fun <R : Any> map(transform: (StackedT) -> R): BackStackScreen<R> {
    return frames.map(transform)
      .toBackStackScreen()
  }

  public fun <R : Any> mapIndexed(transform: (index: Int, StackedT) -> R): BackStackScreen<R> {
    return frames.mapIndexed(transform)
      .toBackStackScreen()
  }

  override fun equals(other: Any?): Boolean {
    return (other as? BackStackScreen<*>)?.frames == frames
  }

  override fun hashCode(): Int {
    return frames.hashCode()
  }

  override fun toString(): String {
    return "${this::class.java.simpleName}($frames)"
  }
}

@WorkflowUiExperimentalApi
public fun <T : Any> List<T>.toBackStackScreenOrNull(): BackStackScreen<T>? = when {
  isEmpty() -> null
  else -> toBackStackScreen()
}

@WorkflowUiExperimentalApi
public fun <T : Any> List<T>.toBackStackScreen(): BackStackScreen<T> {
  require(isNotEmpty())
  return BackStackScreen(first(), subList(1, size))
}

@WorkflowUiExperimentalApi
public fun BackStackScreen<*>.asNonLegacy(): NewBackStackScreen<Screen> {
  return NewBackStackScreen(
    bottom = asScreen(frames.first()),
    rest = when (frames.size) {
      1 -> emptyList()
      else -> frames.takeLast(frames.count() - 1).map { asScreen(it) }
    }
  )
}
