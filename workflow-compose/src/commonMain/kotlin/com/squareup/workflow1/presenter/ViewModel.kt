package com.squareup.workflow1.presenter

import androidx.compose.runtime.State
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface ViewModel {
  /**
   * Represents a [ViewModel] or [ViewModelRef] with no data.
   */
  object Empty : ViewModelRef(typeOf<Empty>()) {
    override val viewModel: State<Empty> = object : State<Empty> {
      override val value: Empty get() = Empty
    }
  }
}

/**
 * Converts a list of [ViewModel]s or [ViewModelRef]s into a single model. If the list contains more
 * than one item, then a [ViewModelStack] is returned.
 *
 * No [ViewModelRef]s in the list are resolved by this method.
 */
fun List<ViewModel>.simplify(): ViewModel = when (this.size) {
  0 -> ViewModel.Empty
  1 -> this[0]
  else -> ViewModelStack(this)
}

fun ViewModel.hasType(type: KType): Boolean =
  if (this is ViewModelRef) {
    this.type == type
  } else {
    (type.classifier as? KClass<*>)?.isInstance(this) == true
  }
