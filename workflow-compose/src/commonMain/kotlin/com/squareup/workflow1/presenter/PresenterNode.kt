package com.squareup.workflow1.presenter

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

@PublishedApi
internal class PresenterNode(
  policy: PresenterPolicy,
) : PresenterPolicyScope,
  ChildPresenter,
  SlotWriter,
  SlotReader {

  var compositionLocals: CompositionLocalMap? = null
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  override val defaultSlot: PresenterSlot<Any>
    // This is a state read and will be tracked by the presenter's snapshot reader.
    get() = compositionLocals!![LocalDefaultPresenterSlot]

  var modifierChain: PresenterModifier = PresenterModifier
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  var producer: PresenterPolicy = policy
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  val children = mutableListOf<PresenterNode>()
  private var committedSlotValues: Map<PresenterSlot<*>, ViewModelRef<*>>
    by mutableStateOf(emptyMap())

  // TODO does this need to be a thread local?
  private var slotValueBuilder: MutableMap<PresenterSlot<*>, ViewModelRef<*>>? = null
  var dirty = true
    private set
  private var hasDirtyChild = false
  private var parent: PresenterNode? = null

  override val outputSlots: PresenterNode get() = this

  fun attach(parent: PresenterNode) {
    check(this.parent == null) { "Tried to attach node that was already attached" }
    this.parent = parent
    invalidate()
    // parent.invalidateChild() isn't enough, since the child list changed the parent itself needs
    // to reproduce.
    parent.invalidate()
  }

  fun detach() {
    val parent = checkNotNull(parent) { "Tried to detach node that was not attached" }
    // parent.invalidateChild() isn't enough, since the child list changed the parent itself needs
    // to reproduce.
    parent.invalidate()
    this.parent = null
  }

  fun invalidate() {
    if (dirty) return
    dirty = true
    parent?.invalidateChild()
  }

  private fun invalidateChild() {
    if (hasDirtyChild) return
    hasDirtyChild = true
    parent?.invalidateChild()
  }

  fun removeChildren(
    from: Int = 0,
    count: Int = children.size
  ) {
    val toRemove = children.subList(fromIndex = from, toIndex = from + count)
    toRemove.forEach { child ->
      child.detach()
    }
    toRemove.clear()
  }

  fun visitDirtyChildren(visitor: (PresenterNode) -> Unit) {
    if (hasDirtyChild) {
      hasDirtyChild = false
      children.forEach { child ->
        child.visitDirtyChildren(visitor)
      }
    }

    visitor(this)
  }

  fun runProducer() {
    check(!hasDirtyChild) { "Tried to reproduce parent with dirty children" }
    checkNotNull(parent) { "Tried to reproduce unattached node" }

    slotValueBuilder = mutableMapOf()
    with(producer) {
      produce(children)
    }
  }

  fun commitToStates() {
    dirty = false
    val parent = checkNotNull(parent) { "Tried to reproduce unattached node" }
    val oldKeys = committedSlotValues.keys
    val newKeys = slotValueBuilder!!.keys
    val keySetChanged = oldKeys != newKeys
    committedSlotValues = slotValueBuilder!!
    slotValueBuilder = null
    if (keySetChanged) {
      parent.invalidate()
    }
  }

  override fun <T : Any> read(slot: PresenterSlot<T>): ViewModelRef<T>? =
    routeReads {
      // TODO implement consumption
      @Suppress("UNCHECKED_CAST")
      committedSlotValues[slot] as ViewModelRef<T>?
    }

  override fun contains(slot: PresenterSlot<*>): Boolean =
    routeReads {
      slot in committedSlotValues
    }

  override fun readAllSlotsTo(writer: SlotWriter) {
    routeReads {
      committedSlotValues.forEach { (slot, holder) ->
        @Suppress("UNCHECKED_CAST")
        writer[slot as PresenterSlot<ViewModelRef<*>>] = holder
      }
    }
  }

  override fun <T : Any> writeRef(
    slot: PresenterSlot<T>,
    ref: ViewModelRef<T>
  ) {
    getSlotHolder(slot).setRef(ref)
  }

  override fun <T : Any> writeValue(
    slot: PresenterSlot<T>,
    value: T
  ) {
    getSlotHolder(slot).setViewModel(value)
  }

  override fun clear(slot: PresenterSlot<*>) {
    requireSlotBuilder().remove(slot)
  }

  override fun toString(): String = buildString {
    append("PresenterNode(dirty=$dirty, hasDirtyChildren=$hasDirtyChild, ")
    if (slotValueBuilder != null) {
      append("producing, ")
    }
    append("slots: ")
    slotValueBuilder?.toDebugString()
      ?: committedSlotValues.toDebugString()
    append(")")
  }

  private inline fun <T> routeReads(block: @DisallowComposableCalls () -> T): T {
    return if (parent?.slotValueBuilder != null) {
      // Parent is reading us during presenter phase, so don't report a snapshot read.
      Snapshot.withoutReadObservation {
        block()
      }
    } else {
      block()
    }
  }

  private fun <T : Any> getSlotHolder(key: PresenterSlot<T>): ViewModelRef<T> {
    val builder = requireSlotBuilder()
    val holder = builder.getOrPut(key) {
      Snapshot.withoutReadObservation {
        committedSlotValues.getOrElse(key) {
          ViewModelRef(key.type)
        }
      }
    }
    @Suppress("UNCHECKED_CAST")
    return holder as ViewModelRef<T>
  }

  private fun requireSlotBuilder(): MutableMap<PresenterSlot<*>, ViewModelRef<*>> =
    checkNotNull(slotValueBuilder) {
      "Cannot write to SlotWriter outside of producer pass"
    }

  context(builder: StringBuilder)
  private fun Map<PresenterSlot<*>, ViewModelRef<*>>.toDebugString() {
    entries.joinTo(builder) { (slot, ref) ->
      "${slot.name}=${ref.resolve()}"
    }
  }
}
