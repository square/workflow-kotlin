package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Immutable map of values that a parent view can pass down to
 * its children at construction time. Allows containers to give
 * descendants information about the context in which they're drawing.
 */
@WorkflowUiExperimentalApi
public class VisualEnvironment
@Deprecated(
  "To eliminate runtime errors this constructor will become private. " +
    "Use VisualEnvironment.EMPTY and VisualEnvironment.plus"
)
constructor(
  public val map: Map<VisualEnvironmentKey<*>, Any> = emptyMap()
) {
  public operator fun <T : Any> get(key: VisualEnvironmentKey<T>): T = getOrNull(key) ?: key.default

  public operator fun <T : Any> plus(pair: Pair<VisualEnvironmentKey<T>, T>): VisualEnvironment {
    val (newKey, newValue) = pair
    val newPair = getOrNull(newKey)
      ?.let { oldValue -> newKey to newKey.combine(oldValue, newValue) }
      ?: pair
    @Suppress("DEPRECATION")
    return VisualEnvironment(map + newPair)
  }

  @Suppress("DEPRECATION")
  public operator fun plus(other: VisualEnvironment): VisualEnvironment {
    if (this == other) return this
    if (other.map.isEmpty()) return this
    if (map.isEmpty()) return other
    val newMap = map.toMutableMap()
    other.map.entries.forEach { (key, value) ->
      @Suppress("UNCHECKED_CAST")
      newMap[key] = getOrNull(key as VisualEnvironmentKey<Any>)
        ?.let { oldValue -> key.combine(oldValue, value) }
        ?: value
    }
    return VisualEnvironment(newMap)
  }

  override fun toString(): String = "VisualEnvironment($map)"

  override fun equals(other: Any?): Boolean =
    (other as? VisualEnvironment)?.let { it.map == map } ?: false

  override fun hashCode(): Int = map.hashCode()

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> getOrNull(key: VisualEnvironmentKey<T>): T? = map[key] as? T

  public companion object {
    @Suppress("DEPRECATION")
    public val EMPTY: VisualEnvironment = VisualEnvironment()
  }
}

/**
 * Defines a value that can be provided by a [VisualEnvironment] map, specifying
 * its [default] value.
 */
@WorkflowUiExperimentalApi
public abstract class VisualEnvironmentKey<T : Any>() {
  /**
   * Convenience for migration.
   */
  @Deprecated("Use no args constructor", ReplaceWith("VisualEnvironmentKey<T>()"))
  public constructor(@Suppress("UNUSED_PARAMETER") type: KClass<T>) : this()

  public abstract val default: T

  /**
   * Applied from [VisualEnvironment.plus] when the receiving environment already contains
   * a value for this key. The default implementation replaces [left] with [right].
   */
  public open fun combine(
    left: T,
    right: T
  ): T = right

  final override fun equals(other: Any?): Boolean {
    return this === other || (other != null && this::class == other::class)
  }

  final override fun hashCode(): Int = this::class.hashCode()
}
