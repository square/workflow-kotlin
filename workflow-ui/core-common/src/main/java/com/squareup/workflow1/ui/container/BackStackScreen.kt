package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenContainer
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen.Companion
import com.squareup.workflow1.ui.container.BackStackScreen.Companion.fromList
import com.squareup.workflow1.ui.container.BackStackScreen.Companion.fromListOrNull

/**
 * Represents an active screen ([top]), and a set of previously visited screens to which we may
 * return ([backStack]). By rendering the entire history we allow the UI to do things like maintain
 * cached view state, implement drag-back gestures without waiting for the workflow, etc.
 *
 * Effectively a list that can never be empty.
 *
 * UI kits are expected to provide handling for this class by default.
 *
 * @see fromList
 * @see fromListOrNull
 */
@WorkflowUiExperimentalApi
public class BackStackScreen<out StackedT : Screen> internal constructor(
  public val frames: List<StackedT>
) : ScreenContainer<StackedT> {
  /**
   * Creates a screen with elements listed from the [bottom] to the top.
   */
  public constructor(
    bottom: StackedT,
    vararg rest: StackedT
  ) : this(listOf(bottom) + rest)

  @Deprecated(
    "Use fromList",
    ReplaceWith("BackStackScreen.fromList(listOf(bottom) + rest)")
  )
  public constructor(
    bottom: StackedT,
    rest: List<StackedT>
  ) : this(listOf(bottom) + rest)

  override fun asSequence(): Sequence<StackedT> = frames.asSequence()

  /**
   * The active screen.
   */
  public val top: StackedT = frames.last()

  /**
   * Screens to which we may return.
   */
  public val backStack: List<StackedT> = frames.subList(0, frames.size - 1)

  public operator fun get(index: Int): StackedT = frames[index]

  public override fun <StackedU : Screen> map(
    transform: (StackedT) -> StackedU
  ): BackStackScreen<StackedU> {
    return frames.map(transform).toBackStackScreen()
  }

  public fun <R : Screen> mapIndexed(transform: (index: Int, StackedT) -> R): BackStackScreen<R> {
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

  public companion object {
    /**
     * Builds a [BackStackScreen] from a non-empty list of [frames].
     *
     * @throws IllegalArgumentException is [frames] is empty
     */
    public fun <T : Screen> fromList(frames: List<T>): BackStackScreen<T> {
      require(frames.isNotEmpty()) {
        "A BackStackScreen must have at least one frame."
      }
      return BackStackScreen(frames)
    }

    /**
     * Builds a [BackStackScreen] from a list of [frames], or returns `null`
     * if [frames] is empty.
     */
    public fun <T : Screen> fromListOrNull(frames: List<T>): BackStackScreen<T>? {
      return when {
        frames.isEmpty() -> null
        else -> BackStackScreen(frames)
      }
    }
  }
}

/**
 * Returns a new [BackStackScreen] with the [BackStackScreen.frames] of [other] added
 * to those of the receiver. [other] is nullable for convenience when using with
 * [toBackStackScreenOrNull].
 */
@WorkflowUiExperimentalApi
public operator fun <T : Screen> BackStackScreen<T>.plus(
  other: BackStackScreen<T>?
): BackStackScreen<T> {
  return other?.let { BackStackScreen(frames + it.frames) } ?: this
}

@WorkflowUiExperimentalApi
public fun <T : Screen> List<T>.toBackStackScreenOrNull(): BackStackScreen<T>? =
  fromListOrNull(this)

@WorkflowUiExperimentalApi
public fun <T : Screen> List<T>.toBackStackScreen(): BackStackScreen<T> =
  Companion.fromList(this)
