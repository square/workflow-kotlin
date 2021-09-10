package com.squareup.workflow1.ui

import kotlin.reflect.KClass

/**
 * Immutable, append-only map of values that a parent view can pass down to
 * its children via [View.showRendering][android.view.View.showRendering] et al.
 * Allows container views to give descendants information about the context in which
 * they're drawing.
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
