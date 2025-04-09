package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.Compatible.Companion.keyFor

/**
 * A rendering type comprised of a set of other renderings.
 *
 * Why two parameter types? The separate [BaseT] type allows implementations
 * and sub-interfaces to constrain the types that [map] is allowed to
 * transform [C] to. E.g., it allows `FooWrapper<S: Screen>` to declare
 * that [map] is only able to transform `S` to other types of `Screen`.
 *
 * @param BaseT the invariant base type of the contents of such a container,
 * usually [Screen] or [Overlay][com.squareup.workflow1.ui.navigation.Overlay].
 * It is common for the [Container] itself to implement [BaseT], but that is
 * not a requirement. E.g., [ScreenOverlay][com.squareup.workflow1.ui.navigation.ScreenOverlay]
 * is an [Overlay][com.squareup.workflow1.ui.navigation.Overlay], but it
 * wraps a [Screen].
 *
 * @param C the specific subtype of [BaseT] collected by this [Container].
 */
public interface Container<BaseT, out C : BaseT> {
  public fun asSequence(): Sequence<C>

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
  public fun <D : BaseT> map(transform: (C) -> D): Container<BaseT, D>
}

/**
 * A [Container] rendering that wraps exactly one other rendering, its [content]. These are
 * typically used to "add value" to the [content], e.g. an
 * [EnvironmentScreen][com.squareup.workflow1.ui.EnvironmentScreen] that allows
 * changes to be made to the [ViewEnvironment].
 *
 * Usually a [Wrapper] is [Compatible] only with others of the same type with
 * [Compatible] [content]. In aid of that, this interface extends [Compatible] and
 * provides a convenient default implementation of [compatibilityKey].
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
