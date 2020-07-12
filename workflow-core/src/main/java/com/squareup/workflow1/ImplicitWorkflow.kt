/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.ImplicitWorkflow.Ctx
import okio.ByteString
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import com.squareup.workflow1.WorkflowAction.Updater as ActionUpdater

/**
 * A property delegate provider that provides access to and mutation of an [ImplicitWorkflow] state
 * value over time.
 */
interface WorkflowState<T> {
  operator fun provideDelegate(
    thisRef: Nothing?,
    property: KProperty<*>
  ): ReadWriteProperty<Nothing?, T>
}

/**
 * Prevents access to [Ctx] methods from inside [Ctx.update] blocks.
 */
@DslMarker
private annotation class ImplicitWorkflowContext

/**
 * A [Workflow] that defines its state implicitly by creating local delegated properties via
 * [Ctx.state] and [Ctx.savedIntState]. It does not use [WorkflowAction]s – instead, state properties
 * can be read at any time, and written within [Ctx.update] blocks. [Ctx.update] can also be used
 * to emit outputs.
 */
abstract class ImplicitWorkflow<PropsT, OutputT, RenderingT> :
    Workflow<PropsT, OutputT, RenderingT> {

  abstract fun Ctx.render(): RenderingT

  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> =
    ImplicitWorkflowImpl()

  @ImplicitWorkflowContext
  abstract inner class Ctx {

    /**
     * The props value from the parent workflow.
     */
    abstract val props: PropsT

    /**
     * Creates a [WorkflowState] property delegate that will initially be set to the value returned
     * by [init]. The delegated property should usually only be written inside an [update] block.
     *
     * @param dependencies An optional vararg array of arbitrary data that this state is scoped to.
     * If any of these values change between render passes, the state will be re-initialized via
     * [init].
     * @param init Provides the initial value for this state.
     */
    abstract fun <T> state(
      vararg dependencies: Any?,
      init: () -> T
    ): WorkflowState<T>

    /**
     * Creates a [WorkflowState] property delegate that will be snapshotted using [saver]. If
     * [key] is provided, it will be used to save the snapshotted bytes, otherwise the name of the
     * property will be used.
     *
     * @param dependencies An optional vararg array of arbitrary data that this state is scoped to.
     * If any of these values change between render passes, the state will be re-initialized via
     * [init].
     * @param init Provides the initial value for this state.
     */
    abstract fun <T> savedState(
      key: String? = null,
      saver: StateSaver<T>,
      vararg dependencies: Any?,
      init: () -> T
    ): WorkflowState<T>

    /**
     * See [BaseRenderContext.renderChild].
     *
     * @param onOutput A function will be called whenever the child workflow emits an output. This
     * function is effectively already an [update] block, so you don't need to call [update] again
     * inside it, you can just set [state] properties and call [Updater.setOutput] to emit
     * outputs to your parent.
     */
    abstract fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String = "",
      onOutput: Updater.(output: ChildOutputT) -> Unit
    ): ChildRenderingT

    /**
     * See [BaseRenderContext.runningSideEffect].
     *
     * Updates sent to this workflow from [sideEffect] can either use [update] to fire-and-forget,
     * or [awaitUpdate] to suspend until the update has been applied.
     */
    abstract fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    )

    /**
     * Creates a transaction in which all writes to [WorkflowState] properties inside [block] will
     * be dispatched as a single [WorkflowAction].
     */
    abstract fun update(block: Updater.() -> Unit)

    /**
     * Like [update], but suspends the current coroutine until the update has been applied.
     * Intended to be used from [runningSideEffect].
     */
    abstract suspend fun awaitUpdate(block: Updater.() -> Unit)
  }

  @ImplicitWorkflowContext
  abstract inner class Updater {
    /**
     * The latest props used to render this workflow when the update is executed.
     */
    abstract val props: PropsT

    /**
     * Sets the output to be emitted by this [Ctx.update]. Only the last call to this function will
     * take effect.
     */
    abstract fun setOutput(value: OutputT)
  }

  private inner class ImplicitWorkflowImpl :
      StatefulWorkflow<PropsT, ImplicitWorkflowState, OutputT, RenderingT>() {

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ): ImplicitWorkflowState = snapshot?.bytes?.parse { source ->
      val count = source.readInt()
      val savedStates = List(count) {
        val key = source.readUtf8WithLength()
        val bytes = source.readByteStringWithLength()
        Pair(key, bytes)
      }
      ImplicitWorkflowState(restoredValues = savedStates.toMap())
    } ?: ImplicitWorkflowState()

    override fun render(
      props: PropsT,
      state: ImplicitWorkflowState,
      context: RenderContext
    ): RenderingT {
      val ctx = CtxImpl(props, state.states, state.restoredValues, state.mutator, context)
      val rendering = state.updateInTransaction {
        ctx.render()
      }

      // Freeze the Ctx so no new properties can be created, children rendered, etc, and lock in
      // the state values so that all future reads from the properties outside of update
      // transactions will always see these values.
      // After this function returns, any other threads that have captured properties from this
      // render pass will start seeing the new state values.
      ctx.commitFinalStates(state.states)

      // After the first render pass, we will never restore anything again, so free up unconsumed
      // bytes for GC.
      state.restoredValues = emptyMap()
      return rendering
    }

    override fun snapshotState(state: ImplicitWorkflowState): Snapshot? = Snapshot.write { sink ->
      val savableStates =
        state.states.values.filter { it.snapshotSaver != null && it.snapshotKey != null }
      sink.writeInt(savableStates.size)
      savableStates.forEach { state ->
        sink.writeUtf8WithLength(state.snapshotKey!!)
        sink.writeByteStringWithLength(state.toByteString()!!)
      }
    }
  }

  private inner class CtxImpl(
    override val props: PropsT,
    /**
     * This is initially the states map from the workflow state at the start of the render pass,
     * and then when this context is frozen it becomes the new state map and will never change
     * again.
     */
    private var baseStates: Map<KProperty<*>, WorkflowStateHolder>,
    private var restoredValues: Map<String, ByteString>,
    private var transactor: MapTransactor<KProperty<*>, WorkflowStateHolder>.Mutator,
    private val context: BaseRenderContext<PropsT, ImplicitWorkflowState, OutputT>
  ) : Ctx() {

    private var isCommitted = false

    override fun <T> state(
      vararg dependencies: Any?,
      init: () -> T
    ): WorkflowState<T> = WorkflowStatePropertyDelegate(init, dependencies)

    @Suppress("UNCHECKED_CAST")
    override fun <T> savedState(
      key: String?,
      saver: StateSaver<T>,
      vararg dependencies: Any?,
      init: () -> T
    ): WorkflowState<T> =
      WorkflowStatePropertyDelegate(init, dependencies, key, saver as StateSaver<Any?>)

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      onOutput: Updater.(ChildOutputT) -> Unit
    ): ChildRenderingT {
      check(!isCommitted)
      return context.renderChild(child, props, key) { output ->
        Action { onOutput(output) }
      }
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    ) {
      check(!isCommitted)
      context.runningSideEffect(key, sideEffect)
    }

    override fun update(block: Updater.() -> Unit) {
      val action = Action(block)
      context.actionSink.send(action)
    }

    @OptIn(ExperimentalWorkflowApi::class)
    override suspend fun awaitUpdate(block: Updater.() -> Unit) {
      val action = Action(block)
      context.actionSink.sendAndAwaitApplication(action)
    }

    fun commitFinalStates(finalBaseStates: Map<KProperty<*>, WorkflowStateHolder>) {
      isCommitted = true
      baseStates = finalBaseStates
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readStateProperty(property: KProperty<*>): T {
      // If we're in the middle of an update transaction, the first source of truth is the
      // property state either as it was written in the current transaction, or from the state
      // at the time the transaction was started.
      //
      // If the property doesn't exist in the transaction, it means this delegate was
      // created from a previous render pass, and a subsequent render pass has since
      // occurred which did not define the property. In that case, we'll fall through and
      // read it from the base state.
      transactor.withExistingTransaction { values ->
        if (property in values) return values.getValue(property).value as T

        // If we're not committed, we're rendering, and should only be able to reference
        // properties that have already been defined in this render pass. All properties defined
        // so far in the render pass will be present in stateBuilder, and it should not be
        // syntactically possible to reference a property that was defined in a previous render
        // pass.
        if (!isCommitted) throw AssertionError("Reading property that doesn't exist.")
      }

      // We're not in an update transaction or a render pass, so just read the property value
      // from the render pass in which this context was created.
      return baseStates.getValue(property).value as T
    }

    private inner class WorkflowStatePropertyDelegate<T>(
      private val init: () -> T,
      private val dependencies: Array<out Any?>,
      private val customSnapshotKey: String? = null,
      private val snapshotSaver: StateSaver<Any?>? = null
    ) : WorkflowState<T>, ReadWriteProperty<Nothing?, T> {
      init {
        // State properties can only be created during a render pass, ie. when not committed.
        check(!isCommitted)
      }

      /**
       * Invoked for every state property, on every render pass. Determines if the property was
       * already created on a previous render pass by looking at [baseStates], and initializes it
       * or restores it if necessary. Also ensures that the state is re-initialized if any of its
       * [dependencies] changed.
       */
      override fun provideDelegate(
        thisRef: Nothing?,
        property: KProperty<*>
      ): ReadWriteProperty<Nothing?, T> {
        // State properties can only be created during a render pass, ie. when not committed.
        check(!isCommitted)

        // State properties can only be created in the transaction set up by the render method.
        check(transactor.isInTransaction)
        transactor.withExistingTransaction { statesBuilder ->
          val snapshotKey = customSnapshotKey ?: property.name
          var propertyState = baseStates[property]

          if (propertyState == null) {
            // This is a new property!
            val initialValue = if (snapshotSaver == null) {
              init()
            } else {
              val bytes = restoredValues[snapshotKey]
              if (bytes == null) {
                init()
              } else {
                snapshotSaver.fromByteString(bytes)
              }
            }
            propertyState =
              WorkflowStateHolder(initialValue, dependencies, snapshotKey, snapshotSaver)
          } else {
            // This property was declared in the previous render pass. Check for anything that
            // changed since then that we need to update.
            if (!dependencies.contentEquals(propertyState.dependencies)) {
              // Dependencies changed, so we need to re-initialize.
              propertyState = propertyState.copy(value = init())
            }
            if (snapshotKey != propertyState.snapshotKey ||
                snapshotSaver != propertyState.snapshotSaver
            ) {
              propertyState = propertyState.copy(
                  snapshotKey = snapshotKey,
                  snapshotSaver = snapshotSaver
              )
            }
          }

          statesBuilder[property] = propertyState
        }
        return this
      }

      override fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
      ): T = readStateProperty(property)

      override fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
      ) {
        // If we're in an update transaction, we won't dispatch our own action, we'll just record
        // the new value.
        transactor.withExistingTransaction { values ->
          var newState = if (property in values) {
            values.getValue(property)
          } else {
            // The snapshot was created from the same render pass as this delegate, so it's
            // guaranteed to exist in the base map at least.
            baseStates.getValue(property)
          }

          // Preserve the property's metadata.
          newState = newState.copy(value = value)
          values[property] = newState
          return
        }

        // We're not in a transaction – make one.
        update { setValue(thisRef, property, value) }
      }
    }

    private inner class Action(
      private val block: Updater.() -> Unit
    ) : WorkflowAction<PropsT, ImplicitWorkflowState, OutputT> {
      override fun ActionUpdater<PropsT, ImplicitWorkflowState, OutputT>.apply() {
        val updater = object : ImplicitWorkflow<PropsT, OutputT, RenderingT>.Updater() {
          override val props: PropsT get() = this@apply.props
          override fun setOutput(value: OutputT) = this@apply.setOutput(value)
        }
        state.updateInTransaction {
          block(updater)
        }
      }
    }
  }
}

/**
 * Stores a map of property reference to property value. When Kotlin generates [KProperty]
 * instances for local delegated vars, the instances are stable by code location. This means we
 * can use the instance of the [KProperty] for positional memoization.
 *
 * @param restoredValues Same caveats and restrictions as [states].
 */
private class ImplicitWorkflowState(
  var restoredValues: Map<String, ByteString> = emptyMap()
) {
  private val transactor = MapTransactor<KProperty<*>, WorkflowStateHolder>()

  /**
   * Exposes access to the current transaction without the ability to create a new transaction.
   */
  val mutator = transactor.mutator

  /**
   * Yes, this is mutable. No, this should not generally be done. However, the very
   * nature of this workflow is that it uses its render pass to determine its state, so we need to
   * be able to update the state without triggering another pass. This property should only ever
   * be mutated in the [StatefulWorkflow.render] method or a [WorkflowAction.apply] method.
   */
  var states: Map<KProperty<*>, WorkflowStateHolder> = emptyMap()
    private set

  /**
   * Runs [block] inside a transaction which can be accessed via [mutator]. The transaction is
   * initialized with the current state, and after [block] returns the state is updated to the
   * result of the transaction.
   *
   * This _MUST_ only be called either from the render method or from a WorkflowAction's apply
   * method.
   */
  fun <R> updateInTransaction(block: () -> R): R {
    val (newStates, returnValue) = transactor.withNewTransaction(states, block)
    states = newStates
    return returnValue
  }
}

/**
 * Values stored in the [ImplicitWorkflowState.states] map that associates metadata about each state property with
 * that property's value.
 */
private data class WorkflowStateHolder(
  val value: Any?,
  val dependencies: Array<out Any?> = emptyArray(),
  val snapshotKey: String? = null,
  val snapshotSaver: StateSaver<Any?>? = null
) {
  fun toByteString() = snapshotSaver?.toByteString(value)

  // Override equals and hashcode to properly support array comparison.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WorkflowStateHolder

    if (value != other.value) return false
    if (!dependencies.contentEquals(other.dependencies)) return false
    if (snapshotKey != other.snapshotKey) return false
    if (snapshotSaver != other.snapshotSaver) return false

    return true
  }

  override fun hashCode(): Int {
    var result = value?.hashCode() ?: 0
    result = 31 * result + dependencies.contentHashCode()
    result = 31 * result + (snapshotKey?.hashCode() ?: 0)
    result = 31 * result + (snapshotSaver?.hashCode() ?: 0)
    return result
  }
}

/**
 * Convenience function for creating anonymous [ImplicitWorkflow]s.
 */
inline fun <PropsT, OutputT, RenderingT> Workflow.Companion.implicit(
  crossinline render: ImplicitWorkflow<PropsT, OutputT, RenderingT>.Ctx.() -> RenderingT
): Workflow<PropsT, OutputT, RenderingT> =
  object : ImplicitWorkflow<PropsT, OutputT, RenderingT>() {
    override fun Ctx.render(): RenderingT = render()
  }

@Suppress("NOTHING_TO_INLINE")
inline fun ImplicitWorkflow<*, *, *>.Ctx.savedIntState(
  key: String? = null,
  vararg dependencies: Any?,
  noinline init: () -> Int
): WorkflowState<Int> = savedState(key, IntStateSaver, *dependencies, init = init)

@Suppress("NOTHING_TO_INLINE")
inline fun ImplicitWorkflow<*, *, *>.Ctx.savedLongState(
  key: String? = null,
  vararg dependencies: Any?,
  noinline init: () -> Long
): WorkflowState<Long> = savedState(key, LongStateSaver, *dependencies, init = init)

@Suppress("NOTHING_TO_INLINE")
inline fun ImplicitWorkflow<*, *, *>.Ctx.savedStringState(
  key: String? = null,
  vararg dependencies: Any?,
  noinline init: () -> String
): WorkflowState<String> = savedState(key, StringStateSaver, *dependencies, init = init)

@Suppress("NOTHING_TO_INLINE")
inline fun <T> ImplicitWorkflow<*, *, *>.Ctx.savedListState(
  key: String? = null,
  itemSaver: StateSaver<T>,
  vararg dependencies: Any?,
  noinline init: () -> List<T>
): WorkflowState<List<T>> = savedState(key, ListSaver(itemSaver), *dependencies, init = init)

@Suppress("NOTHING_TO_INLINE")
inline fun <ChildPropsT, ChildRenderingT> ImplicitWorkflow<*, *, *>.Ctx.renderChild(
  child: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
  key: String = ""
): ChildRenderingT = renderChild(child, props, key) {}

@Suppress("NOTHING_TO_INLINE")
inline fun <ChildOutputT, ChildRenderingT> ImplicitWorkflow<*, *, *>.Ctx.renderChild(
  child: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  key: String = "",
  noinline onOutput: ImplicitWorkflow<*, *, *>.Updater.(output: ChildOutputT) -> Unit
): ChildRenderingT = renderChild(child, Unit, key, onOutput)

@Suppress("NOTHING_TO_INLINE")
inline fun <ChildRenderingT> ImplicitWorkflow<*, *, *>.Ctx.renderChild(
  child: Workflow<Unit, Nothing, ChildRenderingT>,
  key: String = ""
): ChildRenderingT = renderChild(child, Unit, key) {}
