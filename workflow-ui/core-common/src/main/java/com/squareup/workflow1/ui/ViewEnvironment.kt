package com.squareup.workflow1.ui

import kotlin.reflect.KClass

/**
 * Immutable map of values that a parent view can pass down to
 * its children. Allows containers to give descendants information about
 * the context in which they're drawing.
 *
 * Calling [Screen.withEnvironment][com.squareup.workflow1.ui.container.withEnvironment]
 * on a [Screen] is the easiest way to customize its environment before rendering it.
 */
@WorkflowUiExperimentalApi
public class ViewEnvironment
@Deprecated(
  "To eliminate runtime errors this constructor will become private. " +
    "Use ViewEnvironment.EMPTY and ViewEnvironment.plus"
)
constructor(
  public val map: Map<ViewEnvironmentKey<*>, Any> = emptyMap()
) {
  @Suppress("UNCHECKED_CAST")
  public operator fun <T : Any> get(key: ViewEnvironmentKey<T>): T = map[key] as? T ?: key.default

  @Suppress("DEPRECATION")
  public operator fun <T : Any> plus(pair: Pair<ViewEnvironmentKey<T>, T>): ViewEnvironment =
    ViewEnvironment(map + pair)

  @Suppress("DEPRECATION")
  public operator fun plus(other: ViewEnvironment): ViewEnvironment {
    if (this == other) return this
    if (other.map.isEmpty()) return this
    if (this.map.isEmpty()) return other
    return ViewEnvironment(map + other.map)
  }

  override fun toString(): String = "ViewEnvironment($map)"

  override fun equals(other: Any?): Boolean =
    (other as? ViewEnvironment)?.let { it.map == map } ?: false

  override fun hashCode(): Int = map.hashCode()

  public companion object {
    @Suppress("DEPRECATION")
    public val EMPTY: ViewEnvironment = ViewEnvironment()
  }
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
    return "${this::class.simpleName}(${type.simpleName})"
  }
}
