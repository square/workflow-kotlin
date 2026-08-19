package com.squareup.workflow1.presenter

import androidx.compose.runtime.State
import kotlin.reflect.KType

abstract class ViewModelRef internal constructor(val type: KType) : ViewModel {
  internal abstract val viewModel: State<ViewModel>

  private var flattenedStateCache: State<ViewModel>? = null
  internal fun asFlattenedState(): State<ViewModel> =
    flattenedStateCache ?: FlattenedState().also { flattenedStateCache = it }

  override fun toString(): String = buildString {
    val shallowValue = viewModel.value
    append("ViewModelRef<$type>@${hashCode().toHexString()}(currentViewModel=$shallowValue")
    if (shallowValue is ViewModelRef) {
      append(", flattened=${resolveRecursively()}")
    }
    append(")")
  }

  private inner class FlattenedState : State<ViewModel> {
    override val value: ViewModel
      get() = resolveRecursively()
  }
}

internal tailrec fun ViewModelRef.resolveRecursively(): ViewModel =
  when (val viewModel = this.viewModel.value) {
    is ViewModelRef -> viewModel.resolveRecursively()
    else -> viewModel
  }
