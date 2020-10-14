package com.squareup.workflow1.ui

/**
 * Represents an active view ([top]), and a set of previously visited
 * views to which we may return ([backStack]). By rendering the entire
 * history we allow the UI to do things like maintain cached view state,
 * implement synchronous drag-back gestures without hitting the workflow, etc.
 *
 * Effectively a list that can never be empty.
 *
 * @param bottom the bottom-most entry in the stack
 * @param rest the rest of the stack, empty by default
 */
@WorkflowUiExperimentalApi
class BackStackViewRendering<StackedT : ViewRendering>(
  bottom: StackedT,
  rest: List<StackedT>
): ViewRendering {
  /**
   * Creates a view with elements listed from the [bottom] to the top.
   */
  constructor(
    bottom: StackedT,
    vararg rest: StackedT
  ) : this(bottom, rest.toList())

  val frames: List<StackedT> = listOf(bottom) + rest

  /**
   * The active view.
   */
  val top: StackedT = frames.last()

  /**
   * Screens to which we may return.
   */
  val backStack: List<StackedT> = frames.subList(0, frames.size - 1)

  operator fun get(index: Int): StackedT = frames[index]

  operator fun plus(other: BackStackViewRendering<StackedT>?): BackStackViewRendering<StackedT> {
    return if (other == null) this
    else BackStackViewRendering(frames[0], frames.subList(1, frames.size) + other.frames)
  }

  fun <R : ViewRendering> map(transform: (StackedT) -> R): BackStackViewRendering<R> {
    return frames.map(transform)
        .toBackStackScreen()
  }

  fun <R : ViewRendering> mapIndexed(transform: (index: Int, StackedT) -> R): BackStackViewRendering<R> {
    return frames.mapIndexed(transform)
        .toBackStackScreen()
  }

  override fun equals(other: Any?): Boolean {
    return (other as? BackStackViewRendering<*>)?.frames == frames
  }

  override fun hashCode(): Int {
    return frames.hashCode()
  }

  override fun toString(): String {
    return "${this::class.java.simpleName}($frames)"
  }
}

@WorkflowUiExperimentalApi
fun <T : ViewRendering> List<T>.toBackStackScreenOrNull(): BackStackViewRendering<T>? = when {
  isEmpty() -> null
  else -> toBackStackScreen()
}

@WorkflowUiExperimentalApi
fun <T : ViewRendering> List<T>.toBackStackScreen(): BackStackViewRendering<T> {
  require(isNotEmpty())
  return BackStackViewRendering(first(), subList(1, size))
}
