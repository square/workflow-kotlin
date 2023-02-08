package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.Compatible.Companion.keyFor

/**
 * A model type comprised of a set of other models.
 *
 * Why two parameter types? The separate [BaseT] type allows implementations
 * and sub-interfaces to constrain the types that [map] is allowed to
 * transform [ContentT] to. E.g., it allows `FooWrapper<S: Screen>` to declare
 * that [map] is only able to transform `S` to other types of `Screen`.
 *
 * @param BaseT the invariant base type of the contents of such a container,
 * usually [Screen] or [Overlay][com.squareup.workflow1.ui.container.Overlay].
 * It is common for the [Container] itself to implement [BaseT], but that is
 * not a requirement. E.g., an `Overlay` might be container of `Screen`.
 *
 * @param ContentT the specific subtype of [BaseT] collected by this [Container].
 */
@WorkflowUiExperimentalApi
public interface Container<BaseT, out ContentT : BaseT> {
  public fun asSequence(): Sequence<ContentT>

  /**
   * Returns a [Container] with the [transform]ed contents of the receiver.
   * It is expected that an implementation will take advantage of covariance
   * to declare its own type as the return type, rather than plain old [Container].
   * This requirement is not enforced because recursive generics are a fussy nuisance.
   */
  public fun <U : BaseT> map(transform: (ContentT) -> U): Container<BaseT, U>
}

/**
 * A singleton [Container].
 */
@WorkflowUiExperimentalApi
public interface Wrapper<C : Any, T : C> : Container<C, T>, Compatible {
  public val content: T

  public override val compatibilityKey: String
    get() = keyFor(content, this::class.simpleName ?: "Wrapper")

  public override fun asSequence(): Sequence<T> = sequenceOf(content)

  public override fun <U : C> map(transform: (T) -> U): Wrapper<C, U>
}
