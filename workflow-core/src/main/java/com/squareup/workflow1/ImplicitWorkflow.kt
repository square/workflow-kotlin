package com.squareup.workflow1

import com.squareup.workflow1.ImplicitWorkflow.Ctx
import com.squareup.workflow1.ImplicitWorkflow.WorkflowState
import com.squareup.workflow1.ImplicitWorkflowImpl.State
import okio.ByteString
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface StateSaver<T> {
  fun toByteString(value: T): ByteString
  fun fromByteString(bytes: ByteString): T
}

/**
 * TODO write documentation
 */
abstract class ImplicitWorkflow<PropsT, RenderingT> : Workflow<PropsT, Nothing, RenderingT> {

  interface WorkflowState<T> : ReadWriteProperty<Nothing?, T>

  interface Ctx {
    fun <T> state(init: () -> T): WorkflowState<T>
    fun <T> savedState(
      key: String? = null,
      saver: StateSaver<T>,
      init: () -> T
    ): WorkflowState<T>

    fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String = "",
      onOutput: (ChildOutputT) -> Unit
    ): ChildRenderingT

    fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    )
  }

  abstract fun Ctx.render(props: PropsT): RenderingT

  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, Nothing, RenderingT> =
    ImplicitWorkflowImpl(this)
}

private class ImplicitWorkflowImpl<PropsT, RenderingT>(
  private val implicitWorkflow: ImplicitWorkflow<PropsT, RenderingT>
) : StatefulWorkflow<PropsT, State, Nothing, RenderingT>() {

  /**
   * Stores a map of property reference to property value. When Kotlin generates [KProperty]
   * instances for local delegated vars, the instances are stable by code location. This means we
   * can use the instance of the [KProperty] for positional memoization.
   */
  data class State(
    val states: Map<KProperty<*>, ImplicitState<*>> = emptyMap(),
    val restoredValues: Map<String, ByteString> = emptyMap()
  )

  data class ImplicitState<T>(
    val value: T,
    val key: String? = null,
    val saver: StateSaver<T>? = null
  ) {
    fun toByteString() = saver?.toByteString(value)
  }

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): State = snapshot?.bytes?.parse { source ->
    val count = source.readInt()
    val savedStates = List(count) {
      val key = source.readUtf8WithLength()
      val bytes = source.readByteStringWithLength()
      Pair(key, bytes)
    }
    State(restoredValues = savedStates.toMap())
  } ?: State()

  override fun render(
    props: PropsT,
    state: State,
    context: RenderContext
  ): RenderingT = with(implicitWorkflow) {
    val ctx = RenderContextCtx(state, context)
    ctx.render(props)
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write { sink ->
    val savableStates = state.states.values.filter { it.saver != null && it.key != null }
    sink.writeInt(savableStates.size)
    savableStates.forEach { state ->
      sink.writeUtf8WithLength(state.key!!)
      sink.writeByteStringWithLength(state.toByteString()!!)
    }
  }

  private inner class RenderContextCtx(
    private val state: State,
    private val context: BaseRenderContext<PropsT, State, Nothing>
  ) : Ctx {

    private inner class WorkflowStateImpl<T>(
      private val init: () -> T,
      private val saverKey: String? = null,
      private val saver: StateSaver<T>? = null
    ) : WorkflowState<T> {
      override fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
      ): T {
        state.states[property]?.let {
          @Suppress("UNCHECKED_CAST")
          return it.value as T
        }

        val restoredBytes = state.restoredValues[saverKey] ?: return init()
        return saver!!.fromByteString(restoredBytes)
      }

      override fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
      ) {
        context.actionSink.send(action({ "set implicit state $property" }) {
          state = state.copy(
              states = state.states + (property to ImplicitState(value, saverKey, saver)),
              restoredValues = if (saverKey != null) {
                // Will never restore this value again, clean up its serialized blob.
                state.restoredValues - saverKey
              } else {
                state.restoredValues
              }
          )
        })
      }
    }

    override fun <T> state(init: () -> T): WorkflowState<T> = WorkflowStateImpl(init)

    override fun <T> savedState(
      key: String?,
      saver: StateSaver<T>,
      init: () -> T
    ): WorkflowState<T> = WorkflowStateImpl(init, key, saver)

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      onOutput: (ChildOutputT) -> Unit
    ): ChildRenderingT = context.renderChild(child, props, key) { output ->
      // This callback will likely set some WorkflowState properties, which send their own
      // WorkflowActions.
      onOutput(output)
      WorkflowAction.noAction()
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    ) = context.runningSideEffect(key, sideEffect)
  }
}
