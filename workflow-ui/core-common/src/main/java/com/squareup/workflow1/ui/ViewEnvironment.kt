package com.squareup.workflow1.ui

import kotlin.reflect.KClass

/**
 * Immutable, append-only map of values that a parent view can pass down to
 * its children. Allows containers to give descendants information about
 * the context in which they're drawing.
 *
 * Calling [Screen.withEnvironment][com.squareup.workflow1.ui.container.withEnvironment]
 * is the easiest way to customize its environment.
 */
@WorkflowUiExperimentalApi
public class ViewEnvironment(
  public val map: Map<ViewEnvironmentKey<*>, Any> = emptyMap()
) {
  @Suppress("UNCHECKED_CAST")
  public operator fun <T : Any> get(key: ViewEnvironmentKey<T>): T = map[key] as? T ?: key.default

  public operator fun <T : Any> plus(pair: Pair<ViewEnvironmentKey<T>, T>): ViewEnvironment =
    ViewEnvironment(map + pair)

  public operator fun plus(other: ViewEnvironment): ViewEnvironment =
    ViewEnvironment(map + other.map)

  override fun toString(): String = "ViewEnvironment($map)"

  override fun equals(other: Any?): Boolean =
    (other as? ViewEnvironment)?.let { it.map == map } ?: false

  override fun hashCode(): Int = map.hashCode()
}

/**
 * Defines a value that can be provided by a [ViewEnvironment] map, specifying its [type]
 * and [default] value.
 */
@WorkflowUiExperimentalApi
public abstract class ViewEnvironmentKey<T : Any>(
  private val type: KClass<T>
) {
  public abstract val default: T

  final override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other != null && this::class != other::class -> false
    else -> type == (other as ViewEnvironmentKey<*>).type
  }

  final override fun hashCode(): Int = type.hashCode()

  override fun toString(): String {
    return "ViewEnvironmentKey($type)-${super.toString()}"
  }
}

/**
 * Combines the receiving [ViewEnvironment] with [other], taking care to merge
 * their [ViewRegistry] entries. Duplicate values in [other] replace those
 * in the receiver.
 */
@WorkflowUiExperimentalApi
public fun ViewEnvironment.updateFrom(other: ViewEnvironment): ViewEnvironment {
  if (other.map.isEmpty()) return this

  val myReg = this[ViewRegistry]
  val yourReg = other[ViewRegistry]

  val union = (myReg.keys + yourReg.keys).asSequence()
    .map { yourReg.getEntryFor(it) ?: myReg.getEntryFor(it)!! }
    .toList()
    .toTypedArray()

  val unionRegistry = ViewRegistry(*union)
  return this + other + (ViewRegistry to unionRegistry)
}
