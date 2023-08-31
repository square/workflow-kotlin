package com.squareup.workflow1

import kotlin.reflect.KClass

/**
 * Immutable map of values that a parent can pass down to
 * its children. Allows containers to give descendants information about
 * the context in which they're drawing.
 *
 */
public open class LocalMap
@Deprecated(
  "To eliminate runtime errors this constructor will become private. " +
    "Use LocalMap.EMPTY and LocalMap.plus"
)
constructor(
  public val map: Map<LocalMapKey<*>, Any> = emptyMap()
) {
  public operator fun <T : Any> get(key: LocalMapKey<T>): T = getOrNull(key) ?: key.default

  public operator fun <T : Any> plus(pair: Pair<LocalMapKey<T>, T>): LocalMap {
    val (newKey, newValue) = pair
    val newPair = getOrNull(newKey)
      ?.let { oldValue -> newKey to newKey.combine(oldValue, newValue) }
      ?: pair
    @Suppress("DEPRECATION")
    return LocalMap(map + newPair)
  }

  public operator fun plus(other: LocalMap): LocalMap {
    if (this == other) return this
    if (other.map.isEmpty()) return this
    if (map.isEmpty()) return other
    val newMap = map.toMutableMap()
    other.map.entries.forEach { (key, value) ->
      @Suppress("UNCHECKED_CAST")
      newMap[key] = getOrNull(key as LocalMapKey<Any>)
        ?.let { oldValue -> key.combine(oldValue, value) }
        ?: value
    }
    @Suppress("DEPRECATION")
    return LocalMap(newMap)
  }

  override fun toString(): String = "LocalMap($map)"

  override fun equals(other: Any?): Boolean =
    (other as? LocalMap)?.let { it.map == map } ?: false

  override fun hashCode(): Int = map.hashCode()

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> getOrNull(key: LocalMapKey<T>): T? = map[key] as? T

  public companion object {
    @Suppress("DEPRECATION")
    public val EMPTY: LocalMap = LocalMap()
  }
}

/**
 * Defines a value type [T] that can be provided by a [LocalMap] map,
 * and specifies its [default] value.
 *
 * It is hard to imagine a useful implementation of this that is not a Kotlin `object`.
 * Preferred use is to have the `companion object` of [T] extend this class. See
 * [BackStackConfig.Companion][com.squareup.workflow1.ui.container.BackStackConfig.Companion]
 * for an example.
 */
public abstract class LocalMapKey<T : Any>() {
  @Deprecated("Use no args constructor", ReplaceWith("LocalMapKey<T>()"))
  public constructor(@Suppress("UNUSED_PARAMETER") type: KClass<T>) : this()

  /**
   * Defines the default value for this key. It is a grievous error for this value to be
   * dynamic in any way.
   */
  public abstract val default: T

  /**
   * Applied from [LocalMap.plus] when the receiving environment already contains
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
