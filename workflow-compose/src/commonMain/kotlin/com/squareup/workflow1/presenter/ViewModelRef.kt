package com.squareup.workflow1.presenter

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTarget
import androidx.compose.runtime.TestOnly
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KClass

/**
 * A reference to a raw view model value written by a [SlotWriter.writeValue] call in a presenter
 * node.
 *
 * @param T The type of the [PresenterSlot] that this ref comes from.
 */
class ViewModelRef<T : Any> internal constructor(
  val type: KClass<T>
) {

  private var valueOrRef: Any? by mutableStateOf(null)

  internal fun setViewModel(value: T) {
    valueOrRef = value
  }

  internal fun setRef(ref: ViewModelRef<T>) {
    valueOrRef = ref
  }

  internal fun resolve(): T {
    val valueOrRef = valueOrRef
    val ref = valueOrRef as? ViewModelRef<T>
    @Suppress("UNCHECKED_CAST")
    return ref?.resolve() ?: (valueOrRef as T)
  }

  override fun toString(): String =
    "ViewModelRef(currentViewModel=${resolve()})@${hashCode().toHexString()}"
}

/**
 * Returns the raw view model value referred to by this reference. Will cause the caller to
 * recompose whenever the referent changes.
 */
@Composable
@ComposableTarget("androidx.compose.ui.UiComposable")
fun <T : Any> ViewModelRef<T>.resolve(): T = this.resolve()

@RestrictTo(RestrictTo.Scope.TESTS)
fun <T : Any> ViewModelRef<T>.resolveForTest(): T = this.resolve()
