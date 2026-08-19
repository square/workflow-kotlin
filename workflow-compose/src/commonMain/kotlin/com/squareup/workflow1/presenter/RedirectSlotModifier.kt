package com.squareup.workflow1.presenter

/**
 * Redirects view models from the default slot to [presentOn].
 */
fun PresenterModifier.presentOn(slot: PresenterSlot<Any>): PresenterModifier =
  present { child ->
    outputSlots[slot] = child.read()
  }

/**
 * Emits any view model from the modified presenter's [fromSlot] slot to [toSlot].
 */
fun <T : Any> PresenterModifier.redirectSlot(
  fromSlot: PresenterSlot<T>,
  toSlot: PresenterSlot<T>
): PresenterModifier = present { child ->
  outputSlots[toSlot] = child.read(fromSlot)
}

/**
 * Emits any view model from the modified presenter's default slot to [toSlot].
 */
fun <T : Any> PresenterModifier.mapDefaultSlotTo(
  toSlot: PresenterSlot<T>,
  transform: (ViewModelRef<*>) -> T
): PresenterModifier = present { child ->
  val ref = child.read()
  if (ref != null) {
    outputSlots[toSlot] = transform(ref)
  }
}

/**
 * Emits any view model from the modified presenter's [fromSlot] slot to [toSlot].
 */
fun <T : Any, U : Any> PresenterModifier.mapSlot(
  fromSlot: PresenterSlot<T>,
  toSlot: PresenterSlot<U>,
  transform: (ViewModelRef<T>) -> U
): PresenterModifier = present { child ->
  val ref = child.read(fromSlot)
  if (ref != null) {
    outputSlots[toSlot] = transform(ref)
  }
}
