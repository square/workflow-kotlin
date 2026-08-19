package com.squareup.workflow1.presenter

/**
 * The core logic of a [NavigationPresenter] node. See the documentation on [produce] for more information.
 */
fun interface PresenterPolicy {
  /**
   * The core function of a [NavigationPresenter] node. Main job is to emit view models through the
   * [PresenterPolicyScope]'s [outputSlots] [SlotWriter]. You can emit raw values or forward view
   * model refs from your children [ChildPresenter]s via the methods on [SlotWriter].
   *
   * This function will be re-executed in the following cases:
   *  - Whenever a child is added, removed, or changes composition order.
   *  - The set of [PresenterSlot]s a child writes to changes.
   *  - A snapshot state object it reads is written to.
   *
   * Notably, it will not re-execute just because a child writes a new value to a slot it
   * previously wrote to. The [ViewModelRef] read from the child for that slot will always return
   * the same ref instance, and always represents the latest value written by that child. There is
   * no way for a presenter to see its children's actual view model values, so it cannot depend on
   * them. A parent presenter can only depend on _which_ slots the child writes to.
   */
  fun PresenterPolicyScope.produce(children: List<ChildPresenter>)
}

interface PresenterPolicyScope {
  /**
   * A [SlotWriter] that emits view models to the parent presenter node.
   */
  val outputSlots: SlotWriter

  /**
   * The [PresenterSlot] to use if you don't have a specific slot you need to emit on.
   *
   * Convenience methods exist to read/write from/to this slot:
   *  - [read] will read from this slot on a [SlotReader].
   *  - [defaultViewModel] is a write-only property that will write to this slot on this
   *    presenter node.
   *
   * The default slot is assigned by the [LocalDefaultPresenterSlot] composition local.
   */
  val defaultSlot: PresenterSlot<Any>
}

/**
 * Reads from the [PresenterPolicyScope.defaultSlot].
 */
context(scope: PresenterPolicyScope)
fun SlotReader.read(): ViewModelRef<*>? = read(scope.defaultSlot)

/**
 * Writes to this presenter node's [PresenterPolicyScope.defaultSlot].
 */
var PresenterPolicyScope.defaultViewModel: Any?
  get() = throw UnsupportedOperationException("Cannot read from SlotWriter")
  set(value) {
    outputSlots[defaultSlot] = value
  }
