package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Container
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen.Companion
import com.squareup.workflow1.ui.navigation.BackStackScreen.Companion.fromList
import com.squareup.workflow1.ui.navigation.BackStackScreen.Companion.fromListOrNull

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
 *
 * @param name included in the [compatibilityKey] of this screen, for ease
 * of composition -- in classic Android views, view state persistence support
 * requires peer BackStackScreens to have a unique keys.
 *
 * @param frames the complete set of [StackedT] collected in this [BackStackScreen]:
 * [backStack] + [top]
 */
public class BackStackScreen<out StackedT : Screen> internal constructor(
  public val frames: List<StackedT>,
  public val name: String
) : Screen, Container<Screen, StackedT>, Compatible {

  /**
   * Creates a [BackStackScreen] with elements listed from the [bottom] to the top.
   */
  public constructor(
    bottom: StackedT,
    vararg rest: StackedT
  ) : this(listOf(bottom) + rest, "")

  /**
   * Creates a [named][name] [BackStackScreen] with elements listed from the [bottom] to the top.
   */
  public constructor(
    name: String,
    bottom: StackedT,
    vararg rest: StackedT
  ) : this(listOf(bottom) + rest, name)

  override val compatibilityKey: String = keyFor(this, name)

  override val unwrapped: Any get() = top

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
    return frames.map(transform).toBackStackScreen(name)
  }

  public fun <R : Screen> mapIndexed(transform: (index: Int, StackedT) -> R): BackStackScreen<R> {
    return frames.mapIndexed(transform)
      .toBackStackScreen(name)
  }

  public fun withName(name: String): BackStackScreen<StackedT> = BackStackScreen(frames, name)

  override fun toString(): String {
    return name.takeIf { it.isNotEmpty() }?.let { "BackStackScreen-$it($frames)" }
      ?: "BackStackScreen($frames)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BackStackScreen<*>

    return (frames == other.frames && name == other.name)
  }

  override fun hashCode(): Int {
    var result = frames.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  public companion object {
    /**
     * Builds a [BackStackScreen] from a non-empty list of [frames].
     *
     * @throws IllegalArgumentException is [frames] is empty
     */
    public fun <T : Screen> fromList(
      frames: List<T>,
      name: String = ""
    ): BackStackScreen<T> {
      require(frames.isNotEmpty()) {
        "A BackStackScreen must have at least one frame."
      }
      return BackStackScreen(frames, name)
    }

    /**
     * Builds a [BackStackScreen] from a list of [frames], or returns `null`
     * if [frames] is empty.
     */
    public fun <T : Screen> fromListOrNull(
      frames: List<T>,
      name: String = ""
    ): BackStackScreen<T>? {
      return when {
        frames.isEmpty() -> null
        else -> BackStackScreen(frames, name)
      }
    }
  }
}

/**
 * Returns a new [BackStackScreen] with the [BackStackScreen.frames] of [other] added
 * to those of the receiver. [other] is nullable for convenience when using with
 * [toBackStackScreenOrNull].
 */
public operator fun <T : Screen> BackStackScreen<T>.plus(
  other: BackStackScreen<T>?
): BackStackScreen<T> {
  return other?.let { BackStackScreen(frames + it.frames, this.name) } ?: this
}

public fun <T : Screen> List<T>.toBackStackScreenOrNull(name: String = ""): BackStackScreen<T>? =
  fromListOrNull(this, name)

public fun <T : Screen> List<T>.toBackStackScreen(name: String = ""): BackStackScreen<T> =
  Companion.fromList(this, name)
