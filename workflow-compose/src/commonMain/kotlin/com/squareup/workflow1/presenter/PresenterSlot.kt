package com.squareup.workflow1.presenter

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlin.reflect.KClass

/**
 * Defines what slot is considered the "default" by presenter nodes and
 * [PresenterPolicyScope.defaultSlot].
 *
 * The default slot must be usable for all view model types, so only slots that have the [Any]
 * type argument maybe be provided to this composition local.
 */
val LocalDefaultPresenterSlot: ProvidableCompositionLocal<PresenterSlot<Any>> =
  compositionLocalOf { DefaultPresenterSlot }

/**
 * The default [PresenterSlot] used as the [PresenterPolicyScope.defaultSlot]. To use a
 * different slot as the default, provide it to [LocalDefaultPresenterSlot].
 */
val DefaultPresenterSlot = PresenterSlot<Any>("Default")

/**
 * Creates a [PresenterSlot] with the given [name] and [type][T].
 */
inline fun <reified T : Any> PresenterSlot(name: String): PresenterSlot<T> =
  PresenterSlot(name, T::class)

/**
 * Defines a slot through which raw view model values are transmitted from [NavigationPresenter] nodes up
 * to the [PresenterSlotMap] returned from [present].
 *
 * Any number of slots can be defined to emit multiple view models in parallel. There is always
 * a "default" slot, defined by [LocalDefaultPresenterSlot] and exposed via
 * [PresenterPolicyScope.defaultSlot], which should be used for the "main" view model emitted by
 * most presenters.
 *
 * Slots are strongly-typed, and may only carry view model values that conform to the [type][T]
 * of the slot.
 *
 * The slot is only allowed to
 *  * receive values of type [T] or [references][ViewModelRef] to values of that type.
 */
@Suppress("unused")
class PresenterSlot<T : Any>(
  internal val name: String,
  internal val type: KClass<T>,
) {
  override fun toString(): String =
    "PresenterSlot<$type>(name=\"$name\")@${hashCode().toHexString()}"
}
