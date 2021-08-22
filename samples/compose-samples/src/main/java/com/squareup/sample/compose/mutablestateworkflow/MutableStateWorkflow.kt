package com.squareup.sample.compose.mutablestateworkflow

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.squareup.sample.compose.mutablestateworkflow.MutableStateWorkflow.RememberedMutableValue
import com.squareup.sample.compose.mutablestateworkflow.MutableStateWorkflow.RememberedValue
import com.squareup.sample.compose.mutablestateworkflow.MutableStateWorkflowImpl.SaveableProperty
import com.squareup.sample.compose.mutablestateworkflow.MutableStateWorkflowImpl.WorkflowState
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.BufferedSink
import okio.BufferedSource
import kotlin.experimental.ExperimentalTypeInference
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * TODO write documentation
 */
fun interface MutableStateWorkflow<PropsT, OutputT, RenderingT> :
  Workflow<PropsT, OutputT, RenderingT> {

  fun Scope.render(props: PropsT): RenderingT

  override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> =
    MutableStateWorkflowImpl(this)

  @OptIn(ExperimentalTypeInference::class)
  interface Scope {

    fun <T> remember(
      vararg keys: Any,
      block: () -> T
    ): RememberedValue<T>

    fun <T> rememberState(
      vararg keys: Any,
      block: () -> MutableState<T>
    ): RememberedMutableValue<T>

    fun <T : Any> rememberSaveable(
      key: String,
      saver: Saver<T, Any> = autoSaver(),
      block: () -> MutableState<T>
    ): RememberedMutableValue<T>
  }

  fun interface RememberedValue<T> :
    PropertyDelegateProvider<Nothing?, ReadOnlyProperty<Nothing?, T>>

  fun interface RememberedMutableValue<T> :
    PropertyDelegateProvider<Nothing?, ReadWriteProperty<Nothing?, T>>
}

private class MutableStateWorkflowImpl<PropsT, OutputT, RenderingT>(
  private val workflow: MutableStateWorkflow<PropsT, OutputT, RenderingT>
) : StatefulWorkflow<PropsT, WorkflowState, OutputT, RenderingT>() {

  class WorkflowState(
    val snapshotStateObserver: SnapshotStateObserver,
    var restoredValues: Map<String, Any> = emptyMap()
  ) {
    /**
     * Also contains ReadWriteProperties, which are a subtype of ReadOnlyProperties.
     */
    val rememberedProperties = mutableMapOf<KProperty<*>, ReadOnlyProperty<Nothing?, *>>()
    val rememberedSaveableProperties = mutableMapOf<String, SaveableProperty<*>>()
  }

  class SaveableProperty<T>(
    val saver: Saver<T, *>,
    var value: T
  ) : ReadWriteProperty<Nothing?, T> {

    fun snapshot(): Snapshot? {
      val valueToSave = with(saver) {
        SnapshotSaverScope.save(value)
      }
      return valueToSave?.let {
        Snapshot.write { sink ->
          SnapshotSaverScope.writeValueToSink(sink, valueToSave)
        }
      }
    }

    override fun getValue(
      thisRef: Nothing?,
      property: KProperty<*>
    ): T {
      TODO("not implemented")
    }

    override fun setValue(
      thisRef: Nothing?,
      property: KProperty<*>,
      value: T
    ) {
      TODO("not implemented")
    }
  }

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): WorkflowState {
    fun onSnapshotChanged(callback: () -> Unit) {
      TODO()
    }

    val observer = SnapshotStateObserver(::onSnapshotChanged)

    val restoredValues = snapshot?.bytes?.parse { source ->
      val size = source.readInt()
      val restoredValues = mutableMapOf<String, Any>()
      repeat(size) {
        val key = source.readUtf8WithLength()
        val value = SnapshotSaverScope.readValueFromSnapshot(source)
        restoredValues[key] = value
      }
      restoredValues
    } ?: emptyMap()

    return WorkflowState(observer, restoredValues)
  }

  override fun render(
    renderProps: PropsT,
    renderState: WorkflowState,
    context: RenderContext
  ): RenderingT {
    val scope = ScopeImpl(renderState)
    var rendering: RenderingT? = null

    renderState.snapshotStateObserver.observeReads(
      scope = this,
      onValueChangedForScope = {
        // Don't actually need to perform an action, since the state has already been changed, just
        // trigger another render pass.
        context.actionSink.send(noAction())
      },
    ) {
      with(workflow) {
        rendering = scope.render(renderProps)
      }
    }

    // Ensure that values are only restored on the first render pass.
    // Only needs to happen after the first render pass really.
    renderState.restoredValues = emptyMap()

    // Don't use !! because RenderingT may in fact be nullable, and null may be a valid rendering
    // value.
    @Suppress("UNCHECKED_CAST")
    return rendering as RenderingT
  }

  override fun snapshotState(state: WorkflowState): Snapshot {
    // TODO perform this map traversal in a snapshot to ensure consistency
    // TODO refactor the list of saveable properties so it's immutable and this function just copies
    //  it.
    // Capture the values eagerly at the time the snapshot is taken, not when it's serialized.
    val snapshotsToSave = state.rememberedSaveableProperties
      .mapValues { (_, property) -> property.snapshot() }
      .filterValues { it != null }

    return Snapshot.write { sink ->
      sink.writeInt(snapshotsToSave.size)
      snapshotsToSave.forEach { (key, snapshot) ->
        sink.writeUtf8WithLength(key)
        sink.writeByteStringWithLength(snapshot!!.bytes)
      }
    }
  }

  private object SnapshotSaverScope : SaverScope {
    override fun canBeSaved(value: Any): Boolean {
      TODO("not implemented")
    }

    fun writeValueToSink(
      sink: BufferedSink,
      value: Any
    ) {
      TODO("not implemented")
    }

    fun readValueFromSnapshot(source: BufferedSource): Any {
      TODO("not implemented")
    }
  }
}

private class ScopeImpl(
  private val state: WorkflowState
) : MutableStateWorkflow.Scope {

  override fun <T> remember(
    vararg keys: Any,
    block: () -> T
  ): RememberedValue<T> = RememberedValue rem@{ _, property ->
    if (property in state.rememberedProperties) {
      @Suppress("UNCHECKED_CAST")
      return@rem state.rememberedProperties.getValue(property) as ReadWriteProperty<Nothing?, T>
    }

    val value = block()
    val delegate = ReadOnlyProperty<Nothing?, T> { _, _ -> value }
    state.rememberedProperties[property] = delegate

    return@rem delegate
  }

  override fun <T> rememberState(
    vararg keys: Any,
    block: () -> MutableState<T>
  ): RememberedMutableValue<T> = RememberedMutableValue rem@{ _, property ->
    if (property in state.rememberedProperties) {
      @Suppress("UNCHECKED_CAST")
      return@rem state.rememberedProperties.getValue(property) as ReadWriteProperty<Nothing?, T>
    }

    val mutableState = block()
    val delegate = object : ReadWriteProperty<Nothing?, T> {
      override fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
      ): T = mutableState.value

      override fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
      ) {
        mutableState.value = value
      }
    }
    state.rememberedProperties[property] = delegate

    return@rem delegate
  }

  override fun <T : Any> rememberSaveable(
    key: String,
    saver: Saver<T, Any>,
    block: () -> MutableState<T>
  ): RememberedMutableValue<T> = RememberedMutableValue rem@{ _, _ ->
    if (key in state.rememberedSaveableProperties) {
      @Suppress("UNCHECKED_CAST")
      return@rem state.rememberedSaveableProperties.getValue(key) as ReadWriteProperty<Nothing?, T>
    }

    val mutableState = if (key in state.restoredValues) {
      val restoredValue = state.restoredValues.getValue(key)

      @Suppress("UNCHECKED_CAST")
      val convertedValue = saver.restore(restoredValue)
      // Null means the saver can't restore it and it should be initialized new.
      convertedValue?.let(::mutableStateOf) ?: block()
    } else {
      block()
    }

    val delegate = SaveableProperty(
      MutableStateSaver(saver),
      mutableState
    )
    state.rememberedSaveableProperties[key] = SaveableProperty(saver, delegate)

    return@rem delegate
  }

  private class MutableStateSaver<T, S : Any>(
    private val valueSaver: Saver<T, S>
  ) : Saver<MutableState<T>, S> {
    override fun SaverScope.save(value: MutableState<T>): S? {
      with(valueSaver) {
        return save(value.value)
      }
    }

    override fun restore(value: S): MutableState<T>? {
      return valueSaver.restore(value)?.let(::mutableStateOf)
    }
  }
}
