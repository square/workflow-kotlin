package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.Compatible.Companion.keyFor

/**
 * A rendering that wraps another that is actually the interesting
 * bit (read: the visible bit), particularly from a logging or testing
 * point of view.
 *
 * This is the easiest way to customize behavior of the [unwrap] function.
 */
public interface Unwrappable {
  /** Topmost wrapped content, or `this` if empty. */
  public val unwrapped: Any
}

/**
 * Handy for logging and testing, extracts the "topmost" bit from a receiving
 * workflow rendering, honoring [Unwrappable] if applicable.
 */
public tailrec fun Any.unwrap(): Any {
  if (this !is Unwrappable) return this
  return unwrapped.unwrap()
}

/**
 * A rendering that can be decomposed to a [sequence][asSequence] of others.
 */
public interface Composite<out T> : Unwrappable {
  public fun asSequence(): Sequence<T>

  public override val unwrapped: Any get() = asSequence().lastOrNull() ?: this
}

/**
 * A structured [Composite] rendering comprised of a set of other
 * renderings of a [specific type][C] of a particular [category][CategoryT],
 * and whose contents can be transformed by [map].
 *
 * Why two parameter types? The separate [CategoryT] type allows implementations
 * and sub-interfaces to constrain the types that [map] is allowed to
 * transform [C] to. E.g., it allows `BunchOfScreens<S: Screen>` to declare
 * that [map] is only able to transform `S` to other types of `Screen`.
 *
 * @param CategoryT the invariant base type of the contents of such a container,
 * usually [Screen] or [Overlay][com.squareup.workflow1.ui.navigation.Overlay].
 * It is common for the [Container] itself to implement [CategoryT], but that is
 * not a requirement. E.g., [ScreenOverlay][com.squareup.workflow1.ui.navigation.ScreenOverlay]
 * is an [Overlay][com.squareup.workflow1.ui.navigation.Overlay], but it
 * wraps a [Screen].
 *
 * @param C the specific subtype of [CategoryT] collected by this [Container].
 */
public interface Container<CategoryT, out C : CategoryT> : Composite<C> {
  /**
   * Returns a [Container] with the [transform]ed contents of the receiver.
   * It is expected that an implementation will take advantage of covariance
   * to declare its own type as the return type, rather than plain old [Container].
   * This requirement is not enforced because recursive generics are a fussy nuisance.
   *
   * For example, suppose we want to create `LoggingScreen`, one that wraps any
   * other screen to add some logging calls. Its implementation of this method
   * would be expected to have a return type of `LoggingScreen` rather than `Container`:
   *
   *    override fun <D : Screen> map(transform: (C) -> D): LoggingScreen<D> =
   *      LoggingScreen(transform(content))
   *
   * By requiring all [Container] types to implement [map], we ensure that their
   * contents can be repackaged in interesting ways, e.g.:
   *
   *    val childBackStackScreen = renderChild(childWorkflow) { ... }
   *    val loggingBackStackScreen = childBackStackScreen.map { LoggingScreen(it) }
   */
  public fun <D : CategoryT> map(transform: (C) -> D): Container<CategoryT, D>
}

/**
 * A [Container] rendering that wraps exactly one other rendering, its [content]. These are
 * typically used to "add value" to the [content], e.g. an
 * [EnvironmentScreen][com.squareup.workflow1.ui.EnvironmentScreen] that allows
 * changes to be made to the [ViewEnvironment].
 *
 * Usually a [Wrapper] is [Compatible] only with others that are of the same type
 * and which are holding [Compatible] [content]. In aid of that, this interface extends
 * [Compatible] and provides a convenient default implementation of [compatibilityKey].
 */
public interface Wrapper<BaseT : Any, out C : BaseT> : Container<BaseT, C>, Compatible {
  public val content: C

  /**
   * Default implementation makes this [Wrapper] compatible with others of the same type,
   * and which wrap compatible [content].
   */
  public override val compatibilityKey: String
    get() = keyFor(content, this::class.simpleName ?: "Wrapper")

  public override fun asSequence(): Sequence<C> = sequenceOf(content)

  public override fun <D : BaseT> map(
    transform: (C) -> D
  ): Wrapper<BaseT, D>
}
